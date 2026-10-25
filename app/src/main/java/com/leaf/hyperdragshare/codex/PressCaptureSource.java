package com.leaf.hyperdragshare.codex;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.Toast;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** One press pipeline for both hosts: classify once, then use the confirmed gesture's result. */
final class PressCaptureSource implements RootTouchSource.ContactListener {
    interface OcrRequest {
        void cancel();
    }

    interface OcrRecognizer {
        OcrRequest recognize(Context context, android.graphics.Bitmap bitmap,
                             AicrOcrClient.Callback callback);
    }

    private static final String TAG = "DragShare/Press";
    private static final float WECHAT_DRAG_DISTANCE_DP = 36f;
    private static final long READER_RETRY_FIRST_MS = 3000L;
    private static final long READER_RETRY_MAX_MS = 30000L;
    private final Context context;
    private final RootTouchSource input;
    private final DragShareController controller;
    private volatile DragShareSettings settings;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final BackgroundTouchBlocker blocker;
    private final PressTriggerDetector detector = new PressTriggerDetector();
    private final OcrRecognizer ocrRecognizer;
    private volatile PortalPressSettings.Snapshot portalPressSettings;
    private PortalPressSettings.Subscription portalPressSubscription;
    private final Runnable classify = this::classifyPress;
    private final Runnable fire = this::firePress;
    private volatile SharedRootNodeCapture.Connection nodeSession;
    private volatile boolean stopped;
    private long readerRetryDelayMs;
    private boolean readerFailureShown;
    private boolean readerOpening;
    private String readerAttemptId = "-";
    private IBinder.DeathRecipient leaseDeath;
    private final Runnable readerRetry = () -> {
        if (stopped || nodeSession != null) return;
        DragShareLog.i(TAG, "press reader retry started");
        openReader();
    };
    private OcrRequest activeOcrRequest;
    private PendingWeChat pendingWeChat;
    private volatile long generation;
    private volatile boolean pointerDown;
    private volatile long downTime = -1L;
    private boolean lookupRunning;
    private boolean displayQueued;
    private long confirmedAt;
    private CapturedContent cached;
    private float downX;
    private float downY;
    private float latestX;
    private float latestY;
    private boolean weChatImageDragIntent;
    private boolean twoFingerContactSequence;
    private volatile boolean twoFingerModeForGesture;
    private volatile boolean twoFingerPressQualified;

    private static final class PendingWeChat {
        final CapturedContent content;
        final long request;
        final String operation;
        final long started;
        boolean ocrComplete;
        String text;
        Exception error;
        boolean textDelivered;
        boolean textWindowAccepted;

        PendingWeChat(CapturedContent content, long request, String operation) {
            this.content = content;
            this.request = request;
            this.operation = operation;
            this.started = SystemClock.elapsedRealtime();
        }
    }

    private String operationId() {
        return OperationTrace.id("press", downTime);
    }

    PressCaptureSource(
            Context context,
            RootTouchSource input,
            DragShareController controller,
            DragShareSettings settings) {
        this(context, input, controller, settings, (ocrContext, bitmap, callback) -> {
            AicrOcrClient.Request request = AicrOcrClient.recognize(ocrContext, bitmap, callback);
            return request::cancel;
        });
    }

    PressCaptureSource(
            Context context,
            RootTouchSource input,
            DragShareController controller,
            DragShareSettings settings,
            OcrRecognizer ocrRecognizer) {
        this.context = context;
        this.input = input;
        this.controller = controller;
        this.settings = settings;
        this.ocrRecognizer = ocrRecognizer;
        blocker = new BackgroundTouchBlocker(context);
        portalPressSettings = PortalPressSettings.read(context);
        portalPressSubscription = PortalPressSettings.observe(context, main,
                this::refreshPortalPressSettings);
        if (input != null) {
            input.setContactListener(this);
            input.setTwoFingerPressEnabled(isTwoFingerPressEnabled());
        }
        ModuleActivation.reportPressReader(context, false, "");
        openReader();
    }

    /** Start a lease attempt on the serial worker; death-triggered attempts start immediately. */
    private void openReader() {
        if (stopped || nodeSession != null || readerOpening) return;
        readerOpening = true;
        String attemptId = RootConnectionDiagnostics.newAttempt();
        readerAttemptId = attemptId;
        worker.execute(() -> {
            long connectionStarted = SystemClock.elapsedRealtime();
            try {
                SharedRootNodeCapture.Connection ready = SharedRootNodeCapture.open(context, false, attemptId);
                DragShareLog.i(TAG, "connection stage=press_reader_ready elapsed_ms="
                        + (SystemClock.elapsedRealtime() - connectionStarted));
                main.post(() -> {
                    readerOpening = false;
                    if (stopped) {
                        recordRootEvent(ready.connectionAttemptId(), "release", -1L);
                        ready.close();
                        return;
                    }
                    IBinder.DeathRecipient death = () -> main.post(() -> {
                        if (stopped || nodeSession != ready) return;
                        recordRootEvent(ready.connectionAttemptId(), "binder_death", -1L);
                        DragShareLog.w(TAG, "press reader lease died; reconnecting to the module process");
                        readerFailed(ready, "取词服务进程已退出，正在重连。", true);
                    });
                    if (!ready.linkToDeath(death)) {
                        readerAttemptId = ready.connectionAttemptId();
                        recordRootEvent(ready.connectionAttemptId(), "connect_failed", -1L);
                        recordRootEvent(ready.connectionAttemptId(), "release", -1L);
                        ready.close();
                        readerFailed(null, "取词连接已失效，正在重连。", false);
                        return;
                    }
                    nodeSession = ready;
                    leaseDeath = death;
                    readerAttemptId = ready.connectionAttemptId();
                    recordRootEvent(ready.connectionAttemptId(), "lease_ready", -1L);
                    main.removeCallbacks(readerRetry);
                    readerRetryDelayMs = 0L;
                    readerFailureShown = false;
                    ModuleActivation.reportPressReader(context, true, "");
                    DragShareLog.i(TAG, "press reader ready; idle without reading nodes");
                });
            } catch (IOException | SecurityException | IllegalStateException error) {
                DragShareLog.w(TAG, "connection stage=press_reader_failed elapsed_ms="
                        + (SystemClock.elapsedRealtime() - connectionStarted));
                DragShareLog.w(TAG, "cannot prepare press reader error=" + error.getClass().getSimpleName());
                main.post(() -> {
                    readerOpening = false;
                    readerAttemptId = attemptId;
                    recordRootEvent(attemptId, "connect_failed", -1L);
                    if (!stopped) readerFailed(null, "取词连接未建立，正在重连。" + error.getMessage(), false);
                });
            }
        });
    }

    void updateSettings(DragShareSettings updated) {
        DragShareSettings previous = settings;
        settings = updated;
        if (input != null) input.setTwoFingerPressEnabled(isTwoFingerPressEnabled());
        PortalPressSettings.Snapshot system = portalPressSettings;
        boolean pressConfigurationChanged = (!system.available
                && previous.twoFingerLongPressEnabled != updated.twoFingerLongPressEnabled)
                || previous.twoFingerLongPressTimeoutMillis != updated.twoFingerLongPressTimeoutMillis;
        boolean capturePolicyChanged = previous.pauseAutomaticInGames != updated.pauseAutomaticInGames
                || !previous.manualGamePackages.equals(updated.manualGamePackages)
                || !previous.accessibilityBlacklistedPackages.equals(updated.accessibilityBlacklistedPackages);
        if (capturePolicyChanged) {
            if (Looper.myLooper() == Looper.getMainLooper()) cancelGesture();
            else main.post(this::cancelGesture);
        } else if (pressConfigurationChanged) {
            if (Looper.myLooper() == Looper.getMainLooper()) cancelUnconfirmedPressCandidate();
            else main.post(this::cancelUnconfirmedPressCandidate);
        }
    }

    private boolean isTwoFingerPressEnabled() {
        PortalPressSettings.Snapshot system = portalPressSettings;
        if (system == null) system = PortalPressSettings.read(context);
        return system.resolveTwoFingerEnabled(settings.twoFingerLongPressEnabled);
    }

    private void refreshPortalPressSettings() {
        if (stopped) return;
        PortalPressSettings.Snapshot previous = portalPressSettings;
        PortalPressSettings.Snapshot updated = PortalPressSettings.read(context);
        boolean wasEnabled = previous.resolveTwoFingerEnabled(settings.twoFingerLongPressEnabled);
        portalPressSettings = updated;
        boolean isEnabled = updated.resolveTwoFingerEnabled(settings.twoFingerLongPressEnabled);
        if (input != null) input.setTwoFingerPressEnabled(isEnabled);
        if (wasEnabled != isEnabled) {
            cancelUnconfirmedPressCandidate();
        }
    }

    private void cancelUnconfirmedPressCandidate() {
        if (detector.isActive() && !detector.isFired()) cancelGesture();
        else controller.endPressPipeline(downTime);
    }

    void onPointerEvent(int action, float x, float y, long time) {
        if (Looper.myLooper() == Looper.getMainLooper()) handlePointer(action, x, y, time);
        else main.post(() -> handlePointer(action, x, y, time));
    }

    private void handlePointer(int action, float x, float y, long time) {
        if (stopped) return;
        if (twoFingerContactSequence) return;
        latestX = x;
        latestY = y;
        if (action == MotionEvent.ACTION_DOWN) {
            cancelGesture();
            downTime = time;
            twoFingerModeForGesture = isTwoFingerPressEnabled();
            twoFingerPressQualified = !twoFingerModeForGesture;
            if (twoFingerModeForGesture) {
                detector.startTwoFingerCandidate(x, y, time,
                        settings.resolvePressConfirmationTimeoutMillis());
            } else {
                detector.start(x, y, time, settings.resolvePressConfirmationTimeoutMillis());
            }
            pointerDown = true;
            downX = x;
            downY = y;
            latestX = x;
            latestY = y;
        }
        if (!allowed()) {
            cancelGesture();
            return;
        }
        boolean hadFired = detector.isFired();
        if (action != MotionEvent.ACTION_DOWN) detector.onPointerEvent(action, x, y, 1);
        if (action == MotionEvent.ACTION_UP) pointerDown = false;
        if (action == MotionEvent.ACTION_MOVE
                && Math.hypot(x - downX, y - downY) > WECHAT_DRAG_DISTANCE_DP
                        * context.getResources().getDisplayMetrics().density) {
            weChatImageDragIntent = true;
            if (pendingWeChat != null) dragWeChatPressCandidate(x, y);
        }
        if (action == MotionEvent.ACTION_UP) finishWeChatPress();
        if (action == MotionEvent.ACTION_UP && hadFired) {
            main.removeCallbacks(classify);
            main.removeCallbacks(fire);
            blocker.stopAsync();
            return;
        }
        if (!detector.isActive()) {
            cancelGesture();
            return;
        }
        schedule();
    }

    @Override
    public boolean onContactEvent(int action, float x, float y, float secondX, float secondY,
                                  int pointerCount, long eventTime) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return handleContactEvent(action, x, y, secondX, secondY, pointerCount, eventTime);
        } else {
            main.post(() -> handleContactEvent(action, x, y, secondX, secondY,
                    pointerCount, eventTime));
            return false;
        }
    }

    private boolean handleContactEvent(int action, float x, float y, float secondX, float secondY,
                                       int pointerCount, long eventTime) {
        if (stopped) return false;
        boolean initialTwoFingerDown = pointerCount == 2 && action == MotionEvent.ACTION_DOWN
                && !pointerDown && !detector.isActive();
        if (initialTwoFingerDown) {
            boolean twoFingerEnabled = isTwoFingerPressEnabled();
            if (!canStartInitialTwoFingerCandidate(twoFingerEnabled, controller,
                    x, y, secondX, secondY)) {
                return false;
            }
            twoFingerModeForGesture = true;
            twoFingerPressQualified = true;
            downTime = eventTime;
            downX = x;
            downY = y;
            pointerDown = true;
            latestX = x;
            latestY = y;
            detector.startTwoFinger(x, y, secondX, secondY, eventTime,
                    settings.resolveTwoFingerPressConfirmationTimeoutMillis(),
                    ViewConfiguration.get(context).getScaledTouchSlop());
            twoFingerContactSequence = true;
            if (!allowed()) {
                cancelGesture();
                return false;
            }
            schedule();
            return true;
        }
        if (pointerCount == 2 && action == MotionEvent.ACTION_POINTER_DOWN) {
            if (!twoFingerModeForGesture || twoFingerPressQualified
                    || !canStartTwoFingerCandidate(true, controller, detector, eventTime,
                    x, y, secondX, secondY)) return false;
            detector.startTwoFinger(x, y, secondX, secondY, eventTime,
                    settings.resolveTwoFingerPressConfirmationTimeoutMillis(),
                    ViewConfiguration.get(context).getScaledTouchSlop());
            twoFingerPressQualified = true;
            twoFingerContactSequence = true;
            pointerDown = true;
            latestX = x;
            latestY = y;
            schedule();
            return true;
        }
        if (!twoFingerContactSequence) return false;
        if (pointerCount == 2 && action == MotionEvent.ACTION_MOVE) {
            detector.onTwoPointerEvent(MotionEvent.ACTION_MOVE, x, y, secondX, secondY, pointerCount);
            if (!detector.isActive()) {
                cancelGesture();
            } else {
                if (detector.isFired()
                        && Math.hypot(x - downX, y - downY) > WECHAT_DRAG_DISTANCE_DP
                                * context.getResources().getDisplayMetrics().density) {
                    weChatImageDragIntent = true;
                    if (pendingWeChat != null) dragWeChatPressCandidate(x, y);
                }
                schedule();
            }
            return true;
        }
        if (pointerCount != 2 || action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP) {
            boolean released = action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_POINTER_UP;
            if (released && detector.isFired() && !controller.isActive()) {
                // Match a confirmed single-finger release: let accepted OCR/capture work
                // finish after the Root stream cancels its now-invalid physical drag.
                pointerDown = false;
                detector.cancel();
                main.removeCallbacks(classify);
                main.removeCallbacks(fire);
                blocker.stopAsync();
                finishWeChatPress();
            } else {
                cancelGesture();
            }
            if (pointerCount == 0 || action == MotionEvent.ACTION_CANCEL) {
                twoFingerContactSequence = false;
            }
        }
        return true;
    }

    static boolean canStartTwoFingerCandidate(boolean enabled,
                                              DragShareController controller,
                                              PressTriggerDetector detector,
                                              long eventTime,
                                              float x, float y, float secondX, float secondY) {
        return enabled && controller != null && !controller.isActive()
                && detector != null && detector.isActive() && !detector.isFired()
                && eventTime < detector.deadline()
                && Float.isFinite(x) && Float.isFinite(y)
                && Float.isFinite(secondX) && Float.isFinite(secondY);
    }

    static boolean canStartInitialTwoFingerCandidate(boolean enabled,
                                                     DragShareController controller,
                                                     float x, float y, float secondX, float secondY) {
        return enabled && controller != null && !controller.isActive()
                && Float.isFinite(x) && Float.isFinite(y)
                && Float.isFinite(secondX) && Float.isFinite(secondY);
    }

    /** Prefer the host's actual text/image result over the same press's approximate OCR region. */
    boolean acceptWeChatPortalContent(CapturedContent content) {
        boolean ownsCurrentPress = Looper.myLooper() == Looper.getMainLooper()
                && !stopped && pointerDown && detector.isActive()
                && controller.canContinuePress(downTime);
        boolean isWeChatSource = content != null
                && "com.tencent.mm".equals(content.sourcePackage);
        if (!ownsCurrentPress) {
            OperationTrace.event(operationId(), "portal_capture", "rejected_press_not_owned");
            return false;
        }
        if (!isWeChatSource) {
            OperationTrace.event(operationId(), "portal_capture", "rejected_other_source");
            return false;
        }
        if (!settings.isSharingEnabled(content.isImage())) {
            OperationTrace.event(operationId(), "portal_capture", "rejected_sharing_disabled");
            return false;
        }
        OperationTrace.event(operationId(), "portal_capture", "accepted");
        PendingWeChat pending = pendingWeChat;
        if (!content.isImage() && pending != null && pending.textWindowAccepted) {
            // OCR has already opened this press's text window; do not replace it with
            // a second text result, and keep its screenshot available for an image drag.
            return true;
        }
        boolean imageTakeover = content.isImage() && isWeChatSource && ownsCurrentPress
                && (!twoFingerModeForGesture || detector.isFired())
                && (pending != null || controller.isActive());
        if (imageTakeover && pending != null && pending.textWindowAccepted
                && !dismissWeChatTextWindow()) {
            recycle(content);
            showFailure("无法收起本次分词窗口，图片拖拽未接管");
            return true;
        }
        generation++;
        pendingWeChat = null;
        weChatImageDragIntent = false;
        if (pending != null && pending.ocrComplete) recycle(pending.content);
        if (activeOcrRequest != null) {
            activeOcrRequest.cancel();
            activeOcrRequest = null;
        }
        recycle(cached);
        cached = content;
        lookupRunning = false;
        displayQueued = false;
        if (!twoFingerModeForGesture || detector.isFired()) {
            controller.notePressPipeline(downTime);
        }
        DragShareLog.i(TAG, "press accepted native portal source=com.tencent.mm kind="
                + (content.isImage() ? "image" : "text"));
        if (imageTakeover) {
            cached = null;
            displayQueued = false;
            controller.showContentForPressTakeover(content, latestX, latestY, settings, downTime);
        } else if (detector.isFired()) display();
        else schedule();
        return true;
    }

    private boolean allowed() {
        return !stopped && nodeSession != null && input.isReady()
                && (settings.isSharingEnabled(false) || settings.isSharingEnabled(true))
                && controller.canContinuePress(downTime)
                && context.getSystemService(PowerManager.class).isInteractive()
                && !context.getSystemService(KeyguardManager.class).isKeyguardLocked();
    }

    /** Mode is captured at DOWN; only a valid second contact qualifies a two-finger press. */
    private boolean pressTriggerQualified() {
        return !twoFingerModeForGesture || twoFingerPressQualified;
    }

    private void schedule() {
        main.removeCallbacks(classify);
        main.removeCallbacks(fire);
        if (!pressTriggerQualified()) return;
        if (detector.classificationDeadline() >= 0L) main.postAtTime(classify, detector.classificationDeadline());
        if (detector.isActive() && !detector.isFired()) main.postAtTime(fire, detector.deadline());
    }

    private void classifyPress() {
        if (pressTriggerQualified() && allowed()
                && detector.takeClassification(SystemClock.uptimeMillis()) && cached == null) lookup();
    }

    private void firePress() {
        if (!pressTriggerQualified()) return;
        if (!allowed()) {
            cancelGesture();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (!detector.fire(now)) return;
        confirmedAt = now;
        controller.notePressDiagnostics(downTime, cached == null ? null : cached.sourcePackage,
                "ARMED", "press_duration_confirmed");
        controller.notePressPipeline(downTime);
        DragShareLog.i(TAG, "press confirmed by duration/movement elapsedMs=" + (now - detector.downTime()));
        if (cached != null) display();
        else if (!lookupRunning) lookup();
    }

    private void lookup() {
        if (!pressTriggerQualified()) return;
        SharedRootNodeCapture.Connection session = nodeSession;
        if (session == null) {
            cancelGesture();
            return;
        }
        if (lookupRunning) return;
        lookupRunning = true;
        long request = generation;
        long requestDownTime = downTime;
        float x = downX, y = downY;
        boolean earlyCapture = !detector.isFired();
        Set<String> excluded = new LinkedHashSet<>(settings.accessibilityBlacklistedPackages);
        excluded.addAll(AccessibilityBlacklist.builtInPackages(context));
        AtomicBoolean excludedByPolicy = new AtomicBoolean();
        worker.execute(() -> {
            long lookupStarted = SystemClock.elapsedRealtime();
            OperationTrace.event(OperationTrace.id("press", requestDownTime), "capture", "started");
            try {
                if (request != generation || stopped || !pressTriggerQualified()) {
                    OperationTrace.event(OperationTrace.id("press", requestDownTime), "capture",
                            "late_ignored");
                    return;
                }
                SharedRootNodeCapture.Connection.PressResult result = session.capturePress(x, y, excluded,
                        settings.isSharingEnabled(false), settings.isSharingEnabled(true), earlyCapture, source ->
                                acceptAutomaticSource(request, source, excludedByPolicy));
                CapturedContent content = result.content;
                OperationTrace.event(OperationTrace.id("press", requestDownTime), "capture",
                        content == null ? "empty" : "returned");
                OperationTrace.event(OperationTrace.id("press", requestDownTime), "capture_kind",
                        captureKindOutcome(content));
                DragShareLog.i(TAG, "press capture returned elapsed_ms="
                        + (SystemClock.elapsedRealtime() - lookupStarted)
                        + " kind=" + (content == null ? "none" : content.isImage() ? "image" : "text"));
                if (content == null && result.rejectionReason != null) {
                    DragShareLog.i(TAG, "press lookup rejected reason=" + result.rejectionReason);
                }
                if (result.policyExcluded) excludedByPolicy.set(true);
                main.post(() -> {
                    if (request != generation || !allowed() || !pressTriggerQualified()) {
                        OperationTrace.event(OperationTrace.id("press", requestDownTime), "capture",
                                "late_ignored");
                        DragShareLog.i(TAG, "press payload dropped kind="
                                + (content == null ? "none" : content.isImage() ? "image" : "text")
                                + " reason=" + (stopped ? "stopped"
                                : request != generation ? "superseded" : "source_unavailable")
                                + " elapsedFromDownMs=" + (SystemClock.uptimeMillis() - requestDownTime));
                        recycle(content);
                        return;
                    }
                    lookupRunning = false;
                    if (content == null) {
                        if (detector.isFired() && !excludedByPolicy.get()) {
                            showFailure("当前位置没有可读取的文字或安全识字区域");
                        }
                        cancelGesture();
                        return;
                    }
                    cached = content;
                    controller.notePressDiagnostics(requestDownTime, content.sourcePackage,
                            detector.isFired() ? "ARMED" : "PENDING", "capture_result_available");
                    if (detector.isFired()) display();
                    else schedule();
                });
            } catch (IOException | SecurityException error) {
                OperationTrace.event(OperationTrace.id("press", requestDownTime), "capture",
                        "failed_" + error.getClass().getSimpleName());
                DragShareLog.w(TAG, "press lookup failed error=" + error.getClass().getSimpleName());
                main.post(() -> {
                    if (stopped) return;
                    if (error instanceof SharedRootNodeCapture.ConnectionFailedException || error instanceof SecurityException) {
                        readerFailed(session, "取词连接已中断，正在重连。" + error.getMessage(), false);
                    } else if (request == generation) {
                        if (detector.isFired()) showFailure("本次按压未完成：" + error.getMessage());
                        cancelGesture();
                    }
                });
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                DragShareLog.i(TAG, "press lookup stopped");
            }
        });
    }

    private boolean acceptAutomaticSource(long request, String source, AtomicBoolean excludedByPolicy) {
        if (request != generation || !pressTriggerQualified() || !allowed()) return false;
        if (AccessibilityBlacklist.isBlocked(context, settings, source)
                || GameCapturePolicy.shouldPause(context, settings, source)) {
            excludedByPolicy.set(true);
            return false;
        }
        return request == generation && pressTriggerQualified() && allowed();
    }

    private void display() {
        if (displayQueued) return;
        displayQueued = true;
        CapturedContent content = cached;
        cached = null;
        long request = generation;
        if (request != generation || !allowed() || !pressTriggerQualified()) {
            DragShareLog.i(TAG, "press handoff dropped reason="
                    + (stopped ? "stopped" : "source_unavailable"));
            recycle(content);
            return;
        }
        DragShareLog.i(TAG, "press handoff ready elapsedFromConfirmMs="
                + (SystemClock.uptimeMillis() - confirmedAt));
        if (content.isImage()) openImage(request, content);
        else openText(request, content);
    }

    private void openText(long request, CapturedContent content) {
        String operation = operationId();
        Bundle text = new Bundle();
        text.putString("text", content.text);
        text.putLong("press_confirmed_at", confirmedAt);
        text.putString("before_text", content.beforeText);
        text.putString("after_text", content.afterText);
        text.putInt("x", Math.round(downX));
        text.putInt("y", Math.round(downY));
        OperationTrace.put(text, operation);
        if ("com.android.settings".equals(content.sourcePackage)) {
            try {
                android.content.Intent bridge = new android.content.Intent();
                bridge.setComponent(new android.content.ComponentName(BuildConfig.APPLICATION_ID,
                        BuildConfig.APPLICATION_ID + ".ImageOcrActivity"));
                bridge.putExtra(ImageOcrActivity.EXTRA_BIGBANG_ARGS, text);
                bridge.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(bridge);
                OperationTrace.event(operation, "text_window", "activity_launch_returned");
                DragShareLog.i(TAG, "Settings text panel handed to Activity window");
                Bundle accepted = new Bundle();
                accepted.putBoolean("accepted", true);
                completeHandoff(accepted, request, operation);
            } catch (SecurityException | IllegalStateException error) {
                OperationTrace.event(operation, "text_window", "failed_" + error.getClass().getSimpleName());
                DragShareLog.w(TAG, "cannot open Settings text panel error=" + error.getClass().getSimpleName());
                showFailure("无法打开分词窗口：" + error.getMessage());
                controller.endPressPipeline(downTime);
                blocker.stopAsync();
            }
            return;
        }
        try {
            OperationTrace.event(operation, "text_window", "requested");
            Bundle result = context.getContentResolver().call(
                    ImageStagingClient.BASE_URI, "open_bigbang_window", null, text);
            completeHandoff(result, request, operation);
        } catch (SecurityException | IllegalStateException error) {
            OperationTrace.event(operation, "text_window", "failed_" + error.getClass().getSimpleName());
            DragShareLog.w(TAG, "cannot open press text window error=" + error.getClass().getSimpleName());
            showFailure("无法打开分词窗口：" + error.getMessage());
            controller.endPressPipeline(downTime);
            blocker.stopAsync();
        }
    }

    private void openImage(long request, CapturedContent content) {
        if (content.pressOcrRegion && "com.tencent.mm".equals(content.sourcePackage)) {
            OperationTrace.event(operationId(), "image_decision", "wechat_ocr_region");
            openWeChatOcrDirect(request, content);
            return;
        }
        if (isUnverifiedWeChatImage(content)) {
            rejectWeChatScreenCapture(content, "image_candidate");
            return;
        }
        if (!pointerDown) {
            OperationTrace.event(operationId(), "image_decision", "pointer_released");
            DragShareLog.i(TAG, "image press ended before drag preview was ready");
            recycle(content);
            controller.endPressPipeline(downTime);
            return;
        }
        OperationTrace.event(operationId(), "image_decision", "menu_" + captureKindOutcome(content));
        DragShareLog.i(TAG, "image press handed to drag menu source=" + content.sourcePackage);
        controller.show(content, controller.latestPointerX(), controller.latestPointerY(), settings);
    }

    private void openWeChatOcrDirect(long request, CapturedContent content) {
        if (weChatImageDragIntent) {
            rejectWeChatScreenCapture(content, "drag_before_ocr");
            return;
        }
        OperationTrace.event(operationId(), "wechat_ocr", "started");
        PendingWeChat pending = new PendingWeChat(content, request, operationId());
        pendingWeChat = pending;
        startTouchBlocker(request);
        DragShareLog.i(TAG, "WeChat OCR started elapsedFromConfirmMs="
                + (SystemClock.uptimeMillis() - confirmedAt) + "; gesture decides text or image");
        activeOcrRequest = ocrRecognizer.recognize(context, content.bitmap, (text, error) -> {
            if (pending != pendingWeChat || request != generation || stopped) {
                OperationTrace.event(pending.operation, "image_ocr", "late_ignored");
                recycle(content);
                return;
            }
            activeOcrRequest = null;
            pending.ocrComplete = true;
            pending.text = text;
            pending.error = error;
            DragShareLog.i(TAG, "WeChat OCR finished elapsedMs="
                    + (SystemClock.elapsedRealtime() - pending.started)
                    + " characters=" + (text == null ? 0 : text.length()));
            deliverWeChatText();
        });
    }

    private void dragWeChatPressCandidate(float x, float y) {
        PendingWeChat pending = pendingWeChat;
        if (pending.ocrComplete && pending.textWindowAccepted
                && settings.isSharingEnabled(false)
                && pending.text != null && !pending.text.isBlank()) {
            if (pending.textWindowAccepted && !dismissWeChatTextWindow()) {
                OperationTrace.event(pending.operation, "wechat_drag", "rejected_window_not_dismissed");
                showFailure("无法收起本次分词窗口，文字拖拽未接管");
                return;
            }
            CapturedContent text = CapturedContent.text(
                    pending.text, pending.content.sourcePackage, pending.content.sourceBounds);
            pendingWeChat = null;
            generation++;
            weChatImageDragIntent = false;
            blocker.stopAsync();
            recycle(pending.content);
            DragShareLog.i(TAG, "WeChat OCR text moved to drag menu elapsedFromConfirmMs="
                    + (SystemClock.uptimeMillis() - confirmedAt));
            OperationTrace.event(pending.operation, "wechat_drag", "ocr_text_takeover");
            // The takeover API accepts either payload kind; it restores this Root press
            // after the BigBang text window handed it off.
            controller.showContentForPressTakeover(text, x, y, settings, downTime);
            return;
        }
        if (pending.textWindowAccepted && !dismissWeChatTextWindow()) {
            OperationTrace.event(pending.operation, "wechat_drag", "rejected_window_not_dismissed");
            showFailure("无法收起本次分词窗口，图片拖拽未接管");
            return;
        }
        CapturedContent source = pending.content;
        if (isUnverifiedWeChatImage(source)) {
            pendingWeChat = null;
            generation++;
            weChatImageDragIntent = false;
            if (!pending.ocrComplete && activeOcrRequest != null) {
                activeOcrRequest.cancel();
                activeOcrRequest = null;
            }
            blocker.stopAsync();
            rejectWeChatScreenCapture(source, "ocr_region_drag");
            return;
        }
        android.graphics.Bitmap bitmap = pending.ocrComplete
                ? source.bitmap
                : source.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false);
        pendingWeChat = null;
        generation++;
        weChatImageDragIntent = false;
        if (!pending.ocrComplete && activeOcrRequest != null) {
            activeOcrRequest.cancel();
            activeOcrRequest = null;
        }
        blocker.stopAsync();
        if (bitmap == null) {
            OperationTrace.event(pending.operation, "wechat_drag", "image_copy_failed");
            if (pending.ocrComplete) recycle(source);
            controller.endPressPipeline(downTime);
            showFailure("无法准备拖拽图片");
            return;
        }
        CapturedContent image = CapturedContent.image(bitmap, source.sourcePackage,
                source.sourceBounds, true);
        DragShareLog.i(TAG, "WeChat press moved to image drag elapsedFromConfirmMs="
                + (SystemClock.uptimeMillis() - confirmedAt));
        controller.showContentForPressTakeover(image, x, y, settings, downTime);
    }

    private void deliverWeChatText() {
        PendingWeChat pending = pendingWeChat;
        if (pending == null || !pending.ocrComplete) return;
        if (pending.textDelivered) {
            finishWeChatCandidateIfReleased(pending);
            return;
        }
        if (pending.error != null) {
            if (pointerDown) return;
            pendingWeChat = null;
            recycle(pending.content);
            DragShareLog.w(TAG, "WeChat OCR direct failed error=" + pending.error.getClass().getSimpleName());
            showFailure("微信文字识别失败：" + pending.error.getMessage());
            controller.endPressPipeline(downTime);
            blocker.stopAsync();
            return;
        }
        if (pending.text == null || pending.text.isBlank()) {
            if (pointerDown) return;
            pendingWeChat = null;
            recycle(pending.content);
            showFailure("这次按压没有识别出文字");
            controller.endPressPipeline(downTime);
            blocker.stopAsync();
            return;
        }
        pending.textDelivered = true;
        Bundle recognized = new Bundle();
        recognized.putString("text", pending.text);
        recognized.putLong("press_confirmed_at", confirmedAt);
        recognized.putInt("x", Math.round(downX));
        recognized.putInt("y", Math.round(downY));
        OperationTrace.put(recognized, pending.operation);
        try {
            OperationTrace.event(pending.operation, "text_window", "requested");
            Bundle result = context.getContentResolver().call(ImageStagingClient.BASE_URI,
                    BigBangOverlayLauncher.METHOD_OPEN, null, recognized);
            completeHandoff(result, pending.request, pending.operation);
            if (pending.textWindowAccepted) {
                DragShareLog.i(TAG, "WeChat OCR text window accepted; image candidate retained until root release");
            }
        } catch (SecurityException | IllegalStateException | IllegalArgumentException failure) {
            OperationTrace.event(pending.operation, "text_window", "failed_" + failure.getClass().getSimpleName());
            DragShareLog.w(TAG, "cannot open WeChat text panel error=" + failure.getClass().getSimpleName());
            showFailure("无法显示微信识别结果：" + failure.getMessage());
            controller.endPressPipeline(downTime);
            blocker.stopAsync();
        }
        finishWeChatCandidateIfReleased(pending);
    }

    private void finishWeChatPress() {
        PendingWeChat pending = pendingWeChat;
        if (pending != null && pending.ocrComplete) deliverWeChatText();
    }

    private void finishWeChatCandidateIfReleased(PendingWeChat pending) {
        if (!pointerDown && pendingWeChat == pending) {
            pendingWeChat = null;
            recycle(pending.content);
        }
    }

    private boolean dismissWeChatTextWindow() {
        Bundle request = new Bundle();
        request.putLong("press_confirmed_at", confirmedAt);
        OperationTrace.put(request, operationId());
        try {
            Bundle result = context.getContentResolver().call(ImageStagingClient.BASE_URI,
                    BigBangOverlayLauncher.METHOD_DISMISS_PRESS, null, request);
            return result != null && result.getBoolean("accepted");
        } catch (SecurityException | IllegalStateException | IllegalArgumentException error) {
            DragShareLog.w(TAG, "cannot dismiss this WeChat press window error=" + error.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean isUnverifiedWeChatImage(CapturedContent content) {
        return content != null && "com.tencent.mm".equals(content.sourcePackage)
                && content.isImage()
                && content.imageOrigin != CapturedContent.ImageOrigin.PORTAL_MEDIA
                && !content.isWeChatThumbnail();
    }

    private static String captureKindOutcome(CapturedContent content) {
        if (content == null) return "unknown";
        String source = "com.tencent.mm".equals(content.sourcePackage) ? "wechat" : "other";
        if (!content.isImage()) return source + "_text";
        switch (content.imageOrigin) {
            case PORTAL_MEDIA: return source + "_image_portal_media";
            case NODE_THUMBNAIL: return source + "_image_node_thumbnail";
            case SCREEN_CAPTURE: return source + "_image_screen_capture";
            default: return source + "_image_unknown";
        }
    }

    private void rejectWeChatScreenCapture(CapturedContent content, String stage) {
        OperationTrace.event(operationId(), "image_drag", "rejected_" + stage + "_"
                + (content == null ? "unknown" : content.imageOrigin.name().toLowerCase(java.util.Locale.ROOT)));
        DragShareLog.i(TAG, "WeChat image drag rejected source="
                + (content == null ? "unknown" : content.imageOrigin.name())
                + " stage=" + stage);
        recycle(content);
        showFailure("没有取得原图，也未能识别图片边界，请通过传送门选图");
        controller.endPressPipeline(downTime);
        blocker.stopAsync();
    }

    private void completeHandoff(Bundle result, long request, String operation) {
        if (result != null && result.getBoolean("accepted")) {
            OperationTrace.event(operation, "text_window", "accepted");
            PendingWeChat pending = pendingWeChat;
            if (pending != null && pending.request == request) pending.textWindowAccepted = true;
            startTouchBlocker(request);
            controller.handoffToPress(downTime);
        } else {
            OperationTrace.event(operation, "text_window", "rejected");
            DragShareLog.w(TAG, "press window request was not accepted");
            controller.endPressPipeline(downTime);
            blocker.stopAsync();
        }
    }

    private void startTouchBlocker(long request) {
        if (pointerDown && pressTriggerQualified()) {
            controller.notePressDiagnostics(downTime, null, null, "press_background_block_requested");
            blocker.startAsync(() -> !stopped && request == generation && pointerDown);
        }
    }

    private static void recycle(CapturedContent content) {
        if (content != null && content.isImage()) content.bitmap.recycle();
    }

    void cancelGesture() {
        if (pointerDown || detector.isActive() || cached != null || pendingWeChat != null || lookupRunning) {
            controller.notePressDiagnostics(downTime, null, null, "press_detector_cleanup");
        }
        controller.endPressPipeline(downTime);
        generation++;
        pointerDown = false;
        PendingWeChat pending = pendingWeChat;
        pendingWeChat = null;
        weChatImageDragIntent = false;
        if (pending != null && pending.ocrComplete) recycle(pending.content);
        if (activeOcrRequest != null) {
            activeOcrRequest.cancel();
            activeOcrRequest = null;
        }
        main.removeCallbacks(classify);
        main.removeCallbacks(fire);
        detector.cancel();
        twoFingerModeForGesture = false;
        twoFingerPressQualified = false;
        lookupRunning = false;
        displayQueued = false;
        recycle(cached);
        cached = null;
        blocker.stopAsync();
    }

    private void showFailure(String reason) {
        Toast.makeText(context, reason, Toast.LENGTH_LONG).show();
    }

    private void readerFailed(SharedRootNodeCapture.Connection failed, String reason, boolean reconnectNow) {
        if (nodeSession != failed) {
            if (failed != null) failed.close();
            return;
        }
        nodeSession = null;
        cancelGesture();
        if (failed != null) {
            recordRootEvent(failed.connectionAttemptId(), "release", -1L);
            failed.unlinkFromDeath(leaseDeath);
            failed.close();
        }
        leaseDeath = null;
        ModuleActivation.reportPressReader(context, false, reason);
        if (reconnectNow) {
            if (failed != null) recordRootEvent(failed.connectionAttemptId(), "retry_scheduled", 0L);
            openReader();
        }
        else scheduleReaderRetry();
    }

    private void scheduleReaderRetry() {
        if (stopped) return;
        main.removeCallbacks(readerRetry);
        readerRetryDelayMs = readerRetryDelayMs <= 0L
                ? READER_RETRY_FIRST_MS
                : Math.min(readerRetryDelayMs * 2L, READER_RETRY_MAX_MS);
        recordRootEvent(readerAttemptId, "retry_scheduled", readerRetryDelayMs);
        DragShareLog.i(TAG, "press reader retry scheduled in_ms=" + readerRetryDelayMs);
        if (readerRetryDelayMs >= READER_RETRY_MAX_MS && !readerFailureShown) {
            readerFailureShown = true;
            showFailure("取词服务持续不可用，正在继续重连。可打开模块设置查看连接状态。");
        }
        main.postDelayed(readerRetry, readerRetryDelayMs);
    }

    void stop() {
        Runnable stop = () -> {
            stopped = true;
            PortalPressSettings.Subscription observer = portalPressSubscription;
            portalPressSubscription = null;
            if (observer != null) observer.close();
            if (input != null) {
                input.clearContactListener(this);
                input.setTwoFingerPressEnabled(false);
            }
            twoFingerContactSequence = false;
            main.removeCallbacks(readerRetry);
            cancelGesture();
            SharedRootNodeCapture.Connection session = nodeSession;
            nodeSession = null;
            ModuleActivation.reportPressReader(context, false, "");
            if (session != null) {
                recordRootEvent(session.connectionAttemptId(), "release", -1L);
                session.unlinkFromDeath(leaseDeath);
                session.close();
            }
            leaseDeath = null;
            blocker.close();
            worker.shutdownNow();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) stop.run();
        else main.post(stop);
    }

    private void recordRootEvent(String attemptId, String outcome, long retryIntervalMs) {
        if (retryIntervalMs >= 0L) RootConnectionDiagnostics.retry(attemptId, retryIntervalMs);
        else RootConnectionDiagnostics.event(attemptId, outcome);
        ModuleActivation.reportRootConnectionEvent(context, attemptId, outcome, retryIntervalMs);
    }
}

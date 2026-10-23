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
import android.widget.Toast;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** One press pipeline for both hosts: classify once, then use the confirmed gesture's result. */
final class PressCaptureSource {
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
    private final Runnable classify = this::classifyPress;
    private final Runnable fire = this::firePress;
    private volatile SharedRootNodeCapture.Connection nodeSession;
    private volatile boolean stopped;
    private long readerRetryDelayMs;
    private boolean readerFailureShown;
    private boolean readerOpening;
    private IBinder.DeathRecipient leaseDeath;
    private final Runnable readerRetry = () -> {
        if (stopped || nodeSession != null) return;
        DragShareLog.i(TAG, "press reader retry started");
        openReader();
    };
    private AicrOcrClient.Request activeOcrRequest;
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

    private static final class PendingWeChat {
        final CapturedContent content;
        final long request;
        final long started;
        boolean ocrComplete;
        String text;
        Exception error;
        boolean textDelivered;
        boolean textWindowAccepted;

        PendingWeChat(CapturedContent content, long request) {
            this.content = content;
            this.request = request;
            this.started = SystemClock.elapsedRealtime();
        }
    }

    PressCaptureSource(
            Context context,
            RootTouchSource input,
            DragShareController controller,
            DragShareSettings settings) {
        this.context = context;
        this.input = input;
        this.controller = controller;
        this.settings = settings;
        blocker = new BackgroundTouchBlocker(context);
        ModuleActivation.reportPressReader(context, false, "");
        openReader();
    }

    /** Start a lease attempt on the serial worker; death-triggered attempts start immediately. */
    private void openReader() {
        if (stopped || nodeSession != null || readerOpening) return;
        readerOpening = true;
        worker.execute(() -> {
            long connectionStarted = SystemClock.elapsedRealtime();
            try {
                SharedRootNodeCapture.Connection ready = SharedRootNodeCapture.open(context, false);
                DragShareLog.i(TAG, "connection stage=press_reader_ready elapsed_ms="
                        + (SystemClock.elapsedRealtime() - connectionStarted));
                main.post(() -> {
                    readerOpening = false;
                    if (stopped) {
                        ready.close();
                        return;
                    }
                    IBinder.DeathRecipient death = () -> main.post(() -> {
                        if (stopped || nodeSession != ready) return;
                        DragShareLog.w(TAG, "press reader lease died; reconnecting to the module process");
                        readerFailed(ready, "取词服务进程已退出，正在重连。", true);
                    });
                    if (!ready.linkToDeath(death)) {
                        ready.close();
                        readerFailed(null, "取词连接已失效，正在重连。", false);
                        return;
                    }
                    nodeSession = ready;
                    leaseDeath = death;
                    main.removeCallbacks(readerRetry);
                    readerRetryDelayMs = 0L;
                    readerFailureShown = false;
                    ModuleActivation.reportPressReader(context, true, "");
                    DragShareLog.i(TAG, "press reader ready; idle without reading nodes");
                });
            } catch (IOException | SecurityException | IllegalStateException error) {
                DragShareLog.w(TAG, "connection stage=press_reader_failed elapsed_ms="
                        + (SystemClock.elapsedRealtime() - connectionStarted));
                DragShareLog.w(TAG, "cannot prepare press reader", error);
                main.post(() -> {
                    readerOpening = false;
                    if (!stopped) readerFailed(null, "取词连接未建立，正在重连。" + error.getMessage(), false);
                });
            }
        });
    }

    void updateSettings(DragShareSettings updated) {
        DragShareSettings previous = settings;
        settings = updated;
        if (previous.pauseAutomaticInGames != updated.pauseAutomaticInGames
                || !previous.manualGamePackages.equals(updated.manualGamePackages)
                || !previous.accessibilityBlacklistedPackages.equals(updated.accessibilityBlacklistedPackages)) {
            if (Looper.myLooper() == Looper.getMainLooper()) cancelGesture();
            else main.post(this::cancelGesture);
        }
    }

    void onPointerEvent(int action, float x, float y, long time) {
        if (Looper.myLooper() == Looper.getMainLooper()) handlePointer(action, x, y, time);
        else main.post(() -> handlePointer(action, x, y, time));
    }

    private void handlePointer(int action, float x, float y, long time) {
        if (stopped) return;
        latestX = x;
        latestY = y;
        if (action == MotionEvent.ACTION_DOWN) {
            cancelGesture();
            downTime = time;
            detector.start(x, y, time, settings.resolvePressConfirmationTimeoutMillis());
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

    /** Prefer the host's actual text/image result over the same press's approximate OCR region. */
    boolean acceptWeChatPortalContent(CapturedContent content) {
        boolean ownsCurrentPress = Looper.myLooper() == Looper.getMainLooper()
                && !stopped && pointerDown && detector.isActive()
                && controller.canContinuePress(downTime);
        boolean isWeChatSource = content != null
                && "com.tencent.mm".equals(content.sourcePackage);
        if (!ownsCurrentPress || !isWeChatSource
                || !settings.isSharingEnabled(content.isImage())) return false;
        PendingWeChat pending = pendingWeChat;
        if (!content.isImage() && pending != null && pending.textWindowAccepted) {
            // OCR has already opened this press's text window; do not replace it with
            // a second text result, and keep its screenshot available for an image drag.
            return true;
        }
        boolean imageTakeover = content.isImage() && isWeChatSource && ownsCurrentPress
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
        controller.notePressPipeline(downTime);
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

    private void schedule() {
        main.removeCallbacks(classify);
        main.removeCallbacks(fire);
        if (detector.classificationDeadline() >= 0L) main.postAtTime(classify, detector.classificationDeadline());
        if (detector.isActive() && !detector.isFired()) main.postAtTime(fire, detector.deadline());
    }

    private void classifyPress() {
        if (allowed() && detector.takeClassification(SystemClock.uptimeMillis()) && cached == null) lookup();
    }

    private void firePress() {
        if (!allowed()) {
            cancelGesture();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (!detector.fire(now)) return;
        confirmedAt = now;
        controller.notePressPipeline(downTime);
        DragShareLog.i(TAG, "press confirmed by duration/movement elapsedMs=" + (now - detector.downTime()));
        if (cached != null) display();
        else if (!lookupRunning) lookup();
    }

    private void lookup() {
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
            try {
                if (request != generation || stopped) return;
                SharedRootNodeCapture.Connection.PressResult result = session.capturePress(x, y, excluded,
                        settings.isSharingEnabled(false), settings.isSharingEnabled(true), earlyCapture, source ->
                                acceptAutomaticSource(request, source, excludedByPolicy));
                CapturedContent content = result.content;
                DragShareLog.i(TAG, "press capture returned elapsed_ms="
                        + (SystemClock.elapsedRealtime() - lookupStarted)
                        + " kind=" + (content == null ? "none" : content.isImage() ? "image" : "text"));
                if (content == null && result.rejectionReason != null) {
                    DragShareLog.i(TAG, "press lookup rejected reason=" + result.rejectionReason);
                }
                if (result.policyExcluded) excludedByPolicy.set(true);
                main.post(() -> {
                    if (request != generation || !allowed()) {
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
                    if (detector.isFired()) display();
                    else schedule();
                });
            } catch (IOException | SecurityException error) {
                DragShareLog.w(TAG, "press lookup failed", error);
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
        if (request != generation || !allowed()) return false;
        if (AccessibilityBlacklist.isBlocked(context, settings, source)
                || GameCapturePolicy.shouldPause(context, settings, source)) {
            excludedByPolicy.set(true);
            return false;
        }
        return request == generation && allowed();
    }

    private void display() {
        if (displayQueued) return;
        displayQueued = true;
        CapturedContent content = cached;
        cached = null;
        long request = generation;
        if (request != generation || !allowed()) {
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
        Bundle text = new Bundle();
        text.putString("text", content.text);
        text.putLong("press_confirmed_at", confirmedAt);
        text.putString("before_text", content.beforeText);
        text.putString("after_text", content.afterText);
        text.putInt("x", Math.round(downX));
        text.putInt("y", Math.round(downY));
        if ("com.android.settings".equals(content.sourcePackage)) {
            try {
                android.content.Intent bridge = new android.content.Intent();
                bridge.setComponent(new android.content.ComponentName(BuildConfig.APPLICATION_ID,
                        BuildConfig.APPLICATION_ID + ".ImageOcrActivity"));
                bridge.putExtra(ImageOcrActivity.EXTRA_BIGBANG_ARGS, text);
                bridge.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(bridge);
                DragShareLog.i(TAG, "Settings text panel handed to Activity window");
                Bundle accepted = new Bundle();
                accepted.putBoolean("accepted", true);
                completeHandoff(accepted, request);
            } catch (SecurityException | IllegalStateException error) {
                DragShareLog.w(TAG, "cannot open Settings text panel", error);
                showFailure("无法打开分词窗口：" + error.getMessage());
                controller.endPressPipeline(downTime);
                blocker.stopAsync();
            }
            return;
        }
        try {
            Bundle result = context.getContentResolver().call(
                    ImageStagingClient.BASE_URI, "open_bigbang_window", null, text);
            completeHandoff(result, request);
        } catch (SecurityException | IllegalStateException error) {
            DragShareLog.w(TAG, "cannot open press text window", error);
            showFailure("无法打开分词窗口：" + error.getMessage());
            controller.endPressPipeline(downTime);
            blocker.stopAsync();
        }
    }

    private void openImage(long request, CapturedContent content) {
        if (content.pressOcrRegion && "com.tencent.mm".equals(content.sourcePackage)) {
            openWeChatOcrDirect(request, content);
            return;
        }
        if (!pointerDown) {
            DragShareLog.i(TAG, "image press ended before drag preview was ready");
            recycle(content);
            controller.endPressPipeline(downTime);
            return;
        }
        DragShareLog.i(TAG, "image press handed to drag menu source=" + content.sourcePackage);
        controller.show(content, controller.latestPointerX(), controller.latestPointerY(), settings);
    }

    private void openWeChatOcrDirect(long request, CapturedContent content) {
        if (weChatImageDragIntent) {
            weChatImageDragIntent = false;
            CapturedContent image = CapturedContent.image(content.bitmap,
                    content.sourcePackage, content.sourceBounds, true);
            DragShareLog.i(TAG, "WeChat press moved to image before OCR started");
            controller.showContentForPressTakeover(image, latestX, latestY, settings, downTime);
            return;
        }
        PendingWeChat pending = new PendingWeChat(content, request);
        pendingWeChat = pending;
        startTouchBlocker(request);
        DragShareLog.i(TAG, "WeChat OCR started elapsedFromConfirmMs="
                + (SystemClock.uptimeMillis() - confirmedAt) + "; gesture decides text or image");
        activeOcrRequest = AicrOcrClient.recognize(context, content.bitmap, (text, error) -> {
            if (pending != pendingWeChat || request != generation || stopped) {
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
            // The takeover API accepts either payload kind; it restores this Root press
            // after the BigBang text window handed it off.
            controller.showContentForPressTakeover(text, x, y, settings, downTime);
            return;
        }
        if (pending.textWindowAccepted && !dismissWeChatTextWindow()) {
            showFailure("无法收起本次分词窗口，图片拖拽未接管");
            return;
        }
        CapturedContent source = pending.content;
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
            DragShareLog.w(TAG, "WeChat OCR direct failed", pending.error);
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
        try {
            Bundle result = context.getContentResolver().call(ImageStagingClient.BASE_URI,
                    BigBangOverlayLauncher.METHOD_OPEN, null, recognized);
            completeHandoff(result, pending.request);
            if (pending.textWindowAccepted) {
                DragShareLog.i(TAG, "WeChat OCR text window accepted; image candidate retained until root release");
            }
        } catch (SecurityException | IllegalStateException | IllegalArgumentException failure) {
            DragShareLog.w(TAG, "cannot open WeChat text panel", failure);
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
        try {
            Bundle result = context.getContentResolver().call(ImageStagingClient.BASE_URI,
                    BigBangOverlayLauncher.METHOD_DISMISS_PRESS, null, request);
            return result != null && result.getBoolean("accepted");
        } catch (SecurityException | IllegalStateException | IllegalArgumentException error) {
            DragShareLog.w(TAG, "cannot dismiss this WeChat press window", error);
            return false;
        }
    }

    private void completeHandoff(Bundle result, long request) {
        if (result != null && result.getBoolean("accepted")) {
            PendingWeChat pending = pendingWeChat;
            if (pending != null && pending.request == request) pending.textWindowAccepted = true;
            startTouchBlocker(request);
            controller.handoffToPress(downTime);
        } else {
            DragShareLog.w(TAG, "press window request was not accepted");
            controller.endPressPipeline(downTime);
            blocker.stopAsync();
        }
    }

    private void startTouchBlocker(long request) {
        if (pointerDown) {
            blocker.startAsync(() -> !stopped && request == generation && pointerDown);
        }
    }

    private static void recycle(CapturedContent content) {
        if (content != null && content.isImage()) content.bitmap.recycle();
    }

    void cancelGesture() {
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
            failed.unlinkFromDeath(leaseDeath);
            failed.close();
        }
        leaseDeath = null;
        ModuleActivation.reportPressReader(context, false, reason);
        if (reconnectNow) openReader();
        else scheduleReaderRetry();
    }

    private void scheduleReaderRetry() {
        if (stopped) return;
        main.removeCallbacks(readerRetry);
        readerRetryDelayMs = readerRetryDelayMs <= 0L
                ? READER_RETRY_FIRST_MS
                : Math.min(readerRetryDelayMs * 2L, READER_RETRY_MAX_MS);
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
            main.removeCallbacks(readerRetry);
            cancelGesture();
            SharedRootNodeCapture.Connection session = nodeSession;
            nodeSession = null;
            ModuleActivation.reportPressReader(context, false, "");
            if (session != null) {
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
}

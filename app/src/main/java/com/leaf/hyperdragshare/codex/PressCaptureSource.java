package com.leaf.hyperdragshare.codex;

import android.app.KeyguardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
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
    private AicrOcrClient.Request activeOcrRequest;
    private volatile long generation;
    private volatile boolean stopped;
    private volatile boolean pointerDown;
    private volatile long downTime = -1L;
    private boolean lookupRunning;
    private boolean displayQueued;
    private long confirmedAt;
    private CapturedContent cached;
    private float downX;
    private float downY;

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
        worker.execute(() -> {
            long connectionStarted = SystemClock.elapsedRealtime();
            try {
                SharedRootNodeCapture.Connection ready = SharedRootNodeCapture.open(context, false);
                DragShareLog.i(TAG, "connection stage=press_reader_ready elapsed_ms="
                        + (SystemClock.elapsedRealtime() - connectionStarted));
                main.post(() -> {
                    if (stopped) ready.close();
                    else {
                        nodeSession = ready;
                        ModuleActivation.reportPressReader(context, true, "");
                        DragShareLog.i(TAG, "press reader ready; idle without reading nodes");
                    }
                });
            } catch (IOException | SecurityException | IllegalStateException error) {
                DragShareLog.w(TAG, "connection stage=press_reader_failed elapsed_ms="
                        + (SystemClock.elapsedRealtime() - connectionStarted));
                DragShareLog.w(TAG, "cannot prepare press reader", error);
                main.post(() -> { if (!stopped) readerFailed(null, "取词连接未建立，请重载作用域。" + error.getMessage()); });
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
        if (action == MotionEvent.ACTION_DOWN) {
            cancelGesture();
            downTime = time;
            detector.start(x, y, time, settings.resolvePressConfirmationTimeoutMillis());
            pointerDown = true;
            downX = x;
            downY = y;
        }
        if (!allowed()) {
            cancelGesture();
            return;
        }
        boolean hadFired = detector.isFired();
        if (action != MotionEvent.ACTION_DOWN) detector.onPointerEvent(action, x, y, 1);
        if (action == MotionEvent.ACTION_UP) pointerDown = false;
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
        if (allowed() && detector.takeClassification(SystemClock.uptimeMillis())) lookup();
    }

    private void firePress() {
        if (!allowed()) {
            cancelGesture();
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (!detector.fire(now)) return;
        confirmedAt = now;
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
                        readerFailed(session, "取词连接已中断，请重载作用域。" + error.getMessage());
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
        try {
            Bundle result = context.getContentResolver().call(
                    ImageStagingClient.BASE_URI, "open_bigbang_window", null, text);
            completeHandoff(result, request);
        } catch (SecurityException | IllegalStateException error) {
            DragShareLog.w(TAG, "cannot open press text window", error);
            showFailure("无法打开分词窗口：" + error.getMessage());
            blocker.stopAsync();
        }
    }

    private void openImage(long request, CapturedContent content) {
        if (content.pressOcrRegion && "com.tencent.mm".equals(content.sourcePackage)) {
            openWeChatOcrDirect(request, content);
            return;
        }
        ImageStagingClient.stage(context, content.bitmap, new ImageStagingClient.Callback() {
            @Override public void onStaged(Uri uri) {
                recycle(content);
                main.post(() -> {
                    if (request != generation || !allowed()) return;
                    Bundle image = new Bundle();
                    image.putParcelable("image", uri);
                    image.putLong("press_confirmed_at", confirmedAt);
                    try {
                        Bundle result = context.getContentResolver().call(ImageStagingClient.BASE_URI,
                                ImageOcrOverlay.METHOD_RECOGNIZE, null, image);
                        completeHandoff(result, request);
                    } catch (SecurityException | IllegalStateException error) {
                        DragShareLog.w(TAG, "cannot open press image window", error);
                        showFailure("无法打开图片识字：" + error.getMessage());
                        blocker.stopAsync();
                    }
                });
            }
            @Override public void onFailure(Throwable error) {
                recycle(content);
                DragShareLog.w(TAG, "cannot stage press image", error);
                main.post(() -> {
                    if (request == generation && !stopped) {
                        showFailure("无法暂存识字图片：" + error.getMessage());
                        blocker.stopAsync();
                    }
                });
            }
        });
    }

    private void openWeChatOcrDirect(long request, CapturedContent content) {
        long panelRequestedAt = SystemClock.elapsedRealtime();
        long pressId = SystemClock.elapsedRealtimeNanos();
        Bundle pending = new Bundle();
        pending.putBoolean("ocr_pending", true);
        pending.putLong("press_id", pressId);
        pending.putLong("press_confirmed_at", confirmedAt);
        pending.putInt("x", Math.round(downX));
        pending.putInt("y", Math.round(downY));
        try {
            Bundle result = context.getContentResolver().call(
                    ImageStagingClient.BASE_URI, BigBangOverlayLauncher.METHOD_OPEN, null, pending);
            if (result == null || !result.getBoolean("accepted")) {
                recycle(content);
                blocker.stopAsync();
                showFailure("无法打开微信识字窗口");
                return;
            }
            startTouchBlocker(request);
            controller.handoffToPress(downTime);
        } catch (SecurityException | IllegalStateException | IllegalArgumentException error) {
            recycle(content);
            blocker.stopAsync();
            DragShareLog.w(TAG, "cannot open WeChat OCR panel", error);
            showFailure("无法打开微信识字窗口：" + error.getMessage());
            return;
        }

        long started = SystemClock.elapsedRealtime();
        DragShareLog.i(TAG, "WeChat OCR direct start elapsedFromConfirmMs="
                + (SystemClock.uptimeMillis() - confirmedAt) + " panelRequestMs=" + (started - panelRequestedAt)
                + "; image staging and intermediate window skipped");
        activeOcrRequest = AicrOcrClient.recognize(context, content.bitmap, (text, error) -> {
            if (!content.bitmap.isRecycled()) content.bitmap.recycle();
            activeOcrRequest = null;
            if (request != generation || stopped) {
                abortWeChatOcrPanel(pressId);
                return;
            }
            if (error != null) {
                DragShareLog.w(TAG, "WeChat OCR direct failed", error);
                abortWeChatOcrPanel(pressId);
                showFailure("微信文字识别失败：" + error.getMessage());
                blocker.stopAsync();
                return;
            }
            if (request != generation || stopped) {
                abortWeChatOcrPanel(pressId);
                blocker.stopAsync();
                return;
            }
            Bundle recognized = new Bundle();
            recognized.putLong("press_id", pressId);
            recognized.putString("text", text);
            DragShareLog.i(TAG, "WeChat OCR finished elapsedMs="
                    + (SystemClock.elapsedRealtime() - started)
                    + " characters=" + text.length());
            try {
                Bundle update = context.getContentResolver().call(ImageStagingClient.BASE_URI,
                        BigBangOverlayLauncher.METHOD_COMPLETE_OCR, null, recognized);
                if (update == null || !update.getBoolean("accepted")) {
                    abortWeChatOcrPanel(pressId);
                    showFailure("微信识别结果窗口已关闭");
                }
            } catch (SecurityException | IllegalStateException | IllegalArgumentException failure) {
                DragShareLog.w(TAG, "cannot update WeChat OCR panel", failure);
                abortWeChatOcrPanel(pressId);
                showFailure("无法显示微信识别结果：" + failure.getMessage());
            }
        });
    }

    private void abortWeChatOcrPanel(long pressId) {
        Bundle request = new Bundle();
        request.putLong("press_id", pressId);
        try {
            context.getContentResolver().call(ImageStagingClient.BASE_URI,
                    BigBangOverlayLauncher.METHOD_ABORT_OCR, null, request);
        } catch (SecurityException | IllegalStateException error) {
            DragShareLog.w(TAG, "cannot close WeChat OCR panel", error);
        }
    }

    private void completeHandoff(Bundle result, long request) {
        if (result != null && result.getBoolean("accepted")) {
            startTouchBlocker(request);
            controller.handoffToPress(downTime);
        } else {
            DragShareLog.w(TAG, "press window request was not accepted");
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
        generation++;
        pointerDown = false;
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

    private void readerFailed(SharedRootNodeCapture.Connection failed, String reason) {
        if (nodeSession != failed) return;
        nodeSession = null;
        cancelGesture();
        if (failed != null) failed.close();
        ModuleActivation.reportPressReader(context, false, reason);
        showFailure(reason);
    }

    void stop() {
        Runnable stop = () -> {
            stopped = true;
            cancelGesture();
            SharedRootNodeCapture.Connection session = nodeSession;
            nodeSession = null;
            ModuleActivation.reportPressReader(context, false, "");
            if (session != null) session.close();
            blocker.close();
            worker.shutdownNow();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) stop.run();
        else main.post(stop);
    }
}

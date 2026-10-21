package com.leaf.hyperdragshare.codex;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.net.Uri;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Optional calibrated area trigger in the Portal process; ordinary long presses are untouched. */
final class PortalThumbCaptureSource {
    private static final String TAG = "DragShare/ThumbRoot";
    private final Context context;
    private final RootTouchSource input;
    private final DragShareController controller;
    private final DragShareSettings settings;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final BackgroundTouchBlocker blocker;
    private final ContactAreaTriggerDetector detector;
    private final Runnable confirm = this::confirmContact;
    private volatile long generation;
    private float x;
    private float y;
    private volatile boolean stopped;
    private volatile SharedRootNodeCapture.Connection nodeSession;
    private boolean busy;
    private volatile boolean pointerDown;
    private long captureDeadline;

    PortalThumbCaptureSource(Context context, RootTouchSource input, DragShareController controller,
                             DragShareSettings settings, float threshold) {
        this.context = context;
        this.input = input;
        this.controller = controller;
        this.settings = settings;
        blocker = new BackgroundTouchBlocker(context);
        detector = new ContactAreaTriggerDetector(threshold,
                context.getResources().getDisplayMetrics().density * 16f, 180L);
        worker.execute(() -> {
            try {
                SharedRootNodeCapture.Connection ready = SharedRootNodeCapture.open(context, false);
                main.post(() -> {
                    if (stopped) ready.close();
                    else {
                        nodeSession = ready;
                        DragShareLog.i(TAG, "Root node process warmed; waiting for an area gesture");
                    }
                });
            } catch (IOException | SecurityException error) {
                DragShareLog.w(TAG, "cannot prepare Root node process", error);
                main.post(() -> {
                    if (!stopped) showFailure("面积取词服务连接失败，请重载传送门后重试");
                });
            }
        });
    }

    void onPointerEvent(int action, float px, float py, long time, int major, int width) {
        main.post(() -> {
            if (stopped) return;
            x = px;
            y = py;
            if (action == MotionEvent.ACTION_DOWN) {
                generation++;
                pointerDown = true;
                captureDeadline = time + ViewConfiguration.getLongPressTimeout();
                blocker.stop();
            }
            main.removeCallbacks(confirm);
            if (action == MotionEvent.ACTION_CANCEL || !allowed()) {
                generation++;
                pointerDown = false;
                detector.onPointerEvent(MotionEvent.ACTION_CANCEL, px, py, time, 0f);
                blocker.stop();
                return;
            }
            boolean wasConfirmed = detector.isConfirmedContactValid();
            detector.onPointerEvent(action, px, py, time,
                    (float) input.normalizedContactSize(major, width));
            if (action == MotionEvent.ACTION_MOVE && wasConfirmed && !detector.isConfirmedContactValid()) {
                // A late Root candidate must not take over a gesture the user has already moved away from.
                generation++;
                blocker.stop();
            }
            if (action == MotionEvent.ACTION_UP) {
                // Once takeover succeeded, release may precede the complete text/image result.
                pointerDown = false;
                blocker.stop();
                return;
            }
            long deadline = detector.confirmationDeadline();
            if (deadline >= 0L) main.postAtTime(confirm, deadline);
        });
    }

    private boolean allowed() {
        return !stopped && input.isReady() && input.hasContactSize()
                && settings.isPortalCaptureMode()
                && (settings.isSharingEnabled(false) || settings.isSharingEnabled(true))
                && !controller.isActive()
                && settings.isAccessibilityRecognitionEnabledForOrientation(
                        context.getResources().getConfiguration().orientation)
                && context.getSystemService(PowerManager.class).isInteractive()
                && !context.getSystemService(KeyguardManager.class).isKeyguardLocked();
    }

    private void confirmContact() {
        if (allowed() && detector.confirmWhileDown(SystemClock.uptimeMillis())) capture();
    }

    private void capture() {
        if (busy) return;
        SharedRootNodeCapture.Connection session = nodeSession;
        if (session == null) {
            showFailure("面积取词服务尚未就绪，请稍后重试");
            return;
        }
        busy = true;
        long request = generation;
        float px = x, py = y;
        long deadline = captureDeadline;
        Rect sourceDisplay = context.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
        Set<String> excluded = new LinkedHashSet<>(settings.accessibilityBlacklistedPackages);
        excluded.addAll(AccessibilityBlacklist.builtInPackages(context));
        worker.execute(() -> {
            try {
                if (request != generation || !pointerDown) return;
                boolean[] cancelledOriginal = {false};
                CapturedContent content = session.capture(px, py, excluded,
                        settings.isSharingEnabled(false), settings.isSharingEnabled(true), sourcePackage -> {
                    if (request != generation || !pointerDown || !allowed()) return false;
                    if (GameCapturePolicy.shouldPause(context, settings, sourcePackage)) return false;
                    if (SystemClock.uptimeMillis() >= deadline) {
                        DragShareLog.w(TAG, "candidate arrived after long-press deadline; preserving original gesture");
                        main.post(() -> {
                            if (request == generation && allowed()) {
                                showFailure("当前页面取词较慢，本次保留原长按操作");
                            }
                        });
                        return false;
                    }
                    // Text leaves may confirm early; images wait for the full password scan.
                    // Cancel only before the original app's long-press deadline.
                    if (!blocker.start()) {
                        main.post(() -> {
                            if (request == generation && allowed()) {
                                showFailure("无法接管此页面的按压，已保留原操作");
                            }
                        });
                        return false;
                    }
                    cancelledOriginal[0] = true;
                    return true;
                });
                if (!cancelledOriginal[0]) return;
                main.post(() -> {
                    if (request != generation || !allowed()) {
                        if (content != null && content.isImage()) content.bitmap.recycle();
                        return;
                    }
                    if (content == null) {
                        showFailure("页面内容已变化，请重新按压");
                        return;
                    }
                    if (content.isImage()) {
                        openImage(request, content);
                        return;
                    }
                    Bundle text = new Bundle();
                    text.putString("text", content.text);
                    text.putString("before_text", content.beforeText);
                    text.putString("after_text", content.afterText);
                    if (content.sourceBounds != null && !content.sourceBounds.isEmpty()) {
                        Bundle area = new Bundle();
                        area.putParcelable("bounds", content.sourceBounds);
                        area.putInt("width", sourceDisplay.width());
                        area.putInt("height", sourceDisplay.height());
                        text.putBundle("source_area", area);
                    }
                    text.putInt("x", Math.round(px));
                    text.putInt("y", Math.round(py));
                    try {
                        context.getContentResolver().call(DragShareSettings.settingsUri(),
                                "open_bigbang_window", null, text);
                        DragShareLog.i(TAG, "area text window requested package=" + content.sourcePackage);
                    } catch (SecurityException | IllegalStateException error) {
                        DragShareLog.w(TAG, "cannot open area text window", error);
                        showFailure(error.getMessage());
                    }
                });
            } catch (IOException | SecurityException error) {
                DragShareLog.w(TAG, "area capture failed", error);
                main.post(() -> {
                    if (request == generation && !stopped) showFailure("按压识别失败：" + error.getMessage());
                });
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                DragShareLog.i(TAG, "area capture stopped");
            } finally {
                main.post(() -> busy = false);
            }
        });
    }

    private void openImage(long request, CapturedContent content) {
        ImageStagingClient.stage(context, content.bitmap, new ImageStagingClient.Callback() {
            @Override public void onStaged(Uri uri) {
                content.bitmap.recycle();
                main.post(() -> {
                    if (request != generation || !allowed()) return;
                    Bundle image = new Bundle();
                    image.putParcelable("image", uri);
                    try {
                        context.getContentResolver().call(ImageStagingClient.BASE_URI,
                                ImageOcrOverlay.METHOD_RECOGNIZE, null, image);
                        DragShareLog.i(TAG, "area image window requested package=" + content.sourcePackage);
                    } catch (SecurityException | IllegalStateException error) {
                        DragShareLog.w(TAG, "cannot open area image window", error);
                        showFailure("无法打开图片识字窗口：" + error.getMessage());
                    }
                });
            }

            @Override public void onFailure(Throwable error) {
                content.bitmap.recycle();
                DragShareLog.w(TAG, "cannot stage area image", error);
                main.post(() -> {
                    if (request == generation && !stopped) showFailure("无法暂存识字图片，请重试");
                });
            }
        });
    }

    private void showFailure(String reason) {
        Toast.makeText(context, reason, Toast.LENGTH_LONG).show();
    }

    void stop() {
        main.post(() -> {
            stopped = true;
            pointerDown = false;
            generation++;
            main.removeCallbacks(confirm);
            blocker.stop();
            SharedRootNodeCapture.Connection session = nodeSession;
            nodeSession = null;
            if (session != null) session.close();
            worker.shutdownNow();
        });
    }
}

package com.leaf.hyperdragshare.codex;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
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
    private boolean stopped;
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
            detector.onPointerEvent(action, px, py, time,
                    (float) input.normalizedContactSize(major, width));
            if (action == MotionEvent.ACTION_UP) {
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
                && settings.isPortalCaptureMode() && settings.isSharingEnabled(false)
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
        busy = true;
        long request = generation;
        float px = x, py = y;
        long deadline = captureDeadline;
        Set<String> excluded = new LinkedHashSet<>(settings.accessibilityBlacklistedPackages);
        excluded.addAll(AccessibilityBlacklist.builtInPackages(context));
        worker.execute(() -> {
            try {
                if (request != generation || !pointerDown) return;
                CapturedContent content = RootNodeCaptureClient.capture(context, px, py, excluded);
                if (SystemClock.uptimeMillis() >= deadline) {
                    DragShareLog.w(TAG, "area capture exceeded system long-press deadline; preserving original gesture");
                    return;
                }
                // A blocked app or password must retain its original gesture. Only cancel after
                // a safe text candidate has been obtained, and while this contact is still down.
                if (content != null) {
                    if (request != generation || !pointerDown) return;
                    if (!blocker.start()) throw new IOException("Cannot cancel the original touch stream");
                }
                main.post(() -> {
                    if (request != generation || !allowed()) return;
                    if (content == null) {
                        showFailure("触点下没有可读取的文字，可改用图片识字");
                        return;
                    }
                    Bundle text = new Bundle();
                    text.putString("text", content.text);
                    text.putString("before_text", content.beforeText);
                    text.putString("after_text", content.afterText);
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
                    if (request == generation && !stopped) showFailure("面积取词失败，请查看诊断日志");
                });
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                DragShareLog.i(TAG, "area capture stopped");
            } finally {
                main.post(() -> busy = false);
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
            worker.shutdownNow();
        });
    }
}

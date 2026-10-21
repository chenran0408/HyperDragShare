package com.leaf.hyperdragshare.codex;

import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/** One press pipeline for both hosts: classify once, then use the confirmed gesture's result. */
final class PressCaptureSource {
    private static final String TAG = "DragShare/Press";
    private final Context context;
    private final RootTouchSource input;
    private final DragShareController controller;
    private final DragShareSettings settings;
    private final BooleanSupplier hostAllowed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final BackgroundTouchBlocker blocker;
    private final PressTriggerDetector detector = new PressTriggerDetector();
    private final Runnable classify = this::classifyPress;
    private final Runnable fire = this::firePress;
    private volatile SharedRootNodeCapture.Connection nodeSession;
    private volatile long generation;
    private volatile boolean stopped;
    private volatile boolean pointerDown;
    private boolean lookupRunning;
    private boolean displayQueued;
    private CapturedContent cached;
    private float downX;
    private float downY;
    private Rect sourceDisplay;
    private long lastTrigger;

    PressCaptureSource(Context context, RootTouchSource input, DragShareController controller,
                       DragShareSettings settings, BooleanSupplier hostAllowed) {
        this.context = context;
        this.input = input;
        this.controller = controller;
        this.settings = settings;
        this.hostAllowed = hostAllowed;
        blocker = new BackgroundTouchBlocker(context);
        worker.execute(() -> {
            try {
                SharedRootNodeCapture.Connection ready = SharedRootNodeCapture.open(context, false);
                main.post(() -> {
                    if (stopped) ready.close();
                    else {
                        nodeSession = ready;
                        DragShareLog.i(TAG, "press reader ready; idle without reading nodes");
                    }
                });
            } catch (IOException | SecurityException error) {
                DragShareLog.w(TAG, "cannot prepare press reader", error);
                main.post(() -> { if (!stopped) showFailure("按压取词连接失败：" + error.getMessage()); });
            }
        });
    }

    void onPointerEvent(int action, float x, float y, long time, int major, int width) {
        if (Looper.myLooper() == Looper.getMainLooper()) handlePointer(action, x, y, time, major, width);
        else main.post(() -> handlePointer(action, x, y, time, major, width));
    }

    private void handlePointer(int action, float x, float y, long time, int major, int width) {
        if (stopped) return;
        if (action == MotionEvent.ACTION_DOWN) {
            cancelGesture();
            pointerDown = true;
            downX = x;
            downY = y;
            sourceDisplay = context.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
        }
        if (!allowed()) {
            cancelGesture();
            return;
        }
        boolean hadFired = detector.isFired();
        detector.onPointerEvent(action, x, y, time,
                input.normalizedTouchMajor(major), input.normalizedWidthMajor(width), 1);
        if (action == MotionEvent.ACTION_UP) pointerDown = false;
        if (action == MotionEvent.ACTION_UP && hadFired) {
            main.removeCallbacks(classify);
            main.removeCallbacks(fire);
            blocker.stop();
            return;
        }
        if (!detector.isActive()) {
            cancelGesture();
            return;
        }
        schedule();
    }

    private boolean allowed() {
        return !stopped && input.isReady() && hostAllowed.getAsBoolean()
                && (settings.isSharingEnabled(false) || settings.isSharingEnabled(true))
                && !controller.isActive()
                && settings.isAccessibilityRecognitionEnabledForOrientation(
                        context.getResources().getConfiguration().orientation)
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
        if (lastTrigger > 0L && now - lastTrigger < 4000L) {
            cancelGesture();
            return;
        }
        lastTrigger = now;
        DragShareLog.i(TAG, "press confirmed path=" + (detector.isAreaPress() ? "area" : "ordinary")
                + " elapsedMs=" + (now - detector.downTime()));
        if (cached != null) display();
        else if (!lookupRunning) lookup();
    }

    private void lookup() {
        SharedRootNodeCapture.Connection session = nodeSession;
        if (session == null) {
            if (detector.isFired()) showFailure("按压取词尚未连接，请稍后重试");
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
        worker.execute(() -> {
            try {
                if (request != generation || stopped) return;
                CapturedContent content = session.capturePress(x, y, excluded,
                        settings.isSharingEnabled(false), settings.isSharingEnabled(true), earlyCapture, source ->
                                request == generation && allowed()
                                        && !GameCapturePolicy.shouldPause(context, settings, source));
                main.post(() -> {
                    if (request != generation || !allowed()) {
                        recycle(content);
                        return;
                    }
                    lookupRunning = false;
                    if (content == null) {
                        if (detector.isFired()) showFailure("当前位置没有可读取的文字或安全识字区域");
                        cancelGesture();
                        return;
                    }
                    if (!content.isImage() && isHoldControlText(content.text)) {
                        cancelGesture();
                        return;
                    }
                    cached = content;
                    detector.onClassification(!content.isImage());
                    if (detector.isFired()) display();
                    else schedule();
                });
            } catch (IOException | SecurityException error) {
                DragShareLog.w(TAG, "press lookup failed", error);
                main.post(() -> {
                    if (request == generation && !stopped) {
                        showFailure("按压识别失败：" + error.getMessage());
                        cancelGesture();
                    }
                });
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                DragShareLog.i(TAG, "press lookup stopped");
            }
        });
    }

    private void display() {
        if (displayQueued) return;
        displayQueued = true;
        CapturedContent content = cached;
        cached = null;
        long request = generation;
        worker.execute(() -> {
            try {
                SharedRootNodeCapture.Connection session = nodeSession;
                if (request != generation || !allowed() || session == null
                        || !session.isCurrentSource(content.sourcePackage)) {
                    recycle(content);
                    return;
                }
                if (request != generation || !allowed()) {
                    recycle(content);
                    return;
                }
                if (pointerDown && !blocker.start()) {
                    recycle(content);
                    main.post(() -> {
                        if (request == generation && !stopped) showFailure("无法接管此页面的按压，已保留原操作");
                    });
                    return;
                }
                main.post(() -> {
                    if (request != generation || !allowed()) {
                        recycle(content);
                        blocker.stop();
                        return;
                    }
                    try {
                        context.getSystemService(Vibrator.class).vibrate(
                                VibrationEffect.createOneShot(25L, VibrationEffect.DEFAULT_AMPLITUDE));
                    } catch (SecurityException error) {
                        DragShareLog.w(TAG, "press haptic denied", error);
                    }
                    if (content.isImage()) openImage(request, content);
                    else openText(content);
                });
            } catch (IOException | SecurityException error) {
                recycle(content);
                DragShareLog.w(TAG, "cannot confirm press source", error);
                main.post(() -> {
                    if (request == generation && !stopped) showFailure("按压来源核验失败：" + error.getMessage());
                });
            } catch (InterruptedException error) {
                recycle(content);
                Thread.currentThread().interrupt();
            }
        });
    }

    private void openText(CapturedContent content) {
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
        text.putInt("x", Math.round(downX));
        text.putInt("y", Math.round(downY));
        try {
            context.getContentResolver().call(ImageStagingClient.BASE_URI, "open_bigbang_window", null, text);
        } catch (SecurityException | IllegalStateException error) {
            DragShareLog.w(TAG, "cannot open press text window", error);
            showFailure("无法打开分词窗口：" + error.getMessage());
        }
    }

    private void openImage(long request, CapturedContent content) {
        ImageStagingClient.stage(context, content.bitmap, new ImageStagingClient.Callback() {
            @Override public void onStaged(Uri uri) {
                recycle(content);
                main.post(() -> {
                    if (request != generation || !allowed()) return;
                    Bundle image = new Bundle();
                    image.putParcelable("image", uri);
                    try {
                        context.getContentResolver().call(ImageStagingClient.BASE_URI,
                                ImageOcrOverlay.METHOD_RECOGNIZE, null, image);
                    } catch (SecurityException | IllegalStateException error) {
                        DragShareLog.w(TAG, "cannot open press image window", error);
                        showFailure("无法打开图片识字：" + error.getMessage());
                    }
                });
            }
            @Override public void onFailure(Throwable error) {
                recycle(content);
                DragShareLog.w(TAG, "cannot stage press image", error);
                main.post(() -> {
                    if (request == generation && !stopped) showFailure("无法暂存识字图片：" + error.getMessage());
                });
            }
        });
    }

    private static boolean isHoldControlText(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        int matches = 0;
        for (String label : new String[]{"转文字", "松开", "上滑", "按住说话", "语音输入", "取消发送",
                "录音中", "正在录音", "slide up", "release to", "recording"}) {
            if (value.contains(label)) matches++;
        }
        return matches >= 2;
    }

    private static void recycle(CapturedContent content) {
        if (content != null && content.isImage()) content.bitmap.recycle();
    }

    void cancelGesture() {
        generation++;
        pointerDown = false;
        main.removeCallbacks(classify);
        main.removeCallbacks(fire);
        detector.cancel();
        lookupRunning = false;
        displayQueued = false;
        recycle(cached);
        cached = null;
        blocker.stop();
    }

    private void showFailure(String reason) {
        Toast.makeText(context, reason, Toast.LENGTH_LONG).show();
    }

    void stop() {
        Runnable stop = () -> {
            stopped = true;
            cancelGesture();
            SharedRootNodeCapture.Connection session = nodeSession;
            nodeSession = null;
            if (session != null) session.close();
            worker.shutdownNow();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) stop.run();
        else main.post(stop);
    }
}

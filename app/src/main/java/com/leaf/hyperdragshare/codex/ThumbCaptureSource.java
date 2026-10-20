package com.leaf.hyperdragshare.codex;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.widget.Toast;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/** One explicit area gesture opens text selection or image OCR after the finger lifts. */
final class ThumbCaptureSource {
    private static final String TAG = "DragShare/Thumb";
    private final DragShareAccessibilityService service;
    private final RootTouchSource input;
    private final ExecutorService worker;
    private final AccessibilityScreenshotter screenshotter;
    private final ContactAreaTriggerDetector detector;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LatestRequest requests = new LatestRequest();

    ThumbCaptureSource(DragShareAccessibilityService service, RootTouchSource input,
                       ExecutorService worker, AccessibilityScreenshotter screenshotter,
                       float threshold) {
        this.service = service;
        this.input = input;
        this.worker = worker;
        this.screenshotter = screenshotter;
        detector = new ContactAreaTriggerDetector(
                threshold, service.getResources().getDisplayMetrics().density * 16f, 180L);
    }

    void onPointerEvent(int action, float x, float y, long time, int major, int minor) {
        if (action == MotionEvent.ACTION_DOWN) {
            requests.invalidate();
        }
        if (!input.hasContactSize() || !service.isThumbCaptureAllowed()) {
            cancel();
            return;
        }
        if (detector.onPointerEvent(action, x, y, time, major, minor)) {
            capture(x, y);
        } else if (action == MotionEvent.ACTION_CANCEL) {
            cancel();
        }
    }

    void cancel() {
        requests.invalidate();
        detector.onPointerEvent(MotionEvent.ACTION_CANCEL, 0, 0, 0, 0, 0);
        screenshotter.cancelAll();
    }

    private void capture(float x, float y) {
        long request = requests.begin();
        String sourcePackage = service.foregroundPackage();
        DragShareLog.i(TAG, "area gesture confirmed; reading selected content");
        try {
            worker.execute(() -> {
                if (!accepts(request, sourcePackage)) return;
                AccessibilityCandidateSelector.Selection selection = service.selectCandidateAt(x, y, request);
                if (!accepts(request, sourcePackage)) return;
                if (selection != null && !selection.isImage()) {
                    Intent intent = TextSegmentationActivity.createIntent(
                            selection.candidate.text, Math.round(x), Math.round(y));
                    main.post(() -> open(request, sourcePackage, intent));
                    return;
                }
                Rect bounds;
                if (selection != null) {
                    bounds = selection.candidate.bounds;
                } else {
                    DragShareAccessibilityService.OcrFallback region = service.ocrFallbackAt(x, y, request);
                    bounds = region == null ? null : region.bounds;
                }
                if (bounds == null) {
                    fail(request, sourcePackage, "没有可读取的文字或安全的识字区域", null);
                    return;
                }
                captureImage(request, sourcePackage, bounds);
            });
        } catch (RejectedExecutionException error) {
            fail(request, sourcePackage, "取词服务已停止，请重新开启无障碍服务", error);
        }
    }

    private void captureImage(long request, String sourcePackage, Rect bounds) {
        screenshotter.capture(request, bounds, new AccessibilityScreenshotter.Callback() {
            @Override
            public void onSuccess(long completed, Bitmap bitmap) {
                if (!accepts(request, sourcePackage)) {
                    bitmap.recycle();
                    return;
                }
                ImageStagingClient.stage(service, bitmap, new ImageStagingClient.Callback() {
                    @Override
                    public void onStaged(Uri uri) {
                        bitmap.recycle();
                        Intent intent = ImageOcrActivity.createRecheckIntent(service, uri)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        main.post(() -> open(request, sourcePackage, intent));
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        bitmap.recycle();
                        fail(request, sourcePackage, "无法保存识字图片，请重试", error);
                    }
                });
            }

            @Override
            public void onFailure(long completed, Throwable error) {
                fail(request, sourcePackage, "当前画面无法截取，请换一个页面重试", error);
            }
        });
    }

    private boolean accepts(long request, String sourcePackage) {
        return requests.accepts(request) && service.isThumbCaptureAllowed()
                && Objects.equals(sourcePackage, service.foregroundPackage());
    }

    private void open(long request, String sourcePackage, Intent intent) {
        if (!accepts(request, sourcePackage)) return;
        try {
            service.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            fail(request, sourcePackage, "系统拒绝打开分词或识字窗口", error);
        }
    }

    private void fail(long request, String sourcePackage, String reason, Throwable error) {
        if (error == null) DragShareLog.w(TAG, reason);
        else DragShareLog.w(TAG, reason, error);
        main.post(() -> {
            if (accepts(request, sourcePackage)) Toast.makeText(service, reason, Toast.LENGTH_LONG).show();
        });
    }
}

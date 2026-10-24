package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.widget.Toast;

import java.io.IOException;

/** Executes a selected drag-share action using only the data captured for that action. */
final class DragShareActions {
    private static final String TAG = "DragShare/UI";

    private final Context context;
    private final Handler mainHandler;
    private final DragShareToast dragShareToast;
    private final boolean accessibilitySource;

    DragShareActions(
            Context context,
            Handler mainHandler,
            DragShareToast dragShareToast,
            boolean accessibilitySource) {
        this.context = context;
        this.mainHandler = mainHandler;
        this.dragShareToast = dragShareToast;
        this.accessibilitySource = accessibilitySource;
    }

    void execute(CapturedContent payload, ShareTarget target, Uri stagedUri, float x, float y) {
        execute(payload, target, stagedUri, x, y, OperationTrace.fresh("action"));
    }

    void execute(CapturedContent payload, ShareTarget target, Uri stagedUri,
                 float x, float y, String operation) {
        OperationTrace.event(operation, "action", "started", stagedUri);
        if (target.isImageOcr()) {
            try {
                launchImageOcrHost(stagedUri, false, operation);
            } catch (RuntimeException error) {
                failed(operation, "image_ocr", error);
                showToast("无法识别图片：" + error.getMessage());
            }
            return;
        }
        if (target.isQrScan()) {
            try {
                launchImageOcrHost(stagedUri, true, operation);
            } catch (RuntimeException error) {
                failed(operation, "qr_scan", error);
                showToast("无法扫描图片二维码：" + error.getMessage());
            }
            return;
        }
        if (target.isStash()) {
            stashContent(payload, stagedUri, operation);
            return;
        }
        if (target.isSaveToLocal()) {
            saveImageLocally(payload.bitmap, stagedUri, operation);
            return;
        }
        if (target.isCopyToClipboard()) {
            copyToClipboard(payload, stagedUri, operation);
            return;
        }
        if (target.isTextSegmentation()) {
            openTextSegmentation(payload, x, y, operation);
            return;
        }
        try {
            log("drop target=" + target.component.flattenToShortString());
            ShareLauncher.launch(context, payload, target, stagedUri, dragShareToast, operation);
        } catch (Throwable error) {
            failed(operation, "share", error);
        }
    }

    private void launchImageOcrHost(Uri image, boolean qr, String operation) {
        Intent intent = new Intent();
        intent.setComponent(new android.content.ComponentName(BuildConfig.APPLICATION_ID,
                ImageOcrActivity.class.getName()));
        intent.putExtra(ImageOcrActivity.EXTRA_HOSTED_URI, image);
        intent.putExtra(ImageOcrActivity.EXTRA_QR, qr);
        intent.setDataAndType(image, "image/*");
        android.content.ClipData clip = android.content.ClipData.newRawUri("image", image);
        OperationTrace.put(clip, operation);
        intent.setClipData(clip);
        android.os.Bundle trace = new android.os.Bundle();
        OperationTrace.put(trace, operation);
        intent.putExtras(trace);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        String stage = qr ? "qr_scan" : "image_ocr";
        OperationTrace.event(operation, stage, "activity_requested", image);
        context.startActivity(intent);
        OperationTrace.event(operation, stage, "activity_launch_returned", image);
        log("image activity launch returned qr=" + qr);
    }

    private void stashContent(CapturedContent payload, Uri stagedImage, String operation) {
        new Thread(() -> {
            try {
                StashClient.put(context, payload, stagedImage);
                OperationTrace.event(operation, "stash", "completed", stagedImage);
                log("stored content in stash kind=" + (payload.isImage() ? "image" : "text"));
                mainHandler.post(() -> showToast("已存入中转站"));
            } catch (IOException | SecurityException | IllegalStateException error) {
                failed(operation, "stash", error);
                mainHandler.post(() -> showToast("存入中转站失败：" + error.getMessage(), Toast.LENGTH_LONG));
            }
        }, "drag-share-stash-save").start();
    }

    private void copyToClipboard(CapturedContent payload, Uri stagedImage, String operation) {
        boolean image = payload != null && payload.isImage();
        if (!image) {
            String text = payload == null ? null : payload.text;
            if (text == null || text.trim().isEmpty()) {
                OperationTrace.event(operation, "clipboard", "rejected_empty_text");
                showToast("没有可复制的文字");
                return;
            }
            ClipboardWriter.copy(context, text, error -> {
                if (error == null) {
                    OperationTrace.event(operation, "clipboard", "completed");
                    log("copied text to clipboard");
                    showToast("文字已复制到剪贴板");
                } else {
                    failed(operation, "clipboard", error);
                    showToast("复制文字失败：" + error.getMessage(), Toast.LENGTH_LONG);
                }
            });
            return;
        }
        if (stagedImage == null) {
            OperationTrace.event(operation, "clipboard", "rejected_image_not_staged");
            showToast("图片尚未准备好，无法复制");
            return;
        }
        ClipboardWriter.copyImage(context, stagedImage, error -> {
            if (error == null) {
                OperationTrace.event(operation, "clipboard", "completed", stagedImage);
                log("copied image to clipboard");
                showToast("图片已复制到剪贴板");
            } else {
                failed(operation, "clipboard", error, stagedImage);
                showToast("复制图片失败：" + error.getMessage(), Toast.LENGTH_LONG);
            }
        });
    }

    private void openTextSegmentation(CapturedContent payload, float x, float y, String operation) {
        String text = payload.text;
        if (text == null || text.trim().isEmpty()) {
            OperationTrace.event(operation, "segmentation", "rejected_empty_text");
            showToast("没有可分词的文字");
            return;
        }
        try {
            android.os.Bundle input = new android.os.Bundle();
            input.putString("text", text);
            input.putString("before_text", payload.beforeText);
            input.putString("after_text", payload.afterText);
            input.putInt("x", Math.round(x));
            input.putInt("y", Math.round(y));
            OperationTrace.put(input, operation);
            android.os.Bundle result = context.getContentResolver().call(
                    ImageStagingClient.BASE_URI, BigBangOverlayLauncher.METHOD_OPEN, null, input);
            if (result == null || !result.getBoolean("accepted")) {
                OperationTrace.event(operation, "segmentation", "rejected");
                showToast("无法打开分词窗口：请求未被接受");
                return;
            }
            OperationTrace.event(operation, "segmentation", "accepted");
            log("requested text segmentation window");
        } catch (RuntimeException error) {
            failed(operation, "segmentation", error);
            showToast("无法打开分词窗口：" + error.getMessage());
        }
    }

    private void saveImageLocally(Bitmap bitmap, Uri stagedImage, String operation) {
        if (bitmap == null || bitmap.isRecycled()) {
            OperationTrace.event(operation, "local_save", "rejected_bitmap_unavailable", stagedImage);
            showToast("保存图片失败");
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                LocalImageSaver.save(context, bitmap);
                OperationTrace.event(operation, "local_save", "completed", stagedImage);
                log("saved image as PNG");
                traceAccessibility("local save succeeded");
                mainHandler.post(() -> showToast("已保存到本地", Toast.LENGTH_LONG));
            } catch (Throwable error) {
                failed(operation, "local_save", error, stagedImage);
                traceAccessibility("local save failed=" + error.getClass().getSimpleName());
                mainHandler.post(() -> showToast("保存图片失败", Toast.LENGTH_LONG));
            }
        }, "drag-share-local-save");
        worker.start();
    }

    private void showToast(String message) {
        dragShareToast.show(message);
    }

    private void showToast(String message, int duration) {
        dragShareToast.show(message, duration);
    }

    private void traceAccessibility(String message) {
        if (accessibilitySource) {
            AccessibilityTrace.record(context, message);
        }
    }

    private static void log(String message) {
        DragShareLog.i(TAG, message);
    }

    private static void failed(String operation, String stage, Throwable error) {
        failed(operation, stage, error, null);
    }

    private static void failed(String operation, String stage, Throwable error, Uri image) {
        String type = error == null ? "Unknown" : error.getClass().getSimpleName();
        OperationTrace.event(operation, stage, "failed_" + type, image);
        DragShareLog.w(TAG, stage + " failed error=" + type);
    }
}

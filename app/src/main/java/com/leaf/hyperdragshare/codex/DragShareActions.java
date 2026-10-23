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
        if (target.isImageOcr()) {
            try {
                launchImageOcrHost(stagedUri, false);
            } catch (RuntimeException error) {
                log("image OCR window failed", error);
                showToast("无法识别图片：" + error.getMessage());
            }
            return;
        }
        if (target.isQrScan()) {
            try {
                launchImageOcrHost(stagedUri, true);
            } catch (RuntimeException error) {
                log("image QR window failed", error);
                showToast("无法扫描图片二维码：" + error.getMessage());
            }
            return;
        }
        if (target.isStash()) {
            stashContent(payload, stagedUri);
            return;
        }
        if (target.isSaveToLocal()) {
            saveImageLocally(payload.bitmap);
            return;
        }
        if (target.isCopyToClipboard()) {
            copyToClipboard(payload, stagedUri);
            return;
        }
        if (target.isTextSegmentation()) {
            openTextSegmentation(payload, x, y);
            return;
        }
        try {
            log("drop target=" + target.component.flattenToShortString());
            ShareLauncher.launch(context, payload, target, stagedUri, dragShareToast);
        } catch (Throwable error) {
            log("share launch failed", error);
        }
    }

    private void launchImageOcrHost(Uri image, boolean qr) {
        Intent intent = new Intent();
        intent.setComponent(new android.content.ComponentName(BuildConfig.APPLICATION_ID,
                ImageOcrActivity.class.getName()));
        intent.putExtra(ImageOcrActivity.EXTRA_HOSTED_URI, image);
        intent.putExtra(ImageOcrActivity.EXTRA_QR, qr);
        intent.setDataAndType(image, "image/*");
        intent.setClipData(android.content.ClipData.newRawUri("image", image));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
        log("image window handed to Activity qr=" + qr);
    }

    private void stashContent(CapturedContent payload, Uri stagedImage) {
        new Thread(() -> {
            try {
                StashClient.put(context, payload, stagedImage);
                log("stored content in stash kind=" + (payload.isImage() ? "image" : "text"));
                mainHandler.post(() -> showToast("已存入中转站"));
            } catch (IOException | SecurityException | IllegalStateException error) {
                log("stash save failed", error);
                mainHandler.post(() -> showToast("存入中转站失败：" + error.getMessage(), Toast.LENGTH_LONG));
            }
        }, "drag-share-stash-save").start();
    }

    private void copyToClipboard(CapturedContent payload, Uri stagedImage) {
        boolean image = payload != null && payload.isImage();
        if (!image) {
            String text = payload == null ? null : payload.text;
            if (text == null || text.trim().isEmpty()) {
                showToast("没有可复制的文字");
                return;
            }
            ClipboardWriter.copy(context, text, error -> {
                if (error == null) {
                    log("copied text to clipboard");
                    showToast("文字已复制到剪贴板");
                } else {
                    log("clipboard text copy failed", error);
                    showToast("复制文字失败：" + error.getMessage(), Toast.LENGTH_LONG);
                }
            });
            return;
        }
        if (stagedImage == null) {
            showToast("图片尚未准备好，无法复制");
            return;
        }
        ClipboardWriter.copyImage(context, stagedImage, error -> {
            if (error == null) {
                log("copied image to clipboard");
                showToast("图片已复制到剪贴板");
            } else {
                log("clipboard image copy failed", error);
                showToast("复制图片失败：" + error.getMessage(), Toast.LENGTH_LONG);
            }
        });
    }

    private void openTextSegmentation(CapturedContent payload, float x, float y) {
        String text = payload.text;
        if (text == null || text.trim().isEmpty()) {
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
            android.os.Bundle result = context.getContentResolver().call(
                    ImageStagingClient.BASE_URI, BigBangOverlayLauncher.METHOD_OPEN, null, input);
            if (result == null || !result.getBoolean("accepted")) {
                throw new IllegalStateException("分词窗口未接受请求");
            }
            log("requested text segmentation window");
        } catch (RuntimeException error) {
            log("open text segmentation failed", error);
            showToast("无法打开分词窗口：" + error.getMessage());
        }
    }

    private void saveImageLocally(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            showToast("保存图片失败");
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                LocalImageSaver.save(context, bitmap);
                log("saved image as PNG");
                traceAccessibility("local save succeeded");
                mainHandler.post(() -> showToast("已保存到本地", Toast.LENGTH_LONG));
            } catch (Throwable error) {
                log("local image save failed", error);
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

    private static void log(String message, Throwable error) {
        DragShareLog.w(TAG, message, error);
    }
}

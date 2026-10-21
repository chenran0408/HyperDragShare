package com.leaf.hyperdragshare.codex;

import android.app.Activity;
import android.app.Instrumentation;
import android.net.Uri;
import android.os.Bundle;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Debug-only overlay inspection; Root image mode is for the generated receiver sample only. */
public final class OverlayInteractionProbe extends Instrumentation {
    private boolean longText;
    private Bundle arguments;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments;
        longText = arguments != null && "long".equals(arguments.getString("sample"));
        start();
    }

    @Override public void onStart() {
        if (arguments != null && "stash_batch".equals(arguments.getString("mode"))) {
            Bundle report = new Bundle();
            boolean accepted = createStashBatch(report);
            finishAfterInspection(accepted, report);
            return;
        }
        if (arguments != null && "root_image".equals(arguments.getString("mode"))) {
            Bundle report = new Bundle();
            boolean accepted = captureRootImage(report);
            finishAfterInspection(accepted, report);
            return;
        }
        String sample = "传送门文字识别\n小米15 Pro HyperOS 4\n复制 搜索 分享 12345";
        if (longText) {
            StringBuilder paragraphs = new StringBuilder();
            for (int index = 1; index <= 25; index++) {
                paragraphs.append("第").append(index).append("段：选择文字后可以复制、合并、搜索与分享。\n");
            }
            sample = paragraphs.toString();
        }
        Bundle report = new Bundle();
        boolean accepted = BigBangOverlayLauncher.showText(getTargetContext(), sample, -1, -1, null);
        report.putBoolean("accepted", accepted);
        report.putBoolean("longText", longText);
        report.putInt("syntheticLength", sample.length());
        finishAfterInspection(accepted, report);
    }

    private boolean createStashBatch(Bundle report) {
        java.util.List<java.io.File> files = new java.util.ArrayList<>();
        try {
            java.util.List<Uri> uris = new java.util.ArrayList<>();
            for (int index = 0; index < 2; index++) {
                java.io.File file = new java.io.File(getTargetContext().getCacheDir(), "batch123-" + index + ".png");
                files.add(file);
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                        index == 0 ? 640 : 320, index == 0 ? 240 : 180, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                canvas.drawColor(android.graphics.Color.WHITE);
                android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                paint.setColor(android.graphics.Color.BLACK);
                paint.setTextSize(36);
                canvas.drawText("BATCH 123 " + (index == 0 ? "A" : "B"), 18, 100, paint);
                try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
                    if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) {
                        throw new IOException("Generated PNG encode failed");
                    }
                } finally { bitmap.recycle(); }
                uris.add(Uri.fromFile(file));
            }
            try (StashStore store = new StashStore(getTargetContext())) {
                report.putInt("saved", store.addImages(getTargetContext().getContentResolver(), uris));
                report.putString("batchId", store.listEntries().get(0).batchId);
            }
            StashOverlayPanel.show(getTargetContext());
            return true;
        } catch (IOException | SecurityException error) {
            report.putString("error", error.toString());
            return false;
        } finally {
            for (java.io.File file : files) {
                if (file.exists() && !file.delete()) android.util.Log.w("DragShare/OverlayProbe", "Generated sample cleanup failed");
            }
        }
    }

    private boolean captureRootImage(Bundle report) {
        try {
            float x = Float.parseFloat(arguments.getString("x", "NaN"));
            float y = Float.parseFloat(arguments.getString("y", "NaN"));
            if (!Float.isFinite(x) || !Float.isFinite(y) || x < 0 || y < 0) {
                throw new IllegalArgumentException("root_image requires explicit nonnegative x and y");
            }
            CapturedContent content;
            try (RootNodeCaptureClient.Session session = RootNodeCaptureClient.open(getTargetContext())) {
                content = session.capture(x, y, AccessibilityBlacklist.builtInPackages(getTargetContext()),
                        true, true, () -> true);
            }
            if (content == null || !content.isImage()) {
                throw new IOException("Root capture did not return an image; target the generated ImageView");
            }
            report.putInt("imageWidth", content.bitmap.getWidth());
            report.putInt("imageHeight", content.bitmap.getHeight());
            CountDownLatch staged = new CountDownLatch(1);
            ImageStagingClient.stage(getTargetContext(), content.bitmap, new ImageStagingClient.Callback() {
                @Override public void onStaged(Uri uri) {
                    try {
                        Bundle extras = new Bundle();
                        extras.putParcelable("image", uri);
                        getTargetContext().getContentResolver().call(ImageStagingClient.BASE_URI,
                                ImageOcrOverlay.METHOD_RECOGNIZE, null, extras);
                        // Accepted is submission to the real window, not an OCR accuracy assertion.
                        report.putBoolean("accepted", true);
                    } catch (RuntimeException error) {
                        report.putString("error", error.toString());
                    } finally {
                        recycle(content);
                        staged.countDown();
                    }
                }

                @Override public void onFailure(Throwable error) {
                    report.putString("error", error.toString());
                    recycle(content);
                    staged.countDown();
                }
            });
            if (!staged.await(65, TimeUnit.SECONDS)) {
                // The callback retains ownership until the encoder stops reading the bitmap.
                report.putString("error", "Image staging did not finish within 65 seconds");
                return false;
            }
            return report.getBoolean("accepted");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            report.putString("error", "Root image inspection interrupted");
            return false;
        } catch (IOException | RuntimeException error) {
            report.putString("error", error.toString());
            return false;
        }
    }

    private static void recycle(CapturedContent content) {
        if (content.bitmapOwnedByDragShare && !content.bitmap.isRecycled()) content.bitmap.recycle();
    }

    private void finishAfterInspection(boolean accepted, Bundle report) {
        report.putBoolean("accepted", accepted);
        if (accepted) {
            try {
                Thread.sleep(90000L); // Keep the target process alive for bounded, manual UI inspection.
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                report.putString("error", "inspection interrupted");
                accepted = false;
            }
        }
        finish(accepted ? Activity.RESULT_OK : Activity.RESULT_CANCELED, report);
    }
}

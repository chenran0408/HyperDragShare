package com.leaf.hyperdragshare.codex;

import android.app.Activity;
import android.app.Instrumentation;
import android.net.Uri;
import android.os.Bundle;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;
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
        if (arguments != null && "stash_cleanup".equals(arguments.getString("mode"))) {
            Bundle report = new Bundle();
            try (StashStore store = new StashStore(getTargetContext())) {
                String ids = arguments.getString("ids", "");
                if (ids.isEmpty()) throw new IOException("Explicit generated entry IDs required");
                int removed = 0;
                for (String id : ids.split(",")) {
                    UUID.fromString(id);
                    if (store.delete(id)) removed++;
                }
                report.putInt("removed", removed);
                report.putInt("remaining", store.listEntries().size());
                finish(Activity.RESULT_OK, report);
            } catch (IOException | IllegalArgumentException | SecurityException error) {
                report.putString("error", error.toString());
                finish(Activity.RESULT_CANCELED, report);
            }
            return;
        }
        if (arguments != null && ("cache_cleanup_prepare".equals(arguments.getString("mode"))
                || "cache_cleanup_verify".equals(arguments.getString("mode")))) {
            Bundle report = new Bundle();
            boolean passed = "cache_cleanup_prepare".equals(arguments.getString("mode"))
                    ? prepareCacheCleanup(report) : verifyCacheCleanup(report);
            report.putBoolean("passed", passed);
            finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, report);
            return;
        }
        if (arguments != null && ("stash_batch".equals(arguments.getString("mode"))
                || "stash_manual".equals(arguments.getString("mode")))) {
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
        boolean accepted = BigBangOverlayLauncher.showText(getTargetContext(), sample, -1, -1);
        report.putBoolean("accepted", accepted);
        report.putBoolean("longText", longText);
        report.putInt("syntheticLength", sample.length());
        finishAfterInspection(accepted, report);
    }

    private boolean prepareCacheCleanup(Bundle report) {
        File cache = getTargetContext().getCacheDir();
        File metadata = new File(cache, "cleanup133-probe.properties");
        Properties paths = new Properties();
        boolean prepared = false;
        try {
            if (metadata.exists()) throw new IOException("Verify the previous cache cleanup probe before preparing again");
            paths.setProperty("old", stageGeneratedPng().getAbsolutePath());
            paths.setProperty("fresh", stageGeneratedPng().getAbsolutePath());
            File temporary = new File(cache, "shared-images/" + UUID.randomUUID() + ".tmp");
            paths.setProperty("temporary", temporary.getAbsolutePath());
            java.nio.file.Files.write(temporary.toPath(), new byte[]{1, 2, 3});
            File ocrDirectory = new File(cache, "ocr-input");
            if (!ocrDirectory.exists() && !ocrDirectory.mkdir()) throw new IOException("Unable to create OCR sample directory");
            File ocr = new File(ocrDirectory, UUID.randomUUID() + ".image");
            paths.setProperty("ocr", ocr.getAbsolutePath());
            java.nio.file.Files.write(ocr.toPath(), new byte[]{4, 5, 6});
            File keepDirectory = new File(cache, "cleanup133-" + UUID.randomUUID());
            if (!keepDirectory.mkdir()) throw new IOException("Unable to create non-target sample directory");
            File keep = new File(keepDirectory, UUID.randomUUID().toString());
            paths.setProperty("keep", keep.getAbsolutePath());
            java.nio.file.Files.write(keep.toPath(), new byte[]{7, 8, 9});
            long expired = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(25);
            for (String key : new String[]{"old", "temporary", "ocr", "keep"}) {
                if (!new File(paths.getProperty(key)).setLastModified(expired)) {
                    throw new IOException("Unable to age the generated " + key + " sample");
                }
            }
            try (FileOutputStream output = new FileOutputStream(metadata)) {
                paths.store(output, null);
            }
            prepared = true;
            report.putBoolean("prepared", true);
            report.putInt("expiredTargetFiles", 3);
            report.putString("metadata", metadata.getAbsolutePath());
        } catch (IOException | IllegalStateException | SecurityException error) {
            report.putString("error", error.toString());
        } finally {
            if (!prepared && !paths.isEmpty()) cleanCacheProbe(paths, metadata, report);
        }
        return prepared;
    }

    private File stageGeneratedPng() throws IOException {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888);
        byte[] bytes;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            bitmap.eraseColor(android.graphics.Color.WHITE);
            if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)) {
                throw new IOException("Generated PNG encode failed");
            }
            bytes = output.toByteArray();
        } finally { bitmap.recycle(); }
        Bundle extras = new Bundle();
        extras.putByteArray(ImageStagingClient.EXTRA_BYTES, bytes);
        Bundle result = getTargetContext().getContentResolver().call(ImageStagingClient.BASE_URI,
                ImageStagingClient.METHOD_STAGE, null, extras);
        if (result == null || result.getString(ImageStagingClient.RESULT_URI) == null) {
            throw new IOException("Provider did not return the generated image URI");
        }
        Uri uri = Uri.parse(result.getString(ImageStagingClient.RESULT_URI));
        return new File(getTargetContext().getCacheDir(), "shared-images/" + uri.getLastPathSegment());
    }

    private boolean verifyCacheCleanup(Bundle report) {
        File metadata = new File(getTargetContext().getCacheDir(), "cleanup133-probe.properties");
        Properties paths = new Properties();
        boolean passed = false;
        try {
            try (FileInputStream input = new FileInputStream(metadata)) { paths.load(input); }
            boolean oldRemoved = !new File(paths.getProperty("old")).exists();
            boolean temporaryRemoved = !new File(paths.getProperty("temporary")).exists();
            boolean ocrRemoved = !new File(paths.getProperty("ocr")).exists();
            boolean keepPreserved = new File(paths.getProperty("keep")).isFile();
            report.putBoolean("oldSharedRemoved", oldRemoved);
            report.putBoolean("temporaryRemoved", temporaryRemoved);
            report.putBoolean("oldOcrRemoved", ocrRemoved);
            report.putBoolean("nonTargetPreserved", keepPreserved);
            File fresh = new File(paths.getProperty("fresh"));
            Uri freshUri = ImageStagingClient.BASE_URI.buildUpon().appendPath("shared").appendPath(fresh.getName()).build();
            boolean freshReadable;
            try (InputStream input = getTargetContext().getContentResolver().openInputStream(freshUri)) {
                if (input == null) throw new IOException("The fresh staged image could not be opened");
                byte[] header = new byte[8];
                new java.io.DataInputStream(input).readFully(header);
                freshReadable = Arrays.equals(header, new byte[]{(byte) 0x89, 80, 78, 71, 13, 10, 26, 10});
            }
            report.putBoolean("freshPngReadable", freshReadable);
            passed = oldRemoved && temporaryRemoved && ocrRemoved && keepPreserved && freshReadable;
        } catch (IOException | IllegalStateException | SecurityException error) {
            report.putString("error", error.toString());
        } finally {
            if (!cleanCacheProbe(paths, metadata, report)) passed = false;
        }
        return passed;
    }

    private boolean cleanCacheProbe(Properties paths, File metadata, Bundle report) {
        java.util.List<File> files = new java.util.ArrayList<>();
        for (String key : new String[]{"old", "fresh", "temporary", "ocr", "keep"}) {
            String path = paths.getProperty(key);
            if (path == null) { // A preparation failure can precede this sample.
                continue;
            }
            files.add(new File(path));
        }
        if (paths.getProperty("keep") != null) {
            files.add(new File(paths.getProperty("keep")).getParentFile());
        }
        files.add(metadata);
        boolean cleaned = true;
        for (File file : files) {
            try {
                java.nio.file.Files.deleteIfExists(file.toPath());
            } catch (IOException | SecurityException error) {
                cleaned = false;
                report.putString("cleanupError", error.toString());
                android.util.Log.w("DragShare/OverlayProbe", "Generated cache sample cleanup failed", error);
            }
        }
        for (String key : new String[]{"old", "fresh"}) {
            String path = paths.getProperty(key);
            if (path == null) continue;
            try {
                getTargetContext().revokeUriPermission(ImageStagingClient.BASE_URI.buildUpon()
                        .appendPath("shared").appendPath(new File(path).getName()).build(),
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException error) {
                cleaned = false;
                report.putString("cleanupError", error.toString());
                android.util.Log.w("DragShare/OverlayProbe", "Generated image grant cleanup failed", error);
            }
        }
        report.putBoolean("probeFilesCleaned", cleaned);
        return cleaned;
    }

    private boolean createStashBatch(Bundle report) {
        java.util.List<java.io.File> files = new java.util.ArrayList<>();
        try {
            boolean manual = "stash_manual".equals(arguments.getString("mode"));
            java.util.List<Uri> uris = new java.util.ArrayList<>();
            for (int index = 0; index < (manual ? 1 : 2); index++) {
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
                if (manual) {
                    report.putString("textA", store.addText("HDS129 文字 A · 与图片一起取用").id);
                    report.putString("textB", store.addText("HDS129 文字 B · 保留原来顺序").id);
                    report.putInt("saved", 3);
                }
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
                        true, true, sourcePackage -> true);
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

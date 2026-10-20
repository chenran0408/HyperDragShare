package com.leaf.hyperdragshare.codex;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Debug-only: exercise the production client and optional overlay using generated Chinese text. */
public final class AicrOcrProbe extends Instrumentation {
    private String mode;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mode = arguments == null ? "client" : arguments.getString("mode", "client");
        start();
    }

    @Override public void onStart() {
        Context context = getTargetContext();
        Bundle report = new Bundle();
        report.putInt("uid", Process.myUid());
        report.putString("mode", mode);
        boolean originalEngine = IntegrationSettings.isAicrOcrEnabled(context);
        File imageFile = null;
        int result = Activity.RESULT_CANCELED;
        try {
            if (!mode.equals("client") && !mode.equals("cancel") && !mode.equals("overlay")) {
                throw new IllegalArgumentException("Unknown probe mode: " + mode);
            }
            if (mode.equals("cancel")) {
                Result cancelled = recognize(context, true);
                if (!(cancelled.error instanceof CancellationException) || cancelled.calls != 1) {
                    throw new IOException("Cancellation callback contract failed");
                }
                report.putBoolean("cancelledOnce", true);
            }
            Result recognized = recognize(context, false);
            if (recognized.error != null) throw recognized.error;
            if (recognized.calls != 1) throw new IOException("Expected one completion callback");
            String compact = recognized.text == null ? "" : recognized.text.replaceAll("\\s", "");
            report.putString("text", recognized.text); // Generated sample only, never user content.
            report.putLong("recognitionMs", recognized.millis);
            if (!compact.contains("传送门") || !compact.contains("HyperOS") || !compact.contains("12345")) {
                throw new IOException("Chinese/mixed synthetic markers were not recognized");
            }
            report.putBoolean("productionClientPassed", true);
            if (mode.equals("overlay")) {
                imageFile = new File(context.getCacheDir(), "aicr-synthetic-probe.png");
                Bitmap sample = sample();
                try (FileOutputStream output = new FileOutputStream(imageFile)) {
                    if (!sample.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        throw new IOException("Cannot encode synthetic image");
                    }
                } finally {
                    sample.recycle();
                }
                Uri uri = Uri.fromFile(imageFile);
                runOnMainSync(() -> {
                    IntegrationSettings.setAicrOcrEnabled(context, true);
                    if (!ImageOcrOverlay.start(context, uri)) {
                        throw new IllegalStateException("Image OCR overlay did not start");
                    }
                });
                Log.i("DragShare/AicrProbe", "synthetic product overlay started; selected engine=AICR");
                report.putBoolean("overlayStarted", true);
                // Keep this instrumentation process alive for device UI inspection.
                Thread.sleep(25000L);
            }
            result = Activity.RESULT_OK;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            report.putString("error", "probe interrupted");
        } catch (Exception error) {
            report.putString("error", error.toString());
            Log.w("DragShare/AicrProbe", "production client probe failed", error);
        } finally {
            // Synchronous test teardown prevents instrumentation exit from racing preference IO.
            boolean restored = context.getSharedPreferences("drag_share_integrations", Context.MODE_PRIVATE)
                    .edit().putBoolean("aicr_ocr_enabled", originalEngine).commit();
            report.putBoolean("engineSettingRestored", restored);
            if (!restored) result = Activity.RESULT_CANCELED;
            if (imageFile != null && !imageFile.delete()) {
                Log.w("DragShare/AicrProbe", "synthetic image cleanup failed");
            }
            finish(result, report);
        }
    }

    private Result recognize(Context context, boolean cancel) throws IOException, InterruptedException {
        Bitmap image = sample();
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> text = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        long started = SystemClock.elapsedRealtime();
        AicrOcrClient.Request request = AicrOcrClient.recognize(context, image, (value, error) -> {
            text.set(value);
            failure.set(error);
            calls.incrementAndGet();
            image.recycle(); // The production callback runs after Binder cleanup.
            completed.countDown();
        });
        if (cancel) request.cancel();
        if (!completed.await(30, TimeUnit.SECONDS)) {
            request.cancel();
            // Do not recycle a bitmap while a synchronous Binder call can still be reading it.
            throw new IOException("Production AICR client exceeded probe wait");
        }
        return new Result(text.get(), failure.get(), calls.get(), SystemClock.elapsedRealtime() - started);
    }

    private static Bitmap sample() {
        Bitmap image = Bitmap.createBitmap(960, 420, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        paint.setColor(Color.BLACK);
        paint.setTextSize(64f);
        canvas.drawText("传送门文字识别", 32f, 110f, paint);
        canvas.drawText("小米15 Pro HyperOS 4", 32f, 230f, paint);
        canvas.drawText("复制 搜索 分享 12345", 32f, 350f, paint);
        return image;
    }

    private static final class Result {
        final String text;
        final Exception error;
        final int calls;
        final long millis;
        Result(String text, Exception error, int calls, long millis) {
            this.text = text;
            this.error = error;
            this.calls = calls;
            this.millis = millis;
        }
    }
}

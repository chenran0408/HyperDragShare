package com.leaf.hyperdragshare.codex;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Generated, known-position text only. Raw vendor coordinates are evidence, not an assumed Rect. */
public final class AicrGeometryProbe extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle report = new Bundle();
        int status = Activity.RESULT_CANCELED;
        Bitmap image = Bitmap.createBitmap(1200, 720, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(60);
        String[] text = {"LEFT 123", "RIGHT 456", "原文附近文字"};
        int[] xs = {72, 620, 80}, ys = {130, 380, 620};
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<AicrOcrResult> result = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        try {
            JSONArray expected = new JSONArray();
            for (int i = 0; i < text.length; i++) {
                Rect bounds = new Rect();
                paint.getTextBounds(text[i], 0, text[i].length(), bounds);
                bounds.offset(xs[i], ys[i]);
                canvas.drawText(text[i], xs[i], ys[i], paint);
                expected.put(new JSONObject().put("text", text[i]).put("bounds", bounds.flattenToString()));
            }
            AicrOcrClient.Request request = AicrOcrClient.recognizeWithLines(getTargetContext(), image, (value, error) -> {
                result.set(value); failure.set(error); image.recycle(); completed.countDown();
            });
            if (!completed.await(30, TimeUnit.SECONDS)) {
                request.cancel();
                throw new IOException("Generated geometry recognition did not finish");
            }
            if (failure.get() != null) throw failure.get();
            AicrOcrResult recognized = result.get();
            if (recognized == null || !recognized.text.replaceAll("\\s", "").contains("RIGHT456")) {
                throw new IOException("Generated marker not recognized");
            }
            JSONArray lines = new JSONArray();
            for (AicrOcrResult.Line line : recognized.lines) {
                lines.put(new JSONObject().put("text", line.text)
                        .put("poly", Arrays.toString(line.polygon)).put("box", Arrays.toString(line.box)));
            }
            report.putString("expected", expected.toString());
            report.putString("rawLines", lines.toString());
            report.putString("generatedText", recognized.text);
            status = Activity.RESULT_OK;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); report.putString("error", error.toString());
        } catch (Exception error) { report.putString("error", error.toString()); }
        finish(status, report);
    }
}

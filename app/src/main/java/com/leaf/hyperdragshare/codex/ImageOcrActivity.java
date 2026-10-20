package com.leaf.hyperdragshare.codex;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/** Recognizes a granted image URI in the module process, never in the hooked host. */
public final class ImageOcrActivity extends ComponentActivity {
    private static final String TAG = "DragShare/OCR";
    private static final int MAX_IMAGE_EDGE = 4096;
    private final LatestRequest requests = new LatestRequest();
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final Semaphore recognitionSlot = new Semaphore(1);
    private TextView status;
    private ProgressBar progress;
    private Button retry;
    private String pendingText;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        panel.setPadding(padding, padding, padding, padding);
        progress = new ProgressBar(this);
        status = new TextView(this);
        status.setGravity(Gravity.CENTER);
        status.setTextSize(18);
        retry = new Button(this);
        retry.setText(R.string.ocr_retry);
        retry.setOnClickListener(view -> recognizeIntent());
        Button cancel = new Button(this);
        cancel.setText(android.R.string.cancel);
        cancel.setOnClickListener(view -> finish());
        panel.addView(progress);
        panel.addView(status);
        panel.addView(retry);
        panel.addView(cancel);
        setContentView(panel);
        recognizeIntent();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        recognizeIntent();
    }

    private void recognizeIntent() {
        long request = requests.begin();
        pendingText = null;
        progress.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
        status.setText(R.string.ocr_recognizing);
        Intent input = getIntent();
        Uri uri = resolveImageUri(input);
        if (uri == null) {
            showFailure(request, R.string.ocr_invalid_image);
            return;
        }
        Uri imageUri = uri;
        decoder.execute(() -> decodeAndRecognize(imageUri, request));
    }

    static Uri resolveImageUri(Intent input) {
        if (!Intent.ACTION_SEND.equals(input.getAction())
                || input.getType() == null || !input.getType().startsWith("image/")) {
            return null;
        }
        Uri uri = input.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        if (uri == null) {
            uri = input.getData();
        }
        return uri != null && "content".equals(uri.getScheme()) ? uri : null;
    }

    private void decodeAndRecognize(Uri uri, long request) {
        // At most one decoded image may be held by native recognition at a time.
        try {
            recognitionSlot.acquire();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!requests.accepts(request)) {
            recognitionSlot.release();
            return;
        }
        Bitmap bitmap;
        try {
            bitmap = ImageDecoder.decodeBitmap(
                    ImageDecoder.createSource(getContentResolver(), uri),
                    (imageDecoder, info, source) -> {
                        imageDecoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                        int longest = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                        if (longest > MAX_IMAGE_EDGE) {
                            float scale = MAX_IMAGE_EDGE / (float) longest;
                            imageDecoder.setTargetSize(
                                    Math.max(1, Math.round(info.getSize().getWidth() * scale)),
                                    Math.max(1, Math.round(info.getSize().getHeight() * scale)));
                        }
                    });
        } catch (IOException | RuntimeException error) {
            recognitionSlot.release();
            reportFailure(request, R.string.ocr_read_failed, error);
            return;
        }
        if (!requests.accepts(request)) {
            bitmap.recycle();
            recognitionSlot.release();
            return;
        }
        // Each request owns its recognizer and bitmap until the task completes, including
        // after cancellation. Closing a screen must never recycle an image still in use.
        TextRecognizer recognizer;
        try {
            recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        } catch (RuntimeException error) {
            bitmap.recycle();
            recognitionSlot.release();
            reportFailure(request, R.string.ocr_failed, error);
            return;
        }
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnCompleteListener(Runnable::run, task -> {
                        try {
                            if (task.isSuccessful()) {
                                String text = task.getResult().getText();
                                runOnUiThread(() -> acceptText(request, text));
                            } else {
                                reportFailure(request, R.string.ocr_failed, task.getException());
                            }
                        } finally {
                            recognizer.close();
                            bitmap.recycle();
                            recognitionSlot.release();
                        }
                    });
        } catch (RuntimeException error) {
            recognizer.close();
            bitmap.recycle();
            recognitionSlot.release();
            reportFailure(request, R.string.ocr_failed, error);
        }
    }

    private void acceptText(long request, String text) {
        if (!canAccept(request)) {
            return;
        }
        if (text.trim().isEmpty()) {
            showFailure(request, R.string.ocr_no_text);
            return;
        }
        pendingText = text;
        openTextWhenResumed();
    }

    private void openTextWhenResumed() {
        if (resumed && pendingText != null && !isFinishing()) {
            String text = pendingText;
            pendingText = null;
            startActivity(TextSegmentationActivity.createIntent(text));
            finish();
        }
    }

    private void reportFailure(long request, int message, Exception error) {
        // Log error type, never input URIs, recognized text, or image data.
        DragShareLog.w(TAG, "image recognition failed: "
                + (error == null ? "cancelled" : error.getClass().getSimpleName()));
        runOnUiThread(() -> showFailure(request, message));
    }

    private void showFailure(long request, int message) {
        if (canAccept(request)) {
            progress.setVisibility(View.GONE);
            status.setText(message);
            retry.setVisibility(View.VISIBLE);
        }
    }

    private boolean canAccept(long request) {
        return requests.accepts(request) && !isFinishing() && !isDestroyed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        openTextWhenResumed();
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        requests.invalidate();
        decoder.shutdownNow();
        super.onDestroy();
    }
}

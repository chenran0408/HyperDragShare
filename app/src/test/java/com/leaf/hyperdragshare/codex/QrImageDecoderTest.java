package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class QrImageDecoderTest {
    @Test public void decodesChinesePayloadFromImageContainingPrintedText() throws WriterException {
        Bitmap image = blankImage(700, 600);
        try {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.BLACK);
            paint.setTextSize(38);
            new Canvas(image).drawText("OCR SAMPLE / 图片文字", 30, 60, paint);
            String payload = "二维码：原页复制 / QR 118";
            drawCode(image, payload, 160, 150);
            assertEquals(Collections.singletonList(payload), QrImageDecoder.decode(image));
        } finally {
            image.recycle();
        }
    }

    @Test public void findsMultipleCodesAndReturnsDuplicateContentOnlyOnce() throws WriterException {
        Bitmap image = blankImage(800, 850);
        try {
            drawCode(image, "https://example.com/first", 30, 30);
            drawCode(image, "https://example.com/second", 430, 30);
            drawCode(image, "https://example.com/first", 30, 460);
            List<String> values = QrImageDecoder.decode(image);
            assertEquals(2, values.size());
            assertTrue(values.contains("https://example.com/first"));
            assertTrue(values.contains("https://example.com/second"));
        } finally {
            image.recycle();
        }
    }

    @Test public void blankImageHasNoCodes() {
        Bitmap image = blankImage(400, 400);
        try {
            assertTrue(QrImageDecoder.decode(image).isEmpty());
        } finally {
            image.recycle();
        }
    }

    private static Bitmap blankImage(int width, int height) {
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        image.eraseColor(Color.WHITE);
        return image;
    }

    private static void drawCode(Bitmap image, String text, int left, int top) throws WriterException {
        int side = 320;
        BitMatrix code = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, side, side,
                Collections.singletonMap(EncodeHintType.CHARACTER_SET, "UTF-8"));
        int[] pixels = new int[side * side];
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                pixels[y * side + x] = code.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        image.setPixels(pixels, 0, side, left, top, side, side);
    }
}

package com.leaf.hyperdragshare.codex;

import android.graphics.Bitmap;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** Decodes the supplied software bitmap without taking ownership of its pixels. */
final class QrImageDecoder {
    private QrImageDecoder() {}

    static List<String> decode(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        BinaryBitmap image = new BinaryBitmap(new HybridBinarizer(
                new RGBLuminanceSource(width, height, pixels)));
        Result[] decoded;
        try {
            decoded = new QRCodeMultiReader().decodeMultiple(image,
                    Collections.singletonMap(DecodeHintType.TRY_HARDER, Boolean.TRUE));
        } catch (NotFoundException noCode) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Result result : decoded) values.add(result.getText());
        return new ArrayList<>(values);
    }
}

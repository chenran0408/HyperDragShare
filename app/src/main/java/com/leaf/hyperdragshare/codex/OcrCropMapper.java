package com.leaf.hyperdragshare.codex;

import android.graphics.Rect;

/** Maps a drag selection on a stretched screenshot preview to bitmap pixels. */
final class OcrCropMapper {
    private OcrCropMapper() {}

    static Rect map(
            float startX,
            float startY,
            float endX,
            float endY,
            int viewWidth,
            int viewHeight,
            int bitmapWidth,
            int bitmapHeight) {
        if (viewWidth <= 0 || viewHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) {
            return null;
        }
        float left = Math.min(startX, endX);
        float top = Math.min(startY, endY);
        float right = Math.max(startX, endX);
        float bottom = Math.max(startY, endY);
        if (right <= left || bottom <= top) {
            return null;
        }

        Rect crop = new Rect(
                (int) Math.floor(left * bitmapWidth / viewWidth),
                (int) Math.floor(top * bitmapHeight / viewHeight),
                (int) Math.ceil(right * bitmapWidth / viewWidth),
                (int) Math.ceil(bottom * bitmapHeight / viewHeight));
        if (!crop.intersect(0, 0, bitmapWidth, bitmapHeight)
                || crop.width() <= 0 || crop.height() <= 0) {
            return null;
        }
        return crop;
    }
}

package com.leaf.hyperdragshare.codex;

import android.graphics.Rect;

import java.util.List;

/** A bounded OCR crop around a long press, kept inside its source window. */
final class OcrFallbackRegion {
    private static final int WIDTH_DP = 360;
    private static final int HEIGHT_DP = 220;

    private OcrFallbackRegion() {}

    static Rect around(float x, float y, Rect window, int screenWidth, int screenHeight,
                       float density) {
        if (window == null || !Float.isFinite(x) || !Float.isFinite(y)
                || screenWidth <= 0 || screenHeight <= 0 || !contains(window, x, y)) {
            return null;
        }
        Rect available = new Rect(window);
        if (!available.intersect(0, 0, screenWidth, screenHeight)) {
            return null;
        }
        int width = Math.min(available.width(), Math.max(1, Math.round(WIDTH_DP * density)));
        int height = Math.min(available.height(), Math.max(1, Math.round(HEIGHT_DP * density)));
        int left = clamp(Math.round(x) - width / 2, available.left, available.right - width);
        int top = clamp(Math.round(y) - height / 2, available.top, available.bottom - height);
        return new Rect(left, top, left + width, top + height);
    }

    static boolean overlapsPassword(Rect capture, List<AccessibilityNodeSnapshot> nodes) {
        if (capture == null || nodes == null) {
            return false;
        }
        for (AccessibilityNodeSnapshot node : nodes) {
            if (node != null && node.visible && node.password
                    && Rect.intersects(node.bounds, capture)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(Rect bounds, float x, float y) {
        return x >= bounds.left && x < bounds.right && y >= bounds.top && y < bounds.bottom;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

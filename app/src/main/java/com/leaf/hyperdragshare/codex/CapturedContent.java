package com.leaf.hyperdragshare.codex;

import android.graphics.Bitmap;
import android.graphics.Rect;

/** Immutable, source-neutral content handed to the common share UI. */
final class CapturedContent {
    enum Kind {
        TEXT,
        IMAGE
    }

    final Kind kind;
    final String text;
    final String beforeText;
    final String afterText;
    final Bitmap bitmap;
    final String sourcePackage;
    final Rect sourceBounds;
    final boolean bitmapOwnedByDragShare;
    final boolean pressOcrRegion;

    private CapturedContent(
            Kind kind,
            String text,
            Bitmap bitmap,
            String sourcePackage,
            Rect sourceBounds,
            boolean bitmapOwnedByDragShare,
            boolean pressOcrRegion,
            String beforeText,
            String afterText) {
        this.kind = kind;
        this.text = text;
        this.beforeText = beforeText;
        this.afterText = afterText;
        this.bitmap = bitmap;
        this.sourcePackage = sourcePackage;
        this.sourceBounds = sourceBounds == null ? null : new Rect(sourceBounds);
        this.bitmapOwnedByDragShare = bitmapOwnedByDragShare;
        this.pressOcrRegion = pressOcrRegion;
    }

    static CapturedContent text(String value, String sourcePackage, Rect sourceBounds) {
        return text(value, sourcePackage, sourceBounds, null, null);
    }

    static CapturedContent text(String value, String sourcePackage, Rect sourceBounds,
                                String beforeText, String afterText) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return new CapturedContent(Kind.TEXT, value, null, sourcePackage, sourceBounds,
                false, false, beforeText, afterText);
    }

    static CapturedContent image(
            Bitmap value,
            String sourcePackage,
            Rect sourceBounds,
            boolean bitmapOwnedByDragShare) {
        if (value == null || value.isRecycled()) {
            return null;
        }
        return new CapturedContent(
                Kind.IMAGE,
                null,
                value,
                sourcePackage,
                sourceBounds,
                bitmapOwnedByDragShare,
                false,
                null,
                null);
    }

    static CapturedContent pressOcrRegion(Bitmap value, String sourcePackage, Rect sourceBounds) {
        if (value == null || value.isRecycled()) return null;
        return new CapturedContent(Kind.IMAGE, null, value, sourcePackage, sourceBounds,
                true, true, null, null);
    }

    boolean isImage() {
        return kind == Kind.IMAGE;
    }

    String mimeType() {
        return isImage() ? "image/png" : "text/plain";
    }
}

package com.leaf.hyperdragshare.codex;

import android.graphics.Rect;

/** Filters launcher icons and tiny inline thumbnails from portal image drags. */
final class PortalCapturePolicy {
    private static final float MIN_IMAGE_DP = 48f;

    private PortalCapturePolicy() {}

    static String rejectionReason(String sourcePackage, boolean sourceExcluded, Rect bounds,
                                  float density) {
        if (sourcePackage == null || sourcePackage.trim().isEmpty()) {
            return "missing_source";
        }
        if (sourceExcluded) {
            return "excluded_source";
        }
        if (bounds == null) {
            return null;
        }
        int minimumPx = Math.max(1, Math.round(MIN_IMAGE_DP * Math.max(0.1f, density)));
        if (bounds.width() < minimumPx || bounds.height() < minimumPx) {
            return "small_image";
        }
        return null;
    }
}

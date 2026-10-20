package com.leaf.hyperdragshare.codex;

import java.util.List;

/** Applies the documented editable/text/image selection priority at a point. */
final class AccessibilityCandidateSelector {
    private AccessibilityCandidateSelector() {}

    static Selection select(AccessibilityNodeClassifier.Buckets buckets, float x, float y) {
        if (buckets == null) {
            return null;
        }
        AccessibilityCandidate hit = firstContaining(buckets.webEditable, x, y);
        if (hit != null) return withAdjacent(hit, buckets);
        hit = firstContaining(buckets.nativeEditable, x, y);
        if (hit != null) return withAdjacent(hit, buckets);
        hit = firstContaining(buckets.nativeText, x, y);
        if (hit != null) return withAdjacent(hit, buckets);

        AccessibilityCandidate webText = firstContaining(buckets.webText, x, y);
        AccessibilityCandidate webImage = firstContaining(buckets.webNonText, x, y);
        if (webText != null && webImage != null) {
            return withAdjacent(webImage.isInside(webText) ? webImage : webText, buckets);
        }
        if (webText != null) return withAdjacent(webText, buckets);
        if (webImage != null) return withAdjacent(webImage, buckets);

        hit = firstContaining(buckets.nativeNonText, x, y);
        return hit == null ? null : withAdjacent(hit, buckets);
    }

    private static Selection withAdjacent(AccessibilityCandidate hit, AccessibilityNodeClassifier.Buckets buckets) {
        if (hit.kind != AccessibilityCandidate.Kind.TEXT || hit.editable || hit.sourcePackage == null) {
            return new Selection(hit, null, null);
        }
        List<AccessibilityCandidate> text = hit.insideWebView ? buckets.webText : buckets.nativeText;
        AccessibilityCandidate before = null;
        AccessibilityCandidate after = null;
        for (AccessibilityCandidate candidate : text) {
            if (candidate == hit || candidate.editable
                    || !hit.sourcePackage.equals(candidate.sourcePackage)
                    || hit.text.equals(candidate.text)
                    || candidate.bounds.left >= hit.bounds.right
                    || candidate.bounds.right <= hit.bounds.left) continue;
            // An overlapping parent/child is part of the current block, not a new paragraph.
            if (candidate.bounds.bottom <= hit.bounds.top) {
                if (before == null || candidate.bounds.bottom > before.bounds.bottom
                        || (candidate.bounds.bottom == before.bounds.bottom && candidate.area() < before.area())) {
                    before = candidate;
                }
            } else if (candidate.bounds.top >= hit.bounds.bottom) {
                if (after == null || candidate.bounds.top < after.bounds.top
                        || (candidate.bounds.top == after.bounds.top && candidate.area() < after.area())) {
                    after = candidate;
                }
            }
        }
        return new Selection(hit, before == null ? null : before.text, after == null ? null : after.text);
    }

    private static AccessibilityCandidate firstContaining(
            List<AccessibilityCandidate> candidates,
            float x,
            float y) {
        if (candidates == null) {
            return null;
        }
        for (AccessibilityCandidate candidate : candidates) {
            if (candidate.contains(x, y)) {
                return candidate;
            }
        }
        return null;
    }

    static final class Selection {
        final AccessibilityCandidate candidate;
        final String beforeText;
        final String afterText;

        Selection(AccessibilityCandidate candidate) {
            this(candidate, null, null);
        }

        Selection(AccessibilityCandidate candidate, String beforeText, String afterText) {
            this.candidate = candidate;
            this.beforeText = beforeText;
            this.afterText = afterText;
        }

        boolean isImage() {
            return candidate != null && candidate.kind == AccessibilityCandidate.Kind.IMAGE_REGION;
        }
    }
}

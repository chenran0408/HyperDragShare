package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class PortalCapturePolicyTest {
    @Test
    public void rejectsTinyInlineImageAtTargetDensity() {
        String reason = PortalCapturePolicy.rejectionReason(
                "com.ss.android.article.news", false, new Rect(0, 0, 160, 300), 3.75f);

        assertEquals("small_image", reason);
    }

    @Test
    public void acceptsContentImageAtMinimumSize() {
        String reason = PortalCapturePolicy.rejectionReason(
                "com.ss.android.article.news", false, new Rect(0, 0, 180, 180), 3.75f);

        assertNull(reason);
    }

    @Test
    public void rejectsLauncherBeforeReadingBitmap() {
        String reason = PortalCapturePolicy.rejectionReason(
                "com.miui.home", true, new Rect(0, 0, 360, 360), 3.75f);

        assertEquals("excluded_source", reason);
    }

    @Test
    public void rejectsUnknownSource() {
        assertEquals("missing_source", PortalCapturePolicy.rejectionReason(
                null, false, null, 3.75f));
    }
}

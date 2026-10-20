package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class OcrFallbackRegionTest {
    @Test
    public void centersOnTouchWithoutLeavingSourceWindow() {
        assertEquals(new Rect(140, 290, 860, 730),
                OcrFallbackRegion.around(500, 510,
                        new Rect(100, 100, 1000, 1200), 1080, 2400, 2f));
    }

    @Test
    public void clampsAtScreenAndSourceEdges() {
        assertEquals(new Rect(20, 50, 220, 250),
                OcrFallbackRegion.around(30, 60,
                        new Rect(20, 50, 220, 250), 1080, 2400, 3f));
        assertEquals(new Rect(860, 2180, 1080, 2400),
                OcrFallbackRegion.around(1070, 2390,
                        new Rect(860, 2180, 1300, 2600), 1080, 2400, 2f));
    }

    @Test
    public void rejectsTouchesOutsideKnownWindow() {
        assertNull(OcrFallbackRegion.around(300, 300,
                new Rect(0, 0, 200, 200), 1080, 2400, 2f));
        assertNull(OcrFallbackRegion.around(Float.NaN, 50,
                new Rect(0, 0, 200, 200), 1080, 2400, 2f));
    }

    @Test
    public void refusesRegionThatIncludesPasswordNode() {
        Rect capture = new Rect(100, 100, 500, 400);
        AccessibilityNodeSnapshot password = new AccessibilityNodeSnapshot.Builder()
                .bounds(new Rect(450, 300, 600, 450))
                .password(true)
                .visible(true)
                .build();
        assertTrue(OcrFallbackRegion.overlapsPassword(capture, List.of(password)));
        assertFalse(OcrFallbackRegion.overlapsPassword(
                new Rect(0, 0, 300, 200),
                List.of(new AccessibilityNodeSnapshot.Builder()
                        .bounds(new Rect(450, 300, 600, 450))
                        .password(true)
                        .build())));
    }
}

package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class OcrCropMapperTest {
    @Test
    public void mapsReverseDragToScreenshotPixels() {
        Rect crop = OcrCropMapper.map(80f, 60f, 20f, 10f, 100, 100, 200, 300);

        assertEquals(new Rect(40, 30, 160, 180), crop);
    }

    @Test
    public void clampsDragAtScreenshotEdges() {
        Rect crop = OcrCropMapper.map(-10f, 20f, 50f, 120f, 100, 100, 200, 200);

        assertEquals(new Rect(0, 40, 100, 200), crop);
    }

    @Test
    public void rejectsEmptyOrInvalidSelection() {
        assertNull(OcrCropMapper.map(10f, 10f, 10f, 30f, 100, 100, 200, 200));
        assertNull(OcrCropMapper.map(0f, 0f, 20f, 20f, 0, 100, 200, 200));
    }
}

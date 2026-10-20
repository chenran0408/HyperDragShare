package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class BigBangSettingsTest {
    @Test
    public void interactionOptionsDefaultOffAndPersistIndependently() {
        Context context = RuntimeEnvironment.getApplication();

        assertFalse(BigBangSettings.isSelectionHapticEnabled(context));
        assertFalse(BigBangSettings.isExitAfterCopyEnabled(context));

        BigBangSettings.setSelectionHapticEnabled(context, true);
        BigBangSettings.setExitAfterCopyEnabled(context, true);
        assertTrue(BigBangSettings.isSelectionHapticEnabled(context));
        assertTrue(BigBangSettings.isExitAfterCopyEnabled(context));

        BigBangSettings.setSelectionHapticEnabled(context, false);
        assertFalse(BigBangSettings.isSelectionHapticEnabled(context));
        assertTrue(BigBangSettings.isExitAfterCopyEnabled(context));
    }

    @Test
    public void panelOptionsKeepReferenceCyclesAndPersistValues() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals(15f, BigBangSettings.getTextSizeSp(context), 0f);
        assertEquals(0.6f, BigBangSettings.getPanelHeightFraction(context), 0f);
        assertEquals(1f, BigBangSettings.getPanelOpacity(context), 0f);
        assertFalse(BigBangSettings.isPanelAdjustmentEnabled(context));

        assertEquals(18f, BigBangSettings.nextTextSizeSp(15f), 0f);
        assertEquals(12f, BigBangSettings.nextTextSizeSp(18f), 0f);
        assertEquals(15f, BigBangSettings.nextTextSizeSp(12f), 0f);
        assertEquals(0.8f, BigBangSettings.nextPanelHeightFraction(0.6f), 0f);
        assertEquals(0.45f, BigBangSettings.nextPanelHeightFraction(0.8f), 0f);
        assertEquals(0.85f, BigBangSettings.nextPanelOpacity(1f), 0f);
        assertEquals(0.55f, BigBangSettings.nextPanelOpacity(0.7f), 0f);

        BigBangSettings.setTextSizeSp(context, 18f);
        BigBangSettings.setPanelHeightFraction(context, 0.8f);
        BigBangSettings.setPanelOpacity(context, 0.7f);
        BigBangSettings.setPanelAdjustmentEnabled(context, true);
        BigBangSettings.setPanelPositionDp(context, 12f, -24f);
        assertEquals(18f, BigBangSettings.getTextSizeSp(context), 0f);
        assertEquals(0.8f, BigBangSettings.getPanelHeightFraction(context), 0f);
        assertEquals(0.7f, BigBangSettings.getPanelOpacity(context), 0f);
        assertTrue(BigBangSettings.isPanelAdjustmentEnabled(context));
        assertEquals(12f, BigBangSettings.getPanelOffsetXDp(context), 0f);
        assertEquals(-24f, BigBangSettings.getPanelOffsetYDp(context), 0f);
    }
}

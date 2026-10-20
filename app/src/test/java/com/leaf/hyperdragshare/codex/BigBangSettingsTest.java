package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
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
}

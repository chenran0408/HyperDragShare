package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public final class ThumbTriggerSettingsTest {
    @Test public void oldDeviceUnitThresholdRequiresExplicitRecalibration() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("drag_share_settings", Context.MODE_PRIVATE).edit().clear()
                .putBoolean("thumb_trigger_enabled", true)
                .putFloat("thumb_trigger_threshold", 12f).commit();
        ThumbTriggerSettings old = ThumbTriggerSettings.read(context);
        assertFalse(old.enabled);
        assertFalse(old.calibrated);
        assertTrue(old.requiresRecalibration);
        assertEquals(0f, old.threshold, 0f);

        ThumbTriggerSettings.saveCalibration(context, .4f);
        ThumbTriggerSettings current = ThumbTriggerSettings.read(context);
        assertFalse(current.enabled);
        assertTrue(current.calibrated);
        assertFalse(current.requiresRecalibration);
        assertEquals(.4f, current.threshold, 0f);
    }
}

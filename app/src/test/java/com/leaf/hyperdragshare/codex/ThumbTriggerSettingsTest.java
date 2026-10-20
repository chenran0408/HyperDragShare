package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public final class ThumbTriggerSettingsTest {
    @Test public void providerBundlePreservesCalibrationAndExplicitEnable() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("drag_share_settings", Context.MODE_PRIVATE).edit().clear().commit();
        ThumbTriggerSettings.saveCalibration(context, .4f);
        Bundle disabled = new Bundle();
        ThumbTriggerSettings.appendToBundle(context, disabled);
        assertFalse(ThumbTriggerSettings.fromBundle(disabled).enabled);
        assertTrue(ThumbTriggerSettings.fromBundle(disabled).calibrated);

        ThumbTriggerSettings.setEnabled(context, true);
        Bundle enabled = new Bundle();
        ThumbTriggerSettings.appendToBundle(context, enabled);
        ThumbTriggerSettings settings = ThumbTriggerSettings.fromBundle(enabled);
        assertTrue(settings.enabled);
        assertEquals(.4f, settings.threshold, 0f);
    }

    @Test public void providerBundleNeverEnablesMissingOrOldCalibration() {
        Bundle missing = new Bundle();
        missing.putBoolean("thumb_trigger_enabled", true);
        assertFalse(ThumbTriggerSettings.fromBundle(missing).enabled);
        missing.putFloat("thumb_trigger_threshold", .4f);
        ThumbTriggerSettings old = ThumbTriggerSettings.fromBundle(missing);
        assertFalse(old.enabled);
        assertFalse(old.calibrated);
        assertTrue(old.requiresRecalibration);
    }

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

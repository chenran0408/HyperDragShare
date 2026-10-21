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
    @Test public void providerBundlePreservesDefaultsAndExplicitEnable() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("drag_share_settings", Context.MODE_PRIVATE).edit().clear().commit();
        Bundle disabled = new Bundle();
        ThumbTriggerSettings.appendToBundle(context, disabled);
        ThumbTriggerSettings defaults = ThumbTriggerSettings.fromBundle(disabled);
        assertFalse(defaults.enabled);
        assertFalse(defaults.calibrated);
        assertEquals(.30f, defaults.threshold, 0f);
        assertEquals(.095f, defaults.widthThreshold, 0f);

        ThumbTriggerSettings.setEnabled(context, true);
        Bundle enabled = new Bundle();
        ThumbTriggerSettings.appendToBundle(context, enabled);
        ThumbTriggerSettings settings = ThumbTriggerSettings.fromBundle(enabled);
        assertTrue(settings.enabled);
        assertFalse(settings.calibrated);
        assertEquals(ThumbTriggerSettings.read(context).threshold, settings.threshold, 0f);
        assertEquals(ThumbTriggerSettings.read(context).widthThreshold, settings.widthThreshold, 0f);
    }

    @Test public void missingOrOldCalibrationDoesNotDisableTheDefaultThresholds() {
        Bundle missing = new Bundle();
        missing.putBoolean("thumb_trigger_enabled", true);
        ThumbTriggerSettings defaults = ThumbTriggerSettings.fromBundle(missing);
        assertTrue(defaults.enabled);
        assertFalse(defaults.calibrated);
        assertEquals(.30f, defaults.threshold, 0f);
        assertEquals(.095f, defaults.widthThreshold, 0f);
        missing.putFloat("thumb_trigger_threshold", .4f);
        ThumbTriggerSettings old = ThumbTriggerSettings.fromBundle(missing);
        assertTrue(old.enabled);
        assertFalse(old.calibrated);
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("drag_share_settings", Context.MODE_PRIVATE).edit().clear()
                .putBoolean("thumb_trigger_enabled", true)
                .putFloat("thumb_trigger_threshold", 12f).commit();
        ThumbTriggerSettings oldDeviceUnits = ThumbTriggerSettings.read(context);
        assertTrue(oldDeviceUnits.enabled);
        assertFalse(oldDeviceUnits.calibrated);
        assertEquals(.30f, old.threshold, 0f);
        assertEquals(.095f, old.widthThreshold, 0f);
        assertEquals(.30f, oldDeviceUnits.threshold, 0f);
        assertEquals(.095f, oldDeviceUnits.widthThreshold, 0f);
    }

    @Test public void savingCalibrationPreservesTheSwitchAndProviderThresholds() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("drag_share_settings", Context.MODE_PRIVATE).edit().clear().commit();
        ThumbTriggerSettings.saveCalibration(context, .4f);
        ThumbTriggerSettings current = ThumbTriggerSettings.read(context);
        assertFalse(current.enabled);
        assertTrue(current.calibrated);
        assertEquals(.4f, current.threshold, 0f);
        assertEquals(.4f, current.widthThreshold, 0f);
        ThumbTriggerSettings.setEnabled(context, true);
        ThumbTriggerSettings.saveCalibration(context, .45f);
        Bundle enabled = new Bundle();
        ThumbTriggerSettings.appendToBundle(context, enabled);
        ThumbTriggerSettings saved = ThumbTriggerSettings.fromBundle(enabled);
        assertTrue(saved.enabled);
        assertTrue(saved.calibrated);
        assertEquals(.45f, saved.threshold, 0f);
        assertEquals(.45f, saved.widthThreshold, 0f);
        assertEquals(ThumbTriggerSettings.read(context).threshold, saved.threshold, 0f);
        assertEquals(ThumbTriggerSettings.read(context).widthThreshold, saved.widthThreshold, 0f);
    }
}

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
    @Test public void missingCalibrationDoesNotBlockEnableOrProviderRoundTrip() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("drag_share_settings", Context.MODE_PRIVATE).edit().clear().commit();
        assertFalse(ThumbTriggerSettings.read(context).enabled);
        ThumbTriggerSettings.setEnabled(context, true);
        Bundle result = new Bundle();
        ThumbTriggerSettings.appendToBundle(context, result);
        assertTrue(ThumbTriggerSettings.fromBundle(result).enabled);
        ThumbTriggerSettings.setEnabled(context, false);
        assertFalse(ThumbTriggerSettings.read(context).enabled);
    }
}

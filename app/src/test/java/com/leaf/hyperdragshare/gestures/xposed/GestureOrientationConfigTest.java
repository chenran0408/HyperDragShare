package com.leaf.hyperdragshare.gestures.xposed;

import com.leaf.hyperdragshare.gestures.config.ConfigContract;

import android.os.Bundle;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class GestureOrientationConfigTest {
    @Test
    public void bottomStagesAndLaunchModesAreIndependentByOrientation() {
        GestureConfig config = GestureConfig.defaults();
        config.bottomPortraitEnabled = true;
        config.bottomLandscapeEnabled = false;
        config.bottomPortraitSecondStageEnabled = true;
        config.bottomLandscapeSecondStageEnabled = false;
        config.bottomPortraitFullscreen = false;
        config.bottomLandscapeFullscreen = true;
        config.bottomPortraitHoneycombFreeform = true;
        config.bottomLandscapeHoneycombFreeform = false;

        assertTrue(config.bottomEnabledFor(false));
        assertFalse(config.bottomEnabledFor(true));
        assertTrue(config.bottomSecondStageEnabledFor(false));
        assertFalse(config.bottomSecondStageEnabledFor(true));
        assertFalse(config.bottomFullscreenFor(false));
        assertTrue(config.bottomFullscreenFor(true));
        assertTrue(config.bottomHoneycombFreeformFor(false));
        assertFalse(config.bottomHoneycombFreeformFor(true));
    }

    @Test
    public void sideKeepsOneStageWithIndependentLayoutAndLaunchMode() {
        GestureConfig config = GestureConfig.defaults();
        config.sidePortraitLayoutMode = ConfigContract.SIDE_LAYOUT_RING;
        config.sideLandscapeLayoutMode = ConfigContract.SIDE_LAYOUT_TASKS;
        config.sidePortraitFullscreen = false;
        config.sideLandscapeFullscreen = true;

        assertEquals(ConfigContract.SIDE_LAYOUT_RING, config.sideLayoutModeFor(false));
        assertEquals(ConfigContract.SIDE_LAYOUT_TASKS, config.sideLayoutModeFor(true));
        assertFalse(config.sideFullscreenFor(false));
        assertTrue(config.sideFullscreenFor(true));
    }

    @Test
    public void bottomCornerSettingsDefaultOnAndLoadIndependently() {
        GestureConfig defaults = GestureConfig.from(new Bundle());
        assertTrue(defaults.bottomLeftEnabled);
        assertTrue(defaults.bottomRightEnabled);

        Bundle values = new Bundle();
        values.putBoolean(ConfigContract.KEY_BOTTOM_LEFT_ENABLED, false);
        GestureConfig config = GestureConfig.from(values);
        assertFalse(config.bottomCornerEnabled(GestureGeometry.Corner.LEFT));
        assertTrue(config.bottomCornerEnabled(GestureGeometry.Corner.RIGHT));

        values.putBoolean(ConfigContract.KEY_BOTTOM_RIGHT_ENABLED, false);
        config = GestureConfig.from(values);
        assertFalse(config.bottomCornerEnabled(GestureGeometry.Corner.LEFT));
        assertFalse(config.bottomCornerEnabled(GestureGeometry.Corner.RIGHT));
    }
}

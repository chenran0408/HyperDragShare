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
    public void hotBottomGapLoadsSeparatelyFromLegacyHeightAndDefaultsToZero() {
        GestureConfig legacy = GestureConfig.from(new Bundle());
        assertEquals(ConfigContract.DEFAULT_HOT_HEIGHT_PERCENT, legacy.hotHeightPercent);
        assertEquals(0, legacy.hotBottomGapDp);

        Bundle values = new Bundle();
        values.putInt(ConfigContract.KEY_HOT_HEIGHT_PERCENT, 15);
        values.putInt(ConfigContract.KEY_HOT_BOTTOM_GAP_DP, 48);
        GestureConfig configured = GestureConfig.from(values);
        assertEquals(15, configured.hotHeightPercent);
        assertEquals(48, configured.hotBottomGapDp);
    }

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

    @Test
    public void outsideMasterSwitchGatesBothOrientationsWithoutOverridingOrientationSettings() {
        Bundle values = new Bundle();
        values.putBoolean(ConfigContract.KEY_OUTSIDE_ENABLED, false);
        values.putBoolean(ConfigContract.KEY_OUTSIDE_PORTRAIT_ENABLED, true);
        values.putBoolean(ConfigContract.KEY_OUTSIDE_LANDSCAPE_ENABLED, true);
        GestureConfig disabled = GestureConfig.from(values);
        assertFalse(disabled.outsideEnabledFor(false));
        assertFalse(disabled.outsideEnabledFor(true));

        values.putBoolean(ConfigContract.KEY_OUTSIDE_ENABLED, true);
        values.putBoolean(ConfigContract.KEY_OUTSIDE_LANDSCAPE_ENABLED, false);
        GestureConfig enabledByOrientation = GestureConfig.from(values);
        assertTrue(enabledByOrientation.outsideEnabledFor(false));
        assertFalse(enabledByOrientation.outsideEnabledFor(true));
    }

    @Test
    public void sideEntryRequiresEnabledOrientationAndTargetsForSelectedLayout() {
        Bundle values = new Bundle();
        values.putBoolean(ConfigContract.KEY_SIDE_GESTURE_ENABLED, true);
        values.putBoolean(ConfigContract.KEY_SIDE_PORTRAIT_ENABLED, true);
        values.putBoolean(ConfigContract.KEY_SIDE_LANDSCAPE_ENABLED, false);
        values.putInt(ConfigContract.KEY_SIDE_PORTRAIT_LAYOUT_MODE,
                ConfigContract.SIDE_LAYOUT_FAN);
        values.putInt(ConfigContract.KEY_SIDE_LANDSCAPE_LAYOUT_MODE,
                ConfigContract.SIDE_LAYOUT_TASKS);
        GestureConfig config = GestureConfig.from(values);

        assertFalse(config.sideHasTargetsFor(false, 0, 0));
        assertTrue(config.sideHasTargetsFor(false, 1, 0));
        assertFalse(config.sideHasTargetsFor(true, 0, 0));

        values.putBoolean(ConfigContract.KEY_SIDE_GESTURE_ENABLED, false);
        GestureConfig masterDisabled = GestureConfig.from(values);
        assertFalse(masterDisabled.sideHasTargetsFor(false, 1, 0));
        values.putBoolean(ConfigContract.KEY_SIDE_GESTURE_ENABLED, true);

        config.sideLandscapeEnabled = true;
        assertTrue(config.sideHasTargetsFor(true, 0, 0));

        config.sidePortraitLayoutMode = ConfigContract.SIDE_LAYOUT_HONEYCOMB;
        assertFalse(config.sideHasTargetsFor(false, 3, 0));
        assertTrue(config.sideHasTargetsFor(false, 0, 1));
        values.putBoolean(ConfigContract.KEY_HONEYCOMB_ENABLED, false);
        GestureConfig disabledHoneycomb = GestureConfig.from(values);
        assertFalse(disabledHoneycomb.sideHasTargetsFor(false, 0, 1));
    }

    @Test
    public void configReloadKeepsSideGesturesIndependentOfBottomTargetCount() {
        Bundle values = new Bundle();
        values.putBoolean(ConfigContract.KEY_ENABLED, true);
        values.putBoolean(ConfigContract.KEY_SIDE_GESTURE_ENABLED, true);
        values.putBoolean(ConfigContract.KEY_SIDE_PORTRAIT_ENABLED, true);
        values.putBoolean(ConfigContract.KEY_SIDE_LANDSCAPE_ENABLED, false);
        values.putInt(ConfigContract.KEY_SIDE_PORTRAIT_LAYOUT_MODE,
                ConfigContract.SIDE_LAYOUT_FAN);
        GestureConfig sideFan = GestureConfig.from(values);

        assertFalse(sideFan.shouldResetGestureForConfig(true, false, false,
                0, 1, 0));

        values.putInt(ConfigContract.KEY_SIDE_PORTRAIT_LAYOUT_MODE,
                ConfigContract.SIDE_LAYOUT_TASKS);
        GestureConfig sideTasks = GestureConfig.from(values);
        assertFalse(sideTasks.shouldResetGestureForConfig(true, false, false,
                0, 0, 0));

        assertTrue(sideTasks.shouldResetGestureForConfig(false, true, false,
                0, 0, 0));
        assertFalse(sideTasks.shouldResetGestureForConfig(false, false, false,
                0, 0, 0));

        values.putBoolean(ConfigContract.KEY_SIDE_GESTURE_ENABLED, false);
        GestureConfig sideDisabled = GestureConfig.from(values);
        assertTrue(sideDisabled.shouldResetGestureForConfig(true, false, false,
                0, 0, 0));

        values.putBoolean(ConfigContract.KEY_SIDE_GESTURE_ENABLED, true);
        values.putInt(ConfigContract.KEY_SIDE_PORTRAIT_LAYOUT_MODE,
                ConfigContract.SIDE_LAYOUT_FAN);
        GestureConfig sideTargetsMissing = GestureConfig.from(values);
        assertTrue(sideTargetsMissing.shouldResetGestureForConfig(true, false,
                false, 0, 0, 0));
    }
}

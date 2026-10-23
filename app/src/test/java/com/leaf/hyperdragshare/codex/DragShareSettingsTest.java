package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import android.content.res.Configuration;
import android.content.Context;
import android.os.Bundle;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class DragShareSettingsTest {
    @Test public void menuDirectionsPreserveAllSupportedValuesAndRejectUnknownValues() {
        for (int position = DragShareSettings.SIMPLE_MENU_POSITION_TOP;
                position <= DragShareSettings.SIMPLE_MENU_POSITION_DRAG_EDGE; position++) {
            Bundle stored = new Bundle();
            stored.putInt("simple_menu_position", position);
            DragShareSettings settings = DragShareSettings.fromBundle(stored);
            assertEquals(position, settings.simpleMenuPosition);
            assertEquals(position, settings.toBundle().getInt("simple_menu_position"));
        }
        Bundle invalid = new Bundle();
        invalid.putInt("simple_menu_position", 99);
        assertEquals(DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                DragShareSettings.fromBundle(invalid).simpleMenuPosition);
    }
    @Test
    public void defaultsUseModernStyleAndFullIconOpacity() {
        DragShareSettings settings = DragShareSettings.defaults();

        assertEquals(DragShareSettings.COLOR_LIGHT, settings.colorMode);
        assertEquals(
                DragShareSettings.CONTENT_CAPTURE_PORTAL,
                settings.contentCaptureMode);
        assertEquals(DragShareSettings.STYLE_MODERN, settings.uiStyle);
        assertEquals(DragShareSettings.STYLE_MODERN, DragShareSettings.DEFAULT_UI_STYLE);
        assertEquals(
                DragShareSettings.STYLE_MODERN,
                DragShareSettings.fromBundle(new Bundle()).uiStyle);
        assertEquals(DragShareSettings.DEFAULT_EDGE_TRIGGER_DP, settings.edgeTriggerDp);
        assertEquals(
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                settings.scrollSpeedDpPerSecond);
        assertEquals(
                DragShareSettings.DEFAULT_BLOCK_BACKGROUND_SCROLL,
                settings.blockBackgroundScroll);
        assertEquals(
                DragShareSettings.DEFAULT_TEXT_SHARING_ENABLED,
                settings.textSharingEnabled);
        assertEquals(
                DragShareSettings.DEFAULT_IMAGE_SHARING_ENABLED,
                settings.imageSharingEnabled);
        assertTrue(settings.isTargetVisible(DragShareSettings.TARGET_COPY_TEXT));
        assertTrue(settings.isTargetVisible(DragShareSettings.TARGET_COPY_IMAGE));
        assertEquals(
                DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                settings.simpleMenuPosition);
        assertEquals(
                DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
                settings.simpleMenuOpacityPercent);
        assertEquals(
                DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                settings.simpleMenuCornerRadiusDp);
        assertEquals(
                DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
                settings.simpleMenuEdgeDistanceDp);
        assertEquals(
                DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
                settings.iconOpacityPercent);
        assertEquals(100, DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT);
        assertEquals(100, settings.iconOpacityPercent);
        assertEquals(
                DragShareSettings.DEFAULT_MODERN_BLUR_RADIUS_DP,
                settings.modernBlurRadiusDp);
        assertEquals(
                DragShareSettings.DEFAULT_MODERN_GLASS_OPACITY_PERCENT,
                settings.modernGlassOpacityPercent);
        assertTrue(settings.closeMenuWhenPointerLeaves);
        assertTrue(settings.hiddenTargetKeys.isEmpty());
        assertTrue(settings.targetOrder.isEmpty());
        assertFalse(settings.accessibilityLandscapeRecognitionEnabled);
        assertTrue(settings.accessibilityBlacklistedPackages.isEmpty());
        assertEquals(
                DragShareSettings.DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
                settings.accessibilityLongPressTimeoutMillis);
        assertEquals(
                DragShareSettings.DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
                settings.accessibilityRecognitionSensitivityPercent);
        assertEquals(DragShareSettings.LOG_LEVEL_INFO, settings.logLevel);
        assertEquals(DragShareSettings.LOG_DESTINATION_SYSTEM, settings.logDestination);
        assertFalse(settings.pauseAutomaticInGames);
        assertEquals(600, settings.resolvePressConfirmationTimeoutMillis());
        assertEquals(1f, settings.accessibilityTouchSlopMultiplier(), 0f);
        assertFalse(settings.isAccessibilityRecognitionEnabledForOrientation(
                Configuration.ORIENTATION_LANDSCAPE));
        assertTrue(settings.isAccessibilityRecognitionEnabledForOrientation(
                Configuration.ORIENTATION_PORTRAIT));
    }

    @Test
    public void loggingAndGamePauseSettingsSurvivePreferencesAndProviderBundle() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
                DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
                DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
                true,
                new LinkedHashSet<>(),
                Arrays.asList(),
                DragShareSettings.CONTENT_CAPTURE_PORTAL,
                false,
                new LinkedHashSet<>(),
                DragShareSettings.DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
                DragShareSettings.DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
                DragShareSettings.DEFAULT_MODERN_BLUR_RADIUS_DP,
                DragShareSettings.DEFAULT_MODERN_GLASS_OPACITY_PERCENT,
                DragShareSettings.LOG_LEVEL_DEBUG,
                DragShareSettings.LOG_DESTINATION_FILE,
                true);

        DragShareSettings roundTripped = DragShareSettings.fromBundle(settings.toBundle());
        assertEquals(DragShareSettings.LOG_LEVEL_DEBUG, roundTripped.logLevel);
        assertEquals(DragShareSettings.LOG_DESTINATION_FILE, roundTripped.logDestination);
        assertTrue(roundTripped.pauseAutomaticInGames);
        assertFalse(DragShareSettings.fromBundle(new Bundle()).pauseAutomaticInGames);
        Context context = RuntimeEnvironment.getApplication();
        DragShareSettings previous = DragShareSettings.readLocal(context);
        try {
            settings.saveLocal(context);
            DragShareSettings saved = DragShareSettings.readLocal(context);
            assertEquals(DragShareSettings.LOG_LEVEL_DEBUG, saved.logLevel);
            assertEquals(DragShareSettings.LOG_DESTINATION_FILE, saved.logDestination);
            assertTrue(saved.pauseAutomaticInGames);
        } finally {
            previous.saveLocal(context);
        }

        DragShareSettings invalid = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
                DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
                DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
                true,
                new LinkedHashSet<>(),
                Arrays.asList(),
                DragShareSettings.CONTENT_CAPTURE_PORTAL,
                false,
                new LinkedHashSet<>(),
                DragShareSettings.DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
                DragShareSettings.DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
                DragShareSettings.DEFAULT_MODERN_BLUR_RADIUS_DP,
                DragShareSettings.DEFAULT_MODERN_GLASS_OPACITY_PERCENT,
                99,
                99,
                false);
        assertEquals(DragShareSettings.LOG_LEVEL_INFO, invalid.logLevel);
        assertEquals(DragShareSettings.LOG_DESTINATION_SYSTEM, invalid.logDestination);
    }

    @Test
    public void invalidValuesAreClampedToSupportedRanges() {
        DragShareSettings settings = new DragShareSettings(
                99,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE);

        assertEquals(DragShareSettings.COLOR_LIGHT, settings.colorMode);
        assertEquals(DragShareSettings.DEFAULT_UI_STYLE, settings.uiStyle);
        assertEquals(DragShareSettings.MIN_EDGE_TRIGGER_DP, settings.edgeTriggerDp);
        assertEquals(
                DragShareSettings.MAX_SCROLL_SPEED_DP_PER_SECOND,
                settings.scrollSpeedDpPerSecond);
    }

    @Test
    public void systemThemeSurvivesPreferencesAndProviderBundle() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_SYSTEM,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND);
        Context context = RuntimeEnvironment.getApplication();
        DragShareSettings previous = DragShareSettings.readLocal(context);
        try {
            settings.saveLocal(context);
            assertEquals(DragShareSettings.COLOR_SYSTEM,
                    DragShareSettings.readLocal(context).colorMode);
            assertEquals(DragShareSettings.COLOR_SYSTEM,
                    DragShareSettings.fromBundle(settings.toBundle()).colorMode);
        } finally {
            previous.saveLocal(context);
        }
    }

    @Test
    public void systemThemeFollowsNightModeAndExplicitThemesKeepTheirChoice() {
        int day = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_NO;
        int night = Configuration.UI_MODE_TYPE_NORMAL | Configuration.UI_MODE_NIGHT_YES;
        for (int mode : new int[] {DragShareSettings.COLOR_LIGHT,
                DragShareSettings.COLOR_DARK, DragShareSettings.COLOR_SYSTEM}) {
            DragShareSettings settings = new DragShareSettings(mode,
                    DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                    DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND);
            assertEquals(mode == DragShareSettings.COLOR_DARK, settings.isDarkTheme(day));
            assertEquals(mode != DragShareSettings.COLOR_LIGHT, settings.isDarkTheme(night));
        }
        DragShareSettings system = new DragShareSettings(DragShareSettings.COLOR_SYSTEM,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND);
        Context context = RuntimeEnvironment.getApplication();
        assertEquals(system.isDarkTheme(context.getResources().getConfiguration().uiMode),
                system.isDarkTheme(context));
    }

    @Test
    public void darkModeAndUpperEdgeValuesAreRetained() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_DARK,
                DragShareSettings.STYLE_PORTAL,
                DragShareSettings.MAX_EDGE_TRIGGER_DP,
                DragShareSettings.MAX_SCROLL_SPEED_DP_PER_SECOND,
                true);

        assertEquals(DragShareSettings.COLOR_DARK, settings.colorMode);
        assertEquals(DragShareSettings.STYLE_PORTAL, settings.uiStyle);
        assertEquals(DragShareSettings.MAX_EDGE_TRIGGER_DP, settings.edgeTriggerDp);
        assertEquals(
                DragShareSettings.MAX_SCROLL_SPEED_DP_PER_SECOND,
                settings.scrollSpeedDpPerSecond);
        assertEquals(true, settings.blockBackgroundScroll);
    }

    @Test
    public void circleStyleIsAcceptedAndPreserved() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_CIRCLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false);

        assertEquals(DragShareSettings.STYLE_CIRCLE, settings.uiStyle);
    }

    @Test
    public void modernStyleAndBlurParametersAreClampedAndRoundTrip() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_DARK,
                DragShareSettings.STYLE_MODERN,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
                DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
                DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
                true,
                new LinkedHashSet<>(),
                Arrays.asList(),
                DragShareSettings.CONTENT_CAPTURE_PORTAL,
                false,
                new LinkedHashSet<>(),
                DragShareSettings.DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
                DragShareSettings.DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
                Integer.MAX_VALUE,
                Integer.MIN_VALUE);

        assertTrue(settings.isModernStyle());
        assertEquals(
                DragShareSettings.MAX_MODERN_BLUR_RADIUS_DP,
                settings.modernBlurRadiusDp);
        assertEquals(
                DragShareSettings.MIN_MODERN_GLASS_OPACITY_PERCENT,
                settings.modernGlassOpacityPercent);
        DragShareSettings fromBundle = DragShareSettings.fromBundle(settings.toBundle());
        assertEquals(DragShareSettings.STYLE_MODERN, fromBundle.uiStyle);
        assertEquals(settings.modernBlurRadiusDp, fromBundle.modernBlurRadiusDp);
        assertEquals(settings.modernGlassOpacityPercent, fromBundle.modernGlassOpacityPercent);
    }

    @Test
    public void sharingSwitchesAndTargetRulesAreRetained() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                false,
                true,
                new LinkedHashSet<>(Arrays.asList("pkg/.Hidden")),
                Arrays.asList("pkg/.Second", "pkg/.First"));

        assertFalse(settings.isSharingEnabled(false));
        assertTrue(settings.isSharingEnabled(true));
        assertFalse(settings.isTargetVisible("pkg/.Hidden"));
        assertTrue(settings.isTargetVisible("pkg/.Visible"));
        assertEquals(
                Arrays.asList("pkg/.Second", "pkg/.First"),
                settings.targetOrder);
    }

    @Test
    public void simpleMenuOptionsAreClamped() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                99,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                false,
                new LinkedHashSet<>(),
                Arrays.asList());

        assertEquals(DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION, settings.simpleMenuPosition);
        assertEquals(
                DragShareSettings.MIN_SIMPLE_MENU_OPACITY_PERCENT,
                settings.simpleMenuOpacityPercent);
        assertEquals(
                DragShareSettings.MAX_SIMPLE_MENU_CORNER_RADIUS_DP,
                settings.simpleMenuCornerRadiusDp);
        assertFalse(settings.closeMenuWhenPointerLeaves);
    }

    @Test
    public void portalSettingsRetainSimpleMenuBackgroundOpacity() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                DragShareSettings.MIN_SIMPLE_MENU_OPACITY_PERCENT,
                DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
                DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
                true,
                new LinkedHashSet<>(),
                Arrays.asList(),
                DragShareSettings.CONTENT_CAPTURE_PORTAL);

        DragShareSettings portalSettings = DragShareSettings.fromBundle(settings.toBundle());

        assertTrue(portalSettings.isPortalCaptureMode());
        assertEquals(
                DragShareSettings.MIN_SIMPLE_MENU_OPACITY_PERCENT,
                portalSettings.simpleMenuOpacityPercent);
        assertEquals(
                0.2f,
                DragShareController.simpleMenuBackgroundOpacityFraction(
                        portalSettings.simpleMenuOpacityPercent),
                0f);
    }

    @Test
    public void edgeDistanceAndIconOpacityAreClamped() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
                DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                Integer.MAX_VALUE,
                Integer.MIN_VALUE,
                true,
                new LinkedHashSet<>(),
                Arrays.asList());

        assertEquals(
                DragShareSettings.MAX_SIMPLE_MENU_EDGE_DISTANCE_DP,
                settings.simpleMenuEdgeDistanceDp);
        assertEquals(
                DragShareSettings.MIN_ICON_OPACITY_PERCENT,
                settings.iconOpacityPercent);
    }

    @Test
    public void hiddenTargetsDoNotEnterSettingsOrder() {
        ShareTarget hidden = ShareTarget.saveToLocal(null);
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                new LinkedHashSet<>(Arrays.asList(hidden.key())),
                Arrays.asList(hidden.key()));

        List<ShareTarget> ordered = ShareTargetRepository.orderForSettings(
                Arrays.asList(hidden),
                settings);
        assertTrue(ordered.isEmpty());
    }

    @Test
    public void builtInActionsAppearForMatchingPayloads() {
        DragShareSettings settings = DragShareSettings.defaults();

        List<ShareTarget> textTargets = ShareTargetRepository.applySettings(
                null,
                java.util.Collections.emptyList(),
                settings,
                false);
        List<ShareTarget> imageTargets = ShareTargetRepository.applySettings(
                null,
                java.util.Collections.emptyList(),
                settings,
                true);

        assertEquals(3, textTargets.size());
        assertTrue(textTargets.get(0).isStash());
        assertTrue(textTargets.get(1).isCopyTextToClipboard());
        assertTrue(textTargets.get(2).isTextSegmentation());
        assertEquals(5, imageTargets.size());
        assertTrue(imageTargets.get(0).isStash());
        assertTrue(imageTargets.get(1).isCopyImageToClipboard());
        assertTrue(imageTargets.get(2).isSaveToLocal());
        assertTrue(imageTargets.get(3).isQrScan());
        assertTrue(imageTargets.get(4).isImageOcr());
    }

    @Test
    public void hiddenOcrActionStaysHiddenAfterSettingsTransfer() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false, true, true,
                new LinkedHashSet<>(Arrays.asList(DragShareSettings.TARGET_IMAGE_OCR)),
                Arrays.asList());
        List<ShareTarget> targets = ShareTargetRepository.applySettings(
                null, java.util.Collections.emptyList(),
                DragShareSettings.fromBundle(settings.toBundle()), true);
        assertEquals(4, targets.size());
        assertTrue(targets.get(0).isStash());
        assertTrue(targets.get(1).isCopyImageToClipboard());
        assertTrue(targets.get(2).isSaveToLocal());
        assertTrue(targets.get(3).isQrScan());
    }

    @Test
    public void accessibilityCaptureModeRoundTripsThroughBundle() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
                DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
                DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
                true,
                new LinkedHashSet<>(),
                Arrays.asList(),
                DragShareSettings.CONTENT_CAPTURE_ACCESSIBILITY,
                true,
                new LinkedHashSet<>(Arrays.asList("pkg.accessibility.blacklisted")),
                700,
                150);

        assertTrue(settings.isAccessibilityCaptureMode());
        assertTrue(settings.accessibilityLandscapeRecognitionEnabled);
        assertTrue(settings.isAccessibilityRecognitionEnabledForOrientation(
                Configuration.ORIENTATION_LANDSCAPE));
        assertTrue(settings.isAccessibilityPackageBlacklisted("pkg.accessibility.blacklisted"));
        assertEquals(700, settings.accessibilityLongPressTimeoutMillis);
        assertEquals(700, settings.resolvePressConfirmationTimeoutMillis());
        assertEquals(150, settings.accessibilityRecognitionSensitivityPercent);
        assertEquals(1.5f, settings.accessibilityTouchSlopMultiplier(), 0f);
        assertEquals(
                DragShareSettings.CONTENT_CAPTURE_ACCESSIBILITY,
                DragShareSettings.fromBundle(settings.toBundle()).contentCaptureMode);
        assertTrue(DragShareSettings.fromBundle(settings.toBundle())
                .accessibilityLandscapeRecognitionEnabled);
        assertTrue(DragShareSettings.fromBundle(settings.toBundle())
                .isAccessibilityPackageBlacklisted("pkg.accessibility.blacklisted"));
        assertEquals(700, DragShareSettings.fromBundle(settings.toBundle())
                .accessibilityLongPressTimeoutMillis);
        assertEquals(150, DragShareSettings.fromBundle(settings.toBundle())
                .accessibilityRecognitionSensitivityPercent);
    }

    @Test
    public void legacyCopyPreferencesMigrateToSeparateVisibleTargets() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
                DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
                DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
                true,
                new LinkedHashSet<>(),
                Arrays.asList(),
                DragShareSettings.CONTENT_CAPTURE_PORTAL,
                false,
                new LinkedHashSet<>(),
                DragShareSettings.DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
                DragShareSettings.DEFAULT_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
                false,
                true);

        assertFalse(settings.isTargetVisible(DragShareSettings.TARGET_COPY_TEXT));
        assertTrue(settings.isTargetVisible(DragShareSettings.TARGET_COPY_IMAGE));
        List<ShareTarget> textTargets = ShareTargetRepository.applySettings(
                null,
                java.util.Collections.emptyList(),
                settings,
                false);
        List<ShareTarget> imageTargets = ShareTargetRepository.applySettings(
                null,
                java.util.Collections.emptyList(),
                settings,
                true);
        assertEquals(2, textTargets.size());
        assertTrue(textTargets.get(0).isStash());
        assertTrue(textTargets.get(1).isTextSegmentation());
        assertEquals(5, imageTargets.size());
        assertTrue(imageTargets.get(0).isStash());
        assertTrue(imageTargets.get(1).isCopyImageToClipboard());
        assertTrue(imageTargets.get(2).isSaveToLocal());
        assertTrue(imageTargets.get(3).isQrScan());
        assertTrue(imageTargets.get(4).isImageOcr());
        assertFalse(DragShareSettings.fromBundle(settings.toBundle())
                .isTargetVisible(DragShareSettings.TARGET_COPY_TEXT));
        assertTrue(DragShareSettings.fromBundle(settings.toBundle())
                .isTargetVisible(DragShareSettings.TARGET_COPY_IMAGE));
        assertTrue(DragShareSettings.fromBundle(new Bundle())
                .isTargetVisible(DragShareSettings.TARGET_COPY_TEXT));
        assertTrue(DragShareSettings.fromBundle(new Bundle())
                .isTargetVisible(DragShareSettings.TARGET_COPY_IMAGE));

        DragShareSettings sharedCopyHidden = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                new LinkedHashSet<>(Arrays.asList(DragShareSettings.TARGET_COPY)),
                Arrays.asList());
        assertFalse(sharedCopyHidden.isTargetVisible(DragShareSettings.TARGET_COPY_TEXT));
        assertFalse(sharedCopyHidden.isTargetVisible(DragShareSettings.TARGET_COPY_IMAGE));
    }

    @Test
    public void accessibilityGestureValuesAreClamped() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
                DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
                DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
                true,
                new LinkedHashSet<>(),
                Arrays.asList(),
                DragShareSettings.CONTENT_CAPTURE_ACCESSIBILITY,
                false,
                new LinkedHashSet<>(),
                Integer.MIN_VALUE,
                Integer.MAX_VALUE);

        assertEquals(
                DragShareSettings.MIN_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
                settings.accessibilityLongPressTimeoutMillis);
        assertEquals(
                DragShareSettings.MAX_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT,
                settings.accessibilityRecognitionSensitivityPercent);
    }

    @Test
    public void legacyFollowSystemSentinelMigratesToExplicitDefault() {
        Bundle legacy = new Bundle();
        legacy.putInt("accessibility_long_press_timeout_millis", 0);
        DragShareSettings migrated = DragShareSettings.fromBundle(legacy);

        assertEquals(600, migrated.accessibilityLongPressTimeoutMillis);
        assertEquals(600, migrated.toBundle().getInt("accessibility_long_press_timeout_millis"));
        assertEquals(600, migrated.resolvePressConfirmationTimeoutMillis());
    }

    @Test
    public void readingLegacyLocalPreferencePersistsTheMigratedDefault() {
        Context context = RuntimeEnvironment.getApplication();
        DragShareSettings previous = DragShareSettings.readLocal(context);
        android.content.SharedPreferences preferences = context.getSharedPreferences(
                "drag_share_settings", Context.MODE_PRIVATE);
        try {
            preferences.edit().putInt("accessibility_long_press_timeout_millis", 0).commit();

            DragShareSettings migrated = DragShareSettings.readLocal(context);

            assertEquals(600, migrated.accessibilityLongPressTimeoutMillis);
            assertEquals(600, preferences.getInt("accessibility_long_press_timeout_millis", -1));
        } finally {
            previous.saveLocal(context);
        }
    }

    @Test
    public void validCustomPressConfirmationSurvivesBundlesAndExpandedRange() {
        for (int value : new int[] {250, 700, 1200, 1800, 3000}) {
            Bundle stored = new Bundle();
            stored.putInt("accessibility_long_press_timeout_millis", value);
            DragShareSettings settings = DragShareSettings.fromBundle(stored);

            assertEquals(value, settings.accessibilityLongPressTimeoutMillis);
            assertEquals(value, settings.toBundle().getInt(
                    "accessibility_long_press_timeout_millis"));
            assertEquals(value, settings.resolvePressConfirmationTimeoutMillis());
        }
    }

    @Test
    public void customPressConfirmationClampsToExpandedEndpoints() {
        Bundle below = new Bundle();
        below.putInt("accessibility_long_press_timeout_millis", 120);
        assertEquals(250, DragShareSettings.fromBundle(below).accessibilityLongPressTimeoutMillis);

        Bundle above = new Bundle();
        above.putInt("accessibility_long_press_timeout_millis", 4500);
        assertEquals(3000, DragShareSettings.fromBundle(above).accessibilityLongPressTimeoutMillis);
    }

    @Test
    public void invalidCaptureModesMigrateToPortal() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                false,
                true,
                true,
                DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION,
                DragShareSettings.DEFAULT_SIMPLE_MENU_OPACITY_PERCENT,
                DragShareSettings.DEFAULT_SIMPLE_MENU_CORNER_RADIUS_DP,
                DragShareSettings.DEFAULT_SIMPLE_MENU_EDGE_DISTANCE_DP,
                DragShareSettings.DEFAULT_ICON_OPACITY_PERCENT,
                true,
                new LinkedHashSet<>(),
                Arrays.asList(),
                99);
        assertTrue(settings.isPortalCaptureMode());

        Bundle oldBundle = new Bundle();
        assertTrue(DragShareSettings.fromBundle(oldBundle).isPortalCaptureMode());
    }

    @Test
    public void backgroundScrollSettingRoundTripsThroughPortalBundle() {
        DragShareSettings settings = new DragShareSettings(
                DragShareSettings.COLOR_LIGHT,
                DragShareSettings.STYLE_SIMPLE,
                DragShareSettings.DEFAULT_EDGE_TRIGGER_DP,
                DragShareSettings.DEFAULT_SCROLL_SPEED_DP_PER_SECOND,
                true);

        assertTrue(settings.blockBackgroundScroll);
        assertTrue(DragShareSettings.fromBundle(settings.toBundle()).blockBackgroundScroll);
    }

    @Test
    public void accessibilityBlacklistCombinesUserAndBuiltInPackages() {
        assertTrue(AccessibilityBlacklist.isBlockedByPackages(
                "pkg.user",
                new LinkedHashSet<>(Arrays.asList("pkg.user")),
                new LinkedHashSet<>()));
        assertTrue(AccessibilityBlacklist.isBlockedByPackages(
                "pkg.builtin",
                new LinkedHashSet<>(),
                new LinkedHashSet<>(Arrays.asList("pkg.builtin"))));
        assertFalse(AccessibilityBlacklist.isBlockedByPackages(
                "pkg.allowed",
                new LinkedHashSet<>(Arrays.asList("pkg.user")),
                new LinkedHashSet<>(Arrays.asList("pkg.builtin"))));
    }

    @Test
    public void builderStartsWithTheSameValuesAsDefaults() {
        assertSettingsEqual(DragShareSettings.defaults(), DragShareSettings.builder().build());
    }

    @Test
    public void changingOneFieldOnNonDefaultSettingsPreservesEveryOtherField() {
        DragShareSettings current = nonDefaultSettings();

        DragShareSettings updated = current.toBuilder()
                .setEdgeTriggerDp(123)
                .build();

        assertEquals(123, updated.edgeTriggerDp);
        assertSettingsEqualExcept(current, updated, "edgeTriggerDp");
    }

    @Test
    public void successiveBuilderUpdatesRetainPreviousFieldChanges() {
        DragShareSettings first = DragShareSettings.defaults().toBuilder()
                .setColorMode(DragShareSettings.COLOR_DARK)
                .build();
        DragShareSettings second = first.toBuilder()
                .setLogLevel(DragShareSettings.LOG_LEVEL_DEBUG)
                .build();

        assertEquals(DragShareSettings.COLOR_DARK, second.colorMode);
        assertEquals(DragShareSettings.LOG_LEVEL_DEBUG, second.logLevel);
        assertSettingsEqualExcept(DragShareSettings.defaults(), second, "colorMode", "logLevel");
    }

    @Test
    public void allBuilderFieldsSurviveLocalPreferencesAndBundleRoundTrips() {
        Context context = RuntimeEnvironment.getApplication();
        DragShareSettings previous = DragShareSettings.readLocal(context);
        DragShareSettings settings = nonDefaultSettings();
        try {
            settings.saveLocal(context);

            assertSettingsEqual(settings, DragShareSettings.readLocal(context));
            assertSettingsEqual(settings, DragShareSettings.fromBundle(settings.toBundle()));
        } finally {
            previous.saveLocal(context);
        }
    }

    @Test
    public void absentBundleFieldsKeepCurrentDefaultsAndLegacyRules() {
        assertSettingsEqual(DragShareSettings.defaults(), DragShareSettings.fromBundle(new Bundle()));
        assertSettingsEqual(DragShareSettings.defaults(), DragShareSettings.fromBundle(null));

        Bundle legacy = new Bundle();
        legacy.putInt("accessibility_long_press_timeout_millis", 0);
        DragShareSettings migrated = DragShareSettings.fromBundle(legacy);
        assertEquals(DragShareSettings.DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
                migrated.accessibilityLongPressTimeoutMillis);
        assertEquals(DragShareSettings.DEFAULT_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS,
                migrated.toBundle().getInt("accessibility_long_press_timeout_millis"));
    }

    @Test
    public void builderCollectionSettersAndBuildTakeIndependentSnapshots() {
        Set<String> hidden = new LinkedHashSet<>(Arrays.asList("hidden.before"));
        List<String> order = new ArrayList<>(Arrays.asList("target.before"));
        Set<String> blacklist = new LinkedHashSet<>(Arrays.asList("blacklist.before"));
        Set<String> games = new LinkedHashSet<>(Arrays.asList("game.before"));

        DragShareSettings.Builder builder = DragShareSettings.defaults().toBuilder()
                .setHiddenTargetKeys(hidden)
                .setTargetOrder(order)
                .setAccessibilityBlacklistedPackages(blacklist)
                .setManualGamePackages(games);
        hidden.add("hidden.after-setter");
        order.add("target.after-setter");
        blacklist.add("blacklist.after-setter");
        games.add("game.after-setter");

        DragShareSettings built = builder.build();
        hidden.add("hidden.after-build");
        order.add("target.after-build");
        blacklist.add("blacklist.after-build");
        games.add("game.after-build");
        builder.setHiddenTargetKeys(Collections.singleton("builder.changed.after-build"));
        builder.setTargetOrder(Collections.singletonList("builder.changed.after-build"));
        builder.setAccessibilityBlacklistedPackages(Collections.singleton("builder.changed.after-build"));
        builder.setManualGamePackages(Collections.singleton("builder.changed.after-build"));

        assertEquals(Collections.singleton("hidden.before"), built.hiddenTargetKeys);
        assertEquals(Collections.singletonList("target.before"), built.targetOrder);
        assertEquals(Collections.singleton("blacklist.before"),
                built.accessibilityBlacklistedPackages);
        assertEquals(Collections.singleton("game.before"), built.manualGamePackages);
        assertUnmodifiableSet(built.hiddenTargetKeys);
        assertUnmodifiableList(built.targetOrder);
        assertUnmodifiableSet(built.accessibilityBlacklistedPackages);
        assertUnmodifiableSet(built.manualGamePackages);
    }

    @Test
    public void builderUsesTheExistingConstructorBoundaryNormalization() {
        Set<String> hidden = new LinkedHashSet<>(Arrays.asList("hidden"));
        List<String> order = Arrays.asList("target");
        Set<String> blacklist = new LinkedHashSet<>(Arrays.asList("blocked"));
        Set<String> games = new LinkedHashSet<>(Arrays.asList("game"));
        DragShareSettings constructorResult = new DragShareSettings(
                99,
                99,
                DragShareSettings.MIN_EDGE_TRIGGER_DP - 1,
                DragShareSettings.MAX_SCROLL_SPEED_DP_PER_SECOND + 1,
                true,
                false,
                false,
                99,
                DragShareSettings.MIN_SIMPLE_MENU_OPACITY_PERCENT - 1,
                DragShareSettings.MAX_SIMPLE_MENU_CORNER_RADIUS_DP + 1,
                DragShareSettings.MAX_SIMPLE_MENU_EDGE_DISTANCE_DP + 1,
                DragShareSettings.MIN_ICON_OPACITY_PERCENT - 1,
                false,
                hidden,
                order,
                99,
                true,
                blacklist,
                0,
                DragShareSettings.MAX_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT + 1,
                DragShareSettings.MAX_MODERN_BLUR_RADIUS_DP + 1,
                DragShareSettings.MIN_MODERN_GLASS_OPACITY_PERCENT - 1,
                99,
                99,
                true,
                games);

        DragShareSettings builderResult = DragShareSettings.builder()
                .setColorMode(99)
                .setUiStyle(99)
                .setEdgeTriggerDp(DragShareSettings.MIN_EDGE_TRIGGER_DP - 1)
                .setScrollSpeedDpPerSecond(DragShareSettings.MAX_SCROLL_SPEED_DP_PER_SECOND + 1)
                .setBlockBackgroundScroll(true)
                .setTextSharingEnabled(false)
                .setImageSharingEnabled(false)
                .setSimpleMenuPosition(99)
                .setSimpleMenuOpacityPercent(DragShareSettings.MIN_SIMPLE_MENU_OPACITY_PERCENT - 1)
                .setSimpleMenuCornerRadiusDp(DragShareSettings.MAX_SIMPLE_MENU_CORNER_RADIUS_DP + 1)
                .setSimpleMenuEdgeDistanceDp(DragShareSettings.MAX_SIMPLE_MENU_EDGE_DISTANCE_DP + 1)
                .setIconOpacityPercent(DragShareSettings.MIN_ICON_OPACITY_PERCENT - 1)
                .setCloseMenuWhenPointerLeaves(false)
                .setHiddenTargetKeys(hidden)
                .setTargetOrder(order)
                .setContentCaptureMode(99)
                .setAccessibilityLandscapeRecognitionEnabled(true)
                .setAccessibilityBlacklistedPackages(blacklist)
                .setAccessibilityLongPressTimeoutMillis(0)
                .setAccessibilityRecognitionSensitivityPercent(
                        DragShareSettings.MAX_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT + 1)
                .setModernBlurRadiusDp(DragShareSettings.MAX_MODERN_BLUR_RADIUS_DP + 1)
                .setModernGlassOpacityPercent(
                        DragShareSettings.MIN_MODERN_GLASS_OPACITY_PERCENT - 1)
                .setLogLevel(99)
                .setLogDestination(99)
                .setPauseAutomaticInGames(true)
                .setManualGamePackages(games)
                .build();

        assertSettingsEqual(constructorResult, builderResult);
    }

    private static DragShareSettings nonDefaultSettings() {
        return DragShareSettings.builder()
                .setColorMode(DragShareSettings.COLOR_SYSTEM)
                .setContentCaptureMode(DragShareSettings.CONTENT_CAPTURE_ACCESSIBILITY)
                .setUiStyle(DragShareSettings.STYLE_CIRCLE)
                .setEdgeTriggerDp(91)
                .setScrollSpeedDpPerSecond(777)
                .setBlockBackgroundScroll(true)
                .setTextSharingEnabled(false)
                .setImageSharingEnabled(false)
                .setSimpleMenuPosition(DragShareSettings.SIMPLE_MENU_POSITION_TOP)
                .setSimpleMenuOpacityPercent(83)
                .setSimpleMenuCornerRadiusDp(17)
                .setSimpleMenuEdgeDistanceDp(23)
                .setIconOpacityPercent(47)
                .setModernBlurRadiusDp(88)
                .setModernGlassOpacityPercent(91)
                .setCloseMenuWhenPointerLeaves(false)
                .setHiddenTargetKeys(new LinkedHashSet<>(Arrays.asList("target.hidden")))
                .setTargetOrder(Arrays.asList("target.first", "target.second"))
                .setAccessibilityLandscapeRecognitionEnabled(true)
                .setAccessibilityBlacklistedPackages(
                        new LinkedHashSet<>(Arrays.asList("app.blocked")))
                .setAccessibilityLongPressTimeoutMillis(1250)
                .setAccessibilityRecognitionSensitivityPercent(137)
                .setLogLevel(DragShareSettings.LOG_LEVEL_DEBUG)
                .setLogDestination(DragShareSettings.LOG_DESTINATION_FILE)
                .setPauseAutomaticInGames(true)
                .setManualGamePackages(new LinkedHashSet<>(Arrays.asList("app.game")))
                .build();
    }

    private static void assertSettingsEqual(
            DragShareSettings expected,
            DragShareSettings actual) {
        assertSettingsEqualExcept(expected, actual);
    }

    private static void assertSettingsEqualExcept(
            DragShareSettings expected,
            DragShareSettings actual,
            String... excludedFields) {
        List<String> excluded = Arrays.asList(excludedFields);
        if (!excluded.contains("colorMode")) assertEquals(expected.colorMode, actual.colorMode);
        if (!excluded.contains("contentCaptureMode")) assertEquals(expected.contentCaptureMode, actual.contentCaptureMode);
        if (!excluded.contains("uiStyle")) assertEquals(expected.uiStyle, actual.uiStyle);
        if (!excluded.contains("edgeTriggerDp")) assertEquals(expected.edgeTriggerDp, actual.edgeTriggerDp);
        if (!excluded.contains("scrollSpeedDpPerSecond")) assertEquals(expected.scrollSpeedDpPerSecond, actual.scrollSpeedDpPerSecond);
        if (!excluded.contains("blockBackgroundScroll")) assertEquals(expected.blockBackgroundScroll, actual.blockBackgroundScroll);
        if (!excluded.contains("textSharingEnabled")) assertEquals(expected.textSharingEnabled, actual.textSharingEnabled);
        if (!excluded.contains("imageSharingEnabled")) assertEquals(expected.imageSharingEnabled, actual.imageSharingEnabled);
        if (!excluded.contains("simpleMenuPosition")) assertEquals(expected.simpleMenuPosition, actual.simpleMenuPosition);
        if (!excluded.contains("simpleMenuOpacityPercent")) assertEquals(expected.simpleMenuOpacityPercent, actual.simpleMenuOpacityPercent);
        if (!excluded.contains("simpleMenuCornerRadiusDp")) assertEquals(expected.simpleMenuCornerRadiusDp, actual.simpleMenuCornerRadiusDp);
        if (!excluded.contains("simpleMenuEdgeDistanceDp")) assertEquals(expected.simpleMenuEdgeDistanceDp, actual.simpleMenuEdgeDistanceDp);
        if (!excluded.contains("iconOpacityPercent")) assertEquals(expected.iconOpacityPercent, actual.iconOpacityPercent);
        if (!excluded.contains("modernBlurRadiusDp")) assertEquals(expected.modernBlurRadiusDp, actual.modernBlurRadiusDp);
        if (!excluded.contains("modernGlassOpacityPercent")) assertEquals(expected.modernGlassOpacityPercent, actual.modernGlassOpacityPercent);
        if (!excluded.contains("closeMenuWhenPointerLeaves")) assertEquals(expected.closeMenuWhenPointerLeaves, actual.closeMenuWhenPointerLeaves);
        if (!excluded.contains("hiddenTargetKeys")) assertEquals(expected.hiddenTargetKeys, actual.hiddenTargetKeys);
        if (!excluded.contains("targetOrder")) assertEquals(expected.targetOrder, actual.targetOrder);
        if (!excluded.contains("accessibilityLandscapeRecognitionEnabled")) assertEquals(expected.accessibilityLandscapeRecognitionEnabled, actual.accessibilityLandscapeRecognitionEnabled);
        if (!excluded.contains("accessibilityBlacklistedPackages")) assertEquals(expected.accessibilityBlacklistedPackages, actual.accessibilityBlacklistedPackages);
        if (!excluded.contains("accessibilityLongPressTimeoutMillis")) assertEquals(expected.accessibilityLongPressTimeoutMillis, actual.accessibilityLongPressTimeoutMillis);
        if (!excluded.contains("accessibilityRecognitionSensitivityPercent")) assertEquals(expected.accessibilityRecognitionSensitivityPercent, actual.accessibilityRecognitionSensitivityPercent);
        if (!excluded.contains("logLevel")) assertEquals(expected.logLevel, actual.logLevel);
        if (!excluded.contains("logDestination")) assertEquals(expected.logDestination, actual.logDestination);
        if (!excluded.contains("pauseAutomaticInGames")) assertEquals(expected.pauseAutomaticInGames, actual.pauseAutomaticInGames);
        if (!excluded.contains("manualGamePackages")) assertEquals(expected.manualGamePackages, actual.manualGamePackages);
    }

    private static void assertUnmodifiableSet(Set<String> values) {
        try {
            values.add("must.not.be.added");
            throw new AssertionError("Expected an immutable set");
        } catch (UnsupportedOperationException expected) {
            // The settings value owns an immutable snapshot.
        }
    }

    private static void assertUnmodifiableList(List<String> values) {
        try {
            values.add("must.not.be.added");
            throw new AssertionError("Expected an immutable list");
        } catch (UnsupportedOperationException expected) {
            // The settings value owns an immutable snapshot.
        }
    }

}

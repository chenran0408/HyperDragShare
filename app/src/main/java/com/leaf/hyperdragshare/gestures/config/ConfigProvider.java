package com.leaf.hyperdragshare.gestures.config;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import com.leaf.hyperdragshare.codex.DragShareSettings;
import com.leaf.hyperdragshare.codex.GameCapturePolicy;

public final class ConfigProvider extends ContentProvider {
    private static final String TAG = "HyperDragShareConfig";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        enforceCaller();
        Context context = requireProviderContext();
        SharedPreferences prefs = context.getSharedPreferences(ConfigContract.PREFS, Context.MODE_PRIVATE);
        if ("get_catalog".equals(method)) {
            Bundle out = new Bundle();
            out.putString(ConfigContract.KEY_SHORTCUT_CATALOG,
                    prefs.getString(ConfigContract.KEY_SHORTCUT_CATALOG, "[]"));
            out.putString(ConfigContract.KEY_ACTIVITY_CATALOG,
                    prefs.getString(ConfigContract.KEY_ACTIVITY_CATALOG, "[]"));
            return out;
        }
        if ("get".equals(method)) {
            ConfigStore.seedDefaultsIfNeeded(context, prefs);
            ConfigStore.migrateUnifiedActionsIfNeeded(prefs);
            ConfigStore.migrateDoubleTapPinIfNeeded(prefs);
            ConfigStore.migrateSideLayoutModeIfNeeded(prefs);
            ConfigStore.migrateSelectedAppNameIfNeeded(prefs);
            ConfigStore.migrateIndependentSideTargetsIfNeeded(prefs);
            ConfigStore.migrateOrientationBehaviorIfNeeded(prefs);
            ConfigStore.migrateHoneycombBackgroundIfNeeded(prefs);
            ConfigStore.migrateHoneycombWallpaperDefaultIfNeeded(prefs);

            ConfigStore.ensureNativeWindowScaleDefaults(prefs);
            ConfigStore.ensureHoneycombPositions(prefs);

            Bundle out = new Bundle();
            out.putBoolean(ConfigContract.KEY_ENABLED, prefs.getBoolean(ConfigContract.KEY_ENABLED, ConfigContract.DEFAULT_ENABLED));
            out.putBoolean(ConfigContract.KEY_HAPTIC, prefs.getBoolean(ConfigContract.KEY_HAPTIC, ConfigContract.DEFAULT_HAPTIC));
            out.putBoolean(ConfigContract.KEY_FAN_SHADOW, prefs.getBoolean(ConfigContract.KEY_FAN_SHADOW, ConfigContract.DEFAULT_FAN_SHADOW));
            out.putBoolean(ConfigContract.KEY_FAN_ANIMATIONS_ENABLED, prefs.getBoolean(
                    ConfigContract.KEY_FAN_ANIMATIONS_ENABLED,
                    ConfigContract.DEFAULT_FAN_ANIMATIONS_ENABLED));
            out.putInt(ConfigContract.KEY_FAN_ANIMATION_SPEED, Math.max(
                    ConfigContract.MIN_FAN_ANIMATION_SPEED, Math.min(
                            ConfigContract.MAX_FAN_ANIMATION_SPEED, prefs.getInt(
                                    ConfigContract.KEY_FAN_ANIMATION_SPEED,
                                    ConfigContract.DEFAULT_FAN_ANIMATION_SPEED))));
            out.putInt(ConfigContract.KEY_BOTTOM_ANIMATION_SPEED, clamp(prefs.getInt(
                    ConfigContract.KEY_BOTTOM_ANIMATION_SPEED,
                    prefs.getInt(ConfigContract.KEY_FAN_ANIMATION_SPEED,
                            ConfigContract.DEFAULT_BOTTOM_ANIMATION_SPEED)),
                    ConfigContract.MIN_FAN_ANIMATION_SPEED,
                    ConfigContract.MAX_FAN_ANIMATION_SPEED));
            out.putBoolean(ConfigContract.KEY_BOTTOM_TRIGGER_HAPTIC, prefs.getBoolean(
                    ConfigContract.KEY_BOTTOM_TRIGGER_HAPTIC,
                    prefs.getBoolean(ConfigContract.KEY_HAPTIC,
                            ConfigContract.DEFAULT_BOTTOM_TRIGGER_HAPTIC)));
            out.putInt(ConfigContract.KEY_SELECTION_TRANSFORM_LEVEL, clamp(prefs.getInt(
                    ConfigContract.KEY_SELECTION_TRANSFORM_LEVEL,
                    ConfigContract.DEFAULT_SELECTION_TRANSFORM_LEVEL),
                    ConfigContract.SELECTION_TRANSFORM_OFF,
                    ConfigContract.SELECTION_TRANSFORM_STRONG));
            out.putInt(ConfigContract.KEY_FAN_REVEAL_AMOUNT, Math.max(
                    ConfigContract.MIN_FAN_REVEAL_AMOUNT, Math.min(
                            ConfigContract.MAX_FAN_REVEAL_AMOUNT, prefs.getInt(
                                    ConfigContract.KEY_FAN_REVEAL_AMOUNT,
                                    ConfigContract.DEFAULT_FAN_REVEAL_AMOUNT))));
            out.putInt(ConfigContract.KEY_FAN_ROTATION_DEGREES, Math.max(
                    ConfigContract.MIN_FAN_ROTATION_DEGREES, Math.min(
                            ConfigContract.MAX_FAN_ROTATION_DEGREES, prefs.getInt(
                                    ConfigContract.KEY_FAN_ROTATION_DEGREES,
                                    ConfigContract.DEFAULT_FAN_ROTATION_DEGREES))));
            out.putInt(ConfigContract.KEY_FAN_SELECTION_SCALE_PERCENT, Math.max(
                    ConfigContract.MIN_FAN_SELECTION_SCALE_PERCENT, Math.min(
                            ConfigContract.MAX_FAN_SELECTION_SCALE_PERCENT, prefs.getInt(
                                    ConfigContract.KEY_FAN_SELECTION_SCALE_PERCENT,
                                    ConfigContract.DEFAULT_FAN_SELECTION_SCALE_PERCENT))));
            out.putBoolean(ConfigContract.KEY_FAN_SELECTION_RING, prefs.getBoolean(
                    ConfigContract.KEY_FAN_SELECTION_RING,
                    ConfigContract.DEFAULT_FAN_SELECTION_RING));
            out.putBoolean(ConfigContract.KEY_FORCE_CIRCULAR_ICONS, prefs.getBoolean(
                    ConfigContract.KEY_FORCE_CIRCULAR_ICONS,
                    ConfigContract.DEFAULT_FORCE_CIRCULAR_ICONS));
            out.putBoolean(ConfigContract.KEY_FAN_FIXED_SEVEN_ROWS, false);
            int fanLayoutMode = prefs.contains(ConfigContract.KEY_FAN_LAYOUT_MODE)
                    ? prefs.getInt(ConfigContract.KEY_FAN_LAYOUT_MODE,
                    ConfigContract.DEFAULT_FAN_LAYOUT_MODE)
                    : prefs.getBoolean(ConfigContract.KEY_FAN_FIXED_SEVEN_ROWS,
                    ConfigContract.DEFAULT_FAN_FIXED_SEVEN_ROWS)
                    ? ConfigContract.FAN_LAYOUT_SMART
                    : ConfigContract.FAN_LAYOUT_SMART;
            if (fanLayoutMode == ConfigContract.FAN_LAYOUT_FIXED_SEVEN) {
                fanLayoutMode = ConfigContract.FAN_LAYOUT_SMART;
            }
            out.putInt(ConfigContract.KEY_FAN_LAYOUT_MODE, clamp(fanLayoutMode,
                    ConfigContract.FAN_LAYOUT_SMART, ConfigContract.FAN_LAYOUT_CUSTOM));
            int outer = clamp(prefs.getInt(ConfigContract.KEY_FAN_CUSTOM_OUTER_COUNT,
                            ConfigContract.DEFAULT_FAN_CUSTOM_OUTER_COUNT),
                    ConfigContract.MIN_FAN_CUSTOM_ROW_COUNT,
                    ConfigContract.MAX_FAN_CUSTOM_OUTER_COUNT);
            int middle = clamp(prefs.getInt(ConfigContract.KEY_FAN_CUSTOM_MIDDLE_COUNT,
                            ConfigContract.DEFAULT_FAN_CUSTOM_MIDDLE_COUNT),
                    ConfigContract.MIN_FAN_CUSTOM_ROW_COUNT,
                    ConfigContract.MAX_FAN_CUSTOM_MIDDLE_COUNT);
            int inner = clamp(prefs.getInt(ConfigContract.KEY_FAN_CUSTOM_INNER_COUNT,
                            ConfigContract.DEFAULT_FAN_CUSTOM_INNER_COUNT),
                    ConfigContract.MIN_FAN_CUSTOM_ROW_COUNT,
                    ConfigContract.MAX_FAN_CUSTOM_INNER_COUNT);
            out.putInt(ConfigContract.KEY_FAN_CUSTOM_OUTER_COUNT, outer);
            out.putInt(ConfigContract.KEY_FAN_CUSTOM_MIDDLE_COUNT, middle);
            out.putInt(ConfigContract.KEY_FAN_CUSTOM_INNER_COUNT, inner);
            out.putBoolean(ConfigContract.KEY_BOTTOM_PORTRAIT_ENABLED, prefs.getBoolean(
                    ConfigContract.KEY_BOTTOM_PORTRAIT_ENABLED,
                    ConfigContract.DEFAULT_BOTTOM_PORTRAIT_ENABLED));
            out.putBoolean(ConfigContract.KEY_BOTTOM_LANDSCAPE_ENABLED, prefs.getBoolean(
                    ConfigContract.KEY_BOTTOM_LANDSCAPE_ENABLED,
                    ConfigContract.DEFAULT_BOTTOM_LANDSCAPE_ENABLED));
            out.putBoolean(ConfigContract.KEY_BOTTOM_PORTRAIT_SECOND_STAGE_ENABLED,
                    prefs.getBoolean(ConfigContract.KEY_BOTTOM_PORTRAIT_SECOND_STAGE_ENABLED,
                            ConfigContract.DEFAULT_BOTTOM_PORTRAIT_SECOND_STAGE_ENABLED));
            out.putBoolean(ConfigContract.KEY_BOTTOM_LANDSCAPE_SECOND_STAGE_ENABLED,
                    prefs.getBoolean(ConfigContract.KEY_BOTTOM_LANDSCAPE_SECOND_STAGE_ENABLED,
                            ConfigContract.DEFAULT_BOTTOM_LANDSCAPE_SECOND_STAGE_ENABLED));
            out.putBoolean(ConfigContract.KEY_BOTTOM_PORTRAIT_FULLSCREEN,
                    prefs.getBoolean(ConfigContract.KEY_BOTTOM_PORTRAIT_FULLSCREEN,
                            ConfigContract.DEFAULT_BOTTOM_PORTRAIT_FULLSCREEN));
            out.putBoolean(ConfigContract.KEY_BOTTOM_LANDSCAPE_FULLSCREEN,
                    prefs.getBoolean(ConfigContract.KEY_BOTTOM_LANDSCAPE_FULLSCREEN,
                            ConfigContract.DEFAULT_BOTTOM_LANDSCAPE_FULLSCREEN));
            out.putBoolean(ConfigContract.KEY_BOTTOM_PORTRAIT_HONEYCOMB_FREEFORM,
                    prefs.getBoolean(ConfigContract.KEY_BOTTOM_PORTRAIT_HONEYCOMB_FREEFORM,
                            ConfigContract.DEFAULT_BOTTOM_PORTRAIT_HONEYCOMB_FREEFORM));

            out.putBoolean(ConfigContract.KEY_BOTTOM_LANDSCAPE_HONEYCOMB_FREEFORM,
                    prefs.getBoolean(ConfigContract.KEY_BOTTOM_LANDSCAPE_HONEYCOMB_FREEFORM,
                            ConfigContract.DEFAULT_BOTTOM_LANDSCAPE_HONEYCOMB_FREEFORM));
            out.putInt(ConfigContract.KEY_BOTTOM_HONEYCOMB_SETTLE_MS, clamp(prefs.getInt(
                    ConfigContract.KEY_BOTTOM_HONEYCOMB_SETTLE_MS,
                    ConfigContract.DEFAULT_BOTTOM_HONEYCOMB_SETTLE_MS),
                    ConfigContract.MIN_BOTTOM_HONEYCOMB_SETTLE_MS,
                    ConfigContract.MAX_BOTTOM_HONEYCOMB_SETTLE_MS));
            out.putBoolean(ConfigContract.KEY_SIDE_GESTURE_ENABLED, prefs.getBoolean(ConfigContract.KEY_SIDE_GESTURE_ENABLED, ConfigContract.DEFAULT_SIDE_GESTURE_ENABLED));
            out.putBoolean(ConfigContract.KEY_SIDE_PORTRAIT_ENABLED, prefs.getBoolean(
                    ConfigContract.KEY_SIDE_PORTRAIT_ENABLED,
                    ConfigContract.DEFAULT_SIDE_PORTRAIT_ENABLED));
            out.putBoolean(ConfigContract.KEY_SIDE_LANDSCAPE_ENABLED, prefs.getBoolean(
                    ConfigContract.KEY_SIDE_LANDSCAPE_ENABLED,
                    ConfigContract.DEFAULT_SIDE_LANDSCAPE_ENABLED));
            out.putInt(ConfigContract.KEY_SIDE_PORTRAIT_LAYOUT_MODE, LayoutModeSanitizer.sideLayout(prefs.getInt(
                    ConfigContract.KEY_SIDE_PORTRAIT_LAYOUT_MODE,
                    ConfigContract.DEFAULT_SIDE_LAYOUT_MODE)));
            out.putInt(ConfigContract.KEY_SIDE_LANDSCAPE_LAYOUT_MODE, LayoutModeSanitizer.sideLayout(prefs.getInt(
                    ConfigContract.KEY_SIDE_LANDSCAPE_LAYOUT_MODE,
                    ConfigContract.DEFAULT_SIDE_LAYOUT_MODE)));
            out.putBoolean(ConfigContract.KEY_SIDE_PORTRAIT_FULLSCREEN,
                    prefs.getBoolean(ConfigContract.KEY_SIDE_PORTRAIT_FULLSCREEN,
                            ConfigContract.DEFAULT_SIDE_PORTRAIT_FULLSCREEN));
            out.putBoolean(ConfigContract.KEY_SIDE_LANDSCAPE_FULLSCREEN,
                    prefs.getBoolean(ConfigContract.KEY_SIDE_LANDSCAPE_FULLSCREEN,
                            ConfigContract.DEFAULT_SIDE_LANDSCAPE_FULLSCREEN));
            out.putBoolean(ConfigContract.KEY_SIDE_ANIMATIONS_ENABLED, prefs.getBoolean(
                    ConfigContract.KEY_SIDE_ANIMATIONS_ENABLED,
                    ConfigContract.DEFAULT_SIDE_ANIMATIONS_ENABLED));
            out.putInt(ConfigContract.KEY_SIDE_ANIMATION_SPEED, clamp(prefs.getInt(
                    ConfigContract.KEY_SIDE_ANIMATION_SPEED,
                    ConfigContract.DEFAULT_SIDE_ANIMATION_SPEED),
                    ConfigContract.MIN_FAN_ANIMATION_SPEED,
                    ConfigContract.MAX_FAN_ANIMATION_SPEED));
            out.putInt(ConfigContract.KEY_SIDE_REVEAL_AMOUNT, clamp(prefs.getInt(
                    ConfigContract.KEY_SIDE_REVEAL_AMOUNT,
                    ConfigContract.DEFAULT_SIDE_REVEAL_AMOUNT),
                    ConfigContract.MIN_FAN_REVEAL_AMOUNT,
                    ConfigContract.MAX_FAN_REVEAL_AMOUNT));
            out.putInt(ConfigContract.KEY_SIDE_ROTATION_DEGREES, clamp(prefs.getInt(
                    ConfigContract.KEY_SIDE_ROTATION_DEGREES,
                    ConfigContract.DEFAULT_SIDE_ROTATION_DEGREES),
                    ConfigContract.MIN_FAN_ROTATION_DEGREES,
                    ConfigContract.MAX_FAN_ROTATION_DEGREES));
            out.putInt(ConfigContract.KEY_SIDE_SELECTION_SCALE_PERCENT, clamp(prefs.getInt(
                    ConfigContract.KEY_SIDE_SELECTION_SCALE_PERCENT,
                    ConfigContract.DEFAULT_SIDE_SELECTION_SCALE_PERCENT),
                    ConfigContract.MIN_FAN_SELECTION_SCALE_PERCENT,
                    ConfigContract.MAX_FAN_SELECTION_SCALE_PERCENT));
            out.putBoolean(ConfigContract.KEY_SIDE_SELECTION_RING, prefs.getBoolean(
                    ConfigContract.KEY_SIDE_SELECTION_RING,
                    ConfigContract.DEFAULT_SIDE_SELECTION_RING));
            out.putInt(ConfigContract.KEY_SIDE_TRIGGER_PERCENT, clamp(prefs.getInt(
                    ConfigContract.KEY_SIDE_TRIGGER_PERCENT,
                    ConfigContract.DEFAULT_SIDE_TRIGGER_PERCENT),
                    ConfigContract.MIN_SIDE_TRIGGER_PERCENT,
                    ConfigContract.MAX_SIDE_TRIGGER_PERCENT));
            out.putInt(ConfigContract.KEY_SIDE_ICON_SIZE_DP, prefs.getInt(
                    ConfigContract.KEY_SIDE_ICON_SIZE_DP,
                    prefs.getInt(ConfigContract.KEY_ICON_SIZE_DP,
                            ConfigContract.DEFAULT_SIDE_ICON_SIZE_DP)));
            out.putInt(ConfigContract.KEY_SIDE_TOP_SAFE_MARGIN_PERCENT, prefs.getInt(
                    ConfigContract.KEY_SIDE_TOP_SAFE_MARGIN_PERCENT,
                    ConfigContract.DEFAULT_SIDE_TOP_SAFE_MARGIN_PERCENT));
            out.putBoolean(ConfigContract.KEY_SHOW_SELECTED_APP_NAME, prefs.getBoolean(
                    ConfigContract.KEY_SHOW_SELECTED_APP_NAME,
                    ConfigContract.DEFAULT_SHOW_SELECTED_APP_NAME));
            out.putBoolean(ConfigContract.KEY_SIDE_FOLLOW_FINGER, prefs.getBoolean(
                    ConfigContract.KEY_SIDE_FOLLOW_FINGER,
                    ConfigContract.DEFAULT_SIDE_FOLLOW_FINGER));
            out.putBoolean(ConfigContract.KEY_SIDE_FAN_LIST, prefs.getBoolean(
                    ConfigContract.KEY_SIDE_FAN_LIST,
                    ConfigContract.DEFAULT_SIDE_FAN_LIST));
            out.putInt(ConfigContract.KEY_SIDE_LAYOUT_MODE, LayoutModeSanitizer.sideLayout(prefs.getInt(
                    ConfigContract.KEY_SIDE_LAYOUT_MODE,
                    ConfigContract.DEFAULT_SIDE_LAYOUT_MODE)));
            out.putInt(ConfigContract.KEY_SIDE_RING_SIZE_PERCENT, Math.max(
                    ConfigContract.MIN_SIDE_RING_SIZE_PERCENT, Math.min(
                            ConfigContract.MAX_SIDE_RING_SIZE_PERCENT, prefs.getInt(
                                    ConfigContract.KEY_SIDE_RING_SIZE_PERCENT,
                                    ConfigContract.DEFAULT_SIDE_RING_SIZE_PERCENT))));
            out.putBoolean(ConfigContract.KEY_SIDE_WHEEL_MODE, prefs.getBoolean(
                    ConfigContract.KEY_SIDE_WHEEL_MODE,
                    ConfigContract.DEFAULT_SIDE_WHEEL_MODE));
            out.putInt(ConfigContract.KEY_SIDE_REVERSE_CANCEL_PERCENT,
                    Math.max(ConfigContract.MIN_SIDE_REVERSE_CANCEL_PERCENT,
                            Math.min(ConfigContract.MAX_SIDE_REVERSE_CANCEL_PERCENT,
                                    prefs.getInt(ConfigContract.KEY_SIDE_REVERSE_CANCEL_PERCENT,
                                            ConfigContract.DEFAULT_SIDE_REVERSE_CANCEL_PERCENT))));
            out.putBoolean(ConfigContract.KEY_SIDE_HOLD_ENABLED, prefs.getBoolean(
                    ConfigContract.KEY_SIDE_HOLD_ENABLED,
                    ConfigContract.DEFAULT_SIDE_HOLD_ENABLED));
            out.putInt(ConfigContract.KEY_SIDE_HOLD_DELAY_MS, clamp(prefs.getInt(
                    ConfigContract.KEY_SIDE_HOLD_DELAY_MS,
                    ConfigContract.DEFAULT_SIDE_HOLD_DELAY_MS),
                    ConfigContract.MIN_SIDE_HOLD_DELAY_MS,
                    ConfigContract.MAX_SIDE_HOLD_DELAY_MS));
            out.putInt(ConfigContract.KEY_SIDE_TASK_MAX_COUNT, clamp(prefs.getInt(
                    ConfigContract.KEY_SIDE_TASK_MAX_COUNT,
                    ConfigContract.DEFAULT_SIDE_TASK_MAX_COUNT),
                    ConfigContract.MIN_SIDE_TASK_MAX_COUNT,
                    ConfigContract.MAX_SIDE_TASK_MAX_COUNT));
            out.putInt(ConfigContract.KEY_SIDE_TASK_CARD_SIZE_DP, clamp(prefs.getInt(
                    ConfigContract.KEY_SIDE_TASK_CARD_SIZE_DP,
                    ConfigContract.DEFAULT_SIDE_TASK_CARD_SIZE_DP),
                    ConfigContract.MIN_SIDE_TASK_CARD_SIZE_DP,
                    ConfigContract.MAX_SIDE_TASK_CARD_SIZE_DP));
            int legacyTaskCardSize = prefs.getInt(ConfigContract.KEY_SIDE_TASK_CARD_SIZE_DP,
                    ConfigContract.DEFAULT_SIDE_TASK_CARD_SIZE_DP);
            out.putInt(ConfigContract.KEY_SIDE_TASK_CARD_WIDTH_DP, clamp(prefs.getInt(
                    ConfigContract.KEY_SIDE_TASK_CARD_WIDTH_DP, legacyTaskCardSize),
                    ConfigContract.MIN_SIDE_TASK_CARD_WIDTH_DP,
                    ConfigContract.MAX_SIDE_TASK_CARD_WIDTH_DP));
            out.putInt(ConfigContract.KEY_SIDE_TASK_CARD_HEIGHT_DP, clamp(prefs.getInt(
                    ConfigContract.KEY_SIDE_TASK_CARD_HEIGHT_DP,
                    Math.round(legacyTaskCardSize * 1.67f)),
                    ConfigContract.MIN_SIDE_TASK_CARD_HEIGHT_DP,
                    ConfigContract.MAX_SIDE_TASK_CARD_HEIGHT_DP));
            out.putInt(ConfigContract.KEY_SIDE_TASK_CARD_CORNER_DP, clamp(prefs.getInt(
                    ConfigContract.KEY_SIDE_TASK_CARD_CORNER_DP,
                    ConfigContract.DEFAULT_SIDE_TASK_CARD_CORNER_DP),
                    ConfigContract.MIN_SIDE_TASK_CARD_CORNER_DP,
                    ConfigContract.MAX_SIDE_TASK_CARD_CORNER_DP));
            out.putInt(ConfigContract.KEY_SIDE_TASK_ICON_SIZE_DP, clamp(prefs.getInt(
                    ConfigContract.KEY_SIDE_TASK_ICON_SIZE_DP,
                    ConfigContract.DEFAULT_SIDE_TASK_ICON_SIZE_DP),
                    ConfigContract.MIN_SIDE_TASK_ICON_SIZE_DP,
                    ConfigContract.MAX_SIDE_TASK_ICON_SIZE_DP));
            out.putInt(ConfigContract.KEY_SIDE_TASK_ICON_GAP_DP, clamp(prefs.getInt(
                    ConfigContract.KEY_SIDE_TASK_ICON_GAP_DP,
                    ConfigContract.DEFAULT_SIDE_TASK_ICON_GAP_DP),
                    ConfigContract.MIN_SIDE_TASK_ICON_GAP_DP,
                    ConfigContract.MAX_SIDE_TASK_ICON_GAP_DP));
            out.putInt(ConfigContract.KEY_SIDE_TASK_FINGER_OFFSET_DP, clamp(prefs.getInt(
                    ConfigContract.KEY_SIDE_TASK_FINGER_OFFSET_DP,
                    ConfigContract.DEFAULT_SIDE_TASK_FINGER_OFFSET_DP),
                    ConfigContract.MIN_SIDE_TASK_FINGER_OFFSET_DP,
                    ConfigContract.MAX_SIDE_TASK_FINGER_OFFSET_DP));
            out.putInt(ConfigContract.KEY_SIDE_TASK_LAYOUT_MODE, LayoutModeSanitizer.taskLayout(prefs.getInt(
                    ConfigContract.KEY_SIDE_TASK_LAYOUT_MODE,
                    ConfigContract.DEFAULT_SIDE_TASK_LAYOUT_MODE)));
            out.putBoolean(ConfigContract.KEY_SIDE_TASK_REVERSE_ORDER,
                    prefs.getBoolean(ConfigContract.KEY_SIDE_TASK_REVERSE_ORDER,
                            ConfigContract.DEFAULT_SIDE_TASK_REVERSE_ORDER));
            out.putBoolean(ConfigContract.KEY_SIDE_TASK_SHOW_NAME, prefs.getBoolean(
                    ConfigContract.KEY_SIDE_TASK_SHOW_NAME,
                    ConfigContract.DEFAULT_SIDE_TASK_SHOW_NAME));
            out.putInt(ConfigContract.KEY_SIDE_TASK_MOTION_MODE, clamp(prefs.getInt(
                    ConfigContract.KEY_SIDE_TASK_MOTION_MODE,
                    ConfigContract.DEFAULT_SIDE_TASK_MOTION_MODE),
                    ConfigContract.SIDE_TASK_MOTION_APPLE,
                    ConfigContract.SIDE_TASK_MOTION_MARBLE));
            out.putInt(ConfigContract.KEY_SIDE_TASK_SWIPE_SPEED_PERCENT,
                    clamp(prefs.getInt(ConfigContract.KEY_SIDE_TASK_SWIPE_SPEED_PERCENT,
                                    ConfigContract.DEFAULT_SIDE_TASK_SWIPE_SPEED_PERCENT),
                            ConfigContract.MIN_SIDE_TASK_SWIPE_SPEED_PERCENT,
                            ConfigContract.MAX_SIDE_TASK_SWIPE_SPEED_PERCENT));
            out.putInt(ConfigContract.KEY_SIDE_TASK_ANIMATION_SPEED,
                    clamp(prefs.getInt(ConfigContract.KEY_SIDE_TASK_ANIMATION_SPEED,
                                    ConfigContract.DEFAULT_SIDE_TASK_ANIMATION_SPEED),
                            0, 4));
            out.putBoolean(ConfigContract.KEY_SIDE_TASK_EXTENDED_DOWNWARD_TOLERANCE,
                    prefs.getBoolean(
                            ConfigContract.KEY_SIDE_TASK_EXTENDED_DOWNWARD_TOLERANCE,
                            ConfigContract.DEFAULT_SIDE_TASK_EXTENDED_DOWNWARD_TOLERANCE));
            out.putBoolean(ConfigContract.KEY_HIDE_SYSTEM_RECENTS_CLEAR,
                    prefs.getBoolean(ConfigContract.KEY_HIDE_SYSTEM_RECENTS_CLEAR,
                            ConfigContract.DEFAULT_HIDE_SYSTEM_RECENTS_CLEAR));
            out.putInt(ConfigContract.KEY_TRIGGER_PERCENT, prefs.getInt(ConfigContract.KEY_TRIGGER_PERCENT, ConfigContract.DEFAULT_TRIGGER_PERCENT));
            out.putInt(ConfigContract.KEY_SELECTION_RADIUS_PERCENT, clamp(prefs.getInt(
                    ConfigContract.KEY_SELECTION_RADIUS_PERCENT,
                    ConfigContract.DEFAULT_SELECTION_RADIUS_PERCENT),
                    ConfigContract.MIN_SELECTION_RADIUS_PERCENT,
                    ConfigContract.MAX_SELECTION_RADIUS_PERCENT));
            out.putInt(ConfigContract.KEY_HOT_WIDTH_PERCENT, prefs.getInt(ConfigContract.KEY_HOT_WIDTH_PERCENT, ConfigContract.DEFAULT_HOT_WIDTH_PERCENT));
            out.putInt(ConfigContract.KEY_HOT_HEIGHT_PERCENT, prefs.getInt(ConfigContract.KEY_HOT_HEIGHT_PERCENT, ConfigContract.DEFAULT_HOT_HEIGHT_PERCENT));
            out.putInt(ConfigContract.KEY_ICON_SIZE_DP, prefs.getInt(ConfigContract.KEY_ICON_SIZE_DP, ConfigContract.DEFAULT_ICON_SIZE_DP));
            out.putBoolean(ConfigContract.KEY_CUSTOM_WINDOW_BOUNDS_ENABLED,
                    prefs.getBoolean(ConfigContract.KEY_CUSTOM_WINDOW_BOUNDS_ENABLED,
                            ConfigContract.DEFAULT_CUSTOM_WINDOW_BOUNDS_ENABLED));
            out.putBoolean(ConfigContract.KEY_CUSTOM_WINDOW_PORTRAIT_ENABLED,
                    prefs.getBoolean(ConfigContract.KEY_CUSTOM_WINDOW_PORTRAIT_ENABLED,
                            prefs.getBoolean(ConfigContract.KEY_CUSTOM_WINDOW_BOUNDS_ENABLED,
                                    ConfigContract.DEFAULT_CUSTOM_WINDOW_PORTRAIT_ENABLED)));
            out.putBoolean(ConfigContract.KEY_CUSTOM_WINDOW_LANDSCAPE_ENABLED,
                    prefs.getBoolean(ConfigContract.KEY_CUSTOM_WINDOW_LANDSCAPE_ENABLED,
                            ConfigContract.DEFAULT_CUSTOM_WINDOW_LANDSCAPE_ENABLED));
            out.putBoolean(ConfigContract.KEY_PORTRAIT_PROPORTIONAL_SIZE_ENABLED,
                    prefs.getBoolean(ConfigContract.KEY_PORTRAIT_PROPORTIONAL_SIZE_ENABLED,
                            ConfigContract.DEFAULT_PORTRAIT_PROPORTIONAL_SIZE_ENABLED));
            out.putBoolean(ConfigContract.KEY_LANDSCAPE_PROPORTIONAL_SIZE_ENABLED,
                    prefs.getBoolean(ConfigContract.KEY_LANDSCAPE_PROPORTIONAL_SIZE_ENABLED,
                            ConfigContract.DEFAULT_LANDSCAPE_PROPORTIONAL_SIZE_ENABLED));
            out.putInt(ConfigContract.KEY_WIDTH_PERCENT, prefs.getInt(ConfigContract.KEY_WIDTH_PERCENT, ConfigContract.DEFAULT_WIDTH_PERCENT));
            out.putInt(ConfigContract.KEY_HEIGHT_PERCENT, prefs.getInt(ConfigContract.KEY_HEIGHT_PERCENT, ConfigContract.DEFAULT_HEIGHT_PERCENT));
            out.putInt(ConfigContract.KEY_PORTRAIT_NATIVE_SCALE_PERCENT,
                    clamp(prefs.getInt(ConfigContract.KEY_PORTRAIT_NATIVE_SCALE_PERCENT,
                                    ConfigContract.DEFAULT_NATIVE_WINDOW_SCALE_PERCENT),
                            ConfigContract.MIN_NATIVE_WINDOW_SCALE_PERCENT,
                            ConfigContract.MAX_NATIVE_WINDOW_SCALE_PERCENT));
            out.putInt(ConfigContract.KEY_POSITION_X, WindowPositionPolicy.clampPercent(
                    prefs.getInt(ConfigContract.KEY_POSITION_X,
                            ConfigContract.DEFAULT_POSITION_X)));
            out.putInt(ConfigContract.KEY_POSITION_Y, WindowPositionPolicy.clampPercent(
                    prefs.getInt(ConfigContract.KEY_POSITION_Y,
                            ConfigContract.DEFAULT_POSITION_Y)));
            out.putInt(ConfigContract.KEY_LANDSCAPE_WIDTH_PERCENT, clamp(prefs.getInt(
                    ConfigContract.KEY_LANDSCAPE_WIDTH_PERCENT,
                    ConfigContract.DEFAULT_LANDSCAPE_WIDTH_PERCENT),
                    ConfigContract.MIN_LANDSCAPE_WIDTH_PERCENT,
                    ConfigContract.MAX_LANDSCAPE_WIDTH_PERCENT));
            out.putInt(ConfigContract.KEY_LANDSCAPE_HEIGHT_PERCENT, prefs.getInt(
                    ConfigContract.KEY_LANDSCAPE_HEIGHT_PERCENT,
                    ConfigContract.DEFAULT_LANDSCAPE_HEIGHT_PERCENT));
            out.putInt(ConfigContract.KEY_LANDSCAPE_NATIVE_SCALE_PERCENT,
                    clamp(prefs.getInt(ConfigContract.KEY_LANDSCAPE_NATIVE_SCALE_PERCENT,
                                    ConfigContract.DEFAULT_NATIVE_WINDOW_SCALE_PERCENT),
                            ConfigContract.MIN_NATIVE_WINDOW_SCALE_PERCENT,
                            ConfigContract.MAX_NATIVE_WINDOW_SCALE_PERCENT));
            out.putInt(ConfigContract.KEY_LANDSCAPE_POSITION_X,
                    WindowPositionPolicy.clampPercent(prefs.getInt(
                            ConfigContract.KEY_LANDSCAPE_POSITION_X,
                            ConfigContract.DEFAULT_LANDSCAPE_POSITION_X)));
            out.putInt(ConfigContract.KEY_LANDSCAPE_POSITION_Y,
                    WindowPositionPolicy.clampPercent(prefs.getInt(
                            ConfigContract.KEY_LANDSCAPE_POSITION_Y,
                            ConfigContract.DEFAULT_LANDSCAPE_POSITION_Y)));
            out.putInt(ConfigContract.KEY_OUTSIDE_SINGLE_ACTION, prefs.getInt(ConfigContract.KEY_OUTSIDE_SINGLE_ACTION, ConfigContract.DEFAULT_OUTSIDE_SINGLE_ACTION));
            out.putInt(ConfigContract.KEY_OUTSIDE_DOUBLE_ACTION, prefs.getInt(ConfigContract.KEY_OUTSIDE_DOUBLE_ACTION, ConfigContract.DEFAULT_OUTSIDE_DOUBLE_ACTION));
            out.putInt(ConfigContract.KEY_OUTSIDE_TAP_WINDOW_MS, clamp(prefs.getInt(
                    ConfigContract.KEY_OUTSIDE_TAP_WINDOW_MS,
                    ConfigContract.DEFAULT_OUTSIDE_TAP_WINDOW_MS),
                    ConfigContract.MIN_OUTSIDE_TAP_WINDOW_MS,
                    ConfigContract.MAX_OUTSIDE_TAP_WINDOW_MS));
            out.putBoolean(ConfigContract.KEY_OUTSIDE_ENABLED, prefs.getBoolean(
                    ConfigContract.KEY_OUTSIDE_ENABLED,
                    ConfigContract.DEFAULT_OUTSIDE_ENABLED));
            out.putBoolean(ConfigContract.KEY_OUTSIDE_PORTRAIT_ENABLED,
                    prefs.getBoolean(ConfigContract.KEY_OUTSIDE_PORTRAIT_ENABLED,
                            prefs.getBoolean(ConfigContract.KEY_OUTSIDE_ENABLED,
                                    ConfigContract.DEFAULT_OUTSIDE_PORTRAIT_ENABLED)));
            out.putBoolean(ConfigContract.KEY_OUTSIDE_LANDSCAPE_ENABLED,
                    prefs.getBoolean(ConfigContract.KEY_OUTSIDE_LANDSCAPE_ENABLED,
                            ConfigContract.DEFAULT_OUTSIDE_LANDSCAPE_ENABLED));
            out.putString(ConfigContract.KEY_COMPONENTS, prefs.getString(ConfigContract.KEY_COMPONENTS, "[]"));
            out.putBoolean(ConfigContract.KEY_HONEYCOMB_ENABLED, prefs.getBoolean(
                    ConfigContract.KEY_HONEYCOMB_ENABLED,
                    ConfigContract.DEFAULT_HONEYCOMB_ENABLED));
            out.putString(ConfigContract.KEY_HONEYCOMB_COMPONENTS, prefs.getString(
                    ConfigContract.KEY_HONEYCOMB_COMPONENTS, "[]"));
            out.putString(ConfigContract.KEY_HONEYCOMB_POSITIONS, prefs.getString(
                    ConfigContract.KEY_HONEYCOMB_POSITIONS, ""));
            out.putInt(ConfigContract.KEY_HONEYCOMB_MODE, clamp(prefs.getInt(
                    ConfigContract.KEY_HONEYCOMB_MODE,
                    ConfigContract.DEFAULT_HONEYCOMB_MODE), 0, 1));
            out.putInt(ConfigContract.KEY_HONEYCOMB_ICON_SIZE_DP, clamp(prefs.getInt(
                    ConfigContract.KEY_HONEYCOMB_ICON_SIZE_DP,
                    ConfigContract.DEFAULT_HONEYCOMB_ICON_SIZE_DP),
                    ConfigContract.MIN_HONEYCOMB_ICON_SIZE_DP,
                    ConfigContract.MAX_HONEYCOMB_ICON_SIZE_DP));
            out.putInt(ConfigContract.KEY_HONEYCOMB_SPACING_DP, clamp(prefs.getInt(
                    ConfigContract.KEY_HONEYCOMB_SPACING_DP,
                    ConfigContract.DEFAULT_HONEYCOMB_SPACING_DP),
                    ConfigContract.MIN_HONEYCOMB_SPACING_DP,
                    ConfigContract.MAX_HONEYCOMB_SPACING_DP));
            out.putInt(ConfigContract.KEY_HONEYCOMB_ANIMATION_SPEED, clamp(prefs.getInt(
                    ConfigContract.KEY_HONEYCOMB_ANIMATION_SPEED,
                    ConfigContract.DEFAULT_HONEYCOMB_ANIMATION_SPEED), 0, 4));
            out.putBoolean(ConfigContract.KEY_HONEYCOMB_CENTERED_SYSTEM_ANIMATION,
                    prefs.getBoolean(ConfigContract.KEY_HONEYCOMB_CENTERED_SYSTEM_ANIMATION,
                            ConfigContract.DEFAULT_HONEYCOMB_CENTERED_SYSTEM_ANIMATION));
            out.putInt(ConfigContract.KEY_HONEYCOMB_INERTIA, clamp(prefs.getInt(
                    ConfigContract.KEY_HONEYCOMB_INERTIA,
                    ConfigContract.DEFAULT_HONEYCOMB_INERTIA), 0, 2));
            out.putInt(ConfigContract.KEY_HONEYCOMB_CENTER_SCALE, clamp(prefs.getInt(
                    ConfigContract.KEY_HONEYCOMB_CENTER_SCALE,
                    ConfigContract.DEFAULT_HONEYCOMB_CENTER_SCALE),
                    ConfigContract.MIN_HONEYCOMB_CENTER_SCALE,
                    ConfigContract.MAX_HONEYCOMB_CENTER_SCALE));
            out.putInt(ConfigContract.KEY_HONEYCOMB_EDGE_SCALE, clamp(prefs.getInt(
                    ConfigContract.KEY_HONEYCOMB_EDGE_SCALE,
                    ConfigContract.DEFAULT_HONEYCOMB_EDGE_SCALE),
                    ConfigContract.MIN_HONEYCOMB_EDGE_SCALE,
                    ConfigContract.MAX_HONEYCOMB_EDGE_SCALE));
            out.putInt(ConfigContract.KEY_HONEYCOMB_SELECTION_SCALE, clamp(prefs.getInt(
                    ConfigContract.KEY_HONEYCOMB_SELECTION_SCALE,
                    ConfigContract.DEFAULT_HONEYCOMB_SELECTION_SCALE),
                    ConfigContract.MIN_HONEYCOMB_SELECTION_SCALE,
                    ConfigContract.MAX_HONEYCOMB_SELECTION_SCALE));
            out.putBoolean(ConfigContract.KEY_HONEYCOMB_SHOW_SELECTED_NAME,
                    prefs.getBoolean(ConfigContract.KEY_HONEYCOMB_SHOW_SELECTED_NAME,
                            ConfigContract.DEFAULT_HONEYCOMB_SHOW_SELECTED_NAME));
            out.putBoolean(ConfigContract.KEY_HONEYCOMB_EMPTY_TAP_CLOSE,
                    prefs.getBoolean(ConfigContract.KEY_HONEYCOMB_EMPTY_TAP_CLOSE,
                            ConfigContract.DEFAULT_HONEYCOMB_EMPTY_TAP_CLOSE));
            out.putInt(ConfigContract.KEY_FAN_MAX_TARGETS, clamp(prefs.getInt(
                    ConfigContract.KEY_FAN_MAX_TARGETS,
                    ConfigContract.DEFAULT_FAN_MAX_TARGETS), 3, 24));
            out.putInt(ConfigContract.KEY_HONEYCOMB_MAX_TARGETS, clamp(prefs.getInt(
                    ConfigContract.KEY_HONEYCOMB_MAX_TARGETS,
                    ConfigContract.DEFAULT_HONEYCOMB_MAX_TARGETS), 1, 60));
            out.putString(ConfigContract.KEY_SIDE_COMPONENTS, prefs.getString(
                    ConfigContract.KEY_SIDE_COMPONENTS, "[]"));
            out.putBoolean(ConfigContract.KEY_SIDE_FOLLOW_HONEYCOMB, prefs.getBoolean(
                    ConfigContract.KEY_SIDE_FOLLOW_HONEYCOMB,
                    ConfigContract.DEFAULT_SIDE_FOLLOW_HONEYCOMB));
            out.putInt(ConfigContract.KEY_SIDE_MAX_TARGETS, clamp(prefs.getInt(
                    ConfigContract.KEY_SIDE_MAX_TARGETS,
                    ConfigContract.DEFAULT_SIDE_MAX_TARGETS), 1, 36));
            out.putBoolean(ConfigContract.KEY_SIDE_DIRECTION_HORIZONTAL, prefs.getBoolean(
                    ConfigContract.KEY_SIDE_DIRECTION_HORIZONTAL, true));
            out.putBoolean(ConfigContract.KEY_SIDE_DIRECTION_UP, prefs.getBoolean(
                    ConfigContract.KEY_SIDE_DIRECTION_UP, true));
            out.putBoolean(ConfigContract.KEY_SIDE_DIRECTION_DOWN, prefs.getBoolean(
                    ConfigContract.KEY_SIDE_DIRECTION_DOWN, true));
            out.putBoolean(ConfigContract.KEY_SIDE_FULLSCREEN, prefs.getBoolean(
                    ConfigContract.KEY_SIDE_FULLSCREEN,
                    ConfigContract.DEFAULT_SIDE_FULLSCREEN));
            out.putBoolean(ConfigContract.KEY_BOTTOM_FULLSCREEN, prefs.getBoolean(
                    ConfigContract.KEY_BOTTOM_FULLSCREEN,
                    ConfigContract.DEFAULT_BOTTOM_FULLSCREEN));
            out.putBoolean(ConfigContract.KEY_BOTTOM_HONEYCOMB_FREEFORM, prefs.getBoolean(
                    ConfigContract.KEY_BOTTOM_HONEYCOMB_FREEFORM,
                    ConfigContract.DEFAULT_BOTTOM_HONEYCOMB_FREEFORM));
            out.putBoolean(ConfigContract.KEY_HONEYCOMB_FOLLOW_FINGER, prefs.getBoolean(
                    ConfigContract.KEY_HONEYCOMB_FOLLOW_FINGER,
                    ConfigContract.DEFAULT_HONEYCOMB_FOLLOW_FINGER));
            out.putBoolean(ConfigContract.KEY_HONEYCOMB_LANDSCAPE_ENABLED,
                    prefs.getBoolean(ConfigContract.KEY_HONEYCOMB_LANDSCAPE_ENABLED,
                            ConfigContract.DEFAULT_HONEYCOMB_LANDSCAPE_ENABLED));
            out.putInt(ConfigContract.KEY_HONEYCOMB_FIXED_X_PERCENT, clamp(prefs.getInt(
                    ConfigContract.KEY_HONEYCOMB_FIXED_X_PERCENT,
                    ConfigContract.DEFAULT_HONEYCOMB_FIXED_X_PERCENT), 0, 100));
            out.putInt(ConfigContract.KEY_HONEYCOMB_FIXED_Y_PERCENT, clamp(prefs.getInt(
                    ConfigContract.KEY_HONEYCOMB_FIXED_Y_PERCENT,
                    ConfigContract.DEFAULT_HONEYCOMB_FIXED_Y_PERCENT), 0, 100));
            out.putInt(ConfigContract.KEY_HONEYCOMB_BACKGROUND_STYLE, clamp(prefs.getInt(
                    ConfigContract.KEY_HONEYCOMB_BACKGROUND_STYLE, 0), 0, 1));
            out.putInt(ConfigContract.KEY_HONEYCOMB_BLUR_DP, clamp(prefs.getInt(
                    ConfigContract.KEY_HONEYCOMB_BLUR_DP, 36), 0, 60));
            out.putInt(ConfigContract.KEY_HONEYCOMB_DIM_PERCENT, clamp(prefs.getInt(
                    ConfigContract.KEY_HONEYCOMB_DIM_PERCENT, 22), 0, 60));
            out.putBoolean(ConfigContract.KEY_HONEYCOMB_APP_BACKGROUND_ENABLED,
                    prefs.getBoolean(ConfigContract.KEY_HONEYCOMB_APP_BACKGROUND_ENABLED,
                            ConfigContract.DEFAULT_HONEYCOMB_APP_BACKGROUND_ENABLED));
            out.putBoolean(ConfigContract.KEY_HONEYCOMB_LIVE_BLUR_ENABLED,
                    prefs.getBoolean(ConfigContract.KEY_HONEYCOMB_LIVE_BLUR_ENABLED,
                            ConfigContract.DEFAULT_HONEYCOMB_LIVE_BLUR_ENABLED));
            out.putInt(ConfigContract.KEY_HONEYCOMB_LIVE_BLUR_DP, clamp(prefs.getInt(
                    ConfigContract.KEY_HONEYCOMB_LIVE_BLUR_DP,
                    ConfigContract.DEFAULT_HONEYCOMB_LIVE_BLUR_DP), 0, 60));
            out.putInt(ConfigContract.KEY_HONEYCOMB_BACKGROUND_DIM_PERCENT,
                    clamp(prefs.getInt(ConfigContract.KEY_HONEYCOMB_BACKGROUND_DIM_PERCENT,
                            ConfigContract.DEFAULT_HONEYCOMB_BACKGROUND_DIM_PERCENT), 0, 60));
            out.putInt(ConfigContract.KEY_HONEYCOMB_DISC_SIZE_PERCENT, clamp(prefs.getInt(
                    ConfigContract.KEY_HONEYCOMB_DISC_SIZE_PERCENT,
                    ConfigContract.DEFAULT_HONEYCOMB_DISC_SIZE_PERCENT),
                    ConfigContract.MIN_HONEYCOMB_DISC_SIZE_PERCENT,
                    ConfigContract.MAX_HONEYCOMB_DISC_SIZE_PERCENT));
            out.putStringArrayList(ConfigContract.KEY_GESTURE_EXCLUDED_PACKAGES,
                    gestureExcludedPackages(context));
            return out;
        }

        if ("report".equals(method) && extras != null) {
            prefs.edit()
                    .putString(ConfigContract.KEY_INTERFACE_STATUS, extras.getString(ConfigContract.KEY_INTERFACE_STATUS, ""))
                    .putLong(ConfigContract.KEY_INTERFACE_TIME, System.currentTimeMillis())
                    .apply();
            context.getContentResolver().notifyChange(ConfigContract.STATUS_URI, null);
            return Bundle.EMPTY;
        }
        if ("report_shortcuts".equals(method) && extras != null) {
            String catalog = extras.getString(ConfigContract.KEY_SHORTCUT_CATALOG, "[]");
            if (!catalog.equals(prefs.getString(ConfigContract.KEY_SHORTCUT_CATALOG, "[]"))) {
                prefs.edit().putString(ConfigContract.KEY_SHORTCUT_CATALOG, catalog).apply();
                context.getContentResolver().notifyChange(ConfigContract.CATALOG_URI, null);
            }
            pruneShortcutIcons(context, catalog);
            return Bundle.EMPTY;
        }
        if (ShortcutIconLoader.METHOD_REPORT.equals(method) && extras != null) {
            ArrayList<String> keys = extras.getStringArrayList(ShortcutIconLoader.EXTRA_KEYS);
            @SuppressWarnings("deprecation")
            ArrayList<Bitmap> icons = extras.getParcelableArrayList(
                    ShortcutIconLoader.EXTRA_ICONS);
            if (keys != null && icons != null) {
                int count = Math.min(keys.size(), icons.size());
                for (int index = 0; index < count; index++) {
                    saveShortcutIcon(context, keys.get(index), icons.get(index));
                }
            }
            return Bundle.EMPTY;
        }
        if (ShortcutIconLoader.METHOD_GET.equals(method) && arg != null) {
            Bitmap icon = readShortcutIcon(context, arg);
            Bundle out = new Bundle();
            if (icon != null) out.putParcelable(ShortcutIconLoader.EXTRA_ICON, icon);
            return out;
        }
        if ("report_activities".equals(method) && extras != null) {
            String catalog = extras.getString(ConfigContract.KEY_ACTIVITY_CATALOG, "[]");
            if (!catalog.equals(prefs.getString(ConfigContract.KEY_ACTIVITY_CATALOG, "[]"))) {
                prefs.edit().putString(ConfigContract.KEY_ACTIVITY_CATALOG, catalog).apply();
                context.getContentResolver().notifyChange(ConfigContract.CATALOG_URI, null);
            }
            return Bundle.EMPTY;
        }
        if ("report_diagnostics".equals(method) && extras != null) {
            String process = extras.getString(ConfigContract.EXTRA_DIAGNOSTIC_PROCESS, "");
            String key;
            if ("com.android.systemui".equals(process)) {
                key = ConfigContract.KEY_DIAGNOSTICS_SYSTEM_UI;
            } else if ("com.miui.home".equals(process)) {
                key = ConfigContract.KEY_DIAGNOSTICS_MIUI_HOME;
            } else {
                return Bundle.EMPTY;
            }
            String diagnostics = extras.getString(
                    ConfigContract.EXTRA_DIAGNOSTIC_TEXT, "");
            if (diagnostics.length() > 32000) {
                diagnostics = diagnostics.substring(diagnostics.length() - 32000);
            }
            prefs.edit().putString(key, diagnostics).apply();
            return Bundle.EMPTY;
        }
        return super.call(method, arg, extras);
    }

    private static ArrayList<String> gestureExcludedPackages(Context context) {
        DragShareSettings settings = DragShareSettings.readLocal(context);
        LinkedHashSet<String> excluded = new LinkedHashSet<>(
                settings.accessibilityBlacklistedPackages);
        if (settings.pauseAutomaticInGames) {
            excluded.addAll(settings.manualGamePackages);
            try {
                PackageManager packageManager = context.getPackageManager();
                for (ApplicationInfo application : packageManager.getInstalledApplications(
                        PackageManager.ApplicationInfoFlags.of(0))) {
                    if (application.packageName != null
                            && (application.category == ApplicationInfo.CATEGORY_GAME
                            || GameCapturePolicy.isBuiltInGame(application.packageName))) {
                        excluded.add(application.packageName);
                    }
                }
            } catch (SecurityException error) {
                Log.w(TAG, "Cannot enumerate installed games for gesture exclusions", error);
            }
        }
        return new ArrayList<>(excluded);
    }

    private Context requireProviderContext() {
        Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("Provider context is unavailable");
        }
        return context;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void saveShortcutIcon(Context context, String key, Bitmap icon) {
        if (key == null || key.isEmpty() || icon == null) return;
        File directory = new File(context.getFilesDir(), "shortcut-icons");
        File file = new File(directory, iconFileName(key));
        try {
            if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
                throw new IOException("Unable to create shortcut icon cache directory");
            }
            try (FileOutputStream output = new FileOutputStream(file)) {
                if (!icon.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw new IOException("Bitmap encoder rejected PNG output");
                }
            }
        } catch (IOException | SecurityException | IllegalStateException error) {
            Log.e(TAG, "Unable to save shortcut icon cache file " + file, error);
            throw new IllegalStateException("Unable to save shortcut icon cache file: " + file,
                    error);
        }
    }

    private static void pruneShortcutIcons(Context context, String catalog) {
        final JSONArray entries;
        try {
            entries = new JSONArray(catalog);
        } catch (JSONException error) {
            Log.w(TAG, "Ignoring malformed shortcut catalog while pruning icon cache", error);
            return;
        }
        Set<String> expectedFiles = new HashSet<>();
        for (int index = 0; index < entries.length(); index++) {
            JSONObject entry = entries.optJSONObject(index);
            if (entry == null) continue;
            String packageName = entry.optString("package", "");
            String shortcutId = entry.optString("id", "");
            if (packageName.isEmpty() || shortcutId.isEmpty()) continue;
            int userId = entry.optInt("userId", 0);
            expectedFiles.add(iconFileName(ShortcutIconLoader.key(
                    packageName, shortcutId, userId)));
        }
        // A temporarily missing catalog entry must not remove a selected shortcut's icon.
        SharedPreferences prefs = context.getSharedPreferences(ConfigContract.PREFS,
                Context.MODE_PRIVATE);
        for (String targetKey : new String[] { ConfigContract.KEY_COMPONENTS,
                ConfigContract.KEY_HONEYCOMB_COMPONENTS, ConfigContract.KEY_SIDE_COMPONENTS }) {
            try {
                JSONArray targets = new JSONArray(prefs.getString(targetKey, "[]"));
                for (int index = 0; index < targets.length(); index++) {
                    JSONObject target = targets.optJSONObject(index);
                    if (target == null) continue;
                    String packageName = target.optString("package", "");
                    String shortcutId = target.optString("id", "");
                    if (!packageName.isEmpty() && !shortcutId.isEmpty()) {
                        expectedFiles.add(iconFileName(ShortcutIconLoader.key(packageName,
                                shortcutId, target.optInt("userId", 0))));
                    }
                }
            } catch (JSONException error) {
                Log.w(TAG, "Skipping icon cleanup because selected targets cannot be read", error);
                return;
            }
        }
        File directory = new File(context.getFilesDir(), "shortcut-icons");
        try {
            if (!directory.exists()) return;
            File[] files = directory.listFiles();
            if (files == null) {
                Log.w(TAG, "Unable to list shortcut icon cache directory: " + directory);
                return;
            }
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".png")
                        && !expectedFiles.contains(file.getName())) {
                    // This directory contains only generated shortcut icon files.
                    if (!file.delete()) {
                        Log.w(TAG, "Unable to remove stale shortcut icon cache file: " + file);
                    }
                }
            }
        } catch (SecurityException error) {
            Log.w(TAG, "Unable to prune shortcut icon cache directory: " + directory, error);
        }
    }

    private static Bitmap readShortcutIcon(Context context, String key) {
        File file = new File(new File(context.getFilesDir(), "shortcut-icons"),
                iconFileName(key));
        try {
            if (!file.isFile()) return null;
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap == null) {
                Log.w(TAG, "Unable to decode shortcut icon cache file: " + file);
            }
            return bitmap;
        } catch (SecurityException | IllegalArgumentException error) {
            Log.w(TAG, "Unable to read shortcut icon cache file: " + file, error);
            return null;
        }
    }

    private static String iconFileName(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    key.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder(digest.length * 2 + 4);
            for (byte value : digest) {
                name.append(Character.forDigit((value >>> 4) & 0xf, 16));
                name.append(Character.forDigit(value & 0xf, 16));
            }
            return name.append(".png").toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable for shortcut icon keys",
                    error);
        }
    }

    private void enforceCaller() {
        Context context = requireProviderContext();
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid() || uid == Process.SYSTEM_UID) {
            return;
        }
        String[] packages = context.getPackageManager().getPackagesForUid(uid);
        if (packages != null) {
            for (String packageName : packages) {
                if ("com.android.systemui".equals(packageName)
                        || "com.miui.home".equals(packageName)) {
                    return;
                }
            }
        }
        throw new SecurityException("Caller cannot read FanFreeform configuration");
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}

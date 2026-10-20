package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Values consumed by the imported BigBang word-chip core. DragShare does not expose the
 * reference app's separate search-settings surface, so the original defaults are retained.
 */
final class BigBangSettings {
    private static final String PREFERENCES_NAME = "text_panel_settings";
    private static final String KEY_SELECTION_HAPTIC_ENABLED = "selection_haptic_enabled";
    private static final String KEY_EXIT_AFTER_COPY_ENABLED = "exit_after_copy_enabled";
    private static final String KEY_TEXT_SIZE_SP = "text_size_sp";
    private static final String KEY_PANEL_HEIGHT_FRACTION = "panel_height_fraction";
    private static final String KEY_PANEL_OPACITY = "panel_opacity";
    private static final String KEY_PANEL_ADJUSTMENT_ENABLED = "panel_adjustment_enabled";
    private static final String KEY_PANEL_OFFSET_X_DP = "panel_offset_x_dp";
    private static final String KEY_PANEL_OFFSET_Y_DP = "panel_offset_y_dp";

    static final float DEFAULT_TEXT_SIZE_SP = 15f;
    static final float DEFAULT_PANEL_HEIGHT_FRACTION = 0.6f;
    static final float DEFAULT_PANEL_OPACITY = 1f;
    private static final BigBangSettings INSTANCE = new BigBangSettings();

    private BigBangSettings() {}

    static BigBangSettings get(Context context) {
        return INSTANCE;
    }

    int getWebSearchType() {
        return BoomActionHandler.SEARCH_WEB;
    }

    int getDictSearchType() {
        return BoomActionHandler.SEARCH_DICTIONARY;
    }

    static boolean isSelectionHapticEnabled(Context context) {
        return preferences(context).getBoolean(KEY_SELECTION_HAPTIC_ENABLED, false);
    }

    static void setSelectionHapticEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_SELECTION_HAPTIC_ENABLED, enabled).apply();
    }

    static boolean isExitAfterCopyEnabled(Context context) {
        return preferences(context).getBoolean(KEY_EXIT_AFTER_COPY_ENABLED, false);
    }

    static void setExitAfterCopyEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_EXIT_AFTER_COPY_ENABLED, enabled).apply();
    }

    static float getTextSizeSp(Context context) {
        return preferences(context).getFloat(KEY_TEXT_SIZE_SP, DEFAULT_TEXT_SIZE_SP);
    }

    static void setTextSizeSp(Context context, float textSizeSp) {
        preferences(context).edit().putFloat(KEY_TEXT_SIZE_SP, textSizeSp).apply();
    }

    static float nextTextSizeSp(float current) {
        return nextValue(current, new float[]{15f, 18f, 12f});
    }

    static float getPanelHeightFraction(Context context) {
        return preferences(context).getFloat(KEY_PANEL_HEIGHT_FRACTION, DEFAULT_PANEL_HEIGHT_FRACTION);
    }

    static void setPanelHeightFraction(Context context, float fraction) {
        preferences(context).edit().putFloat(KEY_PANEL_HEIGHT_FRACTION, fraction).apply();
    }

    static float nextPanelHeightFraction(float current) {
        return nextValue(current, new float[]{0.45f, 0.6f, 0.8f});
    }

    static float getPanelOpacity(Context context) {
        return preferences(context).getFloat(KEY_PANEL_OPACITY, DEFAULT_PANEL_OPACITY);
    }

    static void setPanelOpacity(Context context, float opacity) {
        preferences(context).edit().putFloat(KEY_PANEL_OPACITY, opacity).apply();
    }

    static float nextPanelOpacity(float current) {
        return nextValue(current, new float[]{1f, 0.85f, 0.7f, 0.55f});
    }

    static boolean isPanelAdjustmentEnabled(Context context) {
        return preferences(context).getBoolean(KEY_PANEL_ADJUSTMENT_ENABLED, false);
    }

    static void setPanelAdjustmentEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_PANEL_ADJUSTMENT_ENABLED, enabled).apply();
    }

    static float getPanelOffsetXDp(Context context) {
        return preferences(context).getFloat(KEY_PANEL_OFFSET_X_DP, 0f);
    }

    static float getPanelOffsetYDp(Context context) {
        return preferences(context).getFloat(KEY_PANEL_OFFSET_Y_DP, 0f);
    }

    static void setPanelPositionDp(Context context, float xDp, float yDp) {
        preferences(context).edit()
                .putFloat(KEY_PANEL_OFFSET_X_DP, xDp)
                .putFloat(KEY_PANEL_OFFSET_Y_DP, yDp)
                .apply();
    }

    private static float nextValue(float current, float[] values) {
        int closestIndex = 0;
        float closestDistance = Math.abs(current - values[0]);
        for (int i = 1; i < values.length; i++) {
            float distance = Math.abs(current - values[i]);
            if (distance < closestDistance) {
                closestIndex = i;
                closestDistance = distance;
            }
        }
        return values[(closestIndex + 1) % values.length];
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}

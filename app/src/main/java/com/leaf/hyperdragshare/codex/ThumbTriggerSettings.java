package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/** Settings shared by the app and the injected process for the optional thumb trigger. */
public final class ThumbTriggerSettings {
    private static final String PREFS_NAME = "drag_share_settings";
    private static final String KEY_ENABLED = "thumb_trigger_enabled";
    private static final String KEY_THRESHOLD = "thumb_trigger_threshold";
    private static final String KEY_METRIC_VERSION = "thumb_trigger_metric_version";
    private static final int METRIC_VERSION = 2;

    public final boolean enabled;
    public final boolean calibrated;
    public final float threshold;
    public final boolean requiresRecalibration;

    private ThumbTriggerSettings(boolean enabled, boolean calibrated, float threshold,
                                 boolean requiresRecalibration) {
        this.enabled = enabled;
        this.calibrated = calibrated;
        this.threshold = threshold;
        this.requiresRecalibration = requiresRecalibration;
    }

    public static ThumbTriggerSettings read(Context context) {
        SharedPreferences preferences = preferences(context);
        float threshold = preferences.getFloat(KEY_THRESHOLD, 0f);
        boolean currentMetric = preferences.getInt(KEY_METRIC_VERSION, 0) == METRIC_VERSION;
        boolean calibrated = currentMetric && threshold > 0f && threshold <= 1f;
        return new ThumbTriggerSettings(
                preferences.getBoolean(KEY_ENABLED, false) && calibrated,
                calibrated, calibrated ? threshold : 0f, threshold > 0f && !currentMetric);
    }

    public static void saveCalibration(Context context, float threshold) {
        if (!(threshold > 0f) || threshold > 1f || !Float.isFinite(threshold)) {
            throw new IllegalArgumentException("Calibration threshold must be in (0, 1]");
        }
        preferences(context).edit().putFloat(KEY_THRESHOLD, threshold)
                .putInt(KEY_METRIC_VERSION, METRIC_VERSION)
                .putBoolean(KEY_ENABLED, false).apply();
        notifySettingsChanged(context);
    }

    public static void setEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        notifySettingsChanged(context);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static void notifySettingsChanged(Context context) {
        context.getContentResolver().notifyChange(
                Uri.parse("content://com.leaf.hyperdragshare.codex.share/settings"), null);
    }
}

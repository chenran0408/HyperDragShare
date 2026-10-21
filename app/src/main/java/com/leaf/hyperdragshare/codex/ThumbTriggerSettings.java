package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

/** Settings shared by the app and the injected process for the optional thumb trigger. */
public final class ThumbTriggerSettings {
    public static final float DEFAULT_TOUCH_THRESHOLD = .30f;
    public static final float DEFAULT_WIDTH_THRESHOLD = .095f;
    private static final String PREFS_NAME = "drag_share_settings";
    private static final String KEY_ENABLED = "thumb_trigger_enabled";
    private static final String KEY_THRESHOLD = "thumb_trigger_threshold";
    private static final String KEY_METRIC_VERSION = "thumb_trigger_metric_version";
    private static final int METRIC_VERSION = 2;

    public final boolean enabled;
    public final boolean calibrated;
    public final float threshold;
    public final float widthThreshold;

    private ThumbTriggerSettings(boolean enabled, boolean calibrated, float threshold,
                                 float widthThreshold) {
        this.enabled = enabled;
        this.calibrated = calibrated;
        this.threshold = threshold;
        this.widthThreshold = widthThreshold;
    }

    public static ThumbTriggerSettings read(Context context) {
        SharedPreferences preferences = preferences(context);
        return fromValues(preferences.getBoolean(KEY_ENABLED, false),
                preferences.getFloat(KEY_THRESHOLD, 0f),
                preferences.getInt(KEY_METRIC_VERSION, 0));
    }

    public static ThumbTriggerSettings readFromProvider(Context portalContext) {
        Bundle result = portalContext.getContentResolver().call(
                ImageStagingClient.BASE_URI, DragShareSettings.METHOD_GET_SETTINGS, null, null);
        if (result == null) throw new IllegalStateException("模块未返回拇指触发设置");
        return fromBundle(result);
    }

    static void appendToBundle(Context context, Bundle result) {
        SharedPreferences preferences = preferences(context);
        result.putBoolean(KEY_ENABLED, preferences.getBoolean(KEY_ENABLED, false));
        result.putFloat(KEY_THRESHOLD, preferences.getFloat(KEY_THRESHOLD, 0f));
        result.putInt(KEY_METRIC_VERSION, preferences.getInt(KEY_METRIC_VERSION, 0));
    }

    static ThumbTriggerSettings fromBundle(Bundle bundle) {
        return fromValues(bundle.getBoolean(KEY_ENABLED, false),
                bundle.getFloat(KEY_THRESHOLD, 0f), bundle.getInt(KEY_METRIC_VERSION, 0));
    }

    private static ThumbTriggerSettings fromValues(boolean enabled, float threshold, int metricVersion) {
        boolean currentMetric = metricVersion == METRIC_VERSION;
        boolean calibrated = currentMetric && threshold > 0f && threshold <= 1f;
        return new ThumbTriggerSettings(
                enabled, calibrated,
                calibrated ? threshold : DEFAULT_TOUCH_THRESHOLD,
                calibrated ? threshold : DEFAULT_WIDTH_THRESHOLD);
    }

    public static void saveCalibration(Context context, float threshold) {
        if (!(threshold > 0f) || threshold > 1f || !Float.isFinite(threshold)) {
            throw new IllegalArgumentException("Calibration threshold must be in (0, 1]");
        }
        preferences(context).edit().putFloat(KEY_THRESHOLD, threshold)
                .putInt(KEY_METRIC_VERSION, METRIC_VERSION).apply();
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

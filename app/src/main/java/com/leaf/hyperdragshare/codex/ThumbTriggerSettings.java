package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

/** Settings shared by the app and the injected process for the optional thumb trigger. */
public final class ThumbTriggerSettings {
    private static final String PREFS_NAME = "drag_share_settings";
    private static final String KEY_ENABLED = "thumb_trigger_enabled";
    private static final String KEY_THRESHOLD = "thumb_trigger_threshold";

    public final boolean enabled;
    public final boolean calibrated;
    public final float threshold;

    private ThumbTriggerSettings(boolean enabled, boolean calibrated, float threshold) {
        this.enabled = enabled;
        this.calibrated = calibrated;
        this.threshold = threshold;
    }

    public static ThumbTriggerSettings read(Context context) {
        SharedPreferences preferences = preferences(context);
        float threshold = preferences.getFloat(KEY_THRESHOLD, 0f);
        return new ThumbTriggerSettings(
                preferences.getBoolean(KEY_ENABLED, false), threshold > 0f, threshold);
    }

    public static void saveCalibration(Context context, float threshold) {
        if (!(threshold > 0f) || !Float.isFinite(threshold)) {
            throw new IllegalArgumentException("Calibration threshold must be finite and positive");
        }
        preferences(context).edit().putFloat(KEY_THRESHOLD, threshold).apply();
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

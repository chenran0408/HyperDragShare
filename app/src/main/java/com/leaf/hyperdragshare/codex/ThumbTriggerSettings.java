package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

/** Settings shared by the app and the injected process for the optional thumb trigger. */
public final class ThumbTriggerSettings {
    private static final String PREFS_NAME = "drag_share_settings";
    private static final String KEY_ENABLED = "thumb_trigger_enabled";

    public final boolean enabled;

    private ThumbTriggerSettings(boolean enabled) {
        this.enabled = enabled;
    }

    public static ThumbTriggerSettings read(Context context) {
        return new ThumbTriggerSettings(preferences(context).getBoolean(KEY_ENABLED, false));
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
    }

    static ThumbTriggerSettings fromBundle(Bundle bundle) {
        return new ThumbTriggerSettings(bundle.getBoolean(KEY_ENABLED, false));
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

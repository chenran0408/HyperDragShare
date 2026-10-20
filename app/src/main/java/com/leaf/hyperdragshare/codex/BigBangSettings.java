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
    private static final BigBangSettings INSTANCE = new BigBangSettings();

    private BigBangSettings() {}

    static BigBangSettings get(Context context) {
        return INSTANCE;
    }

    int getGapRowHeightPercent() {
        return 100;
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

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}

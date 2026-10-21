package com.leaf.hyperdragshare.gestures.config;

import android.content.SharedPreferences;

/** Upgrade defaults fill missing keys and leave every stored value authoritative. */
final class MissingPreferenceEditor {
    private final SharedPreferences preferences;
    private final SharedPreferences.Editor editor;

    private MissingPreferenceEditor(SharedPreferences preferences) {
        this.preferences = preferences;
        editor = preferences.edit();
    }

    static MissingPreferenceEditor edit(SharedPreferences preferences) {
        return new MissingPreferenceEditor(preferences);
    }

    MissingPreferenceEditor putBoolean(String key, boolean value) {
        if (!preferences.contains(key)) editor.putBoolean(key, value);
        return this;
    }

    MissingPreferenceEditor putInt(String key, int value) {
        if (!preferences.contains(key)) editor.putInt(key, value);
        return this;
    }

    MissingPreferenceEditor putString(String key, String value) {
        if (!preferences.contains(key)) editor.putString(key, value);
        return this;
    }

    boolean commit() { return editor.commit(); }
}

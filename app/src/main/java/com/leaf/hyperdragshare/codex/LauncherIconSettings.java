package com.leaf.hyperdragshare.codex;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

final class LauncherIconSettings {
    private static final String TAG = "DragShare/LauncherIcon";
    private static final ComponentName LAUNCHER_ALIAS = new ComponentName(
            "com.leaf.hyperdragshare.codex",
            "com.leaf.hyperdragshare.codex.LauncherAlias");

    private LauncherIconSettings() {}

    static boolean isEnabled(Context context) {
        int state = context.getPackageManager().getComponentEnabledSetting(LAUNCHER_ALIAS);
        return isEnabledState(state);
    }

    static boolean setEnabled(Context context, boolean enabled) {
        int state = enabled
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        try {
            context.getPackageManager().setComponentEnabledSetting(
                    LAUNCHER_ALIAS,
                    state,
                    PackageManager.DONT_KILL_APP);
            return true;
        } catch (IllegalArgumentException | SecurityException error) {
            DragShareLog.w(TAG, "unable to change launcher icon enabled=" + enabled, error);
            return false;
        }
    }

    static boolean isEnabledState(int state) {
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                && state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
    }
}

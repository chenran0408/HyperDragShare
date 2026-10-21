package com.leaf.hyperdragshare.gestures.xposed;

import android.os.Build;

import de.robv.android.xposed.XposedHelpers;

/** Compatibility switches recovered from the OS4 build150 APK. */
final class HyperOsProfile {
    static final HyperOsProfile CURRENT = detect();
    final boolean modernTaskSnapshot;
    final boolean directShortcutLaunch;
    final boolean nativeCleanCover;
    final boolean shortcutFreeformAlignment;
    final boolean nativeInputPriority;

    private HyperOsProfile(boolean os4) {
        modernTaskSnapshot = os4;
        directShortcutLaunch = os4;
        nativeCleanCover = os4;
        shortcutFreeformAlignment = os4;
        nativeInputPriority = os4;
    }

    private static HyperOsProfile detect() {
        String version = "";
        try {
            Class<?> properties = XposedHelpers.findClass("android.os.SystemProperties", null);
            Object value = XposedHelpers.callStaticMethod(properties, "get",
                    "ro.mi.os.version.name", "");
            if (value instanceof String) version = (String) value;
        } catch (Throwable ignored) { }
        boolean os4 = HyperOsVersionPolicy.useOs4Compatibility(version, Build.VERSION.SDK_INT);
        Log.i("Compatibility profile=" + (os4 ? "OS4" : "legacy")
                + " version=" + version + " sdk=" + Build.VERSION.SDK_INT);
        return new HyperOsProfile(os4);
    }
}

package com.leaf.hyperdragshare.codex;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Suppresses only the framework's alert-window notice for this module. */
final class OverlayNotificationHooks {
    private static final String TAG = "DragShare/OverlayNotice: ";
    private static final String ALERT_WINDOW_NOTIFICATION =
            "com.android.server.wm.AlertWindowNotification";
    private static volatile boolean installed;
    private static volatile boolean suppressionLogged;

    private OverlayNotificationHooks() {}

    static void install(ClassLoader loader) {
        if (installed) {
            return;
        }
        Class<?> notificationClass = XposedHelpers.findClassIfExists(
                ALERT_WINDOW_NOTIFICATION, loader);
        if (notificationClass == null) {
            log("framework class absent; original notification retained");
            return;
        }

        XC_MethodHook suppress = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!AlertWindowNotificationGuard.shouldSuppress(modulePackage(param.thisObject))) {
                    return;
                }
                param.setResult(null);
                if (!suppressionLogged) {
                    suppressionLogged = true;
                    log("suppressed framework notice for " + BuildConfig.APPLICATION_ID);
                }
            }
        };
        try {
            int hookCount = XposedBridge.hookAllMethods(
                    notificationClass, "post", suppress).size();
            hookCount += XposedBridge.hookAllMethods(
                    notificationClass, "onPostNotification", suppress).size();
            if (hookCount == 0) {
                log("notification methods absent; original notification retained");
                return;
            }
            installed = true;
            log("installed exact package hook build="
                    + BuildConfig.VERSION_NAME + "/" + BuildConfig.VERSION_CODE);
        } catch (RuntimeException | LinkageError error) {
            log("hook unavailable; original notification retained: " + error);
        }
    }

    private static String modulePackage(Object notification) {
        try {
            Object packageName = XposedHelpers.getObjectField(notification, "mPackageName");
            return packageName instanceof String ? (String) packageName : null;
        } catch (RuntimeException | LinkageError error) {
            return null;
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + message);
    }
}

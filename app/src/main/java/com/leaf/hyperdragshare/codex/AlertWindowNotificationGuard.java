package com.leaf.hyperdragshare.codex;

/** Decides which framework overlay notices belong to this module. */
final class AlertWindowNotificationGuard {
    private AlertWindowNotificationGuard() {}

    static boolean shouldSuppress(String packageName) {
        return BuildConfig.APPLICATION_ID.equals(packageName);
    }
}

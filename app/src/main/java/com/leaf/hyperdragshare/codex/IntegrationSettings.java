package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Settings for optional MIUI process integrations that are separate from drag behavior. */
public final class IntegrationSettings {
    public static final String METHOD_GET_AICR_SETTINGS = "get_aicr_settings";
    public static final String METHOD_OPEN_AICR_TEXT = "open_aicr_text";
    public static final String METHOD_GET_TRUST_SERVICE_SETTINGS = "get_trust_service_settings";
    public static final String EXTRA_COPY_DIRECT_ENABLED = "copy_direct_enabled";
    public static final String EXTRA_TRUST_SERVICE_RISK_DISABLED =
            "trust_service_risk_disabled";
    public static final String EXTRA_TEXT = "text";

    private static final String PREFS_NAME = "drag_share_integrations";
    private static final String KEY_COPY_DIRECT_ENABLED = "copy_direct_enabled";
    private static final String KEY_BACK_TAP_MODE = "back_tap_mode";
    private static final String KEY_TRUST_SERVICE_RISK_DISABLED =
            "trust_service_risk_disabled";
    private static final String BACK_TAP_GLOBAL_KEY = "dragshare_back_tap_mode";
    private static final boolean DEFAULT_COPY_DIRECT_ENABLED = false;

    private IntegrationSettings() {}

    public static boolean isCopyDirectEnabled(Context context) {
        return preferences(context).getBoolean(
                KEY_COPY_DIRECT_ENABLED,
                DEFAULT_COPY_DIRECT_ENABLED);
    }

    public static void setCopyDirectEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(KEY_COPY_DIRECT_ENABLED, enabled).apply();
    }

    public static boolean isTrustServiceRiskDetectionDisabled(Context context) {
        return preferences(context).getBoolean(KEY_TRUST_SERVICE_RISK_DISABLED, false);
    }

    public static void setTrustServiceRiskDetectionDisabled(Context context, boolean disabled) {
        preferences(context).edit()
                .putBoolean(KEY_TRUST_SERVICE_RISK_DISABLED, disabled)
                .apply();
    }

    public static String getCopyDirectBrowserPackage(Context context) {
        return preferences(context).getString("copy_direct_browser_package", "");
    }

    public static void setCopyDirectBrowserPackage(Context context, String packageName) {
        preferences(context).edit().putString("copy_direct_browser_package", packageName).apply();
    }

    public static int getBackTapMode(Context context) {
        return BackTapMode.normalize(preferences(context).getInt(KEY_BACK_TAP_MODE, BackTapMode.OFF));
    }

    public static void setBackTapMode(Context context, int mode) {
        preferences(context).edit()
                .putInt(KEY_BACK_TAP_MODE, BackTapMode.normalize(mode))
                .apply();
    }

    /** Called on an IO dispatcher after a user changes the optional back-tap setting. */
    public static boolean syncBackTapModeAsRoot(Context context) {
        int mode = getBackTapMode(context);
        Process process = null;
        try {
            process = new ProcessBuilder(
                    "su",
                    "-c",
                    "settings put global " + BACK_TAP_GLOBAL_KEY + " " + mode)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                DragShareLog.w("DragShare/BackTap", "writing the back-tap mode timed out");
                return false;
            }
            boolean succeeded = process.exitValue() == 0;
            if (!succeeded) {
                DragShareLog.w("DragShare/BackTap", "Root could not update the system back-tap mode");
            }
            return succeeded;
        } catch (IOException error) {
            DragShareLog.w("DragShare/BackTap", "Root is unavailable for the back-tap setting", error);
            return false;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            DragShareLog.w("DragShare/BackTap", "back-tap setting sync interrupted", error);
            return false;
        } finally {
            if (process != null) {
                try {
                    process.getInputStream().close();
                } catch (IOException ignored) {
                    // Process teardown only.
                }
                process.destroy();
            }
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}

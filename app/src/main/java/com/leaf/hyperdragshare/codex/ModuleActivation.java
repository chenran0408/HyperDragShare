package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Current-build injection handshake and root availability checks for the settings UI. */
final class ModuleActivation {
    static final String METHOD_REPORT_INJECTED = "report_injected";
    static final String EXTRA_VERSION_CODE = "version_code";
    static final String EXTRA_PROCESS_ID = "process_id";
    static final String EXTRA_PORTAL_ROOT_GRANTED = "portal_root_granted";
    static final String EXTRA_PRESS_READER_READY = "press_reader_ready";
    static final String EXTRA_PRESS_READER_ERROR = "press_reader_error";

    private static final String PREFS_NAME = "module_activation";
    private static final String KEY_INJECTED_VERSION = "injected_version";
    private static final String KEY_PORTAL_ROOT_VERSION = "portal_root_version";
    private static final String KEY_PROCESS_ID = "process_id";
    private static final String KEY_PORTAL_ROOT_GRANTED = "portal_root_granted";
    private static final String KEY_PRESS_READER_VERSION = "press_reader_version";
    private static final long ROOT_TIMEOUT_SECONDS = 4L;
    private static final String PORTAL_PROVIDER_URI =
            "content://com.miui.contentextension.provider.switchcontrolprovider";
    private static final String PORTAL_BLACKLIST_ACTIVITY_COMMAND =
            "am start --user current -n com.miui.contentextension/"
                    + "com.miui.contentextension.setting.whitelist.BlacklistSettingActivity";
    private static boolean portalRootProbeInFlight;

    private ModuleActivation() {}

    static boolean reportInjected(Context portalContext) {
        Bundle extras = new Bundle();
        extras.putLong(EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
        extras.putInt(EXTRA_PROCESS_ID, android.os.Process.myPid());
        return reportPortalStatus(portalContext, extras);
    }

    static void reportPressReader(Context context, boolean ready, String error) {
        if (MainHook.TAPLUS_PACKAGE.equals(context.getPackageName())) {
            Bundle extras = new Bundle();
            extras.putLong(EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
            extras.putInt(EXTRA_PROCESS_ID, android.os.Process.myPid());
            extras.putBoolean(EXTRA_PRESS_READER_READY, ready);
            extras.putString(EXTRA_PRESS_READER_ERROR, error);
            reportPortalStatus(context, extras);
        } else if (BuildConfig.APPLICATION_ID.equals(context.getPackageName())) {
            AccessibilityRuntimeStatus.setPressReader(ready, error);
        }
    }

    /** Runs in the injected portal process so the root manager evaluates the portal UID. */
    static void probePortalRootAccessAsync(Context portalContext) {
        if (portalContext == null) {
            return;
        }
        final Context reportContext = portalContext.getApplicationContext() == null
                ? portalContext
                : portalContext.getApplicationContext();
        synchronized (ModuleActivation.class) {
            if (portalRootProbeInFlight) {
                return;
            }
            portalRootProbeInFlight = true;
        }
        new Thread(() -> {
            try {
                reportPortalRootAccess(reportContext, hasRootAccess());
            } finally {
                synchronized (ModuleActivation.class) {
                    portalRootProbeInFlight = false;
                }
            }
        }, "DragShare-PortalRootCheck").start();
    }

    private static boolean reportPortalRootAccess(Context portalContext, boolean rootGranted) {
        Bundle extras = new Bundle();
        extras.putLong(EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
        extras.putInt(EXTRA_PROCESS_ID, android.os.Process.myPid());
        extras.putBoolean(EXTRA_PORTAL_ROOT_GRANTED, rootGranted);
        return reportPortalStatus(portalContext, extras);
    }

    private static boolean reportPortalStatus(Context portalContext, Bundle extras) {
        if (portalContext == null) {
            return false;
        }
        try {
            portalContext.getContentResolver().call(
                    ImageStagingClient.BASE_URI,
                    METHOD_REPORT_INJECTED,
                    null,
                    extras);
            return true;
        } catch (RuntimeException | LinkageError error) {
            DragShareLog.w("DragShare/Activation", "cannot report portal injection", error);
            return false;
        }
    }

    static void recordInjected(Context moduleContext, Bundle extras) {
        long reportedVersion = extras == null
                ? -1L
                : extras.getLong(EXTRA_VERSION_CODE, -1L);
        if (!matchesCurrentBuild(reportedVersion)) {
            return;
        }
        SharedPreferences preferences = moduleContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        if (extras.getInt(EXTRA_PROCESS_ID, 0) != preferences.getInt(KEY_PROCESS_ID, 0)) {
            editor.remove(KEY_PORTAL_ROOT_VERSION).remove(KEY_PORTAL_ROOT_GRANTED)
                    .remove(KEY_PRESS_READER_VERSION).remove(EXTRA_PRESS_READER_READY).remove(EXTRA_PRESS_READER_ERROR);
        }
        editor
                .putLong(KEY_INJECTED_VERSION, reportedVersion)
                .putInt(KEY_PROCESS_ID, extras.getInt(EXTRA_PROCESS_ID, 0));
        if (extras != null && extras.containsKey(EXTRA_PORTAL_ROOT_GRANTED)) {
            editor.putLong(KEY_PORTAL_ROOT_VERSION, reportedVersion)
                    .putBoolean(
                            KEY_PORTAL_ROOT_GRANTED,
                            extras.getBoolean(EXTRA_PORTAL_ROOT_GRANTED));
        }
        if (extras.containsKey(EXTRA_PRESS_READER_READY)) {
            editor.putLong(KEY_PRESS_READER_VERSION, reportedVersion)
                    .putBoolean(EXTRA_PRESS_READER_READY, extras.getBoolean(EXTRA_PRESS_READER_READY))
                    .putString(EXTRA_PRESS_READER_ERROR, extras.getString(EXTRA_PRESS_READER_ERROR, ""));
        }
        editor.apply();
    }

    static boolean isCurrentBuildInjected(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences preferences = context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE);
        return matchesCurrentBuild(preferences.getLong(KEY_INJECTED_VERSION, -1L));
    }

    static boolean isCurrentBuildPortalRootGranted(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences preferences = context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE);
        return matchesCurrentBuild(preferences.getLong(KEY_PORTAL_ROOT_VERSION, -1L))
                && preferences.getBoolean(KEY_PORTAL_ROOT_GRANTED, false);
    }

    static boolean hasRootAccess() {
        return runRootCommand("id -u");
    }

    static boolean isPressReaderReady(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return matchesCurrentBuild(preferences.getLong(KEY_PRESS_READER_VERSION, -1L))
                && preferences.getBoolean(EXTRA_PRESS_READER_READY, false);
    }

    static String pressReaderError(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return matchesCurrentBuild(preferences.getLong(KEY_PRESS_READER_VERSION, -1L))
                ? preferences.getString(EXTRA_PRESS_READER_ERROR, "") : "";
    }

    /** Root reads the no-op provider to start the host without opening an Activity. */
    static boolean requestPortalInjectionHandshake() {
        return runRootCommand(portalHandshakeCommand());
    }

    static String portalHandshakeCommand() {
        return "content query --user $(am get-current-user) --uri " + PORTAL_PROVIDER_URI;
    }

    static int injectedProcessId(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_PROCESS_ID, 0);
    }

    static void clearPortalReport(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .remove(KEY_INJECTED_VERSION).remove(KEY_PORTAL_ROOT_VERSION)
                .remove(KEY_PRESS_READER_VERSION).remove(EXTRA_PRESS_READER_READY).remove(EXTRA_PRESS_READER_ERROR)
                .remove(KEY_PORTAL_ROOT_GRANTED).remove(KEY_PROCESS_ID).apply();
    }

    /** Starts Taplus' non-exported blacklist activity as root for the current Android user. */
    static boolean openPortalBlacklistSettings() {
        return runRootCommand(portalBlacklistCommand());
    }

    static String portalBlacklistCommand() {
        return PORTAL_BLACKLIST_ACTIVITY_COMMAND;
    }

    private static boolean runRootCommand(String command) {
        java.lang.Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            try (java.io.InputStream output = process.getInputStream();
                 java.io.OutputStream input = process.getOutputStream()) {
                if (!process.waitFor(ROOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    DragShareLog.w("DragShare/Activation", "Root command timed out", null);
                    return false;
                }
                if (process.exitValue() != 0) {
                    String reason = new String(output.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                    DragShareLog.w("DragShare/Activation", "Root command rejected: " + reason, null);
                    return false;
                }
                return true;
            }
        } catch (IOException error) {
            DragShareLog.w("DragShare/Activation", "Root command failed", error);
            return false;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            DragShareLog.w("DragShare/Activation", "Root command interrupted", error);
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    static boolean matchesCurrentBuild(long reportedVersion) {
        return reportedVersion == BuildConfig.VERSION_CODE;
    }
}

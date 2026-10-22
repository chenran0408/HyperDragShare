package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Current-build injection handshake and root availability checks for the settings UI. */
final class ModuleActivation {
    static final String METHOD_REPORT_INJECTED = "report_injected";
    static final String EXTRA_VERSION_CODE = "version_code";
    static final String EXTRA_PROCESS_ID = "process_id";
    static final String EXTRA_PORTAL_ROOT_GRANTED = "portal_root_granted";
    static final String EXTRA_PORTAL_SERVICE_READY = "portal_service_ready";
    static final String EXTRA_PRESS_READER_READY = "press_reader_ready";
    static final String EXTRA_PRESS_READER_ERROR = "press_reader_error";
    static final String EXTRA_ROOT_INPUT_READY = "root_input_ready";
    static final String EXTRA_ROOT_INPUT_ERROR = "root_input_error";
    static final String EXTRA_LIFETIME_TOKEN = "portal_lifetime_token";

    static final String PREFS_NAME = "module_activation";
    private static final String KEY_INJECTED_VERSION = "injected_version";
    private static final String KEY_PORTAL_ROOT_VERSION = "portal_root_version";
    private static final String KEY_PROCESS_ID = "process_id";
    private static final String KEY_PORTAL_ROOT_GRANTED = "portal_root_granted";
    private static final String KEY_PRESS_READER_VERSION = "press_reader_version";
    private static final String KEY_ROOT_INPUT_VERSION = "root_input_version";
    private static final long ROOT_TIMEOUT_SECONDS = 4L;
    private static final String PORTAL_START_COMMAND =
            "am start-foreground-service --user current -n "
                    + "com.miui.contentextension/.services.TextContentExtensionService";
    private static final String PORTAL_BLACKLIST_ACTIVITY_COMMAND =
            "am start --user current -n com.miui.contentextension/"
                    + "com.miui.contentextension.setting.whitelist.BlacklistSettingActivity";
    private static boolean portalRootProbeInFlight;
    // Each injected Portal process owns its token; the module never persists this capability.
    private static final Binder LIFETIME_TOKEN = new Binder();
    private static IBinder livePortal;
    private static int livePortalPid;
    private static IBinder.DeathRecipient portalDeath;

    private ModuleActivation() {}

    static boolean reportInjected(Context portalContext, boolean serviceReady) {
        Bundle extras = new Bundle();
        extras.putLong(EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
        extras.putInt(EXTRA_PROCESS_ID, android.os.Process.myPid());
        extras.putBoolean(EXTRA_PORTAL_SERVICE_READY, serviceReady);
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
        }
    }

    static void reportRootInput(Context context, boolean ready, String error) {
        Bundle extras = new Bundle();
        extras.putLong(EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
        extras.putInt(EXTRA_PROCESS_ID, android.os.Process.myPid());
        extras.putBoolean(EXTRA_ROOT_INPUT_READY, ready);
        extras.putString(EXTRA_ROOT_INPUT_ERROR, error);
        reportPortalStatus(context, extras);
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
        extras.putBinder(EXTRA_LIFETIME_TOKEN, LIFETIME_TOKEN);
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

    static synchronized void recordInjected(Context moduleContext, Bundle extras) {
        long reportedVersion = extras == null
                ? -1L
                : extras.getLong(EXTRA_VERSION_CODE, -1L);
        if (!matchesCurrentBuild(reportedVersion)) {
            return;
        }
        IBinder token = extras.getBinder(EXTRA_LIFETIME_TOKEN);
        int pid = extras.getInt(EXTRA_PROCESS_ID, 0);
        if (token == null || pid <= 0) {
            DragShareLog.w("DragShare/Activation", "Portal report has no valid lifetime token/PID");
            clearPortalReport(moduleContext);
            return;
        }
        if (livePortal != token || livePortalPid != pid) {
            clearPortalReport(moduleContext);
            Context app = moduleContext.getApplicationContext();
            IBinder.DeathRecipient death = () -> portalDied(app, token, pid);
            try {
                token.linkToDeath(death, 0);
            } catch (RemoteException error) {
                DragShareLog.w("DragShare/Activation", "Portal ended before its lifetime could be linked", error);
                return;
            }
            livePortal = token;
            livePortalPid = pid;
            portalDeath = death;
        }
        SharedPreferences preferences = moduleContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor
                .putLong(KEY_INJECTED_VERSION, reportedVersion)
                .putInt(KEY_PROCESS_ID, pid);
        if (extras.containsKey(EXTRA_PORTAL_SERVICE_READY)) {
            editor.putBoolean(EXTRA_PORTAL_SERVICE_READY, extras.getBoolean(EXTRA_PORTAL_SERVICE_READY));
        }
        if (extras.containsKey(EXTRA_PORTAL_ROOT_GRANTED)) {
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
        if (extras.containsKey(EXTRA_ROOT_INPUT_READY)) {
            editor.putLong(KEY_ROOT_INPUT_VERSION, reportedVersion)
                    .putBoolean(EXTRA_ROOT_INPUT_READY, extras.getBoolean(EXTRA_ROOT_INPUT_READY))
                    .putString(EXTRA_ROOT_INPUT_ERROR, extras.getString(EXTRA_ROOT_INPUT_ERROR, ""));
        }
        editor.apply();
    }

    static synchronized boolean isCurrentBuildInjected(Context context) {
        if (context == null || livePortal == null || !livePortal.isBinderAlive()) {
            return false;
        }
        SharedPreferences preferences = context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE);
        return matchesCurrentBuild(preferences.getLong(KEY_INJECTED_VERSION, -1L))
                && livePortalPid == preferences.getInt(KEY_PROCESS_ID, 0);
    }

    static synchronized boolean isCurrentBuildPortalRootGranted(Context context) {
        if (!isCurrentBuildInjected(context)) {
            return false;
        }
        SharedPreferences preferences = context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE);
        return matchesCurrentBuild(preferences.getLong(KEY_PORTAL_ROOT_VERSION, -1L))
                && preferences.getBoolean(KEY_PORTAL_ROOT_GRANTED, false);
    }

    static synchronized boolean isPortalServiceReady(Context context) {
        return isCurrentBuildInjected(context)
                && context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(EXTRA_PORTAL_SERVICE_READY, false);
    }

    static boolean hasRootAccess() {
        return runRootCommand("id -u");
    }

    static synchronized boolean isPressReaderReady(Context context) {
        if (!isCurrentBuildInjected(context)) return false;
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return matchesCurrentBuild(preferences.getLong(KEY_PRESS_READER_VERSION, -1L))
                && preferences.getBoolean(EXTRA_PRESS_READER_READY, false);
    }

    static synchronized String pressReaderError(Context context) {
        if (!isCurrentBuildInjected(context)) return "";
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return matchesCurrentBuild(preferences.getLong(KEY_PRESS_READER_VERSION, -1L))
                ? preferences.getString(EXTRA_PRESS_READER_ERROR, "") : "";
    }

    static synchronized boolean isRootInputReady(Context context) {
        if (!isCurrentBuildInjected(context)) return false;
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return matchesCurrentBuild(preferences.getLong(KEY_ROOT_INPUT_VERSION, -1L))
                && preferences.getBoolean(EXTRA_ROOT_INPUT_READY, false);
    }

    static synchronized String rootInputError(Context context) {
        if (!isCurrentBuildInjected(context)) return "";
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return matchesCurrentBuild(preferences.getLong(KEY_ROOT_INPUT_VERSION, -1L))
                ? preferences.getString(EXTRA_ROOT_INPUT_ERROR, "") : "";
    }

    /** Start the host's existing foreground service without opening an Activity. */
    static boolean requestPortalRuntimeStart(Context context) {
        RootProcessKeepAlive.Result keepAlive = RootProcessKeepAlive.ensureLocked(context);
        boolean notificationReady = ensureCopyDirectNotificationPermission(context);
        boolean started = runRootCommand(portalStartCommand());
        return keepAlive.ready && notificationReady && started;
    }

    static boolean ensureCopyDirectNotificationPermission(Context context) {
        if (!IntegrationSettings.isCopyDirectEnabled(context)) return true;
        if (context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED
                && !runRootCommand("pm grant --user 0 " + BuildConfig.APPLICATION_ID
                        + " android.permission.POST_NOTIFICATIONS")) return false;
        // HyperOS disables floating notifications for new channels independently of importance.
        // Configure only this feature's channel through the ROM's notification provider.
        String target = " --extra package:s:" + BuildConfig.APPLICATION_ID
                + " --extra channel_id:s:copy_direct";
        return runRootCommand("content call --uri content://statusbar.notification --method setFloat"
                + target + " --extra canShowFloat:b:true && "
                + "{ content call --uri content://statusbar.notification --method canFloat"
                + target + " | grep -q 'canShowFloat=true' || "
                + "{ echo 'MIUI copy_direct floating notification permission was not enabled'; exit 1; }; }");
    }

    static RootProcessKeepAlive.Result ensurePortalRootKeepAlive(Context context) {
        return RootProcessKeepAlive.ensureLocked(context);
    }

    static String portalStartCommand() {
        return PORTAL_START_COMMAND;
    }

    static synchronized int injectedProcessId(Context context) {
        return isCurrentBuildInjected(context) ? livePortalPid : 0;
    }

    static synchronized void clearPortalReport(Context context) {
        IBinder previous = livePortal;
        IBinder.DeathRecipient death = portalDeath;
        livePortal = null;
        livePortalPid = 0;
        portalDeath = null;
        if (previous != null) previous.unlinkToDeath(death, 0);
        clearStoredPortalReport(context);
    }

    private static synchronized void portalDied(Context context, IBinder token, int pid) {
        if (livePortal != token || livePortalPid != pid) return;
        livePortal = null;
        livePortalPid = 0;
        portalDeath = null;
        clearStoredPortalReport(context);
        DragShareLog.w("DragShare/Activation", "Portal process ended; cleared its runtime readiness pid=" + pid);
    }

    private static void clearStoredPortalReport(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .remove(KEY_INJECTED_VERSION).remove(KEY_PORTAL_ROOT_VERSION)
                .remove(KEY_ROOT_INPUT_VERSION).remove(EXTRA_ROOT_INPUT_READY).remove(EXTRA_ROOT_INPUT_ERROR)
                .remove(KEY_PRESS_READER_VERSION).remove(EXTRA_PRESS_READER_READY).remove(EXTRA_PRESS_READER_ERROR)
                .remove(KEY_PORTAL_ROOT_GRANTED).remove(EXTRA_PORTAL_SERVICE_READY)
                .remove(KEY_PROCESS_ID).apply();
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

package com.leaf.hyperdragshare.codex;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Version-independent compatibility for MMGG's AICR Core Binder calls. */
final class AicrCoreAccessHooks {
    private static final String TAG = "DragShare/AicrCoreAccess";
    private static final String MMGG_PACKAGE = "com.leaf.hyperdragshare.codex";
    private static final String CORE_TRANSACTION_CLASS = "com.xiaomi.aicr.access.AiCrCoreService$";
    private static final int SYSTEM_UID = 1000;
    private static final int PER_USER_RANGE = 100_000;
    private static final int FIRST_APPLICATION_APP_ID = 10_000;
    private static final int LAST_APPLICATION_APP_ID = 19_999;
    private static final int SIGNATURE_MATCH = 0;
    private static final AtomicBoolean LOGGED_MMGG_OVERRIDE = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_GLOBAL_OVERRIDE = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_SETTING_READ_FAILURE = new AtomicBoolean();
    private static volatile Context aicrContext;

    private AicrCoreAccessHooks() {}

    static void install() {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            aicrContext = (Context) param.thisObject;
                        }
                    });
            XposedBridge.log(TAG + ": captured AICR application context for settings RPC");
        } catch (XposedHelpers.ClassNotFoundError | RuntimeException | LinkageError error) {
            XposedBridge.log(TAG + ": settings context hook unavailable ("
                    + error.getClass().getSimpleName() + "); global access remains disabled");
        }
        try {
            // Hook the Android framework boundary used by AICR's caller-signature gate rather
            // than an obfuscated private AICR helper. The runtime predicates below keep it narrow.
            XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", null,
                    "checkSignatures", int.class, int.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            if (!(param.thisObject instanceof PackageManager packageManager)) return;
                            int callingUid = Binder.getCallingUid();
                            String[] callers = packagesForBinderUid(packageManager, callingUid);
                            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                            int requestedUid = (Integer) param.args[0];
                            int comparedUid = (Integer) param.args[1];
                            if (!isAicrCoreSignatureComparison(
                                    requestedUid, comparedUid, callingUid, callers, stack)) return;
                            boolean globallyAllowed = callerPackagesContainMmgg(callers)
                                    || isAicrGlobalSignatureAccessEnabled(callers);
                            if (!shouldAllowSystemSignatureMatch(requestedUid, comparedUid,
                                    callingUid, callers, stack, globallyAllowed)) return;

                            // Treat only the app UID/system UID comparison in AICR Core as matching.
                            // The user toggle does not bypass other AICR permission or plugin checks.
                            param.setResult(SIGNATURE_MATCH);
                            boolean mmggCaller = callerPackagesContainMmgg(callers);
                            AtomicBoolean logged = mmggCaller
                                    ? LOGGED_MMGG_OVERRIDE : LOGGED_GLOBAL_OVERRIDE;
                            if (logged.compareAndSet(false, true)) {
                                XposedBridge.log(TAG + (mmggCaller
                                        ? ": MMGG Core signature compatibility enabled"
                                        : ": global app Core signature compatibility enabled"));
                            }
                        }
                    });
            XposedBridge.log(TAG + ": installed on framework signature check; runtime scope checks enabled");
        } catch (XposedHelpers.ClassNotFoundError | RuntimeException | LinkageError error) {
            XposedBridge.log(TAG + ": unavailable; native signature check retained ("
                    + error.getClass().getSimpleName() + ")");
        }
    }

    static boolean shouldAllowSystemSignatureMatch(int requestedUid, int comparedUid, int callingUid,
            String[] callerPackages, StackTraceElement[] stack) {
        return shouldAllowSystemSignatureMatch(
                requestedUid, comparedUid, callingUid, callerPackages, stack, false);
    }

    static boolean shouldAllowSystemSignatureMatch(int requestedUid, int comparedUid, int callingUid,
            String[] callerPackages, StackTraceElement[] stack, boolean globalAccessEnabled) {
        if (!isAicrCoreSignatureComparison(
                requestedUid, comparedUid, callingUid, callerPackages, stack)) return false;
        boolean mmggCaller = callerPackagesContainMmgg(callerPackages);
        if (!mmggCaller && !globalAccessEnabled) return false;
        return true;
    }

    static boolean isAicrCoreSignatureComparison(int requestedUid, int comparedUid, int callingUid,
            String[] callerPackages, StackTraceElement[] stack) {
        if (callingUid <= 0 || requestedUid != callingUid || comparedUid != SYSTEM_UID
                || !isApplicationUid(callingUid) || callerPackages == null || callerPackages.length == 0
                || stack == null) return false;
        for (StackTraceElement frame : stack) {
            if (frame != null && frame.getClassName().startsWith(CORE_TRANSACTION_CLASS)
                    && "onTransact".equals(frame.getMethodName())) return true;
        }
        return false;
    }

    private static boolean callerPackagesContainMmgg(String[] callerPackages) {
        return callerPackages != null && Arrays.asList(callerPackages).contains(MMGG_PACKAGE);
    }

    private static boolean isApplicationUid(int uid) {
        int appId = uid % PER_USER_RANGE;
        return appId >= FIRST_APPLICATION_APP_ID && appId <= LAST_APPLICATION_APP_ID;
    }

    private static boolean isAicrGlobalSignatureAccessEnabled(String[] callerPackages) {
        if (callerPackagesContainMmgg(callerPackages) || callerPackages == null
                || callerPackages.length == 0) return false;
        Context context = aicrContext;
        if (context == null) return false;
        try {
            android.os.Bundle settings = context.getContentResolver().call(
                    ImageStagingClient.BASE_URI,
                    IntegrationSettings.METHOD_GET_AICR_SETTINGS,
                    null,
                    null);
            return settings != null && settings.getBoolean(
                    IntegrationSettings.EXTRA_AICR_GLOBAL_SIGNATURE_ACCESS_ENABLED, false);
        } catch (RuntimeException error) {
            if (LOGGED_SETTING_READ_FAILURE.compareAndSet(false, true)) {
                XposedBridge.log(TAG + ": cannot read global access setting ("
                        + error.getClass().getSimpleName() + "); keeping native signature result");
            }
            return false;
        }
    }

    private static String[] packagesForBinderUid(PackageManager packageManager, int uid) {
        if (uid <= 0) return null;
        try {
            return packageManager.getPackagesForUid(uid);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}

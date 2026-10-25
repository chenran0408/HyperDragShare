package com.leaf.hyperdragshare.codex;

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
    private static final int SIGNATURE_MATCH = 0;
    private static final AtomicBoolean LOGGED_OVERRIDE = new AtomicBoolean();

    private AicrCoreAccessHooks() {}

    static void install() {
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
                            if (!shouldAllowSystemSignatureMatch((Integer) param.args[0],
                                    (Integer) param.args[1], callingUid, callers,
                                    Thread.currentThread().getStackTrace())) return;

                            // AICR's gate compares the calling MMGG UID with system UID.
                            // Treat only that comparison as matching; preserve other checks.
                            param.setResult(SIGNATURE_MATCH);
                            if (LOGGED_OVERRIDE.compareAndSet(false, true)) {
                                XposedBridge.log(TAG + ": MMGG Core signature check compatibility enabled");
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
        if (callingUid <= 0 || requestedUid != callingUid || comparedUid != SYSTEM_UID
                || callerPackages == null || stack == null
                || !Arrays.asList(callerPackages).contains(MMGG_PACKAGE)) return false;
        for (StackTraceElement frame : stack) {
            if (frame != null && frame.getClassName().startsWith(CORE_TRANSACTION_CLASS)
                    && "onTransact".equals(frame.getMethodName())) return true;
        }
        return false;
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

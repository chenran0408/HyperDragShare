package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.os.Binder;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Version-independent compatibility for MMGG's AICR Core Binder calls. */
final class AicrCoreAccessHooks {
    private static final String TAG = "DragShare/AicrCoreAccess";
    private static final String AICR_PACKAGE = "com.xiaomi.aicr";
    private static final String MMGG_PACKAGE = "com.leaf.hyperdragshare.codex";
    private static final String CORE_TRANSACTION_CLASS = "com.xiaomi.aicr.access.AiCrCoreService$";
    private static final AtomicBoolean LOGGED_OVERRIDE = new AtomicBoolean();

    private AicrCoreAccessHooks() {}

    static void install(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod("defpackage.u8", classLoader, "a", Context.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            if (!(param.args[0] instanceof Context context)) return;
                            String[] callers = packagesForBinderUid(context, Binder.getCallingUid());
                            if (!shouldAllowCoreCall(context.getPackageName(), callers,
                                    Thread.currentThread().getStackTrace())) return;

                            // Some AICR builds deny the Core Binder descriptor query and plugin
                            // transactions before their stable AIDL implementation is reached.
                            // Keep the exception scoped to MMGG callers inside Core onTransact.
                            param.setResult(true);
                            if (LOGGED_OVERRIDE.compareAndSet(false, true)) {
                                XposedBridge.log(TAG + ": MMGG Core-service access compatibility enabled");
                            }
                        }
                    });
            XposedBridge.log(TAG + ": installed; runtime caller and Core transaction checks enabled");
        } catch (RuntimeException | LinkageError error) {
            XposedBridge.log(TAG + ": unavailable; native AICR access check retained ("
                    + error.getClass().getSimpleName() + ")");
        }
    }

    static boolean shouldAllowCoreCall(String aicrPackage,
            String[] callerPackages, StackTraceElement[] stack) {
        if (!AICR_PACKAGE.equals(aicrPackage) || callerPackages == null || stack == null
                || !Arrays.asList(callerPackages).contains(MMGG_PACKAGE)) return false;
        for (StackTraceElement frame : stack) {
            if (frame != null && frame.getClassName().startsWith(CORE_TRANSACTION_CLASS)
                    && "onTransact".equals(frame.getMethodName())) return true;
        }
        return false;
    }

    private static String[] packagesForBinderUid(Context context, int uid) {
        if (uid <= 0) return null;
        try {
            return context.getPackageManager().getPackagesForUid(uid);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}

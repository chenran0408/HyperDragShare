package com.leaf.hyperdragshare.codex;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Disables MiTrustService's optional MRM lookup without depending on an obfuscated class. */
final class TrustServiceRiskHooks {
    private static final String TAG = "DragShare/TrustService";
    private static final String MRM_SERVICE = "vendor.xiaomi.hardware.mrm.IMrm/default";
    private static final Uri SETTINGS_URI =
            Uri.parse("content://com.leaf.hyperdragshare.codex.share/settings");
    private static volatile boolean hookInstalled;

    private TrustServiceRiskHooks() {}

    static void install(XC_LoadPackage.LoadPackageParam loadPackage) {
        if (!MainHook.TRUST_SERVICE_PACKAGE.equals(loadPackage.processName)) {
            log("risk-detection hook skipped process=" + loadPackage.processName);
            return;
        }
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            installForContext((Context) param.args[0], loadPackage.classLoader);
                        }
                    });
        } catch (RuntimeException | LinkageError error) {
            log("risk-detection application hook unavailable; native behavior retained: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private static void installForContext(Context context, ClassLoader classLoader) {
        if (hookInstalled || context == null
                || !MainHook.TRUST_SERVICE_PACKAGE.equals(context.getPackageName())) {
            return;
        }
        try {
            Bundle state = context.getContentResolver().call(SETTINGS_URI,
                    IntegrationSettings.METHOD_GET_TRUST_SERVICE_SETTINGS, null, null);
            if (state == null) {
                log("risk-detection setting unavailable; native behavior retained");
                return;
            }
            if (!state.getBoolean(
                    IntegrationSettings.EXTRA_TRUST_SERVICE_RISK_DISABLED, false)) {
                log("risk-detection hook disabled");
                return;
            }
            XposedHelpers.findAndHookMethod("android.os.ServiceManager", classLoader,
                    "waitForService", String.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (MRM_SERVICE.equals(param.args[0])) {
                                param.setResult(null);
                                log("MRM service hidden from MiTrustService");
                            }
                        }
                    });
            hookInstalled = true;
            log("risk-detection hook installed; MRM service lookup is disabled");
        } catch (RuntimeException | LinkageError error) {
            log("risk-detection hook unavailable; native behavior retained: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}

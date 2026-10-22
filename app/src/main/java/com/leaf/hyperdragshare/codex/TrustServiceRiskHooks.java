package com.leaf.hyperdragshare.codex;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Bypasses MiTrustService's MRM initializer without depending on an obfuscated class name. */
final class TrustServiceRiskHooks {
    private static final String TAG = "DragShare/TrustService";
    private static final String STATUS_EVENT_TAG = "MiTrustService/statusEventHandle";
    private static final String MRM_INIT_MESSAGE = "try init mrmd Service";
    private static final String MRM_SERVICE = "vendor.xiaomi.hardware.mrm.IMrm/default";
    private static final Uri SETTINGS_URI =
            Uri.parse("content://com.leaf.hyperdragshare.codex.share/settings");
    private static volatile boolean hookInstalled;
    private static volatile boolean bypassLogged;

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
            Method initializer = findMrmInitializer(context, classLoader);
            if (initializer == null) {
                log("MRM initializer variants were not found; native behavior retained");
                return;
            }
            XposedBridge.hookMethod(initializer, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!bypassLogged) {
                        bypassLogged = true;
                        log("MRM risk gate bypassed");
                    }
                    param.setResult(false);
                }
            });
            hookInstalled = true;
            log("risk-detection hook installed; bypassed "
                    + initializer.getDeclaringClass().getName() + "#" + initializer.getName());
        } catch (PackageManager.NameNotFoundException | NoSuchMethodException
                 | RuntimeException | LinkageError error) {
            log("risk-detection hook unavailable; native behavior retained: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    private static Method findMrmInitializer(Context context, ClassLoader classLoader)
            throws PackageManager.NameNotFoundException, NoSuchMethodException {
        ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(
                MainHook.TRUST_SERVICE_PACKAGE, 0);
        System.loadLibrary("dexkit");
        try (DexKitBridge bridge = DexKitBridge.create(appInfo.sourceDir)) {
            MethodData match = findUnique(bridge, STATUS_EVENT_TAG, MRM_SERVICE);
            if (match == null) {
                match = findUnique(bridge, STATUS_EVENT_TAG, MRM_INIT_MESSAGE);
            }
            return match == null ? null : match.getMethodInstance(classLoader);
        }
    }

    private static MethodData findUnique(DexKitBridge bridge, String firstString,
            String secondString) {
        MethodDataList matches = bridge.findMethod(FindMethod.create().matcher(
                MethodMatcher.create()
                        .paramCount(0)
                        .returnType(boolean.class)
                        .usingEqStrings(firstString, secondString)));
        if (matches.size() != 1) {
            log("MRM initializer matcher count=" + matches.size()
                    + " markers=" + firstString + ", " + secondString);
            return null;
        }
        return matches.get(0);
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}

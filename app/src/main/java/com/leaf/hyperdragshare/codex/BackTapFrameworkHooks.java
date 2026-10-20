package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.provider.Settings;

import java.lang.reflect.Member;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Narrow HyperOS system-process hook for the user's optional back-tap shortcut. */
final class BackTapFrameworkHooks {
    private static final String TAG = "DragShare/BackTap";
    private static final String SERVICE_CLASS = "com.miui.server.input.MiuiBackTapGestureService";
    private static final String SENSOR_LISTENER_CLASS = SERVICE_CLASS + "$1";
    private static final String GLOBAL_MODE_KEY = "dragshare_back_tap_mode";
    private static final String MODULE_PACKAGE = "com.leaf.hyperdragshare.codex";
    private static final String SCREEN_OCR_ACTIVITY =
            "com.leaf.hyperdragshare.codex.ScreenOcrActivity";
    private static final long MODE_CACHE_MILLIS = 500L;
    private static final long DUPLICATE_EVENT_WINDOW_MILLIS = 350L;

    private static volatile long modeReadAt;
    private static volatile int cachedMode = BackTapMode.OFF;
    private static volatile long lastHandledAt;
    private static volatile boolean lastHandledWasDoubleTap;

    private BackTapFrameworkHooks() {}

    static void install(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        Class<?> serviceClass = XposedHelpers.findClassIfExists(
                SERVICE_CLASS,
                loadPackageParam.classLoader);
        if (serviceClass == null) {
            log("target service class is absent on this ROM");
            return;
        }

        try {
            XposedBridge.hookAllMethods(serviceClass, "postShortcutFunction", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object[] args = param.args;
                    if (args == null || args.length < 3 || !(args[2] instanceof String)) {
                        return;
                    }
                    String shortcut = (String) args[2];
                    boolean doubleTap = "back_double_tap".equals(shortcut);
                    if (!doubleTap && !"back_triple_tap".equals(shortcut)) {
                        return;
                    }
                    if (intercept(doubleTap)) {
                        param.setResult(resultFor(param.method));
                    }
                }
            });
        } catch (Throwable error) {
            log("shortcut dispatch hook unavailable: " + error.getClass().getSimpleName());
        }

        hookSensorCallbacks(loadPackageParam.classLoader);
        log("back-tap hooks installed for " + SERVICE_CLASS + " (mode defaults off)");
    }

    private static void hookSensorCallbacks(ClassLoader classLoader) {
        Class<?> listenerClass = XposedHelpers.findClassIfExists(SENSOR_LISTENER_CLASS, classLoader);
        if (listenerClass == null) {
            log("sensor callback class is absent; shortcut dispatch hook remains active");
            return;
        }
        hookSensorMethod(listenerClass, "onBackDoubleTap", true);
        hookSensorMethod(listenerClass, "onBackTripleTap", false);
    }

    private static void hookSensorMethod(Class<?> listenerClass, String methodName, boolean doubleTap) {
        try {
            XposedBridge.hookAllMethods(listenerClass, methodName, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (intercept(doubleTap)) {
                        param.setResult(resultFor(param.method));
                    }
                }
            });
        } catch (Throwable error) {
            log(methodName + " hook unavailable: " + error.getClass().getSimpleName());
        }
    }

    private static boolean intercept(boolean doubleTap) {
        int mode = readMode();
        if (!BackTapMode.handles(mode, doubleTap)) {
            return false;
        }

        long now = SystemClock.uptimeMillis();
        if (lastHandledAt > 0L
                && lastHandledWasDoubleTap == doubleTap
                && now - lastHandledAt < DUPLICATE_EVENT_WINDOW_MILLIS) {
            return true;
        }

        Context context = systemContext();
        if (context == null) {
            log("system context unavailable; allowing the ROM shortcut");
            return false;
        }
        try {
            Intent intent = new Intent()
                    .setClassName(MODULE_PACKAGE, SCREEN_OCR_ACTIVITY)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
            lastHandledAt = now;
            lastHandledWasDoubleTap = doubleTap;
            log("intercepted " + (doubleTap ? "double" : "triple") + " back tap");
            return true;
        } catch (Throwable error) {
            log("screen OCR launch failed; allowing the ROM shortcut: "
                    + error.getClass().getSimpleName());
            return false;
        }
    }

    private static int readMode() {
        long now = SystemClock.uptimeMillis();
        if (modeReadAt > 0L && now - modeReadAt < MODE_CACHE_MILLIS) {
            return cachedMode;
        }
        Context context = systemContext();
        if (context == null) {
            return BackTapMode.OFF;
        }
        try {
            String value = Settings.Global.getString(
                    context.getContentResolver(),
                    GLOBAL_MODE_KEY);
            cachedMode = BackTapMode.normalize(value == null
                    ? BackTapMode.OFF
                    : Integer.parseInt(value.trim()));
            modeReadAt = now;
        } catch (Throwable ignored) {
            cachedMode = BackTapMode.OFF;
            modeReadAt = now;
        }
        return cachedMode;
    }

    private static Context systemContext() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThread = activityThreadClass.getDeclaredMethod(
                    "currentActivityThread");
            currentActivityThread.setAccessible(true);
            Object activityThread = currentActivityThread.invoke(null);
            if (activityThread == null) {
                return null;
            }
            Method getSystemContext = activityThreadClass.getDeclaredMethod("getSystemContext");
            getSystemContext.setAccessible(true);
            return (Context) getSystemContext.invoke(activityThread);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object resultFor(Member member) {
        if (member instanceof Method) {
            Class<?> returnType = ((Method) member).getReturnType();
            if (returnType == boolean.class || returnType == Boolean.class) {
                return Boolean.TRUE;
            }
            if (returnType == byte.class) return (byte) 0;
            if (returnType == short.class) return (short) 0;
            if (returnType == int.class) return 0;
            if (returnType == long.class) return 0L;
            if (returnType == float.class) return 0f;
            if (returnType == double.class) return 0d;
            if (returnType == char.class) return '\0';
        }
        return null;
    }

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + ": " + message);
        } catch (Throwable ignored) {
            // Logging must never alter system input handling.
        }
    }
}

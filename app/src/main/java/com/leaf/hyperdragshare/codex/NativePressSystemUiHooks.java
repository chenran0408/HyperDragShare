package com.leaf.hyperdragshare.codex;

import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.InputEvent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Passive diagnostics for the original MotionEvents seen by HyperOS's multi-window handler. */
final class NativePressSystemUiHooks {
    private static final String TAG = "DragShare/NativePress";
    private static final String EVENT_RECEIVER_CLASS =
            "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.MulWinSwitchEventController$EventReceiver";

    private static boolean installed;
    private static boolean tracking;
    private static int sampledEvents;
    private static float maxSize;
    private static float maxTouchMajor;
    private static float maxTouchMinor;
    private static float maxToolMajor;
    private static long downAt;
    private static int reports;

    private NativePressSystemUiHooks() {}

    static synchronized void install(ClassLoader classLoader) {
        if (installed) return;

        try {
            Class<?> receiver = Class.forName(EVENT_RECEIVER_CLASS, false, classLoader);
            XposedBridge.hookMethod(receiver.getDeclaredMethod("onInputEvent", InputEvent.class), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!(param.args[0] instanceof MotionEvent)) return;
                    sample((MotionEvent) param.args[0]);
                }
            });
            installed = true;
            log("passive pre-filter MotionEvent sampling installed build="
                    + BuildConfig.VERSION_NAME + "/" + BuildConfig.VERSION_CODE);
        } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException | LinkageError error) {
            log("passive sampling hook unavailable: " + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
        }
    }

    private static synchronized void sample(MotionEvent event) {
        if (reports >= 32 || !event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) return;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            tracking = true;
            sampledEvents = 0;
            maxSize = 0f;
            maxTouchMajor = 0f;
            maxTouchMinor = 0f;
            maxToolMajor = 0f;
            downAt = event.getEventTime();
        }
        if (!tracking) return;

        sampledEvents++;
        for (int pointer = 0; pointer < event.getPointerCount(); pointer++) {
            maxSize = Math.max(maxSize, event.getSize(pointer));
            maxTouchMajor = Math.max(maxTouchMajor, event.getTouchMajor(pointer));
            maxTouchMinor = Math.max(maxTouchMinor, event.getTouchMinor(pointer));
            maxToolMajor = Math.max(maxToolMajor, event.getToolMajor(pointer));
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            long duration = event.getEventTime() - downAt;
            if (duration >= 200L) {
                reports++;
                log("sample=" + reports + " action=" + action + " events=" + sampledEvents
                        + " durationMs=" + duration + " maxSize=" + maxSize + " maxTouchMajor=" + maxTouchMajor
                        + " maxTouchMinor=" + maxTouchMinor + " maxToolMajor=" + maxToolMajor);
            }
            tracking = false;
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}

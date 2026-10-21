package com.leaf.hyperdragshare.codex;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class PortalHooks {
    private static final String TAG = "DragShare/Taplus";
    private static final String SERVICE_CLASS =
            "com.miui.contentextension.services.TextContentExtensionService";
    private static final String CALLBACK_CLASS = SERVICE_CLASS + "$1";
    private static final String BASE_FLOAT_CLASS =
            "com.miui.contentextension.text.floatview.BaseFloatView";
    private static final String MOTION_KEY = "MotionEvent";
    private static final String CONTROL_KEY = "observe_control_event";
    private static final Object DEFERRED_HOST_LOCK = new Object();
    private static final ArrayDeque<DeferredHostCall> DEFERRED_HOST_CALLS = new ArrayDeque<>();
    private static final ThreadLocal<Boolean> REPLAYING_HOST_CALL = new ThreadLocal<>();

    private static final Set<Class<?>> HOOKED_CALLBACK_CLASSES = Collections.newSetFromMap(
            new ConcurrentHashMap<>());

    @SuppressLint("StaticFieldLeak")
    private static volatile DragShareController controller;
    private static volatile MiuiMotionSource motionSource;
    private static volatile RootTouchSource rootTouchSource;
    private static volatile PortalThumbCaptureSource thumbCaptureSource;
    private static float thumbThreshold;
    private static volatile boolean rootAuthorityLogged;
    private static volatile boolean hostCancelIgnoredLogged;
    private static volatile boolean controlIgnoredLogged;
    private static volatile boolean activationReported;
    private static volatile Context portalContext;
    private static volatile ClassLoader portalClassLoader;
    private static volatile ContentObserver settingsObserver;
    private static volatile String xposedBridgeVersion = "unavailable";

    private PortalHooks() {}

    static void install(ClassLoader classLoader) {
        try {
            portalClassLoader = classLoader;
            xposedBridgeVersion = readXposedBridgeVersion();
            Class<?> serviceClass = XposedHelpers.findClass(SERVICE_CLASS, classLoader);
            suppressOriginalFloatWindows(classLoader);
            hookServiceLifecycle(serviceClass, classLoader);
            hookShareStart(serviceClass);
            hookKnownCallbackClass(classLoader);
            hookClipboardCard(serviceClass, classLoader);
            log("portal hooks installed");
        } catch (Throwable error) {
            log("unable to install Taplus hooks", error);
        }
    }

    private static void hookClipboardCard(Class<?> serviceClass, ClassLoader classLoader) {
        String listener = "com.miui.contentextension.text.floatview.TaplusSplashFloatView$13";
        try {
            XposedHelpers.findAndHookMethod(listener, classLoader, "onClick", android.view.View.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Context context = ((android.view.View) param.args[0]).getContext();
                            Object owner;
                            try {
                                android.os.Bundle settings = context.getContentResolver().call(
                                        ImageStagingClient.BASE_URI, DragShareSettings.METHOD_GET_SETTINGS,
                                        null, null);
                                if (settings == null || !settings.getBoolean(
                                        IntegrationSettings.EXTRA_COPY_DIRECT_ENABLED, false)) return;
                                String text = AicrClipboardText.read(context.getSystemService(
                                        android.content.ClipboardManager.class).getPrimaryClip());
                                if (text == null || !text.equals(
                                        PortalContentCaptureSource.clipboardCardText(serviceClass))) return;
                                owner = PortalContentCaptureSource.clipboardCardOwner(param.thisObject);
                                android.os.Bundle input = new android.os.Bundle();
                                input.putString("text", text);
                                android.os.Bundle result = context.getContentResolver().call(
                                        ImageStagingClient.BASE_URI, BigBangOverlayLauncher.METHOD_OPEN,
                                        null, input);
                                if (result == null || !result.getBoolean("accepted")) {
                                    throw new IllegalStateException("分词窗口未接受请求");
                                }
                            } catch (RuntimeException | LinkageError | XposedHelpers.InvocationTargetError error) {
                                log("clipboard card handoff failed; keeping the host editor", error);
                                android.widget.Toast.makeText(context, "无法打开分词窗口，保留原编辑操作："
                                        + error.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                                return;
                            }
                            param.setResult(null);
                            log("opened original-page text window from portal clipboard card");
                            try {
                                PortalContentCaptureSource.dismissClipboardCard(owner);
                            } catch (RuntimeException | LinkageError | XposedHelpers.InvocationTargetError error) {
                                log("text window opened but the clipboard card could not close", error);
                                android.widget.Toast.makeText(context, "分词窗口已打开，但复制卡片未能收起",
                                        android.widget.Toast.LENGTH_LONG).show();
                            }
                        }
                    });
            log("portal clipboard card hook installed");
        } catch (RuntimeException | LinkageError error) {
            log("clipboard card hook unavailable; keeping the host editor", error);
        }
    }

    private static void suppressOriginalFloatWindows(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(
                BASE_FLOAT_CLASS,
                classLoader,
                "addToWindow",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        DragShareController current = controller;
                        if (current != null && current.shouldSuppressPortalHostFloatWindow()) {
                            param.setResult(null);
                        }
                    }
                });
    }

    private static void hookServiceLifecycle(Class<?> serviceClass, ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(serviceClass, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Context context = ((Context) param.thisObject).getApplicationContext();
                portalContext = context;
                portalClassLoader = classLoader;
                DragShareLog.configure(DragShareSettings.readFromProvider(context));
                DragShareDiagnostics.captureRuntimeOnce(
                        context,
                        "portal service created",
                        xposedBridgeVersion);
                activationReported = reportPortalActivation(context);
                registerSettingsObserver(context);
                applyPortalRuntime();
                hookCallbackFromService(param.thisObject);
            }
        });

        XposedHelpers.findAndHookMethod(serviceClass, "onDestroy", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                unregisterSettingsObserver();
                stopPortalRuntime(false);
                portalContext = null;
            }
        });

        XposedHelpers.findAndHookMethod(
                serviceClass,
                "onStartCommand",
                Intent.class,
                int.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Context context = ((Context) param.thisObject).getApplicationContext();
                        activationReported = reportPortalActivation(context)
                                || activationReported;
                    }
                });

        XposedHelpers.findAndHookMethod(serviceClass, "cancelTask", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (isReplayingHostCall()) {
                    return;
                }
                DragShareController current = controller;
                if (current != null) {
                    if (deferHostCallIfRootDragActive(param, "cancelTask")) {
                        // Let the root ACTION_UP replay the original method after the
                        // physical gesture has ended. Running it now poisons Taplus'
                        // task state and prevents the next long press in the same app.
                        param.setResult(null);
                        if (!hostCancelIgnoredLogged) {
                            hostCancelIgnoredLogged = true;
                            log("ignoring host cancel while root drag is active");
                        }
                    } else {
                        current.onHostTaskCancelled();
                    }
                }
            }
        });
    }

    private static void hookShareStart(Class<?> serviceClass) {
        XC_MethodHook showHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!activationReported) {
                    Context context = ((Context) param.thisObject).getApplicationContext();
                    activationReported = reportPortalActivation(context);
                }
                Context context = ((Context) param.thisObject).getApplicationContext();
                DragShareSettings settings = DragShareSettings.readFromProvider(context);
                if (!settings.isPortalCaptureMode()) {
                    return;
                }
                applyPortalRuntime();
                DragShareController current = controller;
                if (current != null) {
                    CapturedContent content = PortalContentCaptureSource.capture(param.thisObject, settings);
                    if (content == null) {
                        return;
                    }
                    PointF point = PortalContentCaptureSource.initialPoint(
                            param.thisObject,
                            current.latestPointerX(),
                            current.latestPointerY());
                    current.reservePortalHostFloatWindowSuppression();
                    current.show(
                            content,
                            point == null ? -1f : point.x,
                            point == null ? -1f : point.y,
                            settings);
                }
            }
        };
        XposedHelpers.findAndHookMethod(serviceClass, "startPickTextTask", showHook);
        XposedHelpers.findAndHookMethod(serviceClass, "startPickImageTask", showHook);
    }

    private static void hookKnownCallbackClass(ClassLoader classLoader) {
        Class<?> callbackClass = XposedHelpers.findClassIfExists(CALLBACK_CLASS, classLoader);
        if (callbackClass != null) {
            hookCallbackClass(callbackClass);
        }
    }

    private static void hookCallbackFromService(Object service) {
        try {
            Object callback = XposedHelpers.getObjectField(service, "mCallback");
            if (callback != null) {
                hookCallbackClass(callback.getClass());
            }
        } catch (Throwable error) {
            log("unable to discover Taplus callback", error);
        }
    }

    private static void hookCallbackClass(Class<?> callbackClass) {
        if (!HOOKED_CALLBACK_CLASSES.add(callbackClass)) {
            return;
        }

        try {
            Method target = null;
            for (Method method : callbackClass.getDeclaredMethods()) {
                if ("onContentReceived".equals(method.getName())
                        && method.getParameterTypes().length == 1) {
                    target = method;
                    break;
                }
            }
            if (target == null) {
                throw new NoSuchMethodException(callbackClass.getName() + ".onContentReceived");
            }
            target.setAccessible(true);
            XposedBridge.hookMethod(target, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    handleCallback(param);
                }
            });
            log("hooked callback " + callbackClass.getName());
        } catch (Throwable error) {
            HOOKED_CALLBACK_CLASSES.remove(callbackClass);
            log("unable to hook Taplus callback", error);
        }
    }

    @SuppressWarnings("unchecked")
    private static void handleCallback(XC_MethodHook.MethodHookParam param) {
        try {
            if (isReplayingHostCall()) {
                return;
            }
            DragShareController current = controller;
            // The callback hook remains installed after switching to accessibility
            // mode. Let the host receive its own events when our runtime is stopped.
            if (current == null) {
                return;
            }
            Map<String, Object> properties = (Map<String, Object>)
                    XposedHelpers.callMethod(param.args[0], "getPropertyMap");
            if (properties == null) {
                return;
            }

            Object motion = properties.get(MOTION_KEY);
            DragShareLog.d(TAG, "portal callback keys=" + properties.keySet()
                    + " motion=" + (motion instanceof MotionEvent)
                    + " control=" + String.valueOf(properties.get(CONTROL_KEY)));
            if (motion instanceof MotionEvent) {
                dispatchMotion(MotionEvent.obtain((MotionEvent) motion));
                // A motion-only result is not valid Taplus pick content.
                param.setResult(null);
                return;
            }

            Object control = properties.get(CONTROL_KEY);
            if ("258".equals(control)) {
                // Suppress a move cancellation only for our active drag. If the
                // host has no module session, it must reset its own task state.
                if (current.isActive()) {
                    param.setResult(null);
                }
            } else if ("257".equals(control)) {
                if (deferHostCallIfRootDragActive(param, "control-257")) {
                    if (!controlIgnoredLogged) {
                        controlIgnoredLogged = true;
                        log("ignoring host finish; waiting for root ACTION_UP");
                    }
                    param.setResult(null);
                } else {
                    if (current != null) {
                        current.finishFromControlEvent();
                    }
                }
            }
        } catch (Throwable error) {
            log("callback handling failed", error);
        }
    }

    private static void dispatchMotion(MotionEvent event) {
        DragShareLog.d(TAG, "MIUI motion action="
                + MotionEvent.actionToString(event.getActionMasked())
                + " point=" + Math.round(event.getRawX()) + "," + Math.round(event.getRawY()));
        if (hasReadyRootSource()) {
            event.recycle();
            if (!rootAuthorityLogged) {
                rootAuthorityLogged = true;
                log("root input is authoritative; ignoring MIUI motion events");
            }
            return;
        }
        DragShareController current = controller;
        if (current == null) {
            event.recycle();
            return;
        }
        int action = event.getActionMasked();
        current.acceptMotionEvent(
                event,
                action == MotionEvent.ACTION_UP ? PortalHooks::replayDeferredHostCalls : null);
    }

    private static boolean hasReadyRootSource() {
        RootTouchSource source = rootTouchSource;
        return source != null && source.isReady();
    }

    private static boolean reportPortalActivation(Context context) {
        boolean reported = ModuleActivation.reportInjected(context);
        if (reported) {
            ModuleActivation.probePortalRootAccessAsync(context);
        }
        return reported;
    }

    private static synchronized void applyPortalRuntime() {
        Context context = portalContext;
        ClassLoader classLoader = portalClassLoader;
        if (context == null || classLoader == null) {
            return;
        }
        DragShareSettings settings = DragShareSettings.readFromProvider(context);
        DragShareLog.configure(settings);
        DragShareLog.d(TAG, "portal runtime settings captureMode=" + settings.contentCaptureMode
                + " logLevel=" + settings.logLevel
                + " logDestination=" + settings.logDestination);
        if (!settings.isPortalCaptureMode()) {
            stopPortalRuntime(true);
            return;
        }
        if (controller != null) {
            configureThumbCapture(context, settings);
            return;
        }
        rootAuthorityLogged = false;
        hostCancelIgnoredLogged = false;
        controlIgnoredLogged = false;
        clearDeferredHostCalls();
        controller = new DragShareController(context, OverlayWindowPolicy.portal());
        motionSource = new MiuiMotionSource(classLoader, PortalHooks::dispatchMotion);
        motionSource.start();
        rootTouchSource = new RootTouchSource(context, PortalHooks::dispatchRootPointer);
        configureThumbCapture(context, settings);
        rootTouchSource.start();
        log("portal runtime started");
    }

    private static synchronized void stopPortalRuntime(boolean replayDeferredCalls) {
        PortalThumbCaptureSource thumb = thumbCaptureSource;
        thumbCaptureSource = null;
        if (thumb != null) thumb.stop();
        if (replayDeferredCalls) {
            replayDeferredHostCalls();
        }
        MiuiMotionSource source = motionSource;
        motionSource = null;
        if (source != null) {
            source.stop();
        }
        RootTouchSource rootSource = rootTouchSource;
        rootTouchSource = null;
        if (rootSource != null) {
            rootSource.stop();
        }
        DragShareController current = controller;
        controller = null;
        if (current != null) {
            current.destroy();
        }
        clearDeferredHostCalls();
    }

    private static void registerSettingsObserver(Context context) {
        unregisterSettingsObserver();
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                PortalThumbCaptureSource thumb = thumbCaptureSource;
                thumbCaptureSource = null;
                if (thumb != null) thumb.stop();
                applyPortalRuntime();
            }
        };
        try {
            context.getContentResolver().registerContentObserver(
                    DragShareSettings.settingsUri(),
                    false,
                    observer);
            settingsObserver = observer;
        } catch (Throwable error) {
            log("unable to observe settings changes", error);
        }
    }

    private static void unregisterSettingsObserver() {
        ContentObserver observer = settingsObserver;
        settingsObserver = null;
        Context context = portalContext;
        if (observer == null || context == null) {
            return;
        }
        try {
            context.getContentResolver().unregisterContentObserver(observer);
        } catch (Throwable ignored) {
            // The process may be leaving while the resolver is already gone.
        }
    }

    private static void dispatchRootPointer(
            int action, float x, float y, long eventTime, int rawTouchMajor, int rawTouchMinor, int rawWidthMajor) {
        PortalThumbCaptureSource thumb = thumbCaptureSource;
        if (thumb != null) thumb.onPointerEvent(action, x, y, eventTime, rawTouchMajor, rawWidthMajor);
        DragShareLog.d(TAG, "root pointer action=" + MotionEvent.actionToString(action)
                + " point=" + Math.round(x) + "," + Math.round(y)
                + " time=" + eventTime);
        DragShareController current = controller;
        if (current != null) {
            current.acceptPointerEvent(
                    action,
                    x,
                    y,
                    eventTime,
                    "root",
                    action == MotionEvent.ACTION_UP
                            ? PortalHooks::replayDeferredHostCalls
                            : null);
        }
    }

    private static void configureThumbCapture(Context context, DragShareSettings settings) {
        try {
            ThumbTriggerSettings thumb = ThumbTriggerSettings.readFromProvider(context);
            if (thumbCaptureSource != null && thumb.enabled && thumb.calibrated
                    && Float.compare(thumbThreshold, thumb.threshold) == 0) return;
            PortalThumbCaptureSource previous = thumbCaptureSource;
            thumbCaptureSource = null;
            if (previous != null) previous.stop();
            if (thumb.enabled && thumb.calibrated) {
                thumbThreshold = thumb.threshold;
                thumbCaptureSource = new PortalThumbCaptureSource(
                        context, rootTouchSource, controller, settings, thumb.threshold);
            }
        } catch (SecurityException | IllegalStateException error) {
            log("cannot load calibrated area trigger settings", error);
        }
    }

    private static boolean deferHostCallIfRootDragActive(
            XC_MethodHook.MethodHookParam param,
            String kind) {
        synchronized (DEFERRED_HOST_LOCK) {
            DragShareController current = controller;
            if (current == null || !current.isActive() || !hasReadyRootSource()) {
                return false;
            }
            for (DeferredHostCall existing : DEFERRED_HOST_CALLS) {
                if (kind.equals(existing.kind)) {
                    return true;
                }
            }
            Object[] args = param.args == null ? new Object[0] : param.args.clone();
            DEFERRED_HOST_CALLS.addLast(
                    new DeferredHostCall(kind, param.method, param.thisObject, args));
        }
        log("deferred host call=" + kind + " until root ACTION_UP");
        return true;
    }

    private static void replayDeferredHostCalls() {
        ArrayList<DeferredHostCall> calls;
        synchronized (DEFERRED_HOST_LOCK) {
            if (DEFERRED_HOST_CALLS.isEmpty()) {
                return;
            }
            calls = new ArrayList<>(DEFERRED_HOST_CALLS);
            DEFERRED_HOST_CALLS.clear();
        }

        REPLAYING_HOST_CALL.set(Boolean.TRUE);
        try {
            for (DeferredHostCall call : calls) {
                try {
                    XposedBridge.invokeOriginalMethod(call.method, call.receiver, call.args);
                    log("replayed host call=" + call.kind);
                } catch (Throwable error) {
                    log("unable to replay host call=" + call.kind, error);
                }
            }
        } finally {
            REPLAYING_HOST_CALL.remove();
        }
    }

    private static void clearDeferredHostCalls() {
        synchronized (DEFERRED_HOST_LOCK) {
            DEFERRED_HOST_CALLS.clear();
        }
    }

    private static boolean isReplayingHostCall() {
        return Boolean.TRUE.equals(REPLAYING_HOST_CALL.get());
    }

    private static String readXposedBridgeVersion() {
        try {
            Method method = XposedBridge.class.getMethod("getXposedVersion");
            Object value = method.invoke(null);
            return String.valueOf(value);
        } catch (Throwable error) {
            return "unavailable:" + error.getClass().getSimpleName();
        }
    }

    private static final class DeferredHostCall {
        final String kind;
        final Member method;
        final Object receiver;
        final Object[] args;

        DeferredHostCall(String kind, Member method, Object receiver, Object[] args) {
            this.kind = kind;
            this.method = method;
            this.receiver = receiver;
            this.args = args;
        }
    }

    private static void log(String message) {
        DragShareLog.i(TAG, message);
    }

    private static void log(String message, Throwable error) {
        DragShareLog.w(TAG, message, error);
    }
}

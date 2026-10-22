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
    private static volatile RootTouchSource rootTouchSource;
    private static volatile PressCaptureSource thumbCaptureSource;
    @SuppressLint("StaticFieldLeak")
    private static android.app.Service portalService;
    private static String appIdleHookError;
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
            hookApplicationStartup(classLoader);
            hookServiceLifecycle(serviceClass, classLoader);
            hookAppIdleExit(classLoader);
            hookShareStart(serviceClass);
            hookKnownCallbackClass(classLoader);
            hookClipboardCard(serviceClass, classLoader);
            log("portal hooks installed");
        } catch (Throwable error) {
            log("unable to install Taplus hooks", error);
        }
    }

    private static void hookApplicationStartup(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod("com.miui.contentextension.App", classLoader, "onCreate",
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        if (!MainHook.TAPLUS_PACKAGE.equals(android.app.Application.getProcessName())) return;
                        Context context = ((Context) param.thisObject).getApplicationContext();
                        try {
                            portalContext = context;
                            registerSettingsObserver(context);
                            applyPortalRuntime();
                            activationReported = reportPortalActivation(context);
                            log("application input initialized; foreground service startup reported separately");
                        } catch (RuntimeException | LinkageError error) {
                            stopPortalRuntime(false);
                            log("cannot initialize application press input; keeping host behavior", error);
                            android.widget.Toast.makeText(context, "按压监听启动失败：" + error.getMessage(),
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private static void hookAppIdleExit(ClassLoader classLoader) {
        String handlerClass = "com.miui.contentextension.setting.whitelist.utils.StopSelfHandler";
        try {
            XposedHelpers.findAndHookMethod(handlerClass, classLoader, "handleMessage", android.os.Message.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            android.os.Message message = (android.os.Message) param.args[0];
                            if (message.what == 1 && thumbCaptureSource != null) {
                                log("press input active; skipping settings idle exit");
                                param.setResult(null);
                            }
                        }
                    });
            log("settings idle exit hook installed");
        } catch (RuntimeException | LinkageError | XposedHelpers.ClassNotFoundError error) {
            appIdleHookError = "传送门空闲退出Hook不可用，按压监听可能随进程退出";
            log("cannot hook settings idle exit; keeping host behavior", error);
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
                                input.putBoolean("copy_direct_source", true);
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
                        boolean suppress = current != null && current.shouldSuppressPortalHostFloatWindow();
                        DragShareLog.i(TAG, "host float window addToWindow suppressed=" + suppress
                                + " view=" + param.thisObject.getClass().getName());
                        if (suppress) {
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
                portalService = (android.app.Service) param.thisObject;
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
                portalService = null;
                stopPortalRuntime(false);
                ModuleActivation.reportInjected((Context) param.thisObject, false);
                portalContext = null;
            }
        });

        XposedHelpers.findAndHookMethod(serviceClass, "scheduleStopSelfJob", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (thumbCaptureSource != null) {
                    param.setResult(null);
                    log("press input active; skipping host idle stop job");
                }
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
                applyPortalRuntime();
                DragShareController current = controller;
                if (current != null) {
                    PressCaptureSource press = thumbCaptureSource;
                    CapturedContent content = PortalContentCaptureSource.capture(param.thisObject, settings);
                    if (content != null && press != null && press.acceptWeChatPortalContent(content)) {
                        return;
                    }
                    if (current.isAwaitingRootRelease()) {
                        if (content != null && content.isImage()) content.bitmap.recycle();
                        log("portal pick ignored while Root press owns the gesture");
                        return;
                    }
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
            if (!(motion instanceof MotionEvent)
                    || ((MotionEvent) motion).getActionMasked() != MotionEvent.ACTION_MOVE) {
                DragShareLog.d(TAG, "portal callback keys=" + properties.keySet()
                        + " motion=" + (motion instanceof MotionEvent)
                        + " control=" + String.valueOf(properties.get(CONTROL_KEY)));
            }
            if (motion instanceof MotionEvent) {
                // A motion-only result is not valid Taplus pick content.
                param.setResult(null);
                return;
            }

            Object control = properties.get(CONTROL_KEY);
            if ("258".equals(control)) {
                // A confirmed press owns this same root gesture through release too.
                if (current.isAwaitingRootRelease()) {
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

    private static boolean reportPortalActivation(Context context) {
        boolean reported = ModuleActivation.reportInjected(context, portalService != null);
        if (reported) {
            ModuleActivation.probePortalRootAccessAsync(context);
        }
        return reported;
    }

    private static boolean hasReadyRootSource() {
        RootTouchSource source = rootTouchSource;
        return source != null && source.isReady();
    }

    private static synchronized void applyPortalRuntime() {
        Context context = portalContext;
        ClassLoader classLoader = portalClassLoader;
        if (context == null || classLoader == null) {
            return;
        }
        DragShareSettings settings = DragShareSettings.readFromProvider(context);
        DragShareLog.configure(settings);
        DragShareLog.d(TAG, "portal runtime settings logLevel=" + settings.logLevel
                + " logDestination=" + settings.logDestination);
        if ((portalService == null
                && !ThumbTriggerSettings.readFromProvider(context).enabled)) {
            stopPortalRuntime(true);
            return;
        }
        if (controller != null) {
            configureThumbCapture(context, settings);
            return;
        }
        hostCancelIgnoredLogged = false;
        controlIgnoredLogged = false;
        clearDeferredHostCalls();
        controller = new DragShareController(context, OverlayWindowPolicy.portal());
        rootTouchSource = new RootTouchSource(context, PortalHooks::dispatchRootPointer,
                (ready, reason) -> ModuleActivation.reportRootInput(context, ready, reason));
        configureThumbCapture(context, settings);
        rootTouchSource.start();
        log("portal runtime started");
    }

    private static synchronized void stopPortalRuntime(boolean replayDeferredCalls) {
        PressCaptureSource thumb = thumbCaptureSource;
        thumbCaptureSource = null;
        if (thumb != null) thumb.stop();
        updateHostIdleTimer(false);
        if (replayDeferredCalls) {
            replayDeferredHostCalls();
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
        if (action != MotionEvent.ACTION_MOVE) {
            DragShareLog.d(TAG, "root pointer action=" + MotionEvent.actionToString(action)
                    + " point=" + Math.round(x) + "," + Math.round(y)
                    + " time=" + eventTime);
        }
        DragShareController current = controller;
        if (current != null) {
            current.acceptPointerEvent(
                    action,
                    x,
                    y,
                    eventTime,
                    "root",
                    action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                            ? PortalHooks::replayDeferredHostCalls
                            : null);
        }
        PressCaptureSource thumb = thumbCaptureSource;
        if (thumb != null) thumb.onPointerEvent(action, x, y, eventTime);
    }

    private static void configureThumbCapture(Context context, DragShareSettings settings) {
        try {
            ThumbTriggerSettings thumb = ThumbTriggerSettings.readFromProvider(context);
            if (thumbCaptureSource != null && thumb.enabled) {
                thumbCaptureSource.updateSettings(settings);
                return;
            }
            PressCaptureSource previous = thumbCaptureSource;
            thumbCaptureSource = null;
            if (previous != null) previous.stop();
            if (thumb.enabled) {
                thumbCaptureSource = new PressCaptureSource(
                        context, rootTouchSource, controller, settings);
                log("reference press trigger enabled; no calibration required");
                if (appIdleHookError != null) {
                    new Handler(Looper.getMainLooper()).post(() -> android.widget.Toast.makeText(context,
                            appIdleHookError, android.widget.Toast.LENGTH_LONG).show());
                }
            }
            updateHostIdleTimer(thumb.enabled);
        } catch (SecurityException | IllegalStateException error) {
            log("cannot load press trigger settings", error);
        }
    }

    private static void updateHostIdleTimer(boolean pressEnabled) {
        android.app.Service service = portalService;
        if (service == null) return;
        try {
            XposedHelpers.callMethod(service, pressEnabled ? "cancelStopSelfJob" : "scheduleStopSelfJob");
        } catch (RuntimeException | LinkageError | XposedHelpers.InvocationTargetError error) {
            log("cannot update Portal idle job", error);
            new Handler(Looper.getMainLooper()).post(() -> android.widget.Toast.makeText(service,
                    "传送门空闲管理不兼容，按压入口可能随服务退出", android.widget.Toast.LENGTH_LONG).show());
        }
    }

    private static boolean deferHostCallIfRootDragActive(
            XC_MethodHook.MethodHookParam param,
            String kind) {
        synchronized (DEFERRED_HOST_LOCK) {
            DragShareController current = controller;
            if (current == null || !current.isAwaitingRootRelease() || !hasReadyRootSource()) {
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

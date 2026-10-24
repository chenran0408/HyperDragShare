package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class DragShareExitLifecycleTest {
    @Test
    public void destroyDisposesShownPrewarmWindowBeforeItsFirstDrawCallback() throws Exception {
        ActivityController<Activity> activityController = Robolectric.buildActivity(Activity.class).setup();
        Activity context = activityController.get();
        DragShareController controller = new DragShareController(
                context, OverlayWindowPolicy.portal());
        ModernPreviewOverlayView prewarmView = new ModernPreviewOverlayView(
                context,
                CapturedContent.text("预热", "test", null),
                DragShareSettings.defaults());
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                2,
                2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        ModernOverlayWindow prewarmWindow = new ModernOverlayWindow(
                context, windowManager, prewarmView, params, 0);
        AtomicInteger drawCount = new AtomicInteger();
        ViewTreeObserverListener drawListener = new ViewTreeObserverListener(drawCount);
        prewarmView.getViewTreeObserver().addOnDrawListener(drawListener);

        try {
            discardScheduledPrewarm(controller);
            set(controller, "modernOverlayPrewarmView", prewarmView);
            set(controller, "modernOverlayPrewarmWindow", prewarmWindow);
            set(controller, "modernOverlayPrewarmDrawListener", drawListener);

            prewarmWindow.show();
            ShadowLooper.shadowMainLooper().idle();
            assertTrue(prewarmWindow.isShowing());
            int drawsBeforeDestroy = drawCount.get();

            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();

            assertFalse("destroy left the prewarm Dialog showing", prewarmWindow.isShowing());
            assertNull(get(controller, "modernOverlayPrewarmView"));
            assertNull(get(controller, "modernOverlayPrewarmWindow"));
            assertNull(get(controller, "modernOverlayPrewarmDrawListener"));
            assertEquals(drawsBeforeDestroy, drawCount.get());
        } finally {
            prewarmWindow.dispose();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            activityController.pause().stop().destroy();
        }
    }

    @Test
    public void destroyMakesCapturedPrewarmDrawListenerInert() throws Exception {
        Field prewarmStartedField =
                DragShareController.class.getDeclaredField("modernOverlayPrewarmStarted");
        prewarmStartedField.setAccessible(true);
        boolean previousPrewarmStarted = prewarmStartedField.getBoolean(null);
        prewarmStartedField.setBoolean(null, false);
        ActivityController<Activity> activityController = null;
        DragShareController controller = null;
        try {
            activityController = Robolectric.buildActivity(Activity.class).setup();
            Activity context = activityController.get();
            controller = new DragShareController(context, OverlayWindowPolicy.portal());
            Runnable prewarmTask = (Runnable) get(controller, "modernOverlayPrewarmTask");
            assertTrue(prewarmTask != null);
            ((Handler) get(controller, "mainHandler")).removeCallbacks(prewarmTask);
            prewarmTask.run();
            ShadowLooper.shadowMainLooper().idle();

            ModernPreviewOverlayView prewarmView =
                    (ModernPreviewOverlayView) get(controller, "modernOverlayPrewarmView");
            ModernOverlayWindow prewarmWindow =
                    (ModernOverlayWindow) get(controller, "modernOverlayPrewarmWindow");
            ViewTreeObserver.OnDrawListener drawListener =
                    (ViewTreeObserver.OnDrawListener) get(
                            controller, "modernOverlayPrewarmDrawListener");
            assertTrue(prewarmView != null);
            assertTrue(prewarmWindow != null && prewarmWindow.isShowing());
            assertTrue(drawListener != null);
            Field finishPosted = drawListener.getClass().getDeclaredField("finishPosted");
            finishPosted.setAccessible(true);
            assertFalse(finishPosted.getBoolean(drawListener));

            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            assertFalse(prewarmWindow.isShowing());
            assertNull(get(controller, "modernOverlayPrewarmView"));
            assertNull(get(controller, "modernOverlayPrewarmWindow"));
            assertNull(get(controller, "modernOverlayPrewarmDrawListener"));

            drawListener.onDraw();
            assertFalse("a captured draw callback must stop before posting finish work",
                    finishPosted.getBoolean(drawListener));
            ShadowLooper.shadowMainLooper().idle();
            assertNull(get(controller, "modernOverlayPrewarmView"));
            assertNull(get(controller, "modernOverlayPrewarmWindow"));

            ModernPreviewOverlayView replacementView = new ModernPreviewOverlayView(
                    context,
                    CapturedContent.text("替换", "test", null),
                    DragShareSettings.defaults());
            WindowManager windowManager =
                    (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            ModernOverlayWindow replacementWindow = new ModernOverlayWindow(
                    context,
                    windowManager,
                    replacementView,
                    new WindowManager.LayoutParams(2, 2,
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                            PixelFormat.TRANSLUCENT),
                    0);
            ViewTreeObserver.OnDrawListener replacementListener =
                    new ViewTreeObserverListener(new AtomicInteger());
            set(controller, "modernOverlayPrewarmView", replacementView);
            set(controller, "modernOverlayPrewarmWindow", replacementWindow);
            set(controller, "modernOverlayPrewarmDrawListener", replacementListener);
            invoke(controller, "finishModernOverlayPrewarm",
                    new Class<?>[]{ModernPreviewOverlayView.class, ModernOverlayWindow.class,
                            ViewTreeObserver.OnDrawListener.class, long.class},
                    prewarmView, prewarmWindow, drawListener, 0L);
            assertSame(replacementView, get(controller, "modernOverlayPrewarmView"));
            assertSame(replacementWindow, get(controller, "modernOverlayPrewarmWindow"));
            assertSame(replacementListener,
                    get(controller, "modernOverlayPrewarmDrawListener"));
        } finally {
            try {
                if (controller != null) {
                    controller.destroy();
                    ShadowLooper.shadowMainLooper().idle();
                }
            } finally {
                prewarmStartedField.setBoolean(null, previousPrewarmStarted);
                if (activityController != null) {
                    activityController.pause().stop().destroy();
                }
            }
        }
    }

    @Test
    public void destroyImmediatelyFinishesPendingPreviewExitAndRejectsLateFinish() throws Exception {
        DragShareController controller = controller();
        View preview = new View(RuntimeEnvironment.getApplication());
        invoke(controller, "startPreviewExit",
                new Class<?>[]{View.class, ModernPreviewOverlayView.class, ModernOverlayWindow.class},
                preview, null, null);
        int oldGeneration = (int) get(controller, "previewExitGeneration");
        assertSame(preview, get(controller, "exitingPreviewView"));

        controller.destroy();
        ShadowLooper.shadowMainLooper().idle();
        invoke(controller, "finishPreviewExit",
                new Class<?>[]{int.class, View.class, ModernPreviewOverlayView.class,
                        ModernOverlayWindow.class},
                oldGeneration, preview, null, null);

        assertNull(get(controller, "exitingPreviewView"));
        assertNull(get(controller, "exitingModernPreviewView"));
        assertNull(get(controller, "exitingModernPreviewWindow"));
    }

    @Test
    public void stalePreviewFinishCannotRemoveReplacementAndRepeatedCleanupIsSafe() throws Exception {
        DragShareController controller = controller();
        View oldPreview = new View(RuntimeEnvironment.getApplication());
        View newPreview = new View(RuntimeEnvironment.getApplication());
        Class<?>[] exitParameters = {
                View.class, ModernPreviewOverlayView.class, ModernOverlayWindow.class};
        invoke(controller, "startPreviewExit", exitParameters, oldPreview, null, null);
        int oldGeneration = (int) get(controller, "previewExitGeneration");

        invoke(controller, "startPreviewExit", exitParameters, newPreview, null, null);
        int newGeneration = (int) get(controller, "previewExitGeneration");
        assertTrue(newGeneration > oldGeneration);
        assertSame(newPreview, get(controller, "exitingPreviewView"));

        invoke(controller, "finishPreviewExit",
                new Class<?>[]{int.class, View.class, ModernPreviewOverlayView.class,
                        ModernOverlayWindow.class},
                oldGeneration, oldPreview, null, null);
        assertSame(newPreview, get(controller, "exitingPreviewView"));

        invoke(controller, "removePendingPreviewExitImmediately", new Class<?>[0]);
        invoke(controller, "removePendingPreviewExitImmediately", new Class<?>[0]);
        assertNull(get(controller, "exitingPreviewView"));
        assertNull(get(controller, "exitingModernPreviewView"));
        assertNull(get(controller, "exitingModernPreviewWindow"));
        controller.destroy();
        ShadowLooper.shadowMainLooper().idle();
    }

    @Test
    public void repeatedDisposeRemovesBlurListenerOnceAndLateBlurCallbackDoesNotMutateView() {
        ActivityController<Activity> activityController = Robolectric.buildActivity(Activity.class).setup();
        Context context = activityController.get();
        TrackingGlassApi glassApi = new TrackingGlassApi();
        NativeGlassBackdrop backdrop = new NativeGlassBackdrop(
                context, glassApi, darkTheme -> new float[42]);
        ModernMenuOverlayView overlay = new ModernMenuOverlayView(
                context,
                Collections.emptyList(),
                new HashMap<>(),
                DragShareSettings.defaults(),
                false,
                backdrop);
        WindowManager actualWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        AtomicReference<Consumer<Boolean>> blurListener = new AtomicReference<>();
        AtomicInteger removedListeners = new AtomicInteger();
        WindowManager windowManager = (WindowManager) Proxy.newProxyInstance(
                WindowManager.class.getClassLoader(), new Class<?>[]{WindowManager.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isCrossWindowBlurEnabled": return true;
                        case "addCrossWindowBlurEnabledListener":
                            for (Object argument : args) {
                                if (argument instanceof Consumer) {
                                    @SuppressWarnings("unchecked")
                                    Consumer<Boolean> listener = (Consumer<Boolean>) argument;
                                    blurListener.set(listener);
                                    listener.accept(true);
                                }
                            }
                            return null;
                        case "removeCrossWindowBlurEnabledListener":
                            removedListeners.incrementAndGet();
                            return null;
                        default: return method.invoke(actualWindowManager, args);
                    }
                });
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                240,
                240,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        ModernOverlayWindow window = new ModernOverlayWindow(
                context, windowManager, overlay, params, 120);
        try {
            window.show();
            ShadowLooper.shadowMainLooper().idle();
            int updatesBeforeDispose = glassApi.updateCount.get();

            window.dispose();
            window.dispose();
            int updatesAfterDispose = glassApi.updateCount.get();
            blurListener.get().accept(true);
            ShadowLooper.shadowMainLooper().idle();

            assertEquals(1, removedListeners.get());
            assertEquals(updatesAfterDispose, glassApi.updateCount.get());
            assertTrue(updatesAfterDispose >= updatesBeforeDispose);
            assertFalse(backdrop.isActive());
        } finally {
            window.dispose();
            activityController.pause().stop().destroy();
        }
    }

    private static DragShareController controller() {
        DragShareController controller = new DragShareController(
                RuntimeEnvironment.getApplication(), OverlayWindowPolicy.portal());
        try {
            discardScheduledPrewarm(controller);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
        return controller;
    }

    private static void discardScheduledPrewarm(DragShareController controller) throws Exception {
        Runnable pendingTask = (Runnable) get(controller, "modernOverlayPrewarmTask");
        if (pendingTask != null) {
            ((Handler) get(controller, "mainHandler")).removeCallbacks(pendingTask);
        }
        set(controller, "modernOverlayPrewarmTask", null);
    }

    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static void invoke(Object object, String name, Class<?>[] parameterTypes,
                               Object... args) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        method.invoke(object, args);
    }

    private static final class ViewTreeObserverListener
            implements android.view.ViewTreeObserver.OnDrawListener {
        final AtomicInteger count;

        ViewTreeObserverListener(AtomicInteger count) {
            this.count = count;
        }

        @Override
        public void onDraw() {
            count.incrementAndGet();
        }
    }

    private static final class TrackingGlassApi implements NativeGlassBackdrop.GlassApi {
        final AtomicInteger updateCount = new AtomicInteger();

        @Override public boolean setPassWindowBlurEnabled(View view, boolean enabled) {
            updateCount.incrementAndGet();
            return true;
        }
        @Override public boolean getPassWindowBlurEnabled(View view) { return false; }
        @Override public boolean setBackgroundBlurMode(View view, int mode) {
            updateCount.incrementAndGet();
            return true;
        }
        @Override public boolean setBackgroundBlurRadius(View view, int radiusPx) {
            updateCount.incrementAndGet();
            return true;
        }
        @Override public boolean setViewBlurMode(View view, int mode) {
            updateCount.incrementAndGet();
            return true;
        }
        @Override public boolean setGlass(View view, float[] params) {
            updateCount.incrementAndGet();
            return true;
        }
        @Override public boolean setGlassBlurRadius(View view, int radius, int secondaryRadius) {
            updateCount.incrementAndGet();
            return true;
        }
    }
}

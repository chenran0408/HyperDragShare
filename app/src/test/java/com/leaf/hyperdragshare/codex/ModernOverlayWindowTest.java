package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.view.View;
import android.view.WindowManager;
import java.util.Collections;
import java.util.HashMap;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class ModernOverlayWindowTest {
    @Test
    public void transparentWindowBackgroundUsesTheModernLocalCornerRadius() {
        Context context = RuntimeEnvironment.getApplication();
        assertEquals(16f * context.getResources().getDisplayMetrics().density,
                ModernOverlayWindow.localBackgroundCornerRadiusPx(context),
                0.001f);
    }

    @Test
    public void opaqueFallbackIsUsedWhenCrossWindowBlurIsUnavailable() {
        assertTrue(ModernOverlayWindow.shouldUseNativeBackdropBlur(120, true));
        assertFalse(ModernOverlayWindow.shouldUseNativeBackdropBlur(0, true));
        assertFalse(ModernOverlayWindow.shouldUseNativeBackdropBlur(120, false));
    }

    @Test
    public void portalUsesTheVerifiedOpaqueWindowTypeForPublicBackdropBlur() {
        assertEquals(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG,
                OverlayWindowPolicy.portal().windowType);
    }

    @Test
    public void nativeGlassIsAppliedWhenTheBackdropActuallyAttachesAfterShow() {
        ActivityController<Activity> activityController = Robolectric.buildActivity(Activity.class).setup();
        Context context = activityController.get();
        FakeGlassApi glassApi = new FakeGlassApi();
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
                        case "removeCrossWindowBlurEnabledListener": return null;
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
            assertFalse(backdrop.isActive());

            window.show();
            // Dialog/Window attachment is queued by Robolectric; the production callback is the
            // backdrop child's actual attach event, not the return from Dialog.show().
            ShadowLooper.shadowMainLooper().idle();

            assertTrue(backdrop.isActive());
            assertTrue(overlay.isNativeGlassMaterialEnabled());
            assertTrue(glassApi.passWindowBlurEnabled);
            blurListener.get().accept(false);
            ShadowLooper.shadowMainLooper().idle();
            assertFalse(backdrop.isActive());
            assertFalse(overlay.isNativeGlassMaterialEnabled());
            blurListener.get().accept(true);
            ShadowLooper.shadowMainLooper().idle();
            assertTrue(backdrop.isActive());
        } finally {
            window.dispose();
            activityController.pause().stop().destroy();
        }
        assertFalse(backdrop.isActive());
        assertFalse(glassApi.passWindowBlurEnabled);
        blurListener.get().accept(true);
        ShadowLooper.shadowMainLooper().idle();
        assertFalse(backdrop.isActive());
    }

    private static final class FakeGlassApi implements NativeGlassBackdrop.GlassApi {
        boolean passWindowBlurEnabled;

        @Override
        public boolean setPassWindowBlurEnabled(View view, boolean enabled) {
            passWindowBlurEnabled = enabled;
            return true;
        }

        @Override
        public boolean getPassWindowBlurEnabled(View view) {
            return passWindowBlurEnabled;
        }

        @Override
        public boolean setBackgroundBlurMode(View view, int mode) {
            return true;
        }

        @Override
        public boolean setBackgroundBlurRadius(View view, int radiusPx) {
            return true;
        }

        @Override
        public boolean setViewBlurMode(View view, int mode) {
            return true;
        }

        @Override
        public boolean setGlass(View view, float[] params) {
            return true;
        }

        @Override
        public boolean setGlassBlurRadius(View view, int radius, int secondaryRadius) {
            return true;
        }
    }
}

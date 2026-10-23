package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class NativeGlassBackdropTest {
    @Test
    public void appliesSelectedThemeAndOpacityOnlyToTheNativeGlassParameters() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        try {
            Context context = controller.get();
            FakeGlassApi api = new FakeGlassApi();
            NativeGlassBackdrop backdrop = new NativeGlassBackdrop(
                    context,
                    api,
                    darkTheme -> {
                        assertTrue(darkTheme);
                        return paramsWithAlpha(0.8f);
                    });
            controller.get().setContentView(backdrop);

            assertTrue(backdrop.configure(120, 50, true));

            assertTrue(backdrop.isActive());
            assertTrue(api.passWindowBlurEnabled);
            assertEquals(0.4f, api.glassParams[18], 0.0001f);
            assertEquals(120, api.backdropBlurRadiusPx);
            assertEquals(
                    Math.min(100, Math.round(120f / context.getResources().getDisplayMetrics().density)),
                    api.glassBlurRadius);
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void passWindowBlurSetterMustBeConfirmedByTheGetter() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        try {
            FakeGlassApi api = new FakeGlassApi();
            api.reportPassWindowBlurEnabled = false;
            NativeGlassBackdrop backdrop = attachedBackdrop(controller, api);

            assertFalse(backdrop.configure(120, 100, false));

            assertFalse(backdrop.isActive());
            assertFalse(api.passWindowBlurEnabled);
            assertTrue(api.passWindowBlurDisableCalled);
            assertTrue(api.glassResetCalled);
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void partialGlassFailureClearsVendorState() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        try {
            FakeGlassApi api = new FakeGlassApi();
            api.acceptGlass = false;
            NativeGlassBackdrop backdrop = attachedBackdrop(controller, api);

            assertFalse(backdrop.configure(90, 75, false));

            assertFalse(backdrop.isActive());
            assertFalse(api.passWindowBlurEnabled);
            assertTrue(api.passWindowBlurDisableCalled);
            assertTrue(api.glassResetCalled);
            assertEquals(0, api.backdropBlurRadiusPx);
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void explicitDisableAndDetachmentClearAnActiveBackdrop() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        try {
            FakeGlassApi api = new FakeGlassApi();
            NativeGlassBackdrop backdrop = attachedBackdrop(controller, api);
            assertTrue(backdrop.configure(60, 100, false));

            backdrop.disable();

            assertFalse(backdrop.isActive());
            assertFalse(api.passWindowBlurEnabled);
            assertTrue(api.passWindowBlurDisableCalled);
            assertTrue(api.glassResetCalled);

            api.passWindowBlurDisableCalled = false;
            api.glassResetCalled = false;
            assertTrue(backdrop.configure(60, 100, false));
            controller.get().setContentView(new View(controller.get()));
            assertFalse(backdrop.isActive());
            assertFalse(api.passWindowBlurEnabled);
            assertTrue(api.passWindowBlurDisableCalled);
            assertTrue(api.glassResetCalled);
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void unavailableTokenFallsBackWithoutEnablingVendorState() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        try {
            FakeGlassApi api = new FakeGlassApi();
            NativeGlassBackdrop backdrop = new NativeGlassBackdrop(
                    controller.get(), api, darkTheme -> null);
            controller.get().setContentView(backdrop);

            assertFalse(backdrop.configure(80, 100, false));

            assertFalse(backdrop.isActive());
            assertFalse(api.passWindowBlurEnabled);
            assertFalse(api.passWindowBlurDisableCalled);
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test
    public void incompleteShaderParametersAreRejectedInsteadOfBeingApplied() {
        ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class).setup();
        try {
            FakeGlassApi api = new FakeGlassApi();
            NativeGlassBackdrop backdrop = new NativeGlassBackdrop(
                    controller.get(), api, darkTheme -> new float[19]);
            controller.get().setContentView(backdrop);

            assertFalse(backdrop.configure(80, 100, false));

            assertFalse(backdrop.isActive());
            assertFalse(api.passWindowBlurEnabled);
        } finally {
            controller.pause().stop().destroy();
        }
    }

    private static NativeGlassBackdrop attachedBackdrop(
            ActivityController<Activity> controller,
            FakeGlassApi api) {
        NativeGlassBackdrop backdrop = new NativeGlassBackdrop(
                controller.get(), api, darkTheme -> paramsWithAlpha(1f));
        controller.get().setContentView(backdrop);
        return backdrop;
    }

    private static float[] paramsWithAlpha(float alpha) {
        float[] params = new float[42];
        params[18] = alpha;
        return params;
    }

    private static final class FakeGlassApi implements NativeGlassBackdrop.GlassApi {
        boolean passWindowBlurEnabled;
        boolean reportPassWindowBlurEnabled = true;
        boolean passWindowBlurDisableCalled;
        boolean acceptGlass = true;
        boolean glassResetCalled;
        float[] glassParams;
        int backdropBlurRadiusPx;
        int glassBlurRadius;

        @Override
        public boolean setPassWindowBlurEnabled(View view, boolean enabled) {
            passWindowBlurEnabled = enabled;
            if (!enabled) {
                passWindowBlurDisableCalled = true;
            }
            return true;
        }

        @Override
        public boolean getPassWindowBlurEnabled(View view) {
            return passWindowBlurEnabled && reportPassWindowBlurEnabled;
        }

        @Override
        public boolean setBackgroundBlurMode(View view, int mode) {
            return true;
        }

        @Override
        public boolean setBackgroundBlurRadius(View view, int radiusPx) {
            backdropBlurRadiusPx = radiusPx;
            return true;
        }

        @Override
        public boolean setViewBlurMode(View view, int mode) {
            return true;
        }

        @Override
        public boolean setGlass(View view, float[] params) {
            if (params == null) {
                glassResetCalled = true;
                glassParams = null;
                return true;
            }
            if (!acceptGlass) {
                return false;
            }
            glassParams = params;
            return true;
        }

        @Override
        public boolean setGlassBlurRadius(View view, int radius, int secondaryRadius) {
            glassBlurRadius = radius;
            return true;
        }
    }
}

package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.FrameLayout;
import dalvik.system.PathClassLoader;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** A non-interactive vendor-glass backdrop behind the modern Compose surface. */
final class NativeGlassBackdrop extends FrameLayout {
    private static final String TAG = "DragShare/NativeGlass";
    private static final int GLASS_PARAMETER_COUNT = 42;
    private static final int MAX_BACKDROP_BLUR_RADIUS_PX = 400;
    private static final int MAX_GLASS_BLUR = 100;
    private static final int MAX_GLASS_SECONDARY_BLUR = 500;
    private static final String MIUIX_JAR_PATH = "/system_ext/framework/miuix.jar";
    private static final ClassLoader MIUIX_CLASS_LOADER = resolveMiuixClassLoader();

    interface GlassApi {
        boolean setPassWindowBlurEnabled(View view, boolean enabled);
        boolean getPassWindowBlurEnabled(View view);
        boolean setBackgroundBlurMode(View view, int mode);
        boolean setBackgroundBlurRadius(View view, int radiusPx);
        boolean setViewBlurMode(View view, int mode);
        boolean setGlass(View view, float[] params);
        boolean setGlassBlurRadius(View view, int radius, int secondaryRadius);
    }

    interface GlassParametersProvider {
        float[] load(boolean darkTheme) throws ReflectiveOperationException;
    }

    private final View glassLayer;
    private final GlassApi glassApi;
    private final GlassParametersProvider parametersProvider;
    private boolean active;
    private boolean configurationStarted;

    NativeGlassBackdrop(Context context) {
        this(context, new ReflectiveMiuiGlassApi(), new SystemGlassParametersProvider());
    }

    NativeGlassBackdrop(Context context, GlassApi glassApi,
            GlassParametersProvider parametersProvider) {
        super(context);
        if (glassApi == null || parametersProvider == null) {
            throw new IllegalArgumentException("Native glass requires an API and token provider");
        }
        this.glassApi = glassApi;
        this.parametersProvider = parametersProvider;
        setBackgroundColor(Color.TRANSPARENT);
        setClickable(false);
        setFocusable(false);

        glassLayer = new View(context);
        glassLayer.setBackgroundColor(Color.TRANSPARENT);
        glassLayer.setClickable(false);
        glassLayer.setFocusable(false);
        addView(glassLayer, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
    }

    boolean configure(int blurRadiusPx, int opacityPercent, boolean darkTheme) {
        disable();
        if (!isAttachedToWindow() || blurRadiusPx <= 0) {
            return false;
        }

        final float[] parameters;
        try {
            parameters = parametersProvider.load(darkTheme);
            if (parameters == null || parameters.length != GLASS_PARAMETER_COUNT) {
                return false;
            }
            float[] adjusted = parameters.clone();
            float opacity = Math.max(0, Math.min(100, opacityPercent)) / 100f;
            adjusted[18] *= opacity;
            configurationStarted = true;

            int clampedBackdropRadius = Math.max(0,
                    Math.min(MAX_BACKDROP_BLUR_RADIUS_PX, blurRadiusPx));
            int glassBlur = Math.max(0, Math.min(MAX_GLASS_BLUR,
                    Math.round(blurRadiusPx / getResources().getDisplayMetrics().density)));
            int secondaryGlassBlur = Math.min(MAX_GLASS_SECONDARY_BLUR,
                    glassBlur * MAX_GLASS_SECONDARY_BLUR / 36);

            if (!glassApi.setPassWindowBlurEnabled(this, true)
                    || !glassApi.getPassWindowBlurEnabled(this)
                    || !glassApi.setBackgroundBlurMode(this, 1)
                    || !glassApi.setBackgroundBlurRadius(this, clampedBackdropRadius)
                    || !glassApi.setViewBlurMode(this, 0)
                    || !glassApi.setBackgroundBlurMode(glassLayer, 0)
                    || !glassApi.setViewBlurMode(glassLayer, 1)
                    || !glassApi.setGlassBlurRadius(glassLayer, glassBlur, secondaryGlassBlur)
                    || !glassApi.setGlass(glassLayer, adjusted)) {
                return false;
            }
            active = true;
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            DragShareLog.w(TAG, "Native glass setup failed; using the window blur fallback", error);
            return false;
        } finally {
            if (!active && configurationStarted) {
                clearVendorState();
            }
        }
    }

    void disable() {
        if (active || configurationStarted) {
            clearVendorState();
        }
    }

    boolean isActive() {
        return active;
    }

    private void clearVendorState() {
        try {
            cleanupCall(() -> glassApi.setGlass(glassLayer, (float[]) null));
            cleanupCall(() -> glassApi.setGlassBlurRadius(glassLayer, 0, 0));
            cleanupCall(() -> glassApi.setBackgroundBlurMode(glassLayer, 0));
            cleanupCall(() -> glassApi.setViewBlurMode(glassLayer, 0));
            cleanupCall(() -> glassApi.setBackgroundBlurMode(this, 0));
            cleanupCall(() -> glassApi.setBackgroundBlurRadius(this, 0));
            cleanupCall(() -> glassApi.setViewBlurMode(this, 0));
            cleanupCall(() -> glassApi.setPassWindowBlurEnabled(this, false));
            try {
                if (glassApi.getPassWindowBlurEnabled(this)) {
                    DragShareLog.w(TAG, "Vendor pass-window blur remained enabled after reset");
                }
            } catch (RuntimeException | LinkageError error) {
                DragShareLog.w(TAG, "Unable to verify vendor blur reset", error);
            }
        } finally {
            active = false;
            configurationStarted = false;
        }
    }

    private static void cleanupCall(BooleanCall call) {
        try {
            call.run();
        } catch (RuntimeException | LinkageError error) {
            DragShareLog.w(TAG, "Unable to clear part of the vendor glass state", error);
        }
    }

    private interface BooleanCall {
        boolean run();
    }

    @Override
    protected void onDetachedFromWindow() {
        disable();
        super.onDetachedFromWindow();
    }

    private static final class SystemGlassParametersProvider implements GlassParametersProvider {
        private static final String TOKEN_CLASS = "miuix.theme.token.GlassToken";
        private static final String LIGHT_TOKEN = "Glass_Common_Medium_Regular_Low_Light";
        private static final String DARK_TOKEN = "Glass_Common_Medium_Regular_Dark";

        @Override
        public float[] load(boolean darkTheme) throws ReflectiveOperationException {
            Class<?> tokenClass = Class.forName(TOKEN_CLASS, true, MIUIX_CLASS_LOADER);
            Field presetField = tokenClass.getField(darkTheme ? DARK_TOKEN : LIGHT_TOKEN);
            Object preset = presetField.get(null);
            Method toShaderParams = tokenClass.getMethod("toShaderParams");
            Object value = toShaderParams.invoke(preset);
            if (!(value instanceof float[])) {
                throw new ReflectiveOperationException("GlassToken did not return float parameters");
            }
            int expectedLength = tokenClass.getField("PARAMS_LENGTH").getInt(null);
            float[] parameters = (float[]) value;
            if (expectedLength != GLASS_PARAMETER_COUNT || parameters.length != expectedLength) {
                throw new ReflectiveOperationException("Unexpected GlassToken parameter length");
            }
            return parameters;
        }
    }

    private static final class ReflectiveMiuiGlassApi implements GlassApi {
        private static final String UTILS_CLASS = "miuix.core.util.MiuiBlurUtils";
        private final Class<?> utilsClass;

        ReflectiveMiuiGlassApi() {
            Class<?> loaded;
            try {
                loaded = Class.forName(UTILS_CLASS, true, MIUIX_CLASS_LOADER);
            } catch (ClassNotFoundException | LinkageError error) {
                loaded = null;
            }
            utilsClass = loaded;
        }

        @Override
        public boolean setPassWindowBlurEnabled(View view, boolean enabled) {
            return callBoolean("setPassWindowBlurEnabled",
                    new Class<?>[]{View.class, boolean.class}, view, enabled);
        }

        @Override
        public boolean getPassWindowBlurEnabled(View view) {
            return callBoolean("getPassWindowBlurEnabled", new Class<?>[]{View.class}, view);
        }

        @Override
        public boolean setBackgroundBlurMode(View view, int mode) {
            return callBoolean("setBackgroundBlurMode",
                    new Class<?>[]{View.class, int.class}, view, mode);
        }

        @Override
        public boolean setBackgroundBlurRadius(View view, int radiusPx) {
            return callBoolean("setBackgroundBlurRadius",
                    new Class<?>[]{View.class, int.class}, view, radiusPx);
        }

        @Override
        public boolean setViewBlurMode(View view, int mode) {
            return callBoolean("setViewBlurMode", new Class<?>[]{View.class, int.class}, view, mode);
        }

        @Override
        public boolean setGlass(View view, float[] params) {
            return callBoolean("setGlass", new Class<?>[]{View.class, float[].class}, view, params);
        }

        @Override
        public boolean setGlassBlurRadius(View view, int radius, int secondaryRadius) {
            return callBoolean("setMiGlassBlurRadius",
                    new Class<?>[]{View.class, int.class, int.class}, view, radius, secondaryRadius);
        }

        private boolean callBoolean(String name, Class<?>[] parameterTypes, Object... args) {
            if (utilsClass == null) {
                return false;
            }
            try {
                Object result = utilsClass.getMethod(name, parameterTypes).invoke(null, args);
                return result instanceof Boolean && (Boolean) result;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                return false;
            }
        }
    }

    private static ClassLoader resolveMiuixClassLoader() {
        ClassLoader hostLoader = NativeGlassBackdrop.class.getClassLoader();
        if (canLoadMiuixClasses(hostLoader)) {
            return hostLoader;
        }
        try {
            File miuixJar = new File(MIUIX_JAR_PATH);
            if (miuixJar.isFile()) {
                ClassLoader systemExtensionLoader = new PathClassLoader(
                        MIUIX_JAR_PATH,
                        View.class.getClassLoader());
                if (canLoadMiuixClasses(systemExtensionLoader)) {
                    return systemExtensionLoader;
                }
            }
        } catch (RuntimeException | LinkageError error) {
            DragShareLog.w(TAG, "Unable to create the system Miuix class loader", error);
        }
        return hostLoader;
    }

    private static boolean canLoadMiuixClasses(ClassLoader loader) {
        if (loader == null) {
            return false;
        }
        try {
            Class.forName("miuix.theme.token.GlassToken", false, loader);
            Class.forName("miuix.core.util.MiuiBlurUtils", false, loader);
            return true;
        } catch (ClassNotFoundException | RuntimeException | LinkageError error) {
            return false;
        }
    }
}

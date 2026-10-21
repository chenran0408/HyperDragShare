package com.leaf.hyperdragshare.codex;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Generated payload and controller events only: no Root, input injection or page capture. */
public final class MenuLifecycleProbe extends Instrumentation {
    private String mode;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mode = arguments == null ? null : arguments.getString("mode");
        start();
    }

    @Override public void onStart() {
        Bundle report = new Bundle();
        List<ModernOverlayWindow> observed = new ArrayList<>();
        Context context = getTargetContext().createDisplayContext(getTargetContext()
                .getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY)).createWindowContext(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        Rect bounds = context.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
        if ("linear_race".equals(mode)) {
            boolean passed = checkLinearRace(context, bounds, DragShareSettings.STYLE_SIMPLE, report);
            passed &= checkLinearRace(context, bounds, DragShareSettings.STYLE_PORTAL, report);
            passed &= checkGlowReentry(context, bounds, report);
            report.putBoolean("passed", passed);
            finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, report);
            return;
        }
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.application());
        boolean passed = false;
        try {
            Bundle settings = DragShareSettings.readLocal(context).toBundle();
            settings.putInt("ui_style", DragShareSettings.STYLE_MODERN);
            settings.putBoolean("block_background_scroll", false);
            runOnMainSync(() -> controller.show(
                    CapturedContent.text("MENU LIFECYCLE 122", "generated", null, null, null),
                    bounds.width() / 2f, bounds.height() / 2f, DragShareSettings.fromBundle(settings)));
            move(controller, bounds.width() - 2, bounds.height() / 2f);
            ModernOverlayWindow right = window(controller);
            observed.add(right);
            require(right.isShowing(), "Right menu did not open");
            move(controller, bounds.width() / 2f, 180);
            ModernOverlayWindow top = window(controller);
            observed.add(top);
            require(top != right && top.isShowing(), "Top menu did not replace right menu");
            report.putBoolean("oldWindowDisposed", disposed(right));
            require(disposed(right), "Changing edges retained the old modern menu window");
            move(controller, 2, bounds.height() / 2f);
            ModernOverlayWindow left = window(controller);
            observed.add(left);
            require(left != top && left.isShowing() && disposed(top), "Left edge retained the top menu");
            move(controller, bounds.width() / 2f, bounds.height() / 2f);
            require(disposed(left), "Leaving the edge did not dispose its menu");
            move(controller, bounds.width() / 2f, bounds.height() - 180);
            ModernOverlayWindow bottom = window(controller);
            observed.add(bottom);
            require(bottom.isShowing(), "Bottom menu did not reopen after leaving");
            runOnMainSync(controller::cancelActiveSession);
            SystemClock.sleep(450);
            waitForIdleSync();
            require(disposed(bottom), "Cancel retained the bottom menu");
            report.putString("behavior", "right-top-left, leave, bottom-reopen, cancel");
            passed = true;
        } catch (ReflectiveOperationException | IllegalStateException error) {
            report.putString("error", error.toString());
        } finally {
            // Also clean leaked windows on the unfixed build used to reproduce the defect.
            runOnMainSync(() -> {
                controller.destroy();
                for (ModernOverlayWindow window : observed) window.dispose();
            });
        }
        report.putBoolean("passed", passed);
        finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, report);
    }

    private boolean checkLinearRace(Context context, Rect bounds, int style, Bundle report) {
        String key = "style" + style;
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.application());
        List<View> observed = new ArrayList<>();
        boolean passed = false;
        try {
            showGenerated(controller, context, bounds, style);
            move(controller, bounds.width() - 2, bounds.height() / 2f);
            View right = (View) readField(controller, "menuView");
            require(right != null && right.isAttachedToWindow(), "Initial right menu was not attached");
            observed.add(right);
            runOnMainSync(() -> {
                long time = SystemClock.uptimeMillis();
                controller.acceptPointerEvent(MotionEvent.ACTION_MOVE, bounds.width() / 2f,
                        bounds.height() / 2f, time, "probe");
                controller.acceptPointerEvent(MotionEvent.ACTION_MOVE, bounds.width() / 2f,
                        180, time + 1, "probe");
                // acceptPointerEvent always posts, while cancel runs immediately on Main.
                new Handler(Looper.getMainLooper()).post(controller::cancelActiveSession);
            });
            SystemClock.sleep(450);
            waitForIdleSync();
            boolean detached = !right.isAttachedToWindow();
            report.putBoolean(key + "OldRightDetached", detached);
            require(detached, "Leaving, changing edge and cancelling retained the old right menu");
            passed = true;
        } catch (ReflectiveOperationException | IllegalStateException error) {
            report.putString(key + "Error", error.toString());
        } finally {
            cleanLinearWindows(controller, context, observed);
        }
        report.putBoolean(key + "Passed", passed);
        return passed;
    }

    private boolean checkGlowReentry(Context context, Rect bounds, Bundle report) {
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.application());
        List<View> observed = new ArrayList<>();
        boolean passed = false;
        try {
            showGenerated(controller, context, bounds, DragShareSettings.STYLE_PORTAL);
            move(controller, bounds.width() - 2, bounds.height() / 2f);
            View right = (View) readField(controller, "menuView");
            require(right != null && right.isAttachedToWindow(), "Initial glow menu was not attached");
            observed.add(right);
            PortalGlowView glow = (PortalGlowView) readField(controller, "glowView");
            require(glow != null, "Initial glow view was not created");
            require((float) readField(glow, "expansionProgress") >= .95f, "Initial glow did not expand");
            runOnMainSync(() -> {
                long time = SystemClock.uptimeMillis();
                controller.acceptPointerEvent(MotionEvent.ACTION_MOVE, bounds.width() / 2f,
                        bounds.height() / 2f, time, "probe");
                controller.acceptPointerEvent(MotionEvent.ACTION_MOVE, bounds.width() - 2,
                        bounds.height() / 2f, time + 1, "probe");
            });
            SystemClock.sleep(450);
            waitForIdleSync();
            boolean shown = (boolean) readField(controller, "menuShown");
            float progress = (float) readField(glow, "expansionProgress");
            report.putBoolean("glowReentryMenuShown", shown);
            report.putFloat("glowReentryExpansion", progress);
            require(shown && right.isAttachedToWindow(), "Same-edge reentry did not restore the menu");
            require(progress >= .95f, "The restored menu retained a collapsed glow");
            passed = true;
        } catch (ReflectiveOperationException | IllegalStateException error) {
            report.putString("glowReentryError", error.toString());
        } finally {
            cleanLinearWindows(controller, context, observed);
        }
        report.putBoolean("glowReentryPassed", passed);
        return passed;
    }

    private void showGenerated(DragShareController controller, Context context, Rect bounds, int style) {
        Bundle settings = DragShareSettings.readLocal(context).toBundle();
        settings.putInt("ui_style", style);
        settings.putBoolean("block_background_scroll", false);
        settings.putBoolean("close_menu_when_pointer_leaves", true);
        runOnMainSync(() -> controller.show(
                CapturedContent.text("MENU RACE 129", "generated", null, null, null),
                bounds.width() / 2f, bounds.height() / 2f, DragShareSettings.fromBundle(settings)));
    }

    private void cleanLinearWindows(DragShareController controller, Context context, List<View> observed) {
        runOnMainSync(() -> {
            controller.destroy();
            // The baseline can lose ownership of an old menu. Remove only our captured views.
            for (View view : observed) {
                if (view.isAttachedToWindow()) {
                    view.animate().cancel();
                    context.getSystemService(WindowManager.class).removeViewImmediate(view);
                }
            }
        });
    }

    private static Object readField(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private void move(DragShareController controller, float x, float y) {
        controller.acceptPointerEvent(MotionEvent.ACTION_MOVE, x, y, SystemClock.uptimeMillis(), "probe");
        SystemClock.sleep(450);
        waitForIdleSync();
    }

    private static ModernOverlayWindow window(DragShareController controller) throws ReflectiveOperationException {
        Field field = DragShareController.class.getDeclaredField("modernMenuWindow");
        field.setAccessible(true);
        ModernOverlayWindow window = (ModernOverlayWindow) field.get(controller);
        require(window != null, "Native modern menu was not created");
        return window;
    }

    private static boolean disposed(ModernOverlayWindow window) throws ReflectiveOperationException {
        Field field = ModernOverlayWindow.class.getDeclaredField("disposed");
        field.setAccessible(true);
        return field.getBoolean(window);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

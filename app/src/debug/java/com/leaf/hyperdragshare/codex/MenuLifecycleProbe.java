package com.leaf.hyperdragshare.codex;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.Display;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Generated payload and controller events only: no Root, input injection or page capture. */
public final class MenuLifecycleProbe extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle report = new Bundle();
        List<ModernOverlayWindow> observed = new ArrayList<>();
        Context context = getTargetContext().createDisplayContext(getTargetContext()
                .getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY)).createWindowContext(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
        Rect bounds = context.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
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

package com.leaf.hyperdragshare.gestures.xposed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public final class GestureOverlayExitTest {
    @Test public void taskSwitcherIgnoresRepeatedCloseAndCancelAfterRemoval() {
        TaskSwitcherOverlayController controller = new TaskSwitcherOverlayController(
                RuntimeEnvironment.getApplication(), new Handler(Looper.getMainLooper()));
        controller.removeNow();
        controller.externalCancel();
        controller.dismiss();
        controller.removeNow();
        controller.externalCancel();
        controller.dismiss();
        ShadowLooper.shadowMainLooper().idle();
    }

    @Test public void honeycombIgnoresRepeatedCloseAndCancelAfterRemoval() {
        HoneycombOverlayController controller = new HoneycombOverlayController(
                RuntimeEnvironment.getApplication(), new Handler(Looper.getMainLooper()));
        controller.removeNow();
        controller.externalCancel();
        controller.dismiss();
        controller.removeNow();
        controller.externalCancel();
        controller.dismiss();
        ShadowLooper.shadowMainLooper().idle();
    }

    @Test public void detachingDuringSnapDoesNotRearmIdleDismissal() throws Exception {
        var activityController = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = activityController.get();
        SideWheelOverlayView view = new SideWheelOverlayView(activity);
        int[] dismissed = {0};
        view.configure(Collections.emptyList(), GestureGeometry.Corner.LEFT,
                40, 80, 40, 30, 0, true, false,
                new SideWheelOverlayView.Listener() {
                    @Override public void onLaunch(int index) { }
                    @Override public void onDismiss() { dismissed[0]++; }
                    @Override public void onSelectionChanged(int index) { }
                });
        try {
            activity.setContentView(view);
            ShadowLooper.shadowMainLooper().idle();
            assertTrue(view.isAttachedToWindow());
            Field position = SideWheelOverlayView.class.getDeclaredField("position");
            position.setAccessible(true);
            position.setFloat(view, .5f);
            var snap = SideWheelOverlayView.class.getDeclaredMethod("snapToNearest");
            snap.setAccessible(true);
            snap.invoke(view);
            assertNotNull(field(view, "snapAnimator"));

            activity.setContentView(new View(activity));
            assertFalse(view.isAttachedToWindow());
            Handler handler = (Handler) field(view, "handler");
            Runnable timeout = (Runnable) field(view, "timeout");
            assertFalse("cancelled snap must not leave a timeout on the detached view",
                    handler.hasCallbacks(timeout));
            ShadowLooper.shadowMainLooper().idleFor(6, TimeUnit.SECONDS);
            assertEquals(0, dismissed[0]);
        } finally {
            activityController.pause().stop().destroy();
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}

package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class PressPreviewHandoffTest {
    @Test public void nativePreviewDoesNotCancelItsOwnPendingPress() throws Exception {
        DragShareController controller = controller();
        nativeSession(controller, 100L);

        assertTrue(controller.canContinuePress(100L));
        assertFalse(controller.canContinuePress(200L));
        assertFalse(controller.canContinuePress(-1L));
        controller.destroy();
    }

    @Test public void acceptedPressRemovesOnlyItsPreviewAndWaitsForPhysicalRelease() throws Exception {
        DragShareController controller = controller();
        down(controller, 100L);
        Object session = nativeSession(controller, 100L);
        controller.handoffToPress(100L);

        assertFalse(controller.isActive());
        assertTrue((boolean) field(session, "cancelled").get(session));
        assertTrue(controller.shouldSuppressPortalHostFloatWindow());
        int[] callbacks = {0};
        controller.acceptPointerEvent(MotionEvent.ACTION_UP, 20f, 30f, 900L, "root", () -> {
            assertTrue(controller.isAwaitingRootRelease());
            callbacks[0]++;
        });
        ShadowLooper.shadowMainLooper().idle();

        assertEquals(1, callbacks[0]);
        assertFalse(controller.isAwaitingRootRelease());
        assertFalse(controller.shouldSuppressPortalHostFloatWindow());
        controller.destroy();
    }

    @Test public void queuedNativeStartCannotReopenAfterEarlyAreaHandoff() throws Exception {
        DragShareController controller = controller();
        down(controller, 100L);
        Thread host = new Thread(() -> controller.show(
                CapturedContent.text("test", "test.app", null), 20f, 30f, DragShareSettings.defaults()));
        host.start();
        host.join();
        controller.handoffToPress(100L);
        ShadowLooper.shadowMainLooper().idle();

        assertFalse(controller.isActive());
        assertTrue(controller.isAwaitingRootRelease());
        assertEquals(null, field(controller, "session").get(controller));
        controller.destroy();
    }

    @Test public void rootMultitouchCancelFlushesCleanupWithoutWaitingForAnUp() throws Exception {
        for (boolean handoff : new boolean[]{false, true}) {
            DragShareController controller = controller();
            down(controller, 100L);
            nativeSession(controller, 100L);
            if (handoff) controller.handoffToPress(100L);
            int[] callbacks = {0};
            controller.acceptPointerEvent(MotionEvent.ACTION_CANCEL, 20f, 30f, 400L, "root",
                    () -> callbacks[0]++);
            ShadowLooper.shadowMainLooper().idle();

            assertEquals(1, callbacks[0]);
            assertFalse(controller.isActive());
            assertFalse(controller.isAwaitingRootRelease());
            controller.destroy();
        }
    }

    @Test public void oldPressCannotTakeOverTheNextGesture() throws Exception {
        DragShareController controller = controller();
        down(controller, 100L);
        controller.handoffToPress(100L);
        // Root receives the next DOWN before the press source's main-thread callback.
        controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, 1000L, "root");
        assertFalse(controller.canContinuePress(100L));
        ShadowLooper.shadowMainLooper().idle();
        nativeSession(controller, 1000L);
        controller.handoffToPress(100L);

        assertTrue(controller.isActive());
        assertTrue(controller.canContinuePress(1000L));
        controller.destroy();
    }

    private static DragShareController controller() {
        return new DragShareController(RuntimeEnvironment.getApplication(), OverlayWindowPolicy.portal());
    }

    private static void down(DragShareController controller, long time) {
        controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, time, "root");
        ShadowLooper.shadowMainLooper().idle();
    }

    // Establish the passive native preview state without attaching real overlay windows.
    private static Object nativeSession(DragShareController controller, long downTime) throws Exception {
        Class<?> type = Class.forName(DragShareController.class.getName() + "$Session");
        var constructor = type.getDeclaredConstructor(CapturedContent.class, long.class);
        constructor.setAccessible(true);
        Object session = constructor.newInstance(CapturedContent.text("test", "test.app", null), downTime);
        field(controller, "session").set(controller, session);
        field(controller, "active").set(controller, true);
        return session;
    }

    private static Field field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}

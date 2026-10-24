package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.view.MotionEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public final class DragShareTouchDiagnosticsTest {
    private static final String TAG = "DragShare/TouchStream";

    @Before public void setUp() {
        configureDebug();
        ShadowLog.clear();
    }

    @After public void tearDown() {
        DragShareLog.configure(DragShareSettings.defaults());
        ShadowLog.clear();
    }

    @Test public void rootMoveContinuesUntilUpAndCancelRecordsActualStopReasons() throws Exception {
        DragShareController controller = controller();
        try {
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 500f, 1000f, 100L, "root");
            ShadowLooper.shadowMainLooper().idle();
            Object first = session("first", 100L);
            set(controller, "session", first);
            set(controller, "active", true);

            controller.acceptPointerEvent(MotionEvent.ACTION_MOVE, 504f, 1004f, 110L, "root");
            ShadowLooper.shadowMainLooper().idle();
            assertTrue(controller.isActive());
            controller.acceptPointerEvent(MotionEvent.ACTION_UP, 510f, 1010f, 120L, "root");
            ShadowLooper.shadowMainLooper().idle();
            assertFalse(controller.isActive());

            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 600f, 1200f, 200L, "root");
            ShadowLooper.shadowMainLooper().idle();
            Object second = session("second", 200L);
            set(controller, "session", second);
            set(controller, "active", true);
            controller.acceptPointerEvent(MotionEvent.ACTION_MOVE, 604f, 1204f, 210L, "root");
            ShadowLooper.shadowMainLooper().idle();
            assertTrue(controller.isActive());
            controller.acceptPointerEvent(MotionEvent.ACTION_CANCEL, 604f, 1204f, 220L, "root");
            ShadowLooper.shadowMainLooper().idle();
            assertFalse(controller.isActive());

            String log = messages();
            assertTrue(log.contains("action=ACTION_MOVE"));
            assertTrue(log.contains("state=DRAGGING dragging=true"));
            assertTrue(log.contains("action=ACTION_UP"));
            assertTrue(log.contains("action=ACTION_CANCEL"));
            assertTrue(log.contains("action=DRAG_STOP") && log.contains("reason=root_up"));
            assertTrue(log.contains("action=DRAG_STOP") && log.contains("reason=root_cancel"));
        } finally {
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void staleInputWatchdogStillCancelsAtThirtySecondsAndRecordsReason() throws Exception {
        DragShareController controller = controller();
        long downTime = android.os.SystemClock.uptimeMillis();
        controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 500f, 1000f, downTime, "root");
        ShadowLooper.shadowMainLooper().idle();
        Object activeSession = session("watchdog", downTime);
        set(controller, "rootDownTime", downTime);
        set(controller, "session", activeSession);
        set(controller, "active", true);
        try {
            invoke(controller, "refreshStaleInputWatchdog", new Class<?>[0]);
            ShadowLooper.shadowMainLooper().idleFor(
                    DragShareController.STALE_INPUT_TIMEOUT_MS - 1, TimeUnit.MILLISECONDS);
            assertTrue(controller.isActive());
            ShadowLooper.shadowMainLooper().idleFor(1, TimeUnit.MILLISECONDS);

            assertFalse(controller.isActive());
            assertTrue((boolean) get(activeSession, "cancelled"));
            String log = messages();
            assertTrue(log.contains("reason=stale_input_watchdog_30000ms"));
            assertTrue(eventAgeForReason(log, "stale_input_watchdog_30000ms") > 0);
        } finally {
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void delayedHostCancelCanCancelNewGestureAndDiagnosticRecordsExistingRace() throws Exception {
        DragShareController controller = controller();
        try {
            assertFalse(controller.isActive());
            controller.onHostTaskCancelled();
            ShadowLooper.shadowMainLooper().idleFor(16, TimeUnit.MILLISECONDS);

            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 500f, 1000f, 1000L, "root");
            ShadowLooper.shadowMainLooper().idle();
            Object replacement = session("replacement", 1000L);
            set(controller, "session", replacement);
            set(controller, "active", true);
            controller.acceptPointerEvent(MotionEvent.ACTION_MOVE, 505f, 1005f, 1010L, "root");
            ShadowLooper.shadowMainLooper().idle();
            assertTrue("replacement gesture receives MOVE before old timer fires", controller.isActive());
            assertFalse((boolean) get(replacement, "cancelled"));

            ShadowLooper.shadowMainLooper().idleFor(16, TimeUnit.MILLISECONDS);

            assertFalse("old delayed callback still uses the new active flag", controller.isActive());
            assertTrue((boolean) get(replacement, "cancelled"));
            String log = messages();
            assertTrue(log.contains("action=HOST_CANCEL_SCHEDULED"));
            assertTrue(log.contains("action=ACTION_MOVE") && log.contains("state=DRAGGING dragging=true"));
            assertTrue(log.contains("action=DRAG_STOP")
                    && log.contains("reason=delayed_host_cancel_task"));
        } finally {
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void focusObservationAloneDoesNotCancelController() throws Exception {
        DragShareController controller = controller();
        Object activeSession = session("focus", 400L);
        set(controller, "rootDownTime", 400L);
        set(controller, "session", activeSession);
        set(controller, "active", true);
        try {
            // This is only a helper-level false-focus observation; it does not model host focus.
            new GestureDiagnostics.Stream("focus_probe").mark(
                    "FOCUS_SAMPLE", "example.app", "DRAGGING", true,
                    false, false, "test_observation_only");
            controller.traceDiagnostic("FOCUS_PROBE", "module_focus_query", "DRAGGING");
            assertTrue(controller.isActive());
            assertFalse((boolean) get(activeSession, "cancelled"));
            assertTrue(java.util.Arrays.stream(messages().split("\n")).anyMatch(line ->
                    line.contains("action=FOCUS_SAMPLE ") && line.contains("windowFocus=false viewFocus=false")));
            assertTrue(messages().contains("action=FOCUS_PROBE"));
        } finally {
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void moduleMenuMarkerAndPortalCallbackMotionRemainSeparateObservations() throws Exception {
        DragShareController controller = controller();
        Object activeSession = session("menu", 500L);
        set(controller, "rootDownTime", 500L);
        set(controller, "session", activeSession);
        set(controller, "active", true);
        MotionEvent event = MotionEvent.obtain(500L, 520L, MotionEvent.ACTION_MOVE,
                510f, 1010f, 0);
        try {
            controller.traceDiagnostic("MMGG_MENU_SHOW", "own_menu_only", null);
            new GestureDiagnostics.Stream("portal_callback_observed").motion(event,
                    "example.app", "HOST_CALLBACK_OBSERVED", null, null, null,
                    "existing_motion_callback_suppression");

            assertTrue(controller.isActive());
            assertFalse((boolean) get(activeSession, "cancelled"));
            String log = messages();
            assertTrue(log.contains("action=MMGG_MENU_SHOW"));
            assertTrue(log.contains("action=ACTION_MOVE")
                    && log.contains("channel=portal_callback_observed"));
            assertTrue(log.contains("reason=existing_motion_callback_suppression"));
        } finally {
            event.recycle();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    private static DragShareController controller() throws Exception {
        DragShareController controller = new DragShareController(
                RuntimeEnvironment.getApplication(), OverlayWindowPolicy.portal());
        set(controller, "screenWidth", 1080);
        set(controller, "screenHeight", 2400);
        return controller;
    }

    private static Object session(String text, long downTime) throws Exception {
        Class<?> type = Class.forName(DragShareController.class.getName() + "$Session");
        var constructor = type.getDeclaredConstructor(CapturedContent.class, long.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(CapturedContent.text(text, "example.app", new Rect()),
                downTime, OperationTrace.id("press", downTime));
    }

    private static String messages() {
        StringBuilder out = new StringBuilder();
        for (ShadowLog.LogItem item : ShadowLog.getLogsForTag(TAG)) out.append(item.msg).append('\n');
        return out.toString();
    }

    private static long eventAgeForReason(String log, String reason) {
        for (String line : log.split("\n")) {
            if (!line.contains("reason=" + reason)) continue;
            java.util.regex.Matcher match = java.util.regex.Pattern
                    .compile("lastEventAge=([0-9]+)").matcher(line);
            if (match.find()) return Long.parseLong(match.group(1));
        }
        return -1L;
    }

    private static void configureDebug() {
        DragShareLog.configure(DragShareSettings.defaults().toBuilder()
                .setLogLevel(DragShareSettings.LOG_LEVEL_DEBUG)
                .setLogDestination(DragShareSettings.LOG_DESTINATION_SYSTEM)
                .build());
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

    private static void invoke(Object object, String name, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        java.lang.reflect.Method method = object.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        method.invoke(object, args);
    }
}

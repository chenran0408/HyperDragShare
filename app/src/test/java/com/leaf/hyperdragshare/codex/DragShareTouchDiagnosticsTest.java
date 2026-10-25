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
    @Test public void repeatedInactivePressCleanupDoesNotBypassMoveSamplingWithStateSpam() throws Exception {
        DragShareController controller = controller();
        controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 500f, 1000f, 100L, "root");
        ShadowLooper.shadowMainLooper().idle();
        PressCaptureSource source = new PressCaptureSource(RuntimeEnvironment.getApplication(), null,
                controller, DragShareSettings.defaults());
        set(source, "downTime", 100L);
        set(source, "pointerDown", true);
        ShadowLog.clear();
        try {
            source.cancelGesture();
            for (int i = 0; i < 100; i++) source.cancelGesture();
            ShadowLooper.shadowMainLooper().idle();
            long cleanups = ShadowLog.getLogsForTag("DragShare/TouchStream").stream()
                    .filter(item -> item.msg.contains("reason=press_detector_cleanup ")).count();
            org.junit.Assert.assertEquals(1L, cleanups);
        } finally {
            source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }
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

    @Test public void delayedHostCancelCannotCancelNewGesture() throws Exception {
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

            assertTrue("old delayed callback must not cancel the replacement session", controller.isActive());
            assertFalse((boolean) get(replacement, "cancelled"));
            String log = messages();
            assertTrue(log.contains("action=HOST_CANCEL_SCHEDULED"));
            assertTrue(log.contains("action=HOST_CANCEL_RESOLVED"));
            assertTrue(log.contains("action=ACTION_MOVE") && log.contains("state=DRAGGING dragging=true"));
            assertFalse(log.contains("reason=delayed_host_cancel_task"));
        } finally {
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void control257FinishesSessionThatOwnedQueuedCallback() throws Exception {
        DragShareController controller = controller();
        Object owner = session("owner", 300L);
        set(controller, "session", owner);
        set(controller, "active", true);
        try {
            controller.finishFromControlEvent();
            ShadowLooper.shadowMainLooper().idle();
            assertFalse(controller.isActive());
            assertTrue("no-target 257 retains existing finish cancellation semantics",
                    (boolean) get(owner, "cancelled"));
            assertTrue(messages().contains("reason=host_control_257"));
        } finally {
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void queuedControl257ForOldSessionCannotFinishReplacement() throws Exception {
        DragShareController controller = controller();
        Object owner = session("owner", 300L);
        Object replacement = session("replacement", 400L);
        set(controller, "session", owner);
        set(controller, "active", true);
        try {
            controller.finishFromControlEvent();
            // The callback remains queued until the main looper is drained.
            set(controller, "session", replacement);
            set(controller, "active", true);
            ShadowLooper.shadowMainLooper().idle();
            assertTrue(controller.isActive());
            assertFalse((boolean) get(replacement, "cancelled"));
            assertFalse(messages().contains("reason=host_control_257"));
        } finally {
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void control257QueuedWithoutSessionCannotFinishLaterSession() throws Exception {
        DragShareController controller = controller();
        Object later = session("later", 500L);
        try {
            controller.finishFromControlEvent();
            set(controller, "session", later);
            set(controller, "active", true);
            ShadowLooper.shadowMainLooper().idle();
            assertTrue(controller.isActive());
            assertFalse((boolean) get(later, "cancelled"));
            assertFalse(messages().contains("reason=host_control_257"));
        } finally {
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void delayedHostCancelRecordsInactiveResolutionWithoutCancelling() throws Exception {
        DragShareController controller = controller();
        try {
            assertFalse(controller.isActive());
            controller.onHostTaskCancelled();
            ShadowLooper.shadowMainLooper().idleFor(32, TimeUnit.MILLISECONDS);

            assertFalse(controller.isActive());
            String log = messages();
            assertTrue(log.contains("action=HOST_CANCEL_SCHEDULED"));
            assertTrue(log.contains("action=HOST_CANCEL_RESOLVED"));
            assertTrue(log.contains("state=IDLE dragging=false"));
            assertTrue(log.contains("reason=inactive_at_delayed_callback"));
            assertFalse(log.contains("reason=delayed_host_cancel_task"));
        } finally {
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void delayedHostCancelCannotCancelReplacementSessionWithoutNewRootDown() throws Exception {
        DragShareController controller = controller();
        Object original = session("original", 600L);
        Object replacement = session("portal_replacement", 700L);
        set(controller, "session", original);
        set(controller, "active", true);
        try {
            controller.onHostTaskCancelled();
            ShadowLooper.shadowMainLooper().idleFor(16, TimeUnit.MILLISECONDS);
            set(controller, "session", replacement);
            ShadowLooper.shadowMainLooper().idleFor(16, TimeUnit.MILLISECONDS);

            assertTrue(controller.isActive());
            assertFalse((boolean) get(replacement, "cancelled"));
            assertTrue(messages().contains("reason=stale_root_owner"));
        } finally {
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void delayedHostCancelFromFinishedSessionCannotCancelBInSameRootGeneration() throws Exception {
        DragShareController controller = controller();
        Object owner = session("owner", 650L);
        Object replacement = session("same_generation_replacement", 660L);
        set(controller, "rootDownTime", 650L);
        set(controller, "session", owner);
        set(controller, "active", true);
        try {
            long generation = (long) get(controller, "rootOperationGeneration");
            controller.onHostTaskCancelled();
            ShadowLooper.shadowMainLooper().idleFor(16, TimeUnit.MILLISECONDS);
            controller.acceptPointerEvent(MotionEvent.ACTION_UP, 505f, 1005f, 670L, "root");
            ShadowLooper.shadowMainLooper().idle();
            assertFalse(controller.isActive());
            assertTrue((boolean) get(owner, "cancelled"));

            // Model a Portal payload replacing the completed Session without a new Root DOWN.
            set(controller, "session", replacement);
            set(controller, "active", true);
            assertTrue((long) get(controller, "rootOperationGeneration") == generation);
            ShadowLooper.shadowMainLooper().idleFor(16, TimeUnit.MILLISECONDS);

            assertTrue(controller.isActive());
            assertFalse((boolean) get(replacement, "cancelled"));
            assertTrue(messages().contains("reason=stale_root_owner"));
        } finally {
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void delayedHostCancelWithNoSessionCannotCancelLaterSameGenerationSession() throws Exception {
        DragShareController controller = controller();
        Object later = session("same_generation_later", 800L);
        try {
            long generation = (long) get(controller, "rootOperationGeneration");
            controller.onHostTaskCancelled();
            ShadowLooper.shadowMainLooper().idleFor(16, TimeUnit.MILLISECONDS);
            set(controller, "session", later);
            set(controller, "active", true);
            assertTrue((long) get(controller, "rootOperationGeneration") == generation);
            ShadowLooper.shadowMainLooper().idleFor(16, TimeUnit.MILLISECONDS);

            assertTrue(controller.isActive());
            assertFalse((boolean) get(later, "cancelled"));
            assertTrue(messages().contains("reason=stale_root_owner"));
        } finally {
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void delayedHostCancelAfterOwnerFinishesDoesNotRepeatCurrentCleanup() throws Exception {
        DragShareController controller = controller();
        Object owner = session("finished_owner", 900L);
        set(controller, "rootDownTime", 900L);
        set(controller, "session", owner);
        set(controller, "active", true);
        try {
            controller.onHostTaskCancelled();
            ShadowLooper.shadowMainLooper().idleFor(16, TimeUnit.MILLISECONDS);
            controller.acceptPointerEvent(MotionEvent.ACTION_UP,
                    505f, 1005f, 920L, "root");
            ShadowLooper.shadowMainLooper().idle();
            assertFalse(controller.isActive());
            assertTrue((boolean) get(owner, "cancelled"));

            BackgroundTouchBlocker blocker = (BackgroundTouchBlocker) get(controller, "backgroundTouchBlocker");
            DisposeMarker marker = new DisposeMarker();
            set(blocker, "inputMonitor", marker);
            set(blocker, "started", true);
            ShadowLooper.shadowMainLooper().idleFor(16, TimeUnit.MILLISECONDS);

            assertTrue("finished owner must not stop a later/current blocker",
                    get(blocker, "inputMonitor") == marker);
            assertTrue((boolean) get(blocker, "started"));
            assertFalse(messages().contains("reason=remove_views"));
            assertTrue(messages().contains("reason=owner_already_finished"));
        } finally {
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void oldHostCancelCannotRemoveNewRootSessionsPreviewView() throws Exception {
        DragShareController controller = controller();
        Object owner = session("owner", 1000L);
        Object replacement = session("replacement", 1100L);
        set(controller, "rootDownTime", 1000L);
        set(controller, "session", owner);
        set(controller, "active", true);
        try {
            controller.onHostTaskCancelled();
            ShadowLooper.shadowMainLooper().idleFor(16, TimeUnit.MILLISECONDS);
            controller.acceptPointerEvent(MotionEvent.ACTION_UP,
                    505f, 1005f, 1020L, "root");
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN,
                    600f, 1200f, 1100L, "root");
            ShadowLooper.shadowMainLooper().idle();
            set(controller, "session", replacement);
            set(controller, "active", true);
            android.view.View replacementPreview = new android.view.View(RuntimeEnvironment.getApplication());
            set(controller, "previewView", replacementPreview);
            ShadowLooper.shadowMainLooper().idleFor(16, TimeUnit.MILLISECONDS);

            assertTrue(controller.isActive());
            assertFalse((boolean) get(replacement, "cancelled"));
            assertTrue(get(controller, "previewView") == replacementPreview);
        } finally {
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void queuedRootUpCompletesOldSessionBeforeNewDownStarts() throws Exception {
        assertQueuedRootTerminalKeepsLaterSessionActive(MotionEvent.ACTION_UP, 120L, "root_up");
    }

    @Test public void queuedRootCancelCancelsOldSessionBeforeNewDownStarts() throws Exception {
        assertQueuedRootTerminalKeepsLaterSessionActive(MotionEvent.ACTION_CANCEL, 120L, "root_cancel");
    }

    private static void assertQueuedRootTerminalKeepsLaterSessionActive(
            int terminalAction, long terminalTime, String expectedReason) throws Exception {
        DragShareController controller = controller();
        Object owner = session("queued_owner", 100L);
        Object later = session("queued_later", 200L);
        set(controller, "rootDownTime", 100L);
        set(controller, "session", owner);
        set(controller, "active", true);
        try {
            controller.acceptPointerEvent(terminalAction, 510f, 1010f, terminalTime, "root");
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 600f, 1200f, 200L, "root");
            postSessionOnMain(controller, later);
            ShadowLooper.shadowMainLooper().idle();

            assertFalse("old terminal event must finish its session", controller.isActive() && get(controller, "session") == owner);
            assertTrue((boolean) get(owner, "cancelled"));
            assertTrue(controller.isActive());
            assertTrue(get(controller, "session") == later);
            assertFalse((boolean) get(later, "cancelled"));
            assertTrue(messages().contains("reason=" + expectedReason));
        } finally {
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    private static void postSessionOnMain(DragShareController controller, Object nextSession) throws Exception {
        android.os.Handler handler = (android.os.Handler) get(controller, "mainHandler");
        handler.post(() -> {
            try {
                set(controller, "session", nextSession);
                set(controller, "active", true);
            } catch (Exception error) {
                throw new AssertionError(error);
            }
        });
    }

    public static final class DisposeMarker {
        public void dispose() {}
    }

    @Test public void rootOwnerGenerationAdvancesOnlyForAcceptedRootDown() throws Exception {
        DragShareController controller = controller();
        try {
            assertTrue((long) get(controller, "rootOperationGeneration") == 0L);
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 500f, 1000f, 100L, "root");
            // Reader-thread enqueue must not itself advance the serial owner.
            assertTrue((long) get(controller, "rootOperationGeneration") == 0L);
            ShadowLooper.shadowMainLooper().idle();
            assertTrue((long) get(controller, "rootOperationGeneration") == 1L);

            controller.acceptPointerEvent(MotionEvent.ACTION_POINTER_DOWN,
                    510f, 1010f, 110L, "root");
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN,
                    520f, 1020f, 120L, "portal");
            ShadowLooper.shadowMainLooper().idle();
            assertTrue((long) get(controller, "rootOperationGeneration") == 1L);
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

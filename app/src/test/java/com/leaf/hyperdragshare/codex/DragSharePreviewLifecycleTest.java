package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewTreeObserver;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class DragSharePreviewLifecycleTest {
    @Test public void missingTerminalInputCancelsStaleSessionAndReleasesItsOwner() throws Exception {
        DragShareController controller = controller();
        Object session = session("stale", 100L);
        set(controller, "active", true);
        set(controller, "session", session);
        invoke(controller, "refreshStaleInputWatchdog", new Class<?>[0]);

        ShadowLooper.shadowMainLooper().idleFor(
                DragShareController.STALE_INPUT_TIMEOUT_MS - 1, java.util.concurrent.TimeUnit.MILLISECONDS);
        assertTrue(controller.isActive());
        ShadowLooper.shadowMainLooper().idleFor(1, java.util.concurrent.TimeUnit.MILLISECONDS);

        assertFalse(controller.isActive());
        assertTrue((boolean) get(session, "cancelled"));
        assertNull(get(controller, "staleInputWatchdog"));
        controller.destroy();
        ShadowLooper.shadowMainLooper().idle();
    }

    @Test public void oldWatchdogCannotCancelReplacementSession() throws Exception {
        DragShareController controller = controller();
        Object first = session("first", 100L);
        Object replacement = session("replacement", 200L);
        set(controller, "active", true);
        set(controller, "session", first);
        invoke(controller, "refreshStaleInputWatchdog", new Class<?>[0]);
        Runnable oldWatchdog = (Runnable) get(controller, "staleInputWatchdog");

        set(controller, "session", replacement);
        invoke(controller, "refreshStaleInputWatchdog", new Class<?>[0]);
        oldWatchdog.run();

        assertTrue(controller.isActive());
        assertFalse((boolean) get(replacement, "cancelled"));
        assertTrue(get(controller, "staleInputWatchdog") != oldWatchdog);
        controller.destroy();
        ShadowLooper.shadowMainLooper().idle();
    }

    @Test public void endingBeforePostedQueryStartCannotStartWorkLater() throws Exception {
        DragShareController controller = controller();
        View preview = new View(RuntimeEnvironment.getApplication());
        Object session = session("first", 100L);
        preparePreview(controller, preview, session);

        Runnable queuedStart = dispatchPreviewDraw(controller);
        set(controller, "active", false);
        invoke(controller, "removeGestureViews", new Class<?>[]{boolean.class}, false);
        queuedStart.run();

        assertNull(task(controller));
        assertNull(get(controller, "targetQueryPreviewView"));
        assertNull(get(controller, "targetQueryStartRunnable"));
        controller.destroy();
        ShadowLooper.shadowMainLooper().idle();
    }

    @Test public void oldPreviewCallbackCannotStartWorkForReplacementSession() throws Exception {
        DragShareController controller = controller();
        View firstPreview = new View(RuntimeEnvironment.getApplication());
        Object firstSession = session("first", 100L);
        preparePreview(controller, firstPreview, firstSession);
        Runnable oldStart = dispatchPreviewDraw(controller);

        View replacementPreview = new View(RuntimeEnvironment.getApplication());
        Object replacementSession = session("replacement", 200L);
        set(controller, "session", replacementSession);
        set(controller, "previewView", replacementPreview);
        set(controller, "targetQueryRequested", false);
        invoke(controller, "loadTargetsAndCreateMenu",
                new Class<?>[]{firstSession.getClass(), CapturedContent.class, float.class, float.class},
                replacementSession, CapturedContent.text("replacement", "test.app", null), 20f, 30f);
        Runnable replacementStart = dispatchPreviewDraw(controller);

        oldStart.run();

        assertNull(task(controller));
        if (replacementStart != null) {
            // The current operation remains owner of its own pending callback.
            assertSame(replacementStart, get(controller, "targetQueryStartRunnable"));
        }
        set(controller, "active", false);
        invoke(controller, "removeGestureViews", new Class<?>[]{boolean.class}, false);
        controller.destroy();
        ShadowLooper.shadowMainLooper().idle();
    }

    private static DragShareController controller() {
        return new DragShareController(RuntimeEnvironment.getApplication(), OverlayWindowPolicy.portal());
    }

    private static Object session(String text, long downTime) throws Exception {
        Class<?> type = Class.forName(DragShareController.class.getName() + "$Session");
        var constructor = type.getDeclaredConstructor(CapturedContent.class, long.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(CapturedContent.text(text, "test.app", null), downTime, OperationTrace.id("press", downTime));
    }

    private static void preparePreview(DragShareController controller, View preview, Object session)
            throws Exception {
        set(controller, "active", true);
        set(controller, "session", session);
        set(controller, "previewView", preview);
        invoke(controller, "loadTargetsAndCreateMenu",
                new Class<?>[]{session.getClass(), CapturedContent.class, float.class, float.class},
                session, CapturedContent.text("private test text", "test.app", null), 10f, 20f);
    }

    private static Runnable dispatchPreviewDraw(DragShareController controller) throws Exception {
        ViewTreeObserver.OnDrawListener listener =
                (ViewTreeObserver.OnDrawListener) get(controller, "targetQueryDrawListener");
        listener.onDraw();
        return (Runnable) get(controller, "targetQueryStartRunnable");
    }

    private static Object task(DragShareController controller) throws Exception {
        Object slot = get(controller, "targetQueryTask");
        return get(slot, "task");
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
        Method method = object.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        method.invoke(object, args);
    }

    private static void assertSame(Object expected, Object actual) {
        org.junit.Assert.assertSame(expected, actual);
    }
}

package com.leaf.hyperdragshare.gestures.xposed;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewConfiguration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public final class ImeFreeformLifecycleTest {
    @Test
    public void showingImeCancelsPendingOutsideCloseAndHidingRestoresIt() {
        int[] actions = {0};
        OutsideGestureRecognizer recognizer = recognizer(actions);
        Rect window = new Rect(200, 300, 800, 900);

        tap(recognizer, window, 1000);
        recognizer.setInputMethodVisible(true);
        ShadowLooper.shadowMainLooper().idleFor(1, TimeUnit.SECONDS);
        assertEquals("pending outside tap must not close a window after IME appears",
                0, actions[0]);

        tap(recognizer, window, 2500);
        ShadowLooper.shadowMainLooper().idleFor(1, TimeUnit.SECONDS);
        assertEquals("ordinary outside taps stay suppressed while IME is visible",
                0, actions[0]);

        int[] hideRequests = {0};
        recognizer.onDown(50, 50, window, 1200, 0, 0, 4000);
        recognizer.onUpImmediate(50, 50, 4020, null, () -> hideRequests[0]++);
        assertEquals("the explicit outside-to-hide-IME path remains available",
                1, hideRequests[0]);

        recognizer.setInputMethodVisible(false);
        tap(recognizer, window, 5000);
        ShadowLooper.shadowMainLooper().idleFor(1, TimeUnit.SECONDS);
        assertEquals("original outside close behavior resumes after IME hides",
                1, actions[0]);
    }

    @Test
    public void restoreBoundsKeepImeResizedInputAreaInsideTheWindow() {
        Rect current = new Rect(1200, 100, 2300, 700);
        Rect restore = new Rect(900, 80, 2400, 1000);

        assertEquals(current, HyperOsFreeformBridge.includeRestoreBounds(
                current, restore, 1f, false));
        assertEquals(new Rect(900, 80, 2400, 1000),
                HyperOsFreeformBridge.includeRestoreBounds(current, restore, 1f, true));
    }

    @Test
    public void landscapeOutsideCaptureDoesNotCoverImeOrRestoredFreeformArea() {
        Rect display = new Rect(0, 0, 2400, 1080);
        Rect imeResizedWindow = new Rect(1400, 60, 2300, 600);
        Rect restoredWindow = new Rect(1300, 60, 2360, 820);
        Rect window = HyperOsFreeformBridge.includeRestoreBounds(
                imeResizedWindow, restoredWindow, 1f, true);
        Rect ime = new Rect(0, 760, 2400, 1080);

        List<Rect> regions = OutsideTouchCapture.outsideRegions(
                display, List.of(window), ime, 0, 0);
        assertTrue("other space above the keyboard retains outside-tap capture",
                contains(regions, 300, 300));
        assertFalse("restored input content is excluded from outside capture",
                contains(regions, 1500, 700));
        assertFalse("IME surface is excluded from outside capture",
                contains(regions, 300, 900));
        assertFalse("space below the IME inset is not intercepted",
                contains(regions, 300, 1060));
    }

    @Test
    public void floatingImeWithoutReliableBoundsDisablesOutsideCapture() {
        assertFalse(FanRuntime.shouldCaptureOutsideWithIme(true, null));
        assertTrue(FanRuntime.shouldCaptureOutsideWithIme(true,
                new Rect(0, 760, 2400, 1080)));
        assertTrue(FanRuntime.shouldCaptureOutsideWithIme(false, null));
    }

    private static OutsideGestureRecognizer recognizer(int[] actions) {
        return new OutsideGestureRecognizer(new Handler(Looper.getMainLooper()),
                ViewConfiguration.get(RuntimeEnvironment.getApplication()), 1f,
                doubleTap -> actions[0]++);
    }

    private static void tap(OutsideGestureRecognizer recognizer, Rect window, long downTime) {
        assertTrue(recognizer.onDown(50, 50, window, 1200, 0, 0, downTime));
        recognizer.onUp(50, 50, downTime + 20, null);
    }

    private static boolean contains(List<Rect> regions, int x, int y) {
        for (Rect region : regions) {
            if (region.contains(x, y)) return true;
        }
        return false;
    }
}

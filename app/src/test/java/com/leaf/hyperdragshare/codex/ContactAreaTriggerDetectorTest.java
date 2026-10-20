package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class ContactAreaTriggerDetectorTest {
    private static final float THRESHOLD = 10f;

    @Test
    public void ordinarySmallContactLongPressDoesNotTrigger() {
        ContactAreaTriggerDetector detector = detector(100L);

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 0L, 3, 3));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 1_000L, 6, 6));
        assertFalse(event(detector, MotionEvent.ACTION_UP, 1_500L, 0, 0));
    }

    @Test
    public void missingContactDimensionsDoNotStartConfirmation() {
        ContactAreaTriggerDetector detector = detector(0L);

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 0L, 0, 0));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 1L, 25, 0));
        assertFalse(event(detector, MotionEvent.ACTION_UP, 2L, 0, 25));
    }

    @Test
    public void contactAtThresholdBoundaryTriggersAfterConfirmation() {
        ContactAreaTriggerDetector detector = detector(100L);

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 0L, 20, 5));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 99L, 20, 5));
        assertTrue(event(detector, MotionEvent.ACTION_UP, 100L, 0, 0));
    }

    @Test
    public void shortPeakDoesNotSatisfyConfirmation() {
        ContactAreaTriggerDetector detector = detector(100L);

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 0L, 3, 3));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 20L, 25, 5));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 60L, 3, 3));
        assertFalse(event(detector, MotionEvent.ACTION_UP, 200L, 0, 0));
    }

    @Test
    public void sizeDropDuringConfirmationRestartsTheTimer() {
        ContactAreaTriggerDetector detector = detector(100L);

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 0L, 3, 3));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 10L, 25, 5));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 80L, 3, 3));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 90L, 25, 5));
        assertFalse(event(detector, MotionEvent.ACTION_UP, 189L, 0, 0));
    }

    @Test
    public void movementPastTouchSlopRejectsTheGesture() {
        ContactAreaTriggerDetector detector = detector(0L);

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 0L, 25, 5));
        assertFalse(detector.onPointerEvent(MotionEvent.ACTION_MOVE, 8f, 8f, 1L, 25, 5));
        assertFalse(detector.onPointerEvent(MotionEvent.ACTION_UP, 8f, 8f, 2L, 25, 5));
    }

    @Test
    public void cancelRejectsButZeroSizeOnUpKeepsConfirmedContact() {
        ContactAreaTriggerDetector detector = detector(100L);

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 0L, 25, 5));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 100L, 25, 5));
        assertFalse(event(detector, MotionEvent.ACTION_CANCEL, 110L, 0, 0));
        assertFalse(event(detector, MotionEvent.ACTION_UP, 120L, 25, 5));

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 200L, 25, 5));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 300L, 25, 5));
        assertTrue(event(detector, MotionEvent.ACTION_UP, 310L, 0, 0));
    }

    @Test
    public void eachGestureStartsWithFreshConfirmationState() {
        ContactAreaTriggerDetector detector = detector(100L);

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 0L, 25, 5));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 100L, 25, 5));
        assertTrue(event(detector, MotionEvent.ACTION_UP, 110L, 25, 5));
        assertFalse(event(detector, MotionEvent.ACTION_UP, 111L, 25, 5));

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 200L, 3, 3));
        assertFalse(event(detector, MotionEvent.ACTION_UP, 300L, 25, 5));
    }

    private static ContactAreaTriggerDetector detector(long confirmationMillis) {
        return new ContactAreaTriggerDetector(THRESHOLD, 10f, confirmationMillis);
    }

    private static boolean event(
            ContactAreaTriggerDetector detector,
            int action,
            long time,
            int major,
            int minor) {
        return detector.onPointerEvent(action, 0f, 0f, time, major, minor);
    }
}

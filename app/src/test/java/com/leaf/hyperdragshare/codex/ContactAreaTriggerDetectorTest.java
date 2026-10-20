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
    private static final float THRESHOLD = .5f;

    @Test
    public void widthOnlyCalibrationAndTriggerUseTheSameNormalizedMetric() {
        double small = ThumbCalibration.contactSize(0, 255, 20, 100);
        double large = ThumbCalibration.contactSize(0, 255, 60, 100);
        ThumbCalibration.Result result = ThumbCalibration.calculate(
                new double[]{small}, new double[]{large});
        ContactAreaTriggerDetector detector = new ContactAreaTriggerDetector(
                (float) result.threshold, 10f, 100L);
        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 0L, (float) small));
        assertFalse(event(detector, MotionEvent.ACTION_UP, 200L, (float) small));
        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 300L, (float) large));
        assertTrue(event(detector, MotionEvent.ACTION_UP, 400L, (float) large));
    }

    @Test
    public void ordinarySmallContactLongPressDoesNotTrigger() {
        ContactAreaTriggerDetector detector = detector(100L);

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 0L, .1f));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 1_000L, .2f));
        assertFalse(event(detector, MotionEvent.ACTION_UP, 1_500L, 0f));
    }

    @Test
    public void missingContactMetricDoesNotStartConfirmation() {
        ContactAreaTriggerDetector detector = detector(0L);

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 0L, 0f));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 1L, 0f));
        assertFalse(event(detector, MotionEvent.ACTION_UP, 2L, 0f));
    }

    @Test
    public void contactAtThresholdBoundaryTriggersAfterConfirmation() {
        ContactAreaTriggerDetector detector = detector(100L);

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 0L, .5f));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 99L, .5f));
        assertTrue(event(detector, MotionEvent.ACTION_UP, 100L, 0f));
    }

    @Test
    public void shortPeakDoesNotSatisfyConfirmation() {
        ContactAreaTriggerDetector detector = detector(100L);

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 0L, .1f));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 20L, .6f));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 60L, .1f));
        assertFalse(event(detector, MotionEvent.ACTION_UP, 200L, 0f));
    }

    @Test
    public void sizeDropDuringConfirmationRestartsTheTimer() {
        ContactAreaTriggerDetector detector = detector(100L);

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 0L, .1f));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 10L, .6f));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 80L, .1f));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 90L, .6f));
        assertFalse(event(detector, MotionEvent.ACTION_UP, 189L, 0f));
    }

    @Test
    public void movementPastTouchSlopRejectsTheGesture() {
        ContactAreaTriggerDetector detector = detector(0L);

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 0L, .6f));
        assertFalse(detector.onPointerEvent(MotionEvent.ACTION_MOVE, 8f, 8f, 1L, .6f));
        assertFalse(detector.onPointerEvent(MotionEvent.ACTION_UP, 8f, 8f, 2L, .6f));
    }

    @Test
    public void cancelRejectsButZeroSizeOnUpKeepsConfirmedContact() {
        ContactAreaTriggerDetector detector = detector(100L);

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 0L, .6f));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 100L, .6f));
        assertFalse(event(detector, MotionEvent.ACTION_CANCEL, 110L, 0f));
        assertFalse(event(detector, MotionEvent.ACTION_UP, 120L, .6f));

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 200L, .6f));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 300L, .6f));
        assertTrue(event(detector, MotionEvent.ACTION_UP, 310L, 0f));
    }

    @Test
    public void eachGestureStartsWithFreshConfirmationState() {
        ContactAreaTriggerDetector detector = detector(100L);

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 0L, .6f));
        assertFalse(event(detector, MotionEvent.ACTION_MOVE, 100L, .6f));
        assertTrue(event(detector, MotionEvent.ACTION_UP, 110L, .6f));
        assertFalse(event(detector, MotionEvent.ACTION_UP, 111L, .6f));

        assertFalse(event(detector, MotionEvent.ACTION_DOWN, 200L, .1f));
        assertFalse(event(detector, MotionEvent.ACTION_UP, 300L, .6f));
    }

    private static ContactAreaTriggerDetector detector(long confirmationMillis) {
        return new ContactAreaTriggerDetector(THRESHOLD, 10f, confirmationMillis);
    }

    private static boolean event(
            ContactAreaTriggerDetector detector,
            int action,
            long time,
            float size) {
        return detector.onPointerEvent(action, 0f, 0f, time, size);
    }
}

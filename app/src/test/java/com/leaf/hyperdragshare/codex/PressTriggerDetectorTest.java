package com.leaf.hyperdragshare.codex;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PressTriggerDetectorTest {
    @Test public void stationaryPressClassifiesOnceAndConfirmsAtConfiguredTime() {
        PressTriggerDetector detector = down(300L);
        assertFalse(detector.takeClassification(1119L));
        assertTrue(detector.takeClassification(1120L));
        assertFalse(detector.takeClassification(1121L));
        assertFalse(detector.fire(1299L));
        assertTrue(detector.fire(1300L));
        assertFalse(detector.fire(1400L));
        assertFalse(detector.takeClassification(1400L));
    }

    @Test public void configuredDurationIsNotReplacedByAContentClassificationShortcut() {
        PressTriggerDetector detector = down(700L);
        assertTrue(detector.takeClassification(1120L));
        assertEquals(1700L, detector.deadline());
        assertFalse(detector.fire(1300L));
        assertTrue(detector.fire(1700L));
    }

    @Test public void movementIsMeasuredFromDownPerAxis() {
        PressTriggerDetector detector = down(300L);
        detector.onPointerEvent(2, 46f, 56f, 1);
        assertTrue(detector.isActive());
        detector.onPointerEvent(2, 46.01f, 20f, 1);
        assertFalse(detector.isActive());
        assertFalse(detector.fire(1300L));
    }

    @Test public void releaseCancelAndSecondPointerPreventConfirmation() {
        for (int action : new int[]{1, 3, 5, 6}) {
            PressTriggerDetector detector = down(300L);
            detector.onPointerEvent(action, 10f, 20f, action == 5 ? 2 : 1);
            assertFalse(detector.takeClassification(1200L));
            assertFalse(detector.fire(2000L));
        }
        PressTriggerDetector detector = down(300L);
        detector.onPointerEvent(2, 10f, 20f, 2);
        assertFalse(detector.fire(1300L));
    }

    @Test public void nextDownUsesTheNewDurationAndResetsFiredState() {
        PressTriggerDetector detector = down(300L);
        assertTrue(detector.fire(1300L));
        detector.onPointerEvent(1, 10f, 20f, 1);
        detector.start(30f, 40f, 2000L, 650L);
        assertTrue(detector.isActive());
        assertFalse(detector.isFired());
        assertEquals(2000L, detector.downTime());
        assertFalse(detector.fire(2649L));
        assertTrue(detector.fire(2650L));
    }

    @Test public void movementAfterConfirmationRemainsAvailableForImageDrag() {
        PressTriggerDetector detector = down(300L);
        assertTrue(detector.fire(1300L));
        detector.onPointerEvent(2, 400f, 600f, 1);
        assertTrue(detector.isActive());
        assertTrue(detector.isFired());
        detector.onPointerEvent(1, 400f, 600f, 1);
        assertFalse(detector.isActive());
    }

    @Test public void twoFingerCandidateConfirmsOnlyAfterItsOwnConfiguredDuration() {
        PressTriggerDetector detector = new PressTriggerDetector();
        detector.startTwoFinger(10f, 20f, 40f, 50f, 2000L, 700L, 8f);

        assertFalse(detector.takeClassification(2119L));
        assertTrue(detector.takeClassification(2120L));
        assertFalse(detector.fire(2699L));
        assertTrue(detector.fire(2700L));
        assertTrue(detector.isTwoFinger());
    }

    @Test public void firstContactCandidateWaitsWithoutClassificationOrFireUntilSecondFingerJoins() {
        PressTriggerDetector detector = new PressTriggerDetector();
        detector.startTwoFingerCandidate(10f, 20f, 2000L, 600L);

        assertTrue(detector.isAwaitingSecondFinger());
        assertFalse(detector.takeClassification(2120L));
        assertFalse(detector.fire(2600L));
        assertTrue(detector.isActive());

        detector.startTwoFinger(10f, 20f, 40f, 50f, 2700L, 700L, 8f);
        assertFalse(detector.isAwaitingSecondFinger());
        assertFalse(detector.fire(3399L));
        assertTrue(detector.fire(3400L));
        assertTrue(detector.isTwoFinger());
    }

    @Test public void movementOfEitherFingerCancelsTwoFingerCandidateUsingProvidedSlop() {
        PressTriggerDetector first = twoFinger(8f);
        first.onTwoPointerEvent(2, 19f, 20f, 40f, 50f, 2);
        assertFalse(first.isActive());

        PressTriggerDetector second = twoFinger(8f);
        second.onTwoPointerEvent(2, 10f, 20f, 40f, 58.01f, 2);
        assertFalse(second.isActive());
        assertFalse(second.fire(3000L));
    }

    @Test public void losingEitherFingerThirdFingerAndTerminalEventsCancelCandidate() {
        for (int count : new int[]{0, 1, 3}) {
            PressTriggerDetector detector = twoFinger(8f);
            detector.onTwoPointerEvent(2, 10f, 20f, 40f, 50f, count);
            assertFalse(detector.isActive());
            assertFalse(detector.fire(3000L));
        }
        for (int action : new int[]{1, 3, 6}) {
            PressTriggerDetector detector = twoFinger(8f);
            detector.onTwoPointerEvent(action, 10f, 20f, 40f, 50f, 2);
            assertFalse(detector.isActive());
        }
    }

    @Test public void confirmedTwoFingerGestureRemainsActiveForRootOwnedDragMovement() {
        PressTriggerDetector detector = twoFinger(8f);
        assertTrue(detector.fire(1700L));
        detector.onTwoPointerEvent(2, 100f, 200f, 140f, 250f, 2);
        assertTrue(detector.isActive());
        assertTrue(detector.isFired());
    }

    private static PressTriggerDetector twoFinger(float slop) {
        PressTriggerDetector detector = new PressTriggerDetector();
        detector.startTwoFinger(10f, 20f, 40f, 50f, 1000L, 700L, slop);
        return detector;
    }

    private static PressTriggerDetector down(long duration) {
        PressTriggerDetector detector = new PressTriggerDetector();
        detector.start(10f, 20f, 1000L, duration);
        return detector;
    }
}

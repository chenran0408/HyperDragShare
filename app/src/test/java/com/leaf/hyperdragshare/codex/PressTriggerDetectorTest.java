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

    private static PressTriggerDetector down(long duration) {
        PressTriggerDetector detector = new PressTriggerDetector();
        detector.start(10f, 20f, 1000L, duration);
        return detector;
    }
}

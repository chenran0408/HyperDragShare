package com.leaf.hyperdragshare.codex;

import org.junit.Test;

/** Timing and cancellation behavior of the reference press gesture. */
public final class PressTriggerDetectorTest {
    @Test public void ordinaryTextKeeps700ms() {
        PressTriggerDetector detector = down(0f, 0f);
        check(!detector.isAreaPress(), "zero area is ordinary press");
        check(detector.classificationDeadline() == 1120L, "ordinary classification at DOWN+120");
        check(!detector.takeClassification(1119L), "classification cannot start early");
        check(detector.takeClassification(1120L), "classification becomes due");
        check(!detector.takeClassification(1121L), "classification is claimed once");
        detector.onClassification(true);
        check(!detector.fire(1699L) && detector.fire(1700L), "ordinary text waits 700ms");
        check(!detector.fire(1800L) && detector.isFired(), "press fires once");
    }

    @Test public void safeNoTextUses300ms() {
        PressTriggerDetector detector = down(0f, 0f);
        check(detector.takeClassification(1120L), "start classification");
        detector.onClassification(false);
        check(!detector.isAreaPress(), "no-text shortcut does not claim area input");
        check(!detector.fire(1299L) && detector.fire(1300L), "safe no-text waits DOWN+300");
    }

    @Test public void bothAreaAxesUse220ms() {
        for (float[] axes : new float[][]{{0.30f, 0f}, {0f, 0.095f}}) {
            PressTriggerDetector detector = down(axes[0], axes[1]);
            check(detector.isAreaPress(), "either inclusive threshold is sufficient");
            check(detector.classificationDeadline() == -1L, "area path needs no ordinary classification");
            detector.onPointerEvent(2, 10f, 20f, 1100L, 0f, 0f, 1);
            check(!detector.fire(1219L) && detector.fire(1220L), "area peak survives smaller samples");
        }
    }

    @Test public void lateAreaPeakUsesOriginalDownTime() {
        PressTriggerDetector detector = down(0f, 0f);
        detector.onPointerEvent(2, 10f, 20f, 1250L, 0.30f, 0f, 1);
        check(detector.deadline() == 1220L && detector.fire(1250L), "late peak is immediately due");
    }

    @Test public void classificationCannotDelayArea() {
        PressTriggerDetector detector = down(0f, 0f);
        check(detector.takeClassification(1120L), "classification starts before peak");
        detector.onPointerEvent(2, 10f, 20f, 1150L, 0f, 0.095f, 1);
        detector.onClassification(false);
        check(detector.deadline() == 1220L && detector.fire(1220L), "late no-text result cannot extend 220ms");
    }

    @Test public void movementIsPerAxis() {
        PressTriggerDetector detector = down(0.30f, 0f);
        detector.onPointerEvent(2, 46f, 56f, 1100L, 0f, 0f, 1);
        check(detector.isActive(), "36px diagonal is allowed despite larger Euclidean distance");
        detector.onPointerEvent(2, 46.01f, 20f, 1150L, 0.30f, 0f, 1);
        check(!detector.isActive() && !detector.fire(1300L), "more than 36px on one axis cancels");
    }

    @Test public void releaseAndMultiplePointersCancel() {
        for (int action : new int[]{1, 3, 5, 6}) {
            PressTriggerDetector detector = down(0f, 0f);
            detector.onPointerEvent(action, 10f, 20f, 1100L, 0f, 0f, action == 5 ? 2 : 1);
            detector.onClassification(false);
            check(!detector.takeClassification(1200L) && !detector.fire(2000L), "ended press cannot classify or fire");
        }
        PressTriggerDetector detector = down(0.30f, 0f);
        detector.onPointerEvent(2, 10f, 20f, 1100L, 0.30f, 0f, 2);
        check(!detector.fire(1220L), "multiple pointers cancel even when reported as MOVE");
    }

    @Test public void nextPressResetsTheSession() {
        PressTriggerDetector detector = down(0.30f, 0f);
        check(detector.fire(1220L), "first area press fires");
        detector.onPointerEvent(1, 10f, 20f, 1250L, 0f, 0f, 1);
        detector.onPointerEvent(0, 30f, 40f, 2000L, 0f, 0f, 1);
        check(detector.isActive() && !detector.isFired() && !detector.isAreaPress(), "new DOWN resets state");
        check(detector.downTime() == 2000L && detector.deadline() == 2700L, "new deadline uses new DOWN");
    }

    private static PressTriggerDetector down(float touch, float width) {
        PressTriggerDetector detector = new PressTriggerDetector();
        detector.onPointerEvent(0, 10f, 20f, 1000L, touch, width, 1);
        return detector;
    }

    private static void check(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
}

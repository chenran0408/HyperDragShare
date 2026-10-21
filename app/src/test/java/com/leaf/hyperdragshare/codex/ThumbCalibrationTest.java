package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ThumbCalibrationTest {
    @Test
    public void thresholdSplitsObservedFingertipAndThumbRanges() {
        ThumbCalibration.Result result = ThumbCalibration.calculate(
                new double[]{8d, 9d, 10d}, new double[]{15d, 16d, 18d});

        assertTrue(result.calibrated);
        assertEquals(12.5d, result.threshold, 0.001d);
        assertEquals(10d, result.fingertipMaximum, 0.001d);
        assertEquals(15d, result.thumbMinimum, 0.001d);
    }

    @Test
    public void overlappingOrTouchingRangesRequireRetry() {
        assertFalse(ThumbCalibration.calculate(
                new double[]{8d, 12d}, new double[]{11d, 16d}).calibrated);
        assertFalse(ThumbCalibration.calculate(
                new double[]{8d, 12d}, new double[]{12d, 16d}).calibrated);
    }

    @Test
    public void missingOrInvalidSamplesCannotProduceThreshold() {
        assertFalse(ThumbCalibration.calculate(new double[0], new double[]{12d}).calibrated);
        assertFalse(ThumbCalibration.calculate(new double[]{0d}, new double[]{12d}).calibrated);
        assertFalse(ThumbCalibration.calculate(new double[]{Double.NaN}, new double[]{12d}).calibrated);
    }

    @Test
    public void contactSizeAcceptsEitherMajorAxisAndUsesItsOwnMaximum() {
        assertEquals(0d, ThumbCalibration.contactSize(0, 100, 0, 255), 0d);
        assertEquals(.5d, ThumbCalibration.contactSize(50, 100, 0, 255), .0001d);
        assertEquals(.5d, ThumbCalibration.contactSize(0, 100, 128, 256), .0001d);
        assertEquals(.8d, ThumbCalibration.contactSize(50, 100, 80, 100), .0001d);
        assertEquals(0d, ThumbCalibration.contactSize(50, 0, 80, -1), 0d);
    }

    @Test public void defaultGatesKeepTouchAndWidthIndependentIncludingExactBoundary() {
        assertTrue(ThumbCalibration.triggerLevel(30, 100, 0, 1000, .30f, .095f) >= 1d);
        assertTrue(ThumbCalibration.triggerLevel(0, 100, 95, 1000, .30f, .095f) >= 1d);
        assertTrue(ThumbCalibration.triggerLevel(29, 100, 94, 1000, .30f, .095f) < 1d);
    }

    @Test public void missingAxesNeverBecomeContactAndCustomGateKeepsCalibrationUnits() {
        assertEquals(0d, ThumbCalibration.triggerLevel(0, 255, 0, 255, .30f, .095f), 0d);
        assertEquals(0d, ThumbCalibration.triggerLevel(50, 0, 20, -1, .30f, .095f), 0d);
        assertEquals(1d, ThumbCalibration.triggerLevel(0, 255, 40, 100, .4f, .4f), 0d);
    }
}

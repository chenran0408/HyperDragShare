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
    public void contactSizeRejectsZeroAndUsesGeometricMean() {
        assertEquals(0d, ThumbCalibration.contactSize(0, 20), 0d);
        assertEquals(10d, ThumbCalibration.contactSize(5, 20), 0.001d);
    }
}

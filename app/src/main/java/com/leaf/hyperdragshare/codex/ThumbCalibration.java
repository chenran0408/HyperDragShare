package com.leaf.hyperdragshare.codex;

import java.util.Arrays;

/** Calibrates a threshold for the larger of the two independently normalized contact axes. */
public final class ThumbCalibration {
    private ThumbCalibration() {}

    public static Result calculate(double[] fingertipSamples, double[] thumbSamples) {
        if (fingertipSamples == null || fingertipSamples.length == 0
                || thumbSamples == null || thumbSamples.length == 0) {
            return Result.retry();
        }
        if (!areValid(fingertipSamples) || !areValid(thumbSamples)) {
            return Result.retry();
        }
        double fingertipMax = Arrays.stream(fingertipSamples).max().orElse(0d);
        double thumbMin = Arrays.stream(thumbSamples).min().orElse(0d);
        if (fingertipMax <= 0d || thumbMin <= fingertipMax) {
            return Result.retry();
        }
        return new Result(true, (fingertipMax + thumbMin) / 2d, fingertipMax, thumbMin);
    }

    public static double contactSize(int rawTouchMajor, int maxTouchMajor,
                                     int rawWidthMajor, int maxWidthMajor) {
        double touch = rawTouchMajor > 0 && maxTouchMajor > 0
                ? Math.min(1d, (double) rawTouchMajor / maxTouchMajor) : 0d;
        double width = rawWidthMajor > 0 && maxWidthMajor > 0
                ? Math.min(1d, (double) rawWidthMajor / maxWidthMajor) : 0d;
        return Math.max(touch, width);
    }

    private static boolean areValid(double[] samples) {
        for (double sample : samples) {
            if (!(sample > 0d) || !Double.isFinite(sample)) {
                return false;
            }
        }
        return true;
    }

    public static final class Result {
        public final boolean calibrated;
        public final double threshold;
        public final double fingertipMaximum;
        public final double thumbMinimum;

        private Result(boolean calibrated, double threshold,
                       double fingertipMaximum, double thumbMinimum) {
            this.calibrated = calibrated;
            this.threshold = threshold;
            this.fingertipMaximum = fingertipMaximum;
            this.thumbMinimum = thumbMinimum;
        }

        private static Result retry() {
            return new Result(false, 0d, 0d, 0d);
        }
    }
}

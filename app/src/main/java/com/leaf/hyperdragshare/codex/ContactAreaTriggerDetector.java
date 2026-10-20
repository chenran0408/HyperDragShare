package com.leaf.hyperdragshare.codex;

import android.view.MotionEvent;

/** Detects a stationary contact whose evdev touch dimensions stay above a calibrated threshold. */
final class ContactAreaTriggerDetector {
    private final float threshold;
    private final float touchSlop;
    private final long confirmationMillis;

    private boolean gestureActive;
    private boolean rejected;
    private float downX;
    private float downY;
    private long thresholdSince = -1L;

    ContactAreaTriggerDetector(float threshold, float touchSlop, long confirmationMillis) {
        if (threshold <= 0f) {
            throw new IllegalArgumentException("threshold must be positive");
        }
        if (touchSlop < 0f) {
            throw new IllegalArgumentException("touchSlop must not be negative");
        }
        if (confirmationMillis < 0L) {
            throw new IllegalArgumentException("confirmationMillis must not be negative");
        }
        this.threshold = threshold;
        this.touchSlop = touchSlop;
        this.confirmationMillis = confirmationMillis;
    }

    boolean onPointerEvent(
            int action,
            float x,
            float y,
            long eventTime,
            int major,
            int minor) {
        if (action == MotionEvent.ACTION_DOWN) {
            reset();
            gestureActive = true;
            downX = x;
            downY = y;
            updateContactSize(major, minor, eventTime);
            return false;
        }
        if (!gestureActive) {
            return false;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            reset();
            return false;
        }
        if (action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_UP) {
            return false;
        }

        if (distanceFromDown(x, y) > touchSlop) {
            rejected = true;
        }
        if (rejected) {
            if (action == MotionEvent.ACTION_UP) {
                reset();
            }
            return false;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            updateContactSize(major, minor, eventTime);
            return false;
        }

        boolean confirmedBeforeRelease = isConfirmed(eventTime);
        if (hasContactSize(major, minor)
                && contactArea(major, minor) < threshold
                && !confirmedBeforeRelease) {
            thresholdSince = -1L;
        } else if (hasContactSize(major, minor) && thresholdSince < 0L) {
            thresholdSince = eventTime;
        }

        boolean confirmed = isConfirmed(eventTime);
        reset();
        return confirmed;
    }

    private void updateContactSize(int major, int minor, long eventTime) {
        if (!hasContactSize(major, minor) || contactArea(major, minor) < threshold) {
            thresholdSince = -1L;
        } else if (thresholdSince < 0L) {
            thresholdSince = eventTime;
        }
    }

    private boolean isConfirmed(long eventTime) {
        return thresholdSince >= 0L && eventTime - thresholdSince >= confirmationMillis;
    }

    private float distanceFromDown(float x, float y) {
        float dx = x - downX;
        float dy = y - downY;
        return (float) Math.sqrt((dx * dx) + (dy * dy));
    }

    private static boolean hasContactSize(int major, int minor) {
        return major > 0 && minor > 0;
    }

    private static float contactArea(int major, int minor) {
        return (float) Math.sqrt((double) major * minor);
    }

    private void reset() {
        gestureActive = false;
        rejected = false;
        thresholdSince = -1L;
    }
}

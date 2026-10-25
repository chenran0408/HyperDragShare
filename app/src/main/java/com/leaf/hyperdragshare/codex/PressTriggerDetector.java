package com.leaf.hyperdragshare.codex;

/** Root evdev press timing and movement, independent of contact-size reporting. */
public final class PressTriggerDetector {
    static final float MOVE_LIMIT = 36f;
    private boolean active;
    private boolean fired;
    private float downX;
    private float downY;
    private long downTime = -1L;
    private long deadline = -1L;
    private long classificationDeadline = -1L;
    private boolean awaitingSecondFinger;
    private boolean twoFinger;
    private float secondDownX;
    private float secondDownY;
    private float movementLimit = MOVE_LIMIT;

    public void start(float x, float y, long time, long confirmMillis) {
        awaitingSecondFinger = false;
        twoFinger = false;
        movementLimit = MOVE_LIMIT;
        active = true;
        fired = false;
        downX = x;
        downY = y;
        downTime = time;
        deadline = time + confirmMillis;
        classificationDeadline = time + 120L;
    }

    /** Starts the bounded first-contact window without scheduling a press trigger. */
    public void startTwoFingerCandidate(float x, float y, long time, long candidateMillis) {
        start(x, y, time, candidateMillis);
        awaitingSecondFinger = true;
        classificationDeadline = -1L;
    }

    /** Starts a two-contact candidate with an explicit screen-space slop. */
    public void startTwoFinger(float x, float y, float secondX, float secondY,
                               long time, long confirmMillis, float movementSlop) {
        start(x, y, time, confirmMillis);
        twoFinger = true;
        secondDownX = secondX;
        secondDownY = secondY;
        movementLimit = Math.max(0f, movementSlop);
    }

    /** MOVE=2 continues one pointer; release, cancellation or a second pointer ends it. */
    public void onPointerEvent(int action, float x, float y, int pointerCount) {
        if (!active) return;
        if (twoFinger) {
            if (action != 2 || pointerCount != 2) cancel();
            return;
        }
        // Movement after confirmation belongs to the image drag, not press qualification.
        if (fired && action == 2 && pointerCount == 1) return;
        if (action != 2 || pointerCount != 1
                || Math.abs(x - downX) > movementLimit || Math.abs(y - downY) > movementLimit) cancel();
    }

    /** MOVE=2 continues exactly two contacts; a changed count or terminal cancels qualification. */
    public void onTwoPointerEvent(int action, float x, float y, float secondX, float secondY,
                                  int pointerCount) {
        if (!active) return;
        if (!twoFinger || action != 2 || pointerCount != 2) {
            cancel();
            return;
        }
        // After confirmation, movement belongs to the Root-owned drag stream.
        if (fired) return;
        if (Math.abs(x - downX) > movementLimit || Math.abs(y - downY) > movementLimit
                || Math.abs(secondX - secondDownX) > movementLimit
                || Math.abs(secondY - secondDownY) > movementLimit) cancel();
    }

    public boolean takeClassification(long now) {
        if (!active || awaitingSecondFinger || fired
                || classificationDeadline < 0L || now < classificationDeadline) return false;
        classificationDeadline = -1L;
        return true;
    }

    public boolean fire(long now) {
        if (!active || awaitingSecondFinger || fired || now < deadline) return false;
        fired = true;
        classificationDeadline = -1L;
        return true;
    }

    public void cancel() {
        active = false;
        deadline = -1L;
        classificationDeadline = -1L;
        awaitingSecondFinger = false;
        twoFinger = false;
    }

    public boolean isActive() { return active; }
    public boolean isFired() { return fired; }
    public boolean isAwaitingSecondFinger() { return active && awaitingSecondFinger; }
    public boolean isTwoFinger() { return twoFinger; }
    public long downTime() { return downTime; }
    public long deadline() { return deadline; }
    public long classificationDeadline() { return classificationDeadline; }
}

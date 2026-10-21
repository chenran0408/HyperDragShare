package com.leaf.hyperdragshare.codex;

/** One pointer's press timing; all times use the same monotonic clock. */
public final class PressTriggerDetector {
    private static final float TOUCH_THRESHOLD = 0.30f;
    private static final float WIDTH_THRESHOLD = 0.095f;
    private static final float MOVE_LIMIT = 36f;

    private boolean active;
    private boolean fired;
    private boolean areaPress;
    private float downX;
    private float downY;
    private long downTime = -1L;
    private long deadline = -1L;
    private long classificationDeadline = -1L;

    /** action uses Android's masked DOWN=0, UP=1, MOVE=2, CANCEL=3 values. */
    public void onPointerEvent(int action, float x, float y, long time,
                               float normalizedTouch, float normalizedWidth, int pointerCount) {
        if (action == 0) {
            active = pointerCount == 1;
            fired = false;
            downX = x;
            downY = y;
            downTime = time;
            areaPress = reachesArea(normalizedTouch, normalizedWidth);
            deadline = active ? time + (areaPress ? 220L : 700L) : -1L;
            classificationDeadline = active && !areaPress ? time + 120L : -1L;
            return;
        }
        if (action != 2 || pointerCount != 1) {
            cancel();
            return;
        }
        if (!active) return;
        if (Math.abs(x - downX) > MOVE_LIMIT || Math.abs(y - downY) > MOVE_LIMIT) {
            cancel();
            return;
        }
        if (fired) return;
        if (reachesArea(normalizedTouch, normalizedWidth)) {
            areaPress = true;
            deadline = Math.min(deadline, downTime + 220L);
        }
    }

    /** Claims the ordinary press's classification once it becomes due. */
    public boolean takeClassification(long now) {
        if (!active || fired || classificationDeadline < 0L || now < classificationDeadline) {
            return false;
        }
        classificationDeadline = -1L;
        return true;
    }

    /** Call only for an explicit safe classification result, never for a failed/blocked lookup. */
    public void onClassification(boolean hasText) {
        if (!active || fired) return;
        classificationDeadline = -1L;
        if (!hasText) deadline = Math.min(deadline, downTime + 300L);
    }

    /** Fires once; a prior area peak remains sufficient even if the current size has dropped. */
    public boolean fire(long now) {
        if (!active || fired || now < deadline) return false;
        fired = true;
        classificationDeadline = -1L;
        return true;
    }

    public void cancel() {
        active = false;
        deadline = -1L;
        classificationDeadline = -1L;
    }

    public boolean isActive() { return active; }
    public boolean isFired() { return fired; }
    public boolean isAreaPress() { return areaPress; }
    public long downTime() { return downTime; }
    public long deadline() { return deadline; }
    public long classificationDeadline() { return classificationDeadline; }

    private static boolean reachesArea(float touch, float width) {
        return touch >= TOUCH_THRESHOLD || width >= WIDTH_THRESHOLD;
    }
}

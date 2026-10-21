package com.leaf.hyperdragshare.gestures.xposed;

/** Reset whenever a view is attached, removed or starts fading out. */
final class OpacityUpdates {
    private boolean applied;
    private float last;

    boolean shouldApply(float value) {
        if (applied && last == value) return false;
        applied = true;
        last = value;
        return true;
    }

    void reset() { applied = false; }
}

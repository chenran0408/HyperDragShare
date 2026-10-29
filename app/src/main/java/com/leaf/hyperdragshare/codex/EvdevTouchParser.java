package com.leaf.hyperdragshare.codex;

import android.view.MotionEvent;

import java.util.Arrays;

/** Converts multi-touch evdev frames into one stable primary pointer stream. */
final class EvdevTouchParser {
    interface Listener {
        void onFrame(
                int action,
                float rawX,
                float rawY,
                int rawTouchMajor,
                int rawTouchMinor,
                int rawWidthMajor);
    }

    interface ContactListener {
        void onContactFrame(int action, float x, float y, float secondX, float secondY,
                           int pointerCount);
    }

    static final int EV_SYN = 0;
    static final int EV_KEY = 1;
    static final int EV_ABS = 3;
    static final int SYN_REPORT = 0;
    static final int BTN_TOUCH = 330;
    static final int ABS_MT_SLOT = 47;
    static final int ABS_MT_TOUCH_MAJOR = 48;
    static final int ABS_MT_TOUCH_MINOR = 49;
    static final int ABS_MT_WIDTH_MAJOR = 50;
    static final int ABS_MT_POSITION_X = 53;
    static final int ABS_MT_POSITION_Y = 54;
    static final int ABS_MT_TRACKING_ID = 57;

    private static final int MAX_SLOTS = 16;
    private static final int UPDATED_X = 1;
    private static final int UPDATED_Y = 2;
    private static final int UPDATED_TOUCH_MAJOR = 4;
    private static final int UPDATED_TOUCH_MINOR = 8;
    private static final int UPDATED_WIDTH_MAJOR = 16;

    private final Listener listener;
    private final ContactListener contactListener;
    private final float[] slotX = new float[MAX_SLOTS];
    private final float[] slotY = new float[MAX_SLOTS];
    private final int[] slotTouchMajor = new int[MAX_SLOTS];
    private final int[] slotTouchMinor = new int[MAX_SLOTS];
    private final int[] slotWidthMajor = new int[MAX_SLOTS];
    private final int[] trackingId = new int[MAX_SLOTS];
    private final int[] updatedAxes = new int[MAX_SLOTS];

    private int currentSlot;
    private int primarySlot = -1;
    private boolean gestureActive;
    private boolean buttonTouchSeen;
    private boolean buttonTouchDown;
    private boolean buttonDownEdge;
    private boolean sawTrackingStart;
    private boolean primaryLifted;
    private boolean secondaryLifted;
    private boolean multiTouchDetected;
    private boolean thirdContactDetected;
    private boolean frameChanged;
    private boolean ignoreUntilAllPointersUp;
    private float lastX = Float.NaN;
    private float lastY = Float.NaN;
    private int lastTouchMajor;
    private int lastTouchMinor;
    private int lastWidthMajor;
    private int lastContactCount;
    private volatile boolean twoFingerPressEnabled;
    private boolean twoFingerStream;
    private boolean singleFingerContinuation;

    EvdevTouchParser(Listener listener) {
        this(listener, null);
    }

    EvdevTouchParser(Listener listener, ContactListener contactListener) {
        this.listener = listener;
        this.contactListener = contactListener;
        resetSlots();
    }

    void setTwoFingerPressEnabled(boolean enabled) {
        twoFingerPressEnabled = enabled;
    }

    void consume(int type, int code, int value) {
        if (type == EV_ABS) {
            consumeAbsolute(code, value);
        } else if (type == EV_KEY && code == BTN_TOUCH) {
            buttonTouchSeen = true;
            if (value != 0 && !buttonTouchDown) {
                buttonDownEdge = true;
                clearStaleCoordinates();
            }
            buttonTouchDown = value != 0;
            frameChanged = true;
        } else if (type == EV_SYN && code == SYN_REPORT) {
            emitFrame();
        }
    }

    void cancel() {
        if (contactListener != null && lastContactCount > 0) {
            contactListener.onContactFrame(MotionEvent.ACTION_CANCEL, lastX, lastY,
                    Float.NaN, Float.NaN, 0);
        }
        lastContactCount = 0;
        if (gestureActive && Float.isFinite(lastX) && Float.isFinite(lastY)) {
            listener.onFrame(
                    MotionEvent.ACTION_CANCEL,
                    lastX,
                    lastY,
                    lastTouchMajor,
                    lastTouchMinor,
                    lastWidthMajor);
        }
        resetGesture();
        ignoreUntilAllPointersUp = false;
        resetSlots();
    }

    private void consumeAbsolute(int code, int value) {
        if (code == ABS_MT_SLOT) {
            currentSlot = Math.max(0, Math.min(MAX_SLOTS - 1, value));
            return;
        }
        if (code == ABS_MT_TRACKING_ID) {
            if (value < 0) {
                if (currentSlot == primarySlot) {
                    primaryLifted = true;
                } else if (gestureActive && trackingId[currentSlot] >= 0) {
                    secondaryLifted = true;
                }
                trackingId[currentSlot] = -1;
                updatedAxes[currentSlot] = 0;
            } else {
                if (gestureActive && currentSlot != primarySlot) {
                    if (trackedPointerCount() >= 2) thirdContactDetected = true;
                    else multiTouchDetected = true;
                }
                trackingId[currentSlot] = value;
                // Drivers can report the new contact's axes before its tracking/button edge.
                // Retain only values explicitly updated in this frame, never a prior contact.
                int updated = updatedAxes[currentSlot];
                if ((updated & UPDATED_X) == 0) slotX[currentSlot] = Float.NaN;
                if ((updated & UPDATED_Y) == 0) slotY[currentSlot] = Float.NaN;
                if ((updated & UPDATED_TOUCH_MAJOR) == 0) slotTouchMajor[currentSlot] = 0;
                if ((updated & UPDATED_TOUCH_MINOR) == 0) slotTouchMinor[currentSlot] = 0;
                if ((updated & UPDATED_WIDTH_MAJOR) == 0) slotWidthMajor[currentSlot] = 0;
                sawTrackingStart = true;
            }
            frameChanged = true;
            return;
        }
        if (code == ABS_MT_POSITION_X) {
            slotX[currentSlot] = value;
            updatedAxes[currentSlot] |= UPDATED_X;
            frameChanged = true;
        } else if (code == ABS_MT_POSITION_Y) {
            slotY[currentSlot] = value;
            updatedAxes[currentSlot] |= UPDATED_Y;
            frameChanged = true;
        } else if (code == ABS_MT_TOUCH_MAJOR) {
            slotTouchMajor[currentSlot] = value;
            updatedAxes[currentSlot] |= UPDATED_TOUCH_MAJOR;
            frameChanged = true;
        } else if (code == ABS_MT_WIDTH_MAJOR) {
            slotWidthMajor[currentSlot] = value;
            updatedAxes[currentSlot] |= UPDATED_WIDTH_MAJOR;
            frameChanged = true;
        } else if (code == ABS_MT_TOUCH_MINOR) {
            slotTouchMinor[currentSlot] = value;
            updatedAxes[currentSlot] |= UPDATED_TOUCH_MINOR;
            frameChanged = true;
        }
    }

    private void emitFrame() {
        if (!frameChanged) {
            return;
        }
        int trackedCount = trackedPointerCount();
        int previousContactCount = lastContactCount;
        emitContactFrame(trackedCount, buttonTouchSeen && !buttonTouchDown);
        if (ignoreUntilAllPointersUp) {
            if (trackedCount == 0 && (!buttonTouchSeen || !buttonTouchDown)) {
                ignoreUntilAllPointersUp = false;
                clearGestureSignals();
                clearTouchSizes();
            }
            finishFrame();
            return;
        }

        int candidate = firstTrackedSlotWithCoordinates();
        if (candidate < 0 && buttonTouchSeen && buttonTouchDown) {
            candidate = firstSlotWithCoordinates();
        }

        if (!gestureActive) {
            if (trackedCount > 1) {
                if (twoFingerPressEnabled && trackedCount == 2
                        && trackedPointerCountWithCoordinates() == 2 && candidate >= 0) {
                    primarySlot = candidate;
                    gestureActive = true;
                    twoFingerStream = true;
                    setLast(candidate);
                    listener.onFrame(MotionEvent.ACTION_DOWN, lastX, lastY,
                            lastTouchMajor, lastTouchMinor, lastWidthMajor);
                } else {
                    ignoreUntilAllPointersUp = true;
                }
            } else if (candidate >= 0 && (sawTrackingStart || buttonDownEdge)) {
                primarySlot = candidate;
                gestureActive = true;
                setLast(candidate);
                listener.onFrame(
                        MotionEvent.ACTION_DOWN,
                        lastX,
                        lastY,
                        lastTouchMajor,
                        lastTouchMinor,
                        lastWidthMajor);
            }
            finishFrame();
            return;
        }

        if (buttonTouchSeen && !buttonTouchDown) {
            emitUp();
            if (trackedCount > 0) ignoreUntilAllPointersUp = true;
            finishFrame();
            return;
        }

        if (previousContactCount == 2 && trackedCount == 1 && twoFingerStream
                && (primaryLifted || secondaryLifted)) {
            int remaining = firstTrackedSlotWithCoordinates();
            if (remaining >= 0) {
                // Keep the Root stream alive; PressCaptureSource decides whether the
                // confirmed two-finger press may continue as a one-finger drag.
                // This is now a single-pointer stream. A later contact must not be
                // treated as a continuation of the original two-finger qualification.
                twoFingerStream = false;
                singleFingerContinuation = true;
                primarySlot = remaining;
                setLast(remaining);
                listener.onFrame(MotionEvent.ACTION_MOVE, lastX, lastY,
                        lastTouchMajor, lastTouchMinor, lastWidthMajor);
                finishFrame();
                return;
            }
        }

        if (previousContactCount == 2
                && (trackedCount < 2 && trackedCount > 0 || primaryLifted || secondaryLifted)) {
            emitCancelAndIgnore();
            finishFrame();
            return;
        }

        if (trackedCount > 2 || thirdContactDetected || multiTouchDetected && trackedCount != 2) {
            emitCancelAndIgnore();
            finishFrame();
            return;
        }

        if (singleFingerContinuation && trackedCount > 1) {
            emitCancelAndIgnore();
            finishFrame();
            return;
        }

        if (trackedCount == 2) {
            boolean hasTwoCoordinates = trackedPointerCountWithCoordinates() == 2
                    && hasCoordinates(primarySlot);
            if (twoFingerPressEnabled && gestureActive && hasTwoCoordinates) {
                twoFingerStream = true;
            }
            if (twoFingerStream && gestureActive && hasTwoCoordinates) {
                lastX = slotX[primarySlot];
                lastY = slotY[primarySlot];
                lastTouchMajor = slotTouchMajor[primarySlot];
                lastTouchMinor = slotTouchMinor[primarySlot];
                lastWidthMajor = slotWidthMajor[primarySlot];
                listener.onFrame(MotionEvent.ACTION_MOVE, lastX, lastY,
                        lastTouchMajor, lastTouchMinor, lastWidthMajor);
            } else {
                emitCancelAndIgnore();
            }
            finishFrame();
            return;
        }

        boolean primaryTracked = primarySlot >= 0 && trackingId[primarySlot] >= 0;
        boolean buttonReleased = buttonTouchSeen && !buttonTouchDown;
        if (!primaryTracked || primaryLifted || buttonReleased) {
            emitUp();
            if (trackedCount > 0 || (buttonTouchSeen && buttonTouchDown)) {
                ignoreUntilAllPointersUp = true;
            }
            finishFrame();
            return;
        }

        if (hasCoordinates(primarySlot)) {
            float nextX = slotX[primarySlot];
            float nextY = slotY[primarySlot];
            int nextTouchMajor = slotTouchMajor[primarySlot];
            int nextTouchMinor = slotTouchMinor[primarySlot];
            int nextWidthMajor = slotWidthMajor[primarySlot];
            if (nextX != lastX || nextY != lastY
                    || nextTouchMajor != lastTouchMajor
                    || nextTouchMinor != lastTouchMinor
                    || nextWidthMajor != lastWidthMajor) {
                lastX = nextX;
                lastY = nextY;
                lastTouchMajor = nextTouchMajor;
                lastTouchMinor = nextTouchMinor;
                lastWidthMajor = nextWidthMajor;
                listener.onFrame(
                        MotionEvent.ACTION_MOVE,
                        lastX,
                        lastY,
                        lastTouchMajor,
                        lastTouchMinor,
                        lastWidthMajor);
            }
        }
        finishFrame();
    }

    private void emitContactFrame(int pointerCount, boolean terminal) {
        if (contactListener == null) return;
        if (terminal) {
            contactListener.onContactFrame(MotionEvent.ACTION_UP, lastX, lastY,
                    Float.NaN, Float.NaN, 0);
            lastContactCount = 0;
            return;
        }
        int action;
        if (pointerCount == 0) {
            action = lastContactCount > 0 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
        } else if (lastContactCount == 0) {
            action = MotionEvent.ACTION_DOWN;
        } else if (pointerCount > lastContactCount) {
            action = MotionEvent.ACTION_POINTER_DOWN;
        } else if (pointerCount < lastContactCount) {
            action = pointerCount == 0 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_POINTER_UP;
        } else {
            action = MotionEvent.ACTION_MOVE;
        }
        lastContactCount = pointerCount;
        if (pointerCount == 0) {
            contactListener.onContactFrame(action, lastX, lastY, Float.NaN, Float.NaN, 0);
            return;
        }
        int first = primarySlot >= 0 && trackingId[primarySlot] >= 0
                ? primarySlot : firstTrackedSlotWithCoordinates();
        int second = -1;
        for (int index = 0; index < MAX_SLOTS; index++) {
            if (index != first && trackingId[index] >= 0 && hasCoordinates(index)) {
                second = index;
                break;
            }
        }
        float x = first >= 0 ? slotX[first] : Float.NaN;
        float y = first >= 0 ? slotY[first] : Float.NaN;
        float secondX = second >= 0 ? slotX[second] : Float.NaN;
        float secondY = second >= 0 ? slotY[second] : Float.NaN;
        contactListener.onContactFrame(action, x, y, secondX, secondY, pointerCount);
    }

    private void emitUp() {
        if (gestureActive && Float.isFinite(lastX) && Float.isFinite(lastY)) {
            listener.onFrame(
                    MotionEvent.ACTION_UP,
                    lastX,
                    lastY,
                    lastTouchMajor,
                    lastTouchMinor,
                    lastWidthMajor);
        }
        resetGesture();
    }

    private void emitCancelAndIgnore() {
        if (gestureActive && Float.isFinite(lastX) && Float.isFinite(lastY)) {
            listener.onFrame(
                    MotionEvent.ACTION_CANCEL,
                    lastX,
                    lastY,
                    lastTouchMajor,
                    lastTouchMinor,
                    lastWidthMajor);
        }
        resetGesture();
        ignoreUntilAllPointersUp = true;
    }

    private void setLast(int slot) {
        lastX = slotX[slot];
        lastY = slotY[slot];
        lastTouchMajor = slotTouchMajor[slot];
        lastTouchMinor = slotTouchMinor[slot];
        lastWidthMajor = slotWidthMajor[slot];
    }

    /** Snapshot at the frame callback; tracking IDs are deliberately not Android pointer IDs. */
    int diagnosticPointerCount() {
        int count = trackedPointerCount();
        return count == 0 && buttonTouchSeen && buttonTouchDown ? 1 : count;
    }

    private int trackedPointerCount() {
        int count = 0;
        for (int id : trackingId) {
            if (id >= 0) {
                count++;
            }
        }
        return count;
    }

    private int trackedPointerCountWithCoordinates() {
        int count = 0;
        for (int index = 0; index < MAX_SLOTS; index++) {
            if (trackingId[index] >= 0 && hasCoordinates(index)) count++;
        }
        return count;
    }

    private int firstTrackedSlotWithCoordinates() {
        for (int index = 0; index < MAX_SLOTS; index++) {
            if (trackingId[index] >= 0 && hasCoordinates(index)) {
                return index;
            }
        }
        return -1;
    }

    private int firstSlotWithCoordinates() {
        for (int index = 0; index < MAX_SLOTS; index++) {
            if (hasCoordinates(index)) {
                return index;
            }
        }
        return -1;
    }

    private boolean hasCoordinates(int slot) {
        return slot >= 0 && slot < MAX_SLOTS
                && Float.isFinite(slotX[slot]) && Float.isFinite(slotY[slot]);
    }

    private void finishFrame() {
        Arrays.fill(updatedAxes, 0);
        frameChanged = false;
        primaryLifted = false;
        secondaryLifted = false;
        multiTouchDetected = false;
        thirdContactDetected = false;
        buttonDownEdge = false;
        if (!gestureActive && !ignoreUntilAllPointersUp) {
            sawTrackingStart = false;
        }
    }

    private void clearGestureSignals() {
        sawTrackingStart = false;
        buttonDownEdge = false;
        primaryLifted = false;
        secondaryLifted = false;
        multiTouchDetected = false;
        thirdContactDetected = false;
    }

    private void resetGesture() {
        gestureActive = false;
        twoFingerStream = false;
        singleFingerContinuation = false;
        primarySlot = -1;
        primaryLifted = false;
        secondaryLifted = false;
        lastX = Float.NaN;
        lastY = Float.NaN;
        clearTouchSizes();
    }

    private void clearStaleCoordinates() {
        for (int index = 0; index < MAX_SLOTS; index++) {
            if ((updatedAxes[index] & UPDATED_X) == 0) slotX[index] = Float.NaN;
            if ((updatedAxes[index] & UPDATED_Y) == 0) slotY[index] = Float.NaN;
        }
    }

    private void clearTouchSizes() {
        Arrays.fill(slotTouchMajor, 0);
        Arrays.fill(slotTouchMinor, 0);
        Arrays.fill(slotWidthMajor, 0);
        lastTouchMajor = 0;
        lastTouchMinor = 0;
        lastWidthMajor = 0;
    }

    private void resetSlots() {
        for (int index = 0; index < MAX_SLOTS; index++) {
            trackingId[index] = -1;
            slotX[index] = Float.NaN;
            slotY[index] = Float.NaN;
            slotTouchMajor[index] = 0;
            slotTouchMinor[index] = 0;
            slotWidthMajor[index] = 0;
            updatedAxes[index] = 0;
        }
        currentSlot = 0;
        buttonTouchSeen = false;
        buttonTouchDown = false;
        frameChanged = false;
        clearGestureSignals();
    }
}

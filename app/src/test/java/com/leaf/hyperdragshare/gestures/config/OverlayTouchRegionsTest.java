package com.leaf.hyperdragshare.gestures.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.view.MotionEvent;

import org.robolectric.RuntimeEnvironment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public final class OverlayTouchRegionsTest {
    @Test
    public void downInsideOverlayOwnsTheWholeTouchSequence() {
        OverlayTouchRegions.DownGate gate = new OverlayTouchRegions.DownGate();

        assertTrue(gate.shouldYield(MotionEvent.ACTION_DOWN, 100L, true));
        assertTrue(gate.shouldYield(MotionEvent.ACTION_MOVE, 100L, false));
        assertTrue(gate.shouldYield(MotionEvent.ACTION_UP, 100L, false));
        assertFalse(gate.shouldYield(MotionEvent.ACTION_MOVE, 100L, false));
    }

    @Test
    public void downOutsideOverlayDoesNotClaimTheSequence() {
        OverlayTouchRegions.DownGate gate = new OverlayTouchRegions.DownGate();

        assertFalse(gate.shouldYield(MotionEvent.ACTION_DOWN, 200L, false));
        assertFalse(gate.shouldYield(MotionEvent.ACTION_MOVE, 200L, false));
    }

    @Test
    public void removingTouchableWindowPublishesAnEmptyLiveSnapshot() {
        int id = OverlayTouchRegions.newWindowId();
        OverlayTouchRegions.update(RuntimeEnvironment.getApplication(), id,
                new Rect(10, 20, 110, 220));
        assertArrayEquals(new int[]{10, 20, 110, 220}, OverlayTouchRegions.read()
                .getIntArray(ConfigContract.KEY_OVERLAY_TOUCH_RECTS));
        assertNotNull(OverlayTouchRegions.read().getBinder(ConfigContract.KEY_OVERLAY_TOUCH_OWNER));

        OverlayTouchRegions.update(RuntimeEnvironment.getApplication(), id, null);
        assertArrayEquals(new int[0], OverlayTouchRegions.read()
                .getIntArray(ConfigContract.KEY_OVERLAY_TOUCH_RECTS));
    }
}

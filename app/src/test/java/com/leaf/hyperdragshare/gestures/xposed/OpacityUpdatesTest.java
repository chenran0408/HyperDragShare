package com.leaf.hyperdragshare.gestures.xposed;

import org.junit.Test;
import static org.junit.Assert.*;

public final class OpacityUpdatesTest {
    @Test public void stationaryMovesStopCancellingAnimatorButFadeAndNewSessionStillApply() {
        OpacityUpdates updates = new OpacityUpdates();
        assertTrue(updates.shouldApply(1f)); // First move still cancels the reveal animator.
        for (int i = 0; i < 1000; i++) assertFalse(updates.shouldApply(1f));
        assertTrue(updates.shouldApply(.8f));
        assertTrue(updates.shouldApply(.6f));
        assertTrue(updates.shouldApply(1f));
        updates.reset(); // A dismissal starts; the same alpha must interrupt it.
        assertTrue(updates.shouldApply(1f));
        assertFalse(updates.shouldApply(1f));
        updates.reset(); // Remove/re-attach must not inherit the previous alpha command.
        assertTrue(updates.shouldApply(1f));
    }
}

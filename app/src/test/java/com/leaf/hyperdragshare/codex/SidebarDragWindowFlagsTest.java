package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.WindowManager;

import org.junit.Test;

public class SidebarDragWindowFlagsTest {
    @Test public void passThroughChangesOnlyTouchabilityAndRestoresOriginalState() {
        int original = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        int duringDrag = SidebarDragWindowFlags.allowDropThrough(original);
        assertTrue((duringDrag & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0);
        assertEquals(original, duringDrag & ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);

        int current = duringDrag | WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        int restored = SidebarDragWindowFlags.restoreTouchability(current, false);
        assertFalse((restored & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0);
        assertTrue((restored & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0);
    }

    @Test public void restoresAWindowThatWasAlreadyNotTouchable() {
        int original = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        assertEquals(original, SidebarDragWindowFlags.restoreTouchability(
                SidebarDragWindowFlags.allowDropThrough(original), true));
    }
}

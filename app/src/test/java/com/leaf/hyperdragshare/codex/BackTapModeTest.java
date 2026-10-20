package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BackTapModeTest {
    @Test
    public void eachConfiguredModeHandlesOnlyItsSelectedGestures() {
        assertFalse(BackTapMode.handles(BackTapMode.OFF, true));
        assertFalse(BackTapMode.handles(BackTapMode.OFF, false));

        assertTrue(BackTapMode.handles(BackTapMode.DOUBLE_TAP, true));
        assertFalse(BackTapMode.handles(BackTapMode.DOUBLE_TAP, false));

        assertFalse(BackTapMode.handles(BackTapMode.TRIPLE_TAP, true));
        assertTrue(BackTapMode.handles(BackTapMode.TRIPLE_TAP, false));

        assertTrue(BackTapMode.handles(BackTapMode.DOUBLE_AND_TRIPLE_TAP, true));
        assertTrue(BackTapMode.handles(BackTapMode.DOUBLE_AND_TRIPLE_TAP, false));
    }

    @Test
    public void invalidStoredModesFailClosedToOff() {
        assertEquals(BackTapMode.OFF, BackTapMode.normalize(-1));
        assertEquals(BackTapMode.OFF, BackTapMode.normalize(4));
        assertEquals(BackTapMode.TRIPLE_TAP, BackTapMode.normalize(BackTapMode.TRIPLE_TAP));
    }
}

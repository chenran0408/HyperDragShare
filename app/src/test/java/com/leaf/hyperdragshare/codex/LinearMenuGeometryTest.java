package com.leaf.hyperdragshare.codex;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LinearMenuGeometryTest {
    @Test public void everyEdgeStaysInsideContentOnPortraitAndLandscape() {
        for (int[] screen : new int[][]{{384, 854}, {854, 384}}) {
            for (int position = 0; position < 4; position++) {
                for (int count : new int[]{1, 3, 40}) {
                    int[] b = LinearMenuGeometry.bounds(position, screen[0], screen[1], 24, 16,
                            8, count, 1f);
                    assertTrue(b[0] >= 8);
                    assertTrue(b[1] >= 32);
                    assertTrue(b[0] + b[2] <= screen[0] - 8);
                    assertTrue(b[1] + b[3] <= screen[1] - 24);
                    if (position == DragShareSettings.SIMPLE_MENU_POSITION_LEFT) assertEquals(8, b[0]);
                    if (position == DragShareSettings.SIMPLE_MENU_POSITION_RIGHT) assertEquals(screen[0] - 8, b[0] + b[2]);
                    if (position == DragShareSettings.SIMPLE_MENU_POSITION_TOP) assertEquals(32, b[1]);
                    if (position == DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM) assertEquals(screen[1] - 24, b[1] + b[3]);
                }
            }
        }
    }

    @Test public void sideMenuFitsContentInsteadOfCoveringWholeHeight() {
        int[] few = LinearMenuGeometry.bounds(2, 1440, 3200, 100, 80, 30, 2, 3.75f);
        int[] many = LinearMenuGeometry.bounds(2, 1440, 3200, 100, 80, 30, 50, 3.75f);
        assertTrue(few[3] < many[3]);
        assertTrue(many[3] < 1600);
        assertEquals(1610, few[1] + few[3] / 2);
    }

    @Test public void fewHorizontalTargetsUseLessThanScreenWidth() {
        int[] b = LinearMenuGeometry.bounds(1, 384, 854, 24, 16, 8, 2, 1f);
        assertTrue(b[2] < 192);
        assertEquals(192, b[0] + b[2] / 2);
    }

    @Test public void bottomMenuMovesUpByTheConfiguredEdgeDistanceWithinInsets() {
        int[] noMargin = LinearMenuGeometry.bounds(
                DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM, 1080, 2400,
                64, 96, 0, 4, 1f);
        int[] withMargin = LinearMenuGeometry.bounds(
                DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM, 1080, 2400,
                64, 96, 24, 4, 1f);

        assertEquals(2400 - 96 - noMargin[3], noMargin[1]);
        assertEquals(2400 - 96 - 24 - withMargin[3], withMargin[1]);
        assertEquals(24, noMargin[1] - withMargin[1]);
        assertTrue(withMargin[1] >= 64 + 24);
    }
}

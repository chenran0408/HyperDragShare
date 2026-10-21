package com.leaf.hyperdragshare.gestures.xposed;

import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;

public final class SideIconLayoutTest {
    @Test public void cachedFanAndRingMatchLegacyHitTestingIncludingBoundaries() {
        Random random = new Random(157);
        SideIconLayout.Cache cache = new SideIconLayout.Cache();
        for (int count = 1; count <= 60; count++) {
            for (GestureGeometry.Corner side : GestureGeometry.Corner.values()) {
                int width = count % 2 == 0 ? 1440 : 3200;
                float centerX = width * .43f, centerY = 871.25f;
                float radius = 65.5f + count * 12f, diameter = 87.5f, tolerance = 17.25f;
                for (int mode : new int[]{SideIconLayout.FAN, SideIconLayout.RING}) {
                    SideIconLayout layout = cache.get(mode, side, count, width, centerX, centerY, radius);
                    for (int i = 0; i < count; i++) {
                        GestureGeometry.Point point = mode == SideIconLayout.FAN
                                ? GestureGeometry.sideFanIconCenter(side, i, count, width, centerX, centerY, radius)
                                : GestureGeometry.sideRingIconCenter(i, count, centerX, centerY, radius);
                        assertEquals(point.x, layout.x(i), 0f);
                        assertEquals(point.y, layout.y(i), 0f);
                        float edge = diameter / 2f + tolerance;
                        for (float delta : new float[]{0f, Math.nextDown(edge), edge, Math.nextUp(edge), -edge}) {
                            compare(layout, mode, side, count, width, centerX, centerY, radius,
                                    diameter, tolerance, point.x + delta, point.y);
                        }
                    }
                    for (int i = 0; i < 120; i++) {
                        compare(layout, mode, side, count, width, centerX, centerY, radius,
                                diameter, tolerance, random.nextFloat() * width,
                                centerY + (random.nextFloat() * 2 - 1) * (radius + 300));
                    }
                }
            }
        }
    }

    private void compare(SideIconLayout cached, int mode, GestureGeometry.Corner side, int count,
                         int width, float cx, float cy, float radius, float diameter,
                         float tolerance, float x, float y) {
        int legacy = mode == SideIconLayout.FAN
                ? GestureGeometry.sideFanSelection(side, x, y, width, count, cx, cy, radius, diameter, tolerance)
                : GestureGeometry.sideRingSelection(x, y, count, cx, cy, radius, diameter, tolerance);
        assertEquals(legacy, cached.selection(x, y, diameter, tolerance));
    }

    @Test public void everyGeometryInputInvalidatesAndRepeatedInputReusesSnapshot() {
        SideIconLayout.Cache cache = new SideIconLayout.Cache();
        SideIconLayout first = cache.get(SideIconLayout.FAN, GestureGeometry.Corner.LEFT, 8, 1080, 140, 600, 200);
        assertSame(first, cache.get(SideIconLayout.FAN, GestureGeometry.Corner.LEFT, 8, 1080, 140, 600, 200));
        assertNotSame(first, cache.get(SideIconLayout.RING, GestureGeometry.Corner.LEFT, 8, 1080, 140, 600, 200));
        first = cache.get(SideIconLayout.FAN, GestureGeometry.Corner.LEFT, 8, 1080, 140, 600, 200);
        assertNotSame(first, cache.get(SideIconLayout.FAN, GestureGeometry.Corner.RIGHT, 8, 1080, 140, 600, 200));
        first = cache.get(SideIconLayout.FAN, GestureGeometry.Corner.LEFT, 8, 1080, 140, 600, 200);
        assertNotSame(first, cache.get(SideIconLayout.FAN, GestureGeometry.Corner.LEFT, 9, 1080, 140, 600, 200));
        first = cache.get(SideIconLayout.FAN, GestureGeometry.Corner.LEFT, 8, 1080, 140, 600, 200);
        assertNotSame(first, cache.get(SideIconLayout.FAN, GestureGeometry.Corner.LEFT, 8, 2400, 140, 600, 200));
        first = cache.get(SideIconLayout.FAN, GestureGeometry.Corner.LEFT, 8, 1080, 140, 600, 200);
        assertNotSame(first, cache.get(SideIconLayout.FAN, GestureGeometry.Corner.LEFT, 8, 1080, 150, 600, 200));
        first = cache.get(SideIconLayout.FAN, GestureGeometry.Corner.LEFT, 8, 1080, 140, 600, 200);
        assertNotSame(first, cache.get(SideIconLayout.FAN, GestureGeometry.Corner.LEFT, 8, 1080, 140, 620, 200));
        first = cache.get(SideIconLayout.FAN, GestureGeometry.Corner.LEFT, 8, 1080, 140, 600, 200);
        assertNotSame(first, cache.get(SideIconLayout.FAN, GestureGeometry.Corner.LEFT, 8, 1080, 140, 600, 215));
        first = cache.get(SideIconLayout.FAN, GestureGeometry.Corner.LEFT, 8, 1080, 140, 600, 200);
        cache.clear();
        assertNotSame(first, cache.get(SideIconLayout.FAN, GestureGeometry.Corner.LEFT, 8, 1080, 140, 600, 200));
    }

    @Test public void listDrawingAndEmptyLayoutsKeepOriginalCoordinates() {
        SideIconLayout.Cache cache = new SideIconLayout.Cache();
        SideIconLayout list = cache.get(SideIconLayout.LIST, GestureGeometry.Corner.RIGHT, 30, 0, 240, 370, 91);
        for (int i = 0; i < 30; i++) {
            GestureGeometry.Point point = GestureGeometry.sideListIconCenter(i, 240, 370, 91);
            assertEquals(point.x, list.x(i), 0f); assertEquals(point.y, list.y(i), 0f);
        }
        assertEquals(-1, cache.get(SideIconLayout.RING, GestureGeometry.Corner.LEFT, 0, 0, 0, 0, 0)
                .selection(0, 0, 60, 10));
    }
}

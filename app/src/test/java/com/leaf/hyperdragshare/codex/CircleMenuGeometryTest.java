package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class CircleMenuGeometryTest {
    @Test
    public void nearestEdgeKeepsAutomaticLeftRightBehavior() {
        assertEquals(
                CircleMenuGeometry.EDGE_LEFT,
                CircleMenuGeometry.nearestEdge(20, 500, 1080, 2000, 76));
        assertEquals(
                CircleMenuGeometry.EDGE_RIGHT,
                CircleMenuGeometry.nearestEdge(1060, 500, 1080, 2000, 76));
        assertEquals(
                CircleMenuGeometry.EDGE_NONE,
                CircleMenuGeometry.nearestEdge(500, 20, 1080, 2000, 76));
        assertEquals(
                CircleMenuGeometry.EDGE_NONE,
                CircleMenuGeometry.nearestEdge(500, 1980, 1080, 2000, 76));
        assertEquals(
                CircleMenuGeometry.EDGE_NONE,
                CircleMenuGeometry.nearestEdge(20, 20, 1080, 2000, 76));
        assertEquals(
                CircleMenuGeometry.EDGE_NONE,
                CircleMenuGeometry.nearestEdge(500, 1000, 1080, 2000, 76));
    }

    @Test
    public void configuredEdgeProgressSupportsAllFourSides() {
        assertEquals(0.75f, CircleMenuGeometry.edgeProgress(
                CircleMenuGeometry.EDGE_LEFT, 20, 600, 1080, 2000, 80), 0.001f);
        assertEquals(0.75f, CircleMenuGeometry.edgeProgress(
                CircleMenuGeometry.EDGE_RIGHT, 1060, 600, 1080, 2000, 80), 0.001f);
        assertEquals(0.75f, CircleMenuGeometry.edgeProgress(
                CircleMenuGeometry.EDGE_TOP, 500, 20, 1080, 2000, 80), 0.001f);
        assertEquals(0.75f, CircleMenuGeometry.edgeProgress(
                CircleMenuGeometry.EDGE_BOTTOM, 500, 1980, 1080, 2000, 80), 0.001f);
        assertEquals(0f, CircleMenuGeometry.edgeProgress(
                CircleMenuGeometry.EDGE_NONE, 20, 20, 1080, 2000, 80), 0f);
    }

    @Test
    public void arcAnglesCenterAtEachEdgeAndUseThirtySixDegreeSpacing() {
        assertEquals(-72, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_LEFT, 5));
        assertEquals(18, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_TOP, 5));
        assertEquals(108, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_RIGHT, 5));
        assertEquals(198, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_BOTTOM, 5));
        assertEquals(252, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_BOTTOM, 2));
        assertEquals(-18, CircleMenuGeometry.startAngle(CircleMenuGeometry.EDGE_LEFT, 1));
        assertEquals(36, CircleMenuGeometry.arcSweepAngle(1));
        assertEquals(144, CircleMenuGeometry.arcSweepAngle(5));
    }

    @Test
    public void containersUseTheSafeAreaCenterOnEachEdge() {
        int[] left = CircleMenuGeometry.containerBounds(
                CircleMenuGeometry.EDGE_LEFT, 1080, 2000, 600, 40, 80);
        assertArrayEquals(new int[]{0, 680, 300, 1280}, left);

        int[] right = CircleMenuGeometry.containerBounds(
                CircleMenuGeometry.EDGE_RIGHT, 1080, 2000, 600, 40, 80);
        assertArrayEquals(new int[]{780, 680, 1080, 1280}, right);

        int[] top = CircleMenuGeometry.containerBounds(
                CircleMenuGeometry.EDGE_TOP, 1080, 2000, 600, 40, 80);
        assertArrayEquals(new int[]{240, 40, 840, 340}, top);

        int[] bottom = CircleMenuGeometry.containerBounds(
                CircleMenuGeometry.EDGE_BOTTOM, 1080, 2000, 600, 40, 80);
        assertArrayEquals(new int[]{240, 1620, 840, 1920}, bottom);
    }

    @Test
    public void itemCentersProgressAlongEachSemicircleAndStayInsideScreen() {
        int width = 1080;
        int height = 2400;
        int topInset = 72;
        int bottomInset = 84;
        int span = 720;
        int itemSize = 168;
        float radius = 273f;

        int[] left = CircleMenuGeometry.containerBounds(
                CircleMenuGeometry.EDGE_LEFT, width, height, span, topInset, bottomInset);
        int[] right = CircleMenuGeometry.containerBounds(
                CircleMenuGeometry.EDGE_RIGHT, width, height, span, topInset, bottomInset);
        int[] top = CircleMenuGeometry.containerBounds(
                CircleMenuGeometry.EDGE_TOP, width, height, span, topInset, bottomInset);
        int[] bottom = CircleMenuGeometry.containerBounds(
                CircleMenuGeometry.EDGE_BOTTOM, width, height, span, topInset, bottomInset);

        float[] leftFirst = itemCenter(CircleMenuGeometry.EDGE_LEFT, left, span, radius, 0, 5);
        float[] leftLast = itemCenter(CircleMenuGeometry.EDGE_LEFT, left, span, radius, 4, 5);
        float[] rightFirst = itemCenter(CircleMenuGeometry.EDGE_RIGHT, right, span, radius, 0, 5);
        float[] rightLast = itemCenter(CircleMenuGeometry.EDGE_RIGHT, right, span, radius, 4, 5);
        float[] topFirst = itemCenter(CircleMenuGeometry.EDGE_TOP, top, span, radius, 0, 5);
        float[] topLast = itemCenter(CircleMenuGeometry.EDGE_TOP, top, span, radius, 4, 5);
        float[] bottomFirst = itemCenter(CircleMenuGeometry.EDGE_BOTTOM, bottom, span, radius, 0, 5);
        float[] bottomLast = itemCenter(CircleMenuGeometry.EDGE_BOTTOM, bottom, span, radius, 4, 5);

        assertTrue(leftFirst[1] < leftLast[1]);
        assertTrue(rightFirst[1] < rightLast[1]);
        assertTrue(topFirst[0] < topLast[0]);
        assertTrue(bottomFirst[0] < bottomLast[0]);

        for (int edge : new int[]{
                CircleMenuGeometry.EDGE_LEFT,
                CircleMenuGeometry.EDGE_TOP,
                CircleMenuGeometry.EDGE_RIGHT,
                CircleMenuGeometry.EDGE_BOTTOM}) {
            int[] bounds = edge == CircleMenuGeometry.EDGE_LEFT ? left
                    : edge == CircleMenuGeometry.EDGE_TOP ? top
                    : edge == CircleMenuGeometry.EDGE_RIGHT ? right : bottom;
            for (int index = 0; index < 5; index++) {
                float[] center = itemCenter(edge, bounds, span, radius, index, 5);
                assertTrue("left edge=" + edge + " index=" + index,
                        center[0] - itemSize / 2f >= 0f);
                assertTrue("right edge=" + edge + " index=" + index,
                        center[0] + itemSize / 2f <= width);
                assertTrue("top edge=" + edge + " index=" + index,
                        center[1] - itemSize / 2f >= topInset);
                assertTrue("bottom edge=" + edge + " index=" + index,
                        center[1] + itemSize / 2f <= height - bottomInset);
            }
        }
    }

    @Test
    public void geometryFitsSmallDisplaysAndInsets() {
        int[] bounds = CircleMenuGeometry.containerBounds(
                CircleMenuGeometry.EDGE_BOTTOM, 600, 700, 720, 24, 24);
        assertArrayEquals(new int[]{0, 376, 600, 676}, bounds);
        assertArrayEquals(
                new int[]{0, 0, 0, 0},
                CircleMenuGeometry.containerBounds(
                        CircleMenuGeometry.EDGE_TOP, 0, 0, 720, 0, 0));
    }

    @Test
    public void oneItemIsCenteredOnTheChosenEdge() {
        assertCenter(CircleMenuGeometry.EDGE_LEFT, 0, 700, 600, 220, 220, 1000);
        assertCenter(CircleMenuGeometry.EDGE_TOP, 0, 700, 600, 220, 300, 920);
        assertCenter(CircleMenuGeometry.EDGE_RIGHT, 780, 700, 600, 220, 860, 1000);
        assertCenter(CircleMenuGeometry.EDGE_BOTTOM, 0, 700, 600, 220, 300, 780);
    }

    @Test
    public void displayOrderRemainsNatural() {
        for (int edge : new int[]{
                CircleMenuGeometry.EDGE_LEFT,
                CircleMenuGeometry.EDGE_TOP,
                CircleMenuGeometry.EDGE_RIGHT,
                CircleMenuGeometry.EDGE_BOTTOM}) {
            assertArrayEquals(
                    new int[]{0, 1, 2, 3, 4},
                    CircleMenuGeometry.displayOrder(edge, 5));
        }
        assertArrayEquals(
                new int[]{0, 1, 2},
                CircleMenuGeometry.displayOrder(CircleMenuGeometry.EDGE_LEFT, 3));
    }

    private static float[] itemCenter(
            int edge, int[] bounds, int span, float radius, int index, int itemCount) {
        return CircleMenuGeometry.itemCenterValues(
                edge, bounds[0], bounds[1], span, radius, index, itemCount);
    }

    private static void assertCenter(
            int edge,
            int originX,
            int originY,
            int span,
            int radius,
            float expectedX,
            float expectedY) {
        float[] center = CircleMenuGeometry.itemCenterValues(
                edge, originX, originY, span, radius, 0, 1);
        assertEquals(expectedX, center[0], 0.01f);
        assertEquals(expectedY, center[1], 0.01f);
    }
}

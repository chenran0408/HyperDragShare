package com.leaf.hyperdragshare.gestures.xposed;

import com.leaf.hyperdragshare.gestures.config.HoneycombPositions;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class HoneycombPositionMappingTest {
    @Test public void unavailableTargetDoesNotShiftSurvivorsOrTheirLaunchIndices() {
        RuntimeTarget first = new RuntimeTarget("pkg", "id", "", "main", null, 0);
        RuntimeTarget missing = new RuntimeTarget("pkg", "other", "", "missing", null, 0);
        RuntimeTarget clone = new RuntimeTarget("pkg", "id", "", "clone", null, 999);
        List<String> all = Arrays.asList(first.positionIdentity(), missing.positionIdentity(), clone.positionIdentity());
        HoneycombPositions positions = HoneycombPositions.compact(all, 3).swap(0, 2);
        List<HoneycombGeometry.Point> points = HoneycombGeometry.positionedPoints(
                Arrays.asList(first.positionIdentity(), clone.positionIdentity()), positions, 80f);
        assertEquals(positions.find(first.positionIdentity()).x * 80f, points.get(0).x, .001f);
        assertEquals(positions.find(clone.positionIdentity()).y * 80f, points.get(1).y, .001f);
        float[] x = {points.get(0).x, points.get(1).x};
        float[] y = {points.get(0).y, points.get(1).y};
        float[] radius = {20, 20};
        assertEquals(0, HoneycombGeometry.hitVisible(x, y, radius, 2, x[0], y[0]));
        assertEquals(1, HoneycombGeometry.hitVisible(x, y, radius, 2, x[1], y[1]));
        HoneycombPositions.Slot absent = positions.find(missing.positionIdentity());
        assertEquals(-1, HoneycombGeometry.hitVisible(x, y, radius, 2, absent.x * 80, absent.y * 80));
    }
}

package com.leaf.hyperdragshare.gestures.xposed;

/** One bounded snapshot per owner; drawing and hit testing use identical coordinates. */
final class SideIconLayout {
    static final int LIST = 0, FAN = 1, RING = 2;
    private final int mode, count, width;
    private final GestureGeometry.Corner side;
    private final float centerX, centerY, size;
    private final float[] xs, ys;

    private SideIconLayout(int mode, GestureGeometry.Corner side, int count, int width,
                           float centerX, float centerY, float size) {
        this.mode = mode;
        this.side = side;
        this.count = Math.max(0, count);
        this.width = width;
        this.centerX = centerX;
        this.centerY = centerY;
        this.size = size;
        xs = new float[this.count];
        ys = new float[this.count];
        for (int i = 0; i < this.count; i++) {
            GestureGeometry.Point point = mode == RING
                    ? GestureGeometry.sideRingIconCenter(i, this.count, centerX, centerY, size)
                    : mode == FAN ? GestureGeometry.sideFanIconCenter(side, i, this.count,
                    width, centerX, centerY, size)
                    : GestureGeometry.sideListIconCenter(i, centerX, centerY, size);
            xs[i] = point.x;
            ys[i] = point.y;
        }
    }

    float x(int index) { return xs[index]; }
    float y(int index) { return ys[index]; }

    int selection(float x, float y, float iconDiameter, float tolerance) {
        float radius = iconDiameter / 2f + tolerance;
        float limit = radius * radius;
        float nearestDistance = Float.MAX_VALUE;
        int nearest = -1;
        for (int i = 0; i < count; i++) {
            float dx = x - xs[i], dy = y - ys[i];
            float distance = dx * dx + dy * dy;
            if (distance <= limit && distance < nearestDistance) {
                nearest = i;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    static final class Cache {
        private SideIconLayout current;

        SideIconLayout get(int mode, GestureGeometry.Corner side, int count, int width,
                           float centerX, float centerY, float size) {
            if (current == null || current.mode != mode || current.side != side
                    || current.count != Math.max(0, count) || current.width != width
                    || current.centerX != centerX || current.centerY != centerY || current.size != size) {
                current = new SideIconLayout(mode, side, count, width, centerX, centerY, size);
            }
            return current;
        }

        void clear() { current = null; }
    }
}

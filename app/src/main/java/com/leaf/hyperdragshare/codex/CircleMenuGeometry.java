package com.leaf.hyperdragshare.codex;

import android.graphics.PointF;
import android.graphics.Rect;

/** Geometry used by the four-edge semicircle share menu. */
final class CircleMenuGeometry {
    static final int EDGE_NONE = 0;
    static final int EDGE_LEFT = 1;
    static final int EDGE_RIGHT = 2;
    static final int EDGE_TOP = 3;
    static final int EDGE_BOTTOM = 4;

    private CircleMenuGeometry() {}

    static int nearestEdge(float x, float y, int width, int height, float trigger) {
        if (width <= 0 || height <= 0 || trigger <= 0
                || !Float.isFinite(x) || !Float.isFinite(y)) {
            return EDGE_NONE;
        }
        if (y < height * 0.1f || y > height * 0.9f) {
            return EDGE_NONE;
        }
        float left = Math.max(0f, x);
        float right = Math.max(0f, width - x);
        float minimum = Math.min(left, right);
        if (minimum > trigger) {
            return EDGE_NONE;
        }
        return left <= right ? EDGE_LEFT : EDGE_RIGHT;
    }

    static float edgeProgress(float x, float y, int width, int height, float softDistance) {
        int edge = nearestEdge(x, y, width, height, softDistance);
        return edgeProgress(edge, x, y, width, height, softDistance);
    }

    static float edgeProgress(
            int edge, float x, float y, int width, int height, float softDistance) {
        if (width <= 0 || height <= 0 || softDistance <= 0
                || !Float.isFinite(x) || !Float.isFinite(y)) {
            return 0f;
        }
        float distance;
        switch (edge) {
            case EDGE_LEFT:
                distance = Math.max(0f, x);
                break;
            case EDGE_RIGHT:
                distance = Math.max(0f, width - x);
                break;
            case EDGE_TOP:
                distance = Math.max(0f, y);
                break;
            case EDGE_BOTTOM:
                distance = Math.max(0f, height - y);
                break;
            default:
                return 0f;
        }
        return clamp01(1f - distance / softDistance);
    }

    static int startAngle(int edge, int itemCount) {
        int center = edgeCenterAngle(edge);
        int sweep = arcSweepAngle(itemCount);
        return Math.round(center - sweep / 2f);
    }

    static int arcSweepAngle(int itemCount) {
        int count = Math.max(1, Math.min(5, itemCount));
        return Math.max(36, (count - 1) * 36);
    }

    private static int edgeCenterAngle(int edge) {
        switch (edge) {
            case EDGE_LEFT:
                return 0;
            case EDGE_TOP:
                return 90;
            case EDGE_RIGHT:
                return 180;
            case EDGE_BOTTOM:
                return 270;
            default:
                return 0;
        }
    }

    static int[] displayOrder(int edge, int itemCount) {
        int count = Math.max(0, Math.min(5, itemCount));
        int[] natural = new int[count];
        for (int index = 0; index < count; index++) {
            natural[index] = index;
        }
        return natural;
    }

    static PointF itemCenter(
            int edge,
            float originX,
            float originY,
            float containerSize,
            float radius,
            int index,
            int itemCount) {
        float[] values = itemCenterValues(
                edge, originX, originY, containerSize, radius, index, itemCount);
        return new PointF(values[0], values[1]);
    }

    static float[] itemCenterValues(
            int edge,
            float originX,
            float originY,
            float containerSize,
            float radius,
            int index,
            int itemCount) {
        // Each layout centers its circle on the chosen screen edge and places
        // target centers at equal angular intervals along the visible half.
        int count = Math.max(1, Math.min(5, itemCount));
        float centeredOffset = (index - (count - 1) / 2f) * 36f;
        float degrees = itemAngle(edge, centeredOffset);
        float angle = (float) Math.toRadians(degrees);
        float depth = containerSize / 2f;
        float centerX;
        float centerY;
        switch (edge) {
            case EDGE_LEFT:
                centerX = originX;
                centerY = originY + containerSize / 2f;
                break;
            case EDGE_RIGHT:
                centerX = originX + depth;
                centerY = originY + containerSize / 2f;
                break;
            case EDGE_TOP:
                centerX = originX + containerSize / 2f;
                centerY = originY;
                break;
            case EDGE_BOTTOM:
                centerX = originX + containerSize / 2f;
                centerY = originY + depth;
                break;
            default:
                centerX = originX;
                centerY = originY + containerSize / 2f;
                break;
        }
        return new float[]{
                centerX + radius * (float) Math.cos(angle),
                centerY + radius * (float) Math.sin(angle)};
    }

    private static float itemAngle(int edge, float centeredOffset) {
        switch (edge) {
            case EDGE_LEFT:
                return centeredOffset;
            case EDGE_TOP:
                return 90f - centeredOffset;
            case EDGE_RIGHT:
                return 180f - centeredOffset;
            case EDGE_BOTTOM:
                return 270f + centeredOffset;
            default:
                return centeredOffset;
        }
    }

    static Rect containerRect(
            int edge,
            float pointer,
            int width,
            int height,
            int containerSize,
            int topInset,
            int bottomInset) {
        int[] bounds = containerBounds(
                edge, pointer, width, height, containerSize, topInset, bottomInset);
        return new Rect(bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    static int[] containerBounds(
            int edge,
            float pointer,
            int width,
            int height,
            int containerSize,
            int topInset,
            int bottomInset) {
        if (width <= 0 || height <= 0) {
            return new int[]{0, 0, 0, 0};
        }
        int safeTop = Math.max(0, topInset);
        int safeBottom = Math.max(0, bottomInset);
        int size = Math.max(1, Math.min(
                Math.max(1, containerSize),
                Math.min(Math.max(1, width), Math.max(1, height - safeTop - safeBottom))));
        int left;
        int top;
        int depth = Math.max(1, size / 2);
        int right;
        int bottom;
        switch (edge) {
            case EDGE_LEFT:
                left = 0;
                top = Math.round(pointer - size / 2f);
                right = depth;
                bottom = top + size;
                break;
            case EDGE_RIGHT:
                left = width - depth;
                top = Math.round(pointer - size / 2f);
                right = width;
                bottom = top + size;
                break;
            case EDGE_TOP:
                left = Math.round(pointer - size / 2f);
                top = safeTop;
                right = left + size;
                bottom = top + depth;
                break;
            case EDGE_BOTTOM:
                left = Math.round(pointer - size / 2f);
                top = height - safeBottom - depth;
                right = left + size;
                bottom = height - safeBottom;
                break;
            default:
                left = 0;
                top = Math.round(pointer - size / 2f);
                right = depth;
                bottom = top + size;
                break;
        }
        int minTop = safeTop;
        int maxTop = Math.max(minTop, height - safeBottom - size);
        if (edge == EDGE_LEFT || edge == EDGE_RIGHT || edge == EDGE_NONE) {
            top = clamp(top, minTop, maxTop);
            bottom = top + size;
        } else {
            left = clamp(left, 0, Math.max(0, width - size));
            right = left + size;
        }
        return new int[]{left, top, right, bottom};
    }

    static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

package com.leaf.hyperdragshare.codex;

final class GestureMath {
    private static final float EDGE_SCROLL_ENTRY_SPEED_MULTIPLIER = 0.2f;

    private GestureMath() {}

    static int initialMenuPosition(int configuredPosition, float touchX, int screenWidth) {
        if (configuredPosition >= DragShareSettings.SIMPLE_MENU_POSITION_TOP
                && configuredPosition <= DragShareSettings.SIMPLE_MENU_POSITION_RIGHT) {
            return configuredPosition;
        }
        if (configuredPosition == DragShareSettings.SIMPLE_MENU_POSITION_NEAR_HAND) {
            return touchX < Math.max(1, screenWidth) / 2f
                    ? DragShareSettings.SIMPLE_MENU_POSITION_LEFT
                    : DragShareSettings.SIMPLE_MENU_POSITION_RIGHT;
        }
        return DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM;
    }

    static boolean followsDragEdge(int configuredPosition) {
        return configuredPosition == DragShareSettings.SIMPLE_MENU_POSITION_DRAG_EDGE;
    }

    static boolean shouldShowMenu(float pointerY, int triggerTop) {
        return pointerY >= triggerTop;
    }

    static boolean shouldShowEdgeMenu(
            int menuPosition,
            float pointerX,
            float pointerY,
            int screenWidth,
            int triggerWidth,
            int menuTop,
            int menuTriggerTop) {
        int trigger = Math.max(0, triggerWidth);
        switch (menuPosition) {
            case DragShareSettings.SIMPLE_MENU_POSITION_TOP:
                return pointerY <= menuTop + trigger;
            case DragShareSettings.SIMPLE_MENU_POSITION_LEFT:
                return pointerX <= trigger;
            case DragShareSettings.SIMPLE_MENU_POSITION_RIGHT:
                return pointerX >= screenWidth - trigger;
            case DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM:
            default:
                return pointerY >= menuTriggerTop;
        }
    }

    static int edgeScrollDirection(float pointerX, int viewportWidth, int edgeWidth) {
        if (viewportWidth <= 0 || edgeWidth <= 0) {
            return 0;
        }
        if (pointerX <= edgeWidth) {
            return -1;
        }
        if (pointerX >= viewportWidth - edgeWidth) {
            return 1;
        }
        return 0;
    }

    static float edgeScrollSpeedMultiplier(
            float pointerCoordinate,
            int viewportSize,
            int edgeWidth,
            int fullSpeedInset) {
        int direction = edgeScrollDirection(pointerCoordinate, viewportSize, edgeWidth);
        if (direction == 0) {
            return 0f;
        }

        // Motion coordinates end at viewportSize - 1. Keep the physical outer edge at 1x.
        float maximumDistanceFromEdge = direction < 0
                ? edgeWidth
                : Math.max(0, edgeWidth - 1);
        if (maximumDistanceFromEdge == 0f) {
            return 1f;
        }
        float distanceFromEdge = direction < 0
                ? Math.max(0f, pointerCoordinate)
                : Math.max(0f, viewportSize - 1f - pointerCoordinate);
        float clampedFullSpeedInset = Math.min(
                maximumDistanceFromEdge,
                Math.max(0, fullSpeedInset));
        float accelerationDistance = maximumDistanceFromEdge - clampedFullSpeedInset;
        if (accelerationDistance == 0f) {
            return 1f;
        }
        float depth = clamp01(
                (maximumDistanceFromEdge - distanceFromEdge) / accelerationDistance);
        return EDGE_SCROLL_ENTRY_SPEED_MULTIPLIER
                + (1f - EDGE_SCROLL_ENTRY_SPEED_MULTIPLIER) * depth * depth;
    }

    static int clamp(int value, int minimum, int maximum) {
        if (maximum < minimum) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    static boolean samePosition(int currentX, int currentY, int nextX, int nextY) {
        return currentX == nextX && currentY == nextY;
    }

    static int previewLeft(float pointerX, int previewWidth, int screenWidth, int margin) {
        return clamp(
                Math.round(pointerX - previewWidth / 2f),
                margin,
                Math.max(margin, screenWidth - previewWidth - margin));
    }

    static int previewTop(
            float pointerY,
            int previewHeight,
            int fingerOffset,
            int minimumTop,
            int maximumTop) {
        return clamp(
                Math.round(pointerY - previewHeight - fingerOffset),
                minimumTop,
                Math.max(minimumTop, maximumTop));
    }

    static float dragPullProgress(float pointerY, float startY, float endY) {
        if (endY <= startY) {
            return pointerY >= endY ? 1f : 0f;
        }
        return clamp01((pointerY - startY) / (endY - startY));
    }

    static float portalItemScale(float pointerX, float itemCenterX, float itemWidth) {
        float distanceInItems = Math.abs(pointerX - itemCenterX) / Math.max(1f, itemWidth);
        return Math.max(1f, 1.23f - distanceInItems * 0.13f);
    }

    static boolean nearHandMenuOnRight(float tilt) {
        return tilt > 0f;
    }

    static float[] mapRawPoint(
            float rawX,
            float rawY,
            int rawMaxX,
            int rawMaxY,
            int screenWidth,
            int screenHeight,
            int rotation) {
        float normalizedX = clamp01(rawX / Math.max(1, rawMaxX));
        float normalizedY = clamp01(rawY / Math.max(1, rawMaxY));
        float width = Math.max(1, screenWidth - 1);
        float height = Math.max(1, screenHeight - 1);
        switch (rotation) {
            case 1:
                return new float[]{normalizedY * width, (1f - normalizedX) * height};
            case 2:
                return new float[]{(1f - normalizedX) * width, (1f - normalizedY) * height};
            case 3:
                return new float[]{(1f - normalizedY) * width, normalizedX * height};
            case 0:
            default:
                return new float[]{normalizedX * width, normalizedY * height};
        }
    }

    /** Select the closest touched edge; retain the current edge on exact corner ties. */
    static int nearestMenuEdge(float x, float y, int width, int top, int bottom,
            int trigger, int current) {
        float[] distances = {Math.max(0f, y - top), Math.max(0f, bottom - y),
                Math.max(0f, x), Math.max(0f, width - x)};
        int edge = current >= 0 && current < 4 ? current : 0;
        for (int i = 0; i < distances.length; i++) {
            if (distances[i] < distances[edge]) edge = i;
        }
        return distances[edge] <= trigger ? edge : -1;
    }

    /**
     * Preserve the central bottom input drop area except where it overlaps an
     * active bottom menu's actual edge-trigger band.
     */
    static boolean shouldDeferToBottomInputDropZone(float x, float y, int width,
            int height, int bottomInset, int zoneHeight, int bottomTriggerHeight, int menuPosition) {
        boolean inInputDropArea = y >= height - bottomInset - zoneHeight
                && x >= width * 0.15f
                && x <= width * 0.85f;
        boolean inBottomMenuTrigger = menuPosition == DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM
                && y >= height - bottomInset - bottomTriggerHeight;
        return inInputDropArea && !inBottomMenuTrigger;
    }

    static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}

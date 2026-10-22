package com.leaf.hyperdragshare.codex;

/** Shared compact bounds for the three linear drag menus, in screen pixels. */
final class LinearMenuGeometry {
    static final int ITEM_WIDTH_DP = 64;
    static final int ITEM_HEIGHT_DP = 68;
    static final int HEIGHT_DP = 76;
    static final int SIDE_WIDTH_DP = 80;

    private LinearMenuGeometry() {}

    static int[] bounds(int position, int width, int height, int topInset, int bottomInset,
                        int margin, int count, float density) {
        int visibleCount = Math.max(1, Math.min(5, count));
        int availableWidth = Math.max(1, width - margin * 2);
        int availableHeight = Math.max(1, height - topInset - bottomInset - margin * 2);
        boolean vertical = position == DragShareSettings.SIMPLE_MENU_POSITION_LEFT
                || position == DragShareSettings.SIMPLE_MENU_POSITION_RIGHT;
        int menuWidth = Math.min(availableWidth, Math.round(density * (vertical
                ? SIDE_WIDTH_DP : ITEM_WIDTH_DP * visibleCount + 8)));
        int menuHeight = Math.min(availableHeight, Math.round(density * (vertical
                ? ITEM_HEIGHT_DP * visibleCount + 8 : HEIGHT_DP)));
        float centerX = width / 2f;
        float centerY = (topInset + height - bottomInset) / 2f;
        int left = vertical
                ? (position == DragShareSettings.SIMPLE_MENU_POSITION_LEFT ? margin : width - margin - menuWidth)
                : GestureMath.clamp(Math.round(centerX - menuWidth / 2f), margin, width - margin - menuWidth);
        int top = vertical
                ? GestureMath.clamp(Math.round(centerY - menuHeight / 2f), topInset + margin,
                        height - bottomInset - margin - menuHeight)
                : (position == DragShareSettings.SIMPLE_MENU_POSITION_TOP ? topInset + margin
                        : height - bottomInset - margin - menuHeight);
        return new int[]{left, top, menuWidth, menuHeight};
    }
}

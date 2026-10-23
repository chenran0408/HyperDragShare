package com.leaf.hyperdragshare.codex;

import android.view.WindowManager;

/** Flag updates needed while a drag passes through the native sidebar window. */
final class SidebarDragWindowFlags {
    private SidebarDragWindowFlags() {}

    static int allowDropThrough(int flags) {
        return flags | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    }

    static int restoreTouchability(int currentFlags, boolean wasNotTouchable) {
        return wasNotTouchable
                ? currentFlags | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                : currentFlags & ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    }
}

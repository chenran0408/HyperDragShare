package com.leaf.hyperdragshare.codex;

/** Pure mode mapping shared by the settings UI and the framework-hook tests. */
public final class BackTapMode {
    public static final int OFF = 0;
    public static final int DOUBLE_TAP = 1;
    public static final int TRIPLE_TAP = 2;
    public static final int DOUBLE_AND_TRIPLE_TAP = 3;

    private BackTapMode() {}

    public static boolean handles(int mode, boolean doubleTap) {
        return mode == DOUBLE_AND_TRIPLE_TAP
                || (mode == DOUBLE_TAP && doubleTap)
                || (mode == TRIPLE_TAP && !doubleTap);
    }

    public static int normalize(int mode) {
        return mode >= OFF && mode <= DOUBLE_AND_TRIPLE_TAP ? mode : OFF;
    }
}

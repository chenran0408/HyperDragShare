package com.leaf.hyperdragshare.codex;

import android.graphics.Color;

/** Local contrast treatment for the module-owned SecurityCenter stash rail button. */
final class SidebarStashIconStyle {
    static final int ICON_COLOR = Color.WHITE;
    static final int IDLE_SURFACE = 0x99000000;
    static final int ACTIVE_SURFACE = 0xB3000000;
    static final int RIPPLE_COLOR = 0x44FFFFFF;

    private SidebarStashIconStyle() {}

    static int surface(boolean active) {
        return active ? ACTIVE_SURFACE : IDLE_SURFACE;
    }
}

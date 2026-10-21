package com.leaf.hyperdragshare.gestures.xposed;

final class SystemPanelArbitrator {
    private SystemPanelArbitrator() { }

    static boolean acceptShadeExpansion(boolean alreadyExpanded, long now,
                                        long trustedTouchUntil) {
        return alreadyExpanded || now < trustedTouchUntil;
    }
}

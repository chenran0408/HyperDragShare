package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AlertWindowNotificationGuardTest {
    @Test public void suppressesOnlyThisModulesFrameworkNotice() {
        assertTrue(AlertWindowNotificationGuard.shouldSuppress(BuildConfig.APPLICATION_ID));
        assertFalse(AlertWindowNotificationGuard.shouldSuppress(
                BuildConfig.APPLICATION_ID + ".debug"));
        assertFalse(AlertWindowNotificationGuard.shouldSuppress("com.miui.contentextension"));
        assertFalse(AlertWindowNotificationGuard.shouldSuppress(null));
    }
}

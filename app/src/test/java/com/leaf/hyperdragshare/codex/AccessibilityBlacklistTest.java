package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class AccessibilityBlacklistTest {
    @Test public void hyperOsLauncherIsAlwaysExcluded() {
        assertTrue(AccessibilityBlacklist.builtInPackages(
                RuntimeEnvironment.getApplication()).contains("com.miui.home"));
    }
}

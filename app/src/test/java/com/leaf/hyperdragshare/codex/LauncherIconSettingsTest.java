package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.PackageManager;

import org.junit.Test;

public class LauncherIconSettingsTest {
    @Test
    public void launcherAliasEnabledStateReflectsPackageManagerDefaults() {
        assertTrue(LauncherIconSettings.isEnabledState(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT));
        assertTrue(LauncherIconSettings.isEnabledState(PackageManager.COMPONENT_ENABLED_STATE_ENABLED));
        assertFalse(LauncherIconSettings.isEnabledState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED));
        assertFalse(LauncherIconSettings.isEnabledState(PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER));
    }
}

package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class PortalPressSettingsTest {
    @Test
    public void readablePortalSystemModesOverrideModuleFallbackOnlyWhenSupported() {
        Context context = RuntimeEnvironment.getApplication();
        try {
            put(context, 1, PortalPressSettings.MODE_SINGLE_FINGER);
            PortalPressSettings.Snapshot single = PortalPressSettings.read(context);
            assertTrue(single.available);
            assertTrue(single.supportsTwoFinger);
            assertFalse(single.resolveTwoFingerEnabled(true));
            assertEquals(PortalPressSettings.SOURCE_SYSTEM, single.sourceLabel);

            put(context, 1, PortalPressSettings.MODE_TWO_FINGER);
            PortalPressSettings.Snapshot two = PortalPressSettings.read(context);
            assertTrue(two.systemSelectedTwoFinger);
            assertTrue(two.resolveTwoFingerEnabled(false));

            put(context, 0, PortalPressSettings.MODE_SINGLE_FINGER);
            PortalPressSettings.Snapshot unsupported = PortalPressSettings.read(context);
            assertTrue(unsupported.available);
            assertFalse(unsupported.supportsTwoFinger);
            assertFalse(unsupported.resolveTwoFingerEnabled(true));

            put(context, 0, PortalPressSettings.MODE_TWO_FINGER);
            PortalPressSettings.Snapshot inconsistent = PortalPressSettings.read(context);
            assertFalse(inconsistent.available);
            assertTrue(inconsistent.resolveTwoFingerEnabled(true));
        } finally {
            clear(context);
        }
    }

    @Test
    public void missingUnreadableAndUnknownSystemSettingsUseModuleFallback() {
        Context context = RuntimeEnvironment.getApplication();
        try {
            clear(context);
            PortalPressSettings.Snapshot missing = PortalPressSettings.read(context);
            assertFalse(missing.available);
            assertTrue(missing.resolveTwoFingerEnabled(true));
            assertEquals(PortalPressSettings.SOURCE_MODULE, missing.sourceLabel);

            put(context, 1, 99);
            PortalPressSettings.Snapshot unknown = PortalPressSettings.read(context);
            assertFalse(unknown.available);
            assertFalse(unknown.resolveTwoFingerEnabled(false));

            PortalPressSettings.Snapshot denied = PortalPressSettings.read(key -> {
                throw new SecurityException("settings are not readable");
            });
            assertFalse(denied.available);
            assertTrue(denied.resolveTwoFingerEnabled(true));
        } finally {
            clear(context);
        }
    }

    @Test
    public void settingsObserverRefreshesBothKeysAndStopsAfterClose() {
        Context context = RuntimeEnvironment.getApplication();
        try {
            put(context, 1, PortalPressSettings.MODE_SINGLE_FINGER);
            AtomicInteger changes = new AtomicInteger();
            PortalPressSettings.Subscription subscription = PortalPressSettings.observe(
                    context, new Handler(Looper.getMainLooper()), changes::incrementAndGet);

            Settings.System.putInt(context.getContentResolver(),
                    PortalPressSettings.KEY_SUPPORT_LONG_PRESS_MODE,
                    PortalPressSettings.MODE_TWO_FINGER);
            ShadowLooper.shadowMainLooper().idle();
            int afterModeChange = changes.get();
            assertTrue(afterModeChange > 0);
            assertTrue(PortalPressSettings.read(context).systemSelectedTwoFinger);

            Settings.System.putInt(context.getContentResolver(),
                    PortalPressSettings.KEY_SUPPORT_DOUBLE_PRESS_MODE, 0);
            subscription.close();
            ShadowLooper.shadowMainLooper().idle();
            assertEquals(afterModeChange, changes.get());
        } finally {
            clear(context);
        }
    }

    private static void put(Context context, int support, int mode) {
        Settings.System.putInt(context.getContentResolver(),
                PortalPressSettings.KEY_SUPPORT_DOUBLE_PRESS_MODE, support);
        Settings.System.putInt(context.getContentResolver(),
                PortalPressSettings.KEY_SUPPORT_LONG_PRESS_MODE, mode);
    }

    private static void clear(Context context) {
        Settings.System.putString(context.getContentResolver(),
                PortalPressSettings.KEY_SUPPORT_DOUBLE_PRESS_MODE, null);
        Settings.System.putString(context.getContentResolver(),
                PortalPressSettings.KEY_SUPPORT_LONG_PRESS_MODE, null);
    }
}

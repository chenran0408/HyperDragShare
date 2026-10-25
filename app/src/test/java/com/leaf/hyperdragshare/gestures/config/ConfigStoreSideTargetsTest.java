package com.leaf.hyperdragshare.gestures.config;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.os.Bundle;
import org.json.JSONArray;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public final class ConfigStoreSideTargetsTest {
    @Test
    public void sideTargetsRoundTripThroughExistingPreferenceKey() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ConfigStore store = new ConfigStore(context);
        List<AppTarget> expected = Arrays.asList(
                new AppTarget("com.example.one/.MainActivity"),
                AppTarget.shortcut("com.example.two", "open", "Open"));

        store.setSideTargets(expected);

        assertEquals(expected, store.getSideTargets());
        JSONArray stored = new JSONArray(store.preferences().getString(ConfigContract.KEY_SIDE_COMPONENTS, "[]"));
        assertEquals("com.example.one/.MainActivity", stored.getJSONObject(0).getString("component"));
    }

    @Test
    public void bottomGapIsReadThroughProviderAndClampedAtRuntimeBoundary() {
        Context context = RuntimeEnvironment.getApplication();
        ConfigStore store = new ConfigStore(context);
        ConfigProvider provider = new ConfigProvider();
        provider.attachInfo(context, new ProviderInfo());

        store.putInt(ConfigContract.KEY_HOT_BOTTOM_GAP_DP, 48);
        ShadowLooper.shadowMainLooper().idleFor(120, TimeUnit.MILLISECONDS);
        Bundle configured = provider.call("get", null, null);
        assertEquals(48, configured.getInt(ConfigContract.KEY_HOT_BOTTOM_GAP_DP));

        store.putInt(ConfigContract.KEY_HOT_BOTTOM_GAP_DP,
                ConfigContract.MAX_HOT_BOTTOM_GAP_DP + 1);
        ShadowLooper.shadowMainLooper().idleFor(120, TimeUnit.MILLISECONDS);
        Bundle clamped = provider.call("get", null, null);
        assertEquals(ConfigContract.MAX_HOT_BOTTOM_GAP_DP,
                clamped.getInt(ConfigContract.KEY_HOT_BOTTOM_GAP_DP));
    }
}

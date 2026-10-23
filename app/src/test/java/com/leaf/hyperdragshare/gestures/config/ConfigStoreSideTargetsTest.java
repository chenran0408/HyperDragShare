package com.leaf.hyperdragshare.gestures.config;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import org.json.JSONArray;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

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
}

package com.leaf.hyperdragshare.gestures.config;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConfigUpgradePreservationTest {
    @Test public void storedFalseZeroAndEmptyListsOverrideNewDefaults() {
        Map<String, Object> data = new HashMap<>();
        data.put("enabled", false);
        data.put("distance", 0);
        data.put("apps", "[]");
        SharedPreferences prefs = preferences(data);
        MissingPreferenceEditor.edit(prefs).putBoolean("enabled", true)
                .putInt("distance", 20).putString("apps", "[new-default]")
                .putInt("new-feature", 7).commit();
        assertEquals(false, data.get("enabled"));
        assertEquals(0, data.get("distance"));
        assertEquals("[]", data.get("apps"));
        assertEquals(7, data.get("new-feature"));
    }

    @Test public void upgradeKeepsSavedSettingsEvenIfMigrationMarkersAreMissing() {
        Map<String, Object> data = new HashMap<>();
        data.put(ConfigContract.KEY_COMPONENTS, "[{\"component\":\"my.app/.Main\"}]");
        data.put(ConfigContract.KEY_HONEYCOMB_COMPONENTS, "[]");
        data.put(ConfigContract.KEY_SIDE_COMPONENTS, "[]");
        data.put(ConfigContract.KEY_PRESSURE_COMPONENTS, "[]");
        data.put(ConfigContract.KEY_PRESSURE_TRIGGERS, "[{\"id\":3}]");
        data.put(ConfigContract.KEY_SIDE_FOLLOW_HONEYCOMB, true);
        data.put(ConfigContract.KEY_SIDE_GESTURE_ENABLED, false);
        data.put(ConfigContract.KEY_SIDE_PORTRAIT_ENABLED, false);
        data.put(ConfigContract.KEY_SIDE_PORTRAIT_LAYOUT_MODE, ConfigContract.SIDE_LAYOUT_TASKS);
        data.put(ConfigContract.KEY_SIDE_LANDSCAPE_LAYOUT_MODE, ConfigContract.SIDE_LAYOUT_RING);
        data.put(ConfigContract.KEY_SIDE_ANIMATION_SPEED, 88);
        data.put(ConfigContract.KEY_BOTTOM_PORTRAIT_SECOND_STAGE_ENABLED, false);
        data.put(ConfigContract.KEY_BOTTOM_PORTRAIT_FULLSCREEN, true);
        data.put(ConfigContract.KEY_OUTSIDE_SINGLE_ACTION, ConfigContract.ACTION_NONE);
        data.put(ConfigContract.KEY_OUTSIDE_DOUBLE_ACTION, ConfigContract.ACTION_CLOSE);
        data.put("lower_double_action", ConfigContract.ACTION_EDGE_PIN);
        data.put(ConfigContract.KEY_SHOW_SELECTED_APP_NAME, false);
        data.put(ConfigContract.KEY_SIDE_LAYOUT_MODE, ConfigContract.SIDE_LAYOUT_TASKS);
        data.put(ConfigContract.KEY_HONEYCOMB_APP_BACKGROUND_ENABLED, true);
        data.put(ConfigContract.KEY_HONEYCOMB_LIVE_BLUR_ENABLED, true);
        data.put(ConfigContract.KEY_HONEYCOMB_LIVE_BLUR_DP, 63);
        data.put(ConfigContract.KEY_HONEYCOMB_BACKGROUND_DIM_PERCENT, 47);
        data.put(ConfigContract.KEY_PRESSURE_THRESHOLD, .62f);
        data.put(ConfigContract.KEY_PRESSURE_CALIBRATED, true);
        data.put(ConfigContract.KEY_PRESSURE_CALIBRATION_VALID_COUNT, 5);
        data.put(ConfigContract.KEY_PORTRAIT_NATIVE_SCALE_PERCENT, 103);
        data.put("future_feature_unknown_to_this_version", "keep-me");
        Map<String, Object> original = new HashMap<>(data);
        SharedPreferences prefs = preferences(data);
        migrate(prefs);
        for (Map.Entry<String, Object> setting : original.entrySet()) {
            assertEquals("Changed stored key " + setting.getKey(), setting.getValue(),
                    data.get(setting.getKey()));
        }
        Map<String, Object> firstUpgrade = new HashMap<>(data);
        migrate(prefs);
        assertEquals("Repeated startup must be idempotent", firstUpgrade, data);
    }

    @Test public void existingTargetListsSkipFreshInstallSeeding() {
        Map<String, Object> data = new HashMap<>();
        data.put(ConfigContract.KEY_COMPONENTS, "[]");
        data.put(ConfigContract.KEY_HONEYCOMB_COMPONENTS, "[saved-honeycomb]");
        data.put(ConfigContract.KEY_SIDE_COMPONENTS, "[saved-side]");
        Map<String, Object> before = new HashMap<>(data);
        // Null Context deliberately proves that no app query or reseeding occurs.
        ConfigStore.seedDefaultsIfNeeded(null, preferences(data));
        assertEquals(before, data);
    }

    private static void migrate(SharedPreferences prefs) {
        ConfigStore.migrateUnifiedActionsIfNeeded(prefs);
        ConfigStore.migrateDoubleTapPinIfNeeded(prefs);
        ConfigStore.migrateSideLayoutModeIfNeeded(prefs);
        ConfigStore.migrateSelectedAppNameIfNeeded(prefs);
        ConfigStore.migrateIndependentSideTargetsIfNeeded(prefs);
        ConfigStore.migrateOrientationBehaviorIfNeeded(prefs);
        ConfigStore.migrateHoneycombBackgroundIfNeeded(prefs);
        ConfigStore.migrateHoneycombWallpaperDefaultIfNeeded(prefs);
        ConfigStore.ensureNativeWindowScaleDefaults(prefs);
    }

    private static SharedPreferences preferences(Map<String, Object> values) {
        return (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.equals("contains")) return values.containsKey(args[0]);
                    if (name.equals("getAll")) return new HashMap<>(values);
                    if (name.startsWith("get")) return values.getOrDefault(args[0], args[1]);
                    if (name.equals("edit")) return editor(values);
                    if (name.equals("toString")) return "MemoryPreferences";
                    return null;
                });
    }

    private static SharedPreferences.Editor editor(Map<String, Object> values) {
        Map<String, Object> pending = new HashMap<>();
        return (SharedPreferences.Editor) Proxy.newProxyInstance(
                SharedPreferences.Editor.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.Editor.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    if (name.startsWith("put")) { pending.put((String) args[0], args[1]); return proxy; }
                    if (name.equals("commit") || name.equals("apply")) {
                        values.putAll(pending);
                        pending.clear();
                        return name.equals("commit") ? true : null;
                    }
                    throw new AssertionError("Unexpected destructive editor operation: " + name);
                });
    }
}

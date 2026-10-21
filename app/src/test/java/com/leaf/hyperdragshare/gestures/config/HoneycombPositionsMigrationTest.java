package com.leaf.hyperdragshare.gestures.config;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public final class HoneycombPositionsMigrationTest {
    private SharedPreferences prefs(Map<String, Object> values) {
        SharedPreferences.Editor editor = (SharedPreferences.Editor) Proxy.newProxyInstance(
                SharedPreferences.Editor.class.getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("putString")) { values.put((String) args[0], args[1]); return proxy; }
                    if (method.getName().equals("apply")) return null;
                    throw new AssertionError(method.getName());
                });
        return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    if (method.getName().equals("edit")) return editor;
                    if (method.getName().equals("getString") || method.getName().equals("getInt"))
                        return values.getOrDefault(args[0], args[1]);
                    throw new AssertionError(method.getName());
                });
    }

    @Test public void upgradeAddsOnlyPositionsAndRepeatedStartupPreservesManualSlots() throws Exception {
        JSONArray targets = new JSONArray();
        for (int user : new int[]{0, 999}) targets.put(new JSONObject()
                .put("type", "shortcut").put("package", "pkg").put("id", "same-id").put("userId", user));
        Map<String, Object> values = new HashMap<>();
        values.put(ConfigContract.KEY_HONEYCOMB_COMPONENTS, targets.toString());
        values.put(ConfigContract.KEY_HONEYCOMB_MODE, 1);
        values.put("unknown", "keep");
        Map<String, Object> original = new HashMap<>(values);
        SharedPreferences prefs = prefs(values);
        ConfigStore.ensureHoneycombPositions(prefs);
        for (String key : original.keySet()) assertEquals(original.get(key), values.get(key));
        assertEquals(original.size() + 1, values.size());
        String a = HoneycombPositions.identity("", "pkg", "same-id", "", 0);
        String b = HoneycombPositions.identity("", "pkg", "same-id", "", 999);
        HoneycombPositions positions = HoneycombPositions.read(
                (String) values.get(ConfigContract.KEY_HONEYCOMB_POSITIONS), Arrays.asList(a, b), 29);
        assertNotNull(positions.find(a)); assertNotNull(positions.find(b));
        values.put(ConfigContract.KEY_HONEYCOMB_POSITIONS, positions.swap(0, 1).serialize());
        Map<String, Object> beforeRestart = new HashMap<>(values);
        ConfigStore.ensureHoneycombPositions(prefs);
        assertEquals(beforeRestart, values);
        AppTarget shortcut = AppTarget.fromJson(targets.getJSONObject(1));
        assertEquals(999, shortcut.userId);
        assertEquals(999, shortcut.toJson().getInt("userId"));
        assertEquals(b, shortcut.positionIdentity());
    }
}

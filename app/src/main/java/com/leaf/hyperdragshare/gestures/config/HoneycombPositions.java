package com.leaf.hyperdragshare.gestures.config;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persistent coordinates in units of icon pitch. Empty slots survive deletions. */
public final class HoneycombPositions {
    public static final class Slot {
        public final float x, y;
        public final String owner;
        Slot(float x, float y, String owner) { this.x = x; this.y = y; this.owner = owner; }
    }
    public final List<Slot> slots;
    private HoneycombPositions(List<Slot> slots) {
        this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
    }

    public static String identity(String component, String pkg, String id, String intent, int user) {
        JSONArray key = new JSONArray();
        key.put(Math.max(0, user));
        if (id != null && !id.isEmpty()) {
            key.put(intent == null || intent.isEmpty() ? "shortcut" : "launcher_shortcut");
            key.put(pkg); key.put(id); key.put(intent == null ? "" : intent);
        } else {
            key.put("activity");
            String value = component == null ? "" : component;
            int slash = value.indexOf('/');
            if (slash >= 0 && value.substring(slash + 1).startsWith(".")) {
                value = value.substring(0, slash + 1) + value.substring(0, slash)
                        + value.substring(slash + 1);
            }
            key.put(value);
        }
        return key.toString();
    }

    public static HoneycombPositions compact(List<String> owners, int visibleCount) {
        int count = Math.min(owners.size(), Math.max(0, visibleCount));
        ArrayList<Slot> slots = new ArrayList<>();
        List<HoneycombSlotLayout.Point> old = HoneycombSlotLayout.compactPoints(count, 1f);
        for (int i = 0; i < count; i++) slots.add(new Slot(old.get(i).x, old.get(i).y, owners.get(i)));
        return new HoneycombPositions(slots).reconcile(owners);
    }

    public static HoneycombPositions read(String raw, List<String> owners, int visibleCount) {
        try {
            JSONObject object = new JSONObject(raw);
            if (object.getInt("version") != 1) throw new IllegalArgumentException("Unknown positions");
            JSONArray array = object.getJSONArray("slots");
            if (array.length() > ConfigContract.MAX_HONEYCOMB_MAX_TARGETS) throw new IllegalArgumentException();
            ArrayList<Slot> slots = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject value = array.getJSONObject(i);
                float x = (float) value.getDouble("x"), y = (float) value.getDouble("y");
                String owner = value.optString("owner", "");
                if (!Float.isFinite(x) || !Float.isFinite(y) || Math.abs(x) > 100 || Math.abs(y) > 100)
                    throw new IllegalArgumentException();
                for (Slot slot : slots) if (distance(slot.x, slot.y, x, y) < .95f) throw new IllegalArgumentException();
                if (!owner.isEmpty() && !seen.add(owner)) throw new IllegalArgumentException();
                slots.add(new Slot(x, y, owner));
            }
            return new HoneycombPositions(slots).reconcile(owners);
        } catch (Exception ignored) {
            return compact(owners, visibleCount);
        }
    }

    public HoneycombPositions reconcile(List<String> owners) {
        ArrayList<Slot> next = new ArrayList<>();
        Set<String> active = new HashSet<>(owners), assigned = new HashSet<>();
        for (Slot slot : slots) {
            String owner = active.contains(slot.owner) ? slot.owner : "";
            if (!owner.isEmpty()) assigned.add(owner);
            next.add(new Slot(slot.x, slot.y, owner));
        }
        for (String owner : owners) {
            if (owner.isEmpty() || !assigned.add(owner)) continue;
            int vacant = -1;
            float radius = Float.MAX_VALUE;
            for (int i = 0; i < next.size(); i++) {
                Slot slot = next.get(i);
                float r = slot.x * slot.x + slot.y * slot.y;
                if (slot.owner.isEmpty() && r < radius) { vacant = i; radius = r; }
            }
            if (vacant >= 0) {
                Slot slot = next.get(vacant);
                next.set(vacant, new Slot(slot.x, slot.y, owner));
            } else {
                if (next.size() >= ConfigContract.MAX_HONEYCOMB_MAX_TARGETS) break;
                HoneycombSlotLayout.Point point = extension(next);
                next.add(new Slot(point.x, point.y, owner));
            }
        }
        return new HoneycombPositions(next);
    }

    private static HoneycombSlotLayout.Point extension(List<Slot> slots) {
        if (slots.isEmpty()) return new HoneycombSlotLayout.Point(0, 0);
        float bestX = 0, bestY = 0, bestRadius = Float.MAX_VALUE;
        float[] dx = {1, -1, .5f, -.5f, .5f, -.5f};
        float[] dy = {0, 0, .8660254f, .8660254f, -.8660254f, -.8660254f};
        for (Slot source : slots) for (int i = 0; i < 6; i++) {
            float x = source.x + dx[i], y = source.y + dy[i];
            boolean free = true;
            for (Slot slot : slots) if (distance(slot.x, slot.y, x, y) < .95f) { free = false; break; }
            float radius = x * x + y * y;
            if (free && radius < bestRadius) { bestX = x; bestY = y; bestRadius = radius; }
        }
        return new HoneycombSlotLayout.Point(bestX, bestY);
    }

    private static float distance(float x, float y, float xx, float yy) {
        return (float) Math.hypot(x - xx, y - yy);
    }

    public HoneycombPositions swap(int first, int second) {
        if (first < 0 || second < 0 || first >= slots.size() || second >= slots.size()) return this;
        ArrayList<Slot> next = new ArrayList<>(slots);
        Slot a = slots.get(first), b = slots.get(second);
        next.set(first, new Slot(a.x, a.y, b.owner));
        next.set(second, new Slot(b.x, b.y, a.owner));
        return new HoneycombPositions(next);
    }

    public Slot find(String owner) {
        for (Slot slot : slots) if (!owner.isEmpty() && owner.equals(slot.owner)) return slot;
        return null;
    }

    public String serialize() {
        JSONObject object = new JSONObject();
        JSONArray values = new JSONArray();
        try {
            for (Slot slot : slots) {
                JSONObject value = new JSONObject();
                value.put("x", slot.x); value.put("y", slot.y); value.put("owner", slot.owner);
                values.put(value);
            }
            object.put("version", 1); object.put("slots", values);
        } catch (Exception error) { throw new IllegalStateException(error); }
        return object.toString();
    }
}

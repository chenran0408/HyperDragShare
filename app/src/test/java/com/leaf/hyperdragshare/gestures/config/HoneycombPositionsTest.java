package com.leaf.hyperdragshare.gestures.config;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public final class HoneycombPositionsTest {
    private List<String> owners(int count) {
        ArrayList<String> values = new ArrayList<>();
        for (int i = 0; i < count; i++) values.add("app" + i);
        return values;
    }
    private void same(HoneycombPositions.Slot a, HoneycombPositions.Slot b) {
        assertNotNull(a); assertNotNull(b);
        assertEquals(a.x, b.x, .000001f); assertEquals(a.y, b.y, .000001f);
    }

    @Test public void migrationPreservesOldVisibleCoordinatesForEverySupportedCount() {
        for (int count = 1; count <= 60; count++) {
            List<String> owners = owners(count);
            HoneycombPositions migrated = HoneycombPositions.read("", owners, count);
            List<HoneycombSlotLayout.Point> old = HoneycombSlotLayout.compactPoints(count, 1f);
            for (int i = 0; i < count; i++) {
                assertEquals(old.get(i).x, migrated.find(owners.get(i)).x, .000001f);
                assertEquals(old.get(i).y, migrated.find(owners.get(i)).y, .000001f);
            }
            assertEquals(migrated.serialize(), HoneycombPositions.read(migrated.serialize(), owners, count).serialize());
        }
    }

    @Test public void deletionKeepsHolesAndAdditionFillsThemWithoutMovingSurvivors() {
        List<String> owners = owners(29);
        HoneycombPositions old = HoneycombPositions.compact(owners, 29);
        List<String> reduced = new ArrayList<>(owners); reduced.remove(7);
        HoneycombPositions deleted = old.reconcile(reduced);
        assertEquals(29, deleted.slots.size()); assertNull(deleted.find("app7"));
        for (String owner : reduced) same(old.find(owner), deleted.find(owner));
        reduced.add("new"); HoneycombPositions added = deleted.reconcile(reduced);
        same(old.find("app7"), added.find("new"));
        for (String owner : owners) if (!owner.equals("app7")) same(old.find(owner), added.find(owner));
    }

    @Test public void extendingFullLayoutsNeverMovesOrOverlapsExistingIcons() {
        for (int count = 1; count < 60; count++) {
            List<String> owners = owners(count);
            HoneycombPositions old = HoneycombPositions.compact(owners, count);
            owners.add("new"); HoneycombPositions next = old.reconcile(owners);
            for (int i = 0; i < count; i++) same(old.find("app" + i), next.find("app" + i));
            HoneycombPositions.Slot added = next.find("new");
            for (HoneycombPositions.Slot slot : old.slots)
                assertTrue(Math.hypot(slot.x - added.x, slot.y - added.y) >= .95);
            assertEquals(next.serialize(), HoneycombPositions.read(next.serialize(), owners, 60).serialize());
        }
    }

    @Test public void listReorderingAndVisibleLimitDoNotChangeCoordinates() {
        List<String> owners = owners(60);
        HoneycombPositions old = HoneycombPositions.compact(owners, 29);
        List<HoneycombSlotLayout.Point> visible = HoneycombSlotLayout.compactPoints(29, 1f);
        for (int i = 0; i < 29; i++) assertEquals(visible.get(i).x, old.find("app" + i).x, .000001f);
        Collections.reverse(owners);
        HoneycombPositions reordered = HoneycombPositions.read(old.serialize(), owners, 10);
        for (String owner : owners) same(old.find(owner), reordered.find(owner));
    }

    @Test public void swapPersistsIncludingAnEmptyDestination() {
        List<String> owners = owners(5);
        HoneycombPositions old = HoneycombPositions.compact(owners, 5);
        HoneycombPositions swapped = old.swap(0, 3);
        same(old.find("app0"), swapped.find("app3"));
        same(old.find("app3"), swapped.find("app0"));
        owners.remove("app3");
        HoneycombPositions hole = swapped.reconcile(owners).swap(0, 3);
        same(old.find("app0"), hole.find("app0"));
        assertEquals(hole.serialize(), HoneycombPositions.read(hole.serialize(), owners, 5).serialize());
    }

    @Test public void emptyListRetainsSlotsUntilExplicitCompaction() {
        HoneycombPositions old = HoneycombPositions.compact(owners(12), 12);
        HoneycombPositions empty = old.reconcile(Collections.emptyList());
        assertEquals(12, empty.slots.size());
        assertEquals(0, HoneycombPositions.compact(Collections.emptyList(), 12).slots.size());
        HoneycombPositions compact = HoneycombPositions.compact(Arrays.asList("app0", "app5"), 12);
        assertEquals(2, compact.slots.size());
    }

    @Test public void identitiesDistinguishUsersActivitiesAndShortcutVariants() {
        String app = HoneycombPositions.identity("pkg/.Main", "", "", "", 0);
        assertEquals(app, HoneycombPositions.identity("pkg/pkg.Main", "", "", "", 0));
        assertNotEquals(app, HoneycombPositions.identity("pkg/.Main", "", "", "", 999));
        String shortcut = HoneycombPositions.identity("", "pkg", "id", "", 0);
        assertNotEquals(app, shortcut);
        assertNotEquals(shortcut, HoneycombPositions.identity("", "pkg", "id", "", 999));
        assertNotEquals(shortcut, HoneycombPositions.identity("", "pkg", "id2", "", 0));
        assertNotEquals(shortcut, HoneycombPositions.identity("", "pkg", "id", "intent", 0));
    }

    @Test public void invalidSerializedPositionsRecoverWithoutLosingOwners() {
        List<String> owners = owners(3);
        String invalid = "{\"version\":1,\"slots\":[{\"x\":0,\"y\":0},{\"x\":0,\"y\":0}]}";
        HoneycombPositions positions = HoneycombPositions.read(invalid, owners, 3);
        assertEquals(3, positions.slots.size());
        for (String owner : owners) assertNotNull(positions.find(owner));
    }
}

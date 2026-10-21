package com.leaf.hyperdragshare.gestures.xposed;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class TaskSnapshotAccessTest {
    private final Object service = new Object();
    private final Object manager = new Object();
    private final Object snapshot = new Object();

    @Test public void modernManagerMissingFallsBackToLegacySnapshot() {
        List<String> calls = new ArrayList<>();
        Object result = TaskSnapshotAccess.load(service, 42, true, (owner, name, args) -> {
            calls.add(name);
            if (name.equals("getTaskSnapshotManager")) throw new NoSuchMethodError(name);
            assertSame(service, owner);
            assertArrayEquals(new Object[]{42, true}, args);
            return snapshot;
        });
        assertSame(snapshot, result);
        assertEquals(List.of("getTaskSnapshotManager", "getTaskSnapshot"), calls);
    }

    @Test public void missingLegacyApiFallsBackEvenWhenProfileSaysOs3() {
        Object result = TaskSnapshotAccess.load(service, 42, false, (owner, name, args) -> {
            if (name.equals("getTaskSnapshotManager")) return manager;
            if (owner == service) throw new NoSuchMethodError(name);
            assertSame(manager, owner);
            assertArrayEquals(new Object[]{42, 0L, 1}, args);
            return snapshot;
        });
        assertSame(snapshot, result);
    }

    @Test public void modernCacheMissTriesBothModesThenCaptures() {
        List<Object[]> requests = new ArrayList<>();
        Object result = TaskSnapshotAccess.load(service, 42, true, (owner, name, args) -> {
            if (name.equals("getTaskSnapshotManager")) return manager;
            requests.add(args);
            return name.equals("takeTaskSnapshot") ? snapshot : null;
        });
        assertSame(snapshot, result);
        assertEquals(3, requests.size());
        assertArrayEquals(new Object[]{42, 0L, 1}, requests.get(0));
        assertArrayEquals(new Object[]{42, 0L, 3}, requests.get(1));
        assertArrayEquals(new Object[]{42, false, false, false}, requests.get(2));
    }

    @Test public void unavailableProtocolsDoNotBreakTaskList() {
        assertNull(TaskSnapshotAccess.load(service, 42, true, (owner, name, args) -> {
            throw new SecurityException(name);
        }));
    }
}

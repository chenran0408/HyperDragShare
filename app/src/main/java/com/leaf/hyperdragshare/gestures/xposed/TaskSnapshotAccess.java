package com.leaf.hyperdragshare.gestures.xposed;

/** Keeps both task-snapshot protocols available independently of OS detection. */
final class TaskSnapshotAccess {
    interface Caller { Object call(Object owner, String method, Object... args); }

    private TaskSnapshotAccess() { }

    static Object load(Object service, int taskId, boolean preferModern, Caller caller) {
        Object snapshot = preferModern ? modern(service, taskId, caller)
                : legacy(service, taskId, caller);
        if (snapshot != null) return snapshot;
        return preferModern ? legacy(service, taskId, caller)
                : modern(service, taskId, caller);
    }

    private static Object legacy(Object service, int taskId, Caller caller) {
        try {
            Object snapshot = caller.call(service, "getTaskSnapshot", taskId, true);
            return snapshot != null ? snapshot
                    : caller.call(service, "takeTaskSnapshot", taskId, true);
        } catch (Throwable unavailable) {
            return null;
        }
    }

    private static Object modern(Object service, int taskId, Caller caller) {
        try {
            Object manager = caller.call(service, "getTaskSnapshotManager");
            if (manager == null) return null;
            Object snapshot = caller.call(manager, "getTaskSnapshot", taskId, 0L, 1);
            if (snapshot == null) {
                snapshot = caller.call(manager, "getTaskSnapshot", taskId, 0L, 3);
            }
            return snapshot != null ? snapshot
                    : caller.call(manager, "takeTaskSnapshot", taskId, false, false, false);
        } catch (Throwable unavailable) {
            return null;
        }
    }
}

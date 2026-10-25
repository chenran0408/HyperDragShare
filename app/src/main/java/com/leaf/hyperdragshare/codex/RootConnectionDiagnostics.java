package com.leaf.hyperdragshare.codex;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/** Bounded, allow-listed Root connection diagnostics, independent of optional file logging. */
final class RootConnectionDiagnostics {
    private static final int MAX_EVENTS = 80;
    private static final Deque<String> EVENTS = new ArrayDeque<>();

    private RootConnectionDiagnostics() { }

    static String newAttempt() {
        return "root-" + UUID.randomUUID();
    }

    static void event(String attempt, String outcome) {
        try { record(attempt, outcome, -1L, OperationTraceProcess.current(), System.currentTimeMillis()); }
        catch (RuntimeException ignored) { /* Diagnostics never gate connection work. */ }
    }

    static void retry(String attempt, long intervalMs) {
        try { record(attempt, "retry_scheduled", Math.max(0L, intervalMs),
                OperationTraceProcess.current(), System.currentTimeMillis()); }
        catch (RuntimeException ignored) { /* Diagnostics never gate connection work. */ }
    }

    static void importPortalEvent(String attempt, String proc, String outcome, long intervalMs, long atEpochMs) {
        if (!allowedPortalOutcome(outcome)) return;
        try { record(attempt, outcome, intervalMs, proc, atEpochMs); }
        catch (RuntimeException ignored) { /* Untrusted optional metadata cannot affect status. */ }
    }

    private static synchronized void record(String attempt, String outcome, long intervalMs,
                                            String proc, long atEpochMs) {
        if (!validAttempt(attempt) || !allowed(outcome) || !validProc(proc)
                || atEpochMs <= 0L || atEpochMs > System.currentTimeMillis() + 300_000L
                || ("retry_scheduled".equals(outcome)
                    ? intervalMs < 0L || intervalMs > 86_400_000L
                    : intervalMs != -1L)) return;
        String line = "at_epoch_ms=" + atEpochMs + " attempt=" + attempt
                + " proc=" + proc + " outcome=" + outcome;
        if (intervalMs >= 0L) line += " retry_interval_ms=" + Math.min(intervalMs, 86_400_000L);
        EVENTS.addLast(line);
        while (EVENTS.size() > MAX_EVENTS) EVENTS.removeFirst();
        DragShareLog.i("DragShare/RootConnection", line);
    }

    static synchronized String summary() {
        if (EVENTS.isEmpty()) return "No Root connection attempts recorded in this process.\n";
        StringBuilder result = new StringBuilder("Root connection events (bounded, allow-listed):\n");
        for (String event : EVENTS) result.append(event).append('\n');
        return result.toString();
    }

    static boolean validAttempt(String value) {
        return value != null && value.matches("root-[A-Fa-f0-9-]{36}");
    }

    static boolean validProc(String value) {
        return value != null && value.matches("[A-Za-z0-9_.:-]{1,128}");
    }

    static boolean allowedPortalOutcome(String value) {
        return "connect_failed".equals(value) || "lease_ready".equals(value)
                || "binder_death".equals(value) || "release".equals(value)
                || "retry_scheduled".equals(value);
    }

    private static boolean allowed(String value) {
        return "request".equals(value) || "helper_start".equals(value)
                || "helper_start_failed".equals(value)
                || "connect_success".equals(value) || "already_registered".equals(value)
                || "permission_denied".equals(value) || "connect_failed".equals(value)
                || "startup_rejected".equals(value) || "session_release".equals(value)
                || "lease_ready".equals(value)
                || "binder_death".equals(value) || "release".equals(value)
                || "retry_scheduled".equals(value);
    }

    /** Keep process identity on the same diagnostic path used by OperationTrace. */
    private static final class OperationTraceProcess {
        static String current() {
            String value = DragShareLog.processInstance();
            return value != null && value.matches("[A-Za-z0-9_.:-]{1,128}") ? value : "-";
        }
    }
}

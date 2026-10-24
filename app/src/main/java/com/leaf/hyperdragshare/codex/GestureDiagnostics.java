package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;

/** Optional observations, never a gesture state machine or an input consumer. */
public final class GestureDiagnostics {
    private static final String LEVEL = "diagnostic_touch_log_level";
    private static final String DESTINATION = "diagnostic_touch_log_destination";
    private GestureDiagnostics() {}

    /** Additive diagnostic metadata on the already-authorized gesture settings response. */
    public static void appendConfiguration(Context context, Bundle out) {
        try {
            DragShareSettings settings = DragShareSettings.readLocal(context);
            out.putInt(LEVEL, settings.logLevel);
            out.putInt(DESTINATION, settings.logDestination);
        } catch (RuntimeException ignored) { /* Keep the functional settings response usable. */ }
    }

    public static void configure(Bundle bundle) {
        try {
            if (bundle == null || !bundle.containsKey(LEVEL) || !bundle.containsKey(DESTINATION)) return;
            DragShareLog.configure(DragShareSettings.defaults().toBuilder()
                    .setLogLevel(bundle.getInt(LEVEL)).setLogDestination(bundle.getInt(DESTINATION)).build());
        } catch (RuntimeException ignored) { /* Older/missing diagnostic metadata is optional. */ }
    }

    public static boolean enabled() { return DragShareLog.isDebugEnabled(); }

    // Fixed-order, bounded grammar shared with the ZIP exporter. Never accepts free-form text.
    private static final String TOKEN = "[A-Za-z0-9_.:-]{1,128}";
    private static final String NUMBER = "(?:-?[0-9]+(?:\\.[0-9]+)?|unknown)";
    private static final String BOOL = "(?:true|false|unknown)";
    private static final java.util.regex.Pattern BODY = java.util.regex.Pattern.compile(
            "proc=" + TOKEN + " channel=" + TOKEN + " packageName=" + TOKEN + " action=" + TOKEN
            + " downTime=" + NUMBER + " eventTime=" + NUMBER + " x=" + NUMBER + " y=" + NUMBER
            + " rawX=" + NUMBER + " rawY=" + NUMBER + " dx=" + NUMBER + " dy=" + NUMBER
            + " pointerId=" + NUMBER + " pointerCount=" + NUMBER + " state=" + TOKEN
            + " dragging=" + BOOL + " windowFocus=" + BOOL + " viewFocus=" + BOOL
            + " reason=" + TOKEN + " skipped=" + NUMBER + " lastEventAge=" + NUMBER + " gapMs=" + NUMBER
            + " rawSpace=" + TOKEN + " timeBasis=" + TOKEN);

    static String filterLine(String line) {
        java.util.regex.Matcher header = java.util.regex.Pattern.compile(
                "^(\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3}) D "
                + "(?:pid=\\d+ uid=\\d+ thread=[^ ]+ )?DragShare/TouchStream: (.*)$").matcher(line);
        if (!header.matches() || !BODY.matcher(header.group(2)).matches()) return null;
        return header.group(1) + " D DragShare/TouchStream: " + header.group(2);
    }

    private static String token(String value) {
        return value != null && value.matches(TOKEN) ? value : "unknown";
    }
    private static String number(float value) {
        return Float.isFinite(value) ? String.format(java.util.Locale.ROOT, "%.2f", value) : "unknown";
    }
    private static String flag(Boolean value) { return value == null ? "unknown" : value.toString(); }

    /** Numeric sampling history only; holds no View, Window, Context or input object. */
    public static final class Stream {
        private final String channel;
        private long down = -1, lastEvent = -1, lastMove = Long.MIN_VALUE, gap = -1;
        private float startX = Float.NaN, startY = Float.NaN, x = Float.NaN, y = Float.NaN;
        private float rawX = Float.NaN, rawY = Float.NaN;
        private int pointerId = -1, pointerCount = -1, skipped;
        private String rawSpace = "unavailable", timeBasis = "snapshot";

        public Stream(String channel) { this.channel = token(channel); }

        public synchronized void motion(MotionEvent event, String pkg, String state, Boolean dragging,
                           Boolean windowFocus, Boolean viewFocus, String reason) {
            if (!enabled()) { resetObservation(); return; }
            if (event == null) return;
            try {
                int index = event.getActionIndex();
                pointerId = event.getPointerId(index);
                pointerCount = event.getPointerCount();
                rawSpace = "screen";
                timeBasis = "event_uptime";
                record(event.getActionMasked(), event.getX(index), event.getY(index),
                        event.getRawX(index), event.getRawY(index), event.getDownTime(), event.getEventTime(),
                        pkg, state, dragging, windowFocus, viewFocus, reason);
            } catch (RuntimeException ignored) { /* An unavailable observation cannot consume input. */ }
        }

        public synchronized void root(int action, float x, float y, float rawX, float rawY, long downTime,
                         long eventTime, String pkg, String state, Boolean dragging,
                         Boolean windowFocus, Boolean viewFocus, String reason) {
            root(action, x, y, rawX, rawY, downTime, eventTime, -1,
                    pkg, state, dragging, windowFocus, viewFocus, reason);
        }

        public synchronized void root(int action, float x, float y, float rawX, float rawY, long downTime,
                         long eventTime, int contacts, String pkg, String state, Boolean dragging,
                         Boolean windowFocus, Boolean viewFocus, String reason) {
            if (!enabled()) { resetObservation(); return; }
            pointerId = -1;
            pointerCount = contacts; // Physical contacts, not synthesized Android pointer IDs.
            rawSpace = Float.isFinite(rawX) ? "evdev" : "unavailable";
            timeBasis = "read_uptime";
            record(action, x, y, rawX, rawY, downTime, eventTime,
                    pkg, state, dragging, windowFocus, viewFocus, reason);
        }

        private void record(int action, float px, float py, float rx, float ry, long downTime,
                            long eventTime, String pkg, String state, Boolean dragging,
                            Boolean wf, Boolean vf, String reason) {
            if (action == MotionEvent.ACTION_DOWN || (downTime >= 0 && downTime != down)) {
                down = downTime >= 0 ? downTime : eventTime;
                startX = action == MotionEvent.ACTION_DOWN ? px : Float.NaN;
                startY = action == MotionEvent.ACTION_DOWN ? py : Float.NaN;
                lastMove = Long.MIN_VALUE; skipped = 0;
                lastEvent = -1;
            }
            gap = lastEvent < 0 ? -1 : Math.max(0L, eventTime - lastEvent);
            x = px; y = py; rawX = rx; rawY = ry; lastEvent = eventTime;
            if (action == MotionEvent.ACTION_MOVE && lastMove != Long.MIN_VALUE
                    && eventTime >= lastMove && eventTime - lastMove < 100L) { skipped++; return; }
            if (action == MotionEvent.ACTION_MOVE) lastMove = eventTime;
            emit(MotionEvent.actionToString(action), eventTime, pkg, state, dragging, wf, vf, reason);
        }

        public synchronized void mark(String action, String pkg, String state, Boolean dragging,
                         Boolean wf, Boolean vf, String reason) {
            if (!enabled()) { resetObservation(); return; }
            String previousBasis = timeBasis;
            timeBasis = "snapshot";
            emit(action, SystemClock.uptimeMillis(), pkg, state, dragging, wf, vf, reason);
            timeBasis = previousBasis;
        }

        private void resetObservation() {
            down = -1; lastEvent = -1; lastMove = Long.MIN_VALUE; gap = -1; skipped = 0;
            startX = Float.NaN; startY = Float.NaN;
            x = Float.NaN; y = Float.NaN; rawX = Float.NaN; rawY = Float.NaN;
            pointerId = -1; pointerCount = -1;
            rawSpace = "unavailable"; timeBasis = "snapshot";
        }

        private void emit(String action, long eventTime, String pkg, String state,
                          Boolean dragging, Boolean wf, Boolean vf, String reason) {
            try {
                String body = "proc=" + token(DragShareLog.processInstance()) + " channel=" + channel
                        + " packageName=" + token(pkg) + " action=" + token(action)
                        + " downTime=" + (down < 0 ? "unknown" : down) + " eventTime=" + eventTime
                        + " x=" + number(x) + " y=" + number(y) + " rawX=" + number(rawX) + " rawY=" + number(rawY)
                        + " dx=" + number(x - startX) + " dy=" + number(y - startY)
                        + " pointerId=" + (pointerId < 0 ? "unknown" : pointerId)
                        + " pointerCount=" + (pointerCount < 0 ? "unknown" : pointerCount)
                        + " state=" + token(state) + " dragging=" + flag(dragging)
                        + " windowFocus=" + flag(wf) + " viewFocus=" + flag(vf)
                        + " reason=" + token(reason) + " skipped=" + skipped
                        + " lastEventAge=" + (lastEvent < 0 ? "unknown" : Math.max(0L, eventTime - lastEvent))
                        + " gapMs=" + (gap < 0 ? "unknown" : gap)
                        + " rawSpace=" + rawSpace + " timeBasis=" + timeBasis;
                DragShareLog.d("DragShare/TouchStream", body);
                skipped = 0;
            } catch (RuntimeException ignored) { /* Diagnostics cannot consume or cancel input. */ }
        }
    }
}

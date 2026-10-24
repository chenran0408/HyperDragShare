package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public final class GestureDiagnosticsTest {
    @Test public void enablingMidStreamDoesNotInventDownCoordinatesOrReuseOldHistory() {
        configure(DragShareSettings.LOG_LEVEL_DEBUG);
        GestureDiagnostics.Stream stream = new GestureDiagnostics.Stream("partial_root");
        stream.root(MotionEvent.ACTION_DOWN, 10f, 20f, 100f, 200f, -1L, 100L,
                "example.app", "PENDING", false, null, null, "test_frame");
        configure(DragShareSettings.LOG_LEVEL_INFO);
        stream.root(MotionEvent.ACTION_MOVE, 20f, 30f, 200f, 300f, -1L, 120L,
                "example.app", "PENDING", false, null, null, "test_frame");
        ShadowLog.clear();
        configure(DragShareSettings.LOG_LEVEL_DEBUG);
        stream.root(MotionEvent.ACTION_MOVE, 30f, 40f, 300f, 400f, -1L, 140L,
                "example.app", "DRAGGING", true, null, null, "test_frame");
        String message = ShadowLog.getLogsForTag("DragShare/TouchStream").get(0).msg;
        assertTrue(message.contains("downTime=unknown"));
        assertTrue(message.contains("dx=unknown dy=unknown"));
        assertTrue(message.contains("gapMs=unknown"));
    }
    private static final String TAG = "DragShare/TouchStream";

    @Before public void setUp() {
        configure(DragShareSettings.LOG_LEVEL_INFO);
        ShadowLog.clear();
    }

    @After public void tearDown() {
        configure(DragShareSettings.LOG_LEVEL_INFO);
        ShadowLog.clear();
    }

    @Test public void debugOffEmitsNoTouchStreamEvents() {
        GestureDiagnostics.Stream stream = new GestureDiagnostics.Stream("test_stream");
        long now = SystemClock.uptimeMillis();
        stream.root(MotionEvent.ACTION_DOWN, 10f, 20f, 30f, 40f,
                -1L, now, 1, "example.app", "PENDING", false, null, null, "physical_input");
        stream.mark("STATE", "example.app", "ARMED", false, null, null, "press_confirmed");

        assertTrue(ShadowLog.getLogsForTag(TAG).isEmpty());
    }

    @Test public void debugStreamSamplesMovesButKeepsTerminalsAndStateSnapshots() {
        configure(DragShareSettings.LOG_LEVEL_DEBUG);
        ShadowLog.clear();
        GestureDiagnostics.Stream stream = new GestureDiagnostics.Stream("root_evdev_test");
        long start = SystemClock.uptimeMillis();
        stream.root(MotionEvent.ACTION_DOWN, 10f, 20f, 100f, 200f,
                -1L, start, 1, "example.app", "PENDING", false, null, null, "evdev_frame");
        stream.root(MotionEvent.ACTION_MOVE, 20f, 30f, 110f, 210f,
                -1L, start + 10, 1, "example.app", "DRAGGING", true, null, null, "evdev_frame");
        stream.root(MotionEvent.ACTION_MOVE, 25f, 40f, 115f, 220f,
                -1L, start + 20, 1, "example.app", "DRAGGING", true, null, null, "evdev_frame");
        stream.root(MotionEvent.ACTION_MOVE, 27f, 45f, 117f, 225f,
                -1L, start + 50, 1, "example.app", "DRAGGING", true, null, null, "evdev_frame");
        stream.root(MotionEvent.ACTION_MOVE, 30f, 50f, 120f, 230f,
                -1L, start + 110, 1, "example.app", "DRAGGING", true, null, null, "evdev_frame");
        stream.root(MotionEvent.ACTION_UP, 35f, 55f, 125f, 235f,
                -1L, start + 120, 1, "example.app", "FINISHED", false, null, null, "evdev_frame");
        stream.root(MotionEvent.ACTION_CANCEL, 35f, 55f, 125f, 235f,
                -1L, start + 130, 1, "example.app", "CANCELLED", false, null, null, "evdev_multiple_contacts");
        ShadowLooper.shadowMainLooper().idleFor(300, java.util.concurrent.TimeUnit.MILLISECONDS);
        stream.mark("STATE", "example.app", "IDLE", false, null, null, "snapshot_after_input");

        String joined = messages();
        assertEquals(6, ShadowLog.getLogsForTag(TAG).size());
        assertTrue(joined.contains("action=ACTION_DOWN"));
        assertTrue(joined.contains("action=ACTION_UP"));
        assertTrue(joined.contains("action=ACTION_CANCEL"));
        assertTrue(joined.contains("action=STATE"));
        assertTrue(joined.contains("pointerId=unknown pointerCount=1"));
        assertTrue(joined.contains("rawSpace=evdev timeBasis=read_uptime"));
        assertTrue(joined.contains("skipped=2"));
        assertTrue(joined.contains("gapMs=60"));
        assertTrue(joined.contains("ACTION_UP") && joined.contains("lastEventAge=0"));
        assertTrue(stateAge(joined) > 0);
    }

    @Test public void motionEventAndRootInputUseDifferentPointerAndCoordinateLabels() {
        configure(DragShareSettings.LOG_LEVEL_DEBUG);
        ShadowLog.clear();
        GestureDiagnostics.Stream stream = new GestureDiagnostics.Stream("portal_callback_observed");
        MotionEvent event = MotionEvent.obtain(100L, 110L, MotionEvent.ACTION_DOWN,
                12.5f, 32.5f, 0);
        try {
            stream.motion(event, "example.app", "DRAGGING", true, false, false,
                    "portal_motion_observed");
            stream.root(MotionEvent.ACTION_MOVE, 13f, 33f, 101f, 201f,
                    -1L, 120L, 2, "example.app", "DRAGGING", true, null, null, "evdev_frame");
        } finally {
            event.recycle();
        }

        String joined = messages();
        assertTrue(joined.contains("channel=portal_callback_observed packageName=example.app action=ACTION_DOWN"));
        assertTrue(joined.contains("x=12.50 y=32.50 rawX=12.50 rawY=32.50"));
        assertTrue(joined.contains("pointerId=0 pointerCount=1"));
        assertTrue(joined.contains("windowFocus=false viewFocus=false"));
        assertTrue(joined.contains("rawSpace=screen timeBasis=event_uptime"));
        assertTrue(joined.contains("pointerId=unknown pointerCount=2"));
        assertTrue(joined.contains("rawSpace=evdev timeBasis=read_uptime"));
    }

    @Test public void structuredTouchTraceSurvivesFilterAndZipButInjectionDoesNot() throws Exception {
        configure(DragShareSettings.LOG_LEVEL_DEBUG);
        ShadowLog.clear();
        new GestureDiagnostics.Stream("root_evdev_test").root(
                MotionEvent.ACTION_DOWN, 11f, 21f, 100f, 200f, -1L,
                SystemClock.uptimeMillis(), 1, "example.app", "PENDING", false, null, null, "evdev_frame");
        String body = ShadowLog.getLogsForTag(TAG).get(0).msg;
        String prefix = "2026-10-24 12:30:01.123 D pid=44 uid=0 thread=main DragShare/TouchStream: ";
        String validLine = prefix + body + "\n";
        String bodyInjection = prefix + body + " private message body\n";
        String uriInjection = prefix + body.replace("packageName=example.app", "packageName=content://secret/capability") + "\n";
        File directory = Files.createTempDirectory("gesture-diagnostics-test").toFile();
        File raw = new File(directory, "raw.log");
        try {
            Files.writeString(raw.toPath(), validLine + bodyInjection + uriInjection, StandardCharsets.UTF_8);
            String filtered = DiagnosticBundleExporter.filterRuntimeLog(raw);
            assertTrue(filtered.contains("channel=root_evdev_test"));
            assertTrue(filtered.contains("gapMs="));
            assertFalse(filtered.contains("private message body"));
            assertFalse(filtered.contains("content://"));

            DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.writeBundle(
                    directory, "root=READY", null, null, validLine + bodyInjection + uriInjection, null);
            String exported = zipText(result.file, "runtime-log.txt");
            assertTrue(exported.contains("channel=root_evdev_test"));
            assertTrue(exported.contains("gapMs="));
            assertFalse(exported.contains("private message body"));
            assertFalse(exported.contains("content://"));
            assertFalse(exported.contains("capability"));
        } finally {
            for (File file : directory.listFiles()) Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(directory.toPath());
        }
    }

    @Test public void optionalConfigMetadataIsReadOnlyAndMissingKeysKeepExistingLoggerSettings() {
        Context context = RuntimeEnvironment.getApplication();
        android.content.SharedPreferences preferences = context.getSharedPreferences(
                "drag_share_settings", Context.MODE_PRIVATE);
        Map<String, ?> before = preferences.getAll();
        configure(DragShareSettings.LOG_LEVEL_DEBUG);
        Bundle response = new Bundle();
        GestureDiagnostics.appendConfiguration(context, response);

        assertTrue(response.containsKey("diagnostic_touch_log_level"));
        assertTrue(response.containsKey("diagnostic_touch_log_destination"));
        assertEquals(before, preferences.getAll());
        assertTrue(GestureDiagnostics.enabled());
        assertEquals(DragShareSettings.LOG_DESTINATION_SYSTEM, DragShareLog.configuredDestination());

        GestureDiagnostics.configure(null);
        GestureDiagnostics.configure(new Bundle());
        Bundle partial = new Bundle();
        partial.putInt("diagnostic_touch_log_level", DragShareSettings.LOG_LEVEL_INFO);
        GestureDiagnostics.configure(partial);
        assertTrue(GestureDiagnostics.enabled());
        assertEquals(DragShareSettings.LOG_DESTINATION_SYSTEM, DragShareLog.configuredDestination());
        assertEquals(before, preferences.getAll());
    }

    private static long stateAge(String joined) {
        for (String line : joined.split("\n")) {
            if (!line.contains("action=STATE")) continue;
            java.util.regex.Matcher match = java.util.regex.Pattern
                    .compile("lastEventAge=([0-9]+)").matcher(line);
            if (match.find()) return Long.parseLong(match.group(1));
        }
        return -1;
    }

    private static String messages() {
        StringBuilder out = new StringBuilder();
        for (ShadowLog.LogItem item : ShadowLog.getLogsForTag(TAG)) out.append(item.msg).append('\n');
        return out.toString();
    }

    private static String zipText(File zip, String wanted) throws Exception {
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip.toPath()))) {
            for (ZipEntry entry; (entry = input.getNextEntry()) != null;) {
                if (!wanted.equals(entry.getName())) continue;
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private static void configure(int level) {
        DragShareSettings settings = DragShareSettings.defaults().toBuilder()
                .setLogLevel(level)
                .setLogDestination(DragShareSettings.LOG_DESTINATION_SYSTEM)
                .build();
        DragShareLog.configure(settings);
    }
}

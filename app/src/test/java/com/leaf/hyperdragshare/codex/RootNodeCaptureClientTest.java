package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public final class RootNodeCaptureClientTest {
    private static final String OCCUPIED = "{\"ready\":false,\"error\":\"private detail\","
            + "\"error_code\":\"uiautomation_already_registered\"}\n";
    private static final String PERMISSION = "{\"ready\":false,\"error\":\"private detail\","
            + "\"error_code\":\"uiautomation_permission_denied\"}\n";
    private static final String READY = "{\"ready\":true}\n";

    @Test public void startupFailuresUseFixedMessagesAndRecordCategories() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String attempt = RootConnectionDiagnostics.newAttempt();

        assertStartupFailure(context, attempt, OCCUPIED, "其他自动化会话占用");
        assertStartupFailure(context, attempt, PERMISSION, "Root");
        assertStartupFailure(context, attempt,
                "{\"ready\":false,\"error_code\":\"uiautomation_connect_failed\"}\n",
                "无法连接");
        assertStartupFailure(context, attempt,
                "{\"ready\":false,\"error\":\"secret://value\",\"error_code\":\"other\"}\n",
                "无法连接");

        String summary = RootConnectionDiagnostics.summary();
        assertTrue(summary.contains("attempt=" + attempt + " proc="));
        assertTrue(summary.contains("outcome=already_registered"));
        assertTrue(summary.contains("outcome=permission_denied"));
        assertTrue(summary.contains("outcome=startup_rejected"));
        assertFalse(summary.contains("private detail"));
        assertFalse(summary.contains("secret://"));
    }

    @Test public void helperStartFailureIsDistinctAndARecoveryCanOpenAndCloseTwice() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String launchFailureAttempt = RootConnectionDiagnostics.newAttempt();
        try {
            RootNodeCaptureClient.open(context, launchFailureAttempt, command -> {
                throw new IOException("private helper path");
            });
            fail("Expected helper start failure");
        } catch (IOException expected) {
            assertEquals("private helper path", expected.getMessage());
        }

        AtomicInteger starts = new AtomicInteger();
        String failedAttempt = RootConnectionDiagnostics.newAttempt();
        try {
            RootNodeCaptureClient.open(context, failedAttempt,
                    command -> { starts.incrementAndGet(); return new FakeProcess(OCCUPIED); });
            fail("Expected the occupied helper to fail");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("其他自动化会话占用"));
        }
        String recoveredAttempt = RootConnectionDiagnostics.newAttempt();
        RootNodeCaptureClient.Session recovered = RootNodeCaptureClient.open(context, recoveredAttempt,
                command -> { starts.incrementAndGet(); return new FakeProcess(READY); });
        recovered.close();
        recovered.close();

        assertEquals(2, starts.get());
        String summary = RootConnectionDiagnostics.summary();
        assertTrue(summary.contains("outcome=helper_start_failed"));
        assertTrue(summary.contains("outcome=connect_success"));
    }

    @Test public void emptyHelperExitIsConnectionFailureAndSessionReleaseIsRecordedOnce() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String attempt = RootConnectionDiagnostics.newAttempt();
        try {
            RootNodeCaptureClient.open(context, attempt, command -> new FakeProcess(""));
            fail("Expected helper connection failure");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Root node process exited"));
        }

        String successAttempt = RootConnectionDiagnostics.newAttempt();
        FakeProcess process = new FakeProcess(READY);
        RootNodeCaptureClient.Session session = RootNodeCaptureClient.open(context, successAttempt,
                command -> process);
        session.close();
        session.close();

        String[] rows = RootConnectionDiagnostics.summary().split("\\r?\\n");
        long releases = java.util.Arrays.stream(rows)
                .filter(row -> row.contains("attempt=" + successAttempt + " "))
                .filter(row -> row.endsWith("outcome=session_release"))
                .count();
        assertEquals(1L, releases);
        assertEquals(1, process.destroyCalls);
        assertTrue(RootConnectionDiagnostics.summary().contains("outcome=connect_failed"));
    }

    @Test public void helperConnectionStillWorksWhenOptionalLoggingIsDisabled() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        DragShareSettings original = DragShareSettings.readLocal(context);
        DragShareLog.configure(original.toBuilder()
                .setLogLevel(DragShareSettings.LOG_LEVEL_DISABLED).build());
        String attempt = RootConnectionDiagnostics.newAttempt();
        FakeProcess process = new FakeProcess(READY);
        try {
            RootNodeCaptureClient.Session session = RootNodeCaptureClient.open(context, attempt,
                    command -> process);
            session.close();
            assertEquals(1, process.destroyCalls);
            assertTrue(RootConnectionDiagnostics.summary().contains(
                    "attempt=" + attempt + " proc="));
            assertTrue(RootConnectionDiagnostics.summary().contains("outcome=connect_success"));
        } finally {
            DragShareLog.configure(original);
        }
    }

    private static void assertStartupFailure(Context context, String attempt,
                                             String response, String expectedMessage) throws Exception {
        try {
            RootNodeCaptureClient.open(context, attempt, command -> new FakeProcess(response));
            fail("Expected controlled startup failure");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains(expectedMessage));
            assertFalse(expected.getMessage().contains("private detail"));
            assertFalse(expected.getMessage().contains("secret://"));
        }
    }

    private static final class FakeProcess extends Process {
        private final ByteArrayInputStream stdout;
        private final ByteArrayInputStream stderr = new ByteArrayInputStream(new byte[0]);
        private final ByteArrayOutputStream stdin = new ByteArrayOutputStream();
        private boolean destroyed;
        int destroyCalls;

        FakeProcess(String response) {
            stdout = new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));
        }

        @Override public OutputStream getOutputStream() { return stdin; }
        @Override public ByteArrayInputStream getInputStream() { return stdout; }
        @Override public ByteArrayInputStream getErrorStream() { return stderr; }
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() { return 0; }
        @Override public void destroy() { destroyCalls++; destroyed = true; }
        @Override public boolean isAlive() { return !destroyed; }
    }
}

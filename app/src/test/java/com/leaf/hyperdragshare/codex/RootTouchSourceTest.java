package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public final class RootTouchSourceTest {
    @Test
    public void findsDirectTouchscreenEventHandler() {
        String devices = ""
                + "N: Name=\"gpio-keys\"\n"
                + "H: Handlers=event0 cpufreq\n"
                + "B: PROP=0\n"
                + "B: EV=3\n"
                + "\n"
                + "N: Name=\"Xiaomi_Touch_Input_0\"\n"
                + "H: Handlers=event7 cpufreq xiaomitouch\n"
                + "B: PROP=2\n"
                + "B: EV=b\n"
                + "B: ABS=263800000000000\n";

        assertEquals(
                "/dev/input/event7",
                RootTouchSource.findTouchDevicePath(devices));
    }

    @Test
    public void returnsNullForFilteredDeviceList() {
        String devices = ""
                + "N: Name=\"gpio-keys\"\r\n"
                + "H: Handlers=event0 cpufreq\r\n"
                + "B: PROP=0\r\n"
                + "B: EV=3\r\n";

        assertNull(RootTouchSource.findTouchDevicePath(devices));
    }

    @Test
    public void probeReadsInventoryAndRangesInOneRootCommand() {
        String command = RootTouchSource.probeCommand();
        assertTrue(command.contains("cat /proc/bus/input/devices"));
        assertTrue(command.contains("getevent -lp"));
        assertTrue(command.indexOf("HDS_INPUT_INVENTORY")
                < command.indexOf("HDS_INPUT_RANGES"));
    }

    @Test
    public void selectsRangesForTheDiscoveredTouchDeviceOnly() {
        String ranges = ""
                + "add device 1: /dev/input/event0\n"
                + "  name: \"gpio-keys\"\n"
                + "    ABS (0003): 0035 : value 0, min 0, max 255\n"
                + "add device 2: /dev/input/event7\n"
                + "  name: \"touchscreen\"\n"
                + "    ABS (0003): 0035 : value 0, min 0, max 1220\n"
                + "    ABS (0003): 0036 : value 0, min 0, max 2656\n";

        String selected = RootTouchSource.deviceRanges(ranges, "/dev/input/event7");

        assertTrue(selected.contains("/dev/input/event7"));
        assertTrue(selected.contains("max 1220"));
        assertTrue(selected.contains("max 2656"));
        assertFalse(selected.contains("event0"));
        assertNull(RootTouchSource.deviceRanges(ranges, "/dev/input/event9"));
    }

    @Test
    public void stopDuringDiscoveryRejectsTheProcessThatStartsLate() {
        RootTouchSource.Run run = new RootTouchSource.Run();
        assertNull(run.cancel());
        FakeProcess lateDiscovery = new FakeProcess();
        assertFalse(run.attach(lateDiscovery));
        // The launcher retains responsibility for a rejected process.
        assertNull(run.cancel());
    }

    @Test
    public void oldRunCannotClaimOrReleaseTheNewRunsProcess() {
        RootTouchSource.Run oldRun = new RootTouchSource.Run();
        RootTouchSource.Run newRun = new RootTouchSource.Run();
        FakeProcess oldProcess = new FakeProcess();
        FakeProcess newProcess = new FakeProcess();
        assertTrue(oldRun.attach(oldProcess));
        assertSame(oldProcess, oldRun.cancel());
        assertTrue(newRun.attach(newProcess));

        assertFalse(oldRun.release(oldProcess));
        assertFalse(oldRun.release(newProcess));
        assertFalse(oldRun.attach(newProcess));
        assertFalse(newRun.cancelled);
        assertSame(newProcess, newRun.cancel());
    }

    @Test
    public void stopAndWorkerFinallyCannotBothOwnProcessCleanup() {
        RootTouchSource.Run run = new RootTouchSource.Run();
        FakeProcess process = new FakeProcess();
        assertTrue(run.attach(process));
        assertTrue(run.release(process));
        assertNull(run.cancel());
        assertFalse(run.release(process));
    }

    @Test
    public void shutdownSendsEofBeforeWaitingAndNeverClosesBlockedReadPipe() {
        FakeProcess process = new FakeProcess();
        assertEquals(0, RootTouchSource.endProcess(process));
        assertTrue(process.stdinClosed);
        assertFalse(process.destroyed);
    }

    private static final class FakeProcess extends Process {
        boolean stdinClosed;
        boolean destroyed;
        private final InputStream blockedPipe = new ByteArrayInputStream(new byte[0]) {
            @Override public void close() {
                throw new AssertionError("Shutdown must not close a pipe another thread is reading");
            }
        };
        private final OutputStream stdin = new ByteArrayOutputStream() {
            @Override public void close() { stdinClosed = true; }
        };

        @Override public OutputStream getOutputStream() { return stdin; }
        @Override public InputStream getInputStream() { return blockedPipe; }
        @Override public InputStream getErrorStream() { return blockedPipe; }
        @Override public int waitFor() { throw new AssertionError("Use a bounded wait"); }
        @Override public boolean waitFor(long timeout, TimeUnit unit) {
            assertTrue("Supervisor must receive EOF before any wait", stdinClosed);
            return true;
        }
        @Override public int exitValue() { return 0; }
        @Override public void destroy() { destroyed = true; }
    }
}

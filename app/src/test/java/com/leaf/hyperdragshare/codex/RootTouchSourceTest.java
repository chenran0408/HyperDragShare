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
    private static String input(String bus, String name, int event) {
        return "I: Bus=" + bus + "\nN: Name=\"" + name + "\"\nH: Handlers=event" + event
                + "\nB: PROP=2\nB: ABS=263800000000000\n\n";
    }

    private static String range(int event, int x, int y) {
        return "add device " + event + ": /dev/input/event" + event
                + "\n  ABS_MT_POSITION_X : value 0, min 0, max " + x
                + "\n  ABS_MT_POSITION_Y : value 0, min 0, max " + y + "\n";
    }

    @Test public void multipleTouchPanelsUseTheOneMatchingTheCurrentDisplay() {
        String devices = input("0000", "Xiaomi_Touch_Input_1", 7)
                + input("0000", "Xiaomi_Touch_Input_0", 8);
        String ranges = range(7, 97599, 59599) + range(8, 119999, 260799);
        assertEquals("/dev/input/event8",
                RootTouchSource.findTouchDevicePath(devices, ranges, 1200, 2608, 0));
        assertEquals("/dev/input/event8",
                RootTouchSource.findTouchDevicePath(devices, ranges, 2608, 1200, 1));
        assertEquals("/dev/input/event7",
                RootTouchSource.findTouchDevicePath(devices, ranges, 976, 596, 0));
    }

    @Test public void bluetoothDirectInputIsNotThePhonesTouchscreen() {
        String devices = input("0005", "Bluetooth touch input", 12)
                + input("0000", "Xiaomi_Touch_Input_0", 8);
        String ranges = range(12, 1199, 2607) + range(8, 119999, 260799);
        assertEquals("/dev/input/event8",
                RootTouchSource.findTouchDevicePath(devices, ranges, 1200, 2608, 0));
    }

    @Test public void invalidCoordinateDeviceDoesNotHideTheWorkingTouchscreen() {
        String devices = input("0000", "touchscreen", 7) + input("0000", "touchscreen", 8);
        assertEquals("/dev/input/event8", RootTouchSource.findTouchDevicePath(
                devices, range(7, 0, 0) + range(8, 1199, 2607), 1200, 2608, 0));
    }

    @Test public void equallyMatchingScreensAreNotSelectedByInventoryOrder() {
        String devices = input("0000", "touchscreen", 7) + input("0000", "touchscreen", 8);
        assertNull(RootTouchSource.findTouchDevicePath(devices,
                range(7, 1199, 2607) + range(8, 11999, 26079), 1200, 2608, 0));
    }

    @Test public void bluetoothOnlyOrMissingDisplayCannotProvidePhoneTouchInput() {
        String devices = input("0005", "Bluetooth touch input", 12);
        assertNull(RootTouchSource.findTouchDevicePath(devices,
                range(12, 1199, 2607), 1200, 2608, 0));
        assertNull(RootTouchSource.findTouchDevicePath(devices, "", 0, 0, 0));
    }

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

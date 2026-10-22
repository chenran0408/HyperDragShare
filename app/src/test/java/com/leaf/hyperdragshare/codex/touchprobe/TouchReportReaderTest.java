package com.leaf.hyperdragshare.codex.touchprobe;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TouchReportReaderTest {
    @Test
    public void normalizesPaddedPointerAddresses() throws Exception {
        assertEquals(
                "0x72a15367d8",
                TouchReportReader.normalizeAddress("0x00000072a15367d8"));
        assertEquals(
                "0x72a15367d8",
                TouchReportReader.normalizeAddress("0x72a15367d8"));
    }

    @Test
    public void readerScriptUsesSelectedProfileReportSize() {
        TouchDeviceMapParser.DeviceMap map = TouchDeviceMapParser.parse(
                "===ROOT===\nuid=0(root)\n===PROPS===\n"
                        + "===COMMANDS===\ndd=available\n===PID===\n42\n===MAPS===\n"
                        + "7000000000-7000200000 r-xp 00000000 00:00 0 "
                        + "/odm/lib64/libtouchreport_alg.so\n===HASH===\n"
                        + TouchProfileRegistry.REFERENCE_LIBRARY_SHA256
                        + "  /odm/lib64/libtouchreport_alg.so\n");

        String script = TouchReportReader.readerScript(map, TouchProfileRegistry.REFERENCE);

        assertEquals(true, script.contains("count=2304 bs=2304"));
    }
}

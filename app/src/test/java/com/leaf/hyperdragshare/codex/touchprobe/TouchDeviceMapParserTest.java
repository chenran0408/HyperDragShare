package com.leaf.hyperdragshare.codex.touchprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TouchDeviceMapParserTest {
    @Test
    public void parsesReferenceLayoutAndComputesAddresses() throws Exception {
        String output = "===ROOT===\n"
                + "uid=0(root) gid=0(root) context=u:r:ksu:s0\n"
                + "===PROPS===\n"
                + "ro.product.model=2410DPN6CC\n"
                + "ro.mi.os.version.name=OS4.0.0.17.XOBCNXM\n"
                + "===COMMANDS===\n"
                + "dd=available\n"
                + "od=available\n"
                + "===PID===\n"
                + "2641\n"
                + "===MAPS===\n"
                + "72a1477000-72a14e6000 r-xp 00000000 fe:0f 115765089 /odm/lib64/libtouchreport_alg.so\n"
                + "72a14ed000-72a14f0000 rw-p 0006e000 fe:0f 115765089 /odm/lib64/libtouchreport_alg.so\n"
                + "===HASH===\n"
                + "70eda65f126329c76696c4aaf671288a11d789bb0f97c5c102aaa6fbe7bea3fe  /odm/lib64/libtouchreport_alg.so\n";

        TouchDeviceMapParser.DeviceMap map = TouchDeviceMapParser.parse(output);

        assertEquals(2641, map.pid);
        assertEquals("2410DPN6CC", map.properties.get("ro.product.model"));
        assertEquals("70eda65f126329c76696c4aaf671288a11d789bb0f97c5c102aaa6fbe7bea3fe",
                map.libraryHash);
        assertEquals("72a1477000", map.baseAddress.toString(16));
        assertEquals("72a14ef928", map.globalPointerAddress.toString(16));
        assertEquals("72a15367d8", map.expectedReportAddress.toString(16));
        assertTrue(map.commands.containsKey("dd"));
        assertEquals(2, map.libraryMaps.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingTouchLibrary() throws Exception {
        TouchDeviceMapParser.parse("===ROOT===\nuid=0\n===PROPS===\n===COMMANDS===\n"
                + "===PID===\n123\n===MAPS===\n1000-2000 r-xp 00000000 00:00 0 /vendor/lib64/other.so\n"
                + "===HASH===\n");
    }
}

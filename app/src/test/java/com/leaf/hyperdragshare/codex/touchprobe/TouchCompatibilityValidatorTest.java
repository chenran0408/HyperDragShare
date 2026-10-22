package com.leaf.hyperdragshare.codex.touchprobe;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TouchCompatibilityValidatorTest {
    private static final String KNOWN_HASH =
            TouchProfileRegistry.REFERENCE_LIBRARY_SHA256;

    @Test
    public void knownLibraryNeedsRealSamplesBeforeStaticClassification() {
        TouchReportReader.ConnectionInfo connection = connection(
                "7000000000", "7000200000", KNOWN_HASH, "70000bf7d8");
        TouchProbeCollector empty = new TouchProbeCollector();

        assertEquals(
                TouchProfileRegistry.LayoutClassification.SAME_LIB_UNVERIFIED,
                TouchCompatibilityValidator.evaluate(connection, empty.snapshot(), "")
                        .classification);
        assertEquals(
                TouchProfileRegistry.LayoutClassification.REFERENCE_STATIC,
                TouchCompatibilityValidator.evaluate(connection, validSnapshot(), "")
                        .classification);
    }

    @Test
    public void changedAslrBaseKeepsLayoutAndUsesStaticRva() {
        TouchReportReader.ConnectionInfo first = connection(
                "7000000000", "7000200000", KNOWN_HASH, "70000bf7d8");
        TouchReportReader.ConnectionInfo second = connection(
                "7100000000", "7100200000", KNOWN_HASH, "71000bf7d8");

        assertEquals(first.selection.profile.id, second.selection.profile.id);
        assertEquals(
                TouchProfileRegistry.LayoutClassification.REFERENCE_STATIC,
                TouchCompatibilityValidator.evaluate(first, validSnapshot(), "").classification);
        assertEquals(
                TouchProfileRegistry.LayoutClassification.REFERENCE_STATIC,
                TouchCompatibilityValidator.evaluate(second, validSnapshot(), "").classification);
    }

    @Test
    public void readableDynamicPointerBecomesValidAfterRealSamples() {
        TouchReportReader.ConnectionInfo connection = connection(
                "7000000000", "7000200000", KNOWN_HASH, "7000101000");

        assertEquals(
                TouchProfileRegistry.LayoutClassification.DYNAMIC_VALID,
                TouchCompatibilityValidator.evaluate(connection, validSnapshot(), "")
                        .classification);
    }

    @Test
    public void profileRvaOutsideLibraryMappingsIsMismatch() {
        TouchReportReader.ConnectionInfo connection = connection(
                "7000000000", "7000060000", KNOWN_HASH, "7000050000");

        assertEquals(
                TouchProfileRegistry.LayoutClassification.RVA_MISMATCH,
                TouchCompatibilityValidator.evaluate(connection, validSnapshot(), "")
                        .classification);
    }

    @Test
    public void referenceReportMayLiveOutsideFileBackedLibraryMappings() {
        TouchReportReader.ConnectionInfo connection = connection(
                "7000000000", "70000b0000", KNOWN_HASH, "70000bf7d8");

        assertEquals(
                TouchProfileRegistry.LayoutClassification.SAME_LIB_UNVERIFIED,
                TouchCompatibilityValidator.evaluate(connection, new TouchProbeCollector().snapshot(), "")
                        .classification);
    }

    @Test
    public void unalignedOrZeroPointerIsUnreadable() {
        TouchReportReader.ConnectionInfo unaligned = connection(
                "7000000000", "7000200000", KNOWN_HASH, "70000bf7d9");
        TouchReportReader.ConnectionInfo zero = connection(
                "7000000000", "7000200000", KNOWN_HASH, "0x0");

        assertEquals(
                TouchProfileRegistry.LayoutClassification.UNREADABLE,
                TouchCompatibilityValidator.evaluate(unaligned, validSnapshot(), "")
                        .classification);
        assertEquals(
                TouchProfileRegistry.LayoutClassification.UNREADABLE,
                TouchCompatibilityValidator.evaluate(zero, validSnapshot(), "")
                        .classification);
    }

    @Test
    public void unknownHashStaysUnknownEvenWhenModelAndDeviceMatch() {
        String unknownHash =
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        TouchReportReader.ConnectionInfo connection = connection(
                "7000000000", "7000200000", unknownHash, "70000bf7d8");

        assertEquals(
                TouchProfileRegistry.LayoutClassification.UNKNOWN_LAYOUT,
                TouchCompatibilityValidator.evaluate(connection, validSnapshot(), "")
                        .classification);
    }

    private static TouchReportReader.ConnectionInfo connection(
            String base,
            String end,
            String hash,
            String runtimePointer) {
        TouchDeviceMapParser.DeviceMap map = TouchDeviceMapParser.parse(
                "===ROOT===\nuid=0(root)\n===PROPS===\n"
                        + "ro.product.manufacturer=Xiaomi\n"
                        + "ro.product.model=same-model\n"
                        + "ro.product.device=haotian\n"
                        + "ro.mi.os.version.name=OS4.0\n"
                        + "===COMMANDS===\ndd=available\n===PID===\n42\n===MAPS===\n"
                        + base + "-" + end + " r-xp 00000000 00:00 0 "
                        + "/odm/lib64/libtouchreport_alg.so\n"
                        + "===HASH===\n" + hash + "  /odm/lib64/libtouchreport_alg.so\n"
                        + "===LIBRARY_SIZE===\n4096\n");
        TouchProfileRegistry.Selection selection = TouchProfileRegistry.resolve(map);
        String expected = "0x" + new java.math.BigInteger(base, 16)
                .add(java.math.BigInteger.valueOf(TouchReportLayout.STATIC_REPORT_RVA))
                .toString(16);
        return new TouchReportReader.ConnectionInfo(
                map,
                selection,
                runtimePointer.startsWith("0x") ? runtimePointer : "0x" + runtimePointer,
                expected);
    }

    private static TouchProbeCollector.Snapshot validSnapshot() {
        TouchProbeCollector collector = new TouchProbeCollector();
        for (int index = 0; index < 16; index++) {
            collector.accept(frame(index, 0));
        }
        for (TouchProbeCollector.Group group : TouchProbeCollector.Group.values()) {
            collector.beginGroup(group);
            int area = group == TouchProbeCollector.Group.FINGERTIP ? 8 : 12;
            collector.accept(frame(20 + group.ordinal() * 2, area));
            collector.accept(frame(21 + group.ordinal() * 2, area));
            collector.endGroup();
        }
        return collector.snapshot();
    }

    private static TouchReportLayout.Frame frame(int metadata, int area) {
        byte[] bytes = new byte[TouchReportLayout.REPORT_SIZE];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (area > 0) {
            buffer.putInt(TouchReportLayout.OFFSET_ENABLED, 1);
            buffer.putInt(TouchReportLayout.OFFSET_STATE, 2);
            buffer.putInt(TouchReportLayout.OFFSET_ID, 1);
            buffer.putInt(TouchReportLayout.OFFSET_X, 80_000);
            buffer.putInt(TouchReportLayout.OFFSET_Y, 180_000);
            buffer.putInt(TouchReportLayout.OFFSET_CANDIDATE_34, area);
        }
        buffer.putInt(TouchReportLayout.META_OFFSET, metadata);
        return TouchReportLayout.parseFrame(bytes);
    }
}

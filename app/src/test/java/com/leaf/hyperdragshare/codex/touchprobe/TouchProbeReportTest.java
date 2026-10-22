package com.leaf.hyperdragshare.codex.touchprobe;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@RunWith(RobolectricTestRunner.class)
public class TouchProbeReportTest {
    @Test
    public void reportCarriesAdaptationFieldsWithoutContent() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        TouchDeviceMapParser.DeviceMap map = TouchDeviceMapParser.parse(
                "===ROOT===\nuid=0(root)\n===PROPS===\nro.product.model=test-model\n"
                        + "===COMMANDS===\ndd=available\n===PID===\n42\n===MAPS===\n"
                        + "7000000000-7000010000 r-xp 00000000 00:00 0 "
                        + "/odm/lib64/libtouchreport_alg.so\n"
                        + "===HASH===\n"
                        + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  "
                        + "/odm/lib64/libtouchreport_alg.so\n"
                        + "===LIBRARY_SIZE===\n8192\n");
        TouchReportReader.ConnectionInfo connection = new TouchReportReader.ConnectionInfo(
                map, "0x70000bf7d8", "0x70000bf7d8", true);
        TouchProbeCollector collector = new TouchProbeCollector();
        collector.beginGroup(TouchProbeCollector.Group.FINGERTIP);
        collector.accept(frame(7));
        collector.accept(frame(7));
        collector.endGroup();
        collector.beginGroup(TouchProbeCollector.Group.THUMB);
        collector.accept(frame(12));
        collector.accept(frame(12));
        collector.endGroup();
        collector.beginGroup(TouchProbeCollector.Group.THUMB);
        TouchProbeCollector.GestureSample invalid = collector.endGroup();

        String report = TouchProbeReport.build(
                context, connection, collector.snapshot(), "test complete");

        assertTrue(report.contains("schema=" + TouchProbeReport.SCHEMA));
        assertTrue(report.contains("model=test-model"));
        assertTrue(report.contains("library_sha256="
                + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        assertTrue(report.contains("+0x34"));
        assertTrue(report.contains("slot_0_begin"));
        assertTrue(report.contains("slot_11_end"));
        assertTrue(report.contains("FINGERTIP"));
        assertTrue(report.contains("THUMB"));
        assertTrue(report.contains("separation_observed=true"));
        assertTrue(report.contains("single_contact_frames=2"));
        assertTrue(report.contains("library_size=8192"));
        assertTrue(report.contains("library_mapping_start=7000000000"));
        assertTrue(report.contains("library_mapping_end=7000010000"));
        assertTrue(report.contains("global_pointer_rva=0x78928"));
        assertTrue(report.contains("expected_report_rva=0xbf7d8"));
        assertTrue(report.contains("runtime_report_pointer_minus_expected=0x0"));
        assertTrue(report.contains("rva_relationship=REFERENCE_STATIC_RVA"));
        assertTrue(report.contains("peak_2c=0 peak_30=0 peak_34=0 peak_80=0"));
        assertTrue(report.contains("invalid_reason=未收到采样帧"));
        assertTrue(report.contains("不包含页面文字、截图、剪贴板或应用内容"));
        assertTrue(!invalid.valid);
    }

    private static TouchReportLayout.Frame frame(int area) {
        byte[] bytes = new byte[TouchReportLayout.REPORT_SIZE];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(TouchReportLayout.OFFSET_ENABLED, 1);
        buffer.putInt(TouchReportLayout.OFFSET_STATE, 2);
        buffer.putInt(TouchReportLayout.OFFSET_CANDIDATE_34, area);
        return TouchReportLayout.parseFrame(bytes);
    }
}

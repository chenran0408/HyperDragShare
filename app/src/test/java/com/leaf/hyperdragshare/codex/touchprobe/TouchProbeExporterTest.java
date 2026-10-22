package com.leaf.hyperdragshare.codex.touchprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@RunWith(RobolectricTestRunner.class)
public class TouchProbeExporterTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void knownLibraryExportsCompactReportUnlessLibraryIsForced() {
        Context context = RuntimeEnvironment.getApplication();
        TouchDeviceMapParser.DeviceMap map = map(TouchProfileRegistry.REFERENCE_LIBRARY_SHA256);

        assertFalse(TouchProbeExporter.shouldAttachLibrary(
                context, map, TouchProbeExporter.Options.smartDefault()));
        assertTrue(TouchProbeExporter.shouldAttachLibrary(
                context, map, new TouchProbeExporter.Options(true, false, false)));
        assertFalse(TouchProbeExporter.shouldAttachLibrary(
                context, map, new TouchProbeExporter.Options(true, true, true)));
    }

    @Test
    public void unknownLibraryAttachesOnceThenDefaultsToCompactReport() {
        Context context = RuntimeEnvironment.getApplication();
        String hash =
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        TouchDeviceMapParser.DeviceMap map = map(hash);

        assertTrue(TouchProbeExporter.shouldAttachLibrary(
                context, map, TouchProbeExporter.Options.smartDefault()));
        context.getSharedPreferences("touch_probe_exports", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("hash" + hash, true)
                .commit();
        assertFalse(TouchProbeExporter.shouldAttachLibrary(
                context, map, TouchProbeExporter.Options.smartDefault()));
        assertTrue(TouchProbeExporter.shouldAttachLibrary(
                context, map, new TouchProbeExporter.Options(false, false, true)));
    }

    @Test
    public void copyCommandPrefersMapFilesAndThenMappedPath() {
        String command = TouchProbeExporter.libraryCopyCommand(map(
                TouchProfileRegistry.REFERENCE_LIBRARY_SHA256));

        int mapFiles = command.indexOf("/proc/$pid/map_files/");
        int mappedPath = command.indexOf("elif [ -r \"$mapped\" ]");
        assertTrue(mapFiles >= 0);
        assertTrue(mappedPath > mapFiles);
        assertEquals(
                "复制前后 SHA-256 不一致",
                TouchProbeExporter.copyValidationFailure(
                        "aaaaaaaa", "bbbbbbbb", 5, 5, true));
        assertEquals(
                "文件不是 64 位 arm64 ELF",
                TouchProbeExporter.copyValidationFailure(
                        "aaaaaaaa", "aaaaaaaa", 5, 5, false));
    }

    @Test
    public void adapterZipContainsRequiredEntriesAndManifest() throws Exception {
        TouchDeviceMapParser.DeviceMap map = map(
                TouchProfileRegistry.REFERENCE_LIBRARY_SHA256);
        TouchProfileRegistry.Selection selection = TouchProfileRegistry.resolve(map);
        String expected = "0x" + map.baseAddress
                .add(java.math.BigInteger.valueOf(TouchReportLayout.STATIC_REPORT_RVA))
                .toString(16);
        TouchReportReader.ConnectionInfo connection =
                new TouchReportReader.ConnectionInfo(
                        map, selection, expected, expected);
        TouchProbeCollector collector = new TouchProbeCollector();
        collector.beginGroup(TouchProbeCollector.Group.FINGERTIP);
        collector.accept(frame(8, 1));
        collector.accept(frame(8, 2));
        collector.endGroup();

        File library = temporaryFolder.newFile(TouchReportLayout.LIBRARY_NAME);
        try (FileOutputStream output = new FileOutputStream(library)) {
            output.write(new byte[]{1, 2, 3, 4, 5});
        }
        TouchProbeExporter.TouchLibraryCopy copy =
                new TouchProbeExporter.TouchLibraryCopy(
                        "verified",
                        "map_files:/proc/42/map_files/7000000000-7000200000",
                        TouchProfileRegistry.REFERENCE_LIBRARY_SHA256,
                        TouchProfileRegistry.REFERENCE_LIBRARY_SHA256,
                        "",
                        library.length());
        File zipFile = temporaryFolder.newFile("adapter.zip");
        TouchProbeExporter.writeAdapterPackage(
                zipFile,
                "status=test\nlayout_classification=REFERENCE_STATIC\n",
                connection,
                collector.snapshot(),
                map,
                copy,
                library);

        List<String> entries = new ArrayList<>();
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> iterator = zip.entries();
            while (iterator.hasMoreElements()) entries.add(iterator.nextElement().getName());
            Collections.sort(entries);
            String manifest = new String(
                    zip.getInputStream(zip.getEntry("manifest.json")).readAllBytes(),
                    StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(manifest);
            assertEquals("verified", json.getString("library_copy"));
            assertEquals(
                    "SAME_LIB_UNVERIFIED",
                    json.getString("layout_classification"));
            assertEquals(
                    TouchReportLayout.LIBRARY_NAME,
                    new File(json.getJSONObject("library").getString("path")).getName());
            assertTrue(manifest.contains("仅对本次触控服务生命周期有效"));
        }

        assertEquals(List.of(
                "libraries/libtouchreport_alg.so",
                "libraries/libtouchreport_alg.so.sha256",
                "manifest.json",
                "mappings/libtouchreport_alg.so.maps.txt",
                "report.txt",
                "samples.json"), entries);
    }

    private static TouchDeviceMapParser.DeviceMap map(String hash) {
        return TouchDeviceMapParser.parse(
                "===ROOT===\nuid=0(root)\n===PROPS===\n"
                        + "ro.product.manufacturer=Xiaomi\n"
                        + "ro.product.model=test-model\n"
                        + "ro.product.device=haotian\n"
                        + "ro.mi.os.version.name=OS4.0\n"
                        + "ro.build.version.release=17\n"
                        + "ro.build.version.sdk=37\n"
                        + "===COMMANDS===\ndd=available\n===PID===\n42\n===MAPS===\n"
                        + "7000000000-7000200000 r-xp 00000000 00:00 0 "
                        + "/odm/lib64/libtouchreport_alg.so\n"
                        + "===HASH===\n" + hash + "  /odm/lib64/libtouchreport_alg.so\n"
                        + "===LIBRARY_SIZE===\n5\n");
    }

    private static TouchReportLayout.Frame frame(int area, int metadata) {
        byte[] bytes = new byte[TouchReportLayout.REPORT_SIZE];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(TouchReportLayout.OFFSET_ENABLED, 1);
        buffer.putInt(TouchReportLayout.OFFSET_STATE, 2);
        buffer.putInt(TouchReportLayout.OFFSET_CANDIDATE_34, area);
        buffer.putInt(TouchReportLayout.META_OFFSET, metadata);
        return TouchReportLayout.parseFrame(bytes);
    }
}

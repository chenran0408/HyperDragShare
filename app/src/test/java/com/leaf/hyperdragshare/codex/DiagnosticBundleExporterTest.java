package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public final class DiagnosticBundleExporterTest {
    private File directory;

    @Before public void setUp() throws Exception {
        directory = Files.createTempDirectory("diagnostic-bundle-test").toFile();
    }

    @After public void tearDown() {
        deleteRecursively(directory);
    }

    @Test public void completeBundleContainsSummaryCompatibilityAndSafeRuntimeTrace() throws Exception {
        String runtime = "2026-10-24 12:30:01.123 I pid=44 uid=0 thread=main "
                + "DragShare/Operation: proc=mod-44 op=press-44-2 stage=stash "
                + "outcome=completed asset=0123456789abcdef0123456789abcdef\n";

        DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.writeBundle(
                directory, validStateSummary(), validCompatibility(), null,
                runtime, null);

        assertTrue(result.file.isFile());
        assertNull(result.missingLogReason);
        Map<String, String> entries = readZip(result.file);
        assertTrue(entries.get("summary.txt").contains("root=READY"));
        assertTrue(entries.get("compatibility.txt").contains("接口状态: ready"));
        assertTrue(entries.get("runtime-log.txt").contains("stage=stash outcome=completed"));
        assertTrue(entries.get("manifest.txt").contains("compatibility.txt: included"));
        assertTrue(DiagnosticBundleExporter.fileName().endsWith(".zip"));
    }

    @Test public void compatibilityFailureDoesNotPreventRuntimeExport() throws Exception {
        DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.writeBundle(
                directory, validStateSummary(), null, "IllegalStateException", safeRuntimeTrace(), null);

        Map<String, String> entries = readZip(result.file);
        assertFalse(entries.containsKey("compatibility.txt"));
        assertTrue(entries.containsKey("runtime-log.txt"));
        assertTrue(entries.get("manifest.txt").contains("Compatibility report reason: IllegalStateException"));
        assertNull(result.missingLogReason);
    }

    @Test public void unavailableRuntimeStillExportsOtherInformationAndExplainsHowToEnableIt() throws Exception {
        DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.writeBundle(
                directory, validStateSummary(), validCompatibility(), null, null,
                "运行日志未启用。可在设置中启用日志后复现问题并重新导出。");

        Map<String, String> entries = readZip(result.file);
        assertTrue(entries.containsKey("summary.txt"));
        assertTrue(entries.containsKey("compatibility.txt"));
        assertFalse(entries.containsKey("runtime-log.txt"));
        assertEquals("运行日志未启用。可在设置中启用日志后复现问题并重新导出。",
                result.missingLogReason);
        assertTrue(entries.get("manifest.txt").contains(result.missingLogReason));
        assertTrue(entries.get("manifest.txt").contains("启用文件日志，复现问题后重新导出"));
        assertTrue(entries.get("manifest.txt").contains("自由文本与异常详情均已过滤"));
    }

    @Test public void emptyRuntimeIsReportedAsMissingInsteadOfCreatingAnEmptyLogEntry() throws Exception {
        DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.writeBundle(
                directory, null, null, "Report failed", "", "No safe structured runtime diagnostics were available.");

        Map<String, String> entries = readZip(result.file);
        assertFalse(entries.containsKey("runtime-log.txt"));
        assertEquals("No safe structured runtime diagnostics were available.", result.missingLogReason);
        assertTrue(entries.get("manifest.txt").contains("Report failed"));
    }

    @Test public void allSourcesMissingStillProducesAReadableSummaryAndManifest() throws Exception {
        DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.writeBundle(
                directory, validStateSummary(), null, "Report unavailable", null,
                "File log does not exist.");

        Map<String, String> entries = readZip(result.file);
        assertEquals(2, entries.size());
        assertTrue(entries.get("summary.txt").contains("root=READY"));
        assertTrue(entries.get("manifest.txt").contains("compatibility.txt: unavailable"));
        assertTrue(entries.get("manifest.txt").contains("runtime-log.txt: unavailable"));
        assertTrue(entries.get("manifest.txt").contains("File log does not exist."));
    }

    @Test public void runtimeFilterKeepsStructuredTraceAndDropsArbitraryMessagesAndThrowableText() throws Exception {
        File raw = new File(directory, "raw.log");
        String text = "2026-10-24 12:30:00.123 I pid=44 uid=0 thread=main DragShare/Log: "
                + "diagnostic file opened session=abc-44 build=1.7.219-beta.20/262 device=Google Pixel android=15/35 "
                + "level=info destination=file max_bytes=4194304 sampled_debug=true\n"
                + "2026-10-24 12:30:01.123 I pid=44 uid=0 thread=main "
                + "DragShare/Operation: proc=mod-44 op=press-44-2 stage=stash "
                + "outcome=completed asset=0123456789abcdef0123456789abcdef\n"
                + "2026-10-24 12:30:01.124 I pid=44 uid=0 thread=main "
                + "DragShare/Operation: proc=mod-44 op=- stage=image_access "
                + "outcome=descriptor_opened asset=0123456789abcdef0123456789abcdef "
                + "callerUid=12345 read=unknown delivery=unknown\n"
                + "2026-10-24 12:30:02.123 E pid=44 uid=0 thread=main "
                + "DragShare/Actions: failed payload=private message body\n"
                + "java.lang.IllegalStateException: content://secret/capability\n";
        Files.write(raw.toPath(), text.getBytes(StandardCharsets.UTF_8));

        String safe = DiagnosticBundleExporter.filterRuntimeLog(raw);

        assertTrue(safe.contains("stage=stash outcome=completed"));
        assertTrue(safe.contains("diagnostic file opened build=1.7.219-beta.20/262"));
        assertTrue(safe.contains("callerUid=12345 read=unknown delivery=unknown"));
        assertFalse(safe.contains("private message body"));
        assertFalse(safe.contains("IllegalStateException"));
        assertFalse(safe.contains("content://"));
        assertFalse(safe.contains("capability"));
    }

    @Test public void runtimeFilterFindsNewestCurrentTraceAfterLargeArchivedPrefix() throws Exception {
        File raw = new File(directory, "archived-and-current.log");
        StringBuilder content = new StringBuilder(1_100_200);
        for (int i = 0; i < 1_100_000; i++) content.append('x');
        content.append("\n").append(traceLine("current-op"));
        Files.write(raw.toPath(), content.toString().getBytes(StandardCharsets.UTF_8));

        String safe = DiagnosticBundleExporter.filterRuntimeLog(raw);

        assertTrue(safe.contains("op=current-op"));
        assertFalse(safe.contains("xxxxxxxx"));
    }

    @Test public void runtimeFilterRetainsLatestTracesWhenStructuredDataExceedsBudget() throws Exception {
        File raw = new File(directory, "many-traces.log");
        StringBuilder content = new StringBuilder(2_000_000);
        for (int i = 0; i < 10_000; i++) {
            content.append(traceLine(String.format(Locale.US, "trace%05d", i)));
        }
        Files.write(raw.toPath(), content.toString().getBytes(StandardCharsets.UTF_8));

        String safe = DiagnosticBundleExporter.filterRuntimeLog(raw);

        assertTrue(safe.length() <= 1_000_000);
        assertTrue(safe.contains("op=trace09999"));
        assertFalse(safe.contains("op=trace00000"));
    }

    @Test public void summaryAndCompatibilityRedactUrisAndCapabilityTokens() throws Exception {
        DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.writeBundle(
                directory, validStateSummary() + " uri=content://secret/item capability=abc123", null, null,
                null, "logs unavailable");
        String summary = readZip(result.file).get("summary.txt");
        assertFalse(summary.contains("content://secret"));
        assertFalse(summary.contains("abc123"));
        assertTrue(summary.contains("state summary unavailable"));
    }

    @Test public void invalidOutputLocationFailsWithoutLeavingPartialBundle() throws Exception {
        File notDirectory = new File(directory, "file");
        assertTrue(notDirectory.createNewFile());
        try {
            DiagnosticBundleExporter.writeBundle(notDirectory, validStateSummary(),
                    validCompatibility(), null, safeRuntimeTrace(), null);
            fail("Expected report directory creation to fail");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        }
        assertEquals(1, directory.listFiles().length);
    }

    @Test public void createWithDisabledFileLoggingStillExportsAndLeavesLoggingSettingsUntouched() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        android.content.SharedPreferences preferences = context.getSharedPreferences(
                "drag_share_settings", Context.MODE_PRIVATE);
        preferences.edit().putInt("log_level", DragShareSettings.LOG_LEVEL_DISABLED)
                .putInt("log_destination", DragShareSettings.LOG_DESTINATION_FILE).commit();
        int originalLevel = preferences.getInt("log_level", -1);
        int originalDestination = preferences.getInt("log_destination", -1);

        DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.create(
                context, validStateSummary());

        assertEquals("运行日志未启用。可在设置中启用日志后复现问题并重新导出。",
                result.missingLogReason);
        assertTrue(readZip(result.file).containsKey("compatibility.txt"));
        assertEquals(originalLevel, preferences.getInt("log_level", -1));
        assertEquals(originalDestination, preferences.getInt("log_destination", -1));
    }

    @Test public void createWithSystemOnlyLoggingDoesNotTryToEnableOrRedirectIt() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        android.content.SharedPreferences preferences = context.getSharedPreferences(
                "drag_share_settings", Context.MODE_PRIVATE);
        preferences.edit().putInt("log_level", DragShareSettings.LOG_LEVEL_INFO)
                .putInt("log_destination", DragShareSettings.LOG_DESTINATION_SYSTEM).commit();

        DiagnosticBundleExporter.Result result = DiagnosticBundleExporter.create(
                context, validStateSummary());

        assertEquals("运行日志已设为系统日志，无法从应用导出。",
                result.missingLogReason);
        assertEquals(DragShareSettings.LOG_LEVEL_INFO, preferences.getInt("log_level", -1));
        assertEquals(DragShareSettings.LOG_DESTINATION_SYSTEM,
                preferences.getInt("log_destination", -1));
        assertFalse(readZip(result.file).containsKey("runtime-log.txt"));
    }

    private static String validStateSummary() {
        return "overall=NORMAL; root=READY; injection=READY; portal_service=READY; "
                + "portal_root=READY; root_input=READY; press_reader=READY; overlay=READY";
    }

    private static String validCompatibility() {
        return "Hyper手势兼容性报告\n生成时间: 2026-10-24 12:30:00 +0000\n"
                + "说明: Hyper手势兼容性报告不包含快捷方式名称、联系人和用户选择的应用清单。\n"
                + "=== Hook 状态 ===\n接口状态: ready\n最后连接时间: 0\n"
                + "-- SystemUI --\nprivate body=must-not-appear\n";
    }

    private static String safeRuntimeTrace() {
        return traceLine("press-44-2");
    }

    private static String traceLine(String operation) {
        return "2026-10-24 12:30:01.123 I pid=44 uid=0 thread=main "
                + "DragShare/Operation: proc=mod-44 op=" + operation + " stage=stash "
                + "outcome=completed asset=0123456789abcdef0123456789abcdef\n";
    }

    private static Map<String, String> readZip(File file) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int count;
                while ((count = zip.read(buffer)) >= 0) bytes.write(buffer, 0, count);
                entries.put(entry.getName(), bytes.toString(StandardCharsets.UTF_8.name()));
                zip.closeEntry();
            }
        }
        return entries;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }
}

package com.leaf.hyperdragshare.codex.touchprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.leaf.hyperdragshare.codex.BuildConfig;

import org.junit.Test;

public class ExportFileNamesTest {
    @Test
    public void runtimeLogContainsProductVersionTypeTimestampAndLogExtension() {
        String timestamp = "20261023-035206-123";

        assertEquals(
                "MMGG-" + BuildConfig.VERSION_NAME + "-runtime-log-"
                        + timestamp + ".log",
                ExportFileNames.runtimeLog(timestamp));
    }

    @Test
    public void textAndAdapterExportsShareReportIdentityAndHaveMatchingExtensions() {
        String timestamp = "20261023-035206-123";
        String versionAndType = "MMGG-" + BuildConfig.VERSION_NAME + "-touch-report";

        assertEquals(
                versionAndType + "-20261023-035206-123.txt",
                ExportFileNames.touchReport("txt", null, timestamp));
        assertEquals(
                versionAndType + "-Xiaomi-15-Pro-20261023-035206-123.zip",
                ExportFileNames.touchReport("zip", "Xiaomi 15/Pro", timestamp));
    }

    @Test
    public void generatedTimestampIncludesMilliseconds() {
        assertTrue(ExportFileNames.timestampNow().matches("\\d{8}-\\d{6}-\\d{3}"));
    }

    @Test
    public void reportFilenameFacadeUsesTheSharedReportNamingRule() {
        assertTrue(TouchProbeReport.exportFileName().matches(
                "MMGG-" + BuildConfig.VERSION_NAME
                        + "-touch-report-\\d{8}-\\d{6}-\\d{3}\\.txt"));
    }
}

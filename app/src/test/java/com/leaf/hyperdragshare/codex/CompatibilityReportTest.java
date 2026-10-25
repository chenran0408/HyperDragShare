package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;

import com.leaf.hyperdragshare.gestures.config.ConfigStore;
import com.leaf.hyperdragshare.gestures.diagnostics.CompatibilityReport;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPackageManager;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public final class CompatibilityReportTest {
    @Test public void namedSystemPackagesIncludeKnownVersionsAndSurviveBundleSanitizing()
            throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ShadowPackageManager packages = Shadow.extract(context.getPackageManager());
        installPackage(packages, "com.xiaomi.aicr", "4.12.26", 41226);
        installPackage(packages, "com.miui.contentextension", "5.1.5", 51500);

        String report = CompatibilityReport.build(context, new ConfigStore(context));
        assertTrue(report.contains("小米澎湃AI引擎: com.xiaomi.aicr 4.12.26"));
        assertTrue(report.contains("传送门: com.miui.contentextension 5.1.5"));

        File directory = Files.createTempDirectory("compatibility-report-test").toFile();
        try {
            DiagnosticBundleExporter.Result bundle = DiagnosticBundleExporter.writeBundle(
                    directory, null, report, null, null, null);
            String sanitized = readCompatibility(bundle.file);
            assertTrue(sanitized.contains("小米澎湃AI引擎: com.xiaomi.aicr 4.12.26"));
            assertTrue(sanitized.contains("传送门: com.miui.contentextension 5.1.5"));
        } finally {
            File[] files = directory.listFiles();
            if (files != null) for (File file : files) file.delete();
            directory.delete();
        }
    }

    @Test public void missingSystemPackagesAreReportedWithoutScanningInstalledApps() {
        Context context = RuntimeEnvironment.getApplication();
        String report = CompatibilityReport.build(context, new ConfigStore(context));

        assertTrue(report.contains("小米澎湃AI引擎: com.xiaomi.aicr <不可用> NameNotFoundException"));
        assertTrue(report.contains("传送门: com.miui.contentextension <不可用> NameNotFoundException"));
        assertFalse(report.contains("com.miui.contentcatcher"));
    }

    private static void installPackage(ShadowPackageManager packages, String packageName,
            String versionName, int versionCode) {
        PackageInfo info = new PackageInfo();
        info.packageName = packageName;
        info.versionName = versionName;
        info.versionCode = versionCode;
        ApplicationInfo application = new ApplicationInfo();
        application.packageName = packageName;
        info.applicationInfo = application;
        packages.installPackage(info);
    }

    private static String readCompatibility(File bundle) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(bundle))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("compatibility.txt".equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("compatibility.txt missing from exported bundle");
    }
}

package com.leaf.hyperdragshare.codex.touchprobe;

import com.leaf.hyperdragshare.codex.BuildConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Consistent, user-visible names for exported MMGG diagnostics. */
public final class ExportFileNames {
    private static final String STAMP_PATTERN = "yyyyMMdd-HHmmss-SSS";

    private ExportFileNames() {}

    public static String runtimeLog() {
        return runtimeLog(timestampNow());
    }

    static String runtimeLog(String timestamp) {
        return "MMGG-" + BuildConfig.VERSION_NAME + "-runtime-log-" + timestamp + ".log";
    }

    static String touchReport(String extension, String model, String timestamp) {
        String modelPart = model == null || model.isBlank()
                ? ""
                : "-" + model.replaceAll("[^A-Za-z0-9._-]+", "-");
        return "MMGG-" + BuildConfig.VERSION_NAME + "-touch-report"
                + modelPart + "-" + timestamp + "." + extension;
    }

    static String timestampNow() {
        return new SimpleDateFormat(STAMP_PATTERN, Locale.US).format(new Date());
    }
}

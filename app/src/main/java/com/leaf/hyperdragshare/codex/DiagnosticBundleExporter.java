package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.net.Uri;

import com.leaf.hyperdragshare.gestures.config.ConfigStore;
import com.leaf.hyperdragshare.gestures.diagnostics.CompatibilityReport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates a privacy-filtered support bundle from independently available diagnostics. */
final class DiagnosticBundleExporter {
    private static final String REPORT_DIRECTORY = "diagnostics";
    private static final int MAX_RUNTIME_LOG_CHARS = 1_000_000;

    private DiagnosticBundleExporter() { }

    static final class Result {
        final File file;
        final String missingLogReason;

        Result(File file, String missingLogReason) {
            this.file = file;
            this.missingLogReason = missingLogReason;
        }
    }

    static String fileName() {
        return "diagnostic-bundle-" + new SimpleDateFormat(
                "yyyyMMdd-HHmmss-SSS", Locale.US).format(new Date()) + ".zip";
    }

    static Result create(Context context, String stateSummary) throws IOException {
        if (context == null) throw new IOException("Missing application context");
        Context app = context.getApplicationContext();
        File directory = new File(app.getCacheDir(), REPORT_DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create diagnostic report directory");
        }

        String compatibility = null;
        String compatibilityFailure = null;
        try {
            compatibility = CompatibilityReport.build(app, new ConfigStore(app));
        } catch (Throwable error) {
            compatibilityFailure = safeFailure(error);
        }

        File rawRuntime = null;
        String runtime = null;
        String missingLogReason = null;
        DragShareSettings settings = DragShareSettings.readLocal(app);
        if (settings.logLevel == DragShareSettings.LOG_LEVEL_DISABLED) {
            missingLogReason = "运行日志未启用。可在设置中启用日志后复现问题并重新导出。";
        } else if (settings.logDestination != DragShareSettings.LOG_DESTINATION_FILE) {
            missingLogReason = "运行日志已设为系统日志，无法从应用导出。";
        } else {
            try {
                rawRuntime = File.createTempFile("runtime-log-", ".tmp", directory);
                DragShareLog.exportTo(app, Uri.fromFile(rawRuntime));
                runtime = filterRuntimeLog(rawRuntime);
                if (runtime.isEmpty()) {
                    missingLogReason = "没有可导出的结构化运行诊断记录。";
                    runtime = null;
                }
            } catch (Throwable error) {
                missingLogReason = safeFailure(error);
            } finally {
                if (rawRuntime != null && rawRuntime.exists() && !rawRuntime.delete()) {
                    rawRuntime.deleteOnExit();
                }
            }
        }

        return writeBundle(directory, stateSummary, compatibility, compatibilityFailure,
                runtime, missingLogReason);
    }

    /** Package-visible assembly seam keeps behavior tests independent of root and Android state. */
    static Result writeBundle(File directory, String stateSummary, String compatibility,
                              String compatibilityFailure, String runtime,
                              String missingLogReason) throws IOException {
        if (directory == null) throw new IOException("Missing report directory");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create diagnostic report directory");
        }
        File output = new File(directory, fileName());
        String safeRuntime = filterRuntimeText(runtime);
        boolean hasRuntime = !safeRuntime.isEmpty();
        String logReason = hasRuntime ? null : nonEmpty(missingLogReason,
                runtime == null || runtime.isEmpty()
                        ? "Runtime log could not be collected."
                        : "No safe structured runtime diagnostics were available.");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output))) {
            addText(zip, "summary.txt", buildSummary(stateSummary));
            if (compatibility != null) {
                addText(zip, "compatibility.txt", sanitizeCompatibility(compatibility));
            }
            if (hasRuntime) addText(zip, "runtime-log.txt", safeRuntime);
            addText(zip, "manifest.txt", buildManifest(compatibility != null,
                    compatibilityFailure, hasRuntime, logReason));
        } catch (IOException | RuntimeException error) {
            if (output.exists() && !output.delete()) output.deleteOnExit();
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Unable to create diagnostic bundle", error);
        }
        return new Result(output, logReason);
    }

    static String filterRuntimeLog(File rawFile) throws IOException {
        RuntimeTail tail = new RuntimeTail();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(rawFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String filtered = filterRuntimeLine(line);
                if (filtered != null) tail.add(filtered);
            }
        }
        return tail.text();
    }

    private static String filterRuntimeText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        RuntimeTail tail = new RuntimeTail();
        for (String line : raw.split("\\r?\\n")) {
            String filtered = filterRuntimeLine(line);
            if (filtered != null) tail.add(filtered);
        }
        return tail.text();
    }

    private static final class RuntimeTail {
        private final Deque<String> lines = new ArrayDeque<>();
        private int chars;

        void add(String line) {
            int length = line.length() + 1;
            while (!lines.isEmpty() && chars + length > MAX_RUNTIME_LOG_CHARS) {
                chars -= lines.removeFirst().length() + 1;
            }
            if (length <= MAX_RUNTIME_LOG_CHARS) {
                lines.addLast(line);
                chars += length;
            }
        }

        String text() {
            StringBuilder result = new StringBuilder(chars);
            for (String line : lines) result.append(line).append('\n');
            return result.toString();
        }
    }

    private static String filterRuntimeLine(String line) {
        String touch = GestureDiagnostics.filterLine(line);
        if (touch != null) return touch;
        // Only OperationTrace events use a controlled grammar. Raw messages, throwable text,
        // arbitrary tags and all URI/capability values are dropped at this boundary.
        java.util.regex.Matcher normalizedEvent = java.util.regex.Pattern.compile(
                "^(\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3}) "
                        + "([DIWE]) DragShare/Operation: proc=([A-Za-z0-9_.:-]{1,128}) "
                        + "op=([A-Za-z0-9_.:-]{1,128}) stage=([A-Za-z0-9_.:-]{1,128}) "
                        + "outcome=([A-Za-z0-9_.:-]{1,128}) asset=([a-fA-F0-9]{32}|-)"
                        + "(?: callerUid=(\\d+) read=(unknown) delivery=(unknown))?$")
                .matcher(line);
        if (normalizedEvent.matches()) return line;
        java.util.regex.Matcher normalizedHeader = java.util.regex.Pattern.compile(
                "^\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3} [DIWE] "
                        + "DragShare/Log: diagnostic file opened build=[A-Za-z0-9_.+/-]{1,80} "
                        + "level=(debug|info|disabled) destination=(file|system) "
                        + "sampled_debug=(true|false)$").matcher(line);
        if (normalizedHeader.matches()) return line;
        java.util.regex.Matcher event = java.util.regex.Pattern.compile(
                "^(\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3}) "
                        + "([DIWE]) pid=\\d+ uid=\\d+ thread=[^ ]+ DragShare/Operation: "
                        + "proc=([A-Za-z0-9_.:-]{1,128}) op=([A-Za-z0-9_.:-]{1,128}) "
                        + "stage=([A-Za-z0-9_.:-]{1,128}) outcome=([A-Za-z0-9_.:-]{1,128}) "
                        + "asset=([a-fA-F0-9]{32}|-)(?: callerUid=(\\d+) read=(unknown) delivery=(unknown))?$")
                .matcher(line);
        if (event.matches()) {
            String result = event.group(1) + " " + event.group(2) + " DragShare/Operation: proc="
                    + event.group(3) + " op=" + event.group(4) + " stage=" + event.group(5)
                    + " outcome=" + event.group(6) + " asset=" + event.group(7);
            if (event.group(8) != null) {
                result += " callerUid=" + event.group(8) + " read=" + event.group(9)
                        + " delivery=" + event.group(10);
            }
            return result;
        }
        java.util.regex.Matcher header = java.util.regex.Pattern.compile(
                "^(\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3}) "
                        + "([DIWE]) pid=\\d+ uid=\\d+ thread=[^ ]+ DragShare/Log: "
                        + "diagnostic file opened session=[A-Za-z0-9-]{1,80} "
                        + "build=([A-Za-z0-9_.+/-]{1,80}) device=[A-Za-z0-9_. -]{1,100} "
                        + "android=[A-Za-z0-9._/-]{1,30} level=(debug|info|disabled) "
                        + "destination=(file|system) max_bytes=\\d+ sampled_debug=(true|false)$")
                .matcher(line);
        if (header.matches()) {
            return header.group(1) + " " + header.group(2)
                    + " DragShare/Log: diagnostic file opened build=" + header.group(3)
                    + " level=" + header.group(4) + " destination=" + header.group(5)
                    + " sampled_debug=" + header.group(6);
        }
        return null;
    }

    private static String buildSummary(String stateSummary) {
        StringBuilder out = new StringBuilder();
        out.append("MMGG diagnostic bundle\n")
                .append("Version: ").append(BuildConfig.VERSION_NAME).append(" ( ")
                .append(BuildConfig.VERSION_CODE).append(" )\n")
                .append("Environment: ").append(android.os.Build.MANUFACTURER).append(' ')
                .append(android.os.Build.MODEL).append("; Android ")
                .append(android.os.Build.VERSION.RELEASE).append(" (SDK ")
                .append(android.os.Build.VERSION.SDK_INT).append(")\n\n")
                .append("Available state:\n")
                .append(sanitizeStateSummary(stateSummary));
        if (out.charAt(out.length() - 1) != '\n') out.append('\n');
        return out.toString();
    }

    private static String buildManifest(boolean compatibilityAvailable,
                                        String compatibilityFailure,
                                        boolean runtimeAvailable, String logReason) {
        StringBuilder out = new StringBuilder("Diagnostic bundle contents\n");
        out.append("summary.txt: included\n")
                .append("compatibility.txt: ")
                .append(compatibilityAvailable ? "included" : "unavailable")
                .append('\n')
                .append("runtime-log.txt: ")
                .append(runtimeAvailable ? "included" : "unavailable")
                .append('\n');
        if (!compatibilityAvailable) {
            out.append("Compatibility report reason: ")
                    .append(nonEmpty(compatibilityFailure, "Report generation failed."))
                    .append('\n');
        }
        if (!runtimeAvailable) {
            out.append("Runtime log reason: ").append(logReason).append('\n')
                    .append("运行日志未收录：可在设置中启用文件日志，复现问题后重新导出。\n");
        }
        out.append("运行日志仅保留已知的结构化操作追踪和安全日志头；自由文本与异常详情均已过滤。\n");
        out.append("Privacy: no message body, image bytes, raw URI, or capability token is included.\n");
        return out.toString();
    }

    private static String redactSensitive(String value) {
        if (value == null) return "";
        return value.replaceAll("(?i)(?:content|file|https?|shareimage)://[^\\s\\]\"']+",
                        "<redacted-uri>")
                .replaceAll("(?i)\\b(?:token|capability|authorization|bearer)[=: ]+[^\\s,;]+",
                        "<redacted-secret>")
                .replaceAll("(?i)\\b(?:uri|payload|body|text)[=: ]+[^\\s,;]+",
                        "<redacted-sensitive-value>");
    }

    private static String sanitizeStateSummary(String value) {
        java.util.regex.Pattern allowed = java.util.regex.Pattern.compile(
                "^overall=(CHECKING|NORMAL|NEEDS_ACTION|UNKNOWN|NOT_APPLICABLE); "
                        + "root=(CHECKING|READY|UNAVAILABLE|UNKNOWN|NOT_APPLICABLE); "
                        + "injection=(CHECKING|READY|UNAVAILABLE|UNKNOWN|NOT_APPLICABLE); "
                        + "portal_service=(CHECKING|READY|UNAVAILABLE|UNKNOWN|NOT_APPLICABLE); "
                        + "portal_root=(CHECKING|READY|UNAVAILABLE|UNKNOWN|NOT_APPLICABLE); "
                        + "root_input=(CHECKING|READY|UNAVAILABLE|UNKNOWN|NOT_APPLICABLE); "
                        + "press_reader=(CHECKING|READY|UNAVAILABLE|UNKNOWN|NOT_APPLICABLE); "
                        + "overlay=(CHECKING|READY|UNAVAILABLE|UNKNOWN|NOT_APPLICABLE)$");
        if (value != null && allowed.matcher(value.trim()).matches()) return value.trim() + "\n";
        return "<state summary unavailable or contained unsupported text>\n";
    }

    private static String sanitizeCompatibility(String value) {
        StringBuilder safe = new StringBuilder();
        for (String line : redactSensitive(value).split("\\r?\\n")) {
            String trimmed = line.trim();
            // Dynamic hook diagnostics come from other processes and may contain arbitrary text.
            if (trimmed.startsWith("Hyper手势兼容性报告")
                    || trimmed.startsWith("生成时间:")
                    || trimmed.matches("说明: Hyper手势兼容性报告不包含快捷方式名称、联系人和用户选择的应用清单。")
                    || trimmed.matches("=== [^\\r\\n]{1,60} ===")
                    || trimmed.matches("(?:厂商|品牌|型号|设备|产品|Android|安全补丁|增量版本|Fingerprint|当前显示): .{0,256}")
                    || trimmed.matches("(?:Hyper手势|系统界面|小米桌面|LSPosed Manager): [A-Za-z0-9_.-]+ [^\\r\\n]{0,100}")
                    || trimmed.matches("(?:接口状态|最后连接时间|bottom_target_count|honeycomb_target_count):?=?[ A-Za-z0-9_.:-]{0,128}")
                    || trimmed.matches("[A-Za-z0-9_.-]{1,80}=(?:true|false|\\d{1,8})")) {
                safe.append(trimmed).append('\n');
            } else if (trimmed.startsWith("-- SystemUI --") || trimmed.startsWith("-- MiuiHome --")) {
                safe.append(trimmed).append('\n')
                        .append("<free-form hook diagnostics omitted>\n");
            }
        }
        return safe.toString();
    }

    private static String safeFailure(Throwable error) {
        return error == null ? "收集失败。"
                : "读取失败（" + error.getClass().getSimpleName() + "）。";
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static void addText(ZipOutputStream zip, String name, String value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}

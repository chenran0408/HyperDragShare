package com.leaf.hyperdragshare.codex.touchprobe;

import android.content.Context;
import com.leaf.hyperdragshare.codex.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates a compact report or a complete, explicitly user-shared adaptation package. */
public final class TouchProbeExporter {
    private static final String DIRECTORY = "touch-probe";
    private static final String PREFS = "touch_probe_exports";
    private static final String HASH_MAGIC = "hash";
    private static final String CLASS_MAGIC = "class";
    private static final String DATA_MAGIC = "data";
    private static final int ELF_CLASS_64 = 2;
    private static final int ELF_DATA_LITTLE_ENDIAN = 1;
    private static final int EM_AARCH64 = 183;
    private static final ExecutorService COPY_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "touch-probe-library-copy");
                thread.setDaemon(true);
                return thread;
            });

    public enum PackageKind {
        TEXT_REPORT,
        ADAPTER_ZIP
    }

    public static final class Options {
        public final boolean alwaysAttachLibrary;
        public final boolean textOnly;
        public final boolean recaptureLibrary;

        public Options(
                boolean alwaysAttachLibrary,
                boolean textOnly,
                boolean recaptureLibrary) {
            this.alwaysAttachLibrary = alwaysAttachLibrary;
            this.textOnly = textOnly;
            this.recaptureLibrary = recaptureLibrary;
        }

        public static Options smartDefault() {
            return new Options(false, false, false);
        }
    }

    public static final class Result {
        public final File file;
        public final PackageKind kind;
        public final boolean knownLibrary;
        public final boolean libraryAttached;
        public final TouchLibraryCopy libraryCopy;
        public final String refreshedIdentityWarning;

        Result(
                File file,
                PackageKind kind,
                boolean knownLibrary,
                boolean libraryAttached,
                TouchLibraryCopy libraryCopy,
                String refreshedIdentityWarning) {
            this.file = file;
            this.kind = kind;
            this.knownLibrary = knownLibrary;
            this.libraryAttached = libraryAttached;
            this.libraryCopy = libraryCopy;
            this.refreshedIdentityWarning = refreshedIdentityWarning;
        }
    }

    @FunctionalInterface
    interface Discovery {
        TouchDeviceMapParser.DeviceMap discover() throws Exception;
    }

    public static final class TouchLibraryCopy {
        public final String state;
        public final String source;
        public final String sourceSha256;
        public final String copiedSha256;
        public final String error;
        public final long copiedSize;

        TouchLibraryCopy(
                String state,
                String source,
                String sourceSha256,
                String copiedSha256,
                String error,
                long copiedSize) {
            this.state = state;
            this.source = source;
            this.sourceSha256 = safe(sourceSha256);
            this.copiedSha256 = safe(copiedSha256);
            this.error = safe(error);
            this.copiedSize = Math.max(0L, copiedSize);
        }

        static TouchLibraryCopy unavailable(String source, String sourceSha, String error) {
            return new TouchLibraryCopy(
                    "unavailable", source, sourceSha, "", error, 0L);
        }
    }

    private TouchProbeExporter() {}

    public static Result export(
            Context context,
            TouchReportReader.ConnectionInfo connection,
            TouchProbeCollector.Snapshot snapshot,
            String status,
            Options options) throws Exception {
        return export(context, connection, snapshot, status, options, TouchReportReader::discover);
    }

    static Result export(
            Context context,
            TouchReportReader.ConnectionInfo connection,
            TouchProbeCollector.Snapshot snapshot,
            String status,
            Options options,
            Discovery discovery) throws Exception {
        Options effective = options == null ? Options.smartDefault() : options;
        File directory = new File(context.getCacheDir(), DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建触控报告目录");
        }

        String stamp = ExportFileNames.timestampNow();
        TouchDeviceMapParser.DeviceMap identityMap =
                connection == null ? null : connection.map;
        TouchDeviceMapParser.DeviceMap copyMap = null;
        String refreshWarning = "";
        try {
            TouchDeviceMapParser.DeviceMap refreshed = discovery.discover();
            copyMap = refreshed;
            if (identityMap == null
                    || (refreshed.pid == identityMap.pid
                    && refreshed.libraryHash.equalsIgnoreCase(identityMap.libraryHash))) {
                identityMap = refreshed;
            } else {
                refreshWarning = "导出时触控服务或算法库身份发生变化；样本使用连接时身份，"
                        + "算法库包使用当前映射。";
            }
        } catch (Exception error) {
            refreshWarning = "导出时重新读取映射失败，沿用最近一次连接信息："
                    + safe(error.getMessage());
            copyMap = identityMap;
        }

        TouchProfileRegistry.Selection selection = identityMap == null
                ? new TouchProfileRegistry.Selection(
                        TouchProfileRegistry.SAFE_PROBE,
                        TouchProfileRegistry.MatchKind.NONE)
                : TouchProfileRegistry.resolve(identityMap);
        TouchReportReader.ConnectionInfo reportConnection = connection;
        if (reportConnection == null && identityMap != null) {
            String expected = "0x" + identityMap.baseAddress
                    .add(java.math.BigInteger.valueOf(selection.profile.expectedReportRva))
                    .toString(16);
            reportConnection = new TouchReportReader.ConnectionInfo(
                    identityMap, selection, null, expected);
        }
        boolean known = selection.isExactLibrary();
        boolean attach = identityMap != null
                && shouldAttachLibrary(context, identityMap, effective);
        if (identityMap == null
                && (effective.alwaysAttachLibrary || effective.recaptureLibrary)) {
            refreshWarning += " 当前无法读取算法库映射，因此先生成文字报告。";
        }

        String report = TouchProbeReport.build(
                context, reportConnection, snapshot, status);
        report += exportSummary(
                known,
                attach,
                effective,
                refreshWarning,
                identityMap,
                copyMap);

        TouchLibraryCopy libraryCopy = null;
        File copiedLibrary = null;
        File libraryDirectory = new File(directory, "library-cache");
        if (attach) {
            if (!libraryDirectory.exists() && !libraryDirectory.mkdirs()) {
                libraryCopy = TouchLibraryCopy.unavailable(
                        copyMap.libraryPath, copyMap.libraryHash,
                        "无法创建算法库临时目录");
            } else {
                copiedLibrary = new File(libraryDirectory, TouchReportLayout.LIBRARY_NAME);
                libraryCopy = copyLibrary(copyMap, copiedLibrary);
            }
            report += libraryCopySummary(libraryCopy);
        }

        File output;
        if (attach) {
            output = new File(
                    directory,
                    outputFileName(PackageKind.ADAPTER_ZIP, model(identityMap), stamp));
            writeAdapterPackage(
                    output,
                    report,
                    reportConnection,
                    snapshot,
                    identityMap,
                    libraryCopy,
                    copiedLibrary);
        } else {
            output = new File(
                    directory,
                    outputFileName(PackageKind.TEXT_REPORT, null, stamp));
            try (OutputStreamWriterHolder writer = new OutputStreamWriterHolder(output)) {
                writer.write(report);
            }
        }

        if (identityMap != null && !identityMap.libraryHash.isBlank()) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(
                            HASH_MAGIC + identityMap.libraryHash.toLowerCase(Locale.ROOT),
                            true)
                    .apply();
        }
        return new Result(
                output,
                attach ? PackageKind.ADAPTER_ZIP : PackageKind.TEXT_REPORT,
                known,
                attach && libraryCopy != null && "verified".equals(libraryCopy.state),
                libraryCopy,
                refreshWarning);
    }

    static String outputFileName(PackageKind kind, String model, String timestamp) {
        return kind == PackageKind.ADAPTER_ZIP
                ? ExportFileNames.touchReport("zip", model, timestamp)
                : ExportFileNames.touchReport("txt", null, timestamp);
    }

    private static TouchLibraryCopy copyLibrary(
            TouchDeviceMapParser.DeviceMap map,
            File target) {
        String command = libraryCopyCommand(map);
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).start();
            Process activeProcess = process;
            Future<Void> copy = COPY_EXECUTOR.submit(() -> {
                try (InputStream input = activeProcess.getInputStream();
                     OutputStream output = new FileOutputStream(target, false)) {
                    input.transferTo(output);
                }
                return null;
            });
            if (!process.waitFor(12, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return TouchLibraryCopy.unavailable(
                        map.libraryPath, map.libraryHash, "算法库复制超时");
            }
            copy.get(2, TimeUnit.SECONDS);
            String source = readSmall(process.getErrorStream()).trim();
            if (process.exitValue() != 0) {
                return TouchLibraryCopy.unavailable(
                        map.libraryPath,
                        map.libraryHash,
                        "算法库复制失败，退出码 " + process.exitValue()
                                + (source.isEmpty() ? "" : "：" + source));
            }
            String copiedHash = sha256(target);
            if (target.length() == 0) {
                return TouchLibraryCopy.unavailable(
                        map.libraryPath, map.libraryHash, "算法库复制结果为空");
            }
            String validationFailure = copyValidationFailure(
                    map.libraryHash,
                    copiedHash,
                    target.length(),
                    map.librarySize,
                    isElf64Aarch64(target));
            if (!validationFailure.isEmpty()) {
                return new TouchLibraryCopy(
                        "invalid",
                        source,
                        map.libraryHash,
                        copiedHash,
                        validationFailure,
                        target.length());
            }
            return new TouchLibraryCopy(
                    "verified",
                    source,
                    map.libraryHash,
                    copiedHash,
                    "",
                    target.length());
        } catch (Exception error) {
            return TouchLibraryCopy.unavailable(
                    map.libraryPath, map.libraryHash, safe(error.getMessage()));
        } finally {
            if (process != null) process.destroy();
        }
    }

    static String libraryCopyCommand(TouchDeviceMapParser.DeviceMap map) {
        String pid = Integer.toString(map.pid);
        String start = shellQuote(map.mappingStart);
        String end = shellQuote(map.mappingEnd);
        String path = shellQuote(map.libraryPath);
        return "set -u; pid=" + pid + "; start=" + start + "; end=" + end + "; "
                + "mapped=" + path + "; "
                + "map_file=/proc/$pid/map_files/${start}-${end}; "
                + "if [ -r \"$map_file\" ]; then printf 'map_files:%s\\n' \"$map_file\" >&2; "
                + "cat \"$map_file\"; "
                + "elif [ -r \"$mapped\" ]; then printf 'library_path:%s\\n' \"$mapped\" >&2; "
                + "cat \"$mapped\"; "
                + "else printf '未找到可读的算法库映射\\n' >&2; exit 7; fi";
    }

    static boolean shouldAttachLibrary(
            Context context,
            TouchDeviceMapParser.DeviceMap map,
            Options options) {
        Options effective = options == null ? Options.smartDefault() : options;
        if (effective.textOnly) return false;
        if (effective.alwaysAttachLibrary || effective.recaptureLibrary) return true;
        if (TouchProfileRegistry.resolve(map).isExactLibrary()) return false;
        String key = HASH_MAGIC + safe(map.libraryHash).toLowerCase(Locale.ROOT);
        return map.libraryHash.isBlank()
                || !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(key, false);
    }

    static String copyValidationFailure(
            String sourceHash,
            String copiedHash,
            long copiedSize,
            long expectedSize,
            boolean validElf) {
        if (!safe(sourceHash).isBlank()
                && !sourceHash.equalsIgnoreCase(safe(copiedHash))) {
            return "复制前后 SHA-256 不一致";
        }
        if (!validElf) return "文件不是 64 位 arm64 ELF";
        if (expectedSize > 0 && expectedSize != copiedSize) {
            return "复制文件大小与当前映射文件不一致";
        }
        return "";
    }

    static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            for (int count; (count = input.read(buffer)) >= 0;) {
                if (count > 0) digest.update(buffer, 0, count);
            }
        }
        StringBuilder out = new StringBuilder(64);
        for (byte value : digest.digest()) {
            out.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return out.toString();
    }

    static boolean isElf64Aarch64(File file) throws IOException {
        byte[] header = new byte[20];
        try (InputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < header.length) {
                int count = input.read(header, offset, header.length - offset);
                if (count < 0) return false;
                offset += count;
            }
        }
        int machine = (header[18] & 0xff) | ((header[19] & 0xff) << 8);
        return header[0] == 0x7f
                && header[1] == 'E'
                && header[2] == 'L'
                && header[3] == 'F'
                && (header[4] & 0xff) == ELF_CLASS_64
                && (header[5] & 0xff) == ELF_DATA_LITTLE_ENDIAN
                && machine == EM_AARCH64;
    }

    static void writeAdapterPackage(
            File output,
            String report,
            TouchReportReader.ConnectionInfo connection,
            TouchProbeCollector.Snapshot snapshot,
            TouchDeviceMapParser.DeviceMap map,
            TouchLibraryCopy copy,
            File library) throws Exception {
        TouchCompatibilityValidator.Result compatibility =
                TouchCompatibilityValidator.evaluate(
                        connection,
                        snapshot,
                        reportStatus(report));
        try (ZipOutputStream zip = new ZipOutputStream(
                new FileOutputStream(output, false), StandardCharsets.UTF_8)) {
            entry(zip, "report.txt", report);
            entry(zip, "manifest.json", manifest(
                    connection, snapshot, map, compatibility, copy));
            entry(zip, "samples.json", samples(connection, snapshot, compatibility));
            StringBuilder mappings = new StringBuilder();
            for (String line : map.libraryMaps) mappings.append(line).append('\n');
            entry(zip, "mappings/" + TouchReportLayout.LIBRARY_NAME + ".maps.txt",
                    mappings.toString());
            if (library != null && copy != null && "verified".equals(copy.state)) {
                zip.putNextEntry(new ZipEntry("libraries/" + TouchReportLayout.LIBRARY_NAME));
                try (InputStream input = new FileInputStream(library)) {
                    input.transferTo(zip);
                }
                zip.closeEntry();
                entry(zip, "libraries/" + TouchReportLayout.LIBRARY_NAME + ".sha256",
                        copy.copiedSha256 + "  " + TouchReportLayout.LIBRARY_NAME + "\n");
            }
        }
    }

    private static String manifest(
            TouchReportReader.ConnectionInfo connection,
            TouchProbeCollector.Snapshot snapshot,
            TouchDeviceMapParser.DeviceMap map,
            TouchCompatibilityValidator.Result compatibility,
            TouchLibraryCopy copy) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schema", "mmgg-touch-probe-package-v1");
        root.put("generated_at", new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(new Date()));
        root.put("app_version", BuildConfig.VERSION_NAME);
        root.put("version_code", BuildConfig.VERSION_CODE);
        root.put("channel", BuildConfig.UPDATE_CHANNEL);
        root.put("formal_trigger_enabled", false);
        root.put("privacy", "不包含页面文字、截图、剪贴板或应用内容。");
        root.put("absolute_addresses_scope", "仅对本次触控服务生命周期有效");
        root.put("failure_stage", safe(compatibility.failureStage));
        root.put("layout_classification", compatibility.classification.name());
        root.put("library_copy", copy == null ? "unavailable" : copy.state);
        root.put("library_copy_error", copy == null ? "未请求附带算法库" : copy.error);
        JSONObject device = new JSONObject();
        device.put("manufacturer", property(map, "ro.product.manufacturer"));
        device.put("model", property(map, "ro.product.model"));
        device.put("device", property(map, "ro.product.device"));
        device.put("android", property(map, "ro.build.version.release"));
        device.put("sdk", property(map, "ro.build.version.sdk"));
        device.put("hyperos", property(map, "ro.mi.os.version.name"));
        device.put("build", property(map, "ro.mi.os.version.incremental"));
        device.put("fingerprint", property(map, "ro.build.fingerprint"));
        device.put("abi", String.join(",", android.os.Build.SUPPORTED_ABIS));
        root.put("device", device);
        JSONObject library = new JSONObject();
        library.put("pid", map.pid);
        library.put("path", safe(map.libraryPath));
        library.put("sha256", safe(map.libraryHash));
        library.put("size", map.librarySize);
        library.put("code_sha256", safe(map.codeSignature));
        library.put("mapping_start", safe(map.mappingStart));
        library.put("mapping_end", safe(map.mappingEnd));
        library.put("mapping_lines", new JSONArray(map.libraryMaps));
        root.put("library", library);
        TouchProfileRegistry.Profile profile = connection == null
                ? TouchProfileRegistry.SAFE_PROBE : connection.selection.profile;
        JSONObject profileJson = new JSONObject();
        profileJson.put("profile_id", compatibility.profileId);
        profileJson.put("match", compatibility.matchKind.name());
        profileJson.put("global_pointer_rva", hex(profile.globalPointerRva));
        profileJson.put("expected_report_rva", hex(profile.expectedReportRva));
        profileJson.put("report_size", hex(profile.reportSize));
        profileJson.put("slot_count", profile.slotCount);
        profileJson.put("slot_stride", hex(profile.slotStride));
        profileJson.put("validation_status", safe(profile.validationStatus));
        root.put("profile", profileJson);
        JSONObject relation = new JSONObject();
        relation.put("library_base", hex(map.baseAddress));
        relation.put("global_pointer_rva", difference(
                map.globalPointerAddress.toString(), map.baseAddress));
        relation.put("expected_report_rva", connection == null
                ? JSONObject.NULL : difference(connection.expectedAddress, map.baseAddress));
        relation.put("runtime_report_rva", connection == null
                ? JSONObject.NULL : difference(connection.pointerAddress, map.baseAddress));
        relation.put("runtime_minus_expected", connection == null
                ? JSONObject.NULL : difference(
                        connection.pointerAddress, connection.expectedAddress));
        root.put("address_relation", relation);
        return root.toString(2);
    }

    private static String samples(
            TouchReportReader.ConnectionInfo connection,
            TouchProbeCollector.Snapshot snapshot,
            TouchCompatibilityValidator.Result compatibility) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schema", "mmgg-touch-probe-samples-v2");
        root.put("layout_classification", compatibility.classification.name());
        root.put("frames", snapshot.frames);
        root.put("metadata_changes", snapshot.metadataChanges);
        root.put("fingertip_peak", snapshot.fingertipPeak());
        root.put("minimum_thumb_peak", snapshot.minimumThumbPeak());
        root.put("separation_observed", snapshot.hasSeparation());
        JSONArray fields = new JSONArray();
        for (TouchProbeCollector.WordStats word : snapshot.words) {
            JSONObject field = new JSONObject();
            field.put("offset", hex(word.offset));
            field.put("count", word.count);
            field.put("min", word.min);
            field.put("max", word.max);
            field.put("avg", word.average());
            field.put("zero", word.zeros);
            field.put("negative", word.negatives);
            fields.put(field);
        }
        root.put("fields", fields);
        JSONArray slotFields = new JSONArray();
        for (int slot = 0; slot < snapshot.slotWords.length; slot++) {
            JSONObject slotJson = new JSONObject();
            slotJson.put("slot", slot);
            JSONArray words = new JSONArray();
            for (TouchProbeCollector.WordStats word : snapshot.slotWords[slot]) {
                JSONObject field = new JSONObject();
                field.put("offset", hex(word.offset));
                field.put("count", word.count);
                field.put("min", word.min);
                field.put("max", word.max);
                field.put("avg", word.average());
                field.put("zero", word.zeros);
                field.put("negative", word.negatives);
                words.put(field);
            }
            slotJson.put("fields", words);
            slotFields.put(slotJson);
        }
        root.put("slot_fields", slotFields);
        JSONArray samples = new JSONArray();
        for (TouchProbeCollector.GestureSample sample : snapshot.gestureSamples) {
            JSONObject item = new JSONObject();
            item.put("group", sample.group.name());
            item.put("valid", sample.valid);
            item.put("duration_ms", sample.durationMs());
            item.put("observation_frames", sample.observationFrames);
            item.put("single_contact_frames", sample.singleContactFrames);
            item.put("multi_contact_frames", sample.multiContactFrames);
            item.put("max_contacts", sample.maxContacts);
            item.put("peak_2c", sample.peak2C);
            item.put("peak_30", sample.peak30);
            item.put("peak_34", sample.peak34);
            item.put("peak_80", sample.peak80);
            item.put("invalid_reason", safe(sample.invalidReason));
            samples.put(item);
        }
        root.put("samples", samples);
        root.put("events", new JSONArray(snapshot.events));
        root.put("absolute_addresses_scope", "仅对本次触控服务生命周期有效");
        return root.toString(2);
    }

    private static String exportSummary(
            boolean known,
            boolean attach,
            Options options,
            String warning,
            TouchDeviceMapParser.DeviceMap identity,
            TouchDeviceMapParser.DeviceMap copyMap) {
        StringBuilder out = new StringBuilder();
        out.append("\n=== 导出 ===\n");
        out.append("package_kind=").append(attach ? "adapter_zip" : "text_report").append('\n');
        out.append("known_library_sha256=").append(known).append('\n');
        out.append("always_attach_library=").append(options.alwaysAttachLibrary).append('\n');
        out.append("text_only=").append(options.textOnly).append('\n');
        out.append("recapture_library=").append(options.recaptureLibrary).append('\n');
        out.append("export_identity_pid=")
                .append(identity == null ? "unavailable" : identity.pid).append('\n');
        out.append("export_copy_pid=")
                .append(copyMap == null ? "unavailable" : copyMap.pid).append('\n');
        out.append("export_identity_sha256=")
                .append(identity == null ? "unavailable" : safe(identity.libraryHash))
                .append('\n');
        out.append("export_copy_sha256=")
                .append(copyMap == null ? "unavailable" : safe(copyMap.libraryHash))
                .append('\n');
        if (!warning.isBlank()) out.append("refresh_warning=").append(safe(warning)).append('\n');
        return out.toString();
    }

    private static String libraryCopySummary(TouchLibraryCopy copy) {
        if (copy == null) return "";
        StringBuilder out = new StringBuilder();
        out.append("\n=== 算法库复制 ===\n");
        out.append("library_copy=").append(copy.state).append('\n');
        out.append("library_copy_source=").append(copy.source).append('\n');
        out.append("library_sha256_before=").append(copy.sourceSha256).append('\n');
        out.append("library_sha256_after=").append(copy.copiedSha256).append('\n');
        out.append("library_copied_size=").append(copy.copiedSize).append('\n');
        out.append("library_copy_error=").append(copy.error).append('\n');
        return out.toString();
    }

    private static String reportStatus(String report) {
        int start = report.indexOf("status=");
        if (start < 0) return "";
        start += "status=".length();
        int end = report.indexOf('\n', start);
        return end < 0 ? report.substring(start) : report.substring(start, end);
    }

    private static void entry(ZipOutputStream zip, String name, String body)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(body.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String readSmall(InputStream input) {
        try (InputStream stream = input) {
            return new String(stream.readNBytes(16 * 1024), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    private static String shellQuote(String value) {
        return "'" + safe(value).replace("'", "'\\''") + "'";
    }

    private static String difference(String first, java.math.BigInteger second) {
        try {
            java.math.BigInteger value = new java.math.BigInteger(first.substring(2), 16)
                    .subtract(second);
            return value.signum() < 0
                    ? "-0x" + value.negate().toString(16)
                    : "0x" + value.toString(16);
        } catch (RuntimeException ignored) {
            return "unavailable";
        }
    }

    private static String difference(String first, String second) {
        if (first == null || second == null
                || !first.startsWith("0x") || !second.startsWith("0x")) {
            return "unavailable";
        }
        try {
            return difference(first, new java.math.BigInteger(second.substring(2), 16));
        } catch (RuntimeException ignored) {
            return "unavailable";
        }
    }

    private static String hex(long value) {
        return "0x" + Long.toHexString(value);
    }

    private static String hex(int value) {
        return "0x" + Integer.toHexString(value);
    }

    private static String hex(java.math.BigInteger value) {
        return "0x" + value.toString(16);
    }

    private static String property(TouchDeviceMapParser.DeviceMap map, String key) {
        return safe(map.properties.get(key));
    }

    private static String model(TouchDeviceMapParser.DeviceMap map) {
        if (map == null) return "unknown-model";
        String value = map.properties.get("ro.product.model");
        return value == null || value.isBlank() ? "unknown-model" : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replace('\0', '?');
    }

    private static final class OutputStreamWriterHolder implements AutoCloseable {
        private final java.io.OutputStreamWriter writer;

        OutputStreamWriterHolder(File file) throws IOException {
            writer = new java.io.OutputStreamWriter(
                    new FileOutputStream(file, false), StandardCharsets.UTF_8);
        }

        void write(String value) throws IOException {
            writer.write(value);
        }

        @Override
        public void close() throws IOException {
            writer.close();
        }
    }
}

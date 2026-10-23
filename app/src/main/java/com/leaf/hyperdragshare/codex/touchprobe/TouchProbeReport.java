package com.leaf.hyperdragshare.codex.touchprobe;

import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;

import com.leaf.hyperdragshare.codex.BuildConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Produces a standalone compatibility report suitable for returning to the developers. */
public final class TouchProbeReport {
    public static final String SCHEMA = "mmgg-touch-probe-v2";
    private static final String DIRECTORY = "touch-probe";
    private static final String LATEST_FILE = "touch-probe-latest.txt";

    private TouchProbeReport() {}

    public static String exportFileName() {
        return ExportFileNames.touchReport("txt", null, ExportFileNames.timestampNow());
    }

    public static File writeLatest(
            Context context,
            TouchReportReader.ConnectionInfo connection,
            TouchProbeCollector.Snapshot snapshot,
            String status) throws Exception {
        File directory = new File(context.getCacheDir(), DIRECTORY);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("无法创建触控报告目录");
        }
        File report = new File(directory, LATEST_FILE);
        String content = build(context, connection, snapshot, status);
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(report, false), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
        return report;
    }

    public static String build(
            Context context,
            TouchReportReader.ConnectionInfo connection,
            TouchProbeCollector.Snapshot snapshot,
            String status) {
        StringBuilder out = new StringBuilder(32 * 1024);
        TouchCompatibilityValidator.Result compatibility =
                TouchCompatibilityValidator.evaluate(connection, snapshot, status);
        TouchProfileRegistry.Profile profile = connection == null
                ? TouchProfileRegistry.SAFE_PROBE
                : connection.selection.profile;
        line(out, "MMGG 小米触控报告内测");
        line(out, "schema=" + SCHEMA);
        line(out, "generated_at=" + new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(new Date()));
        line(out, "app=" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        line(out, "channel=" + BuildConfig.UPDATE_CHANNEL);
        line(out, "status=" + safe(status));
        line(out, "layout_classification=" + compatibility.classification.name());
        line(out, "profile_id=" + safe(compatibility.profileId));
        line(out, "profile_match=" + compatibility.matchKind.name());
        line(out, "profile_match_exact=" + compatibility.exactProfile);
        line(out, "failure_stage=" + safe(compatibility.failureStage));
        line(out, "layout_trusted_for_read=" + compatibility.layoutTrustedForRead);
        line(out, "calibrated_for_trigger=" + compatibility.calibratedForTrigger);
        line(out, "formal_trigger_enabled=false");
        line(out, "privacy=仅包含触控数值、设备版本和模块状态，"
                + "不包含页面文字、截图、剪贴板或应用内容。");

        section(out, "设备");
        line(out, "manufacturer=" + safe(Build.MANUFACTURER));
        line(out, "brand=" + safe(Build.BRAND));
        line(out, "model=" + safe(Build.MODEL));
        line(out, "device=" + safe(Build.DEVICE));
        line(out, "product=" + safe(Build.PRODUCT));
        line(out, "android=" + safe(Build.VERSION.RELEASE) + " sdk=" + Build.VERSION.SDK_INT);
        line(out, "incremental=" + safe(Build.VERSION.INCREMENTAL));
        line(out, "fingerprint=" + safe(Build.FINGERPRINT));
        line(out, "abis=" + String.join(",", Build.SUPPORTED_ABIS));
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        line(out, "display=" + metrics.widthPixels + "x" + metrics.heightPixels
                + " density=" + metrics.density + " dpi=" + metrics.densityDpi);

        section(out, "布局假设");
        line(out, "profile=" + TouchReportLayout.PROFILE);
        line(out, "selected_profile_id=" + safe(profile.id));
        line(out, "service=" + TouchReportLayout.SERVICE_NAME);
        line(out, "library=" + TouchReportLayout.LIBRARY_NAME);
        line(out, "global_pointer_rva=0x" + Long.toHexString(profile.globalPointerRva));
        line(out, "expected_report_rva=0x" + Long.toHexString(profile.expectedReportRva));
        line(out, "slots=" + profile.slotCount);
        line(out, "slot_stride=0x" + Integer.toHexString(profile.slotStride));
        line(out, "report_size=0x" + Integer.toHexString(profile.reportSize));
        line(out, "field_offsets=" + offsets(TouchReportLayout.RECORDED_WORD_OFFSETS));
        line(out, "profile_static_reference_validated=" + profile.staticReferenceValidated);
        line(out, "profile_validation_status=" + safe(profile.validationStatus));
        line(out, "profile_separation_multiplier=" + profile.separationMultiplier);
        line(out, "profile_minimum_separation_delta=" + profile.minimumSeparationDelta);
        line(out, "selection_rule=library_sha256 -> rom+code_signature_candidate -> unknown_safe_probe");
        line(out, "assumption=型号和库路径不用于选择布局；未验证布局只读采集。");

        section(out, "连接");
        if (connection == null) {
            line(out, "connection=unavailable");
            line(out, "profile_key=unavailable");
            line(out, "device_profile_key=unavailable");
        } else {
            TouchDeviceMapParser.DeviceMap map = connection.map;
            line(out, "connection=ready");
            line(out, "service_pid=" + map.pid);
            line(out, "root_identity=" + safe(map.rootIdentity));
            line(out, "library_path=" + safe(map.libraryPath));
            line(out, "library_sha256=" + safe(map.libraryHash));
            line(out, "library_code_sha256=" + safe(map.codeSignature));
            line(out, "library_size="
                    + (map.librarySize > 0 ? map.librarySize : "unavailable"));
            line(out, "library_mapping_start=" + safe(map.mappingStart));
            line(out, "library_mapping_end=" + safe(map.mappingEnd));
            line(out, "library_base=0x" + map.baseAddress.toString(16));
            line(out, "global_pointer_address=0x" + map.globalPointerAddress.toString(16));
            line(out, "global_pointer_rva="
                    + addressDifference(
                            "0x" + map.globalPointerAddress.toString(16), map));
            line(out, "runtime_report_pointer=" + connection.pointerAddress);
            line(out, "expected_report_pointer=" + connection.expectedAddress);
            line(out, "expected_report_rva="
                    + addressDifference(connection.expectedAddress, map));
            line(out, "pointer_matches_profile=" + connection.pointerMatchesProfile);
            line(out, "runtime_report_rva=" + addressDifference(connection.pointerAddress, map));
            line(out, "runtime_report_pointer_minus_expected="
                    + pointerDifference(connection.pointerAddress, connection.expectedAddress));
            line(out, "rva_relationship=" + rvaRelationship(connection, map));
            line(out, "hyperos_name="
                    + safe(map.properties.get("ro.mi.os.version.name")));
            line(out, "hyperos_incremental="
                    + safe(map.properties.get("ro.mi.os.version.incremental")));
            line(out, "profile_key=" + TouchProfileRegistry.deviceBuildLibraryKey(map));
            line(out, "device_profile_key=" + TouchProfileRegistry.deviceProfileKey(map));
            line(out, "absolute_addresses_scope=current_process_only");
            line(out, "commands=" + formatMap(map.commands));
            line(out, "system_properties=" + formatMap(map.properties));
            line(out, "library_maps_begin");
            for (String mapping : map.libraryMaps) line(out, "  " + mapping);
            line(out, "library_maps_end");
        }

        section(out, "样本总量");
        line(out, "frames=" + snapshot.frames);
        line(out, "active_frames=" + snapshot.activeFrames);
        line(out, "active_slots=" + snapshot.activeSlots);
        line(out, "latest_active_slots=" + snapshot.latestActiveCount);
        line(out, "latest_metadata=" + snapshot.latestMeta);
        line(out, "latest=" + safe(snapshot.latestActiveSlots));
        line(out, "states_up=" + state(snapshot.states, 0)
                + " down=" + state(snapshot.states, 1)
                + " move=" + state(snapshot.states, 2)
                + " other=" + state(snapshot.states, 3));

        section(out, "运行时校验");
        line(out, "metadata_changes=" + snapshot.metadataChanges);
        line(out, "invalid_enabled_values=" + snapshot.invalidEnabledValues);
        line(out, "invalid_state_values=" + snapshot.invalidStateValues);
        line(out, "invalid_coordinate_values=" + snapshot.invalidCoordinateValues);
        line(out, "validation_detail=" + safe(compatibility.detail));

        section(out, "槽位字段分布");
        line(out, "# 仅统计enabled!=0且state=1/2的槽位；每行 n min max avg zero negative");
        for (TouchProbeCollector.WordStats stats : snapshot.words) {
            line(out, String.format(Locale.US, "+0x%02x n=%d min=%d max=%d avg=%.3f zero=%d negative=%d",
                    stats.offset, stats.count, stats.min, stats.max, stats.average(),
                    stats.zeros, stats.negatives));
        }
        section(out, "逐槽位字段分布");
        for (int slot = 0; slot < snapshot.slotWords.length; slot++) {
            line(out, "slot_" + slot + "_begin");
            for (TouchProbeCollector.WordStats stats : snapshot.slotWords[slot]) {
                line(out, String.format(
                        Locale.US,
                        "+0x%02x n=%d min=%d max=%d avg=%.3f zero=%d negative=%d",
                        stats.offset,
                        stats.count,
                        stats.min,
                        stats.max,
                        stats.average(),
                        stats.zeros,
                        stats.negatives));
            }
            line(out, "slot_" + slot + "_end");
        }
        line(out, "quiet_candidate_fields_begin");
        for (TouchProbeCollector.WordStats stats : snapshot.quietWords) {
            line(out, String.format(Locale.US,
                    "+0x%02x n=%d min=%d max=%d avg=%.3f zero=%d negative=%d",
                    stats.offset, stats.count, stats.min, stats.max, stats.average(),
                    stats.zeros, stats.negatives));
        }
        line(out, "quiet_candidate_fields_end");

        section(out, "分组采样");
        for (TouchProbeCollector.Group group : TouchProbeCollector.Group.values()) {
            TouchProbeCollector.GroupStats stats = snapshot.groups.get(group);
            if (stats == null) continue;
            line(out, group.name() + " label=" + group.label
                    + " gestures=" + stats.gestures
                    + " sample_frames=" + stats.sampleFrames
                    + " min_peak=" + stats.minPeak
                    + " max_peak=" + stats.maxPeak
                    + " peaks=" + stats.gesturePeaks);
        }
        line(out, "fingertip_peak=" + snapshot.fingertipPeak());
        line(out, "minimum_thumb_peak=" + snapshot.minimumThumbPeak());
        line(out, "separation_observed=" + snapshot.hasSeparation());
        line(out, "separation_rule=T>=1.15*F且T-F>=1；这里F=指尖历史最大峰值，T=指腹历史最小手势峰值。");
        line(out, "interpretation=该结论只描述本次设备与样本；字段+0x34仍需跨设备回归，不能当作最终面积语义。");

        section(out, "设备校准");
        TouchProbeCollector.GroupStats fingertip =
                snapshot.groups.get(TouchProbeCollector.Group.FINGERTIP);
        TouchProbeCollector.GroupStats thumb =
                snapshot.groups.get(TouchProbeCollector.Group.THUMB);
        TouchProbeCollector.WordStats quiet34 = quietStats(
                snapshot.quietWords, TouchReportLayout.OFFSET_CANDIDATE_34);
        line(out, "calibration_scope=device_build+library_sha256");
        line(out, "fingertip_valid_samples="
                + (fingertip == null ? 0 : fingertip.gestures));
        line(out, "thumb_valid_samples="
                + (thumb == null ? 0 : thumb.gestures));
        line(out, "fingertip_peak_range="
                + (fingertip == null ? "unavailable"
                : fingertip.minPeak + ".." + fingertip.maxPeak));
        line(out, "thumb_peak_range="
                + (thumb == null ? "unavailable"
                : thumb.minPeak + ".." + thumb.maxPeak));
        line(out, "baseline_34_range="
                + (quiet34 == null ? "unavailable" : quiet34.min + ".." + quiet34.max));
        boolean enoughSamples = fingertip != null && thumb != null
                && fingertip.gestures >= 3 && thumb.gestures >= 3;
        boolean thresholdAvailable = enoughSamples && snapshot.hasSeparation();
        line(out, "recommended_trigger_threshold="
                + (thresholdAvailable ? thumbnailThreshold(fingertip, thumb) : "unavailable"));
        line(out, "threshold_status="
                + (thresholdAvailable ? "candidate_after_three_samples_per_group"
                : "pending_real_samples"));
        line(out, "continuous_trigger_rule=2_consecutive_valid_samples(DRAFT)");
        line(out, "cooldown_ms=500(DRAFT_NOT_DEVICE_CALIBRATED)");
        line(out, "category_supported=" + compatibility.calibratedForTrigger);
        line(out, "formal_trigger_ready=false(probe_only)");

        section(out, "单次样本明细");
        line(out, "# 每个样本对应连续采集中的一次单次按压；无效样本保留，但不进入分组统计。");
        if (snapshot.gestureSamples.isEmpty()) {
            line(out, "none");
        } else {
            int index = 0;
            for (TouchProbeCollector.GestureSample sample : snapshot.gestureSamples) {
                index++;
                line(out, "sample_" + index + "_group=" + sample.group.name()
                        + " label=" + sample.group.label
                        + " valid=" + sample.valid);
                line(out, "  duration_ms=" + sample.durationMs()
                        + " observation_frames=" + sample.observationFrames
                        + " single_contact_frames=" + sample.singleContactFrames
                        + " multi_contact_frames=" + sample.multiContactFrames
                        + " max_contacts=" + sample.maxContacts);
                line(out, "  peak_2c=" + sample.peak2C
                        + " peak_30=" + sample.peak30
                        + " peak_34=" + sample.peak34
                        + " peak_80=" + sample.peak80);
                line(out, "  invalid_reason=" + safe(sample.invalidReason));
            }
        }

        section(out, "事件");
        List<String> events = snapshot.events;
        if (events.isEmpty()) {
            line(out, "none");
        } else {
            for (String event : events) line(out, event);
        }
        return out.toString();
    }

    private static String offsets(int[] offsets) {
        StringBuilder out = new StringBuilder();
        for (int offset : offsets) {
            if (out.length() > 0) out.append(',');
            out.append("0x").append(Integer.toHexString(offset));
        }
        return out.toString();
    }

    private static long state(long[] values, int index) {
        return values != null && index >= 0 && index < values.length ? values[index] : 0L;
    }

    private static String addressDifference(
            String address,
            TouchDeviceMapParser.DeviceMap map) {
        if (address == null || !address.startsWith("0x")) return "unavailable";
        try {
            BigInteger difference = new BigInteger(address.substring(2), 16)
                    .subtract(map.baseAddress);
            return difference.signum() < 0
                    ? "-0x" + difference.negate().toString(16)
                    : "0x" + difference.toString(16);
        } catch (NumberFormatException ignored) {
            return "invalid";
        }
    }

    private static String pointerDifference(String first, String second) {
        if (first == null || second == null
                || !first.startsWith("0x") || !second.startsWith("0x")) {
            return "unavailable";
        }
        try {
            BigInteger difference = new BigInteger(first.substring(2), 16)
                    .subtract(new BigInteger(second.substring(2), 16));
            return difference.signum() < 0
                    ? "-0x" + difference.negate().toString(16)
                    : "0x" + difference.toString(16);
        } catch (NumberFormatException ignored) {
            return "invalid";
        }
    }

    private static String rvaRelationship(
            TouchReportReader.ConnectionInfo connection,
            TouchDeviceMapParser.DeviceMap map) {
        TouchProfileRegistry.Profile profile = connection.selection.profile;
        BigInteger runtimeRva;
        try {
            runtimeRva = new BigInteger(connection.pointerAddress.substring(2), 16)
                    .subtract(map.baseAddress);
        } catch (RuntimeException ignored) {
            return "UNREADABLE";
        }
        if (runtimeRva.equals(BigInteger.valueOf(profile.expectedReportRva))) {
            return "REFERENCE_STATIC_RVA";
        }
        return "DYNAMIC_RVA";
    }

    private static TouchProbeCollector.WordStats quietStats(
            TouchProbeCollector.WordStats[] stats,
            int offset) {
        if (stats == null) return null;
        for (TouchProbeCollector.WordStats item : stats) {
            if (item.offset == offset) return item;
        }
        return null;
    }

    private static int thumbnailThreshold(
            TouchProbeCollector.GroupStats fingertip,
            TouchProbeCollector.GroupStats thumb) {
        return fingertip.maxPeak + Math.max(1, (thumb.minPeak - fingertip.maxPeak) / 2);
    }

    private static String formatMap(Map<String, String> values) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) out.append(';');
            first = false;
            out.append(entry.getKey()).append('=').append(safe(entry.getValue()));
        }
        return out.append('}').toString();
    }

    private static void section(StringBuilder out, String title) {
        out.append('\n').append("=== ").append(title).append(" ===\n");
    }

    private static void line(StringBuilder out, String value) {
        out.append(value == null ? "" : value).append('\n');
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replace('\0', '?');
    }
}

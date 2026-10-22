package com.leaf.hyperdragshare.codex.touchprobe;

/** Converts discovery and runtime evidence into a conservative compatibility state. */
public final class TouchCompatibilityValidator {
    private static final int MIN_DYNAMIC_FRAMES = 20;
    private static final int REQUIRED_VALID_SAMPLES_PER_GROUP = 1;

    private TouchCompatibilityValidator() {}

    public static Result evaluate(
            TouchReportReader.ConnectionInfo connection,
            TouchProbeCollector.Snapshot snapshot,
            String status) {
        if (connection == null) {
            return new Result(
                    TouchProfileRegistry.LayoutClassification.UNREADABLE,
                    "",
                    TouchProfileRegistry.MatchKind.NONE,
                    false,
                    false,
                    "connection",
                    "连接、全局指针或报告缓冲区不可读",
                    false,
                    false);
        }

        TouchProfileRegistry.Selection selection = connection.selection;
        TouchProfileRegistry.Profile profile = selection.profile;
        if (selection.matchKind == TouchProfileRegistry.MatchKind.NONE) {
            return new Result(
                    TouchProfileRegistry.LayoutClassification.UNKNOWN_LAYOUT,
                    profile.id,
                    selection.matchKind,
                    false,
                    false,
                    "profile_identity",
                    "算法库哈希与代码签名均未命中已知 profile",
                    false,
                    false);
        }

        if (selection.matchKind != TouchProfileRegistry.MatchKind.NONE
                && !rvaIsMapped(connection, profile.globalPointerRva)) {
            return new Result(
                    TouchProfileRegistry.LayoutClassification.RVA_MISMATCH,
                    profile.id,
                    selection.matchKind,
                    true,
                    false,
                    "rva_mapping",
                    "global pointer RVA 不在算法库映射范围内",
                    false,
                    false);
        }

        if (status != null && status.startsWith("读取失败")) {
            return new Result(
                    TouchProfileRegistry.LayoutClassification.UNREADABLE,
                    profile.id,
                    selection.matchKind,
                    selection.isExactLibrary(),
                    connection.pointerMatchesProfile,
                    "runtime_read",
                    status,
                    false,
                    false);
        }

        if (!connectionPointerUsable(connection, snapshot)) {
            return new Result(
                    TouchProfileRegistry.LayoutClassification.UNREADABLE,
                    profile.id,
                    selection.matchKind,
                    selection.isExactLibrary(),
                    connection.pointerMatchesProfile,
                    "pointer_read",
                    "运行报告指针为空、未按 8 字节对齐或当前不可读",
                    false,
                    false);
        }

        String failure = dynamicFailureStage(connection, snapshot);
        if (!failure.isEmpty()) {
            return new Result(
                    TouchProfileRegistry.LayoutClassification.SAME_LIB_UNVERIFIED,
                    profile.id,
                    selection.matchKind,
                    selection.isExactLibrary(),
                    connection.pointerMatchesProfile,
                    failure,
                    "算法库命中，但布局仍需更多真实单指样本验证",
                    false,
                    false);
        }

        if (connection.pointerMatchesProfile && profile.staticReferenceValidated) {
            return new Result(
                    TouchProfileRegistry.LayoutClassification.REFERENCE_STATIC,
                    profile.id,
                    selection.matchKind,
                    selection.isExactLibrary(),
                    true,
                    "none",
                    "算法库命中，运行指针位于参考静态缓冲区，且两类真实单指样本通过验证",
                    true,
                    triggerCalibrated(snapshot));
        }

        return new Result(
                TouchProfileRegistry.LayoutClassification.DYNAMIC_VALID,
                profile.id,
                selection.matchKind,
                selection.isExactLibrary(),
                false,
                "none",
                "运行报告使用动态地址，地址持续可读且两类真实单指样本通过验证",
                true,
                triggerCalibrated(snapshot));
    }

    private static boolean rvaIsMapped(
            TouchReportReader.ConnectionInfo connection,
            long rva) {
        return connection.map.containsAddress(
                connection.map.baseAddress.add(java.math.BigInteger.valueOf(rva)));
    }

    private static String dynamicFailureStage(
            TouchReportReader.ConnectionInfo connection,
            TouchProbeCollector.Snapshot snapshot) {
        if (snapshot.frames < MIN_DYNAMIC_FRAMES) return "continuous_read";
        if (snapshot.metadataChanges < 2) return "metadata_change";
        if (snapshot.invalidEnabledValues > 0) return "enabled_range";
        if (snapshot.invalidStateValues > 0) return "state_range";
        if (snapshot.invalidCoordinateValues > 0) return "coordinate_range";
        if (!hasNonZeroCandidateData(snapshot)) return "candidate_fields";
        if (validSamples(snapshot, TouchProbeCollector.Group.FINGERTIP)
                < REQUIRED_VALID_SAMPLES_PER_GROUP) {
            return "fingertip_real_press";
        }
        if (validSamples(snapshot, TouchProbeCollector.Group.THUMB)
                < REQUIRED_VALID_SAMPLES_PER_GROUP) {
            return "thumb_real_press";
        }
        return "";
    }

    private static boolean connectionPointerUsable(
            TouchReportReader.ConnectionInfo connection,
            TouchProbeCollector.Snapshot snapshot) {
        if (connection.pointerAddress == null) return false;
        try {
            long address = Long.decode(connection.pointerAddress);
            return address > 0 && (address & 7L) == 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean hasNonZeroCandidateData(TouchProbeCollector.Snapshot snapshot) {
        for (TouchProbeCollector.WordStats stats : snapshot.words) {
            if (stats.offset == TouchReportLayout.OFFSET_CANDIDATE_2C
                    || stats.offset == TouchReportLayout.OFFSET_CANDIDATE_30
                    || stats.offset == TouchReportLayout.OFFSET_CANDIDATE_34
                    || stats.offset == TouchReportLayout.OFFSET_CANDIDATE_80) {
                if (stats.max != 0 || stats.min != 0) return true;
            }
        }
        return false;
    }

    private static int validSamples(
            TouchProbeCollector.Snapshot snapshot,
            TouchProbeCollector.Group group) {
        int count = 0;
        for (TouchProbeCollector.GestureSample sample : snapshot.gestureSamples) {
            if (sample.group == group && sample.valid) count++;
        }
        return count;
    }

    private static boolean triggerCalibrated(TouchProbeCollector.Snapshot snapshot) {
        TouchProbeCollector.GroupStats fingertip =
                snapshot.groups.get(TouchProbeCollector.Group.FINGERTIP);
        TouchProbeCollector.GroupStats thumb =
                snapshot.groups.get(TouchProbeCollector.Group.THUMB);
        return fingertip != null && thumb != null
                && fingertip.gestures >= 3
                && thumb.gestures >= 3
                && snapshot.hasSeparation();
    }

    public static final class Result {
        public final TouchProfileRegistry.LayoutClassification classification;
        public final String profileId;
        public final TouchProfileRegistry.MatchKind matchKind;
        public final boolean exactProfile;
        public final boolean staticPointer;
        public final String failureStage;
        public final String detail;
        public final boolean layoutTrustedForRead;
        public final boolean calibratedForTrigger;

        Result(
                TouchProfileRegistry.LayoutClassification classification,
                String profileId,
                TouchProfileRegistry.MatchKind matchKind,
                boolean exactProfile,
                boolean staticPointer,
                String failureStage,
                String detail,
                boolean layoutTrustedForRead,
                boolean calibratedForTrigger) {
            this.classification = classification;
            this.profileId = profileId;
            this.matchKind = matchKind;
            this.exactProfile = exactProfile;
            this.staticPointer = staticPointer;
            this.failureStage = failureStage;
            this.detail = detail;
            this.layoutTrustedForRead = layoutTrustedForRead;
            this.calibratedForTrigger = calibratedForTrigger;
        }
    }
}

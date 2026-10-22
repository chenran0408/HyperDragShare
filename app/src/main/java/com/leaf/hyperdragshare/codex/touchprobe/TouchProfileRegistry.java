package com.leaf.hyperdragshare.codex.touchprobe;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Algorithm-library-first layout registry. Device names never select a layout directly. */
public final class TouchProfileRegistry {
    public enum MatchKind {
        EXACT_LIBRARY,
        CODE_SIGNATURE_CANDIDATE,
        NONE
    }

    public enum LayoutClassification {
        REFERENCE_STATIC,
        DYNAMIC_VALID,
        SAME_LIB_UNVERIFIED,
        RVA_MISMATCH,
        UNKNOWN_LAYOUT,
        UNREADABLE
    }

    public static final String REFERENCE_LIBRARY_SHA256 =
            "70eda65f126329c76696c4aaf671288a11d789bb0f97c5c102aaa6fbe7bea3fe";
    public static final String REFERENCE_TEXT_SHA256 =
            "896e8fe0ba01939c1caea2a7dca60c055b3528efbda33a8433aaca1729f4ddc6";

    /**
     * The function signature is a SHA-256 of the executable .text bytes. Its file range is
     * versioned with the profile so a changed library is never selected by its model name.
     */
    public static final Profile REFERENCE = new Profile(
            "hyperos4-touch-report-v1",
            "Xiaomi",
            "haotian",
            "OS4.0",
            REFERENCE_LIBRARY_SHA256,
            REFERENCE_TEXT_SHA256,
            0x120b4L,
            0x5b2a4L,
            TouchReportLayout.GLOBAL_POINTER_RVA,
            TouchReportLayout.STATIC_REPORT_RVA,
            TouchReportLayout.REPORT_SIZE,
            TouchReportLayout.SLOT_COUNT,
            TouchReportLayout.SLOT_STRIDE,
            TouchReportLayout.META_OFFSET,
            true,
            1.15,
            1,
            "reference-device-real-press");

    public static final Profile SAFE_PROBE = new Profile(
            "unknown-safe-probe-v1",
            "",
            "",
            "",
            "",
            "",
            0L,
            0L,
            TouchReportLayout.GLOBAL_POINTER_RVA,
            TouchReportLayout.STATIC_REPORT_RVA,
            TouchReportLayout.REPORT_SIZE,
            TouchReportLayout.SLOT_COUNT,
            TouchReportLayout.SLOT_STRIDE,
            TouchReportLayout.META_OFFSET,
            false,
            0.0,
            0,
            "unverified");

    private static final List<Profile> PROFILES =
            Collections.unmodifiableList(Arrays.asList(REFERENCE));

    private TouchProfileRegistry() {}

    public static Selection resolve(TouchDeviceMapParser.DeviceMap map) {
        String hash = map.libraryHash == null
                ? "" : map.libraryHash.toLowerCase(Locale.ROOT);
        for (Profile profile : PROFILES) {
            if (!profile.librarySha256.isEmpty() && profile.librarySha256.equals(hash)) {
                return new Selection(profile, MatchKind.EXACT_LIBRARY);
            }
        }

        String rom = firstNonBlank(
                map.properties.get("ro.mi.os.version.name"),
                map.properties.get("ro.mi.os.version.incremental"));
        String codeHash = map.codeSignature == null
                ? "" : map.codeSignature.toLowerCase(Locale.ROOT);
        for (Profile profile : PROFILES) {
            if (!profile.codeSha256.isEmpty()
                    && profile.codeSha256.equals(codeHash)
                    && rom.startsWith(profile.romPrefix)) {
                return new Selection(profile, MatchKind.CODE_SIGNATURE_CANDIDATE);
            }
        }
        return new Selection(SAFE_PROBE, MatchKind.NONE);
    }

    public static String deviceProfileKey(TouchDeviceMapParser.DeviceMap map) {
        return value(map.properties.get("ro.product.manufacturer"))
                + "|" + value(map.properties.get("ro.product.model"))
                + "|" + value(firstNonBlank(
                        map.properties.get("ro.mi.os.version.name"),
                        map.properties.get("ro.mi.os.version.incremental")))
                + "|" + value(map.libraryHash);
    }

    public static String deviceBuildLibraryKey(TouchDeviceMapParser.DeviceMap map) {
        return value(map.properties.get("ro.product.device"))
                + "|" + value(map.properties.get("ro.build.fingerprint"))
                + "|" + value(map.libraryHash);
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : (second == null ? "" : second);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    public static final class Profile {
        public final String id;
        public final String manufacturer;
        public final String device;
        public final String romPrefix;
        public final String librarySha256;
        public final String codeSha256;
        public final long textFileOffset;
        public final long textByteCount;
        public final long globalPointerRva;
        public final long expectedReportRva;
        public final int reportSize;
        public final int slotCount;
        public final int slotStride;
        public final int metaOffset;
        public final boolean staticReferenceValidated;
        public final double separationMultiplier;
        public final int minimumSeparationDelta;
        public final String validationStatus;

        Profile(
                String id,
                String manufacturer,
                String device,
                String romPrefix,
                String librarySha256,
                String codeSha256,
                long textFileOffset,
                long textByteCount,
                long globalPointerRva,
                long expectedReportRva,
                int reportSize,
                int slotCount,
                int slotStride,
                int metaOffset,
                boolean staticReferenceValidated,
                double separationMultiplier,
                int minimumSeparationDelta,
                String validationStatus) {
            this.id = id;
            this.manufacturer = manufacturer;
            this.device = device;
            this.romPrefix = romPrefix;
            this.librarySha256 = librarySha256;
            this.codeSha256 = codeSha256;
            this.textFileOffset = textFileOffset;
            this.textByteCount = textByteCount;
            this.globalPointerRva = globalPointerRva;
            this.expectedReportRva = expectedReportRva;
            this.reportSize = reportSize;
            this.slotCount = slotCount;
            this.slotStride = slotStride;
            this.metaOffset = metaOffset;
            this.staticReferenceValidated = staticReferenceValidated;
            this.separationMultiplier = separationMultiplier;
            this.minimumSeparationDelta = minimumSeparationDelta;
            this.validationStatus = validationStatus;
        }
    }

    public static final class Selection {
        public final Profile profile;
        public final MatchKind matchKind;

        Selection(Profile profile, MatchKind matchKind) {
            this.profile = profile;
            this.matchKind = matchKind;
        }

        public boolean isExactLibrary() {
            return matchKind == MatchKind.EXACT_LIBRARY;
        }

        public boolean isCandidate() {
            return matchKind == MatchKind.CODE_SIGNATURE_CANDIDATE;
        }
    }
}

package com.leaf.hyperdragshare.codex.touchprobe;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * HyperOS 4 touch-report layout used by the beta compatibility probe.
 *
 * <p>The RVAs describe the reference Xiaomi 15 Pro library. The probe still records a
 * mismatch so reports from other models can be used to refine this table.</p>
 */
public final class TouchReportLayout {
    public static final String PROFILE = "hyperos4-touch-report-v1";
    public static final String SERVICE_NAME = "vendor.xiaomi.hw.touchfeature-service";
    public static final String LIBRARY_NAME = "libtouchreport_alg.so";
    public static final long GLOBAL_POINTER_RVA = 0x78928L;
    public static final long STATIC_REPORT_RVA = 0xbf7d8L;
    public static final int REPORT_SIZE = 0x900;
    public static final int SLOT_COUNT = 12;
    public static final int SLOT_STRIDE = 0xac;
    public static final int META_OFFSET = 0x8fc;

    public static final int OFFSET_ENABLED = 0x00;
    public static final int OFFSET_STATE = 0x08;
    public static final int OFFSET_ID = 0x0c;
    public static final int OFFSET_X = 0x14;
    public static final int OFFSET_Y = 0x18;
    public static final int OFFSET_CANDIDATE_2C = 0x2c;
    public static final int OFFSET_CANDIDATE_30 = 0x30;
    public static final int OFFSET_CANDIDATE_34 = 0x34;
    public static final int OFFSET_CANDIDATE_80 = 0x80;

    /** Covers every aligned word through the current known output fields. */
    public static final int[] RECORDED_WORD_OFFSETS = {
            0x00, 0x04, 0x08, 0x0c, 0x10, 0x14, 0x18, 0x1c,
            0x20, 0x24, 0x28, 0x2c, 0x30, 0x34, 0x38, 0x3c,
            0x40, 0x44, 0x48, 0x4c, 0x50, 0x54, 0x58, 0x5c,
            0x60, 0x64, 0x68, 0x6c, 0x70, 0x74, 0x78, 0x7c,
            0x80, 0x84, 0x88
    };

    private TouchReportLayout() {}

    public static Frame parseFrame(byte[] report) {
        return parseFrame(report, SLOT_COUNT, SLOT_STRIDE, META_OFFSET);
    }

    public static Frame parseFrame(byte[] report, int slotCount, int slotStride, int metaOffset) {
        return parseFrame(report, report == null ? 0 : report.length,
                slotCount, slotStride, metaOffset);
    }

    public static Frame parseFrame(
            byte[] report,
            int reportSize,
            int slotCount,
            int slotStride,
            int metaOffset) {
        if (report == null || reportSize < 4 || report.length < reportSize) {
            throw new IllegalArgumentException("Touch report is shorter than its profile size");
        }
        if (slotCount <= 0 || slotStride < RECORDED_WORD_OFFSETS[RECORDED_WORD_OFFSETS.length - 1] + 4
                || metaOffset < 0 || metaOffset + 4 > reportSize) {
            throw new IllegalArgumentException("Invalid touch-report layout");
        }
        ByteBuffer buffer = ByteBuffer.wrap(report, 0, reportSize)
                .slice()
                .order(ByteOrder.LITTLE_ENDIAN);
        List<Slot> slots = new ArrayList<>(slotCount);
        for (int index = 0; index < slotCount; index++) {
            int slotBase = index * slotStride;
            if (slotBase + RECORDED_WORD_OFFSETS[RECORDED_WORD_OFFSETS.length - 1] + 4
                    > reportSize) {
                throw new IllegalArgumentException("Touch-report slot exceeds buffer");
            }
            int[] words = new int[RECORDED_WORD_OFFSETS.length];
            for (int word = 0; word < RECORDED_WORD_OFFSETS.length; word++) {
                words[word] = buffer.getInt(slotBase + RECORDED_WORD_OFFSETS[word]);
            }
            slots.add(new Slot(index, words));
        }
        return new Frame(buffer.getInt(metaOffset), Collections.unmodifiableList(slots));
    }

    public static final class Frame {
        public final int metadata;
        public final List<Slot> slots;

        Frame(int metadata, List<Slot> slots) {
            this.metadata = metadata;
            this.slots = slots;
        }

        public int activeSlotCount() {
            int count = 0;
            for (Slot slot : slots) {
                if (slot.isActive()) count++;
            }
            return count;
        }
    }

    public static final class Slot {
        public final int index;
        public final int[] words;

        Slot(int index, int[] words) {
            this.index = index;
            this.words = words;
        }

        public int enabled() {
            return words[wordIndex(OFFSET_ENABLED)];
        }

        public int state() {
            return words[wordIndex(OFFSET_STATE)];
        }

        public int id() {
            return words[wordIndex(OFFSET_ID)];
        }

        public int x() {
            return words[wordIndex(OFFSET_X)];
        }

        public int y() {
            return words[wordIndex(OFFSET_Y)];
        }

        public int field(int offset) {
            return words[wordIndex(offset)];
        }

        public boolean isActive() {
            return enabled() != 0 && (state() == 1 || state() == 2);
        }

        private static int wordIndex(int offset) {
            if ((offset & 3) != 0) {
                throw new IllegalArgumentException("Unaligned touch-report offset: " + offset);
            }
            return offset / 4;
        }
    }
}

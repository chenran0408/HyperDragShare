package com.leaf.hyperdragshare.codex.touchprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TouchReportLayoutTest {
    @Test
    public void parsesKnownSlotFields() {
        byte[] report = new byte[TouchReportLayout.REPORT_SIZE];
        ByteBuffer buffer = ByteBuffer.wrap(report).order(ByteOrder.LITTLE_ENDIAN);
        int slot = 3;
        int base = slot * TouchReportLayout.SLOT_STRIDE;
        buffer.putInt(base + TouchReportLayout.OFFSET_ENABLED, 1);
        buffer.putInt(base + TouchReportLayout.OFFSET_STATE, 2);
        buffer.putInt(base + TouchReportLayout.OFFSET_ID, 7);
        buffer.putInt(base + TouchReportLayout.OFFSET_X, 78_000);
        buffer.putInt(base + TouchReportLayout.OFFSET_Y, 181_000);
        buffer.putInt(base + TouchReportLayout.OFFSET_CANDIDATE_2C, 42);
        buffer.putInt(base + TouchReportLayout.OFFSET_CANDIDATE_30, 5_600);
        buffer.putInt(base + TouchReportLayout.OFFSET_CANDIDATE_34, 13);
        buffer.putInt(base + TouchReportLayout.OFFSET_CANDIDATE_80, 1);
        buffer.putInt(TouchReportLayout.META_OFFSET, 1);

        TouchReportLayout.Frame frame = TouchReportLayout.parseFrame(report);
        TouchReportLayout.Slot parsed = frame.slots.get(slot);

        assertEquals(1, frame.metadata);
        assertEquals(1, frame.activeSlotCount());
        assertTrue(parsed.isActive());
        assertEquals(7, parsed.id());
        assertEquals(78_000, parsed.x());
        assertEquals(181_000, parsed.y());
        assertEquals(42, parsed.field(TouchReportLayout.OFFSET_CANDIDATE_2C));
        assertEquals(5_600, parsed.field(TouchReportLayout.OFFSET_CANDIDATE_30));
        assertEquals(13, parsed.field(TouchReportLayout.OFFSET_CANDIDATE_34));
        assertEquals(1, parsed.field(TouchReportLayout.OFFSET_CANDIDATE_80));
    }

    @Test
    public void liftedAndDisabledSlotsAreInactive() {
        byte[] report = new byte[TouchReportLayout.REPORT_SIZE];
        ByteBuffer buffer = ByteBuffer.wrap(report).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(TouchReportLayout.OFFSET_ENABLED, 1);
        buffer.putInt(TouchReportLayout.OFFSET_STATE, 0);
        buffer.putInt(TouchReportLayout.SLOT_STRIDE + TouchReportLayout.OFFSET_ENABLED, 0);
        buffer.putInt(TouchReportLayout.SLOT_STRIDE + TouchReportLayout.OFFSET_STATE, 2);

        TouchReportLayout.Frame frame = TouchReportLayout.parseFrame(report);

        assertFalse(frame.slots.get(0).isActive());
        assertFalse(frame.slots.get(1).isActive());
        assertEquals(0, frame.activeSlotCount());
    }
}

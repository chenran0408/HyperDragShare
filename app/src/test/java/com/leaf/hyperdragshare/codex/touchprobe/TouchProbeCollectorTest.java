package com.leaf.hyperdragshare.codex.touchprobe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TouchProbeCollectorTest {
    @Test
    public void recordsGroupPeaksAndFieldDistributions() {
        TouchProbeCollector collector = new TouchProbeCollector();

        collector.beginGroup(TouchProbeCollector.Group.FINGERTIP);
        collector.accept(frame(7));
        collector.accept(frame(7));
        collector.endGroup();

        collector.beginGroup(TouchProbeCollector.Group.THUMB);
        collector.accept(frame(9));
        collector.accept(frame(9));
        collector.endGroup();
        collector.beginGroup(TouchProbeCollector.Group.THUMB);
        collector.accept(frame(12));
        collector.accept(frame(12));
        collector.endGroup();

        TouchProbeCollector.Snapshot snapshot = collector.snapshot();
        TouchProbeCollector.GroupStats fingertip =
                snapshot.groups.get(TouchProbeCollector.Group.FINGERTIP);
        TouchProbeCollector.GroupStats thumb =
                snapshot.groups.get(TouchProbeCollector.Group.THUMB);

        assertEquals(6, snapshot.frames);
        assertEquals(6, snapshot.activeFrames);
        assertEquals(6, snapshot.activeSlots);
        assertEquals(1, fingertip.gestures);
        assertEquals(2, thumb.gestures);
        assertEquals(7, snapshot.fingertipPeak());
        assertEquals(9, snapshot.minimumThumbPeak());
        assertTrue(snapshot.hasSeparation());
        TouchProbeCollector.WordStats area =
                snapshot.words[indexOf(TouchReportLayout.OFFSET_CANDIDATE_34)];
        assertEquals(6, area.count);
        assertEquals(7, area.min);
        assertEquals(12, area.max);
        assertEquals(6, snapshot.slotWords[0][indexOf(
                TouchReportLayout.OFFSET_CANDIDATE_34)].count);
    }

    @Test
    public void detectsTheReferenceSeparationRule() {
        TouchProbeCollector collector = new TouchProbeCollector();

        collector.beginGroup(TouchProbeCollector.Group.FINGERTIP);
        collector.accept(frame(8));
        collector.accept(frame(8));
        collector.endGroup();

        collector.beginGroup(TouchProbeCollector.Group.THUMB);
        collector.accept(frame(10));
        collector.accept(frame(10));
        collector.endGroup();

        TouchProbeCollector.Snapshot snapshot = collector.snapshot();
        assertEquals(8, snapshot.fingertipPeak());
        assertEquals(10, snapshot.minimumThumbPeak());
        assertTrue(snapshot.hasSeparation());
    }

    @Test
    public void rejectsInsufficientAreaMargin() {
        TouchProbeCollector collector = new TouchProbeCollector();
        collector.beginGroup(TouchProbeCollector.Group.FINGERTIP);
        collector.accept(frame(8));
        collector.accept(frame(8));
        collector.endGroup();
        collector.beginGroup(TouchProbeCollector.Group.THUMB);
        collector.accept(frame(9));
        collector.accept(frame(9));
        collector.endGroup();

        assertFalse(collector.snapshot().hasSeparation());
    }

    @Test
    public void invalidSamplesAreRetainedForDiagnosticsButExcludedFromStats() {
        TouchProbeCollector collector = new TouchProbeCollector();

        collector.beginGroup(TouchProbeCollector.Group.FINGERTIP);
        TouchProbeCollector.GestureSample noFrames = collector.endGroup();

        collector.beginGroup(TouchProbeCollector.Group.THUMB);
        collector.accept(frame(12));
        TouchProbeCollector.GestureSample tooShort = collector.endGroup();

        TouchProbeCollector.Snapshot snapshot = collector.snapshot();
        assertEquals("未收到采样帧", noFrames.invalidReason);
        assertEquals("有效单触点样本不足", tooShort.invalidReason);
        assertEquals(2, snapshot.gestureSamples.size());
        assertEquals(0, snapshot.groups.get(TouchProbeCollector.Group.FINGERTIP).gestures);
        assertEquals(0, snapshot.groups.get(TouchProbeCollector.Group.THUMB).gestures);
        assertEquals(0, snapshot.minimumThumbPeak());
    }

    @Test
    public void rejectsMultiTouchSamples() {
        TouchProbeCollector collector = new TouchProbeCollector();
        collector.beginGroup(TouchProbeCollector.Group.THUMB);
        collector.accept(frame(12, 13));
        collector.accept(frame(12, 13));
        TouchProbeCollector.GestureSample sample = collector.endGroup();

        assertFalse(sample.valid);
        assertEquals("检测到多触点", sample.invalidReason);
        assertEquals(2, sample.multiContactFrames);
        assertEquals(2, sample.maxContacts);
        assertEquals(0, collector.snapshot().groups.get(TouchProbeCollector.Group.THUMB).gestures);
    }

    @Test
    public void undoRebuildsStatsFromRemainingSamples() {
        TouchProbeCollector collector = new TouchProbeCollector();
        collector.beginGroup(TouchProbeCollector.Group.FINGERTIP);
        collector.accept(frame(8));
        collector.accept(frame(8));
        collector.endGroup();
        collector.beginGroup(TouchProbeCollector.Group.THUMB);
        collector.accept(frame(16));
        collector.accept(frame(16));
        collector.endGroup();

        TouchProbeCollector.GestureSample removed = collector.undoLastSample();
        TouchProbeCollector.Snapshot snapshot = collector.snapshot();

        assertEquals(TouchProbeCollector.Group.THUMB, removed.group);
        assertEquals(1, snapshot.gestureSamples.size());
        assertEquals(1, snapshot.groups.get(TouchProbeCollector.Group.FINGERTIP).gestures);
        assertEquals(0, snapshot.groups.get(TouchProbeCollector.Group.THUMB).gestures);
        assertEquals(0, snapshot.minimumThumbPeak());
        assertTrue(snapshot.events.get(snapshot.events.size() - 1).contains("sample removed"));
    }

    @Test
    public void clearRemovesSamplesAndAggregatedData() {
        TouchProbeCollector collector = new TouchProbeCollector();
        collector.beginGroup(TouchProbeCollector.Group.FINGERTIP);
        collector.accept(frame(8));
        collector.accept(frame(8));
        collector.endGroup();

        collector.clearSamples();
        TouchProbeCollector.Snapshot snapshot = collector.snapshot();

        assertEquals(0, snapshot.frames);
        assertEquals(0, snapshot.activeFrames);
        assertTrue(snapshot.gestureSamples.isEmpty());
        assertEquals(0, snapshot.words[0].count);
        assertEquals(1, snapshot.events.size());
        assertEquals("samples cleared", snapshot.events.get(0));
    }

    @Test
    public void distinguishesNoHardwareContactFromNoFrames() {
        TouchProbeCollector collector = new TouchProbeCollector();
        collector.beginGroup(TouchProbeCollector.Group.FINGERTIP);
        collector.accept(frame());
        collector.accept(frame());
        TouchProbeCollector.GestureSample sample = collector.endGroup();

        assertFalse(sample.valid);
        assertEquals("没有检测到硬件单触点", sample.invalidReason);
        assertEquals(2, sample.observationFrames);
        assertEquals(0, sample.singleContactFrames);
    }

    @Test
    public void rejectsActiveFramesWhoseCandidateFieldsAreAllZero() {
        TouchProbeCollector collector = new TouchProbeCollector();
        collector.beginGroup(TouchProbeCollector.Group.THUMB);
        collector.accept(frame(0));
        collector.accept(frame(0));
        TouchProbeCollector.GestureSample sample = collector.endGroup();

        assertFalse(sample.valid);
        assertEquals("触控字段全为零", sample.invalidReason);
    }

    @Test
    public void consecutiveSameGroupSamplesRemainAcceptedWithoutRearming() {
        TouchProbeCollector collector = new TouchProbeCollector();
        for (int sample = 1; sample <= 3; sample++) {
            collector.beginGroup(TouchProbeCollector.Group.THUMB);
            collector.accept(frame(10 + sample));
            collector.accept(frame(10 + sample));
            assertTrue(collector.endGroup().valid);
        }

        TouchProbeCollector.Snapshot snapshot = collector.snapshot();
        assertEquals(3, snapshot.gestureSamples.size());
        assertEquals(3, snapshot.groups.get(TouchProbeCollector.Group.THUMB).gestures);
    }

    @Test
    public void interruptedServiceReadCannotBecomeAValidSample() {
        TouchProbeCollector collector = new TouchProbeCollector();
        collector.beginGroup(TouchProbeCollector.Group.FINGERTIP);
        collector.accept(frame(8));
        collector.accept(frame(8));
        collector.accept(frame(8));

        TouchProbeCollector.GestureSample cancelled =
                collector.cancelActiveGroup("触控读取中断，本次按压未计入");

        assertFalse(cancelled.valid);
        assertEquals("触控读取中断，本次按压未计入", cancelled.invalidReason);
        assertEquals(0, collector.snapshot().groups
                .get(TouchProbeCollector.Group.FINGERTIP).gestures);
    }

    private static TouchReportLayout.Frame frame(int area) {
        return frame(new int[]{area});
    }

    private static TouchReportLayout.Frame frame(int... areas) {
        byte[] report = new byte[TouchReportLayout.REPORT_SIZE];
        ByteBuffer buffer = ByteBuffer.wrap(report).order(ByteOrder.LITTLE_ENDIAN);
        for (int index = 0; index < areas.length; index++) {
            int slotBase = index * TouchReportLayout.SLOT_STRIDE;
            buffer.putInt(slotBase + TouchReportLayout.OFFSET_ENABLED, 1);
            buffer.putInt(slotBase + TouchReportLayout.OFFSET_STATE, 2);
            buffer.putInt(slotBase + TouchReportLayout.OFFSET_ID, index);
            buffer.putInt(slotBase + TouchReportLayout.OFFSET_X, 80_000 + index);
            buffer.putInt(slotBase + TouchReportLayout.OFFSET_Y, 180_000 + index);
            buffer.putInt(slotBase + TouchReportLayout.OFFSET_CANDIDATE_34, areas[index]);
        }
        buffer.putInt(TouchReportLayout.META_OFFSET, 1);
        return TouchReportLayout.parseFrame(report);
    }

    private static int indexOf(int offset) {
        int[] offsets = TouchReportLayout.RECORDED_WORD_OFFSETS;
        for (int index = 0; index < offsets.length; index++) {
            if (offsets[index] == offset) return index;
        }
        throw new IllegalArgumentException("Unrecorded offset");
    }
}

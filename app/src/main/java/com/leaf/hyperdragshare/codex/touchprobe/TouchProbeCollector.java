package com.leaf.hyperdragshare.codex.touchprobe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Accumulates report fields without retaining screen content or pointer coordinates from apps. */
public final class TouchProbeCollector {
    private static final int[] CANDIDATE_OFFSETS = {
            TouchReportLayout.OFFSET_CANDIDATE_2C,
            TouchReportLayout.OFFSET_CANDIDATE_30,
            TouchReportLayout.OFFSET_CANDIDATE_34,
            TouchReportLayout.OFFSET_CANDIDATE_80,
    };

    public enum Group {
        FINGERTIP("指尖"),
        THUMB("拇指指腹");

        public final String label;

        Group(String label) {
            this.label = label;
        }
    }

    private final WordStats[] words = new WordStats[TouchReportLayout.RECORDED_WORD_OFFSETS.length];
    private final WordStats[][] slotWords =
            new WordStats[TouchReportLayout.SLOT_COUNT]
                    [TouchReportLayout.RECORDED_WORD_OFFSETS.length];
    private final WordStats[] quietWords;
    private final long[] states = new long[4];
    private final Map<Group, GroupStats> groups = new LinkedHashMap<>();
    private final List<GestureSample> gestureSamples = new ArrayList<>();
    private final Deque<String> events = new ArrayDeque<>();
    private long frames;
    private long activeFrames;
    private long activeSlots;
    private long metadataChanges;
    private int lastMetadata;
    private long invalidEnabledValues;
    private long invalidStateValues;
    private long invalidCoordinateValues;
    private int latestActiveCount;
    private int latestMeta;
    private String latestActiveSlots = "";
    private Group activeGroup;
    private long activeGroupStartedAt;
    private int activeGroupObservationFrames;
    private int activeGroupSingleContactFrames;
    private int activeGroupMultiContactFrames;
    private int activeGroupMaxContacts;
    private int activeGroupPeak2C;
    private int activeGroupPeak30;
    private int activeGroupPeak34;
    private int activeGroupPeak80;

    public TouchProbeCollector() {
        for (int index = 0; index < words.length; index++) {
            words[index] = new WordStats(TouchReportLayout.RECORDED_WORD_OFFSETS[index]);
        }
        for (int slot = 0; slot < slotWords.length; slot++) {
            for (int word = 0; word < slotWords[slot].length; word++) {
                slotWords[slot][word] =
                        new WordStats(TouchReportLayout.RECORDED_WORD_OFFSETS[word]);
            }
        }
        quietWords = new WordStats[CANDIDATE_OFFSETS.length];
        for (int index = 0; index < CANDIDATE_OFFSETS.length; index++) {
            quietWords[index] = new WordStats(CANDIDATE_OFFSETS[index]);
        }
        groups.put(Group.FINGERTIP, new GroupStats(Group.FINGERTIP));
        groups.put(Group.THUMB, new GroupStats(Group.THUMB));
    }

    public synchronized void reset() {
        frames = 0;
        activeFrames = 0;
        activeSlots = 0;
        metadataChanges = 0;
        lastMetadata = 0;
        invalidEnabledValues = 0;
        invalidStateValues = 0;
        invalidCoordinateValues = 0;
        latestActiveCount = 0;
        latestMeta = 0;
        latestActiveSlots = "";
        activeGroup = null;
        resetActiveGroupCounters();
        for (WordStats stats : words) stats.reset();
        for (WordStats[] slot : slotWords) {
            for (WordStats stats : slot) stats.reset();
        }
        for (WordStats stats : quietWords) stats.reset();
        java.util.Arrays.fill(states, 0L);
        for (GroupStats stats : groups.values()) stats.reset();
        gestureSamples.clear();
        events.clear();
    }

    public synchronized void accept(TouchReportLayout.Frame frame) {
        if (frame == null) return;
        frames++;
        if (frames == 1) {
            lastMetadata = frame.metadata;
        } else if (lastMetadata != frame.metadata) {
            metadataChanges++;
            lastMetadata = frame.metadata;
        }
        latestMeta = frame.metadata;
        int active = 0;
        StringBuilder latest = new StringBuilder();
        for (TouchReportLayout.Slot slot : frame.slots) {
            int state = slot.state();
            int enabled = slot.enabled();
            if (enabled != 0 && enabled != 1) invalidEnabledValues++;
            if (state < 0 || state > 2) invalidStateValues++;
            if (state >= 0 && state < states.length) states[state]++;
            if (!slot.isActive()) {
                for (int index = 0; index < quietWords.length; index++) {
                    quietWords[index].accept(slot.field(CANDIDATE_OFFSETS[index]));
                }
                continue;
            }
            active++;
            if (Math.abs((long) slot.x()) > 10_000_000L
                    || Math.abs((long) slot.y()) > 10_000_000L) {
                invalidCoordinateValues++;
            }
            activeSlots++;
            for (int index = 0; index < words.length; index++) {
                words[index].accept(slot.words[index]);
                if (slot.index >= 0 && slot.index < slotWords.length) {
                    slotWords[slot.index][index].accept(slot.words[index]);
                }
            }
            int peak = slot.field(TouchReportLayout.OFFSET_CANDIDATE_34);
            if (latest.length() > 0) latest.append("; ");
            latest.append("s").append(slot.index)
                    .append(" id=").append(slot.id())
                    .append(" st=").append(state)
                    .append(" x=").append(slot.x())
                    .append(" y=").append(slot.y())
                    .append(" 2c=").append(slot.field(TouchReportLayout.OFFSET_CANDIDATE_2C))
                    .append(" 30=").append(slot.field(TouchReportLayout.OFFSET_CANDIDATE_30))
                    .append(" 34=").append(peak)
                    .append(" 80=").append(slot.field(TouchReportLayout.OFFSET_CANDIDATE_80));
        }
        latestActiveCount = active;
        latestActiveSlots = latest.toString();
        if (active > 0) activeFrames++;
        if (activeGroup != null) {
            activeGroupObservationFrames++;
            activeGroupMaxContacts = Math.max(activeGroupMaxContacts, active);
            if (active == 1) {
                activeGroupSingleContactFrames++;
                TouchReportLayout.Slot contact = firstActive(frame);
                if (contact != null) {
                    activeGroupPeak2C = Math.max(activeGroupPeak2C,
                            contact.field(TouchReportLayout.OFFSET_CANDIDATE_2C));
                    activeGroupPeak30 = Math.max(activeGroupPeak30,
                            contact.field(TouchReportLayout.OFFSET_CANDIDATE_30));
                    activeGroupPeak34 = Math.max(activeGroupPeak34,
                            contact.field(TouchReportLayout.OFFSET_CANDIDATE_34));
                    activeGroupPeak80 = Math.max(activeGroupPeak80,
                            contact.field(TouchReportLayout.OFFSET_CANDIDATE_80));
                    groups.get(activeGroup).acceptPeak(activeGroupPeak34);
                }
            } else if (active > 1) {
                activeGroupMultiContactFrames++;
            }
        }
    }

    public synchronized void beginGroup(Group group) {
        if (group == null || activeGroup == group) return;
        if (activeGroup != null) endGroup();
        activeGroup = group;
        resetActiveGroupCounters();
        activeGroupStartedAt = System.currentTimeMillis();
        addEvent("gesture start group=" + group.name());
    }

    public synchronized GestureSample endGroup() {
        if (activeGroup == null) return null;
        GroupStats group = groups.get(activeGroup);
        long endedAt = System.currentTimeMillis();
        String invalidReason = "";
        if (activeGroupObservationFrames == 0) {
            invalidReason = "未收到采样帧";
        } else if (activeGroupMaxContacts > 1) {
            invalidReason = "检测到多触点";
        } else if (activeGroupSingleContactFrames < 2) {
            invalidReason = activeGroupMaxContacts == 0
                    ? "没有检测到硬件单触点"
                    : "有效单触点样本不足";
        } else if (activeGroupPeak2C == 0 && activeGroupPeak30 == 0
                && activeGroupPeak34 == 0 && activeGroupPeak80 == 0) {
            invalidReason = "触控字段全为零";
        }
        boolean valid = invalidReason.isEmpty();
        GestureSample sample = new GestureSample(
                activeGroup,
                activeGroupStartedAt,
                endedAt,
                activeGroupObservationFrames,
                activeGroupSingleContactFrames,
                activeGroupMultiContactFrames,
                activeGroupMaxContacts,
                activeGroupPeak2C,
                activeGroupPeak30,
                activeGroupPeak34,
                activeGroupPeak80,
                valid,
                invalidReason);
        gestureSamples.add(sample);
        if (valid) group.finishGesture(activeGroupPeak34, activeGroupSingleContactFrames);
        addEvent("gesture end group=" + activeGroup.name() + " valid=" + valid
                + " peak34=" + activeGroupPeak34 + " single_frames=" + activeGroupSingleContactFrames
                + " max_contacts=" + activeGroupMaxContacts
                + (valid ? "" : " reason=" + invalidReason));
        activeGroup = null;
        resetActiveGroupCounters();
        return sample;
    }

    /** Drops an interrupted press so a service restart can never turn it into a valid sample. */
    public synchronized GestureSample cancelActiveGroup(String reason) {
        if (activeGroup == null) return null;
        GestureSample sample = new GestureSample(
                activeGroup,
                activeGroupStartedAt,
                System.currentTimeMillis(),
                activeGroupObservationFrames,
                activeGroupSingleContactFrames,
                activeGroupMultiContactFrames,
                activeGroupMaxContacts,
                activeGroupPeak2C,
                activeGroupPeak30,
                activeGroupPeak34,
                activeGroupPeak80,
                false,
                reason == null || reason.isBlank() ? "读取中断" : reason);
        gestureSamples.add(sample);
        addEvent("gesture cancelled group=" + activeGroup.name()
                + " reason=" + sample.invalidReason);
        activeGroup = null;
        resetActiveGroupCounters();
        return sample;
    }

    public synchronized GestureSample undoLastSample() {
        if (gestureSamples.isEmpty()) return null;
        GestureSample removed = gestureSamples.remove(gestureSamples.size() - 1);
        rebuildGroupStats();
        addEvent("sample removed group=" + removed.group.name() + " valid=" + removed.valid);
        return removed;
    }

    public synchronized void clearSamples() {
        reset();
        addEvent("samples cleared");
    }

    public synchronized void addEvent(String event) {
        while (events.size() >= 40) events.removeFirst();
        events.addLast(event == null ? "" : event);
    }

    public synchronized Snapshot snapshot() {
        WordStats[] wordSnapshot = new WordStats[words.length];
        for (int index = 0; index < words.length; index++) wordSnapshot[index] = words[index].copy();
        WordStats[][] slotWordSnapshot = new WordStats[slotWords.length][];
        for (int slot = 0; slot < slotWords.length; slot++) {
            slotWordSnapshot[slot] = new WordStats[slotWords[slot].length];
            for (int word = 0; word < slotWords[slot].length; word++) {
                slotWordSnapshot[slot][word] = slotWords[slot][word].copy();
            }
        }
        Map<Group, GroupStats> groupSnapshot = new LinkedHashMap<>();
        for (Map.Entry<Group, GroupStats> entry : groups.entrySet()) {
            groupSnapshot.put(entry.getKey(), entry.getValue().copy());
        }
        WordStats[] quietSnapshot = new WordStats[quietWords.length];
        for (int index = 0; index < quietWords.length; index++) {
            quietSnapshot[index] = quietWords[index].copy();
        }
        return new Snapshot(frames, activeFrames, activeSlots, metadataChanges,
                invalidEnabledValues, invalidStateValues, invalidCoordinateValues,
                latestActiveCount, latestMeta, latestActiveSlots,
                wordSnapshot, slotWordSnapshot, quietSnapshot, states.clone(), groupSnapshot,
                new ArrayList<>(gestureSamples), new ArrayList<>(events), activeGroup);
    }

    private void rebuildGroupStats() {
        for (GroupStats stats : groups.values()) stats.reset();
        for (GestureSample sample : gestureSamples) {
            if (sample.valid) {
                GroupStats stats = groups.get(sample.group);
                for (int frame = 0; frame < sample.singleContactFrames; frame++) {
                    stats.acceptPeak(sample.peak34);
                }
                stats.finishGesture(sample.peak34, sample.singleContactFrames);
            }
        }
    }

    private void resetActiveGroupCounters() {
        activeGroupStartedAt = 0L;
        activeGroupObservationFrames = 0;
        activeGroupSingleContactFrames = 0;
        activeGroupMultiContactFrames = 0;
        activeGroupMaxContacts = 0;
        activeGroupPeak2C = 0;
        activeGroupPeak30 = 0;
        activeGroupPeak34 = 0;
        activeGroupPeak80 = 0;
    }

    private static TouchReportLayout.Slot firstActive(TouchReportLayout.Frame frame) {
        for (TouchReportLayout.Slot slot : frame.slots) {
            if (slot.isActive()) return slot;
        }
        return null;
    }

    public static final class WordStats {
        public final int offset;
        public long count;
        public int min;
        public int max;
        public long zeros;
        public long negatives;
        public double sum;

        WordStats(int offset) {
            this.offset = offset;
            reset();
        }

        void accept(int value) {
            if (count == 0) {
                min = value;
                max = value;
            } else {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            if (value == 0) zeros++;
            if (value < 0) negatives++;
            count++;
            sum += value;
        }

        void reset() {
            count = 0;
            min = 0;
            max = 0;
            zeros = 0;
            negatives = 0;
            sum = 0.0;
        }

        WordStats copy() {
            WordStats copy = new WordStats(offset);
            copy.count = count;
            copy.min = min;
            copy.max = max;
            copy.zeros = zeros;
            copy.negatives = negatives;
            copy.sum = sum;
            return copy;
        }

        public double average() {
            return count == 0 ? 0.0 : sum / count;
        }
    }

    public static final class GroupStats {
        public final Group group;
        public int gestures;
        public int sampleFrames;
        public int minPeak;
        public int maxPeak;
        public final List<Integer> gesturePeaks = new ArrayList<>();

        GroupStats(Group group) {
            this.group = group;
            reset();
        }

        void acceptPeak(int peak) {
            if (sampleFrames == 0) minPeak = maxPeak = peak;
            else {
                minPeak = Math.min(minPeak, peak);
                maxPeak = Math.max(maxPeak, peak);
            }
            sampleFrames++;
        }

        void finishGesture(int peak, int samples) {
            if (samples <= 0) return;
            gestures++;
            gesturePeaks.add(peak);
        }

        void reset() {
            gestures = 0;
            sampleFrames = 0;
            minPeak = 0;
            maxPeak = 0;
            gesturePeaks.clear();
        }

        GroupStats copy() {
            GroupStats copy = new GroupStats(group);
            copy.gestures = gestures;
            copy.sampleFrames = sampleFrames;
            copy.minPeak = minPeak;
            copy.maxPeak = maxPeak;
            copy.gesturePeaks.addAll(gesturePeaks);
            return copy;
        }
    }

    public static final class Snapshot {
        public final long frames;
        public final long activeFrames;
        public final long activeSlots;
        public final long metadataChanges;
        public final long invalidEnabledValues;
        public final long invalidStateValues;
        public final long invalidCoordinateValues;
        public final int latestActiveCount;
        public final int latestMeta;
        public final String latestActiveSlots;
        public final WordStats[] words;
        public final WordStats[][] slotWords;
        public final WordStats[] quietWords;
        public final long[] states;
        public final Map<Group, GroupStats> groups;
        public final List<GestureSample> gestureSamples;
        public final List<String> events;
        public final Group activeGroup;

        Snapshot(
                long frames,
                long activeFrames,
                long activeSlots,
                long metadataChanges,
                long invalidEnabledValues,
                long invalidStateValues,
                long invalidCoordinateValues,
                int latestActiveCount,
                int latestMeta,
                String latestActiveSlots,
                WordStats[] words,
                WordStats[][] slotWords,
                WordStats[] quietWords,
                long[] states,
                Map<Group, GroupStats> groups,
                List<GestureSample> gestureSamples,
                List<String> events,
                Group activeGroup) {
            this.frames = frames;
            this.activeFrames = activeFrames;
            this.activeSlots = activeSlots;
            this.metadataChanges = metadataChanges;
            this.invalidEnabledValues = invalidEnabledValues;
            this.invalidStateValues = invalidStateValues;
            this.invalidCoordinateValues = invalidCoordinateValues;
            this.latestActiveCount = latestActiveCount;
            this.latestMeta = latestMeta;
            this.latestActiveSlots = latestActiveSlots;
            this.words = words;
            this.slotWords = slotWords;
            this.quietWords = quietWords;
            this.states = states;
            this.groups = Collections.unmodifiableMap(groups);
            this.gestureSamples = Collections.unmodifiableList(gestureSamples);
            this.events = Collections.unmodifiableList(events);
            this.activeGroup = activeGroup;
        }

        public int fingertipPeak() {
            GroupStats stats = groups.get(Group.FINGERTIP);
            return stats == null || stats.gesturePeaks.isEmpty() ? 0
                    : Collections.max(stats.gesturePeaks);
        }

        public int minimumThumbPeak() {
            GroupStats stats = groups.get(Group.THUMB);
            return stats == null || stats.gesturePeaks.isEmpty() ? 0
                    : Collections.min(stats.gesturePeaks);
        }

        public boolean hasSeparation() {
            int fingertip = fingertipPeak();
            int thumb = minimumThumbPeak();
            return fingertip > 0 && thumb > 0
                    && thumb >= fingertip * 1.15f
                    && thumb - fingertip >= 1;
        }
    }

    public static final class GestureSample {
        public final Group group;
        public final long startedAt;
        public final long endedAt;
        public final int observationFrames;
        public final int singleContactFrames;
        public final int multiContactFrames;
        public final int maxContacts;
        public final int peak2C;
        public final int peak30;
        public final int peak34;
        public final int peak80;
        public final boolean valid;
        public final String invalidReason;

        GestureSample(
                Group group,
                long startedAt,
                long endedAt,
                int observationFrames,
                int singleContactFrames,
                int multiContactFrames,
                int maxContacts,
                int peak2C,
                int peak30,
                int peak34,
                int peak80,
                boolean valid,
                String invalidReason) {
            this.group = group;
            this.startedAt = startedAt;
            this.endedAt = endedAt;
            this.observationFrames = observationFrames;
            this.singleContactFrames = singleContactFrames;
            this.multiContactFrames = multiContactFrames;
            this.maxContacts = maxContacts;
            this.peak2C = peak2C;
            this.peak30 = peak30;
            this.peak34 = peak34;
            this.peak80 = peak80;
            this.valid = valid;
            this.invalidReason = invalidReason;
        }

        public long durationMs() {
            return Math.max(0L, endedAt - startedAt);
        }
    }
}

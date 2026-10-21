package com.leaf.hyperdragshare.codex;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import java.util.Arrays;

/** Exercises production insertion with native segmentation and generated text only. */
public final class TextInsertionProbe extends Instrumentation {
    private static final String ORIGINAL = "甲😀乙";

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle report = new Bundle();
        int result = Activity.RESULT_CANCELED;
        try {
            require(Looper.myLooper() != Looper.getMainLooper(), "Native insertion must run off the main thread");
            checkInsertion(getTargetContext(), "上方 123", "下方456", "bothSides", report);
            checkInsertion(getTargetContext(), "上方 123", "", "prependOnly", report);
            checkInsertion(getTargetContext(), "", "下方456", "appendOnly", report);
            result = Activity.RESULT_OK;
        } catch (RuntimeException | UnsatisfiedLinkError error) {
            report.putString("error", error.toString());
        }
        finish(result, report);
    }

    private static void checkInsertion(Context context, String before, String after, String name, Bundle report) {
        // UTF-16: the first chip contains 甲 plus the complete emoji; ends in segments are inclusive.
        int[] originalSegments = {0, 2, 3, 3, -1};
        int[] unchangedSegments = originalSegments.clone();
        int[][] originalSelection = {{0, 3}}; // Selection ends are exclusive.
        Bundle state = new Bundle();
        state.putString("saved_text", ORIGINAL);
        state.putIntArray("saved_segment", originalSegments);
        state.putSerializable("selected_state", originalSelection);

        BigBangPanelSession.Companion.insertIntoSavedState(context, state, before, after);

        String prefix = before.isEmpty() ? "" : before + "\n";
        String suffix = after.isEmpty() ? "" : "\n" + after;
        String expected = prefix + ORIGINAL + suffix;
        require(expected.equals(state.getString("saved_text")), name + ": inserted text order changed");
        int offset = prefix.length();
        int[] segments = state.getIntArray("saved_segment");
        require(hasWordPair(segments, offset, offset + 2), name + ": merged emoji chip was resegmented");
        require(hasWordPair(segments, offset + 3, offset + 3), name + ": final original chip changed");
        int originalWordCount = 0;
        for (int index = 0; segments[index] != -1; index += 2) {
            if (segments[index] >= offset && segments[index] < offset + ORIGINAL.length()) originalWordCount++;
        }
        require(originalWordCount == 2, name + ": original chips gained extra boundaries");
        int[][] shifted = state.getSerializable("selected_state", int[][].class);
        require(shifted.length == 1 && shifted[0][0] == offset && shifted[0][1] == offset + 3,
                name + ": selection did not follow the UTF-16 prefix length");
        require(Arrays.equals(originalSegments, unchangedSegments), name + ": input segments were mutated");
        require(originalSelection[0][0] == 0 && originalSelection[0][1] == 3,
                name + ": input selection was mutated");
        report.putBoolean(name, true);
        report.putInt(name + "Utf16Length", expected.length());
    }

    private static boolean hasWordPair(int[] segments, int start, int end) {
        for (int index = 0; segments[index] != -1; index += 2) {
            if (segments[index] == start && segments[index + 1] == end) return true;
        }
        return false;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}

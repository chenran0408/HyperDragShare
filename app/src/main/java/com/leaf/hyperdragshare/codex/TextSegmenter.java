package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.icu.text.BreakIterator;

import java.util.ArrayList;
import java.util.Locale;

/** Uses Android ICU word boundaries for the text-segmentation screen. */
final class TextSegmenter {
    private static final TextSegmenter INSTANCE = new TextSegmenter();

    private TextSegmenter() {}

    static TextSegmenter get(Context context) {
        return INSTANCE;
    }

    int[] segment(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        BreakIterator wordBreaks = BreakIterator.getWordInstance(Locale.ROOT);
        wordBreaks.setText(text);
        ArrayList<Integer> wordRanges = new ArrayList<>();
        ArrayList<Integer> punctuations = new ArrayList<>();
        BreakIterator characters = BreakIterator.getCharacterInstance(Locale.ROOT);
        characters.setText(text);
        int cursor = 0;
        for (int boundaryStart = wordBreaks.first(), boundaryEnd = wordBreaks.next();
                boundaryEnd != BreakIterator.DONE;
                boundaryStart = boundaryEnd, boundaryEnd = wordBreaks.next()) {
            int start = boundaryStart;
            int endExclusive = boundaryEnd;
            if (!characters.isBoundary(start)) start = characters.preceding(start);
            if (!characters.isBoundary(endExclusive)) endExclusive = characters.following(endExclusive);
            start = Math.max(start, cursor);
            if (endExclusive <= start) continue;
            appendPunctuation(text, cursor, start, punctuations, characters);
            if (hasWordCodePoint(text, start, endExclusive)) {
                wordRanges.add(start);
                wordRanges.add(endExclusive - 1);
            } else {
                appendPunctuation(text, start, endExclusive, punctuations, characters);
            }
            cursor = endExclusive;
        }
        appendPunctuation(text, cursor, text.length(), punctuations, characters);
        if (wordRanges.isEmpty() && punctuations.isEmpty()) {
            return null;
        }
        int[] result = new int[wordRanges.size() + punctuations.size() + 1];
        int outputIndex = 0;
        for (Integer value : wordRanges) result[outputIndex++] = value;
        result[outputIndex++] = -1;
        for (Integer value : punctuations) result[outputIndex++] = value;
        return result;
    }

    private static void appendPunctuation(
            String text, int start, int endExclusive, ArrayList<Integer> out, BreakIterator characters) {
        for (int index = start; index < endExclusive;) {
            int codePoint = text.codePointAt(index);
            int next = characters.following(index);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                out.add(index);
                out.add(next - 1);
            }
            index = next;
        }
    }

    private static boolean hasWordCodePoint(String text, int start, int endExclusive) {
        for (int index = start; index < endExclusive;) {
            int codePoint = text.codePointAt(index);
            if (Character.isLetterOrDigit(codePoint) || codePoint == '_') {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }
}

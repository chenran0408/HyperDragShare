package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.icu.text.BreakIterator;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public final class TextSegmenterTest {
    private final TextSegmenter segmenter = TextSegmenter.get(new android.app.Application());

    @Test
    public void icuBoundariesKeepCombiningAndEmojiGraphemesWhole() {
        for (String glyph : new String[]{"e\u0301", "\uD83D\uDC4D\uD83C\uDFFD",
                "\uD83C\uDDE8\uD83C\uDDF3", "\u2764\uFE0F",
                "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"}) {
            int[] result = segmenter.segment(glyph);
            BreakIterator characters = BreakIterator.getCharacterInstance(Locale.ROOT);
            characters.setText(glyph);
            int separator = separatorIndex(result);
            for (int i = 0; i < separator; i += 2) {
                assertTrue(characters.isBoundary(result[i]));
                assertTrue(characters.isBoundary(result[i + 1] + 1));
            }
            for (int i = separator + 1; i < result.length; i += 2) {
                assertTrue(characters.isBoundary(result[i]));
                assertTrue(characters.isBoundary(result[i + 1] + 1));
            }
            StringBuilder joined = appendRanges(glyph, result);
            assertEquals(glyph, joined.toString());
        }
    }

    @Test
    public void mixedScriptsKeepSelectableSourceTextAndWhitespaceOffsets() {
        String text = "你好 hello世界 مرحبا 日本語ไทย e\u0301👍🏽!\n";
        int[] result = segmenter.segment(text);
        ArrayList<int[]> ranges = rangesBySourceOrder(result);

        StringBuilder selected = new StringBuilder();
        int previousEnd = 0;
        BreakIterator characters = BreakIterator.getCharacterInstance(Locale.ROOT);
        characters.setText(text);
        for (int[] range : ranges) {
            assertTrue(range[0] >= previousEnd);
            assertTrue(characters.isBoundary(range[0]));
            assertTrue(characters.isBoundary(range[1]));
            selected.append(text, range[0], range[1]);
            previousEnd = range[1];
        }
        assertEquals("你好hello世界مرحبا日本語ไทยe\u0301👍🏽!", selected.toString());
        for (int whitespace : new int[]{2, 10, 16, 23, 31}) {
            assertTrue(Character.isWhitespace(text.charAt(whitespace)));
            assertTrue(ranges.stream().noneMatch(range ->
                    range[0] <= whitespace && whitespace < range[1]));
        }
    }

    @Test
    public void icuFindsMultipleChineseAndThaiWordBoundaries() {
        assertTrue(wordCount("我喜欢苹果") > 1);
        assertTrue(wordCount("ฉันชอบภาษาไทย") > 1);
    }

    @Test
    public void emptyInputDoesNotProduceAnInvalidBigBangSegment() {
        assertNull(segmenter.segment(""));
        assertNull(segmenter.segment(null));
    }

    private int wordCount(String text) {
        return separatorIndex(segmenter.segment(text)) / 2;
    }

    private static int separatorIndex(int[] result) {
        for (int i = 0; i < result.length; i++) {
            if (result[i] == -1) return i;
        }
        throw new AssertionError("Missing word/punctuation separator");
    }

    private static ArrayList<int[]> rangesBySourceOrder(int[] result) {
        int separator = separatorIndex(result);
        ArrayList<int[]> ranges = new ArrayList<>();
        for (int i = 0; i < separator; i += 2) {
            ranges.add(new int[]{result[i], result[i + 1] + 1});
        }
        for (int i = separator + 1; i < result.length; i += 2) {
            ranges.add(new int[]{result[i], result[i + 1] + 1});
        }
        ranges.sort(Comparator.comparingInt(range -> range[0]));
        return ranges;
    }

    private static StringBuilder appendRanges(String text, int[] result) {
        StringBuilder joined = new StringBuilder();
        for (int[] range : rangesBySourceOrder(result)) {
            joined.append(text, range[0], range[1]);
        }
        return joined;
    }
}

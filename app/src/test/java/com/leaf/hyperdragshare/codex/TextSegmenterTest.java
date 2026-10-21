package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public final class TextSegmenterTest {
    @Test
    public void nativeBoundariesInsideGraphemesNeverCreateFragments() {
        for (String glyph : new String[]{"e\u0301", "\uD83D\uDC4D\uD83C\uDFFD",
                "\uD83C\uDDE8\uD83C\uDDF3", "\u2764\uFE0F",
                "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"}) {
            int[] nativeRanges = new int[glyph.length() * 2];
            for (int i = 0; i < glyph.length(); i++) {
                nativeRanges[i * 2] = i;
                nativeRanges[i * 2 + 1] = i + 1;
            }
            int[] expected = glyph.startsWith("e")
                    ? new int[]{0, glyph.length() - 1, -1}
                    : new int[]{-1, 0, glyph.length() - 1};
            assertArrayEquals(glyph, expected, TextSegmenter.buildSegments(glyph, nativeRanges));
            assertArrayEquals(glyph, expected,
                    TextSegmenter.buildSegments(glyph, new int[]{0, glyph.length()}));
        }
    }

    @Test
    public void graphemeAlignmentKeepsNativeWordGranularityAndPunctuationGaps() {
        String text = "hello e\u0301world \uD83D\uDC4D\uD83C\uDFFD";
        assertArrayEquals(new int[]{0, 4, 6, 7, 8, 12, -1, 14, 17},
                TextSegmenter.buildSegments(text, new int[]{0, 5, 6, 7, 7, 13}));
    }

    @Test
    public void nativeRangesUseTheBigBangWordAndPunctuationFormat() {
        assertArrayEquals(
                new int[]{0, 1, 3, 4, -1, 2, 2},
                TextSegmenter.buildSegments("你好，世界", new int[]{0, 2, 3, 5}));
    }

    @Test
    public void whitespaceIsKeptBetweenWordsButNotMadeSelectable() {
        assertArrayEquals(
                new int[]{0, 4, 6, 7, -1},
                TextSegmenter.buildSegments("hello 世界", new int[]{0, 5, 6, 8}));
    }

    @Test
    public void emptyInputDoesNotProduceAnInvalidBigBangSegment() {
        assertNull(TextSegmenter.buildSegments("", new int[]{0, 1}));
    }
}

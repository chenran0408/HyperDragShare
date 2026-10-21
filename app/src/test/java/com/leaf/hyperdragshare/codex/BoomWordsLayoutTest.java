package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.util.Arrays;
import java.util.TreeSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class BoomWordsLayoutTest {
    private BoomWordsLayout layout(String text, int... segments) {
        BoomWordsLayout layout = new BoomWordsLayout(RuntimeEnvironment.getApplication());
        assertTrue(layout.layoutWords(segments, text, -1));
        return layout;
    }

    private TreeSet<Integer> selected(Integer... ids) {
        return new TreeSet<>(Arrays.asList(ids));
    }

    @Test
    public void displayedCharactersCannotBeSplitIntoCombiningOrEmojiFragments() {
        for (String glyph : new String[]{"e\u0301", "\uD83D\uDC4D\uD83C\uDFFD",
                "\uD83C\uDDE8\uD83C\uDDF3", "\u2764\uFE0F",
                "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66"}) {
            BoomWordsLayout layout = layout(glyph, 0, glyph.length() - 1, -1);
            assertFalse(glyph, layout.canSplitSelectedWords(selected(0)));
            assertNull(layout.splitSelectedWordsToChars(selected(0)));
            assertEquals(glyph, layout.getWord(0));
        }
    }

    @Test
    public void splitPreservesGraphemeOffsetsSelectionAndUndoSnapshot() {
        String family = "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66";
        String text = "A" + family + "e\u0301Z";
        BoomWordsLayout layout = layout(text, 0, text.length() - 1, -1);
        int[] before = layout.getSegments();
        assertTrue(layout.canSplitSelectedWords(selected(0)));
        assertEquals(selected(0, 1, 2, 3), layout.splitSelectedWordsToChars(selected(0)));
        assertEquals("A", layout.getWord(0));
        assertEquals(family, layout.getWord(1));
        assertEquals("e\u0301", layout.getWord(2));
        assertEquals("Z", layout.getWord(3));
        assertEquals(1 + family.length(), layout.getWordStart(2));
        assertFalse(layout.canSplitSelectedWords(selected(0, 1, 2, 3)));
        assertEquals(text, layout.getOriText());
        assertEquals(text, layout(text, before).getWord(0));
        assertEquals(selected(0), layout.mergeSelectedWords(selected(0, 1, 2, 3)));
        assertEquals(text, layout.getWord(0));
    }

    @Test
    public void editAvailabilityTracksSelectionAndParagraphBoundaries() {
        BoomWordsLayout layout = layout("甲乙\n丙丁", 0, 0, 1, 1, 3, 3, 4, 4, -1);
        assertFalse(layout.canMergeSelectedWords(selected(0)));
        assertFalse(layout.canMergeSelectedWords(selected(0, 2)));
        assertFalse(layout.canMergeSelectedWords(selected(1, 2)));
        assertTrue(layout.canMergeSelectedWords(selected(0, 1)));
        assertFalse(layout.canSplitSelectedWords(selected(0, 1)));
        TreeSet<Integer> merged = layout.mergeSelectedWords(selected(0, 1));
        assertTrue(layout.canSplitSelectedWords(merged));
        assertFalse(layout.canMergeSelectedWords(merged));
    }

    @Test
    public void singleSupplementaryCharacterCannotBeSplit() {
        BoomWordsLayout layout = layout("😀", 0, 1, -1);
        assertFalse(layout.canSplitSelectedWords(selected(0)));
        assertFalse(layout.canSplitSelectedWords(selected()));
    }

    @Test
    public void mergePreservesSpacesAndLeavesUnselectedWordsAlone() {
        BoomWordsLayout layout = layout("小米 手机 真好", 0, 1, 3, 4, 6, 7, -1);

        assertEquals(selected(0), layout.mergeSelectedWords(selected(0, 1)));
        assertEquals(2, layout.getWordCount());
        assertEquals("小米 手机", layout.getWord(0));
        assertEquals("真好", layout.getWord(1));
        assertEquals(6, layout.getWordStart(1));
        assertEquals("小米 手机 真好", layout.getOriText());
    }

    @Test
    public void mergeKeepsSeparateSelectedRunsAndRemapsTheirSelection() {
        BoomWordsLayout layout = layout("甲乙丙丁戊", 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, -1);

        assertEquals(selected(0, 2), layout.mergeSelectedWords(selected(0, 1, 3, 4)));
        assertEquals(3, layout.getWordCount());
        assertEquals("甲乙", layout.getWord(0));
        assertEquals("丙", layout.getWord(1));
        assertEquals("丁戊", layout.getWord(2));
    }

    @Test
    public void mergeDoesNotCrossParagraphBreaks() {
        BoomWordsLayout layout = layout("甲乙\n丙丁", 0, 0, 1, 1, 3, 3, 4, 4, -1);

        assertNull(layout.mergeSelectedWords(selected(1, 2)));
        assertEquals(selected(0, 1), layout.mergeSelectedWords(selected(0, 1, 2, 3)));
        assertEquals("甲乙", layout.getWord(0));
        assertEquals("丙丁", layout.getWord(1));
        assertTrue(layout.isGapRow(1));
    }

    @Test
    public void savingCurrentSegmentsPreservesMergedChipsAndPunctuation() {
        BoomWordsLayout original = layout("甲乙，丙丁", 0, 0, 1, 1, 3, 3, 4, 4, -1, 2, 2);
        original.mergeSelectedWords(selected(0, 1));
        BoomWordsLayout restored = layout(original.getOriText(), original.getSegments());

        assertEquals(original.getWordCount(), restored.getWordCount());
        for (int i = 0; i < original.getWordCount(); ++i) {
            assertEquals(original.getWord(i), restored.getWord(i));
            assertEquals(original.getWordStart(i), restored.getWordStart(i));
            assertEquals(original.isPunc(i), restored.isPunc(i));
        }
    }

    @Test
    public void splitMergedTextSkipsSpacesAndPreservesUnicodeCodePoints() {
        BoomWordsLayout layout = layout("小米 😀", 0, 1, 3, 4, -1);
        layout.mergeSelectedWords(selected(0, 1));

        assertEquals(selected(0, 1, 2), layout.splitSelectedWordsToChars(selected(0)));
        assertEquals(3, layout.getWordCount());
        assertEquals("小", layout.getWord(0));
        assertEquals("米", layout.getWord(1));
        assertEquals("😀", layout.getWord(2));
        assertEquals(3, layout.getWordStart(2));
        assertNull(layout.splitSelectedWordsToChars(selected(2)));
    }

    @Test
    public void splittingPartialSelectionKeepsOtherWordsAndSelectedCharacterOrder() {
        BoomWordsLayout layout = layout("保留 小米 😀手机 尾巴", 0, 1, 3, 4, 6, 9, 11, 12, -1);

        assertEquals(selected(1, 2, 3, 4, 5), layout.splitSelectedWordsToChars(selected(1, 2)));
        assertEquals(7, layout.getWordCount());
        assertEquals("保留", layout.getWord(0));
        assertEquals("小", layout.getWord(1));
        assertEquals("米", layout.getWord(2));
        assertEquals("😀", layout.getWord(3));
        assertEquals("手", layout.getWord(4));
        assertEquals("机", layout.getWord(5));
        assertEquals("尾巴", layout.getWord(6));
        assertEquals(6, layout.getWordStart(3));
        assertEquals(11, layout.getWordStart(6));
        assertEquals("保留 小米 😀手机 尾巴", layout.getOriText());
        assertFalse(layout.canSplitSelectedWords(selected(1, 2, 3, 4, 5)));
    }

    @Test
    public void splittingAllKeepsPunctuationParagraphsAndTheOriginalSnapshot() {
        BoomWordsLayout layout = layout("小米，\n😀手机", 0, 1, 4, 7, -1, 2, 2);
        int[] before = layout.getSegments();

        assertEquals(selected(0, 1, 2, 3, 4, 5), layout.splitSelectedWordsToChars(selected(0, 1, 2)));
        assertEquals("小米，\n😀手机", layout.getOriText());
        assertArrayEquals(new int[]{0, 0, 1, 1, 4, 5, 6, 6, 7, 7, -1, 2, 2}, layout.getSegments());
        assertTrue(layout.isPunc(2));
        assertEquals("😀", layout.getWord(3));
        BoomWordsLayout restored = layout(layout.getOriText(), before);
        assertArrayEquals(new int[]{0, 1, 4, 7, -1, 2, 2}, restored.getSegments());
        assertEquals("小米", restored.getWord(0));
        assertEquals("😀手机", restored.getWord(2));
    }

    @Test
    public void nonAdjacentSelectionDoesNotChangeChips() {
        BoomWordsLayout layout = layout("甲乙丙", 0, 0, 1, 1, 2, 2, -1);

        assertNull(layout.mergeSelectedWords(selected(0, 2)));
        assertEquals(3, layout.getWordCount());
    }

    @Test
    public void prependParagraphPreservesMergedAndSplitChipsAndPunctuation() {
        BoomWordsLayout original = layout("甲乙，丙丁", 0, 0, 1, 1, 3, 4, -1, 2, 2);
        original.mergeSelectedWords(selected(0, 1));
        original.splitSelectedWordsToChars(selected(2));
        BoomWordsLayout prefix = layout("前文，\n更多", 0, 1, 4, 5, -1, 2, 2);
        int offset = prefix.getOriText().length() + 1;
        int[] originalSegments = original.getSegments();
        int[] joined = BoomWordsLayout.concatenateSegments(prefix.getSegments(), originalSegments, offset);
        BoomWordsLayout result = layout(prefix.getOriText() + "\n" + original.getOriText(), joined);

        assertEquals("前文，\n更多\n甲乙，丙丁", result.getOriText());
        assertEquals(prefix.getWordCount() + original.getWordCount(), result.getWordCount());
        for (int i = 0; i < original.getWordCount(); ++i) {
            int resultIndex = prefix.getWordCount() + i;
            assertEquals(original.getWord(i), result.getWord(resultIndex));
            assertEquals(original.getWordStart(i) + offset, result.getWordStart(resultIndex));
            assertEquals(original.isPunc(i), result.isPunc(resultIndex));
        }
        assertArrayEquals(originalSegments, original.getSegments());
    }

    @Test
    public void appendParagraphPreservesExistingBoundariesAcrossLineBreak() {
        BoomWordsLayout original = layout("甲乙，丙丁", 0, 0, 1, 1, 3, 4, -1, 2, 2);
        original.mergeSelectedWords(selected(0, 1));
        original.splitSelectedWordsToChars(selected(2));
        BoomWordsLayout suffix = layout("后文。\n末尾", 0, 1, 4, 5, -1, 2, 2);
        int offset = original.getOriText().length() + 1;
        int[] joined = BoomWordsLayout.concatenateSegments(original.getSegments(), suffix.getSegments(), offset);
        BoomWordsLayout result = layout(original.getOriText() + "\n" + suffix.getOriText(), joined);

        assertEquals("甲乙，丙丁\n后文。\n末尾", result.getOriText());
        assertEquals(original.getWordCount() + suffix.getWordCount(), result.getWordCount());
        for (int i = 0; i < original.getWordCount(); ++i) {
            assertEquals(original.getWord(i), result.getWord(i));
            assertEquals(original.getWordStart(i), result.getWordStart(i));
            assertEquals(original.isPunc(i), result.isPunc(i));
        }
        for (int i = 0; i < suffix.getWordCount(); ++i) {
            assertEquals(suffix.getWordStart(i) + offset,
                    result.getWordStart(original.getWordCount() + i));
        }
    }
}

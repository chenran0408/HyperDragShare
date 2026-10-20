package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    public void nonAdjacentSelectionDoesNotChangeChips() {
        BoomWordsLayout layout = layout("甲乙丙", 0, 0, 1, 1, 2, 2, -1);

        assertNull(layout.mergeSelectedWords(selected(0, 2)));
        assertEquals(3, layout.getWordCount());
    }
}

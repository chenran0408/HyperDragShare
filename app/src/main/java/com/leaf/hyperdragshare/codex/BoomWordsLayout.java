package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.icu.text.BreakIterator;
import android.content.res.Resources;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;

import java.util.ArrayList;
import java.util.TreeSet;
import java.util.Locale;

public class BoomWordsLayout {

    private final static String TAG = "BoomWordsLayout";

    private final int mMaxRowNumber;
    private int mBoomPageWidth;
    private final int mChipMaxWidth;
    private final int mWordMinWidth;
    private final int mWordBaseWidth;
    private final int mPuncMinWidth;
    private final int mPuncBaseWidth;
    private final TextPaint mWordPaint;
    private final TextPaint mPuncPaint;
    private final Resources mResources;

    private RangeList<Word> mWords = new RangeList<Word>();
    private ArrayList<Integer> mRowStart = new ArrayList<Integer>();
    private ArrayList<Integer> mRowCount = new ArrayList<Integer>();
    private ArrayList<Boolean> mRowIsGap = new ArrayList<Boolean>();
    private ArrayList<Integer> mHardBreaks = new ArrayList<Integer>();
    private int[] mIdToRow;
    private int mTouchedIndex;
    private String mOriText;

    private class RangeList<E> extends ArrayList<E> {
        public void remove(int fromIndex, int toIndex) {
            if (fromIndex < toIndex) {
                removeRange(fromIndex, toIndex);
            }
        }
    }

    private class Word {
        public final String word;
        public final int start;
        public final boolean punc;

        public Word(String w, int s, boolean isPunc) {
            word = w;
            start = s;
            punc = isPunc;
        }
    }

    public BoomWordsLayout(Context context) {
        Resources res = context.getResources();
        mResources = res;
        final int displayWidth = res.getDisplayMetrics().widthPixels;
        mBoomPageWidth = displayWidth - res.getDimensionPixelSize(R.dimen.page_margin_left)
                - res.getDimensionPixelSize(R.dimen.page_margin_right);
        mChipMaxWidth = res.getDimensionPixelSize(R.dimen.chip_max_width);
        //mMaxRowNumber = displayWidth > 1080 ? 11 : 10;
        mMaxRowNumber = 1000;
        mWordMinWidth = res.getDimensionPixelSize(R.dimen.word_min_width);
        mWordBaseWidth = res.getDimensionPixelSize(R.dimen.word_base_width);
        mPuncMinWidth = res.getDimensionPixelSize(R.dimen.punc_min_width);
        mPuncBaseWidth = res.getDimensionPixelSize(R.dimen.punc_base_width);
        mWordPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        mPuncPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        final float defaultTextSize = res.getDimension(R.dimen.chip_text_size);
        mWordPaint.setTextSize(defaultTextSize);
        mPuncPaint.setTextSize(defaultTextSize);
    }

    public void setTextSizeSp(float textSizeSp) {
        final float sizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                textSizeSp,
                mResources.getDisplayMetrics()
        );
        mWordPaint.setTextSize(sizePx);
        mPuncPaint.setTextSize(sizePx);
        generateLayout();
    }

    public boolean setAvailableWidth(int widthPx) {
        if (widthPx <= 0 || widthPx == mBoomPageWidth) {
            return false;
        }
        mBoomPageWidth = widthPx;
        generateLayout();
        return true;
    }

    public int getChipMaxWidth() {
        return Math.max(1, Math.min(mBoomPageWidth, mChipMaxWidth));
    }

    public int getChipWidth(int index) {
        return measureChip(index);
    }

    public boolean layoutWords(int[] segment, String text, int touchedIndex) {
        int puncIndexStart = -1;
        for (int i = 0; i < segment.length; ++i) {
            if (segment[i] == -1) {
                puncIndexStart = i;
                break;
            }
        }
        if (puncIndexStart == -1) return false;
        int[] newSeg = new int[puncIndexStart];
        int wordIndexStart = 0;
        ++puncIndexStart;
        int garbageOffset = 0;
        int touchIndexOffset = 0;
        StringBuilder newText = new StringBuilder();
        for (int i = 0; i < newSeg.length; i += 2) {
            int curWordStart = segment[i];
            int curPuncStart = puncIndexStart == segment.length ? text.length() : segment[puncIndexStart];
            if (curWordStart < curPuncStart) {
                if (curWordStart > wordIndexStart) {
                    int removedDiff = appendFilteredGap(newText, text, wordIndexStart, curWordStart);
                    if (touchedIndex > curWordStart) {
                        touchIndexOffset += removedDiff;
                    }
                    garbageOffset += removedDiff;
                } else if (curWordStart < wordIndexStart) {
                    DragShareLog.e(TAG, "rebuild segment failed curWordStart=" + curWordStart
                            + ", wordIndexStart=" + wordIndexStart);
                    return false;
                }
                newSeg[i] = segment[i] - garbageOffset;
                newSeg[i + 1] = segment[i + 1] - garbageOffset;
                wordIndexStart = segment[i + 1] + 1;
                newText.append(text.substring(segment[i], segment[i + 1] + 1));
            } else {
                if (curPuncStart > wordIndexStart) {
                    int removedDiff = appendFilteredGap(newText, text, wordIndexStart, curPuncStart);
                    if (touchedIndex > curPuncStart) {
                        touchIndexOffset += removedDiff;
                    }
                    garbageOffset += removedDiff;
                } else if (curPuncStart < wordIndexStart) {
                    DragShareLog.e(TAG, "rebuild segment failed curPuncStart=" + curPuncStart
                            + ", wordIndexStart=" + wordIndexStart);
                    return false;
                }
                wordIndexStart = segment[puncIndexStart + 1] + 1;
                newText.append(text.substring(segment[puncIndexStart], segment[puncIndexStart + 1] + 1));
                puncIndexStart += 2;
                i -= 2;
            }
        }
        if (puncIndexStart < segment.length) {
            for (int i = puncIndexStart; i < segment.length; i += 2) {
                int curPuncStart = segment[i];
                if (curPuncStart > wordIndexStart) {
                    int removedDiff = appendFilteredGap(newText, text, wordIndexStart, curPuncStart);
                    if (touchedIndex > curPuncStart) {
                        touchIndexOffset += removedDiff;
                    }
                } else if (curPuncStart < wordIndexStart) {
                    DragShareLog.e(TAG, "add ending punctuation failed curPuncStart="
                            + curPuncStart + ", wordIndexStart=" + wordIndexStart);
                    return false;
                }
                wordIndexStart = segment[i + 1] + 1;
                newText.append(text.substring(segment[i], segment[i + 1] + 1));
            }
        }
        return layoutWordsAfterFilter(newSeg, newText.toString(), touchedIndex - touchIndexOffset);
    }

    private int appendFilteredGap(StringBuilder newText, String text, int start, int end) {
        int preserved = 0;
        for (int i = start; i < end; ++i) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch) || Character.isSpaceChar(ch)) {
                newText.append(ch);
                ++preserved;
            }
        }
        return (end - start) - preserved;
    }

    private boolean layoutWordsAfterFilter(int[] segment, String text, int touchedIndex) {
        mOriText = text;
        mWords.clear();
        mHardBreaks.clear();
        mTouchedIndex = -1;
        int start;
        int end;
        int prev = 0;
        BreakIterator characters = BreakIterator.getCharacterInstance(Locale.ROOT);
        characters.setText(text);
        for (int i = 0; i < segment.length; i += 2) {
            start = segment[i];
            end = segment[i + 1] + 1;
            addGapIntoChips(prev, start, characters);
            String trim = text.substring(start, end).replaceAll("\\p{Z}", " ").trim();
            if (!TextUtils.isEmpty(trim)) {
                if (touchedIndex >= start && touchedIndex < end) {
                    mTouchedIndex = mWords.size();
                }
                mWords.add(new Word(trim, start, false));
            }
            prev = end;
        }
        addGapIntoChips(prev, text.length(), characters);

        final int wordCount = mWords.size();
        if (wordCount > 0) {
            generateLayout();
            final int rowCount = mRowCount.size();
            if (rowCount > mMaxRowNumber) {
                if (mTouchedIndex == -1) {
                    start = 0;
                    end = mRowStart.get(mMaxRowNumber);
                } else {
                    final int row = getRowForIndex(mTouchedIndex);
                    if (row < mMaxRowNumber / 2) {
                        start = 0;
                        end = getRowStart(mMaxRowNumber);
                    } else if (row >= rowCount - mMaxRowNumber / 2) {
                        start = getRowStart(rowCount - mMaxRowNumber);
                        end = wordCount;
                    } else {
                        start = getRowStart(row - mMaxRowNumber / 2);
                        end = getRowStart(row + mMaxRowNumber / 2);
                    }
                }
                mWords.remove(end, wordCount);
                mWords.remove(0, start);
                generateLayout();
            }
            return true;
        }
        return false;
    }

    private void addGapIntoChips(int start, int end, BreakIterator characters) {
        int offset = start;
        while (offset < end) {
            int boundary = characters.following(offset);
            int next = boundary == BreakIterator.DONE || boundary > end ? end : boundary;
            String cluster = mOriText.substring(offset, next);
            if (cluster.indexOf('\n') >= 0) {
                addHardBreak();
            } else if (!isWhitespace(cluster)) {
                mWords.add(new Word(cluster, offset, true));
            }
            offset = next;
        }
    }

    private boolean isWhitespace(String cluster) {
        for (int offset = 0; offset < cluster.length();) {
            int codePoint = cluster.codePointAt(offset);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private void addHardBreak() {
        final int breakIndex = mWords.size();
        if (mHardBreaks.size() > 0 && mHardBreaks.get(mHardBreaks.size() - 1) == breakIndex) {
            return;
        }
        mHardBreaks.add(breakIndex);
    }

    private int measureChip(int index) {
        final Word word = mWords.get(index);
        if (word.punc) {
            return Math.min(getChipMaxWidth(),
                    Math.max(mPuncMinWidth, mPuncBaseWidth
                            + (int) Math.ceil(measuredTextWidth(mPuncPaint, word.word))));
        } else {
            return Math.min(getChipMaxWidth(),
                    Math.max(mWordMinWidth, mWordBaseWidth
                            + (int) Math.ceil(measuredTextWidth(mWordPaint, word.word))));
        }
    }

    private float measuredTextWidth(TextPaint paint, String text) {
        float fallback = 0f;
        final float textSize = paint.getTextSize();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            boolean wide = script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL
                    || script == Character.UnicodeScript.BOPOMOFO;
            fallback += textSize * (wide ? 1f : 0.55f);
            offset += Character.charCount(codePoint);
        }
        return Math.max(paint.measureText(text), fallback);
    }

    private void generateLayout() {
        int count = 0;
        int start = 0;
        int remain = mBoomPageWidth;
        mRowCount.clear();
        mRowStart.clear();
        mRowIsGap.clear();
        mIdToRow = new int[mWords.size()];
        int i = 0;
        while (i < mWords.size()) {
            if (isHardBreakIndex(i)) {
                if (count > 0) {
                    addRow(start, count, false);
                }
                addRow(i, 0, true);
                start = i;
                count = 0;
                remain = mBoomPageWidth;
            }
            int unitEnd = wrapUnitEnd(i);
            int unitWidth = measureRange(i, unitEnd);
            if (unitWidth > mBoomPageWidth) {
                unitEnd = i + 1;
                unitWidth = measureChip(i);
            }
            if (unitWidth > remain && count > 0) {
                addRow(start, count, false);
                start = i;
                count = 0;
                remain = mBoomPageWidth;
                continue;
            }
            for (int index = i; index < unitEnd; ++index) {
                mIdToRow[index] = mRowCount.size();
            }
            count += unitEnd - i;
            remain -= unitWidth;
            i = unitEnd;
        }
        if (count > 0) {
            addRow(start, count, false);
        }
    }

    /**
     * Keep closing punctuation on the same wrap unit as the word it follows. A row may
     * therefore move one word together with its punctuation instead of starting the next
     * row with an orphan punctuation chip.
     */
    private int wrapUnitEnd(int start) {
        int end = start + 1;
        if (mWords.get(start).punc) {
            return end;
        }
        while (end < mWords.size()
                && !isHardBreakIndex(end)
                && mWords.get(end).punc
                && !isOpeningPunctuation(mWords.get(end).word)
                && measureRange(start, end + 1) <= mBoomPageWidth) {
            ++end;
        }
        return end;
    }

    private int measureRange(int start, int end) {
        int width = 0;
        for (int index = start; index < end; ++index) {
            width += measureChip(index);
        }
        return width;
    }

    private boolean isOpeningPunctuation(String word) {
        if (TextUtils.isEmpty(word)) {
            return false;
        }
        int type = Character.getType(word.codePointAt(0));
        return type == Character.START_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION;
    }

    private void addRow(int start, int count, boolean isGap) {
        mRowStart.add(start);
        mRowCount.add(count);
        mRowIsGap.add(isGap);
    }

    private boolean isHardBreakIndex(int index) {
        return mHardBreaks.contains(index);
    }

    public int getRowCount() {
        return mRowCount.size();
    }

    public int getRowStart(int row) {
        return mRowStart.get(row);
    }

    public int getColumnCount(int row) {
        return mRowCount.get(row);
    }

    public boolean isGapRow(int row) {
        return mRowIsGap.get(row);
    }

    public int getRowForIndex(int index) {
        if (index < 0 || index >= mIdToRow.length) {
            return 0;
        }
        return mIdToRow[index];
    }

    public boolean isPunc(int index) {
        return mWords.get(index).punc;
    }

    public String getWord(int index) {
        return mWords.get(index).word;
    }

    public int getWordEnd(int index) {
        return mWords.get(index).word.length() + mWords.get(index).start;
    }

    public int getWordStart(int index) {
        return mWords.get(index).start;
    }

    public String getOriText(int start, int end) {
        return mOriText.substring(start, end);
    }

    public String getOriText() {
        return mOriText;
    }

    public int getWordCount() {
        return mWords.size();
    }

    public int getTouchedIndex() {
        return mTouchedIndex;
    }

    public int[] getSegments() {
        int[] segment = new int[mWords.size() * 2 + 1];
        int output = 0;
        for (Word word : mWords) {
            if (!word.punc) {
                segment[output++] = word.start;
                segment[output++] = word.start + word.word.length() - 1;
            }
        }
        segment[output++] = -1;
        for (Word word : mWords) {
            if (word.punc) {
                segment[output++] = word.start;
                segment[output++] = word.start + word.word.length() - 1;
            }
        }
        return segment;
    }

    /** Concatenates valid word/-1/punctuation arrays; secondOffset includes the joining gap. */
    public static int[] concatenateSegments(int[] first, int[] second, int secondOffset) {
        int firstPunctuation = 0;
        while (first[firstPunctuation] != -1) ++firstPunctuation;
        int secondPunctuation = 0;
        while (second[secondPunctuation] != -1) ++secondPunctuation;
        int[] joined = new int[first.length + second.length - 1];
        int output = 0;
        for (int i = 0; i < firstPunctuation; ++i) joined[output++] = first[i];
        for (int i = 0; i < secondPunctuation; ++i) joined[output++] = second[i] + secondOffset;
        joined[output++] = -1;
        for (int i = firstPunctuation + 1; i < first.length; ++i) joined[output++] = first[i];
        for (int i = secondPunctuation + 1; i < second.length; ++i) {
            joined[output++] = second[i] + secondOffset;
        }
        return joined;
    }

    /** Merge each adjacent selected run, preserving spaces and paragraph boundaries. */
    public boolean canMergeSelectedWords(TreeSet<Integer> selectedIds) {
        for (int index : selectedIds) {
            if (selectedIds.contains(index + 1) && !isHardBreakIndex(index + 1)) return true;
        }
        return false;
    }

    public boolean canSplitSelectedWords(TreeSet<Integer> selectedIds) {
        for (int index : selectedIds) {
            Word word = mWords.get(index);
            if (!word.punc && hasMultipleCharacters(word.word)) return true;
        }
        return false;
    }

    private static boolean hasMultipleCharacters(String text) {
        BreakIterator characters = BreakIterator.getCharacterInstance(Locale.ROOT);
        characters.setText(text);
        characters.first();
        return characters.next() != BreakIterator.DONE && characters.next() != BreakIterator.DONE;
    }

    public TreeSet<Integer> mergeSelectedWords(TreeSet<Integer> selectedIds) {
        if (selectedIds == null || selectedIds.size() < 2) {
            return null;
        }
        RangeList<Word> newWords = new RangeList<Word>();
        ArrayList<Integer> newHardBreaks = new ArrayList<Integer>();
        TreeSet<Integer> newSelected = new TreeSet<Integer>();
        boolean changed = false;
        for (int i = 0; i < mWords.size(); ++i) {
            if (isHardBreakIndex(i)) {
                newHardBreaks.add(newWords.size());
            }
            Word word = mWords.get(i);
            boolean selected = selectedIds.contains(i);
            int end = i;
            if (selected) {
                while (end + 1 < mWords.size() && selectedIds.contains(end + 1)
                        && !isHardBreakIndex(end + 1)) {
                    ++end;
                }
                newSelected.add(newWords.size());
            }
            if (end > i) {
                word = new Word(mOriText.substring(word.start, getWordEnd(end)), word.start, false);
                changed = true;
            }
            newWords.add(word);
            i = end;
        }
        if (!changed) {
            return null;
        }
        mWords = newWords;
        mHardBreaks = newHardBreaks;
        generateLayout();
        return newSelected;
    }

    public TreeSet<Integer> splitSelectedWordsToChars(TreeSet<Integer> selectedIds) {
        if (selectedIds == null || selectedIds.isEmpty()) {
            return null;
        }
        RangeList<Word> newWords = new RangeList<Word>();
        ArrayList<Integer> newHardBreaks = new ArrayList<Integer>();
        TreeSet<Integer> newSelected = new TreeSet<Integer>();
        boolean changed = false;
        for (int i = 0; i < mWords.size(); ++i) {
            if (isHardBreakIndex(i)) {
                newHardBreaks.add(newWords.size());
            }
            final Word word = mWords.get(i);
            final boolean selected = selectedIds.contains(i);
            if (!selected || word.punc || !hasMultipleCharacters(word.word)) {
                newWords.add(word);
                if (selected) {
                    newSelected.add(newWords.size() - 1);
                }
                continue;
            }
            changed = true;
            BreakIterator characters = BreakIterator.getCharacterInstance(Locale.ROOT);
            characters.setText(word.word);
            for (int offset = characters.first(), next = characters.next();
                    next != BreakIterator.DONE; offset = next, next = characters.next()) {
                final int codePoint = word.word.codePointAt(offset);
                if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                    newWords.add(new Word(word.word.substring(offset, next), word.start + offset, false));
                    newSelected.add(newWords.size() - 1);
                }
            }
        }
        if (!changed) {
            return null;
        }
        mWords = newWords;
        mHardBreaks = newHardBreaks;
        generateLayout();
        return newSelected;
    }
}

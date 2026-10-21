package com.leaf.hyperdragshare.codex;

import java.util.TreeSet;

/** Selection state only; the Miuix panel owns actions and never covers the words. */
public final class BoomActionHandler {
    public static final int SEARCH_WEB = 0;
    public static final int SEARCH_DICTIONARY = 1;
    public static final int SEARCH_TRANSLATE = 2;

    private final BoomChipPage mBoomPage;
    final TreeSet<Integer> mSelectedId = new TreeSet<>();

    public BoomActionHandler(BoomChipPage page) {
        mBoomPage = page;
    }

    public void onSelect(TreeSet<Integer> savedState) {
        mSelectedId.clear();
        int count = mBoomPage.mLayout.getWordCount();
        for (int id : savedState) if (id >= 0 && id < count) mSelectedId.add(id);
        mBoomPage.notifySelectionChanged();
    }

    public void onSelect(int start, int end) {
        for (int id = start; id <= end; id++) mSelectedId.add(id);
        mBoomPage.notifySelectionChanged();
    }

    public void deSelect(int start, int end) {
        for (int id = start; id <= end; id++) mSelectedId.remove(id);
        if (mSelectedId.isEmpty()) mBoomPage.resetChips();
        mBoomPage.notifySelectionChanged();
    }

    public boolean handleClick() {
        if (!hasSelection()) return false;
        mSelectedId.clear();
        mBoomPage.resetChips();
        mBoomPage.notifySelectionChanged();
        return true;
    }

    void clearSelectionStateForRelayout() {
        mSelectedId.clear();
        mBoomPage.notifySelectionChanged();
    }

    public boolean hasSelection() { return !mSelectedId.isEmpty(); }

    public boolean isAllSelected() {
        return hasSelection() && mSelectedId.size() == mBoomPage.mLayout.getWordCount();
    }

    public String getSelectedText() {
        final int wordCount = mBoomPage.mLayout.getWordCount();
        if (mSelectedId.size() == wordCount) {
            return mBoomPage.mLayout.getOriText();
        }
        StringBuilder res = new StringBuilder();
        int last = -1;
        for (Integer cur : mSelectedId) {
            if (last == -1) {
                res.append(mBoomPage.mLayout.getWord(cur));
            } else if (cur == last + 1) {
                final int end = cur == wordCount - 1 ?
                        mBoomPage.mLayout.getOriText().length() : mBoomPage.mLayout.getWordEnd(cur);
                res.append(mBoomPage.mLayout.getOriText(mBoomPage.mLayout.getWordEnd(last), end));
            } else {
                res.append(mBoomPage.mLayout.getWord(cur));
            }
            last = cur;
        }
        return res.toString();
    }

    /** Counts the exact code points produced by getSelectedText without building that string. */
    public int getSelectedTextCharacterCount() {
        final int wordCount = mBoomPage.mLayout.getWordCount();
        final String original = mBoomPage.mLayout.getOriText();
        if (mSelectedId.size() == wordCount) {
            return original.codePointCount(0, original.length());
        }

        int count = 0;
        boolean previousEndedWithHighSurrogate = false;
        int last = -1;
        for (Integer cur : mSelectedId) {
            final String fragment;
            final int start;
            final int end;
            if (last == -1 || cur != last + 1) {
                fragment = mBoomPage.mLayout.getWord(cur);
                start = 0;
                end = fragment.length();
            } else {
                fragment = original;
                start = mBoomPage.mLayout.getWordEnd(last);
                end = cur == wordCount - 1
                        ? original.length() : mBoomPage.mLayout.getWordEnd(cur);
            }

            int fragmentCount = fragment.codePointCount(start, end);
            if (previousEndedWithHighSurrogate && start < end
                    && Character.isLowSurrogate(fragment.charAt(start))) {
                fragmentCount--;
            }
            count += fragmentCount;
            if (start < end) {
                previousEndedWithHighSurrogate = Character.isHighSurrogate(fragment.charAt(end - 1));
            }
            last = cur;
        }
        return count;
    }

}

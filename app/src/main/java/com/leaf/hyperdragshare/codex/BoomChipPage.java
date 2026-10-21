package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.view.LayoutInflater;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.Serializable;
import java.util.TreeSet;

public class BoomChipPage {
    
    private final static String TAG = "BoomChipPage";
    private final static boolean DBG = true;

    final BoomWordsLayout mLayout;
    final Context mContext;
    final Actions mActions;

    public interface Actions {
        void dismiss();
        void copy(String text);
        void search(String text, int type);
        void share(String text);
        boolean dragText(View source, String text);
    }
    final View mBoomTable;
    final View mMask;
    final CustomScrollView mScroller;
    final View mCancel;
    final View mBoomPage;
    final BoomActionHandler mBoomActionHandler;
    final View.OnClickListener mDismissClickListener;

    private final SwipeSelectView mBoomConent;
    private final boolean mEnableLegacyMask;
    private final TextView mAdjacentTopHint;
    private final TextView mAdjacentBottomHint;
    private final int mScrollerBaseInset;

    Serializable mSavedData;
    private OnAdjacentRequestListener mOnAdjacentRequestListener;
    private boolean mAdjacentLoading;
    private float mAdjacentOffset;
    private float mTextSizeSp = BigBangSettings.DEFAULT_TEXT_SIZE_SP;

    private int mTouchedX;
    private int mTouchedY;

    OnGlobalLayoutListener mDoBoomAnimation = new OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            mBoomConent.getViewTreeObserver().removeOnGlobalLayoutListener(mDoBoomAnimation);
            if (mEnableLegacyMask && mScroller.canScrollVertically(1)) {
                mMask.setVisibility(View.VISIBLE);
            }
            if (restoreSelectedState()) {
                if (DBG) {
                    LogUtils.d(TAG, "Skip boom animation when restoring");
                }
                return;
            }
            if (mTouchedX == -1 || mTouchedY == -1) {
                DragShareLog.e(TAG, "bad touch position passed");
                return;
            }
            float pageX = getChipParentX();
            float pageY = getChipParentY();
            if (DBG) {
                LogUtils.d(TAG, "init Chip and do boom animation");
            }
            final int animationRows = Math.min(mLayout.getRowCount(), 12);
            for (int i = 0; i < animationRows; ++i) {
                final LinearLayout row = getChipRow(i);
                if (row == null) {
                    continue;
                }
                float rowX = row.getX();
                float rowY = row.getY();
                for (int j = 0; j < row.getChildCount(); ++j) {
                    View child = row.getChildAt(j);
                    child.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    float newX = mTouchedX - pageX - rowX - child.getMeasuredWidth() / 2;
                    float newY = mTouchedY - pageY - rowY - child.getMeasuredHeight() / 2;
                    float x = child.getX();
                    float y = child.getY();
                    child.setTranslationX(newX - x);
                    child.setTranslationY(newY - y);
                    BoomAnimator.makeBoomAnimation(child);
                }
            }
        }

        private float getChipParentX() {
            int[] location = new int[2];
            mBoomConent.getLocationOnScreen(location);
            return location[0];
        }

        private float getChipParentY() {
            int[] location = new int[2];
            mBoomConent.getLocationOnScreen(location);
            return location[1];
        }
    };

    public BoomChipPage(Context context, View contentView, boolean enableLegacyMask, Actions actions) {
        mContext = context;
        mActions = actions;
        mEnableLegacyMask = enableLegacyMask;
        mBoomPage = contentView;
        mBoomTable = contentView.findViewById(R.id.boom_table);
        mBoomConent = (SwipeSelectView) contentView.findViewById(R.id.boom_content);
        mMask = contentView.findViewById(R.id.boom_mask);
        mCancel = contentView.findViewById(R.id.mask_cancel);
        mScroller = (CustomScrollView) contentView.findViewById(R.id.boom_scroller);
        mAdjacentTopHint = (TextView) contentView.findViewById(R.id.boom_adjacent_top_hint);
        mAdjacentBottomHint = (TextView) contentView.findViewById(R.id.boom_adjacent_bottom_hint);
        mScrollerBaseInset = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                28,
                mContext.getResources().getDisplayMetrics()
        );
        if (!mEnableLegacyMask) {
            mMask.setVisibility(View.GONE);
            removeLegacyChromeSpacing();
        }
        mLayout = new BoomWordsLayout(mContext);
        mBoomConent.setBoomPage(this);
        mDismissClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActions.dismiss();
            }
        };
        mBoomPage.setOnClickListener(mDismissClickListener);
        mBoomTable.setOnClickListener(mDismissClickListener);
        mScroller.setOnClickListener(mDismissClickListener);
        mBoomConent.setOnClickListener(mDismissClickListener);
        mCancel.setOnClickListener(mDismissClickListener);
        mBoomActionHandler = new BoomActionHandler(this);
        mScroller.setOnEdgeDragListener(new CustomScrollView.OnEdgeDragListener() {
            @Override
            public void onEdgeDrag(float offset) {
                updateAdjacentPull(offset);
            }

            @Override
            public void onEdgeDragRelease(float offset, boolean triggered) {
                releaseAdjacentPull(offset, triggered);
            }
        });
    }

    public interface OnAdjacentRequestListener {
        void onAdjacentRequest(String direction);
        String peekAdjacentText(String direction);
    }

    private Runnable mOnChipsChanged;
    private Runnable mOnSelectionChanged;

    public void setOnSelectionChangedListener(Runnable listener) {
        mOnSelectionChanged = listener;
    }

    void notifySelectionChanged() {
        if (mOnSelectionChanged != null) mOnSelectionChanged.run();
    }

    public boolean hasSelection() {
        return mBoomActionHandler != null && mBoomActionHandler.hasSelection();
    }

    public void setOnChipsChangedListener(Runnable listener) {
        mOnChipsChanged = listener;
    }

    private void removeLegacyChromeSpacing() {
        ViewGroup.MarginLayoutParams scrollerParams = (ViewGroup.MarginLayoutParams) mScroller.getLayoutParams();
        scrollerParams.topMargin = 0;
        scrollerParams.bottomMargin = 0;
        mScroller.setLayoutParams(scrollerParams);
        mScroller.setClipToPadding(false);
        mScroller.setPadding(
                mScroller.getPaddingLeft(),
                mScrollerBaseInset,
                mScroller.getPaddingRight(),
                mScrollerBaseInset
        );

        ViewGroup.MarginLayoutParams tableParams = (ViewGroup.MarginLayoutParams) mBoomTable.getLayoutParams();
        tableParams.topMargin = 0;
        tableParams.bottomMargin = 0;
        mBoomTable.setLayoutParams(tableParams);
        mBoomTable.setPadding(
                mBoomTable.getPaddingLeft(),
                0,
                mBoomTable.getPaddingRight(),
                mBoomTable.getPaddingBottom()
        );

        mBoomConent.setPadding(
                mBoomConent.getPaddingLeft(),
                0,
                mBoomConent.getPaddingRight(),
                0
        );
    }

    public boolean initWords(int[] segment, String text, int touchedIndex, int touchedX, int touchedY) {
        if (mLayout.layoutWords(segment, text, touchedIndex)) {
            mTouchedX = touchedX;
            mTouchedY = touchedY;
            initChips(true);
            return true;
        }
        return false;
    }

    /**
     * Clear all chip views and selection state so that a subsequent
     * {@link #initWords} call can fully reinitialise the page (e.g. when
     * the Activity receives a new Intent via {@code onNewIntent}).
     */
    public void prepareForReinit() {
        if (mBoomActionHandler != null) {
            mBoomActionHandler.clearSelectionStateForRelayout();
        }
        mBoomConent.removeAllViews();
    }

    public void setTextSizeSp(float textSizeSp) {
        if (Float.compare(mTextSizeSp, textSizeSp) == 0) {
            return;
        }
        Serializable savedSelection = captureSelectedState();
        mTextSizeSp = textSizeSp;
        mLayout.setTextSizeSp(textSizeSp);
        if (mLayout.getWordCount() == 0) {
            return;
        }
        if (mBoomActionHandler != null) {
            mBoomActionHandler.clearSelectionStateForRelayout();
        }
        mSavedData = savedSelection;
        mBoomConent.removeAllViews();
        initChips(false);
        if (savedSelection != null) {
            mBoomConent.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mBoomConent.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    restoreSelectedState();
                }
            });
        }
    }

    public void resetChips() {
        for (int i = 0; i < mLayout.getRowCount(); ++i) {
            final LinearLayout row = getChipRow(i);
            if (row == null) {
                continue;
            }
            for (int j = 0; j < row.getChildCount(); ++j) {
                View child = row.getChildAt(j);
                if (child.getTag() instanceof BoomChip) {
                    BoomChip chip = (BoomChip) child.getTag();
                    chip.setSelected(false);
                }
            }
            BoomAnimator.makeMoveAnimation(row, row.getTranslationY(), 0);
        }
    }

    public void moveChipRow(int row, float to) {
        View child = mBoomConent.getChildAt(row);
        BoomAnimator.makeMoveAnimation(child, child.getTranslationY(), to);
    }

    public int getRowTop(int row) {
        View child = mBoomConent.getChildAt(row);
        return child == null ? 0 : child.getTop();
    }

    public int getRowsHeight(int topRow, int bottomRow) {
        View top = mBoomConent.getChildAt(topRow);
        View bottom = mBoomConent.getChildAt(bottomRow);
        if (top == null || bottom == null) {
            return 0;
        }
        return bottom.getBottom() - top.getTop();
    }

    public boolean handleClick() {
        return mBoomActionHandler != null && mBoomActionHandler.handleClick();
    }

    public Serializable captureSelectedState() {
        if (mBoomActionHandler != null && mBoomActionHandler.hasSelection()) {
            TreeSet<Integer> wordSet = mBoomActionHandler.mSelectedId;
            int[][] ranges = new int[wordSet.size()][2];
            int idx = 0;
            for (Integer wordIdx : wordSet) {
                ranges[idx][0] = mLayout.getWordStart(wordIdx);
                ranges[idx][1] = mLayout.getWordEnd(wordIdx);
                idx++;
            }
            return ranges;
        }
        return null;
    }

    public void restoreSelectedState(Serializable savedState) {
        mSavedData = savedState;
    }

    public String getOriginalText() {
        return mLayout.getOriText();
    }

    public int[] getCurrentSegments() {
        return mLayout.getSegments();
    }

    public String selectedTextOrOriginal() {
        return mBoomActionHandler != null && mBoomActionHandler.hasSelection()
                ? mBoomActionHandler.getSelectedText()
                : mLayout.getOriText();
    }

    public void selectAll() {
        final int wordCount = mLayout.getWordCount();
        if (wordCount <= 0) {
            return;
        }
        if (mBoomActionHandler != null && mBoomActionHandler.isAllSelected()) {
            handleClick();
            return;
        }
        for (int i = 0; i < mLayout.getRowCount(); ++i) {
            final LinearLayout row = getChipRow(i);
            if (row == null) {
                continue;
            }
            for (int j = 0; j < row.getChildCount(); ++j) {
                View child = row.getChildAt(j);
                if (child.getTag() instanceof BoomChip) {
                    BoomChip chip = (BoomChip) child.getTag();
                    chip.setSelected(true);
                }
            }
        }
        mBoomActionHandler.onSelect(0, wordCount - 1);
    }

    /**
     * Auto-select the word that was touched / identified by the initial layout.
     * This is used when a third-party caller provides a character index via
     * {@code EXTRA_SELECTED_CHAR_INDEX} — after layout the touched word index
     * is known, and this method selects it.
     */
    public void selectTouchedWord() {
        final int touchedIndex = mLayout.getTouchedIndex();
        if (touchedIndex < 0 || touchedIndex >= mLayout.getWordCount()) {
            return;
        }
        for (int i = 0; i < mLayout.getRowCount(); ++i) {
            final LinearLayout row = getChipRow(i);
            if (row == null) {
                continue;
            }
            for (int j = 0; j < row.getChildCount(); ++j) {
                View child = row.getChildAt(j);
                if (child.getTag() instanceof BoomChip) {
                    BoomChip chip = (BoomChip) child.getTag();
                    if (chip.index == touchedIndex) {
                        chip.setSelected(true);
                    }
                }
            }
        }
        mBoomActionHandler.onSelect(touchedIndex, touchedIndex);
        // Scroll the touched word to the centre of the viewport
        mScroller.post(new Runnable() {
            @Override
            public void run() {
                final int row = mLayout.getRowForIndex(touchedIndex);
                final View rowView = mBoomConent.getChildAt(row);
                if (rowView == null) {
                    return;
                }
                final int rowCentre = rowView.getTop() + rowView.getHeight() / 2;
                final int viewportCentre = mScroller.getHeight() / 2;
                final int targetScrollY = rowCentre - viewportCentre;
                mScroller.scrollTo(0, Math.max(0, targetScrollY));
            }
        });
    }

    public boolean splitSelectedWordsToChars() {
        if (mBoomActionHandler == null || !mBoomActionHandler.hasSelection()) {
            return false;
        }
        TreeSet<Integer> newSelection = mLayout.splitSelectedWordsToChars(
                new TreeSet<Integer>(mBoomActionHandler.mSelectedId)
        );
        return rebuildWithSelection(newSelection);
    }

    public boolean mergeSelectedWords() {
        if (mBoomActionHandler == null || !mBoomActionHandler.hasSelection()) {
            return false;
        }
        return rebuildWithSelection(mLayout.mergeSelectedWords(
                new TreeSet<Integer>(mBoomActionHandler.mSelectedId)
        ));
    }

    private boolean rebuildWithSelection(TreeSet<Integer> newSelection) {
        if (newSelection == null || newSelection.isEmpty()) {
            return false;
        }
        mBoomActionHandler.clearSelectionStateForRelayout();
        mSavedData = newSelection;
        mBoomConent.removeAllViews();
        initChips(false);
        mBoomConent.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mBoomConent.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                restoreSelectedState();
            }
        });
        return true;
    }

    public void setOnAdjacentRequestListener(OnAdjacentRequestListener listener) {
        mOnAdjacentRequestListener = listener;
        finishAdjacentPull();
    }

    public boolean replaceWords(int[] segment, String text, int targetWordIndex, int charOffset) {
        // Save selection as char ranges before it gets cleared
        Serializable savedSelection = null;
        if (mBoomActionHandler != null && mBoomActionHandler.hasSelection()) {
            savedSelection = captureSelectedState();
        }
        if (mBoomActionHandler != null) {
            mBoomActionHandler.clearSelectionStateForRelayout();
        }
        mSavedData = null;
        mBoomConent.removeAllViews();
        if (!mLayout.layoutWords(segment, text, -1)) {
            finishAdjacentPull();
            return false;
        }
        initChips(false);
        // Restore selection with adjusted char ranges
        if (savedSelection instanceof int[][]) {
            int[][] ranges = (int[][]) savedSelection;
            if (charOffset != 0) {
                for (int[] range : ranges) {
                    range[0] += charOffset;
                    range[1] += charOffset;
                }
            }
            mSavedData = ranges;
            mBoomConent.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mBoomConent.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    restoreSelectedState();
                }
            });
        }
        scrollToWord(targetWordIndex);
        finishAdjacentPull();
        return true;
    }

    public boolean replaceWords(int[] segment, String text, int targetWordIndex) {
        return replaceWords(segment, text, targetWordIndex, 0);
    }

    private void scrollToWord(final int wordIndex) {
        if (wordIndex < 0 || wordIndex >= mLayout.getWordCount()) {
            mScroller.scrollTo(0, 0);
            return;
        }
        mScroller.post(new Runnable() {
            @Override
            public void run() {
                mScroller.scrollTo(0, getRowTop(mLayout.getRowForIndex(wordIndex)));
            }
        });
    }

    public void finishAdjacentPull() {
        mAdjacentLoading = false;
        mScroller.setEdgeDragEnabled(mOnAdjacentRequestListener != null
                && (mOnAdjacentRequestListener.peekAdjacentText("before") != null
                || mOnAdjacentRequestListener.peekAdjacentText("after") != null));
        animateContentOffset(0f);
        hideAdjacentHint(mAdjacentTopHint);
        hideAdjacentHint(mAdjacentBottomHint);
    }

    private void initChips(boolean animate) {
        for (int i = 0; i < mLayout.getRowCount(); ++i) {
            final int start = mLayout.getRowStart(i);
            final int count = mLayout.getColumnCount(i);
            if (mLayout.isGapRow(i)) {
                final int gapHeight = mContext.getResources()
                        .getDimensionPixelSize(R.dimen.chip_paragraph_gap);
                View spacer = new View(mContext);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        gapHeight));
                mBoomConent.addView(spacer);
                continue;
            }
            LinearLayout row = new LinearLayout(mContext);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            for(int j = 0; j < count; ++j) {
                boolean isPunc = mLayout.isPunc(start + j);
                View chipView = LayoutInflater.from(mContext).inflate(
                        isPunc ? R.layout.boom_punc_layout : R.layout.boom_chip_layout, null);
                BoomChip chip = new BoomChip(start + j, chipView);
                chipView.setTag(chip);
                row.addView(chipView);
            }
            mBoomConent.addView(row);
        }
        if (mOnChipsChanged != null) {
            mOnChipsChanged.run();
        }
        notifySelectionChanged();
        mBoomConent.requestLayout();
        if (animate) {
            mBoomConent.getViewTreeObserver().addOnGlobalLayoutListener(mDoBoomAnimation);
        }
    }

    void restoreSelectionNow(TreeSet<Integer> selection) {
        resetChips();
        mSavedData = selection;
        if (!restoreSelectedState()) mBoomActionHandler.clearSelectionStateForRelayout();
        mSavedData = null;
    }

    private boolean restoreSelectedState() {
        if (mSavedData instanceof int[][]) {
            // Char-range based selection (stable across rotation)
            int[][] ranges = (int[][]) mSavedData;
            TreeSet<Integer> newWordSet = new TreeSet<Integer>();
            for (int[] range : ranges) {
                int charStart = range[0];
                int charEnd = range[1];
                for (int i = 0; i < mLayout.getWordCount(); i++) {
                    int wordStart = mLayout.getWordStart(i);
                    int wordEnd = mLayout.getWordEnd(i);
                    if (wordStart < charEnd && wordEnd > charStart) {
                        newWordSet.add(i);
                    }
                }
            }
            if (!newWordSet.isEmpty()) {
                for (int i = 0; i < mLayout.getRowCount(); ++i) {
                    final LinearLayout row = getChipRow(i);
                    if (row == null) continue;
                    for (int j = 0; j < row.getChildCount(); ++j) {
                        View child = row.getChildAt(j);
                        if (child.getTag() instanceof BoomChip) {
                            BoomChip chip = (BoomChip) child.getTag();
                            if (newWordSet.contains(chip.index)) {
                                chip.setSelected(true);
                            }
                        }
                    }
                }
                mBoomActionHandler.onSelect(newWordSet);
                return true;
            }
        } else if (mSavedData instanceof TreeSet) {
            // Legacy: word-index based (used by splitSelectedWordsToChars)
            TreeSet<Integer> set = (TreeSet<Integer>) mSavedData;
            if (set.size() > 0) {
                for (int i = 0; i < mLayout.getRowCount(); ++i) {
                    final LinearLayout row = getChipRow(i);
                    if (row == null) {
                        continue;
                    }
                    for (int j = 0; j < row.getChildCount(); ++j) {
                        View child = row.getChildAt(j);
                        if (child.getTag() instanceof BoomChip) {
                            BoomChip chip = (BoomChip) child.getTag();
                            if (set.contains(new Integer(chip.index))) {
                                chip.setSelected(true);
                            }
                        }
                    }
                }
                mBoomActionHandler.onSelect(set);
                return true;
            }
        }
        return false;
    }

    private LinearLayout getChipRow(int index) {
        final View child = mBoomConent.getChildAt(index);
        return child instanceof LinearLayout ? (LinearLayout) child : null;
    }

    private void updateAdjacentPull(float offset) {
        if (mAdjacentLoading) {
            return;
        }
        mAdjacentOffset = offset;
        applyContentOffset(offset);
        if (offset > 0f) {
            showAdjacentHint(mAdjacentTopHint, "before", offset);
            hideAdjacentHint(mAdjacentBottomHint);
        } else if (offset < 0f) {
            showAdjacentHint(mAdjacentBottomHint, "after", -offset);
            hideAdjacentHint(mAdjacentTopHint);
        } else {
            hideAdjacentHint(mAdjacentTopHint);
            hideAdjacentHint(mAdjacentBottomHint);
        }
    }

    private void releaseAdjacentPull(float offset, boolean triggered) {
        if (mAdjacentLoading) {
            return;
        }
        final String direction = offset > 0f ? "before" : offset < 0f ? "after" : null;
        final String previewText = direction == null || mOnAdjacentRequestListener == null
                ? null : mOnAdjacentRequestListener.peekAdjacentText(direction);
        if (!triggered || direction == null || previewText == null) {
            finishAdjacentPull();
            return;
        }
        mAdjacentLoading = true;
        mScroller.setEdgeDragEnabled(false);
        animateContentOffset(clampHoldOffset(offset));
        if (mOnAdjacentRequestListener != null) {
            mOnAdjacentRequestListener.onAdjacentRequest(direction);
        }
    }

    private void showAdjacentHint(TextView view, String direction, float distance) {
        final String preview = mOnAdjacentRequestListener == null
                ? null : mOnAdjacentRequestListener.peekAdjacentText(direction);
        if (preview == null) {
            hideAdjacentHint(view);
            return;
        }
        view.setVisibility(View.VISIBLE);
        view.setText(getHintTitle(direction));
        float alpha = Math.min(1f, distance / getTriggerDistance());
        view.setAlpha(alpha);
    }

    private void hideAdjacentHint(TextView view) {
        view.setAlpha(0f);
        view.setVisibility(View.GONE);
    }

    private String getHintTitle(String direction) {
        return "before".equals(direction)
                ? mContext.getString(R.string.bigbang_pull_previous)
                : mContext.getString(R.string.bigbang_pull_next);
    }

    private float getTriggerDistance() {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                88f,
                mContext.getResources().getDisplayMetrics()
        );
    }

    private float clampHoldOffset(float offset) {
        float hold = getTriggerDistance();
        return offset > 0f ? hold : -hold;
    }

    private void applyContentOffset(float offset) {
        mScroller.setTranslationY(0f);
        mBoomTable.setTranslationY(offset);
    }

    private void animateContentOffset(float offset) {
        mAdjacentOffset = offset;
        mScroller.animate().cancel();
        mScroller.setTranslationY(0f);
        mBoomTable.animate()
                .translationY(offset)
                .setDuration(180L)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (mAdjacentOffset == 0f && !mAdjacentLoading) {
                            hideAdjacentHint(mAdjacentTopHint);
                            hideAdjacentHint(mAdjacentBottomHint);
                        }
                    }
                })
                .start();
    }

    public class BoomChip {
        int index;
        TextView word;
        boolean punc;


        public BoomChip(final int id, View chipView) {
            index = id;
            punc = mLayout.isPunc(id);
            if (punc) {
                word = (TextView) chipView.findViewById(R.id.punc);
            } else {
                word = (TextView) chipView.findViewById(R.id.word);
            }
            word.setText(mLayout.getWord(id));
            word.setTextSize(TypedValue.COMPLEX_UNIT_SP, mTextSizeSp);
        }

        public void setSelected(boolean selected) {
            word.setSelected(selected);
        }
    }
}

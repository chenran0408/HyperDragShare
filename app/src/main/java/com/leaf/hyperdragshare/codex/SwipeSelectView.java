package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

import com.leaf.hyperdragshare.codex.BoomChipPage.BoomChip;

import java.util.TreeSet;

public class SwipeSelectView extends LinearLayout {

    private final long AUTO_SCROLL_DELAY = 25;
    private int mSelStart;
    private int mSelEnd;
    private int mStartBound;
    private int mEndBound;
    private int mLastTouchIndex;
    private int mStartIndex;
    private boolean mIsSelected;
    private BoomChipPage mBoomPage;
    private enum Gesture { IDLE, PENDING, SELECTING, SCROLLING, DRAGGING }
    private Gesture mGesture = Gesture.IDLE;
    private int mAutoScrollTopInset;
    private int mAutoScrollBottomInset;
    private int mAutoScrollVelocity;

    private float mDownX;
    private float mDownY;
    private float mLastRawX;
    private float mLastRawY;
    private TreeSet<Integer> mSelectionBeforeGesture = new TreeSet<>();
    private final Runnable mStartDrag = () -> {
        if (mGesture != Gesture.PENDING || mStartIndex < 0) return;
        if (mIsSelected) {
            performSelect(true);
        }
        String text = mBoomPage.mBoomActionHandler.getSelectedText();
        if (TextUtils.isEmpty(text)) return;
        // startDragAndDrop can synchronously cancel this View's touch stream.
        mGesture = Gesture.DRAGGING;
        mAutoScrollVelocity = 0;
        removeCallbacks(this.mAutoScroll);
        if (!mBoomPage.mActions.dragText(this, text)) {
            mGesture = Gesture.IDLE;
            mBoomPage.restoreSelectionNow(mSelectionBeforeGesture);
        }
    };

    private Runnable mAutoScroll = new Runnable() {
        @Override
        public void run() {
            if (mGesture != Gesture.SELECTING || mAutoScrollVelocity == 0) return;
            int oldY = mBoomPage.mScroller.getScrollY();
            mBoomPage.mScroller.scrollBy(0, mAutoScrollVelocity);
            updateSelectionAtPointer();
            if (mBoomPage.mScroller.getScrollY() != oldY) postDelayed(mAutoScroll, AUTO_SCROLL_DELAY);
            else mAutoScrollVelocity = 0;
        }
    };

    public SwipeSelectView(Context context) {
        this(context, null);
    }

    public SwipeSelectView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setBoomPage(BoomChipPage boomPage) {
        mBoomPage = boomPage;
        mAutoScrollVelocity = 0;
        final Resources res = getResources();
        mAutoScrollTopInset = res.getDimensionPixelSize(R.dimen.auto_scroll_top);
        mAutoScrollBottomInset = res.getDimensionPixelSize(R.dimen.auto_scroll_bottom);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final float x = ev.getX();
        final float y = ev.getY();
        mLastRawX = ev.getRawX();
        mLastRawY = ev.getRawY();
        if (ev.getPointerCount() > 1) {
            if (mGesture == Gesture.PENDING || mGesture == Gesture.SELECTING) {
                mBoomPage.restoreSelectionNow(mSelectionBeforeGesture);
            }
            finishTouch();
            return true;
        }
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mGesture = Gesture.PENDING;
                mDownX = x;
                mDownY = y;
                mSelectionBeforeGesture = new TreeSet<>(mBoomPage.mBoomActionHandler.mSelectedId);
                BoomChip touchedChip = findChip(x, y, false);
                initSelection(touchedChip == null ? -1 : touchedChip.index);
                if (touchedChip != null) {
                    mIsSelected = !touchedChip.word.isSelected();
                    postDelayed(mStartDrag, ViewConfiguration.getLongPressTimeout());
                }
                mAutoScrollVelocity = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                if (mGesture == Gesture.PENDING) {
                    float dx = Math.abs(x - mDownX);
                    float dy = Math.abs(y - mDownY);
                    int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                    if (Math.max(dx, dy) <= slop) break;
                    removeCallbacks(mStartDrag);
                    if (dy >= dx || mStartIndex < 0) {
                        mGesture = Gesture.SCROLLING;
                        requestDisallowInterceptTouchEvent(false);
                        break;
                    }
                    mGesture = Gesture.SELECTING;
                    requestDisallowInterceptTouchEvent(true);
                }
                if (mGesture == Gesture.SELECTING) {
                    updateSelection(findChip(x, y, true));
                    scrollIfNeeded((int) mLastRawY);
                }
                break;
            case MotionEvent.ACTION_UP:
                if ((mGesture == Gesture.PENDING || mGesture == Gesture.SELECTING) && mSelStart >= 0) {
                    performSelect(mIsSelected);
                    if (BigBangSettings.isSelectionHapticEnabled(getContext())) {
                        performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE);
                    }
                }
                finishTouch();
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mGesture == Gesture.PENDING || mGesture == Gesture.SELECTING) {
                    mBoomPage.restoreSelectionNow(mSelectionBeforeGesture);
                }
                finishTouch();
                break;
            default:
                break;
        }
        return mSelStart >= 0 || super.onTouchEvent(ev);
    }

    private void updateSelection(BoomChip chip) {
        if (chip == null || chip.index == mLastTouchIndex) return;
        mLastTouchIndex = chip.index;
        mStartBound = Math.min(mStartBound, chip.index);
        mEndBound = Math.max(mEndBound, chip.index);
        mSelStart = Math.min(mStartIndex, chip.index);
        mSelEnd = Math.max(mStartIndex, chip.index);
        performSelect(mIsSelected);
    }

    private void updateSelectionAtPointer() {
        int[] location = new int[2];
        mBoomPage.mScroller.getLocationOnScreen(location);
        float screenY = Math.max(location[1] + 1,
                Math.min(mLastRawY, location[1] + mBoomPage.mScroller.getHeight() - 1));
        getLocationOnScreen(location);
        updateSelection(findChip(mLastRawX - location[0], screenY - location[1], true));
    }

    private void scrollIfNeeded(int screenY) {
        final int[] scrollerLocation = new int[2];
        mBoomPage.mScroller.getLocationOnScreen(scrollerLocation);
        final int scrollerTop = scrollerLocation[1];
        final int scrollerBottom = scrollerTop + mBoomPage.mScroller.getHeight();
        final int autoScrollTop = Math.min(
                scrollerTop + mAutoScrollTopInset,
                scrollerTop + mBoomPage.mScroller.getHeight() / 2
        );
        final int autoScrollBottom = Math.max(
                scrollerBottom - mAutoScrollBottomInset,
                scrollerTop + mBoomPage.mScroller.getHeight() / 2
        );
        int velocity = screenY < autoScrollTop ? (screenY - autoScrollTop) / 2
                : screenY > autoScrollBottom ? (screenY - autoScrollBottom) / 2 : 0;
        int maxStep = Math.max(1, Math.round(12 * getResources().getDisplayMetrics().density));
        velocity = Math.max(-maxStep, Math.min(velocity, maxStep));
        if (velocity != 0 && !mBoomPage.mScroller.canScrollVertically(Integer.signum(velocity))) velocity = 0;
        if (velocity != mAutoScrollVelocity) {
            removeCallbacks(mAutoScroll);
            if (velocity != 0) postDelayed(mAutoScroll, AUTO_SCROLL_DELAY);
        }
        mAutoScrollVelocity = velocity;
    }

    private void initSelection(int value) {
        mStartBound = value;
        mEndBound = value;
        mSelStart = value;
        mSelEnd = value;
        mStartIndex = value;
        mLastTouchIndex = -1;
    }

    private void finishTouch() {
        mGesture = Gesture.IDLE;
        requestDisallowInterceptTouchEvent(false);
        mAutoScrollVelocity = 0;
        removeCallbacks(mStartDrag);
        removeCallbacks(mAutoScroll);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(mStartDrag);
        finishTouch();
        super.onDetachedFromWindow();
    }

    private void performSelect(boolean isSelected) {
        int startRow = mBoomPage.mLayout.getRowForIndex(mStartBound);
        int endRow = mBoomPage.mLayout.getRowForIndex(mEndBound);
        for (int i = startRow; i <= endRow; ++i) {
            final LinearLayout row = getChipRow(i);
            if (row == null) {
                continue;
            }
            for (int j = 0; j < row.getChildCount(); ++j) {
                View child = row.getChildAt(j);
                if (child.getTag() instanceof BoomChip) {
                    BoomChip chip = (BoomChip) child.getTag();
                    final int index = chip.index;
                    if (index < mStartBound) continue;
                    if (index > mEndBound) break;
                    if (index >= mSelStart && index <= mSelEnd) {
                        chip.setSelected(isSelected);
                    } else {
                        chip.setSelected(mSelectionBeforeGesture.contains(index));
                    }
                }
            }
        }
        TreeSet<Integer> selected = new TreeSet<>(mSelectionBeforeGesture);
        for (int index = mSelStart; index <= mSelEnd; index++) {
            if (isSelected) selected.add(index);
            else selected.remove(index);
        }
        mBoomPage.mBoomActionHandler.onSelect(selected);
    }

    private boolean isPointInsideView(float x, float y, View view) {
        final Rect hitRect = new Rect();
        view.getHitRect(hitRect);
        return hitRect.contains((int) x, (int) y);
    }

    private BoomChip findChip(float x, float y, boolean isSwiping) {
        for (int i = 0; i < getChildCount(); ++i) {
            final LinearLayout row = getChipRow(i);
            if (row == null) {
                continue;
            }
            if (isPointInsideView(x, y, row)) {
                final float offsetX = row.getScrollX() - row.getLeft();
                final float offsetY = row.getScrollY() - row.getTop();
                float newX = x + offsetX;
                float newY = y + offsetY - row.getTranslationY();
                for (int j = row.getChildCount() - 1; j >= 0; --j) {
                    View child = row.getChildAt(j);
                    if (isSwiping && (newX > child.getX() || j == 0)) {
                        if (child.getTag() instanceof BoomChip) {
                            BoomChip chip = (BoomChip) child.getTag();
                            return chip;
                        }
                        return null;
                    }
                    if (isPointInsideView(newX, newY, child)) {
                        if (child.getTag() instanceof BoomChip) {
                            BoomChip chip = (BoomChip) child.getTag();
                            return chip;
                        }
                        return null;
                    }
                }
                return null;
            }
        }
        return null;
    }

    private LinearLayout getChipRow(int index) {
        final View child = getChildAt(index);
        return child instanceof LinearLayout ? (LinearLayout) child : null;
    }
}

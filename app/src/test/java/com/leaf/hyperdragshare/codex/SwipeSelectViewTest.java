package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.*;

import android.content.Context;
import android.app.Activity;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import java.time.Duration;
import java.util.TreeSet;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
public class SwipeSelectViewTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private ActivityController<Activity> activity;
    private View root;
    private BoomChipPage page;
    private SwipeSelectView words;
    private String dragged;
    private boolean accepted = true;
    private boolean cancelDuringDrag;

    private void open() {
        activity = Robolectric.buildActivity(Activity.class).setup();
        root = LayoutInflater.from(context).inflate(R.layout.boom_activity_layout, null, false);
        page = new BoomChipPage(context, root, false, new BoomChipPage.Actions() {
            public void dismiss() { }
            public void copy(String text) { }
            public void search(String text, int type) { }
            public void share(String text) { }
            public boolean dragText(View source, String text) {
                dragged = text;
                if (cancelDuringDrag) event(MotionEvent.ACTION_CANCEL, 0, 0);
                return accepted;
            }
        });
        assertTrue(page.initWords(new int[]{0, 0, 1, 1, 2, 2, -1}, "甲乙丙", -1, -1, -1));
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 800);
        words = root.findViewById(R.id.boom_content);
        activity.get().setContentView(root);
    }

    @After public void close() {
        activity.pause().stop().destroy();
    }

    private void touch(int action, int chipIndex) {
        LinearLayout row = (LinearLayout) words.getChildAt(0);
        View chip = row.getChildAt(chipIndex);
        event(action, row.getLeft() + chip.getLeft() + chip.getWidth() / 2f,
                row.getTop() + chip.getHeight() / 2f);
    }

    private void event(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        words.onTouchEvent(event);
        event.recycle();
    }

    @Test public void selectedWordsDragAtScrollEdgeAndSurviveSynchronousCancel() {
        open();
        page.restoreSelectionNow(new TreeSet<>(List.of(0, 1)));
        assertFalse(page.mScroller.canScrollVertically(-1));
        cancelDuringDrag = true;
        touch(MotionEvent.ACTION_DOWN, 0);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout()));
        assertEquals("甲乙", dragged);
        assertEquals(new TreeSet<>(List.of(0, 1)), page.mBoomActionHandler.mSelectedId);
    }

    @Test public void selectedCharacterCountMatchesSelectedTextAcrossEmojiAndGaps() {
        open();
        page.prepareForReinit();
        String text = "😀 a b";
        assertTrue(page.initWords(new int[]{0, 1, 3, 3, 5, 5, -1}, text, -1, -1, -1));

        TreeSet<Integer> selection = new TreeSet<>(List.of(0, 1));
        page.restoreSelectionNow(selection);
        String selected = page.selectedTextOrOriginal();
        assertEquals("😀 a", selected);
        assertEquals(selected.codePointCount(0, selected.length()), page.selectedCharacterCount());

        selection = new TreeSet<>(List.of(0, 2));
        page.restoreSelectionNow(selection);
        selected = page.selectedTextOrOriginal();
        assertEquals("😀b", selected);
        assertEquals(selected.codePointCount(0, selected.length()), page.selectedCharacterCount());

        page.restoreSelectionNow(new TreeSet<>(List.of(0, 1, 2)));
        selected = page.selectedTextOrOriginal();
        assertEquals(text, selected);
        assertEquals(selected.codePointCount(0, selected.length()), page.selectedCharacterCount());
    }

    @Test public void rejectedDragRestoresOriginalSelectionRatherThanAddingHeldWord() {
        open();
        page.restoreSelectionNow(new TreeSet<>(List.of(0)));
        accepted = false;
        touch(MotionEvent.ACTION_DOWN, 1);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout()));
        assertEquals("甲乙", dragged);
        assertEquals(new TreeSet<>(List.of(0)), page.mBoomActionHandler.mSelectedId);
        touch(MotionEvent.ACTION_UP, 1);
        assertEquals(new TreeSet<>(List.of(0)), page.mBoomActionHandler.mSelectedId);
    }

    @Test public void cancelledSwipePreservesOldSelectionAndNeverStartsDrag() {
        open();
        page.restoreSelectionNow(new TreeSet<>(List.of(2)));
        touch(MotionEvent.ACTION_DOWN, 0);
        touch(MotionEvent.ACTION_MOVE, 1);
        touch(MotionEvent.ACTION_CANCEL, 1);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout()));
        assertNull(dragged);
        assertEquals(new TreeSet<>(List.of(2)), page.mBoomActionHandler.mSelectedId);
    }
    @Test public void verticalMotionDoesNotSelectWordsOrStartGlobalDrag() {
        open();
        page.restoreSelectionNow(new TreeSet<>(List.of(2)));
        LinearLayout row = (LinearLayout) words.getChildAt(0);
        View chip = row.getChildAt(0);
        float x = row.getLeft() + chip.getLeft() + chip.getWidth() / 2f;
        float y = row.getTop() + chip.getHeight() / 2f;
        event(MotionEvent.ACTION_DOWN, x, y);
        event(MotionEvent.ACTION_MOVE, x, y + 100);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout()));
        event(MotionEvent.ACTION_UP, x, y + 100);
        assertNull(dragged);
        assertEquals(new TreeSet<>(List.of(2)), page.mBoomActionHandler.mSelectedId);
    }

    @Test public void shrinkingSelectionKeepsWordsSelectedBeforeThisGesture() {
        open();
        page.restoreSelectionNow(new TreeSet<>(List.of(2)));
        touch(MotionEvent.ACTION_DOWN, 0);
        touch(MotionEvent.ACTION_MOVE, 2);
        touch(MotionEvent.ACTION_MOVE, 1);
        touch(MotionEvent.ACTION_UP, 1);
        assertEquals(new TreeSet<>(List.of(0, 1, 2)), page.mBoomActionHandler.mSelectedId);
    }

    @Test public void shrinkingDeselectionDoesNotSelectPreviouslyUnselectedWords() {
        open();
        page.restoreSelectionNow(new TreeSet<>(List.of(0)));
        touch(MotionEvent.ACTION_DOWN, 0);
        touch(MotionEvent.ACTION_MOVE, 2);
        touch(MotionEvent.ACTION_MOVE, 1);
        touch(MotionEvent.ACTION_UP, 1);
        assertTrue(page.mBoomActionHandler.mSelectedId.isEmpty());
    }

    @Test public void slidingSelectionKeepsRowsStillUntilGestureEnds() {
        open();
        page.prepareForReinit();
        int wordCount = 80;
        int[] segments = new int[wordCount * 2 + 1];
        StringBuilder text = new StringBuilder(wordCount);
        for (int i = 0; i < wordCount; i++) {
            segments[i * 2] = segments[i * 2 + 1] = i;
            text.append('甲');
        }
        segments[segments.length - 1] = -1;
        assertTrue(page.initWords(segments, text.toString(), -1, -1, -1));
        root.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 480, 800);
        assertTrue(words.getChildCount() >= 3);

        LinearLayout firstRow = (LinearLayout) words.getChildAt(0);
        View firstChip = firstRow.getChildAt(0);
        LinearLayout secondRow = (LinearLayout) words.getChildAt(1);
        View lastSecondRowChip = secondRow.getChildAt(secondRow.getChildCount() - 1);
        float startX = firstRow.getLeft() + firstChip.getLeft() + firstChip.getWidth() / 2f;
        float startY = firstRow.getTop() + firstChip.getTop() + firstChip.getHeight() / 2f;
        float endX = secondRow.getLeft() + lastSecondRowChip.getLeft()
                + lastSecondRowChip.getWidth() / 2f;
        float endY = secondRow.getTop() + lastSecondRowChip.getTop()
                + lastSecondRowChip.getHeight() / 2f;
        LinearLayout rowBelowSelection = (LinearLayout) words.getChildAt(2);

        event(MotionEvent.ACTION_DOWN, startX, startY);
        int paddingBeforeSelection = words.getPaddingBottom();
        assertTrue(words.isSelectionGestureActive());
        event(MotionEvent.ACTION_MOVE, endX, endY);
        assertTrue(words.isSelectionGestureActive());
        assertEquals(0f, rowBelowSelection.getTranslationY(), 0f);
        assertEquals(paddingBeforeSelection, words.getPaddingBottom());

        event(MotionEvent.ACTION_UP, endX, endY);
        assertFalse(words.isSelectionGestureActive());
        assertEquals(paddingBeforeSelection
                + context.getResources().getDimensionPixelSize(R.dimen.chip_row_move_down_offset),
                words.getPaddingBottom());
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(500));
        assertEquals(context.getResources().getDimensionPixelSize(R.dimen.chip_row_move_down_offset),
                rowBelowSelection.getTranslationY(), 0.1f);
    }

    @Test public void horizontalIntentIsNotStolenByTheVerticalScrollView() {
        open();
        page.mScroller.setEdgeDragEnabled(false);
        int slop = ViewConfiguration.get(context).getScaledTouchSlop();
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 20, 20, 0);
        MotionEvent move = MotionEvent.obtain(now, now + 16, MotionEvent.ACTION_MOVE,
                20 + slop * 4, 20 + slop * 2, 0);
        page.mScroller.onInterceptTouchEvent(down);
        assertFalse(page.mScroller.onInterceptTouchEvent(move));
        down.recycle();
        move.recycle();
    }

    @Test public void stationaryFingerAtEdgeKeepsSelectingAsContentScrolls() {
        open();
        int count = 300;
        int[] segments = new int[count * 2 + 1];
        for (int i = 0; i < count; i++) segments[i * 2] = segments[i * 2 + 1] = i;
        segments[segments.length - 1] = -1;
        assertTrue(page.initWords(segments, "甲".repeat(count), -1, -1, -1));
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 400);
        assertTrue(page.mScroller.canScrollVertically(1));
        touch(MotionEvent.ACTION_DOWN, 0);
        touch(MotionEvent.ACTION_MOVE, 1);

        int[] scrollLocation = new int[2];
        int[] wordsLocation = new int[2];
        page.mScroller.getLocationOnScreen(scrollLocation);
        words.getLocationOnScreen(wordsLocation);
        LinearLayout row = (LinearLayout) words.getChildAt(0);
        View chip = row.getChildAt(1);
        float screenX = wordsLocation[0] + row.getLeft() + chip.getLeft() + chip.getWidth() / 2f;
        float screenY = scrollLocation[1] + page.mScroller.getHeight() - 2;
        screenEvent(MotionEvent.ACTION_MOVE, screenX, screenY);
        int lastBeforeScroll = page.mBoomActionHandler.mSelectedId.last();
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(250));
        assertTrue(page.mScroller.getScrollY() > 0);
        assertTrue(page.mBoomActionHandler.mSelectedId.last() > lastBeforeScroll);
        screenEvent(MotionEvent.ACTION_UP, screenX, screenY);
        int stoppedAt = page.mScroller.getScrollY();
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(100));
        assertEquals(stoppedAt, page.mScroller.getScrollY());
    }

    private void screenEvent(int action, float screenX, float screenY) {
        int[] location = new int[2];
        words.getLocationOnScreen(location);
        long now = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, screenX, screenY, 0);
        event.offsetLocation(-location[0], -location[1]);
        words.onTouchEvent(event);
        event.recycle();
    }

    @Test public void cancelledEdgePullNeverConfirmsAdjacentInsertion() {
        open();
        boolean[] released = {false};
        boolean[] appended = {false};
        page.mScroller.setOnEdgeDragListener(new CustomScrollView.OnEdgeDragListener() {
            public void onEdgeDrag(float offset) { }
            public void onEdgeDragRelease(float offset, boolean triggered) {
                released[0] = true;
                appended[0] = triggered;
            }
        });
        long now = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_CANCEL}) {
            MotionEvent event = MotionEvent.obtain(now, now, action, 0,
                    action == MotionEvent.ACTION_DOWN ? 0 : 1000, 0);
            page.mScroller.onTouchEvent(event);
            event.recycle();
        }
        assertTrue(released[0]);
        assertFalse(appended[0]);
    }

}

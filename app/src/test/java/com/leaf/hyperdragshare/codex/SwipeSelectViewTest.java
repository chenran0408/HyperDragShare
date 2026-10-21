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

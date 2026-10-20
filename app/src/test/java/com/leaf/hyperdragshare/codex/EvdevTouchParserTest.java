package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;

import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class EvdevTouchParserTest {
    @Test
    public void currentFrameAxesSurviveLaterTrackingAndButtonEdges() {
        List<Frame> frames = new ArrayList<>();
        List<Integer> widths = new ArrayList<>();
        EvdevTouchParser parser = new EvdevTouchParser((action, x, y, major, minor, widthMajor) -> {
            frames.add(new Frame(action, x, y, major, minor));
            widths.add(widthMajor);
        });
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_SLOT, 0);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_POSITION_X, 10);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_POSITION_Y, 20);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_TOUCH_MAJOR, 35);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_TOUCH_MINOR, 22);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_WIDTH_MAJOR, 40);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_TRACKING_ID, 42);
        parser.consume(EvdevTouchParser.EV_KEY, EvdevTouchParser.BTN_TOUCH, 1);
        parser.consume(EvdevTouchParser.EV_SYN, EvdevTouchParser.SYN_REPORT, 0);

        assertEquals(Arrays.asList(new Frame(MotionEvent.ACTION_DOWN, 10, 20, 35, 22)), frames);
        assertEquals(Arrays.asList(40), widths);
    }

    @Test
    public void widthOnlyFramesAreIndependentOfTouchMinorAndResetForNextContact() {
        List<Integer> widths = new ArrayList<>();
        List<Integer> minors = new ArrayList<>();
        EvdevTouchParser parser = new EvdevTouchParser((action, x, y, major, minor, widthMajor) -> {
            widths.add(widthMajor);
            minors.add(minor);
        });
        pointerDown(parser, 0, 42, 10, 20);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_WIDTH_MAJOR, 40);
        parser.consume(EvdevTouchParser.EV_SYN, EvdevTouchParser.SYN_REPORT, 0);
        pointerUp(parser, 0);
        pointerDown(parser, 0, 43, 10, 20);
        assertEquals(Arrays.asList(0, 40, 40, 0), widths);
        assertEquals(Arrays.asList(0, 0, 0, 0), minors);
    }

    @Test
    public void emitsStableDownMoveAndUp() {
        List<Frame> frames = new ArrayList<>();
        EvdevTouchParser parser = new EvdevTouchParser((action, x, y, major, minor, widthMajor) ->
                frames.add(new Frame(action, x, y, major, minor)));

        pointerDown(parser, 0, 42, 10, 20, 18, 9);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_POSITION_X, 12);
        parser.consume(EvdevTouchParser.EV_SYN, EvdevTouchParser.SYN_REPORT, 0);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_TRACKING_ID, -1);
        parser.consume(EvdevTouchParser.EV_SYN, EvdevTouchParser.SYN_REPORT, 0);

        assertEquals(Arrays.asList(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP), actions(frames));
        assertEquals(new Frame(MotionEvent.ACTION_DOWN, 10, 20, 18, 9), frames.get(0));
        assertEquals(new Frame(MotionEvent.ACTION_MOVE, 12, 20, 18, 9), frames.get(1));
        assertEquals(new Frame(MotionEvent.ACTION_UP, 12, 20, 18, 9), frames.get(2));
    }

    @Test
    public void touchSizeChangeEmitsMoveWithoutCoordinateChange() {
        List<Frame> frames = new ArrayList<>();
        EvdevTouchParser parser = new EvdevTouchParser((action, x, y, major, minor, widthMajor) ->
                frames.add(new Frame(action, x, y, major, minor)));

        pointerDown(parser, 0, 42, 10, 20, 12, 7);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_TOUCH_MAJOR, 30);
        parser.consume(EvdevTouchParser.EV_SYN, EvdevTouchParser.SYN_REPORT, 0);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_TOUCH_MINOR, 15);
        parser.consume(EvdevTouchParser.EV_SYN, EvdevTouchParser.SYN_REPORT, 0);

        assertEquals(Arrays.asList(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_MOVE), actions(frames));
        assertEquals(new Frame(MotionEvent.ACTION_MOVE, 10, 20, 30, 7), frames.get(1));
        assertEquals(new Frame(MotionEvent.ACTION_MOVE, 10, 20, 30, 15), frames.get(2));
    }

    @Test
    public void newTouchStartsWithZeroSizeAfterUp() {
        List<Frame> frames = new ArrayList<>();
        EvdevTouchParser parser = new EvdevTouchParser((action, x, y, major, minor, widthMajor) ->
                frames.add(new Frame(action, x, y, major, minor)));

        pointerDown(parser, 0, 42, 10, 20, 35, 22);
        pointerUp(parser, 0);
        pointerDown(parser, 0, 43, 30, 40);

        assertEquals(new Frame(MotionEvent.ACTION_UP, 10, 20, 35, 22), frames.get(1));
        assertEquals(new Frame(MotionEvent.ACTION_DOWN, 30, 40, 0, 0), frames.get(2));
    }

    @Test
    public void cancelClearsTouchSizeForNextTouch() {
        List<Frame> frames = new ArrayList<>();
        EvdevTouchParser parser = new EvdevTouchParser((action, x, y, major, minor, widthMajor) ->
                frames.add(new Frame(action, x, y, major, minor)));

        pointerDown(parser, 0, 42, 10, 20, 35, 22);
        parser.cancel();
        pointerDown(parser, 0, 43, 30, 40);

        assertEquals(new Frame(MotionEvent.ACTION_CANCEL, 10, 20, 35, 22), frames.get(1));
        assertEquals(new Frame(MotionEvent.ACTION_DOWN, 30, 40, 0, 0), frames.get(2));
    }

    @Test
    public void secondPointerCancelsInsteadOfFinishingShare() {
        List<Frame> frames = new ArrayList<>();
        EvdevTouchParser parser = new EvdevTouchParser((action, x, y, major, minor, widthMajor) ->
                frames.add(new Frame(action, x, y, major, minor)));

        pointerDown(parser, 0, 42, 10, 20, 18, 9);
        pointerDown(parser, 1, 43, 30, 40, 60, 42);

        assertEquals(Arrays.asList(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_CANCEL), actions(frames));
        assertEquals(new Frame(MotionEvent.ACTION_CANCEL, 10, 20, 18, 9), frames.get(1));
    }

    private static List<Integer> actions(List<Frame> frames) {
        List<Integer> actions = new ArrayList<>(frames.size());
        for (Frame frame : frames) {
            actions.add(frame.action);
        }
        return actions;
    }

    private static void pointerDown(
            EvdevTouchParser parser,
            int slot,
            int trackingId,
            int x,
            int y) {
        pointerDown(parser, slot, trackingId, x, y, 0, 0);
    }

    private static void pointerDown(
            EvdevTouchParser parser,
            int slot,
            int trackingId,
            int x,
            int y,
            int touchMajor,
            int touchMinor) {
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_SLOT, slot);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_TRACKING_ID, trackingId);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_POSITION_X, x);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_POSITION_Y, y);
        if (touchMajor != 0) {
            parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_TOUCH_MAJOR, touchMajor);
        }
        if (touchMinor != 0) {
            parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_TOUCH_MINOR, touchMinor);
        }
        parser.consume(EvdevTouchParser.EV_SYN, EvdevTouchParser.SYN_REPORT, 0);
    }

    private static void pointerUp(EvdevTouchParser parser, int slot) {
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_SLOT, slot);
        parser.consume(EvdevTouchParser.EV_ABS, EvdevTouchParser.ABS_MT_TRACKING_ID, -1);
        parser.consume(EvdevTouchParser.EV_SYN, EvdevTouchParser.SYN_REPORT, 0);
    }

    private static final class Frame {
        final int action;
        final float x;
        final float y;
        final int major;
        final int minor;

        Frame(int action, float x, float y, int major, int minor) {
            this.action = action;
            this.x = x;
            this.y = y;
            this.major = major;
            this.minor = minor;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Frame)) {
                return false;
            }
            Frame frame = (Frame) other;
            return action == frame.action
                    && Float.compare(x, frame.x) == 0
                    && Float.compare(y, frame.y) == 0
                    && major == frame.major
                    && minor == frame.minor;
        }

        @Override
        public int hashCode() {
            int result = action;
            result = 31 * result + Float.floatToIntBits(x);
            result = 31 * result + Float.floatToIntBits(y);
            result = 31 * result + major;
            result = 31 * result + minor;
            return result;
        }

        @Override
        public String toString() {
            return "Frame{" + action + "," + x + "," + y + "," + major + "," + minor + "}";
        }
    }
}

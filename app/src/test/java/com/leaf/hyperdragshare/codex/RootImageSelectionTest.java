package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.graphics.Rect;
import android.app.UiAutomation;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAccessibilityWindowInfo;
import org.robolectric.shadows.ShadowAccessibilityNodeInfo;
import org.robolectric.shadows.ShadowUiAutomation;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, shadows = RootImageSelectionTest.AutomationShadow.class)
@LooperMode(LooperMode.Mode.PAUSED)
public final class RootImageSelectionTest {
    private final AccessibilityNodeClassifier classifier = new AccessibilityNodeClassifier(1f, 1080, 2400);

    @Test public void singleRecordingLabelOnControlBlocksAutomaticPress() {
        AccessibilityNodeSnapshot voice = node("android.widget.Button", "reader")
                .text("按住 说话").clickable(true).build();
        assertTrue(RootNodeCaptureMain.holdControlAtPoint(List.of(voice), 30, 30, "reader"));
    }

    @Test public void recordingDescriptionAndClickableParentAreBothRecognized() {
        AccessibilityNodeSnapshot control = node("android.view.ViewGroup", "reader")
                .clickable(true).leaf(false).depth(0).build();
        AccessibilityNodeSnapshot label = node("android.widget.TextView", "reader")
                .bounds(new Rect(20, 20, 200, 100)).contentDescription("Hold to talk")
                .depth(1).build();
        assertTrue(RootNodeCaptureMain.holdControlAtPoint(List.of(control, label), 30, 30, "reader"));
        assertFalse(RootNodeCaptureMain.holdControlAtPoint(List.of(control, label), 250, 150, "reader"));
    }

    @Test public void holdNormalizationRetainsCaseWhitespaceAndNullSemantics() {
        for (String prompt : List.of(" HOLD\tTO\nTALK ", "按\r住 说\t话")) {
            AccessibilityNodeSnapshot button = node("android.widget.Button", "reader")
                    .text(prompt).clickable(true).build();
            assertTrue(RootNodeCaptureMain.holdControlAtPoint(List.of(button), 30, 30, "reader"));
        }
        AccessibilityNodeSnapshot description = node("android.widget.Button", "reader")
                .text(null).contentDescription("PRESS AND HOLD TO SPEAK").clickable(true).build();
        assertTrue(RootNodeCaptureMain.holdControlAtPoint(List.of(description), 30, 30, "reader"));
        AccessibilityNodeSnapshot empty = node("android.widget.Button", "reader")
                .text(null).contentDescription(null).clickable(true).build();
        assertFalse(RootNodeCaptureMain.holdControlAtPoint(List.of(empty), 30, 30, "reader"));
    }

    @Test public void combinedVoicePromptOnEditableInputIsExcluded() {
        AccessibilityNodeSnapshot input = node("android.widget.EditText", "reader")
                .text("发消息或按住说话...").editable(true).build();
        assertTrue(RootNodeCaptureMain.holdControlAtPoint(List.of(input), 30, 30, "reader"));
    }

    @Test public void voicePromptChildOfEditableInputIsExcluded() {
        AccessibilityNodeSnapshot input = node("android.view.ViewGroup", "reader")
                .editable(true).leaf(false).depth(0).build();
        AccessibilityNodeSnapshot hint = node("android.widget.TextView", "reader")
                .bounds(new Rect(20, 20, 200, 100)).text("发消息或按住说话...")
                .depth(1).build();
        assertTrue(RootNodeCaptureMain.holdControlAtPoint(List.of(input, hint), 30, 30, "reader"));
    }

    @Test public void compositeVoicePromptChildOfGenericInteractiveParentIsExcluded() {
        AccessibilityNodeSnapshot input = node("android.view.ViewGroup", "reader")
                .clickable(true).leaf(false).depth(0).build();
        AccessibilityNodeSnapshot hint = node("android.widget.TextView", "reader")
                .bounds(new Rect(20, 20, 200, 100)).text("发消息或按住说话...")
                .depth(1).build();
        assertTrue(RootNodeCaptureMain.holdControlAtPoint(List.of(input, hint), 30, 30, "reader"));

        // A clickable prose node with the same words is still ordinary text.
        AccessibilityNodeSnapshot prose = node("android.widget.TextView", "reader")
                .text("按住语音说话可以开始录音，这是帮助说明。")
                .clickable(true).build();
        assertFalse(RootNodeCaptureMain.holdControlAtPoint(List.of(prose), 30, 30, "reader"));
    }

    @Test public void explicitVideoSpeedHoldLabelsRequireAnInteractiveControl() {
        AccessibilityNodeSnapshot speedControl = node("android.widget.TextView", "reader")
                .text("长按倍速播放").longClickable(true).build();
        assertTrue(RootNodeCaptureMain.holdControlAtPoint(List.of(speedControl), 30, 30, "reader"));

        AccessibilityNodeSnapshot label = node("android.widget.TextView", "reader")
                .text("按住倍速").bounds(new Rect(20, 20, 200, 100)).depth(1).build();
        AccessibilityNodeSnapshot parent = node("android.view.ViewGroup", "reader")
                .clickable(true).leaf(false).depth(0).build();
        assertTrue(RootNodeCaptureMain.holdControlAtPoint(List.of(parent, label), 30, 30, "reader"));

        AccessibilityNodeSnapshot inert = node("android.widget.TextView", "reader")
                .text("按住倍速").build();
        assertFalse(RootNodeCaptureMain.holdControlAtPoint(List.of(inert), 30, 30, "reader"));
    }

    @Test public void disabledExplicitHoldControlsStillYieldAtPressEntry() throws Exception {
        for (String prompt : List.of("长按倍速播放", "按住说话", "松开发送")) {
            AccessibilityNodeInfo control = pressRoot("android.widget.Button", prompt);
            control.setLongClickable(true);
            control.setEnabled(false);
            try {
                JSONObject result = capturePress(control);
                assertEquals(prompt, "excluded_hold_control", result.optString("reason"));
                assertFalse(result.getBoolean("found"));
            } finally { recyclePressRoot(control); }
        }
    }

    @Test public void disabledParentAndLabelPreserveExplicitSpeedAvoidance() {
        AccessibilityNodeSnapshot parent = node("android.view.ViewGroup", "reader")
                .enabled(false).longClickable(true).leaf(false).depth(0).build();
        AccessibilityNodeSnapshot label = node("android.widget.TextView", "reader")
                .enabled(false).bounds(new Rect(20, 20, 200, 100))
                .text("按住倍速").depth(1).build();
        assertTrue(RootNodeCaptureMain.holdControlAtPoint(List.of(parent, label), 30, 30, "reader"));
        assertFalse(RootNodeCaptureMain.holdControlAtPoint(List.of(parent, label), 250, 150, "reader"));
    }

    @Test public void speedPhraseInOrdinaryTextDoesNotBlockCaptureAndImagesStillSelect() {
        AccessibilityNodeSnapshot paragraph = node("android.widget.TextView", "reader")
                .text("这个视频可以长按倍速播放，普通介绍文字仍可读取。")
                .clickable(true).build();
        assertFalse(RootNodeCaptureMain.holdControlAtPoint(List.of(paragraph), 30, 30, "reader"));

        AccessibilityNodeSnapshot image = node("android.widget.ImageView", "reader").build();
        assertFalse(RootNodeCaptureMain.holdControlAtPoint(List.of(image), 30, 30, "reader"));
        assertNotNull(select(image, List.of(), true, true));
    }

    @Test public void ordinaryTextAndUnrelatedRecordingControlsRemainReadable() {
        AccessibilityNodeSnapshot paragraph = node("android.widget.TextView", "reader")
                .text("按住说话，上滑取消是语音按钮的操作说明。").clickable(true).build();
        assertFalse(RootNodeCaptureMain.holdControlAtPoint(List.of(paragraph), 30, 30, "reader"));
        AccessibilityNodeSnapshot quote = node("android.widget.TextView", "reader").text("按住说话").build();
        assertFalse(RootNodeCaptureMain.holdControlAtPoint(List.of(quote), 30, 30, "reader"));
        AccessibilityNodeSnapshot foreign = node("android.widget.Button", "other")
                .text("按住说话").clickable(true).build();
        assertFalse(RootNodeCaptureMain.holdControlAtPoint(List.of(foreign), 30, 30, "reader"));
        AccessibilityNodeSnapshot hidden = node("android.widget.Button", "reader")
                .text("按住说话").clickable(true).visible(false).build();
        assertFalse(RootNodeCaptureMain.holdControlAtPoint(List.of(hidden), 30, 30, "reader"));
    }

    @Test public void voiceInputPromptsCoverDifferentAppsWithoutBlockingProse() {
        AccessibilityNodeSnapshot editablePrompt = node("android.widget.EditText", "reader")
                .text("按住语音输入以提问").editable(true).build();
        assertTrue(RootNodeCaptureMain.holdControlAtPoint(
                List.of(editablePrompt), 30, 30, "reader"));

        AccessibilityNodeSnapshot buttonPrompt = node("android.widget.Button", "reader")
                .text("语音输入，按住说话").clickable(true).build();
        assertTrue(RootNodeCaptureMain.holdControlAtPoint(
                List.of(buttonPrompt), 30, 30, "reader"));

        AccessibilityNodeSnapshot largeClickablePage = node("android.view.ViewGroup", "reader")
                .bounds(new Rect(0, 0, 1080, 2400)).clickable(true).depth(0).build();
        AccessibilityNodeSnapshot ordinaryLabel = node("android.widget.TextView", "reader")
                .bounds(new Rect(20, 20, 200, 100)).text("按住说话").depth(1).build();
        assertFalse(RootNodeCaptureMain.holdControlAtPoint(
                List.of(largeClickablePage, ordinaryLabel), 30, 30, "reader"));
    }

    @Test public void adjustableRangeControlIsExcludedAtActualPressCaptureEntry() throws Exception {
        AccessibilityNodeInfo range = pressRoot("android.view.View", null);
        range.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0f, 100f, 35f));
        range.setScrollable(true);
        try {
            JSONObject result = capturePress(range);
            assertFalse(result.getBoolean("found"));
            assertEquals("excluded_hold_control", result.getString("reason"));
        } finally { recyclePressRoot(range); }

        AccessibilityNodeInfo progressAction = pressRoot("android.view.View", null);
        progressAction.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0f, 100f, 35f));
        progressAction.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
        try {
            JSONObject result = capturePress(progressAction);
            assertEquals("excluded_hold_control", result.getString("reason"));
        } finally { recyclePressRoot(progressAction); }
    }

    @Test public void readOnlyProgressAndDisabledSliderDoNotBlockPressCapture() throws Exception {
        AccessibilityNodeInfo readOnlyProgress = pressRoot("android.widget.ProgressBar", "播放进度 01:20");
        readOnlyProgress.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0f, 100f, 35f));
        try {
            JSONObject result = capturePress(readOnlyProgress);
            assertTrue(result.getBoolean("found"));
            assertEquals("text", result.getString("kind"));
            assertFalse("read-only progress metadata is not hold-control avoidance",
                    "excluded_hold_control".equals(result.optString("reason")));
        } finally { recyclePressRoot(readOnlyProgress); }

        AccessibilityNodeInfo disabledSlider = pressRoot("android.widget.SeekBar", null);
        disabledSlider.setEnabled(false);
        disabledSlider.setClickable(true);
        disabledSlider.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0f, 1f, .5f));
        try {
            JSONObject result = capturePress(disabledSlider);
            assertFalse(result.getBoolean("found"));
            assertFalse("disabled controls do not enter hold-control avoidance",
                    "excluded_hold_control".equals(result.optString("reason")));
        } finally { recyclePressRoot(disabledSlider); }
    }

    @Test public void standardInteractiveSeekBarAndExplicitCameraControlAreExcluded() throws Exception {
        AccessibilityNodeInfo seekBar = pressRoot("android.widget.SeekBar", null);
        seekBar.setClickable(true);
        try {
            JSONObject result = capturePress(seekBar);
            assertEquals("excluded_hold_control", result.getString("reason"));
        } finally { recyclePressRoot(seekBar); }

        AccessibilityNodeInfo camera = pressRoot("android.widget.Button", "拍摄");
        camera.setClickable(true);
        try {
            JSONObject result = capturePress(camera);
            assertEquals("excluded_hold_control", result.getString("reason"));
        } finally { recyclePressRoot(camera); }

        AccessibilityNodeInfo disabledCamera = pressRoot("android.widget.Button", "拍摄");
        disabledCamera.setClickable(true);
        disabledCamera.setEnabled(false);
        try {
            JSONObject result = capturePress(disabledCamera);
            assertTrue(result.getBoolean("found"));
            assertEquals("text", result.getString("kind"));
            assertFalse("disabled camera controls are not hold-control exclusions",
                    "excluded_hold_control".equals(result.optString("reason")));
        } finally { recyclePressRoot(disabledCamera); }

        AccessibilityNodeInfo prose = pressRoot("android.widget.TextView", "这段视频拍摄了日落过程，仍是普通正文。");
        prose.setClickable(true);
        try {
            JSONObject result = capturePress(prose);
            assertTrue(result.getBoolean("found"));
            assertEquals("text", result.getString("kind"));
        } finally { recyclePressRoot(prose); }
    }

    @Test public void imageRequiresEnabledTypeAndExplicitImageNode() {
        AccessibilityNodeSnapshot image = node("android.widget.ImageView", "reader").build();
        assertTrue(select(image, List.of(), true, true).isImage());
        assertNull(select(image, List.of(), true, false));
        assertNull(select(node("android.view.View", "reader").build(), List.of(), true, true));
        assertNull(select(node("android.widget.ImageView", "other").build(), List.of(), true, true));
    }

    @Test public void overlappingPasswordExcludesWholeImageEvenWhenTouchIsOutsidePassword() {
        AccessibilityNodeSnapshot image = node("android.widget.ImageView", "reader").build();
        assertNull(select(image, List.of(new Rect(100, 100, 140, 140)), true, true));
        assertNotNull(select(image, List.of(new Rect(400, 400, 500, 500)), true, true));
    }

    @Test public void imageSwitchDoesNotEnableTextCapture() {
        AccessibilityNodeSnapshot text = node("android.widget.TextView", "reader").text("sample").build();
        assertNull(select(text, List.of(), false, true));
        assertNotNull(select(text, List.of(), true, false));
    }

    @Test public void emptyPressOcrStaysInsideItsVerifiedSourceWindow() {
        assertEquals(new Rect(40, 80, 960, 1920), RootNodeCaptureMain.safeEmptyOcrRegion(
                500, 1000, new Rect(0, 0, 1000, 2000), 1000, 2000, 1f, true,
                "reader", List.of(), List.of()));
        assertEquals(new Rect(110, 660, 890, 1340), RootNodeCaptureMain.safeEmptyOcrRegion(
                500, 1000, new Rect(0, 0, 1000, 2000), 1000, 2000, 1f, false,
                "reader", List.of(), List.of()));
        Rect window = new Rect(20, 40, 320, 240);
        Rect original = new Rect(window);
        Rect region = RootNodeCaptureMain.safeEmptyOcrRegion(30, 50, window, 1000, 2000, 1f, true,
                "reader", List.of(node("android.view.View", "reader").bounds(window).build()), List.of());
        assertEquals(window, region);
        assertEquals(original, window);
        assertNull(RootNodeCaptureMain.safeEmptyOcrRegion(19, 50, window, 1000, 2000, 1f, false,
                "reader", List.of(), List.of()));
        assertEquals(new Rect(900, 1900, 1000, 2000), RootNodeCaptureMain.safeEmptyOcrRegion(
                990, 1990, new Rect(900, 1900, 1100, 2100), 1000, 2000, 1f, false,
                "reader", List.of(), List.of()));
    }

    @Test public void emptyPressOcrRejectsAnyPasswordInCropAndForeignContent() {
        Rect window = new Rect(0, 0, 1000, 2000);
        // The point is outside this field, but the bounded crop includes it.
        assertNull(RootNodeCaptureMain.safeEmptyOcrRegion(500, 1000, window, 1000, 2000, 1f, false,
                "reader", List.of(), List.of(new Rect(700, 1000, 750, 1040))));
        // The one-pixel margin also protects a field adjacent to the crop's right edge.
        assertNull(RootNodeCaptureMain.safeEmptyOcrRegion(500, 1000, window, 1000, 2000, 1f, false,
                "reader", List.of(), List.of(new Rect(890, 1000, 910, 1020))));
        assertNull(RootNodeCaptureMain.safeEmptyOcrRegion(500, 1000, window, 1000, 2000, 1f, false,
                "reader", List.of(node("android.view.View", "other")
                        .bounds(new Rect(600, 800, 800, 1100)).build()), List.of()));
        assertNotNull(RootNodeCaptureMain.safeEmptyOcrRegion(500, 1000, window, 1000, 2000, 1f, false,
                "reader", List.of(), List.of(new Rect(950, 1800, 990, 1900))));
    }

    @Test public void keyboardBlocksPressOverUnderlyingFullSizeApplication() {
        AccessibilityWindowInfo application = window(AccessibilityWindowInfo.TYPE_APPLICATION,
                new Rect(0, 0, 1000, 2000));
        AccessibilityWindowInfo keyboard = window(AccessibilityWindowInfo.TYPE_INPUT_METHOD,
                new Rect(0, 1300, 1000, 2000));
        List<AccessibilityWindowInfo> windows = List.of(application, keyboard);
        assertFalse(RootNodeCaptureMain.inputMethodAtPoint(List.of(application), 500, 1700));
        assertTrue(RootNodeCaptureMain.inputMethodAtPoint(windows, 500, 1700));
        assertFalse(RootNodeCaptureMain.inputMethodAtPoint(windows, 500, 1000));
    }

    @Test public void floatingKeyboardUsesItsOwnBoundsNotTheUnderlyingApplication() {
        List<AccessibilityWindowInfo> windows = List.of(
                window(AccessibilityWindowInfo.TYPE_APPLICATION, new Rect(0, 0, 1000, 2000)),
                window(AccessibilityWindowInfo.TYPE_SYSTEM, new Rect(0, 0, 1000, 2000)),
                window(AccessibilityWindowInfo.TYPE_INPUT_METHOD, new Rect(200, 800, 800, 1400)));
        assertTrue(RootNodeCaptureMain.inputMethodAtPoint(windows, 300, 900));
        assertFalse(RootNodeCaptureMain.inputMethodAtPoint(windows, 100, 900));
        assertFalse(RootNodeCaptureMain.inputMethodAtPoint(windows, 800, 900));
    }

    private static AccessibilityWindowInfo window(int type, Rect bounds) {
        AccessibilityWindowInfo window = AccessibilityWindowInfo.obtain();
        ShadowAccessibilityWindowInfo shadow = Shadow.extract(window);
        shadow.setType(type);
        shadow.setBoundsInScreen(bounds);
        return window;
    }

    private AccessibilityCandidateSelector.Selection select(AccessibilityNodeSnapshot node,
            List<Rect> passwords, boolean text, boolean images) {
        return RootNodeCaptureMain.selectContent(classifier, new ArrayList<>(List.of(node)),
                passwords, 30, 30, "reader", text, images);
    }

    private static AccessibilityNodeSnapshot.Builder node(String className, String packageName) {
        return new AccessibilityNodeSnapshot.Builder().bounds(new Rect(0, 0, 300, 200))
                .className(className).packageName(packageName).visible(true).leaf(true).traversalOrder(1);
    }

    private static AccessibilityNodeInfo pressRoot(String className, String text) {
        AccessibilityWindowInfo window = window(AccessibilityWindowInfo.TYPE_APPLICATION,
                new Rect(0, 0, 1000, 2000));
        AccessibilityNodeInfo root = AccessibilityNodeInfo.obtain();
        root.setPackageName("reader");
        root.setClassName(className);
        root.setText(text);
        root.setVisibleToUser(true);
        root.setEnabled(true);
        root.setBoundsInScreen(new Rect(0, 0, 300, 200));
        Shadow.<ShadowAccessibilityNodeInfo>extract(root).setAccessibilityWindowInfo(window);
        return root;
    }

    private static JSONObject capturePress(AccessibilityNodeInfo root) throws Exception {
        UiAutomation automation = Shadow.newInstanceOf(UiAutomation.class);
        AutomationShadow automationShadow = Shadow.extract(automation);
        automationShadow.root = root;
        JSONObject request = new JSONObject().put("x", 30).put("y", 30)
                .put("density", 1).put("width", 1000).put("height", 2000)
                .put("blocked", new JSONArray()).put("press_lookup", true)
                .put("allow_text", true).put("allow_images", false);
        return RootNodeCaptureMain.captureRequest(request, automation,
                new Object(), new int[]{0}, new Handler(Looper.getMainLooper()));
    }

    private static void recyclePressRoot(AccessibilityNodeInfo root) {
        AccessibilityWindowInfo window = root.getWindow();
        root.recycle();
        if (window != null) window.recycle();
    }

    @Implements(UiAutomation.class)
    public static class AutomationShadow extends ShadowUiAutomation {
        AccessibilityNodeInfo root;

        @Implementation protected void clearCache() {}
        @Implementation protected List<AccessibilityWindowInfo> getWindows() { return List.of(); }
        @Implementation protected AccessibilityNodeInfo getRootInActiveWindow() {
            return root == null ? null : AccessibilityNodeInfo.obtain(root);
        }
    }
}

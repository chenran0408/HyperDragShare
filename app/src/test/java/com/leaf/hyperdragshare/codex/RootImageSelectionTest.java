package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAccessibilityWindowInfo;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class RootImageSelectionTest {
    private final AccessibilityNodeClassifier classifier = new AccessibilityNodeClassifier(1f, 1080, 2400);

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
}

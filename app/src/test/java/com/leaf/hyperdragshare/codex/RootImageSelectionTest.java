package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

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

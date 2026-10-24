package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class AccessibilityNodeClassifierTest {
    @Test
    public void textAndImageFollowDocumentedPriority() {
        AccessibilityNodeSnapshot text = snapshot(new Rect(0, 0, 200, 100))
                .className("android.widget.TextView")
                .text("Hello")
                .build();
        AccessibilityNodeSnapshot image = snapshot(new Rect(20, 20, 120, 90))
                .className("android.widget.ImageView")
                .contentDescription("photo")
                .build();
        AccessibilityNodeClassifier.Buckets buckets = new AccessibilityNodeClassifier(1f, 1080, 2400)
                .classify(Arrays.asList(text, image));

        AccessibilityCandidateSelector.Selection selection = AccessibilityCandidateSelector.select(
                buckets, 30f, 30f);
        assertNotNull(selection);
        assertEquals(AccessibilityCandidate.Kind.TEXT, selection.candidate.kind);
    }

    @Test
    public void strongImageUsesImageRegionEvenWithDescription() {
        AccessibilityNodeSnapshot image = snapshot(new Rect(0, 0, 100, 100))
                .className("android.widget.ImageView")
                .contentDescription("A scenic photo")
                .build();
        AccessibilityCandidateSelector.Selection selection = AccessibilityCandidateSelector.select(
                new AccessibilityNodeClassifier(1f, 1080, 2400)
                        .classify(Arrays.asList(image)),
                10f,
                10f);

        assertNotNull(selection);
        assertEquals(AccessibilityCandidate.Kind.IMAGE_REGION, selection.candidate.kind);
    }

    @Test
    public void buttonAndMessageTextRemainClassifiableOutsidePressControlAvoidance() {
        AccessibilityNodeSnapshot button = snapshot(new Rect(0, 0, 120, 60))
                .className("android.widget.Button").text("转发").clickable(true).build();
        AccessibilityNodeSnapshot message = snapshot(new Rect(0, 80, 400, 160))
                .className("android.widget.TextView").text("这条评论可以转发，也可以回复。")
                .longClickable(true).build();
        AccessibilityNodeClassifier.Buckets buckets = new AccessibilityNodeClassifier(1f, 1080, 2400)
                .classify(Arrays.asList(button, message));

        assertEquals(2, buckets.nativeText.size());
        assertEquals("转发", AccessibilityCandidateSelector.select(buckets, 30, 30).candidate.text);
        assertEquals("这条评论可以转发，也可以回复。",
                AccessibilityCandidateSelector.select(buckets, 30, 100).candidate.text);
    }

    @Test
    public void genericImageRoleWithoutTextIsAnImageRegion() {
        AccessibilityNodeSnapshot image = snapshot(new Rect(0, 0, 100, 100))
                .className("android.view.View")
                .contentDescription("image")
                .build();

        AccessibilityCandidateSelector.Selection selection = AccessibilityCandidateSelector.select(
                new AccessibilityNodeClassifier(1f, 1080, 2400)
                        .classify(Arrays.asList(image)),
                10f,
                10f);

        assertNotNull(selection);
        assertEquals(AccessibilityCandidate.Kind.IMAGE_REGION, selection.candidate.kind);
    }

    @Test
    public void smallEmptyLeafAndPasswordAreRejected() {
        AccessibilityNodeSnapshot tiny = snapshot(new Rect(0, 0, 20, 20))
                .className("android.view.View")
                .build();
        AccessibilityNodeSnapshot password = snapshot(new Rect(30, 0, 200, 80))
                .className("android.widget.EditText")
                .text("secret")
                .password(true)
                .build();
        AccessibilityCandidateSelector.Selection selection = AccessibilityCandidateSelector.select(
                new AccessibilityNodeClassifier(1f, 1080, 2400)
                        .classify(Arrays.asList(tiny, password)),
                10f,
                10f);

        assertNull(selection);
    }

    @Test
    public void sameTextParentIsReplacedBySpecificChild() {
        AccessibilityNodeSnapshot parent = snapshot(new Rect(0, 0, 200, 200))
                .className("android.widget.TextView")
                .text("same")
                .leaf(false)
                .depth(1)
                .traversalOrder(1)
                .build();
        AccessibilityNodeSnapshot child = snapshot(new Rect(20, 20, 100, 60))
                .className("android.widget.TextView")
                .text("same")
                .leaf(true)
                .depth(2)
                .traversalOrder(2)
                .build();
        AccessibilityNodeClassifier.Buckets buckets = new AccessibilityNodeClassifier(1f, 1080, 2400)
                .classify(Arrays.asList(parent, child));

        assertEquals(1, buckets.nativeText.size());
        assertEquals(new Rect(20, 20, 100, 60), buckets.nativeText.get(0).bounds);
    }

    @Test
    public void adjacentTextUsesNearestSameColumnAndPackageFromExistingSnapshot() {
        AccessibilityNodeSnapshot hit = snapshot(new Rect(100, 300, 400, 400))
                .packageName("reader").text("current").build();
        AccessibilityNodeSnapshot before = snapshot(new Rect(110, 200, 390, 270))
                .packageName("reader").text("previous").build();
        AccessibilityNodeSnapshot distant = snapshot(new Rect(100, 20, 400, 80))
                .packageName("reader").text("too far").build();
        AccessibilityNodeSnapshot after = snapshot(new Rect(100, 430, 400, 490))
                .packageName("reader").text("next").build();
        AccessibilityNodeSnapshot parent = snapshot(new Rect(90, 100, 410, 500))
                .packageName("reader").text("aggregate").leaf(false).build();
        AccessibilityNodeSnapshot otherColumn = snapshot(new Rect(450, 280, 700, 299))
                .packageName("reader").text("different column").build();
        AccessibilityNodeSnapshot otherPackage = snapshot(new Rect(100, 280, 400, 299))
                .packageName("other").text("different app").build();
        AccessibilityNodeSnapshot password = snapshot(new Rect(100, 401, 400, 420))
                .packageName("reader").text("secret").password(true).build();
        AccessibilityCandidateSelector.Selection selection = AccessibilityCandidateSelector.select(
                new AccessibilityNodeClassifier(1f, 1080, 2400).classify(Arrays.asList(
                        hit, before, distant, after, parent, otherColumn, otherPackage, password)), 200, 350);
        assertNotNull(selection);
        assertEquals("current", selection.candidate.text);
        assertEquals("previous", selection.beforeText);
        assertEquals("next", selection.afterText);
        CapturedContent content = CapturedContent.text(selection.candidate.text, "reader",
                selection.candidate.bounds, selection.beforeText, selection.afterText);
        assertEquals("previous", content.beforeText);
        assertEquals("next", content.afterText);
    }

    @Test
    public void editableSelectionDoesNotAcquireSurroundingText() {
        AccessibilityNodeSnapshot hit = snapshot(new Rect(100, 300, 400, 400))
                .packageName("reader").text("editing").editable(true).build();
        AccessibilityNodeSnapshot before = snapshot(new Rect(100, 200, 400, 270))
                .packageName("reader").text("previous").build();
        AccessibilityCandidateSelector.Selection selection = AccessibilityCandidateSelector.select(
                new AccessibilityNodeClassifier(1f, 1080, 2400).classify(Arrays.asList(hit, before)), 200, 350);
        assertNotNull(selection);
        assertNull(selection.beforeText);
        assertNull(selection.afterText);
        CapturedContent legacy = CapturedContent.text("text", "reader", hit.bounds);
        assertNull(legacy.beforeText);
        assertNull(legacy.afterText);
    }

    private static AccessibilityNodeSnapshot.Builder snapshot(Rect bounds) {
        return new AccessibilityNodeSnapshot.Builder()
                .bounds(bounds)
                .visible(true)
                .leaf(true)
                .traversalOrder(1);
    }
}

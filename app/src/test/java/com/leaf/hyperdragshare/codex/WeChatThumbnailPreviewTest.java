package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class WeChatThumbnailPreviewTest {
    private static final String BADGE_TEXT = "缩略图";
    private static final String BADGE_DESCRIPTION = "微信图片缩略图，非原文件";

    @Test public void standardThumbnailPreviewHasAccessibleBadgeAndKeepsBitmapOwnershipOnDestroy()
            throws Exception {
        DragShareController controller = controller(DragShareSettings.STYLE_SIMPLE);
        Bitmap bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888);
        CapturedContent content = CapturedContent.nodeThumbnailImage(
                bitmap, "com.tencent.mm", new Rect(0, 0, 30, 20));

        try {
            assertTrue(content.isWeChatThumbnail());
            createPreview(controller, content);
            FrameLayout preview = (FrameLayout) get(controller, "previewView");
            TextView badge = findBadge(preview);
            assertNotNull(badge);
            assertEquals(BADGE_DESCRIPTION, badge.getContentDescription());
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) get(controller, "previewParams");
            assertEquals((int) get(controller, "previewWidth"), params.width);
            assertEquals((int) get(controller, "previewHeight"), params.height);

            // This test constructs a preview directly; full gesture/session teardown is covered elsewhere.
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            assertFalse("preview teardown must not change the source Bitmap ownership", bitmap.isRecycled());
        } finally {
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            if (!bitmap.isRecycled()) bitmap.recycle();
        }
    }

    @Test public void portalOriginalMediaAndWeChatTextPreviewsDoNotShowThumbnailBadge() throws Exception {
        DragShareController portalController = controller(DragShareSettings.STYLE_PORTAL);
        Bitmap originalBitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888);
        CapturedContent portalMedia = CapturedContent.portalMediaImage(
                originalBitmap, "com.tencent.mm", new Rect(0, 0, 30, 20));
        try {
            assertFalse(portalMedia.isWeChatThumbnail());
            createPreview(portalController, portalMedia);
            assertNoBadge((View) get(portalController, "previewView"));
        } finally {
            portalController.destroy();
            ShadowLooper.shadowMainLooper().idle();
            if (!originalBitmap.isRecycled()) originalBitmap.recycle();
        }

        DragShareController textController = controller(DragShareSettings.STYLE_SIMPLE);
        CapturedContent text = CapturedContent.text("普通文字", "com.tencent.mm", null);
        try {
            assertNotNull(text);
            assertFalse(text.isWeChatThumbnail());
            createPreview(textController, text);
            assertNoBadge((View) get(textController, "previewView"));
        } finally {
            textController.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    @Test public void unknownAndScreenCaptureImagesDoNotShowThumbnailBadge() throws Exception {
        Bitmap otherAppBitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888);
        DragShareController otherAppController = controller(DragShareSettings.STYLE_SIMPLE);
        try {
            CapturedContent otherAppThumbnail = CapturedContent.nodeThumbnailImage(
                    otherAppBitmap, "other.app", new Rect(0, 0, 30, 20));
            assertFalse(otherAppThumbnail.isWeChatThumbnail());
            createPreview(otherAppController, otherAppThumbnail);
            assertNoBadge((View) get(otherAppController, "previewView"));
        } finally {
            otherAppController.destroy();
            ShadowLooper.shadowMainLooper().idle();
            if (!otherAppBitmap.isRecycled()) otherAppBitmap.recycle();
        }

        Bitmap unknownBitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888);
        DragShareController unknownController = controller(DragShareSettings.STYLE_SIMPLE);
        try {
            CapturedContent unknown = CapturedContent.image(
                    unknownBitmap, "com.tencent.mm", new Rect(0, 0, 30, 20), true);
            assertFalse(unknown.isWeChatThumbnail());
            createPreview(unknownController, unknown);
            assertNoBadge((View) get(unknownController, "previewView"));
        } finally {
            unknownController.destroy();
            ShadowLooper.shadowMainLooper().idle();
            if (!unknownBitmap.isRecycled()) unknownBitmap.recycle();
        }

        Bitmap screenshotBitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888);
        DragShareController screenshotController = controller(DragShareSettings.STYLE_SIMPLE);
        try {
            CapturedContent screenshot = CapturedContent.screenCaptureImage(
                    screenshotBitmap, "com.tencent.mm", new Rect(0, 0, 30, 20));
            assertFalse(screenshot.isWeChatThumbnail());
            createPreview(screenshotController, screenshot);
            assertNoBadge((View) get(screenshotController, "previewView"));
        } finally {
            screenshotController.destroy();
            ShadowLooper.shadowMainLooper().idle();
            if (!screenshotBitmap.isRecycled()) screenshotBitmap.recycle();
        }

        Bitmap ocrBitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888);
        DragShareController ocrController = controller(DragShareSettings.STYLE_SIMPLE);
        try {
            CapturedContent ocrRegion = CapturedContent.pressOcrRegion(
                    ocrBitmap, "com.tencent.mm", new Rect(0, 0, 30, 20));
            assertFalse(ocrRegion.isWeChatThumbnail());
            createPreview(ocrController, ocrRegion);
            assertNoBadge((View) get(ocrController, "previewView"));
        } finally {
            ocrController.destroy();
            ShadowLooper.shadowMainLooper().idle();
            if (!ocrBitmap.isRecycled()) ocrBitmap.recycle();
        }
    }

    @Test public void circleThumbnailUsesTheSamePreviewBadge() throws Exception {
        DragShareController controller = controller(DragShareSettings.STYLE_CIRCLE);
        Bitmap bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888);
        try {
            createPreview(controller, CapturedContent.nodeThumbnailImage(
                    bitmap, "com.tencent.mm", new Rect(0, 0, 30, 20)));
            assertNotNull(findBadge((View) get(controller, "previewView")));
        } finally {
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            if (!bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private static DragShareController controller(int style) throws Exception {
        DragShareController controller = new DragShareController(
                RuntimeEnvironment.getApplication(), OverlayWindowPolicy.portal());
        DragShareSettings settings = DragShareSettings.defaults().toBuilder().setUiStyle(style).build();
        set(controller, "settings", settings);
        return controller;
    }

    private static void createPreview(DragShareController controller, CapturedContent content)
            throws Exception {
        Method method = DragShareController.class.getDeclaredMethod("createPreview", CapturedContent.class);
        method.setAccessible(true);
        method.invoke(controller, content);
    }

    private static TextView findBadge(View view) {
        if (view instanceof TextView && BADGE_TEXT.contentEquals(((TextView) view).getText())) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView found = findBadge(group.getChildAt(index));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void assertNoBadge(View view) {
        assertNotNull(view);
        org.junit.Assert.assertNull(findBadge(view));
    }

    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
}

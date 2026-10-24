package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;


import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowLog;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, shadows = PortalImagePayloadTest.XposedHelpersShadow.class)
public final class PortalImagePayloadTest {
    @Before public void prepare() {
        DragShareLog.configure(DragShareSettings.defaults());
        ShadowLog.clear();
        FakePortalService.reset();
    }

    @After public void restore() {
        DragShareLog.configure(DragShareSettings.defaults());
        FakePortalService.reset();
    }

    @Test public void captureCopiesCurrentPortalViewBitmapAndRetainsOnlyTheCopy() {
        Bitmap current = bitmap();
        FakePortalService.sBitmap = current;
        FakePortalService.mCatcherResult = resultWithImageMode(modeWithBitmap(current));
        FakePortalService service = new FakePortalService(RuntimeEnvironment.getApplication());

        CapturedContent captured = PortalContentCaptureSource.capture(service, DragShareSettings.defaults());

        assertTrue(captured != null);
        assertEquals(CapturedContent.ImageOrigin.PORTAL_MEDIA, captured.imageOrigin);
        assertNotSame(current, captured.bitmap);
        assertFalse(current.isRecycled());
        assertTrue(operationMessages().contains("op=- stage=portal_capture outcome=wechat_accepted_image_view_bitmap"));
        captured.bitmap.recycle();
        current.recycle();
    }

    @Test public void captureDoesNotReuseStaticBitmapWhenCurrentResultHasNoImagePayload() {
        Bitmap stale = bitmap();
        FakePortalService.sBitmap = stale;
        FakePortalService.mCatcherResult = resultWithImageMode(new Bundle());
        FakePortalService service = new FakePortalService(RuntimeEnvironment.getApplication());

        CapturedContent captured = PortalContentCaptureSource.capture(service, DragShareSettings.defaults());

        assertNull(captured);
        assertFalse(stale.isRecycled());
        assertTrue(operationMessages().contains("op=- stage=portal_capture outcome=wechat_missing_payload"));
        stale.recycle();
    }

    @Test public void captureRejectsStaticBitmapThatDoesNotMatchCurrentViewBitmap() {
        Bitmap current = bitmap();
        Bitmap stale = bitmap();
        FakePortalService.sBitmap = stale;
        FakePortalService.mCatcherResult = resultWithImageMode(modeWithBitmap(current));
        FakePortalService service = new FakePortalService(RuntimeEnvironment.getApplication());

        CapturedContent captured = PortalContentCaptureSource.capture(service, DragShareSettings.defaults());

        assertNull(captured);
        assertFalse(stale.isRecycled());
        assertTrue(operationMessages().contains("op=- stage=portal_capture outcome=wechat_stale_bitmap"));
        current.recycle();
        stale.recycle();
    }

    @Test public void structuredPortalOutcomeSurvivesDiagnosticRuntimeFilter() throws Exception {
        Bitmap stale = bitmap();
        FakePortalService.sBitmap = stale;
        FakePortalService.mCatcherResult = resultWithImageMode(new Bundle());
        PortalContentCaptureSource.capture(
                new FakePortalService(RuntimeEnvironment.getApplication()), DragShareSettings.defaults());

        File raw = File.createTempFile("portal-capture", ".log");
        try {
            StringBuilder log = new StringBuilder();
            for (ShadowLog.LogItem item : ShadowLog.getLogsForTag("DragShare/Operation")) {
                log.append("2026-10-24 12:30:01.123 I pid=44 uid=0 thread=main DragShare/Operation: ")
                        .append(item.msg).append('\n');
            }
            Files.write(raw.toPath(), log.toString().getBytes(StandardCharsets.UTF_8));
            String filtered = DiagnosticBundleExporter.filterRuntimeLog(raw);
            assertTrue(filtered.contains("stage=portal_capture outcome=wechat_missing_payload"));
            assertFalse(filtered.contains("com.tencent.mm"));
        } finally {
            Files.deleteIfExists(raw.toPath());
            stale.recycle();
        }
    }

    @Test public void missingAndOtherModesCannotReuseAnExistingStaticBitmap() {
        Bundle other = new Bundle();
        Bitmap oldBitmap = bitmap();

        assertFalse(PortalContentCaptureSource.hasCurrentImagePayload(null));
        assertFalse(PortalContentCaptureSource.hasCurrentImagePayload(other));
        assertFalse(PortalContentCaptureSource.matchesCurrentImageBitmap(other, oldBitmap));

        oldBitmap.recycle();
    }

    @Test public void viewBitmapMustBeTheSameLiveBitmapHeldByTheHost() {
        Bitmap current = bitmap();
        Bitmap stale = bitmap();
        Bundle mode = new Bundle();
        mode.putParcelable("viewBitmap", current);

        assertTrue(PortalContentCaptureSource.hasCurrentImagePayload(mode));
        assertTrue(PortalContentCaptureSource.matchesCurrentImageBitmap(mode, current));
        assertFalse(PortalContentCaptureSource.matchesCurrentImageBitmap(mode, stale));

        current.recycle();
        assertFalse(PortalContentCaptureSource.hasCurrentImagePayload(mode));
        stale.recycle();
    }

    @Test public void onlyLoadableHttpUrlsQualifyAsCurrentImagePayload() {
        Bitmap hostBitmap = bitmap();
        Bundle mode = new Bundle();
        mode.putString("imgUrl", "https://cdn.example.test/image.png");
        assertTrue(PortalContentCaptureSource.hasCurrentImagePayload(mode));
        assertTrue(PortalContentCaptureSource.matchesCurrentImageBitmap(mode, hostBitmap));

        mode.putString("imgUrl", "//cdn.example.test/image.png");
        assertTrue(PortalContentCaptureSource.hasCurrentImagePayload(mode));
        mode.putString("imgUrl", "ftp://cdn.example.test/image.png");
        assertFalse(PortalContentCaptureSource.hasCurrentImagePayload(mode));
        mode.putString("imgUrl", "https://");
        assertFalse(PortalContentCaptureSource.hasCurrentImagePayload(mode));
        mode.putString("imgUrl", "https://cdn.example.test/image with space.png");
        assertFalse(PortalContentCaptureSource.hasCurrentImagePayload(mode));
        mode.putString("imgUrl", "");
        assertFalse(PortalContentCaptureSource.hasCurrentImagePayload(mode));
        assertFalse(PortalContentCaptureSource.matchesCurrentImageBitmap(mode, hostBitmap));

        hostBitmap.recycle();
    }

    private static Bitmap bitmap() {
        return Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
    }

    private static Bundle modeWithBitmap(Bitmap bitmap) {
        Bundle mode = new Bundle();
        mode.putParcelable("viewBitmap", bitmap);
        return mode;
    }

    private static FakeCatcherResult resultWithImageMode(Bundle mode) {
        Map<String, Object> params = new HashMap<>();
        params.put("image_pick_mode", mode);
        Map<String, Object> result = new HashMap<>();
        result.put("image_pick", params);
        return new FakeCatcherResult(result);
    }

    private static String operationMessages() {
        StringBuilder messages = new StringBuilder();
        for (ShadowLog.LogItem item : ShadowLog.getLogsForTag("DragShare/Operation")) {
            messages.append(item.msg).append('\n');
        }
        return messages.toString();
    }

    public static final class FakeCatcherResult {
        private final Map<String, Object> params;

        FakeCatcherResult(Map<String, Object> params) { this.params = params; }

        public Object getParams(String key) { return params.get(key); }
    }

    public static final class FakePortalService extends ContextWrapper {
        public static Boolean sIsTextMode = Boolean.FALSE;
        public static String sContent;
        public static Object mCatcherResult;
        public static Bitmap sBitmap;
        public static String injectorPackage = "com.tencent.mm";

        FakePortalService(Context base) { super(base); }

        public static String getInjectorPackage() { return injectorPackage; }
        public Rect getContentRect() { return new Rect(0, 0, 2, 2); }
        public static Bitmap getBitmap() { return sBitmap; }

        static void reset() {
            sIsTextMode = Boolean.FALSE;
            sContent = null;
            mCatcherResult = null;
            sBitmap = null;
            injectorPackage = "com.tencent.mm";
        }
    }

    @Implements(className = "de.robv.android.xposed.XposedHelpers", isInAndroidSdk = false)
    public static final class XposedHelpersShadow {
        @Implementation
        protected static Object getStaticObjectField(Class<?> type, String name) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(null);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        }

        @Implementation
        protected static Object callStaticMethod(Class<?> type, String name, Object... args) {
            return invoke(type, null, name, args);
        }

        @Implementation
        protected static Object callMethod(Object receiver, String name, Object... args) {
            return invoke(receiver.getClass(), receiver, name, args);
        }

        // Only the fake service's no-arg accessors and getParams(String) are needed.
        private static Object invoke(Class<?> type, Object receiver, String name, Object[] args) {
            try {
                Class<?>[] types = args.length == 0 ? new Class<?>[0] : new Class<?>[] { String.class };
                java.lang.reflect.Method method = type.getDeclaredMethod(name, types);
                method.setAccessible(true);
                return method.invoke(receiver, args);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException(error);
            }
        }
    }
}

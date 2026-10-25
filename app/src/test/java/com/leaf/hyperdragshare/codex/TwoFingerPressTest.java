package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSystemClock;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class TwoFingerPressTest {
    @Test
    public void twoFingerConfirmationUsesExistingBigBangTextHandoffAndStopReleasesListener()
            throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        DragShareSettings settings = DragShareSettings.defaults().toBuilder()
                .setTwoFingerLongPressEnabled(true)
                .setTwoFingerLongPressTimeoutMillis(600)
                .build();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        PressCaptureSource source = null;
        long downTime = SystemClock.uptimeMillis();
        AtomicBoolean accepted = new AtomicBoolean();
        try {
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            source = new PressCaptureSource(context, input, controller, settings);
            field(source, "nodeSession").set(source, provider.connection);
            BackgroundTouchBlocker originalBlocker =
                    (BackgroundTouchBlocker) field(source, "blocker").get(source);
            originalBlocker.close();
            field(source, "blocker").set(source, new BackgroundTouchBlocker(
                    context, (target, method, args) -> null, () -> false));
            PressCaptureSource activeSource = source;
            new Handler(Looper.getMainLooper()).post(() -> {
                activeSource.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime);
                accepted.set(activeSource.onContactEvent(MotionEvent.ACTION_POINTER_DOWN,
                        20f, 30f, 40f, 50f, 2, downTime + 100L));
            });
            ShadowLooper.shadowMainLooper().idle();
            assertTrue(accepted.get());
            field(source, "cached").set(source, CapturedContent.text("selected text", "test.source", null));

            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(700));
            ShadowLooper.shadowMainLooper().idle();

            assertNotNull(provider.bigBangRequest);
            assertEquals("selected text", provider.bigBangRequest.getString("text"));
            assertTrue(provider.bigBangRequest.getLong("press_confirmed_at") > 0L);
            PressTriggerDetector detector = (PressTriggerDetector) field(source, "detector").get(source);
            assertTrue(detector.isFired());
            new Handler(Looper.getMainLooper()).post(() -> activeSource.onContactEvent(
                    MotionEvent.ACTION_POINTER_UP, 20f, 30f, 40f, 50f, 1, downTime + 701L));
            ShadowLooper.shadowMainLooper().idle();
            assertFalse(detector.isActive());
        } finally {
            if (source != null) source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
        assertNull(field(input, "contactListener").get(input));
    }

    @Test
    public void releasingConfirmedTwoFingerOcrKeepsAsyncTextHandoffAlive() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        DragShareSettings settings = DragShareSettings.defaults().toBuilder()
                .setTwoFingerLongPressEnabled(true)
                .setTwoFingerLongPressTimeoutMillis(600)
                .build();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        AtomicReference<AicrOcrClient.Callback> ocrCallback = new AtomicReference<>();
        AtomicBoolean ocrCancelled = new AtomicBoolean();
        PressCaptureSource source = new PressCaptureSource(context, input, controller, settings,
                (ocrContext, bitmap, callback) -> {
                    ocrCallback.set(callback);
                    return () -> ocrCancelled.set(true);
                });
        Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        long downTime = SystemClock.uptimeMillis();
        try {
            field(source, "nodeSession").set(source, provider.connection);
            BackgroundTouchBlocker originalBlocker =
                    (BackgroundTouchBlocker) field(source, "blocker").get(source);
            originalBlocker.close();
            field(source, "blocker").set(source, new BackgroundTouchBlocker(
                    context, (target, method, args) -> null, () -> false));
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();

            AtomicBoolean accepted = new AtomicBoolean();
            new Handler(Looper.getMainLooper()).post(() -> {
                source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime);
                accepted.set(source.onContactEvent(MotionEvent.ACTION_POINTER_DOWN,
                        20f, 30f, 40f, 50f, 2, downTime + 100L));
                fieldUnchecked(source, "cached", CapturedContent.pressOcrRegion(
                        bitmap, "com.tencent.mm", new Rect()));
            });
            ShadowLooper.shadowMainLooper().idle();
            assertTrue(accepted.get());

            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(700));
            ShadowLooper.shadowMainLooper().idle();
            assertNotNull(ocrCallback.get());

            new Handler(Looper.getMainLooper()).post(() -> source.onContactEvent(
                    MotionEvent.ACTION_POINTER_UP, 20f, 30f, 40f, 50f, 1, downTime + 701L));
            ShadowLooper.shadowMainLooper().idle();
            assertFalse(ocrCancelled.get());
            assertNull(provider.bigBangRequest);
            controller.acceptPointerEvent(MotionEvent.ACTION_CANCEL, 20f, 30f,
                    downTime + 702L, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() -> source.onContactEvent(
                    MotionEvent.ACTION_UP, 20f, 30f, Float.NaN, Float.NaN, 0, downTime + 703L));
            ShadowLooper.shadowMainLooper().idle();
            assertFalse(ocrCancelled.get());

            new Handler(Looper.getMainLooper()).post(() ->
                    ocrCallback.get().onComplete("recognized after release", null));
            ShadowLooper.shadowMainLooper().idle();

            assertNotNull(provider.bigBangRequest);
            assertEquals("recognized after release", provider.bigBangRequest.getString("text"));
            assertTrue(bitmap.isRecycled());
        } finally {
            source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            if (!bitmap.isRecycled()) bitmap.recycle();
            clearPortalSettings(context);
        }
        assertNull(field(input, "contactListener").get(input));
    }

    @Test
    public void trueTwoFingerCancelStillCancelsPendingOcr() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        DragShareSettings settings = DragShareSettings.defaults().toBuilder()
                .setTwoFingerLongPressEnabled(true)
                .setTwoFingerLongPressTimeoutMillis(600)
                .build();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        AtomicReference<AicrOcrClient.Callback> ocrCallback = new AtomicReference<>();
        AtomicBoolean ocrCancelled = new AtomicBoolean();
        PressCaptureSource source = new PressCaptureSource(context, input, controller, settings,
                (ocrContext, bitmap, callback) -> {
                    ocrCallback.set(callback);
                    return () -> ocrCancelled.set(true);
                });
        Bitmap bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        long downTime = SystemClock.uptimeMillis();
        try {
            field(source, "nodeSession").set(source, provider.connection);
            BackgroundTouchBlocker originalBlocker =
                    (BackgroundTouchBlocker) field(source, "blocker").get(source);
            originalBlocker.close();
            field(source, "blocker").set(source, new BackgroundTouchBlocker(
                    context, (target, method, args) -> null, () -> false));
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() -> {
                source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime);
                source.onContactEvent(MotionEvent.ACTION_POINTER_DOWN,
                        20f, 30f, 40f, 50f, 2, downTime + 100L);
                fieldUnchecked(source, "cached", CapturedContent.pressOcrRegion(
                        bitmap, "com.tencent.mm", new Rect()));
            });
            ShadowLooper.shadowMainLooper().idle();
            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(700));
            ShadowLooper.shadowMainLooper().idle();
            assertNotNull(ocrCallback.get());

            new Handler(Looper.getMainLooper()).post(() -> source.onContactEvent(
                    MotionEvent.ACTION_CANCEL, 20f, 30f, Float.NaN, Float.NaN, 0, downTime + 701L));
            ShadowLooper.shadowMainLooper().idle();
            assertTrue(ocrCancelled.get());

            new Handler(Looper.getMainLooper()).post(() ->
                    ocrCallback.get().onComplete("must be ignored", null));
            ShadowLooper.shadowMainLooper().idle();
            assertNull(provider.bigBangRequest);
        } finally {
            source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            if (!bitmap.isRecycled()) bitmap.recycle();
            clearPortalSettings(context);
        }
        assertNull(field(input, "contactListener").get(input));
    }

    @Test
    public void rootPreviewAlreadyActiveRejectsSecondFingerBeforeCandidateDeadline() {
        DragShareSettings settings = DragShareSettings.defaults().toBuilder()
                .setTwoFingerLongPressEnabled(true)
                .build();
        DragShareController controller = new DragShareController(
                RuntimeEnvironment.getApplication(), OverlayWindowPolicy.portal());
        PressTriggerDetector detector = new PressTriggerDetector();
        detector.start(20f, 30f, 100L, 600L);
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        try {
            assertTrue(PressCaptureSource.canStartTwoFingerCandidate(true, controller, detector,
                    200L, 20f, 30f, 40f, 50f));

            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, 100L, "root");
            ShadowLooper.shadowMainLooper().idle();
            controller.handoffToPress(100L);
            controller.showContentForPressTakeover(
                    CapturedContent.image(bitmap, "com.tencent.mm", new Rect(), true),
                    20f, 30f, settings, 100L);
            ShadowLooper.shadowMainLooper().idle();

            assertTrue(controller.isActive());
            assertFalse(PressCaptureSource.canStartTwoFingerCandidate(true, controller, detector,
                    200L, 20f, 30f, 40f, 50f));
        } finally {
            controller.destroy();
            if (!bitmap.isRecycled()) bitmap.recycle();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    private static java.lang.reflect.Field field(Object target, String name) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field result = type.getDeclaredField(name);
                result.setAccessible(true);
                return result;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void fieldUnchecked(Object target, String name, Object value) {
        try {
            field(target, name).set(target, value);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static void clearPortalSettings(Context context) {
        Settings.System.putString(context.getContentResolver(),
                PortalPressSettings.KEY_SUPPORT_DOUBLE_PRESS_MODE, null);
        Settings.System.putString(context.getContentResolver(),
                PortalPressSettings.KEY_SUPPORT_LONG_PRESS_MODE, null);
    }

    private static final class BigBangProvider extends ContentProvider {
        final IRootNodeLease lease = new IRootNodeLease.Stub() {
            @Override public Bundle capture(float x, float y, String[] blocked, boolean text,
                                            boolean images, boolean press, boolean early,
                                            IRootNodeCandidate callback) { return null; }
            @Override public Bundle insertTextAt(float x, float y, String text) { return null; }
            @Override public Bundle verifyWindow(String source, int windowId, Bundle bounds) { return bounds; }
            @Override public Bundle captureOcrRegion(String source, int windowId, Bundle bounds) { return bounds; }
            @Override public Bundle captureImage(String source, int windowId, Bundle bounds) { return bounds; }
            @Override public boolean verifySource(String source) { return true; }
            @Override public boolean isOpen() { return true; }
            @Override public void close() { }
        };
        final SharedRootNodeCapture.Connection connection = new SharedRootNodeCapture.Connection(
                RuntimeEnvironment.getApplication(), new Binder(), 10000, lease);
        Bundle bigBangRequest;

        BigBangProvider(Context context) {
            ProviderInfo info = new ProviderInfo();
            info.authority = ImageStagingClient.AUTHORITY;
            attachInfo(context, info);
        }

        @Override public boolean onCreate() { return true; }
        @Override public Cursor query(Uri uri, String[] projection, String selection,
                                      String[] args, String order) { return null; }
        @Override public String getType(Uri uri) { return null; }
        @Override public Uri insert(Uri uri, ContentValues values) { return null; }
        @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
        @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }

        @Override public Bundle call(String method, String arg, Bundle extras) {
            if (SharedRootNodeCapture.METHOD_OPEN.equals(method)) {
                Bundle result = new Bundle();
                result.putBinder("lease", lease.asBinder());
                return result;
            }
            if (BigBangOverlayLauncher.METHOD_OPEN.equals(method)) {
                bigBangRequest = new Bundle(extras);
                Bundle result = new Bundle();
                result.putBoolean("accepted", true);
                return result;
            }
            return Bundle.EMPTY;
        }
    }
}

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
import android.os.RemoteException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class TwoFingerPressTest {
    @Test
    public void twoFingerModeSinglePointerDoesNotConfirmJustAfterCandidateTimeout() throws Exception {
        assertTwoFingerSinglePointerHoldRemainsUnqualified(601L, false);
    }

    @Test
    public void twoFingerModeSinglePointerLongHoldNeverClassifiesOrConfirms() throws Exception {
        assertTwoFingerSinglePointerHoldRemainsUnqualified(3601L, true);
    }

    @Test
    public void twoFingerModeSinglePointerCannotTurnEarlyCaptureIntoOcrOrBlocker() throws Exception {
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
        AtomicBoolean ocrStarted = new AtomicBoolean();
        PressCaptureSource source = new PressCaptureSource(context, input, controller, settings,
                (ocrContext, image, callback) -> {
                    ocrStarted.set(true);
                    return () -> { };
                });
        Bitmap screenshot = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        long downTime = SystemClock.uptimeMillis();
        try {
            field(source, "nodeSession").set(source, provider.connection);
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() ->
                    source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime));
            ShadowLooper.shadowMainLooper().idle();

            // Model an early asynchronous capture result waiting for gesture qualification.
            field(source, "cached").set(source, CapturedContent.pressOcrRegion(
                    screenshot, "com.tencent.mm", new Rect(10, 20, 100, 120)));
            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(601));
            ShadowLooper.shadowMainLooper().idle();

            PressTriggerDetector detector = (PressTriggerDetector) field(source, "detector").get(source);
            assertFalse("an unqualified single pointer must not start OCR or its touch blocker",
                    ocrStarted.get());
            assertFalse("a lone pointer in two-finger mode must not confirm an early OCR capture",
                    detector.isFired());
            assertFalse(controller.isActive());
        } finally {
            source.stop();
            controller.destroy();
            if (!screenshot.isRecycled()) screenshot.recycle();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    @Test
    public void captureReturningIntoNewUnqualifiedTwoFingerGestureIsDroppedAndRecycled()
            throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        provider.captureStarted = new CountDownLatch(1);
        provider.allowCaptureReturn = new CountDownLatch(1);
        provider.regionCaptureReturned = new CountDownLatch(1);
        Bitmap screenshot = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        Rect bounds = new Rect(10, 20, 100, 120);
        provider.captureResponse = new Bundle();
        provider.captureResponse.putString("package", "com.tencent.mm");
        provider.captureResponse.putParcelable("bounds", bounds);
        provider.captureResponse.putBoolean("image", true);
        provider.captureResponse.putBoolean("press_ocr_region", true);
        provider.captureResponse.putInt("window_id", 7);
        provider.pressRegionResponse = new Bundle();
        provider.pressRegionResponse.putParcelable("bitmap", screenshot);
        provider.pressRegionResponse.putParcelable("bounds", bounds);
        provider.pressRegionResponse.putBoolean("image_cropped", true);

        DragShareSettings singleFinger = DragShareSettings.defaults().toBuilder()
                .setTwoFingerLongPressEnabled(false)
                .build();
        DragShareSettings twoFinger = singleFinger.toBuilder()
                .setTwoFingerLongPressEnabled(true)
                .setTwoFingerLongPressTimeoutMillis(600)
                .build();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        AtomicBoolean ocrStarted = new AtomicBoolean();
        PressCaptureSource source = new PressCaptureSource(context, input, controller, singleFinger,
                (ocrContext, bitmap, callback) -> {
                    ocrStarted.set(true);
                    return () -> { };
                });
        long firstDownTime = SystemClock.uptimeMillis();
        try {
            field(source, "nodeSession").set(source, provider.connection);
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f,
                    firstDownTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() -> source.onPointerEvent(
                    MotionEvent.ACTION_DOWN, 20f, 30f, firstDownTime));
            ShadowLooper.shadowMainLooper().idle();

            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(120));
            ShadowLooper.shadowMainLooper().idle();
            assertTrue("single-finger classification should start Root capture",
                    provider.captureStarted.await(5, TimeUnit.SECONDS));

            source.updateSettings(twoFinger);
            long secondGestureDownTime = firstDownTime + 1000L;
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f,
                    secondGestureDownTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() -> source.onPointerEvent(
                    MotionEvent.ACTION_DOWN, 20f, 30f, secondGestureDownTime));
            ShadowLooper.shadowMainLooper().idle();
            PressTriggerDetector detector = (PressTriggerDetector) field(source, "detector").get(source);
            assertTrue("the new gesture must be waiting for its second finger",
                    detector.isAwaitingSecondFinger());
            assertFalse(detector.isFired());

            provider.allowCaptureReturn.countDown();
            assertTrue("the stale capture should finish constructing its image result",
                    provider.regionCaptureReturned.await(5, TimeUnit.SECONDS));
            ExecutorService worker = (ExecutorService) field(source, "worker").get(source);
            worker.submit(() -> { }).get(5, TimeUnit.SECONDS);
            ShadowLooper.shadowMainLooper().idle();

            assertTrue("stale Root image content must be recycled when dropped", screenshot.isRecycled());
            assertFalse("a stale result must not start OCR or its touch blocker", ocrStarted.get());
            assertNull(provider.bigBangRequest);
            assertFalse("a stale result must not open a preview", controller.isActive());
            assertEquals(-1L, field(controller, "pressPipelineDownTime").getLong(controller));
            assertTrue("the current TWO_FINGER candidate must remain unqualified",
                    detector.isAwaitingSecondFinger());
        } finally {
            provider.allowCaptureReturn.countDown();
            source.stop();
            controller.destroy();
            if (!screenshot.isRecycled()) screenshot.recycle();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    private static void assertTwoFingerSinglePointerHoldRemainsUnqualified(
            long holdMillis, boolean assertNoFeatureEffects) throws Exception {
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
        PressCaptureSource source = new PressCaptureSource(context, input, controller, settings);
        long downTime = SystemClock.uptimeMillis();
        try {
            field(source, "nodeSession").set(source, provider.connection);
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() ->
                    source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime));
            ShadowLooper.shadowMainLooper().idle();

            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(holdMillis));
            ShadowLooper.shadowMainLooper().idle();

            PressTriggerDetector detector = (PressTriggerDetector) field(source, "detector").get(source);
            assertFalse("a lone pointer must not confirm in two-finger mode after " + holdMillis + "ms",
                    detector.isFired());
            if (assertNoFeatureEffects) {
                assertFalse("long single-finger holds must not classify or start lookup",
                        field(source, "lookupRunning").getBoolean(source));
                assertNull("long single-finger holds must not open BigBang", provider.bigBangRequest);
                assertFalse("long single-finger holds must not open preview", controller.isActive());
            }
        } finally {
            source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    @Test
    public void singleFingerModeStillConfirmsAStationarySinglePointer() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        DragShareSettings settings = DragShareSettings.defaults().toBuilder()
                .setTwoFingerLongPressEnabled(false)
                .build();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        PressCaptureSource source = new PressCaptureSource(context, input, controller, settings);
        long downTime = SystemClock.uptimeMillis();
        try {
            field(source, "nodeSession").set(source, provider.connection);
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() ->
                    source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime));
            ShadowLooper.shadowMainLooper().idle();

            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(2000));
            ShadowLooper.shadowMainLooper().idle();

            PressTriggerDetector detector = (PressTriggerDetector) field(source, "detector").get(source);
            assertTrue("single-finger mode must preserve the existing one-finger press", detector.isFired());
        } finally {
            source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    @Test
    public void twoFingerModeDoesNotDowngradeAfterSecondFingerIsReleased() throws Exception {
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
        PressCaptureSource source = new PressCaptureSource(context, input, controller, settings);
        long downTime = SystemClock.uptimeMillis();
        try {
            field(source, "nodeSession").set(source, provider.connection);
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() -> {
                source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime);
                source.onContactEvent(MotionEvent.ACTION_POINTER_DOWN,
                        20f, 30f, 40f, 50f, 2, downTime + 100L);
                source.onContactEvent(MotionEvent.ACTION_POINTER_UP,
                        20f, 30f, 40f, 50f, 1, downTime + 200L);
            });
            ShadowLooper.shadowMainLooper().idle();

            // F1 remains down after F2's UP; it must not resume a single-finger timer.
            new Handler(Looper.getMainLooper()).post(() ->
                    source.onPointerEvent(MotionEvent.ACTION_MOVE, 20f, 30f, downTime + 300L));
            ShadowLooper.shadowMainLooper().idle();

            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(3000));
            ShadowLooper.shadowMainLooper().idle();

            PressTriggerDetector detector = (PressTriggerDetector) field(source, "detector").get(source);
            assertFalse("lifting finger two must not restore single-finger confirmation", detector.isFired());
            assertNull(provider.bigBangRequest);
        } finally {
            source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    @Test
    public void switchingFromSingleToTwoFingerModeRejectsTheNextSinglePointer() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        DragShareSettings singleFinger = DragShareSettings.defaults().toBuilder()
                .setTwoFingerLongPressEnabled(false).build();
        DragShareSettings twoFinger = singleFinger.toBuilder()
                .setTwoFingerLongPressEnabled(true)
                .setTwoFingerLongPressTimeoutMillis(600)
                .build();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        PressCaptureSource source = new PressCaptureSource(context, input, controller, singleFinger);
        long downTime = SystemClock.uptimeMillis();
        try {
            field(source, "nodeSession").set(source, provider.connection);
            source.updateSettings(twoFinger);
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() ->
                    source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime));
            ShadowLooper.shadowMainLooper().idle();

            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(3000));
            ShadowLooper.shadowMainLooper().idle();

            PressTriggerDetector detector = (PressTriggerDetector) field(source, "detector").get(source);
            assertFalse("the newly selected two-finger mode must gate the next single pointer",
                    detector.isFired());
            assertNull(provider.bigBangRequest);
        } finally {
            source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    @Test
    public void switchingFromTwoFingerToSingleModeRestoresSinglePointerConfirmation() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        clearPortalSettings(context);
        BigBangProvider provider = new BigBangProvider(context);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        DragShareSettings twoFinger = DragShareSettings.defaults().toBuilder()
                .setTwoFingerLongPressEnabled(true)
                .setTwoFingerLongPressTimeoutMillis(600)
                .build();
        DragShareSettings singleFinger = twoFinger.toBuilder()
                .setTwoFingerLongPressEnabled(false)
                .build();
        DragShareController controller = new DragShareController(context, OverlayWindowPolicy.portal());
        RootTouchSource input = new RootTouchSource(context,
                (action, x, y, time, major, minor, width) -> { }, (ready, reason) -> { });
        field(input, "ready").setBoolean(input, true);
        PressCaptureSource source = new PressCaptureSource(context, input, controller, twoFinger);
        long downTime = SystemClock.uptimeMillis();
        try {
            field(source, "nodeSession").set(source, provider.connection);
            source.updateSettings(singleFinger);
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() ->
                    source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime));
            ShadowLooper.shadowMainLooper().idle();

            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(2000));
            ShadowLooper.shadowMainLooper().idle();

            PressTriggerDetector detector = (PressTriggerDetector) field(source, "detector").get(source);
            assertTrue("the newly selected single-finger mode must restore one-finger confirmation",
                    detector.isFired());
        } finally {
            source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    @Test
    public void asynchronousPortalImageArrivingBeforeTwoFingerQualificationCannotTakeOverPress()
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
        PressCaptureSource source = new PressCaptureSource(context, input, controller, settings);
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        try {
            field(source, "nodeSession").set(source, provider.connection);
            long downTime = SystemClock.uptimeMillis();
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() ->
                    source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime));
            ShadowLooper.shadowMainLooper().idle();

            CapturedContent portalImage = CapturedContent.portalMediaImage(
                    bitmap, "com.tencent.mm", new Rect(0, 0, 2, 2));
            boolean handled = source.acceptWeChatPortalContent(portalImage);
            ShadowLooper.shadowMainLooper().idle();

            assertTrue("MMGG must handle the owned early callback to prevent host fallback", handled);
            assertTrue("early Portal payload should remain cached pending qualification",
                    field(source, "cached").get(source) == portalImage);
            assertFalse("an unqualified first finger must not open a preview", controller.isActive());
            assertEquals(-1L, field(controller, "pressPipelineDownTime").getLong(controller));
        } finally {
            source.stop();
            controller.destroy();
            if (!bitmap.isRecycled()) bitmap.recycle();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    @Test
    public void twoFingerPortalImageWaitsForConfirmationBeforePreviewTakeover() throws Exception {
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
        PressCaptureSource source = new PressCaptureSource(context, input, controller, settings);
        Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        Bitmap screenshot = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
        try {
            field(source, "nodeSession").set(source, provider.connection);
            long downTime = SystemClock.uptimeMillis();
            controller.acceptPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime, "root");
            ShadowLooper.shadowMainLooper().idle();
            new Handler(Looper.getMainLooper()).post(() -> {
                source.onPointerEvent(MotionEvent.ACTION_DOWN, 20f, 30f, downTime);
                source.onContactEvent(MotionEvent.ACTION_POINTER_DOWN,
                        20f, 30f, 40f, 50f, 2, downTime + 100L);
            });
            ShadowLooper.shadowMainLooper().idle();

            Object pending = pendingWeChat(CapturedContent.pressOcrRegion(
                    screenshot, "com.tencent.mm", new Rect()));
            field(source, "pendingWeChat").set(source, pending);
            CapturedContent portalImage = CapturedContent.portalMediaImage(
                    bitmap, "com.tencent.mm", new Rect(0, 0, 2, 2));
            assertTrue(source.acceptWeChatPortalContent(portalImage));
            ShadowLooper.shadowMainLooper().idle();

            assertTrue("the image should be cached while the two-finger timer is pending",
                    field(source, "cached").get(source) == portalImage);
            assertFalse("second-finger DOWN alone must not take over the Portal image",
                    controller.isActive());
            assertEquals(-1L, field(controller, "pressPipelineDownTime").getLong(controller));

            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(599));
            ShadowLooper.shadowMainLooper().idle();
            assertFalse("preview must remain closed before the complete confirmation duration",
                    controller.isActive());

            ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(101));
            ShadowLooper.shadowMainLooper().idle();
            assertTrue("confirmed two-finger press may now display the cached image",
                    controller.isActive());
        } finally {
            source.stop();
            controller.destroy();
            if (!bitmap.isRecycled()) bitmap.recycle();
            if (!screenshot.isRecycled()) screenshot.recycle();
            ShadowLooper.shadowMainLooper().idle();
            clearPortalSettings(context);
        }
    }

    private static Object pendingWeChat(CapturedContent content) throws Exception {
        Class<?> type = Class.forName(PressCaptureSource.class.getName() + "$PendingWeChat");
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(
                CapturedContent.class, long.class, String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(content, 1L, "test");
    }

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
            assertEquals("confirmed two-finger press must open BigBang exactly once",
                    1, provider.bigBangOpenCount);
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
        volatile Bundle captureResponse;
        volatile Bundle pressRegionResponse;
        volatile CountDownLatch captureStarted;
        volatile CountDownLatch allowCaptureReturn;
        volatile CountDownLatch regionCaptureReturned;
        final IRootNodeLease lease = new IRootNodeLease.Stub() {
            @Override public Bundle capture(float x, float y, String[] blocked, boolean text,
                                            boolean images, boolean press, boolean early,
                                            IRootNodeCandidate callback) throws RemoteException {
                CountDownLatch started = captureStarted;
                if (started != null) started.countDown();
                CountDownLatch release = allowCaptureReturn;
                if (release != null) {
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new RemoteException("timed out waiting for test capture release");
                        }
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new RemoteException("test capture interrupted: " + error.getMessage());
                    }
                }
                return captureResponse;
            }
            @Override public Bundle insertTextAt(float x, float y, String text) { return null; }
            @Override public Bundle verifyWindow(String source, int windowId, Bundle bounds) { return bounds; }
            @Override public Bundle captureOcrRegion(String source, int windowId, Bundle bounds) {
                Bundle response = pressRegionResponse;
                if (response != null && regionCaptureReturned != null) {
                    regionCaptureReturned.countDown();
                    return response;
                }
                return bounds;
            }
            @Override public Bundle captureImage(String source, int windowId, Bundle bounds) { return bounds; }
            @Override public boolean verifySource(String source) { return true; }
            @Override public boolean isOpen() { return true; }
            @Override public void close() { }
        };
        final SharedRootNodeCapture.Connection connection = new SharedRootNodeCapture.Connection(
                RuntimeEnvironment.getApplication(), new Binder(), 10000, lease);
        Bundle bigBangRequest;
        int bigBangOpenCount;

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
                bigBangOpenCount++;
                bigBangRequest = new Bundle(extras);
                Bundle result = new Bundle();
                result.putBoolean("accepted", true);
                return result;
            }
            return Bundle.EMPTY;
        }
    }
}

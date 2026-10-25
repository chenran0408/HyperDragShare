package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSystemClock;

/** Exercises the real reader retry coordinator through a fake authorized lease endpoint. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public final class PressReaderRetryTest {
    @Test public void failedAttemptRetriesAfterConfiguredDelayThenDeathRecoversImmediately() throws Exception {
        Context base = RuntimeEnvironment.getApplication();
        TestProvider provider = new TestProvider(base);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, provider);
        Context context = new PortalContext(base);
        DragShareController controller = new DragShareController(base, OverlayWindowPolicy.portal());
        PressCaptureSource source = null;
        try {
            source = new PressCaptureSource(context, null, controller, DragShareSettings.defaults());

            Bundle failed = awaitOpen(provider);
            awaitWorker(source);
            String failedAttempt = failed.getString("attempt_id");
            ShadowLooper.shadowMainLooper().idle();
            assertNotNull(failedAttempt);
            assertEquals(3000L, field(source, "readerRetryDelayMs").getLong(source));
            assertNull(provider.openRequests.poll());

            ShadowSystemClock.advanceBy(Duration.ofMillis(2999));
            ShadowLooper.shadowMainLooper().idle();
            assertNull(provider.openRequests.poll());
            ShadowSystemClock.advanceBy(Duration.ofMillis(1));
            ShadowLooper.shadowMainLooper().idle();

            Bundle recovered = awaitOpen(provider);
            awaitWorker(source);
            String recoveredAttempt = recovered.getString("attempt_id");
            ShadowLooper.shadowMainLooper().idle();
            assertNotNull(recoveredAttempt);
            assertTrue(!failedAttempt.equals(recoveredAttempt));
            assertNotNull(event(provider, recoveredAttempt, "lease_ready"));

            android.os.IBinder.DeathRecipient death = (android.os.IBinder.DeathRecipient)
                    field(source, "leaseDeath").get(source);
            assertNotNull(death);
            death.binderDied();
            ShadowLooper.shadowMainLooper().idle();

            Bundle afterDeath = awaitOpen(provider);
            awaitWorker(source);
            String recoveredAgain = afterDeath.getString("attempt_id");
            ShadowLooper.shadowMainLooper().idle();
            assertNotNull(recoveredAgain);
            assertTrue(!recoveredAttempt.equals(recoveredAgain));

            Bundle firstRetry = event(provider, failedAttempt, "retry_scheduled");
            assertEquals(3000L, firstRetry.getLong(ModuleActivation.EXTRA_ROOT_CONNECTION_RETRY_MS));
            assertEquals(DragShareLog.processInstance(),
                    firstRetry.getString(ModuleActivation.EXTRA_ROOT_CONNECTION_PROC));
            Bundle deathEvent = event(provider, recoveredAttempt, "binder_death");
            assertNotNull(deathEvent);
            Bundle immediateRetry = event(provider, recoveredAttempt, "retry_scheduled");
            assertEquals(0L, immediateRetry.getLong(ModuleActivation.EXTRA_ROOT_CONNECTION_RETRY_MS));
            assertEquals("retry_scheduled", immediateRetry.getString(ModuleActivation.EXTRA_ROOT_CONNECTION_OUTCOME));
        } finally {
            if (source != null) source.stop();
            controller.destroy();
            ShadowLooper.shadowMainLooper().idle();
        }
    }

    private static void awaitWorker(PressCaptureSource source) throws Exception {
        ((java.util.concurrent.ExecutorService) field(source, "worker").get(source))
                .submit(() -> { }).get(5, TimeUnit.SECONDS);
    }

    private static Bundle awaitOpen(TestProvider provider) throws InterruptedException {
        Bundle result = provider.openRequests.poll(5, TimeUnit.SECONDS);
        assertNotNull("Root lease request did not arrive", result);
        return result;
    }

    private static Bundle event(TestProvider provider, String attemptId, String expectedOutcome) {
        for (Bundle report : provider.statusReports) {
            if (attemptId.equals(report.getString(ModuleActivation.EXTRA_ROOT_CONNECTION_ATTEMPT))
                    && expectedOutcome.equals(report.getString(ModuleActivation.EXTRA_ROOT_CONNECTION_OUTCOME))) {
                return report;
            }
        }
        throw new AssertionError("No " + expectedOutcome + " status for attempt " + attemptId);
    }

    private static java.lang.reflect.Field field(Object target, String name) throws Exception {
        java.lang.reflect.Field result = target.getClass().getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static final class PortalContext extends ContextWrapper {
        PortalContext(Context base) { super(base); }
        @Override public String getPackageName() { return MainHook.TAPLUS_PACKAGE; }
        @Override public Context getApplicationContext() { return this; }
    }

    private static final class TestProvider extends ContentProvider {
        final LinkedBlockingQueue<Bundle> openRequests = new LinkedBlockingQueue<>();
        final LinkedBlockingQueue<Bundle> statusReports = new LinkedBlockingQueue<>();
        private int opens;

        TestProvider(Context context) {
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

        @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
            if (SharedRootNodeCapture.METHOD_OPEN.equals(method)) {
                openRequests.offer(new Bundle(extras));
                if (++opens == 1) throw new IllegalStateException("private provider detail");
                IRootNodeLease lease = newLease();
                Bundle response = new Bundle();
                response.putBinder("lease", lease.asBinder());
                response.putString("attempt_id", extras.getString("attempt_id"));
                return response;
            }
            if (ModuleActivation.METHOD_REPORT_INJECTED.equals(method)) {
                statusReports.offer(new Bundle(extras));
                return Bundle.EMPTY;
            }
            return Bundle.EMPTY;
        }

        private static IRootNodeLease newLease() {
            return new IRootNodeLease.Stub() {
                @Override public Bundle capture(float x, float y, String[] blocked, boolean text,
                                                boolean images, boolean press, boolean early,
                                                IRootNodeCandidate callback) { return null; }
                @Override public Bundle verifyWindow(String source, int windowId, Bundle bounds) { return null; }
                @Override public Bundle captureOcrRegion(String source, int windowId, Bundle bounds) { return null; }
                @Override public Bundle captureImage(String source, int windowId, Bundle bounds) { return null; }
                @Override public boolean verifySource(String source) { return false; }
                @Override public Bundle insertTextAt(float x, float y, String text) { return null; }
                @Override public boolean isOpen() { return true; }
                @Override public void close() { }
            };
        }
    }
}

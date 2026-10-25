package com.leaf.hyperdragshare.codex;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33)
public final class AicrOcrClientTest {
    @Before public void configureLogging() {
        DragShareLog.configure(DragShareSettings.defaults());
        ShadowLog.clear();
    }

    @Test public void successIsDeliveredOnMainAfterReleaseAndUnbind() throws Exception {
        FakeContext context = new FakeContext(false);
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        Completion completion = new Completion(context, bitmap);
        AicrOcrClient.recognize(context, bitmap, completion);
        awaitCompletion(completion);
        assertEquals("测试 123", completion.text);
        assertNull(completion.error);
        assertEquals(1, context.releases.get());
        assertEquals(1, context.unbinds);
        assertFalse(bitmap.isRecycled());
        bitmap.recycle();
    }

    @Test public void cancellationDoesNotFinishWhileBinderStillUsesImageAndDoesNotQueueMoreWork()
            throws Exception {
        FakeContext context = new FakeContext(true);
        Bitmap first = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        Bitmap second = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        Completion completion = new Completion(context, first);
        AicrOcrClient.Request request = AicrOcrClient.recognize(context, first, completion);
        ShadowLooper.shadowMainLooper().idle();
        assertTrue(context.detectEntered.await(3, TimeUnit.SECONDS));
        Completion busy = new Completion(null, second);
        AicrOcrClient.recognize(context, second, busy);
        awaitCompletion(busy);
        assertNotNull(busy.error);
        assertEquals(1, context.binds);
        request.cancel();
        ShadowLooper.shadowMainLooper().idle();
        assertEquals(0, completion.count);
        assertFalse(first.isRecycled());
        context.allowDetect.countDown();
        awaitCompletion(completion);
        assertTrue(completion.error instanceof CancellationException);
        assertNull(completion.text);
        assertEquals(0, context.recognizes.get());
        assertEquals(1, context.releases.get());
        assertEquals(1, context.unbinds);
        first.recycle();
        second.recycle();
    }

    @Test public void cancellationBeforeBindingStillCompletesWithoutTakingAConnection() throws Exception {
        FakeContext context = new FakeContext(false);
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        Completion completion = new Completion(null, bitmap);
        AicrOcrClient.Request request = AicrOcrClient.recognize(context, bitmap, completion);
        request.cancel();
        awaitCompletion(completion);
        assertTrue(completion.error instanceof CancellationException);
        assertEquals(0, context.binds);
        assertEquals(0, context.unbinds);
        bitmap.recycle();
    }

    @Test public void missingServiceFailsBeforeBindAndReleasesSlotForNextRequest() throws Exception {
        assertPreflightFailureAndSlotReleased(
                new FakeContext(false, false, true, true, false),
                "系统 AICR 服务未安装或组件缺失");
    }

    @Test public void disabledApplicationFailsBeforeBindAndReleasesSlotForNextRequest() throws Exception {
        assertPreflightFailureAndSlotReleased(
                new FakeContext(false, true, false, true, false),
                "系统 AICR 服务已停用");
    }

    @Test public void disabledServiceComponentFailsBeforeBindAndReleasesSlotForNextRequest() throws Exception {
        assertPreflightFailureAndSlotReleased(
                new FakeContext(false, true, true, false, false),
                "系统 AICR 服务组件已停用");
    }

    @Test public void packageLookupPermissionFailureIsReportedWithoutBinding() throws Exception {
        FakeContext context = new FakeContext(false, true, true, true, true);
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        Completion completion = new Completion(null, bitmap);
        AicrOcrClient.recognize(context, bitmap, completion);
        awaitCompletion(completion);
        assertTrue(completion.error.getMessage().contains("系统拒绝连接 AICR，权限受限"));
        assertEquals(0, context.binds);
        assertEquals(0, context.unbinds);
        bitmap.recycle();
    }

    @Test public void unsupportedOcrCapabilityIsReportedAsSystemCapability() throws Exception {
        ShadowLog.clear();
        Completion completion = recognize(new FakeContext(false, true, true, true, false, 7, "V2", 1));
        assertEquals("系统 AICR 不支持图片识字，请检查系统智能服务版本", completion.error.getMessage());
        assertFalse(completion.error.getMessage().contains("无法读取这张图片"));
        String events = operationEvents();
        assertTrue(events.contains("stage=engine outcome=plugin_status_7"));
        assertTrue(events.contains("stage=release outcome=released"));
        assertTrue(events.indexOf("stage=core_interface outcome=matched")
                < events.indexOf("stage=engine outcome=plugin_status_7"));
        assertTrue(events.indexOf("stage=engine outcome=plugin_status_7")
                < events.indexOf("stage=release outcome=released"));
        assertFalse(events.contains("stage=image "));
    }

    @Test public void incompatibleOcrVersionIsReportedBeforeImageSubmission() throws Exception {
        ShadowLog.clear();
        FakeContext context = new FakeContext(false, true, true, true, false, 0, "V1", 1);
        Completion completion = recognize(context);
        assertTrue(completion.error.getMessage().contains("OCR 版本不受支持"));
        assertEquals(0, context.visionImageSubmissions);
        String events = operationEvents();
        assertTrue(events.contains("stage=interface_version outcome=version_unsupported"));
        assertTrue(events.indexOf("stage=vision_interface outcome=matched")
                < events.indexOf("stage=interface_version outcome=version_unsupported"));
        assertFalse(events.contains("stage=image "));
    }

    @Test public void missingOcrEngineIsReportedSeparatelyFromCapabilityAndImage() throws Exception {
        ShadowLog.clear();
        Completion completion = recognize(new FakeContext(false, true, true, true, false, 5, "V2", 1));
        assertTrue(completion.error.getMessage().contains("AICR 未提供 OCR 引擎"));
        assertTrue(operationEvents().contains("stage=engine outcome=engine_not_ready"));
    }

    @Test public void unreadableImageIsReportedSeparately() throws Exception {
        ShadowLog.clear();
        Completion completion = recognize(new FakeContext(false, true, true, true, false, 0, "V2", -1));
        assertTrue(completion.error.getMessage().contains("无法读取这张图片"));
        assertTrue(operationEvents().contains("stage=image outcome=image_rejected"));
    }

    private static Completion recognize(FakeContext context) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        Completion completion = new Completion(context, bitmap);
        AicrOcrClient.recognize(context, bitmap, completion);
        awaitCompletion(completion);
        bitmap.recycle();
        return completion;
    }

    private static String operationEvents() {
        StringBuilder events = new StringBuilder();
        for (ShadowLog.LogItem item : ShadowLog.getLogsForTag("DragShare/Operation")) {
            events.append(item.msg).append('\n');
        }
        return events.toString();
    }

    private static void assertPreflightFailureAndSlotReleased(FakeContext unavailable,
            String expectedMessage) throws Exception {
        Bitmap unavailableBitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        Completion unavailableCompletion = new Completion(null, unavailableBitmap);
        AicrOcrClient.recognize(unavailable, unavailableBitmap, unavailableCompletion);
        awaitCompletion(unavailableCompletion);
        assertTrue(unavailableCompletion.error.getMessage().contains(expectedMessage));
        assertEquals(0, unavailable.binds);
        assertEquals(0, unavailable.unbinds);

        FakeContext available = new FakeContext(false);
        Bitmap availableBitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        Completion availableCompletion = new Completion(available, availableBitmap);
        AicrOcrClient.recognize(available, availableBitmap, availableCompletion);
        awaitCompletion(availableCompletion);
        assertNull(availableCompletion.error);
        assertEquals(1, available.binds);
        unavailableBitmap.recycle();
        availableBitmap.recycle();
    }

    private static void awaitCompletion(Completion completion) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (completion.count == 0 && System.nanoTime() < deadline) {
            ShadowLooper.shadowMainLooper().idle();
            Thread.sleep(5);
        }
        assertEquals("completion must be delivered once", 1, completion.count);
    }

    private static final class Completion implements AicrOcrClient.Callback {
        final FakeContext context;
        final Bitmap bitmap;
        int count;
        String text;
        Exception error;

        Completion(FakeContext context, Bitmap bitmap) {
            this.context = context;
            this.bitmap = bitmap;
        }

        @Override public void onComplete(String text, Exception error) {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            assertFalse(bitmap.isRecycled());
            if (context != null) assertEquals(1, context.unbinds);
            this.text = text;
            this.error = error;
            count++;
        }
    }

    private static final class FakeContext extends ContextWrapper {
        final CountDownLatch detectEntered = new CountDownLatch(1);
        final CountDownLatch allowDetect;
        final AtomicInteger releases = new AtomicInteger();
        final AtomicInteger recognizes = new AtomicInteger();
        private final int engineStatus;
        private final String pluginVersion;
        private final int imageSession;
        int visionImageSubmissions;
        int binds;
        int unbinds;
        private final boolean denyPackageLookup;
        final Binder vision = new Binder() {
            { attachInterface(null, "com.xiaomi.aicr.plugin.IVisionService"); }
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                data.enforceInterface("com.xiaomi.aicr.plugin.IVisionService");
                reply.writeNoException();
                if (code == 6) reply.writeString(pluginVersion);
                else if (code == 1) {
                    data.readTypedObject(Bitmap.CREATOR);
                    visionImageSubmissions++;
                    reply.writeInt(imageSession);
                } else if (code == 3) {
                    data.readInt();
                    detectEntered.countDown();
                    try {
                        if (!allowDetect.await(4, TimeUnit.SECONDS)) {
                            throw new RemoteException("test gate timed out");
                        }
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                        throw new RemoteException("interrupted");
                    }
                    reply.writeInt(1);
                } else if (code == 4) {
                    recognizes.incrementAndGet();
                    data.readInt();
                    reply.writeInt(1);
                    reply.writeInt(-1);
                    reply.writeString("测试 123");
                    reply.writeInt(-1);
                } else return false;
                return true;
            }
        };
        final Binder core = new Binder() {
            { attachInterface(null, "com.xiaomi.aicr.IAiCrCoreService"); }
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
                data.enforceInterface("com.xiaomi.aicr.IAiCrCoreService");
                reply.writeNoException();
                if (code == 1) {
                    data.readStrongBinder();
                    data.readString();
                    assertEquals("com.xiaomi.aicr.plugin.IVisionService", data.readString());
                    assertEquals("V2", data.readString());
                    reply.writeStrongBinder(vision);
                    reply.writeInt(1);
                    Bundle status = new Bundle();
                    status.putInt("Status", engineStatus);
                    status.writeToParcel(reply, 0);
                } else if (code == 2) {
                    data.readString();
                    releases.incrementAndGet();
                } else return false;
                return true;
            }
        };

        FakeContext(boolean blockDetection) {
            this(blockDetection, true, true, true, false);
        }

        FakeContext(boolean blockDetection, boolean installService, boolean applicationEnabled,
                boolean serviceEnabled, boolean denyPackageLookup) {
            this(blockDetection, installService, applicationEnabled, serviceEnabled,
                    denyPackageLookup, 0, "V2", 1);
        }

        FakeContext(boolean blockDetection, boolean installService, boolean applicationEnabled,
                boolean serviceEnabled, boolean denyPackageLookup, int engineStatus,
                String pluginVersion, int imageSession) {
            super(RuntimeEnvironment.getApplication());
            this.engineStatus = engineStatus;
            this.pluginVersion = pluginVersion;
            this.imageSession = imageSession;
            allowDetect = new CountDownLatch(blockDetection ? 1 : 0);
            this.denyPackageLookup = denyPackageLookup;
            if (installService) {
                PackageInfo packageInfo = new PackageInfo();
                packageInfo.packageName = "com.xiaomi.aicr";
                ApplicationInfo applicationInfo = new ApplicationInfo();
                applicationInfo.packageName = packageInfo.packageName;
                applicationInfo.enabled = applicationEnabled;
                packageInfo.applicationInfo = applicationInfo;
                ServiceInfo serviceInfo = new ServiceInfo();
                serviceInfo.packageName = packageInfo.packageName;
                serviceInfo.name = "com.xiaomi.aicr.access.AiCrCoreService";
                serviceInfo.applicationInfo = applicationInfo;
                serviceInfo.enabled = serviceEnabled;
                packageInfo.services = new ServiceInfo[] { serviceInfo };
                ShadowPackageManager packages = Shadow.extract(super.getPackageManager());
                packages.installPackage(packageInfo);
            }
        }

        @Override public Context getApplicationContext() { return this; }
        @Override public PackageManager getPackageManager() {
            if (denyPackageLookup) throw new SecurityException("test permission denial");
            return super.getPackageManager();
        }
        @Override public boolean bindService(Intent service, ServiceConnection connection, int flags) {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            binds++;
            connection.onServiceConnected(new ComponentName("com.xiaomi.aicr", "Core"), core);
            return true;
        }
        @Override public void unbindService(ServiceConnection connection) {
            assertSame(Looper.getMainLooper(), Looper.myLooper());
            unbinds++;
        }
    }
}

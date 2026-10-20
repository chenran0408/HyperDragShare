package com.leaf.hyperdragshare.codex;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.annotation.Config;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33)
public final class AicrOcrClientTest {
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
        int binds;
        int unbinds;
        final Binder vision = new Binder() {
            { attachInterface(null, "com.xiaomi.aicr.plugin.IVisionService"); }
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                data.enforceInterface("com.xiaomi.aicr.plugin.IVisionService");
                reply.writeNoException();
                if (code == 6) reply.writeString("V2");
                else if (code == 1) {
                    data.readTypedObject(Bitmap.CREATOR);
                    reply.writeInt(1);
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
                    status.putInt("Status", 0);
                    status.writeToParcel(reply, 0);
                } else if (code == 2) {
                    data.readString();
                    releases.incrementAndGet();
                } else return false;
                return true;
            }
        };

        FakeContext(boolean blockDetection) {
            super(RuntimeEnvironment.getApplication());
            allowDetect = new CountDownLatch(blockDetection ? 1 : 0);
        }

        @Override public Context getApplicationContext() { return this; }
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

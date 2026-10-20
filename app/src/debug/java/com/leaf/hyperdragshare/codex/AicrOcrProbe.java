package com.leaf.hyperdragshare.codex;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Manually invoked debug instrumentation. Uses only a generated image, never screen contents.
 * No permission grants, root identity, automatic model-download retries or production entry point.
 */
public final class AicrOcrProbe extends Instrumentation {
    private static final String TAG = "DragShare/AicrProbe";
    private static final String CORE = "com.xiaomi.aicr.IAiCrCoreService";
    private static final String VISION = "com.xiaomi.aicr.plugin.IVisionService";
    private static final String CALLBACK = "com.xiaomi.aicr.ICoreServiceCallback";
    private final CountDownLatch connected = new CountDownLatch(1);
    private volatile IBinder core;
    private String stage = "bind";
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            core = binder;
            connected.countDown();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            core = null;
        }
        @Override public void onNullBinding(ComponentName name) {
            connected.countDown();
        }
        @Override public void onBindingDied(ComponentName name) {
            core = null;
            connected.countDown();
        }
    };

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Context context = getTargetContext();
        Bundle report = new Bundle();
        report.putInt("uid", Process.myUid());
        long started = SystemClock.elapsedRealtime();
        String address = context.getPackageName() + ":ocr-probe:" + UUID.randomUUID();
        boolean bound = false;
        IBinder service = null;
        boolean requestedPlugin = false;
        Bitmap image = null;
        int result = Activity.RESULT_CANCELED;
        try {
            Intent intent = new Intent("com.xiaomi.aicr.access.AICR_ENGINE")
                    .setComponent(new ComponentName("com.xiaomi.aicr",
                            "com.xiaomi.aicr.access.AiCrCoreService"))
                    .putExtra("bind_type", address);
            mark("bind");
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            if (!bound) throw new IllegalStateException("bindService returned false");
            if (!connected.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("service connection timed out");
            }
            service = core;
            if (service == null) throw new IllegalStateException("service returned no Binder");
            if (!CORE.equals(service.getInterfaceDescriptor())) {
                throw new IllegalStateException("unexpected core descriptor");
            }
            mark("get-plugin");
            requestedPlugin = true;
            IBinder vision = plugin(service, address, report);
            if (vision == null) throw new IllegalStateException("plugin unavailable; see status");
            if (!VISION.equals(vision.getInterfaceDescriptor())) {
                throw new IllegalStateException("unexpected vision descriptor");
            }
            mark("version");
            String version = version(vision);
            report.putString("version", version);
            if (!"V2".equals(version)) throw new IllegalStateException("requires OCR V2");
            image = Bitmap.createBitmap(640, 160, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(image);
            canvas.drawColor(Color.WHITE);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.BLACK);
            paint.setTextSize(56);
            canvas.drawText("HyperDragShare 123", 20, 100, paint);
            mark("set-image");
            int session = setImage(vision, image);
            if (session < 0) throw new IllegalStateException("image rejected: " + session);
            mark("detect");
            detect(vision, session);
            mark("recognize");
            String text = recognize(vision, session);
            report.putString("text", text);
            if (text == null || !text.contains("123")) {
                throw new IllegalStateException("synthetic image marker not recognized");
            }
            result = Activity.RESULT_OK;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            report.putString("error", stage + ": interrupted");
            Log.w(TAG, "probe interrupted", error);
        } catch (RemoteException | RuntimeException error) {
            report.putString("error", stage + ": " + error);
            Log.w(TAG, "probe failed at " + stage, error);
        } finally {
            if (requestedPlugin && service != null) {
                try {
                    release(service, address);
                    report.putBoolean("connectionReleased", true);
                } catch (RemoteException | RuntimeException error) {
                    result = Activity.RESULT_CANCELED;
                    report.putString("cleanupError", error.toString());
                    Log.w(TAG, "release failed", error);
                }
            }
            if (bound) context.unbindService(connection);
            if (image != null) image.recycle();
            report.putString("stage", stage);
            report.putLong("elapsedMs", SystemClock.elapsedRealtime() - started);
            finish(result, report);
        }
    }

    private void mark(String value) {
        stage = value;
        Log.i(TAG, "stage=" + value + " uid=" + Process.myUid());
    }

    private static IBinder plugin(IBinder service, String address, Bundle report)
            throws RemoteException {
        Binder callback = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                if (code == INTERFACE_TRANSACTION) {
                    if (reply != null) reply.writeString(CALLBACK);
                    return true;
                }
                if (code == 1 || code == 2) {
                    data.enforceInterface(CALLBACK);
                    if (code == 1) {
                        data.readStrongBinder();
                        Log.i(TAG, "download callback received; probe does not retry");
                    } else {
                        data.readString();
                        long progress = data.readLong();
                        Log.i(TAG, "download progress=" + progress);
                    }
                    return true;
                }
                return super.onTransact(code, data, reply, flags);
            }
        };
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(CORE);
            data.writeStrongBinder(callback);
            data.writeString(address);
            data.writeString(VISION);
            data.writeString("V2");
            transact(service, 1, data, reply);
            IBinder binder = reply.readStrongBinder();
            Bundle status = new Bundle();
            if (reply.readInt() != 0) status.readFromParcel(reply);
            if (!status.containsKey("Status")) {
                throw new IllegalStateException("missing plugin status");
            }
            report.putInt("status", status.getInt("Status"));
            if (status.getInt("Status") != 0) return null;
            return binder;
        } finally { reply.recycle(); data.recycle(); }
    }

    private static String version(IBinder vision) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(VISION);
            transact(vision, 6, data, reply);
            return reply.readString();
        } finally { reply.recycle(); data.recycle(); }
    }

    private static int setImage(IBinder vision, Bitmap bitmap) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(VISION);
            data.writeTypedObject(bitmap, 0);
            transact(vision, 1, data, reply);
            return reply.readInt();
        } finally { reply.recycle(); data.recycle(); }
    }

    private static void detect(IBinder vision, int session) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(VISION);
            data.writeInt(session);
            transact(vision, 3, data, reply);
            int count = reply.readInt();
            if (count <= 0) throw new IllegalStateException("no detected text: " + count);
            // Detection populates the service's location cache. Recognition reads the full result.
        } finally { reply.recycle(); data.recycle(); }
    }

    private static String recognize(IBinder vision, int session) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(VISION);
            data.writeInt(session);
            transact(vision, 4, data, reply);
            return AicrV2ResultReader.readText(reply);
        } finally { reply.recycle(); data.recycle(); }
    }

    private static void release(IBinder service, String address) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(CORE);
            data.writeString(address);
            transact(service, 2, data, reply);
        } finally { reply.recycle(); data.recycle(); }
    }

    private static void transact(IBinder binder, int code, Parcel data, Parcel reply)
            throws RemoteException {
        if (!binder.transact(code, data, reply, 0)) {
            throw new RemoteException("unsupported transaction " + code);
        }
        reply.readException();
    }
}

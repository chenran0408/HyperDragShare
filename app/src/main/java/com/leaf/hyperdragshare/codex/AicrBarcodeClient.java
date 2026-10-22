package com.leaf.hyperdragshare.codex;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.SystemClock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** App-UID client for the barcode plugin shipped in AICR 4.12.26. */
public final class AicrBarcodeClient {
    private static final String TAG = "DragShare/AicrBarcode";
    private static final String CORE = "com.xiaomi.aicr.IAiCrCoreService";
    private static final String BARCODE = "com.xiaomi.aicr.plugin.IBarcodeService";
    private static final String CORE_CALLBACK = "com.xiaomi.aicr.ICoreServiceCallback";
    private static final String RESULT_CALLBACK = "com.xiaomi.aicr.barcode.IBarcodeResultListener";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AicrBarcodeClient() {}

    public interface Callback {
        /** Main thread, after cleanup. The caller retains the bitmap until this callback. */
        void onComplete(List<String> values, Exception error);
    }

    public static Request scan(Context context, Bitmap bitmap, Callback callback) {
        Request request = new Request(context.getApplicationContext(), bitmap, callback);
        MAIN.post(request::bind);
        return request;
    }

    public static final class Request {
        private final Context context;
        private final Bitmap bitmap;
        private final Callback callback;
        private final String address;
        private final CountDownLatch connected = new CountDownLatch(1);
        private final CountDownLatch received = new CountDownLatch(1);
        private volatile IBinder core;
        private volatile boolean cancelled;
        private volatile Exception connectionError;
        private volatile List<String> values;
        private volatile IOException resultError;
        private boolean bound;

        private final ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                core = binder;
                connected.countDown();
            }

            @Override public void onServiceDisconnected(ComponentName name) {
                connectionError = new IOException("系统 AICR 扫码连接已中断");
                connected.countDown();
                received.countDown();
            }

            @Override public void onNullBinding(ComponentName name) {
                connectionError = new IOException("系统 AICR 未提供扫码连接");
                connected.countDown();
            }

            @Override public void onBindingDied(ComponentName name) {
                onServiceDisconnected(name);
            }
        };

        private Request(Context context, Bitmap bitmap, Callback callback) {
            this.context = context;
            this.bitmap = bitmap;
            this.callback = callback;
            address = context.getPackageName() + ":barcode:" + UUID.randomUUID();
        }

        public void cancel() {
            cancelled = true;
            connected.countDown();
            received.countDown();
        }

        private void bind() {
            if (cancelled) {
                deliver(null, new CancellationException("扫码已取消"));
                return;
            }
            Intent intent = new Intent("com.xiaomi.aicr.access.AICR_ENGINE")
                    .setComponent(new ComponentName("com.xiaomi.aicr",
                            "com.xiaomi.aicr.access.AiCrCoreService"))
                    .putExtra("bind_type", address);
            try {
                bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
                if (!bound) throw new IOException("无法连接系统 AICR 扫码服务，请检查系统智能服务是否启用");
            } catch (SecurityException error) {
                deliver(null, new IOException("系统拒绝连接 AICR 扫码服务，权限受限", error));
                return;
            } catch (IOException | IllegalArgumentException | IllegalStateException error) {
                deliver(null, error);
                return;
            }
            new Thread(this::detect, "AicrBarcode").start();
        }

        private void detect() {
            long started = SystemClock.elapsedRealtime();
            IBinder service = null;
            IBinder plugin = null;
            boolean requested = false;
            boolean registered = false;
            List<String> result = null;
            Exception failure = null;
            try {
                if (!connected.await(15, TimeUnit.SECONDS)) throw new IOException("连接系统 AICR 扫码服务超时");
                checkRequest();
                service = core;
                if (service == null || !CORE.equals(service.getInterfaceDescriptor())) {
                    throw new IOException("当前系统 AICR 核心接口不匹配");
                }
                requested = true;
                plugin = barcodePlugin(service, address);
                if (!BARCODE.equals(plugin.getInterfaceDescriptor())) {
                    throw new IOException("当前系统 AICR 扫码接口不匹配");
                }
                checkRequest();
                Binder listener = new Binder() {
                    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                            throws RemoteException {
                        if (code == INTERFACE_TRANSACTION) {
                            if (reply != null) reply.writeString(RESULT_CALLBACK);
                            return true;
                        }
                        if (code != 1) return super.onTransact(code, data, reply, flags);
                        data.enforceInterface(RESULT_CALLBACK);
                        try {
                            values = readValues(data);
                        } catch (RuntimeException error) {
                            resultError = new IOException("系统 AICR 扫码结果格式不匹配", error);
                        } finally {
                            received.countDown();
                        }
                        if (reply != null) reply.writeNoException();
                        return true;
                    }
                };
                register(plugin, listener, address);
                registered = true;
                detectBitmap(plugin, bitmap, address);
                if (!received.await(15, TimeUnit.SECONDS)) throw new IOException("系统 AICR 扫码超时");
                checkRequest();
                if (resultError != null) throw resultError;
                result = values;
                DragShareLog.i(TAG, "scan complete elapsed_ms="
                        + (SystemClock.elapsedRealtime() - started) + " count=" + result.size());
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                failure = new IOException("系统扫码被中断", error);
            } catch (IOException | RemoteException | RuntimeException error) {
                failure = error;
            } finally {
                try {
                    if (registered) unregister(plugin, address);
                } catch (RemoteException | RuntimeException error) {
                    if (failure == null) failure = new IOException("系统扫码连接清理失败", error);
                    else failure.addSuppressed(error);
                }
                try {
                    if (requested) release(service, address);
                } catch (RemoteException | RuntimeException error) {
                    if (failure == null) failure = new IOException("系统扫码引擎释放失败", error);
                    else failure.addSuppressed(error);
                }
                List<String> completed = result;
                Exception error = failure;
                MAIN.post(() -> deliver(completed, error));
            }
        }

        private void checkRequest() throws IOException {
            if (cancelled) throw new CancellationException("扫码已取消");
            if (connectionError != null) throw new IOException(connectionError.getMessage(), connectionError);
        }

        private void deliver(List<String> result, Exception failure) {
            if (bound) {
                try {
                    context.unbindService(connection);
                } catch (IllegalArgumentException error) {
                    if (failure == null) failure = new IOException("系统扫码连接未能释放", error);
                    else failure.addSuppressed(error);
                }
                bound = false;
            }
            if (failure != null && !(failure instanceof CancellationException)) {
                DragShareLog.w(TAG, "scan failed", failure);
            }
            callback.onComplete(result, cancelled ? new CancellationException("扫码已取消") : failure);
        }
    }

    private static IBinder barcodePlugin(IBinder core, String address) throws RemoteException, IOException {
        Binder callback = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                if (code == INTERFACE_TRANSACTION) {
                    if (reply != null) reply.writeString(CORE_CALLBACK);
                    return true;
                }
                if (code != 1 && code != 2) return super.onTransact(code, data, reply, flags);
                data.enforceInterface(CORE_CALLBACK);
                if (code == 1) data.readStrongBinder();
                else { data.readString(); data.readLong(); }
                return true;
            }
        };
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(CORE);
            data.writeStrongBinder(callback);
            data.writeString(address);
            data.writeString(BARCODE);
            data.writeString("");
            transact(core, 1, data, reply, 0);
            IBinder plugin = reply.readStrongBinder();
            Bundle status = reply.readTypedObject(Bundle.CREATOR);
            if (status == null || !status.containsKey("Status")) throw new IOException("系统 AICR 未返回扫码状态");
            int code = status.getInt("Status");
            if (code != 0) throw new IOException(switch (code) {
                case 1 -> "系统扫码模型正在准备首次使用，请稍后重试";
                case 2 -> "系统扫码插件版本不匹配";
                case 7 -> "当前系统 AICR 不支持扫码";
                case 8 -> "系统 AICR 拒绝了扫码权限";
                default -> "系统 AICR 扫码插件不可用，状态码 " + code;
            });
            if (plugin == null) throw new IOException("系统 AICR 未提供扫码插件");
            return plugin;
        } finally { reply.recycle(); data.recycle(); }
    }

    private static void register(IBinder plugin, IBinder listener, String address) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(BARCODE);
            data.writeStrongBinder(listener);
            data.writeString(address);
            transact(plugin, 1, data, reply, 0);
        } finally { reply.recycle(); data.recycle(); }
    }

    private static void unregister(IBinder plugin, String address) throws RemoteException {
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(BARCODE);
            data.writeString(address);
            transact(plugin, 2, data, null, IBinder.FLAG_ONEWAY);
        } finally { data.recycle(); }
    }

    private static void detectBitmap(IBinder plugin, Bitmap bitmap, String address) throws RemoteException {
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(BARCODE);
            data.writeTypedObject(bitmap, 0);
            data.writeString(address);
            transact(plugin, 3, data, null, IBinder.FLAG_ONEWAY);
        } finally { data.recycle(); }
    }

    private static void release(IBinder core, String address) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(CORE);
            data.writeString(address);
            transact(core, 2, data, reply, 0);
        } finally { reply.recycle(); data.recycle(); }
    }

    private static void transact(IBinder binder, int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (!binder.transact(code, data, reply, flags)) throw new RemoteException("unsupported transaction " + code);
        if (reply != null) reply.readException();
    }

    // BarcodeBean wire order: value type, raw value, corner points, bounding box, format.
    static List<String> readValues(Parcel data) {
        List<String> raw = data.createTypedArrayList(new Parcelable.Creator<String>() {
            @Override public String createFromParcel(Parcel parcel) {
                parcel.readString();
                String value = parcel.readString();
                int corners = parcel.readInt();
                for (int i = 0; i < corners; i++) parcel.readParcelable(Point.class.getClassLoader(), Point.class);
                parcel.readParcelable(Rect.class.getClassLoader(), Rect.class);
                parcel.readInt();
                return value;
            }
            @Override public String[] newArray(int size) { return new String[size]; }
        });
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (raw != null) for (String value : raw) if (value != null && !value.isEmpty()) values.add(value);
        return new ArrayList<>(values);
    }
}

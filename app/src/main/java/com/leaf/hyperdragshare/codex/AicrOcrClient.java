package com.leaf.hyperdragshare.codex;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Direct app-UID client for the AICR V2 protocol verified on HyperOS 4. */
public final class AicrOcrClient {
    private static final String TAG = "DragShare/AicrOcr";
    private static final String CORE = "com.xiaomi.aicr.IAiCrCoreService";
    private static final String VISION = "com.xiaomi.aicr.plugin.IVisionService";
    private static final String CALLBACK = "com.xiaomi.aicr.ICoreServiceCallback";
    private static final ComponentName SERVICE_COMPONENT = new ComponentName(
            "com.xiaomi.aicr", "com.xiaomi.aicr.access.AiCrCoreService");
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // The vendor plugin has one image cache. Do not queue arbitrary images behind a blocked Binder.
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();

    private AicrOcrClient() {}

    public interface Callback {
        /** Main thread, exactly once after cleanup. A cancelled request returns CancellationException. */
        void onComplete(String text, Exception error);
    }

    public interface ResultCallback {
        /** Main thread, exactly once after cleanup; retains line locations from this image. */
        void onComplete(AicrOcrResult result, Exception error);
    }

    /**
     * May be called from any thread. Caller owns the bitmap and must keep it alive until onComplete,
     * including after cancel(). This client never recycles it and never falls back to another engine.
     */
    public static Request recognize(Context context, Bitmap bitmap, Callback callback) {
        return recognizeWithLines(context, bitmap, (result, error) ->
                callback.onComplete(result == null ? null : result.text, error));
    }

    public static Request recognizeWithLines(Context context, Bitmap bitmap, ResultCallback callback) {
        Request request = new Request(context.getApplicationContext(), bitmap, callback);
        boolean accepted = ACTIVE.compareAndSet(false, true);
        MAIN.post(() -> {
            if (accepted) request.bind();
            else request.deliver(null, new IOException("AICR 正在处理上一张图片，请稍后重试"), false);
        });
        return request;
    }

    public static final class Request {
        private final Context context;
        private final Bitmap bitmap;
        private final ResultCallback callback;
        private final String address;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final CountDownLatch connected = new CountDownLatch(1);
        private final long startedAt = SystemClock.elapsedRealtime();
        private volatile IBinder core;
        private boolean bound;
        private String stage = "连接服务";

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

        private Request(Context context, Bitmap bitmap, ResultCallback callback) {
            this.context = context;
            this.bitmap = bitmap;
            this.callback = callback;
            address = context.getPackageName() + ":ocr:" + UUID.randomUUID();
        }

        /**
         * Suppresses result delivery and wakes connection waiting. A synchronous Binder operation
         * cannot be interrupted safely; completion is delayed until it and cleanup actually finish.
         */
        public void cancel() {
            cancelled.set(true);
            connected.countDown();
        }

        private void bind() {
            if (cancelled.get()) {
                deliver(null, null, true);
                return;
            }
            trace("bind_request");
            try {
                ServiceInfo service = context.getPackageManager().getServiceInfo(
                        SERVICE_COMPONENT, PackageManager.MATCH_DISABLED_COMPONENTS);
                if (!service.applicationInfo.enabled) {
                    throw new IOException("系统 AICR 服务已停用");
                }
                if (!service.enabled) {
                    throw new IOException("系统 AICR 服务组件已停用");
                }
            } catch (PackageManager.NameNotFoundException error) {
                DragShareLog.w(TAG, "AICR component is missing", error);
                deliver(null, new IOException("系统 AICR 服务未安装或组件缺失", error), true);
                return;
            } catch (SecurityException error) {
                DragShareLog.w(TAG, "AICR component lookup denied", error);
                deliver(null, new IOException("系统拒绝连接 AICR，权限受限", error), true);
                return;
            } catch (IOException error) {
                DragShareLog.w(TAG, "AICR component is disabled", error);
                deliver(null, error, true);
                return;
            }
            trace("service_checked");
            Intent intent = new Intent("com.xiaomi.aicr.access.AICR_ENGINE")
                    .setComponent(SERVICE_COMPONENT)
                    .putExtra("bind_type", address);
            try {
                bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
                if (!bound) {
                    throw new IOException("无法连接 AICR，请确认系统智能服务已启用");
                }
            } catch (SecurityException error) {
                DragShareLog.w(TAG, "AICR binding denied", error);
                deliver(null, new IOException("系统拒绝连接 AICR，权限受限", error), true);
                return;
            } catch (IOException | RuntimeException error) {
                DragShareLog.w(TAG, "unable to bind AICR", error);
                deliver(null, error instanceof IOException ? error
                        : new IOException("连接 AICR 失败：" + error.getMessage(), error), true);
                return;
            }
            trace("bind_service");
            new Thread(this::recognize, "AicrOcr").start();
        }

        private void recognize() {
            IBinder service = null;
            boolean requestedPlugin = false;
            AicrOcrResult recognized = null;
            Exception failure = null;
            try {
                if (!connected.await(15, TimeUnit.SECONDS)) {
                    throw new IOException("连接 AICR 超时，请稍后重试");
                }
                trace("service_connected");
                checkCancelled();
                service = core;
                if (service == null) throw new IOException("AICR 未提供可用连接");
                if (!CORE.equals(service.getInterfaceDescriptor())) {
                    throw new IOException("当前 AICR 服务接口不受支持");
                }
                stage = "取得识别引擎";
                requestedPlugin = true;
                IBinder vision = plugin(service, address);
                trace("plugin_ready");
                checkCancelled();
                if (!VISION.equals(vision.getInterfaceDescriptor())) {
                    throw new IOException("当前 AICR 视觉接口不受支持");
                }
                if (!"V2".equals(version(vision))) {
                    throw new IOException("当前 AICR OCR 版本不受支持，需要 V2");
                }
                trace("v2_ready");
                checkCancelled();
                stage = "传入图片";
                int session = setImage(vision, bitmap);
                if (session < 0) throw new IOException("AICR 无法读取这张图片");
                trace("image_accepted");
                checkCancelled();
                stage = "检测文字";
                detect(vision, session);
                trace("text_detected");
                checkCancelled();
                stage = "识别文字";
                recognized = recognizeResult(vision, session);
                trace("text_recognized");
                checkCancelled();
                if (recognized == null || recognized.text == null) throw new IOException("AICR 图片会话已失效，请重试");
                if (recognized.text.trim().isEmpty()) throw new IOException("这张图片没有识别到文字");
            } catch (CancellationException error) {
                failure = error;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                failure = new IOException("AICR 识别被中断", error);
                DragShareLog.w(TAG, "interrupted at " + stage, error);
            } catch (IOException | RemoteException | RuntimeException error) {
                failure = error instanceof IOException ? error
                        : new IOException("AICR " + stage + "失败：" + error.getMessage(), error);
                DragShareLog.w(TAG, "failed at " + stage, error);
            } finally {
                if (requestedPlugin && service != null) {
                    try {
                        release(service, address);
                    } catch (RemoteException | RuntimeException error) {
                        DragShareLog.w(TAG, "unable to release AICR connection", error);
                        if (failure == null) failure = new IOException("AICR 连接未能正常释放，请稍后重试", error);
                        else failure.addSuppressed(error);
                    }
                    trace("released");
                }
                AicrOcrResult result = recognized;
                Exception error = failure;
                MAIN.post(() -> deliver(result, error, true));
            }
        }

        private void checkCancelled() {
            if (cancelled.get()) throw new CancellationException("AICR 识别已取消");
        }

        private void trace(String state) {
            DragShareLog.i(TAG, "stage=" + state + " elapsed_ms="
                    + (SystemClock.elapsedRealtime() - startedAt));
        }

        /** Only invoked on main; worker has finished using the image before reaching this point. */
        private void deliver(AicrOcrResult result, Exception failure, boolean ownsSlot) {
            if (bound) {
                try {
                    context.unbindService(connection);
                } catch (RuntimeException error) {
                    DragShareLog.w(TAG, "unable to unbind AICR", error);
                    if (failure == null) failure = new IOException("AICR 连接清理失败", error);
                    else failure.addSuppressed(error);
                }
                bound = false;
            }
            if (ownsSlot) ACTIVE.set(false);
            if (cancelled.get()) {
                callback.onComplete(null, new CancellationException("AICR 识别已取消"));
            } else {
                callback.onComplete(failure == null ? result : null, failure);
            }
        }
    }

    private static IBinder plugin(IBinder service, String address) throws RemoteException, IOException {
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
                        DragShareLog.i(TAG, "model download callback; no automatic retry");
                    } else {
                        data.readString();
                        data.readLong();
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
            if (!status.containsKey("Status")) throw new IOException("AICR 未返回引擎状态");
            int code = status.getInt("Status");
            if (code != 0) throw new IOException(statusMessage(code));
            if (binder == null) throw new IOException("AICR 引擎尚未就绪");
            return binder;
        } finally { reply.recycle(); data.recycle(); }
    }

    private static String statusMessage(int code) {
        return switch (code) {
            case 1 -> "AICR 正在准备首次使用的模型，请稍后重试";
            case 2 -> "AICR 版本不匹配，请更新系统智能服务";
            case 3 -> "AICR 引擎连接已断开";
            case 4 -> "AICR 服务发生错误";
            case 5 -> "AICR 未提供 OCR 引擎";
            case 6 -> "AICR 无法读取图片";
            case 7 -> "当前 AICR 不支持此识别能力";
            case 8 -> "AICR 拒绝了识别权限";
            default -> "AICR 返回未知状态：" + code;
        };
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

    private static void detect(IBinder vision, int session) throws RemoteException, IOException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(VISION);
            data.writeInt(session);
            transact(vision, 3, data, reply);
            int count = reply.readInt();
            if (count < 0) throw new IOException("AICR 图片会话已失效，请重试");
            if (count == 0) throw new IOException("这张图片没有检测到文字");
        } finally { reply.recycle(); data.recycle(); }
    }

    private static AicrOcrResult recognizeResult(IBinder vision, int session) throws RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(VISION);
            data.writeInt(session);
            transact(vision, 4, data, reply);
            return AicrV2ResultReader.readResult(reply);
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

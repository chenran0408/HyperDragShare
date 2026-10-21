package com.leaf.hyperdragshare.codex;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.BadParcelableException;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.Handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Direct app-UID client for the AICR NER V1 Core plugin. */
public final class AicrNerClient {
    private static final String TAG = "DragShare/AicrNer";
    private static final String CORE = "com.xiaomi.aicr.IAiCrCoreService";
    private static final String NER = "com.xiaomi.aicr.plugin.INerService";
    private static final String CALLBACK = "com.xiaomi.aicr.ICoreServiceCallback";
    private static final int MAX_ENTITIES = 10000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();

    private AicrNerClient() {}

    public interface Callback {
        /** Delivered on the main thread exactly once, after release and unbind. */
        void onComplete(List<Entity> entities, Exception error);
    }

    /** NER result with offsets returned by the verified V1 Parcelable wire format. */
    public static final class Entity {
        public static final int TYPE_UNKNOWN = 0;
        public static final int TYPE_LOCATION = 1;
        public static final int TYPE_ID = 2;
        public static final int TYPE_TEL = 3;
        public static final int TYPE_BCN = 4;
        public static final int TYPE_TRAIN = 5;
        public static final int TYPE_FLN = 6;
        public static final int TYPE_CAR = 7;
        public static final int TYPE_ORDER = 8;
        public static final int TYPE_ENO = 9;
        public static final int TYPE_MAIL = 10;
        public static final int TYPE_URL = 11;
        public static final int TYPE_TAX_ID = 12;

        public final String text;
        public final int type;
        public final int start;
        public final int end;

        Entity(String text, int type, int start, int end) {
            this.text = text;
            this.type = type;
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Starts one explicit extraction request. The supplied text is the only input; this client
     * never reads the clipboard and never retries or substitutes another engine.
     */
    public static Request extract(Context context, String text, Callback callback) {
        Request request = new Request(context.getApplicationContext(), text, callback);
        boolean accepted = ACTIVE.compareAndSet(false, true);
        MAIN.post(() -> {
            if (accepted) request.bind();
            else request.deliver(null,
                    new IOException("AICR NER 正在处理上一条文本，请稍后重试。"), false);
        });
        return request;
    }

    public static final class Request {
        private final Context context;
        private final String text;
        private final Callback callback;
        private final String address;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final CountDownLatch connected = new CountDownLatch(1);
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

        private Request(Context context, String text, Callback callback) {
            this.context = context;
            this.text = text;
            this.callback = callback;
            address = context.getPackageName() + ":ner:" + UUID.randomUUID();
        }

        /** Cancellation suppresses results; a synchronous Binder call must finish before cleanup. */
        public void cancel() {
            cancelled.set(true);
            connected.countDown();
        }

        private void bind() {
            if (cancelled.get()) {
                deliver(null, null, true);
                return;
            }
            if (text == null || text.trim().isEmpty()) {
                deliver(null, new IOException("没有可供识别的文本。"), true);
                return;
            }
            Intent intent = new Intent("com.xiaomi.aicr.access.AICR_ENGINE")
                    .setComponent(new ComponentName("com.xiaomi.aicr",
                            "com.xiaomi.aicr.access.AiCrCoreService"))
                    .putExtra("bind_type", address);
            try {
                bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
                if (!bound) throw new IOException("无法连接 AICR，请确认系统智能服务已启用。");
                new Thread(this::extract, "AicrNer").start();
            } catch (IOException | RuntimeException error) {
                DragShareLog.w(TAG, "unable to bind AICR NER", error);
                deliver(null, error instanceof IOException ? (IOException) error
                        : new IOException("连接 AICR NER 失败：" + error.getMessage(), error), true);
            }
        }

        private void extract() {
            long started = android.os.SystemClock.elapsedRealtime();
            IBinder service = null;
            boolean requestedPlugin = false;
            List<Entity> entities = null;
            Exception failure = null;
            try {
                if (!connected.await(15, TimeUnit.SECONDS)) {
                    throw new IOException("连接 AICR 超时。");
                }
                checkCancelled();
                service = core;
                if (service == null) throw new IOException("AICR 未提供可用连接。");
                if (!CORE.equals(service.getInterfaceDescriptor())) {
                    throw new IOException("当前 AICR Core 接口不受支持。");
                }
                stage = "取得 NER 引擎";
                requestedPlugin = true;
                IBinder ner = plugin(service, address);
                checkCancelled();
                if (!NER.equals(ner.getInterfaceDescriptor())) {
                    throw new IOException("当前 AICR NER 接口不受支持。");
                }
                stage = "提取实体";
                entities = extractEntities(ner, text);
                checkCancelled();
                DragShareLog.i(TAG, "extraction complete entities=" + entities.size()
                        + " elapsed_ms=" + (android.os.SystemClock.elapsedRealtime() - started));
            } catch (CancellationException error) {
                failure = error;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                failure = new IOException("AICR NER 请求被中断。", error);
                DragShareLog.w(TAG, "interrupted at " + stage, error);
            } catch (IOException | RemoteException | RuntimeException error) {
                failure = error instanceof IOException ? (IOException) error
                        : new IOException("AICR NER " + stage + "失败：" + error.getMessage(), error);
                DragShareLog.w(TAG, "failed at " + stage, error);
            } finally {
                if (requestedPlugin && service != null) {
                    try {
                        release(service, address);
                    } catch (RemoteException | RuntimeException error) {
                        DragShareLog.w(TAG, "unable to release AICR NER connection", error);
                        if (failure == null) failure = new IOException("AICR NER 连接未能正常释放。", error);
                        else failure.addSuppressed(error);
                    }
                }
                List<Entity> result = entities;
                Exception error = failure;
                MAIN.post(() -> deliver(result, error, true));
            }
        }

        private void checkCancelled() {
            if (cancelled.get()) throw new CancellationException("AICR NER 已取消。");
        }

        /** Called on main after worker-side Core release. */
        private void deliver(List<Entity> entities, Exception failure, boolean ownsSlot) {
            if (bound) {
                try {
                    context.unbindService(connection);
                } catch (RuntimeException error) {
                    DragShareLog.w(TAG, "unable to unbind AICR NER", error);
                    if (failure == null) failure = new IOException("AICR NER 连接清理失败。", error);
                    else failure.addSuppressed(error);
                }
                bound = false;
            }
            if (ownsSlot) ACTIVE.set(false);
            if (cancelled.get()) callback.onComplete(null,
                    new CancellationException("AICR NER 已取消。"));
            else callback.onComplete(failure == null ? entities : null, failure);
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
            data.writeString(NER);
            data.writeString("V1");
            transact(service, 1, data, reply);
            IBinder binder = reply.readStrongBinder();
            Bundle status = new Bundle();
            if (reply.readInt() != 0) status.readFromParcel(reply);
            if (!status.containsKey("Status")) throw new IOException("AICR 未返回 NER 引擎状态。");
            int code = status.getInt("Status");
            if (code != 0) throw new IOException(statusMessage(code));
            if (binder == null) throw new IOException("AICR NER 引擎尚未就绪。");
            return binder;
        } finally { reply.recycle(); data.recycle(); }
    }

    private static String statusMessage(int code) {
        return switch (code) {
            case 1 -> "AICR 正在准备 NER 模型，请稍后手动重试。";
            case 2 -> "AICR NER 版本不匹配，请更新系统智能服务。";
            case 3 -> "AICR NER 引擎连接已断开。";
            case 4 -> "AICR NER 服务发生错误。";
            case 5 -> "AICR 未提供 NER 引擎。";
            case 7 -> "当前 AICR 不支持 NER 能力。";
            case 8 -> "AICR 拒绝了 NER 请求权限。";
            default -> "AICR NER 返回未知状态：" + code;
        };
    }

    private static List<Entity> extractEntities(IBinder ner, String text)
            throws RemoteException, IOException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(NER);
            data.writeString(text);
            transact(ner, 1, data, reply);
            return readEntities(reply);
        } finally { reply.recycle(); data.recycle(); }
    }

    static List<Entity> readEntities(Parcel parcel) {
        int count = parcel.readInt();
        if (count < 0 || count > MAX_ENTITIES || count > parcel.dataAvail() / 20) {
            throw new BadParcelableException("AICR NER: invalid entity count " + count);
        }
        if (count == 0) return Collections.emptyList();
        List<Entity> entities = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int present = parcel.readInt();
            if (present == 0) throw new BadParcelableException("AICR NER: null entity at " + i);
            if (present != 1) throw new BadParcelableException("AICR NER: invalid entity marker " + present);
            String text = parcel.readString();
            int type = parcel.readInt();
            int start = parcel.readInt();
            int end = parcel.readInt();
            entities.add(new Entity(text, type, start, end));
        }
        return Collections.unmodifiableList(entities);
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

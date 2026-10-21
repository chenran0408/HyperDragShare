package com.leaf.hyperdragshare.codex;

import android.app.KeyguardManager;
import android.content.ContentProviderClient;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/** The module process owns one Root connection. Portal and point capture borrow separate leases. */
final class SharedRootNodeCapture {
    static final String METHOD_OPEN = "open_root_node_lease";
    private static final Set<Lease> leases = new HashSet<>();
    private static final ReentrantLock io = new ReentrantLock();
    // Application context only; the last lease closes and clears this process-owned connection.
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static RootNodeCaptureClient.Session session;

    private SharedRootNodeCapture() {}

    static Connection open(Context context, boolean manual) throws IOException {
        ContentProviderClient provider = context.getContentResolver()
                .acquireContentProviderClient(ImageStagingClient.BASE_URI);
        if (provider == null) throw new IOException("无法连接模块取词服务");
        Binder owner = new Binder();
        try {
            Bundle request = new Bundle();
            request.putBinder("owner", owner);
            request.putBoolean("manual", manual);
            int moduleUid = context.getPackageManager().getApplicationInfo(BuildConfig.APPLICATION_ID, 0).uid;
            Bundle response = provider.call(METHOD_OPEN, null, request);
            return new Connection(context, provider, owner, moduleUid,
                    IRootNodeLease.Stub.asInterface(response.getBinder("lease")));
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            provider.close();
            throw new IOException("模块已不可用", error);
        } catch (RemoteException | IllegalStateException error) {
            provider.close();
            throw new IOException("连接取词服务失败：" + error.getMessage(), error);
        } catch (RuntimeException error) {
            provider.close();
            throw error;
        }
    }

    // Called only after the Provider verifies the module/Portal caller and clears Binder identity.
    static synchronized Bundle acquire(Context context, IBinder owner, int uid, boolean manual) {
        if (owner == null) throw new IllegalArgumentException("Missing capture lease owner");
        try {
            if (session == null) session = RootNodeCaptureClient.open(context.getApplicationContext());
            Lease lease = new Lease(session, owner, uid, manual);
            owner.linkToDeath(lease, 0);
            leases.add(lease);
            DragShareLog.i("DragShare/SharedNodes", "lease acquired manual=" + manual + " clients=" + leases.size());
            Bundle response = new Bundle();
            response.putBinder("lease", lease);
            return response;
        } catch (IOException | InterruptedException | RemoteException error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            if (leases.isEmpty() && session != null) { session.close(); session = null; }
            DragShareLog.w("DragShare/SharedNodes", "Unable to acquire Root lease", error);
            throw new IllegalStateException(error.getMessage());
        }
    }

    private static synchronized boolean manualActive() {
        return leases.stream().anyMatch(lease -> lease.manual);
    }

    private static synchronized void invalidate(RootNodeCaptureClient.Session failed) {
        if (session != failed) return;
        for (Lease lease : leases) {
            lease.closed = true;
            lease.owner.unlinkToDeath(lease, 0);
        }
        leases.clear();
        failed.close();
        session = null;
        DragShareLog.w("DragShare/SharedNodes", "Root connection failed; its leases are now closed");
    }

    private static final class Lease extends IRootNodeLease.Stub implements IBinder.DeathRecipient {
        private final RootNodeCaptureClient.Session root;
        private final IBinder owner;
        private final int uid;
        private final boolean manual;
        private volatile boolean closed;

        Lease(RootNodeCaptureClient.Session root, IBinder owner, int uid, boolean manual) {
            this.root = root; this.owner = owner; this.uid = uid; this.manual = manual;
        }

        private void enforceOwner() {
            if (Binder.getCallingUid() != uid) throw new SecurityException("Capture lease belongs to another UID");
            if (closed) throw new IllegalStateException("取词连接已关闭，请重载作用域");
        }

        @Override public Bundle capture(float x, float y, String[] blocked, boolean text, boolean images, boolean pressLookup,
                                         boolean earlyCapture,
                                         IRootNodeCandidate callback) {
            enforceOwner();
            if (!manual && manualActive()) return null;
            if (!io.tryLock()) throw new IllegalStateException("取词服务正在处理另一次请求，请稍后点取");
            long identity = Binder.clearCallingIdentity();
            try {
                Bundle result = root.captureNode(x, y, new HashSet<>(Arrays.asList(blocked)), text, images,
                        pressLookup, earlyCapture, source -> {
                    if (closed || (!manual && manualActive())) return false;
                    try { return callback.onCandidate(source); }
                    catch (RemoteException | IllegalStateException | SecurityException error) {
                        throw new IOException("取词候选回调失败：" + error.getMessage(), error);
                    }
                });
                return closed || (!manual && manualActive()) ? null : result;
            } catch (IOException | InterruptedException error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                invalidate(root);
                DragShareLog.w("DragShare/SharedNodes", "Root lookup failed", error);
                throw new IllegalStateException(error.getMessage());
            } finally { Binder.restoreCallingIdentity(identity); io.unlock(); }
        }

        @Override public Bundle insertTextAt(float x, float y, String text) {
            enforceOwner();
            if (!io.tryLock()) throw new IllegalStateException("输入服务正在处理另一次请求，请稍后重试");
            long identity = Binder.clearCallingIdentity();
            try {
                if (closed) throw new IllegalStateException("输入连接已关闭");
                return root.insertTextAt(x, y, text);
            } catch (IOException | InterruptedException error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                invalidate(root);
                DragShareLog.w("DragShare/SharedNodes", "Root text insertion transport failed", error);
                throw new IllegalStateException(error.getMessage());
            } finally { Binder.restoreCallingIdentity(identity); io.unlock(); }
        }

        @Override public boolean verifyWindow(String source, int windowId) {
            enforceOwner();
            if (!io.tryLock()) throw new IllegalStateException("取词服务正忙，本次图片读取已取消");
            long identity = Binder.clearCallingIdentity();
            try { return root.isCurrentWindow(source, windowId); }
            catch (IOException | InterruptedException error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                invalidate(root);
                DragShareLog.w("DragShare/SharedNodes", "Root window check failed", error);
                throw new IllegalStateException(error.getMessage());
            } finally { Binder.restoreCallingIdentity(identity); io.unlock(); }
        }

        @Override public boolean verifySource(String source) {
            enforceOwner();
            if (!manual && manualActive()) return false;
            if (!io.tryLock()) throw new IllegalStateException("取词服务正忙，本次按压已取消");
            long identity = Binder.clearCallingIdentity();
            try { return root.isCurrentSource(source); }
            catch (IOException | InterruptedException error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                invalidate(root);
                DragShareLog.w("DragShare/SharedNodes", "Root source check failed", error);
                throw new IllegalStateException(error.getMessage());
            } finally { Binder.restoreCallingIdentity(identity); io.unlock(); }
        }

        @Override public boolean isOpen() {
            if (Binder.getCallingUid() != uid) throw new SecurityException("Capture lease belongs to another UID");
            return !closed;
        }

        @Override public void close() {
            if (Binder.getCallingUid() != uid) throw new SecurityException("Capture lease belongs to another UID");
            release();
        }

        @Override public void binderDied() { release(); }

        private void release() {
            synchronized (SharedRootNodeCapture.class) {
                if (closed) return;
                closed = true;
                owner.unlinkToDeath(this, 0);
                leases.remove(this);
                DragShareLog.i("DragShare/SharedNodes", "lease released clients=" + leases.size());
                if (leases.isEmpty()) { session.close(); session = null; }
            }
        }
    }

    static final class Connection implements AutoCloseable {
        static final class PressResult {
            final CapturedContent content;
            final boolean policyExcluded;
            final String rejectionReason;

            PressResult(CapturedContent content, boolean policyExcluded, String rejectionReason) {
                this.content = content;
                this.policyExcluded = policyExcluded;
                this.rejectionReason = rejectionReason;
            }
        }

        private final Context context;
        private final ContentProviderClient provider;
        // Retain this Binder until close so the server can observe a remote client's death.
        private final IBinder owner;
        private final int moduleUid;
        private final IRootNodeLease lease;
        private volatile boolean closed;

        Connection(Context context, ContentProviderClient provider, IBinder owner, int moduleUid, IRootNodeLease lease) {
            this.context = context; this.provider = provider; this.owner = owner;
            this.moduleUid = moduleUid; this.lease = lease;
        }

        CapturedContent capture(float x, float y, Set<String> blocked, boolean text, boolean images,
                                 RootNodeCaptureClient.CandidateHandler handler)
                throws IOException, InterruptedException {
            return capture(x, y, blocked, text, images, false, false, handler);
        }

        PressResult capturePress(float x, float y, Set<String> blocked, boolean text, boolean images, boolean earlyCapture,
                                 RootNodeCaptureClient.CandidateHandler handler)
                throws IOException, InterruptedException {
            return captureResult(x, y, blocked, text, images, true, earlyCapture, handler);
        }

        private CapturedContent capture(float x, float y, Set<String> blocked, boolean text, boolean images,
                                        boolean pressLookup, boolean earlyCapture, RootNodeCaptureClient.CandidateHandler handler)
                throws IOException, InterruptedException {
            return captureResult(x, y, blocked, text, images, pressLookup, earlyCapture, handler).content;
        }

        private PressResult captureResult(float x, float y, Set<String> blocked, boolean text, boolean images,
                                          boolean pressLookup, boolean earlyCapture,
                                          RootNodeCaptureClient.CandidateHandler handler)
                throws IOException, InterruptedException {
            if (closed) throw new ConnectionFailedException("取词连接已关闭", null);
            try {
                Bundle node = lease.capture(x, y, blocked.toArray(new String[0]), text, images, pressLookup, earlyCapture,
                        new IRootNodeCandidate.Stub() {
                            @Override public boolean onCandidate(String source) throws RemoteException {
                                if (Binder.getCallingUid() != moduleUid) throw new SecurityException("Invalid capture callback UID");
                                if (closed) return false;
                                long identity = Binder.clearCallingIdentity();
                                try { return handler.onCandidate(source); }
                                catch (IOException error) { throw new IllegalStateException(error.getMessage()); }
                                finally { Binder.restoreCallingIdentity(identity); }
                            }
                        });
                if (closed) return new PressResult(null, false, "connection_closed");
                if (pressLookup && node != null
                        && node.getBoolean(RootNodeCaptureClient.KEY_POLICY_EXCLUDED)) {
                    return new PressResult(null, true, "excluded_app");
                }
                String rejectionReason = pressLookup && node != null
                        ? node.getString(RootNodeCaptureClient.KEY_CAPTURE_REASON) : null;
                CapturedContent content = RootNodeCaptureClient.contentFromNode(context, node, (source, windowId) -> {
                    try { return !closed && lease.verifyWindow(source, windowId); }
                    catch (RemoteException | IllegalStateException error) {
                        throw requestFailure("核验来源窗口失败", error);
                    }
                });
                return new PressResult(content, false, content == null ? rejectionReason : null);
            } catch (RemoteException | IllegalStateException error) {
                throw requestFailure("读取当前页面失败", error);
            }
        }

        boolean isCurrentSource(String source) throws IOException, InterruptedException {
            KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
            if (closed || keyguard.isKeyguardLocked()) return false;
            try {
                return lease.verifySource(source) && !closed && !keyguard.isKeyguardLocked();
            } catch (RemoteException | IllegalStateException error) {
                throw requestFailure("核验按压来源失败", error);
            }
        }

        boolean insertTextAt(float x, float y, String text) throws IOException {
            if (closed) throw new ConnectionFailedException("输入连接已关闭", null);
            if (!Float.isFinite(x) || !Float.isFinite(y) || text == null || text.isEmpty())
                throw new IOException("拖入位置或文字无效");
            if (context.getSystemService(KeyguardManager.class).isKeyguardLocked())
                throw new IOException("请先解锁手机再拖入文字");
            final Bundle reply;
            try { reply = lease.insertTextAt(x, y, text); }
            catch (RemoteException | IllegalStateException | SecurityException error) {
                throw requestFailure("填入文字失败", error);
            }
            if (!reply.getBoolean("inserted")) throw new IOException(reply.getString("reason", "目标输入框未接受文字"));
            return true;
        }

        private IOException requestFailure(String operation, Exception error) {
            String reason = operation + "：" + error.getMessage();
            if (error instanceof RemoteException) return new ConnectionFailedException(reason, error);
            try {
                return lease.isOpen() ? new IOException(reason, error) : new ConnectionFailedException(reason, error);
            } catch (RemoteException disconnected) {
                return new ConnectionFailedException(reason, disconnected);
            }
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            try { lease.close(); }
            catch (RemoteException error) { DragShareLog.w("DragShare/SharedNodes", "Capture service disconnected during close", error); }
            finally { provider.close(); }
        }
    }

    static final class ConnectionFailedException extends IOException {
        ConnectionFailedException(String message, Exception cause) { super(message, cause); }
    }
}

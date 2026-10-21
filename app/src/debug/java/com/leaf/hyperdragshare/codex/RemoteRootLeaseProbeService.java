package com.leaf.hyperdragshare.codex;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import java.io.IOException;

/** Debug-only remote lease owner. All Root work runs on this process's Binder worker threads. */
public final class RemoteRootLeaseProbeService extends Service {
    static final String DESCRIPTOR = "com.leaf.hyperdragshare.codex.RemoteRootLeaseProbe";
    static final int OPEN = IBinder.FIRST_CALL_TRANSACTION;
    static final int CAPTURE = OPEN + 1;
    static final int CLOSE = OPEN + 2;
    static final int KILL_SELF = OPEN + 3;
    private final Object leaseLock = new Object();
    private SharedRootNodeCapture.Connection lease;

    private final Binder commands = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == INTERFACE_TRANSACTION) { reply.writeString(DESCRIPTOR); return true; }
            if (code < OPEN || code > KILL_SELF) return super.onTransact(code, data, reply, flags);
            if (Binder.getCallingUid() != Process.myUid()) throw new SecurityException("Probe requires module UID");
            data.enforceInterface(DESCRIPTOR);
            Bundle request = data.readBundle(getClassLoader());
            data.enforceNoDataAvail();
            Bundle report = new Bundle();
            report.putInt("servicePid", Process.myPid());
            try {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    throw new IllegalStateException("Probe Root work must run off the main thread");
                }
                synchronized (leaseLock) {
                    if (code == OPEN) {
                        if (lease != null) throw new IllegalStateException("Remote probe lease is already open");
                        lease = SharedRootNodeCapture.open(RemoteRootLeaseProbeService.this, false);
                        report.putBoolean("opened", true);
                    } else if (code == CAPTURE) {
                        if (lease == null) throw new IllegalStateException("Remote probe lease is not open");
                        if (request == null) throw new IllegalArgumentException("Missing explicit capture point");
                        String expected = request.getString("expected");
                        if (expected == null || expected.isBlank()) throw new IllegalArgumentException("Missing expected source");
                        int[] callbacks = {0};
                        boolean[] matched = {false};
                        CapturedContent content = lease.capture(request.getFloat("x"), request.getFloat("y"),
                                AccessibilityBlacklist.builtInPackages(RemoteRootLeaseProbeService.this),
                                true, false, source -> {
                                    callbacks[0]++;
                                    matched[0] = expected.equals(source);
                                    return matched[0];
                                });
                        report.putInt("candidateCallbacks", callbacks[0]);
                        report.putBoolean("candidateSourceMatches", matched[0]);
                        report.putBoolean("found", content != null);
                        report.putBoolean("finalSourceMatches", content != null && expected.equals(content.sourcePackage));
                        report.putInt("textLength", content == null ? 0 : content.text.length());
                    } else if (code == CLOSE) {
                        closeLease();
                        report.putBoolean("closed", true);
                    } else {
                        if (lease == null) throw new IllegalStateException("Death check requires a live remote lease");
                        // Deliberately leave the lease open: the production broker must observe owner death.
                        new Handler(Looper.getMainLooper()).postDelayed(() -> Process.killProcess(Process.myPid()), 200L);
                        report.putBoolean("deathScheduled", true);
                    }
                }
            } catch (IOException | InterruptedException | RuntimeException error) {
                if (error instanceof InterruptedException) Thread.currentThread().interrupt();
                report.putString("error", error.toString());
            }
            reply.writeNoException();
            reply.writeBundle(report);
            return true;
        }
    };

    @Override public IBinder onBind(Intent intent) { return commands; }

    private void closeLease() {
        if (lease == null) return;
        SharedRootNodeCapture.Connection closing = lease;
        lease = null;
        closing.close();
    }

    @Override public void onDestroy() {
        // A normal unbind closes the lease; KILL_SELF intentionally bypasses this lifecycle callback.
        new Thread(() -> { synchronized (leaseLock) { closeLease(); } }, "remote-probe-close").start();
        super.onDestroy();
    }
}

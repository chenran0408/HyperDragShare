package com.leaf.hyperdragshare.codex;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Uses an explicitly supplied point only; never simulates an area gesture or changes the source page. */
public final class SharedRootNodeProbe extends Instrumentation {
    private Bundle arguments;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments;
        start();
    }

    @Override public void onStart() {
        Context context = getTargetContext();
        Bundle report = new Bundle();
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch died = new CountDownLatch(1);
        IBinder[] remote = {null};
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                remote[0] = service;
                connected.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        Intent service = new Intent(context, RemoteRootLeaseProbeService.class);
        SharedRootNodeCapture.Connection manual = null;
        boolean bound = false;
        boolean passed = false;
        try {
            if (arguments == null || !arguments.containsKey("x") || !arguments.containsKey("y")) {
                throw new IllegalArgumentException("Explicit x, y and expected source are required");
            }
            float x = Float.parseFloat(arguments.getString("x"));
            float y = Float.parseFloat(arguments.getString("y"));
            String expected = arguments.getString("expected");
            if (!Float.isFinite(x) || !Float.isFinite(y) || x < 0 || y < 0 || expected == null || expected.isBlank()) {
                throw new IllegalArgumentException("Invalid explicit capture point or expected source");
            }
            Bundle point = new Bundle();
            point.putFloat("x", x);
            point.putFloat("y", y);
            point.putString("expected", expected);
            bound = context.bindService(service, connection, Context.BIND_AUTO_CREATE);
            require(bound && connected.await(10, TimeUnit.SECONDS), "Remote probe service did not connect");
            remote[0].linkToDeath(died::countDown, 0);
            Bundle opened = command(remote[0], RemoteRootLeaseProbeService.OPEN, null);
            require(opened.getBoolean("opened"), "Remote automatic lease did not open");
            report.putInt("servicePid", opened.getInt("servicePid"));
            manual = SharedRootNodeCapture.open(context, true);
            Bundle suppressed = command(remote[0], RemoteRootLeaseProbeService.CAPTURE, point);
            require(!suppressed.getBoolean("found") && suppressed.getInt("candidateCallbacks") == 0,
                    "Manual selection did not suppress automatic capture before its callback");
            report.putBoolean("automaticSuppressed", true);
            Bundle status = new Bundle();
            status.putString("phase", "both-leases-held");
            status.putInt("servicePid", opened.getInt("servicePid"));
            status.putInt("modulePid", Process.myPid());
            status.putInt("holdMillis", 8000);
            sendStatus(0, status);
            Thread.sleep(8000L); // Bounded opportunity for the host to inspect the actual helper process count.

            int[] callbacks = {0};
            boolean[] candidateMatches = {false};
            CapturedContent content = manual.capture(x, y, AccessibilityBlacklist.builtInPackages(context),
                    true, false, source -> {
                        callbacks[0]++;
                        candidateMatches[0] = expected.equals(source);
                        return candidateMatches[0];
                    });
            require(content != null && callbacks[0] == 1 && candidateMatches[0]
                    && expected.equals(content.sourcePackage) && content.text.length() > 0,
                    "Manual lookup did not return matching text/source");
            report.putBoolean("manualSourceMatches", true);
            report.putInt("manualTextLength", content.text.length());
            manual.close();
            manual = null;
            Bundle resumed = command(remote[0], RemoteRootLeaseProbeService.CAPTURE, point);
            require(resumed.getBoolean("found") && resumed.getBoolean("candidateSourceMatches")
                    && resumed.getBoolean("finalSourceMatches") && resumed.getInt("candidateCallbacks") == 1
                    && resumed.getInt("textLength") > 0, "Automatic lease did not survive manual close");
            report.putBoolean("automaticResumed", true);
            report.putInt("automaticTextLength", resumed.getInt("textLength"));
            Bundle death = command(remote[0], RemoteRootLeaseProbeService.KILL_SELF, null);
            require(death.getBoolean("deathScheduled") && died.await(5, TimeUnit.SECONDS),
                    "Remote lease owner death was not observed");
            report.putBoolean("remoteDeathObserved", true);
            Bundle released = new Bundle();
            released.putString("phase", "remote-owner-dead");
            released.putInt("modulePid", Process.myPid());
            released.putInt("holdMillis", 5000);
            sendStatus(0, released);
            // Keep the module alive so host evidence distinguishes lease cleanup from module process exit.
            Thread.sleep(5000L);
            passed = true;
        } catch (IOException | InterruptedException | RemoteException | RuntimeException error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            report.putString("error", error.toString());
        } finally {
            if (manual != null) {
                try { manual.close(); }
                catch (RuntimeException error) { report.putString("cleanupError", error.toString()); passed = false; }
            }
            if (remote[0] != null && remote[0].isBinderAlive()) {
                try { command(remote[0], RemoteRootLeaseProbeService.CLOSE, null); }
                catch (IOException | RemoteException | RuntimeException error) {
                    report.putString("cleanupError", error.toString()); passed = false;
                }
            }
            if (bound) context.unbindService(connection);
            context.stopService(service);
        }
        finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, report);
    }

    private static Bundle command(IBinder remote, int code, Bundle request) throws RemoteException, IOException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(RemoteRootLeaseProbeService.DESCRIPTOR);
            data.writeBundle(request);
            if (!remote.transact(code, data, reply, 0)) throw new IOException("Unknown remote probe command");
            reply.readException();
            Bundle report = reply.readBundle(SharedRootNodeProbe.class.getClassLoader());
            if (report == null) throw new IOException("Remote probe returned no report");
            if (report.containsKey("error")) throw new IOException(report.getString("error"));
            return report;
        } finally { data.recycle(); reply.recycle(); }
    }

    private static void require(boolean condition, String error) {
        if (!condition) throw new IllegalStateException(error);
    }
}

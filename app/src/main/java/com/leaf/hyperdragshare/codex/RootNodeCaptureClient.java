package com.leaf.hyperdragshare.codex;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.util.DisplayMetrics;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Blocking one-shot Root lookup; caller must use its capture executor and reject stale gestures. */
final class RootNodeCaptureClient {
    private RootNodeCaptureClient() {}

    static synchronized CapturedContent capture(Context context, float x, float y,
                                                Set<String> blockedPackages)
            throws IOException, InterruptedException {
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        if (keyguard == null || keyguard.isKeyguardLocked()) return null;
        String apk;
        try {
            apk = context.getPackageManager().getApplicationInfo(BuildConfig.APPLICATION_ID, 0).sourceDir;
        } catch (PackageManager.NameNotFoundException error) {
            throw new IOException("Module APK unavailable for Root node capture", error);
        }
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        JSONObject request = new JSONObject();
        try {
            request.put("x", x).put("y", y).put("density", metrics.density)
                    .put("width", metrics.widthPixels).put("height", metrics.heightPixels)
                    .put("blocked", new JSONArray(blockedPackages));
        } catch (JSONException error) {
            throw new IOException("Invalid Root node capture coordinates", error);
        }
        String command = "CLASSPATH='" + apk.replace("'", "'\\''")
                + "' /system/bin/app_process /system/bin " + RootNodeCaptureMain.class.getName();
        Process process = new ProcessBuilder("su", "-c", command).start();
        FutureTask<String> output = readAsync(process.getInputStream(), "root-node-output", 4 * 1024 * 1024);
        FutureTask<String> errors = readAsync(process.getErrorStream(), "root-node-errors", 16384);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(4500L);
        try {
            try (java.io.OutputStream input = process.getOutputStream()) {
                input.write((request + "\n").getBytes(StandardCharsets.UTF_8));
            }
            if (!process.waitFor(remaining(deadline), TimeUnit.NANOSECONDS)) {
                throw new TimeoutException("Root node process did not exit");
            }
            String response = output.get(remaining(deadline), TimeUnit.NANOSECONDS);
            String diagnostic = errors.get(remaining(deadline), TimeUnit.NANOSECONDS).trim();
            if (process.exitValue() != 0) {
                throw new IOException("Root node capture failed (exit " + process.exitValue() + "): " + diagnostic);
            }
            if (!diagnostic.isEmpty()) DragShareLog.w("DragShare/RootNodes", diagnostic);
            if (keyguard.isKeyguardLocked()) return null;
            JSONObject result = new JSONObject(response);
            if (!result.getBoolean("found")) return null;
            String source = result.getString("package");
            if (blockedPackages.contains(source)) return null;
            return CapturedContent.text(result.getString("text"), source,
                    new Rect(result.getInt("left"), result.getInt("top"),
                            result.getInt("right"), result.getInt("bottom")),
                    result.optString("before", null), result.optString("after", null));
        } catch (JSONException error) {
            throw new IOException("Invalid Root node capture response", error);
        } catch (TimeoutException error) {
            throw new IOException("Root node capture timed out", error);
        } catch (ExecutionException error) {
            throw new IOException("Unable to read Root node capture result", error.getCause());
        } finally {
            output.cancel(true);
            errors.cancel(true);
            process.destroyForcibly();
        }
    }

    private static long remaining(long deadline) {
        return Math.max(0L, deadline - System.nanoTime());
    }

    private static FutureTask<String> readAsync(InputStream stream, String name, int limit) {
        FutureTask<String> result = new FutureTask<>(() -> {
            try (InputStream input = stream; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > limit) throw new IOException("Root node response exceeds limit");
                    bytes.write(buffer, 0, count);
                }
                return bytes.toString(StandardCharsets.UTF_8.name());
            }
        });
        Thread reader = new Thread(result, name);
        reader.setDaemon(true);
        reader.start();
        return result;
    }
}

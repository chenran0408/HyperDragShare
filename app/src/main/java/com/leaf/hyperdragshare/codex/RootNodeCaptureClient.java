package com.leaf.hyperdragshare.codex;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.util.DisplayMetrics;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** A warmed Root process. Nodes are traversed only during explicit capture requests. */
final class RootNodeCaptureClient {
    private static final String TAG = "DragShare/RootNodes";
    private static final long RESPONSE_TIMEOUT_MILLIS = 4500L;

    private RootNodeCaptureClient() {}

    interface CandidateHandler {
        boolean onCandidate() throws IOException;
    }

    static Session open(Context context) throws IOException, InterruptedException {
        String apk;
        try {
            apk = context.getPackageManager().getApplicationInfo(BuildConfig.APPLICATION_ID, 0).sourceDir;
        } catch (PackageManager.NameNotFoundException error) {
            throw new IOException("Module APK unavailable for Root node capture", error);
        }
        String command = "CLASSPATH='" + apk.replace("'", "'\\''")
                + "' /system/bin/app_process /system/bin " + RootNodeCaptureMain.class.getName() + " --serve";
        Session session = new Session(context, new ProcessBuilder("su", "-c", command).start());
        try {
            if (!session.awaitResponse().getBoolean("ready")) {
                throw new IOException("Root node process did not become ready");
            }
            return session;
        } catch (JSONException error) {
            session.close();
            throw new IOException("Invalid Root node startup response", error);
        } catch (IOException | InterruptedException error) {
            session.close();
            throw error;
        }
    }

    static final class Session implements AutoCloseable {
        private final Context context;
        private final Process process;
        private final LinkedBlockingQueue<Reply> responses = new LinkedBlockingQueue<>();
        private final StringBuilder diagnostics = new StringBuilder();
        private volatile boolean closed;

        private Session(Context context, Process process) {
            this.context = context;
            this.process = process;
            Thread errors = new Thread(() -> readErrors(process.getErrorStream()), "root-node-errors");
            errors.setDaemon(true);
            errors.start();
            Thread reader = new Thread(() -> readResponses(process.getInputStream()), "root-node-output");
            reader.setDaemon(true);
            reader.start();
        }

        synchronized CapturedContent capture(float x, float y, Set<String> blockedPackages,
                                             CandidateHandler handler)
                throws IOException, InterruptedException {
            if (closed) throw new IOException("Root node process has stopped; reload the Portal scope");
            KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
            if (keyguard.isKeyguardLocked()) return null;
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            try {
                JSONObject request = new JSONObject().put("x", x).put("y", y)
                        .put("density", metrics.density).put("width", metrics.widthPixels)
                        .put("height", metrics.heightPixels).put("blocked", new JSONArray(blockedPackages))
                        .put("early_candidate", true);
                process.getOutputStream().write((request + "\n").getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().flush();
                JSONObject result = awaitResponse();
                boolean notified = "candidate".equals(result.optString("stage"));
                boolean accepted = notified && handler.onCandidate();
                if (notified) result = awaitResponse();
                DragShareLog.i(TAG, "semantic lookup elapsedMs=" + result.getLong("elapsed_ms"));
                if (keyguard.isKeyguardLocked() || !result.getBoolean("found")) return null;
                if (!notified) accepted = handler.onCandidate();
                if (!accepted) return null;
                String source = result.getString("package");
                if (blockedPackages.contains(source)) return null;
                return CapturedContent.text(result.getString("text"), source,
                        new Rect(result.getInt("left"), result.getInt("top"),
                                result.getInt("right"), result.getInt("bottom")),
                        result.optString("before", null), result.optString("after", null));
            } catch (JSONException error) {
                close();
                throw new IOException("Invalid Root node response", error);
            } catch (IOException | InterruptedException error) {
                close();
                throw error;
            }
        }

        private JSONObject awaitResponse() throws IOException, InterruptedException, JSONException {
            Reply reply = responses.poll(RESPONSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (reply == null) throw new IOException("Root node response timed out");
            if (reply.error != null) throw reply.error;
            return new JSONObject(reply.json);
        }

        private void readResponses(InputStream stream) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.length() > 4 * 1024 * 1024) throw new IOException("Root node response exceeds limit");
                    responses.offer(new Reply(line, null));
                }
                if (!closed) responses.offer(new Reply(null, new IOException("Root node process exited: " + diagnostics())));
            } catch (IOException error) {
                if (!closed) responses.offer(new Reply(null, error));
            }
        }

        private void readErrors(InputStream stream) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (diagnostics) {
                        if (diagnostics.length() < 16384) diagnostics.append(line).append('\n');
                    }
                }
            } catch (IOException error) {
                if (!closed) DragShareLog.w(TAG, "cannot read Root node process diagnostics", error);
            }
        }

        private String diagnostics() {
            synchronized (diagnostics) { return diagnostics.toString(); }
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            responses.offer(new Reply(null, new IOException("Root node process closed")));
            try {
                // EOF lets an idle helper exit normally. Its own watchdog bounds a stuck capture.
                process.getOutputStream().close();
            } catch (IOException error) {
                DragShareLog.w(TAG, "cannot close Root node request pipe", error);
            }
            process.destroy();
        }
    }

    private static final class Reply {
        final String json;
        final IOException error;
        Reply(String json, IOException error) { this.json = json; this.error = error; }
    }
}

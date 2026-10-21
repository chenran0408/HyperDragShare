package com.leaf.hyperdragshare.codex;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.WindowManager;

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
    static final String KEY_POLICY_EXCLUDED = "capture_policy_excluded";
    static final String KEY_CAPTURE_REASON = "capture_rejection_reason";

    private RootNodeCaptureClient() {}

    interface CandidateHandler {
        boolean onCandidate(String sourcePackage) throws IOException;
    }

    interface WindowVerifier {
        boolean isCurrentWindow(String source, int windowId) throws IOException, InterruptedException;
    }

    static Session open(Context context) throws IOException, InterruptedException {
        long started = android.os.SystemClock.elapsedRealtime();
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
            JSONObject startup = session.awaitResponse();
            if (!startup.getBoolean("ready")) {
                throw new IOException(startup.optString("error", "界面取词服务未就绪"));
            }
            DragShareLog.i(TAG, "connection ready elapsed_ms="
                    + (android.os.SystemClock.elapsedRealtime() - started));
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
                                             boolean allowText, boolean allowImages,
                                             CandidateHandler handler)
                throws IOException, InterruptedException {
            return contentFromNode(context, captureNode(x, y, blockedPackages, allowText, allowImages, handler),
                    this::isCurrentWindow);
        }

        synchronized Bundle captureNode(float x, float y, Set<String> blockedPackages,
                                        boolean allowText, boolean allowImages, CandidateHandler handler)
                throws IOException, InterruptedException {
            return captureNode(x, y, blockedPackages, allowText, allowImages, false, false, handler);
        }

        synchronized Bundle captureNode(float x, float y, Set<String> blockedPackages,
                                        boolean allowText, boolean allowImages, boolean pressLookup, boolean earlyCapture,
                                        CandidateHandler handler)
                throws IOException, InterruptedException {
            if (closed) throw new IOException("Root node process has stopped; reload the Portal scope");
            KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
            if (keyguard.isKeyguardLocked()) return null;
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            Rect display = context.getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
            int expectedWidth = display.width(), expectedHeight = display.height();
            String source, text, before, after;
            Rect bounds;
            int windowId;
            boolean image;
            try {
                JSONObject request = new JSONObject().put("x", x).put("y", y)
                        .put("density", metrics.density).put("width", expectedWidth)
                        .put("height", expectedHeight).put("blocked", new JSONArray(blockedPackages))
                        .put("early_candidate", !pressLookup).put("press_lookup", pressLookup)
                        .put("press_early", earlyCapture)
                        .put("allow_text", allowText)
                        .put("allow_images", allowImages);
                process.getOutputStream().write((request + "\n").getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().flush();
                JSONObject result = awaitResponse();
                String fallbackReason = result.optString("fallback_reason");
                if (!fallbackReason.isEmpty()) {
                    DragShareLog.i(TAG, "WeChat window OCR fallback reason=" + fallbackReason);
                }
                boolean notified = "candidate".equals(result.optString("stage"));
                boolean accepted = notified && handler.onCandidate(result.getString("package"));
                if (notified) result = awaitResponse();
                DragShareLog.i(TAG, "semantic lookup elapsedMs=" + result.getLong("elapsed_ms"));
                if (keyguard.isKeyguardLocked()) return null;
                if (!result.getBoolean("found")) {
                    String reason = result.optString("reason", "unknown");
                    DragShareLog.i(TAG, "no content candidate reason=" + reason);
                    if (pressLookup && "excluded_app".equals(reason)) {
                        Bundle excluded = new Bundle();
                        excluded.putBoolean(KEY_POLICY_EXCLUDED, true);
                        return excluded;
                    }
                    if (pressLookup) {
                        Bundle rejection = new Bundle();
                        rejection.putString(KEY_CAPTURE_REASON, reason);
                        return rejection;
                    }
                    return null;
                }
                if (!notified) accepted = handler.onCandidate(result.getString("package"));
                if (!accepted) return null;
                source = result.getString("package");
                if (blockedPackages.contains(source)) {
                    if (pressLookup) {
                        Bundle excluded = new Bundle();
                        excluded.putBoolean(KEY_POLICY_EXCLUDED, true);
                        return excluded;
                    }
                    return null;
                }
                String kind = result.getString("kind");
                if (!"text".equals(kind) && !"image".equals(kind)
                        && !(pressLookup && "ocr_region".equals(kind))) throw new JSONException("Unknown node kind");
                image = !"text".equals(kind);
                if (pressLookup && "ocr_region".equals(kind)) {
                    DragShareLog.i(TAG, "press capture source=" + source + " kind=ocr_region");
                }
                text = image ? null : result.getString("text");
                before = result.optString("before", null);
                after = result.optString("after", null);
                bounds = new Rect(result.getInt("left"), result.getInt("top"),
                        result.getInt("right"), result.getInt("bottom"));
                windowId = result.getInt("window_id");
            } catch (JSONException error) {
                close();
                throw new IOException("Invalid Root node response", error);
            } catch (IOException | InterruptedException error) {
                close();
                throw error;
            }
            Bundle node = new Bundle();
            node.putString("package", source);
            node.putString("text", text);
            node.putString("before", before);
            node.putString("after", after);
            node.putParcelable("bounds", bounds);
            node.putInt("window_id", windowId);
            node.putInt("width", expectedWidth);
            node.putInt("height", expectedHeight);
            node.putBoolean("image", image);
            return node;
        }

        synchronized Bundle insertTextAt(float x, float y, String text) throws IOException, InterruptedException {
            if (closed) throw new IOException("Root 输入连接已关闭");
            Bundle reply = new Bundle();
            if (context.getSystemService(KeyguardManager.class).isKeyguardLocked()) {
                reply.putString("reason", "请先解锁手机再拖入文字");
                return reply;
            }
            try {
                JSONObject request = new JSONObject().put("operation", "insert_text")
                        .put("x", x).put("y", y).put("text", text);
                process.getOutputStream().write((request + "\n").getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().flush();
                JSONObject result = awaitResponse();
                reply.putBoolean("inserted", result.getBoolean("inserted"));
                reply.putString("reason", result.optString("reason", ""));
                return reply;
            } catch (JSONException error) {
                close();
                throw new IOException("Root 输入返回格式无效", error);
            } catch (IOException | InterruptedException error) {
                close();
                throw error;
            }
        }

        synchronized boolean isCurrentWindow(String source, int windowId) throws IOException, InterruptedException {
            try {
                JSONObject request = new JSONObject().put("operation", "verify_window")
                        .put("package", source).put("window_id", windowId);
                process.getOutputStream().write((request + "\n").getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().flush();
                return awaitResponse().getBoolean("same_window");
            } catch (JSONException error) {
                close();
                throw new IOException("Invalid Root window response", error);
            } catch (IOException | InterruptedException error) {
                close();
                throw error;
            }
        }

        synchronized boolean isCurrentSource(String source) throws IOException, InterruptedException {
            if (closed) throw new IOException("Root node process has stopped; reload the Portal scope");
            try {
                JSONObject request = new JSONObject().put("operation", "verify_source").put("package", source);
                process.getOutputStream().write((request + "\n").getBytes(StandardCharsets.UTF_8));
                process.getOutputStream().flush();
                return awaitResponse().getBoolean("same_source");
            } catch (JSONException error) {
                close();
                throw new IOException("Invalid Root source response", error);
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

    static CapturedContent contentFromNode(Context context, Bundle node, WindowVerifier verifier)
            throws IOException, InterruptedException {
        if (node == null || context.getSystemService(KeyguardManager.class).isKeyguardLocked()) return null;
        String source = node.getString("package");
        Rect bounds = node.getParcelable("bounds", Rect.class);
        if (!node.getBoolean("image")) {
            return CapturedContent.text(node.getString("text"), source, bounds,
                    node.getString("before"), node.getString("after"));
        }
        Bitmap screen = new RootScreenshotter().capture();
        try {
            if (context.getSystemService(KeyguardManager.class).isKeyguardLocked()
                    || !verifier.isCurrentWindow(source, node.getInt("window_id"))) return null;
            int width = node.getInt("width"), height = node.getInt("height");
            if (screen.getWidth() != width || screen.getHeight() != height) {
                throw new IOException("画面尺寸已变化，请重新按压（" + width + "×" + height
                        + "→" + screen.getWidth() + "×" + screen.getHeight() + "）");
            }
            Rect crop = new Rect(bounds);
            if (!crop.intersect(0, 0, screen.getWidth(), screen.getHeight())) {
                throw new IOException("图片区域已移出屏幕");
            }
            Bitmap image = Bitmap.createBitmap(screen, crop.left, crop.top, crop.width(), crop.height());
            if (image == screen) screen = null;
            return CapturedContent.image(image, source, crop, true);
        } finally {
            if (screen != null) screen.recycle();
        }
    }

    private static final class Reply {
        final String json;
        final IOException error;
        Reply(String json, IOException error) { this.json = json; this.error = error; }
    }
}

package com.leaf.hyperdragshare.codex;

import android.app.AppOpsManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/** Clipboard writes from overlay windows respect the ROM's actual AppOp restriction. */
final class ClipboardWriter {
    // This AppOp exists on the target ROM but its constant is absent from the public SDK.
    private static final String OPSTR_WRITE_CLIPBOARD = "android:write_clipboard";
    private static final String TAG = "DragShare/Clipboard";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Callback {
        void onComplete(Exception error);
    }

    private ClipboardWriter() {}

    static void copy(Context context, String text, Callback callback) {
        write(context, text, null, callback);
    }

    static void copyImage(Context context, Uri image, Callback callback) {
        write(context, null, image, callback);
    }

    private static void write(Context context, String text, Uri image, Callback callback) {
        Context app = context.getApplicationContext();
        startDaemon("clipboard-copy", () -> {
            Exception failure = null;
            try {
                Uri stagedImage = image == null ? null
                        : ImageStagingClient.AUTHORITY.equals(image.getAuthority()) ? image
                        : ImageStagingClient.stageUri(app, image);
                // The non-raw check translates FOREGROUND into ALLOWED, even for an overlay UID.
                int mode = app.getSystemService(AppOpsManager.class).unsafeCheckOpRawNoThrow(
                        OPSTR_WRITE_CLIPBOARD, android.os.Process.myUid(), app.getPackageName());
                if (mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_DEFAULT) {
                    ClipData clip = stagedImage == null ? ClipData.newPlainText("HyperDragShare", text)
                            : ClipData.newUri(app.getContentResolver(), "HyperDragShare image", stagedImage);
                    app.getSystemService(ClipboardManager.class).setPrimaryClip(clip);
                } else {
                    DragShareLog.i(TAG, "clipboard route=" + (stagedImage == null ? "system" : "shell-image")
                            + " appOp=" + mode);
                    copyPrivileged(app, stagedImage == null ? text : stagedImage.toString(), stagedImage != null);
                }
            } catch (IOException | RuntimeException error) {
                failure = error;
                if (image == null) DragShareLog.w(TAG, "clipboard write failed", error);
                else DragShareLog.w(TAG, "image clipboard write failed (" + error.getClass().getSimpleName() + ")");
            }
            Exception result = failure;
            MAIN.post(() -> callback.onComplete(result));
        });
    }

    private static void copyPrivileged(Context context, String inputValue, boolean image) throws IOException {
        AtomicReference<Process> running = new AtomicReference<>();
        FutureTask<Void> command = new FutureTask<>(() -> {
            String apk;
            try {
                apk = context.getPackageManager().getApplicationInfo(BuildConfig.APPLICATION_ID, 0).sourceDir;
            } catch (PackageManager.NameNotFoundException error) {
                throw new IOException("找不到模块安装文件，无法通过 Root 复制", error);
            }
            String shell = "CLASSPATH='" + apk.replace("'", "'\\''")
                    + "' /system/bin/app_process /system/bin " + RootClipboardMain.class.getName()
                    + (image ? " --image" : "");
            // System UID cannot issue clipboard URI grants; shell UID can preserve image access.
            Process process = new ProcessBuilder("su", image ? "2000" : "1000", "-c", shell).start();
            running.set(process);
            if (Thread.currentThread().isInterrupted()) {
                process.destroyForcibly();
                throw new InterruptedException("Root clipboard task cancelled");
            }
            FutureTask<String> errors = new FutureTask<>(() -> readOutput(process.getErrorStream(), 8192));
            startDaemon("clipboard-stderr", errors);
            try {
                try (java.io.OutputStream input = process.getOutputStream()) {
                    input.write(inputValue.getBytes(StandardCharsets.UTF_8));
                }
                String output = readOutput(process.getInputStream(), 128).trim();
                int status = process.waitFor();
                String diagnostic = errors.get().trim();
                if (status != 0) {
                    throw new IOException("Root 复制失败（退出码 " + status + "）"
                            + (diagnostic.isEmpty() ? "" : "：" + diagnostic));
                }
                if (!"copied".equals(output)) throw new IOException("Root 未能确认复制成功");
                return null;
            } finally {
                errors.cancel(true);
            }
        });
        startDaemon("clipboard-root", command);
        try {
            command.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException error) {
            throw new IOException("Root 复制超时，请检查 Root 授权", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("复制已中断", error);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException("无法通过 Root 写入剪贴板", cause);
        } finally {
            command.cancel(true);
            Process process = running.get();
            if (process != null) startDaemon("clipboard-cleanup", () -> {
                process.destroyForcibly();
                try {
                    process.getOutputStream().close();
                    process.getInputStream().close();
                    process.getErrorStream().close();
                } catch (IOException error) {
                    DragShareLog.w(TAG, "cannot close Root clipboard pipes", error);
                }
            });
        }
    }

    private static String readOutput(InputStream stream, int retainLimit) throws IOException {
        try (InputStream input = stream) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, Math.min(read, retainLimit - output.size()));
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private static void startDaemon(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }
}

package com.leaf.hyperdragshare.codex;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** One explicit in-memory Root screenshot, with a deadline covering process and output pipes. */
final class RootScreenshotter {
    private static final long COMMAND_TIMEOUT_MILLIS = 4000L;

    Bitmap capture() throws IOException {
        Process process = new ProcessBuilder("su", "-c", "/system/bin/screencap -p").start();
        byte[] bytes = readPng(process, COMMAND_TIMEOUT_MILLIS);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bitmap == null) throw new IOException("Root screencap returned invalid PNG");
        return bitmap;
    }

    static byte[] readPng(Process process, long timeoutMillis) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        FutureTask<byte[]> output = new FutureTask<>(() -> readAll(process.getInputStream(), Integer.MAX_VALUE));
        FutureTask<byte[]> errors = new FutureTask<>(() -> readAll(process.getErrorStream(), 8192));
        startDaemon("screencap-png", output);
        startDaemon("screencap-stderr", errors);
        try {
            if (!process.waitFor(Math.max(0L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                throw new TimeoutException("process did not exit");
            }
            byte[] bytes = output.get(Math.max(0L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            byte[] errorBytes = errors.get(Math.max(0L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            String diagnostic = new String(errorBytes, StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0 || bytes.length == 0) {
                throw new IOException("Root screencap failed (exit " + process.exitValue() + ")"
                        + (diagnostic.isEmpty() ? "" : ": " + diagnostic));
            }
            if (!diagnostic.isEmpty()) DragShareLog.w("DragShare/ScreenCapture", diagnostic);
            return bytes;
        } catch (TimeoutException timeout) {
            throw new IOException("Root screencap timed out", timeout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Root screencap interrupted", interrupted);
        } catch (ExecutionException error) {
            throw new IOException("Unable to read Root screencap output", error.getCause());
        } finally {
            output.cancel(true);
            errors.cancel(true);
            // su descendants can retain a pipe after the immediate process exits. Neither pipe
            // closure nor joining its reader may extend the caller's capture deadline.
            startDaemon("screencap-cleanup", () -> {
                process.destroyForcibly();
                try {
                    process.getOutputStream().close();
                } catch (IOException error) {
                    DragShareLog.w("DragShare/ScreenCapture", "unable to close screencap input", error);
                }
            });
        }
    }

    private static void startDaemon(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private static byte[] readAll(InputStream stream, int retainLimit) throws IOException {
        try (InputStream input = stream) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) output.write(buffer, 0, Math.min(read, retainLimit - output.size()));
            }
            return output.toByteArray();
        }
    }
}

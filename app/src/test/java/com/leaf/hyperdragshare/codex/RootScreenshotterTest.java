package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public final class RootScreenshotterTest {
    @Test(timeout = 2000)
    public void exitedProcessWithOpenStdoutStillTimesOut() throws Exception {
        BlockingStream heldPipe = new BlockingStream();
        FakeProcess process = new FakeProcess(heldPipe, bytes(""), 0);
        try {
            RootScreenshotter.readPng(process, 50);
            fail("An inherited stdout pipe must not wait forever");
        } catch (IOException expected) {
            assertEquals("Root screencap timed out", expected.getMessage());
        }
        assertTrue(process.destroyed.await(1, TimeUnit.SECONDS));
    }

    @Test(timeout = 2000)
    public void exitedProcessWithOpenStderrStillTimesOut() throws Exception {
        FakeProcess process = new FakeProcess(bytes("png"), new BlockingStream(), 0);
        try {
            RootScreenshotter.readPng(process, 50);
            fail("An inherited stderr pipe must not wait forever");
        } catch (IOException expected) {
            assertEquals("Root screencap timed out", expected.getMessage());
        }
        assertTrue(process.destroyed.await(1, TimeUnit.SECONDS));
    }

    @Test(timeout = 2000)
    public void cancellationReturnsAndPreservesInterruptFlag() throws Exception {
        BlockingStream heldPipe = new BlockingStream();
        FakeProcess process = new FakeProcess(heldPipe, bytes(""), 0);
        AtomicReference<IOException> failure = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread capture = new Thread(() -> {
            try {
                RootScreenshotter.readPng(process, 4000);
            } catch (IOException error) {
                failure.set(error);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        capture.setDaemon(true);
        capture.start();
        assertTrue(heldPipe.entered.await(1, TimeUnit.SECONDS));
        capture.interrupt();
        capture.join(500);
        assertNotNull(failure.get());
        assertEquals("Root screencap interrupted", failure.get().getMessage());
        assertTrue(interrupted.get());
        assertTrue(process.destroyed.await(1, TimeUnit.SECONDS));
    }

    @Test(timeout = 2000)
    public void nonzeroExitReportsSeparateStderr() throws Exception {
        try {
            RootScreenshotter.readPng(new FakeProcess(bytes("not png"), bytes("su: permission denied"), 1), 500);
            fail("The command failed");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("exit 1"));
            assertTrue(expected.getMessage().contains("su: permission denied"));
        }
        assertArrayEquals("png".getBytes(StandardCharsets.UTF_8),
                RootScreenshotter.readPng(new FakeProcess(bytes("png"), bytes(""), 0), 500));
    }

    private static InputStream bytes(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class BlockingStream extends InputStream {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch released = new CountDownLatch(1);

        @Override public int read() {
            entered.countDown();
            // Model a native pipe read which does not respond to Thread.interrupt().
            boolean interrupted = false;
            while (released.getCount() != 0) {
                try {
                    released.await();
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
            return -1;
        }

        @Override public void close() { released.countDown(); }
    }

    private static final class FakeProcess extends Process {
        final InputStream output;
        final InputStream error;
        final int exit;
        final CountDownLatch destroyed = new CountDownLatch(1);

        FakeProcess(InputStream output, InputStream error, int exit) {
            this.output = output;
            this.error = error;
            this.exit = exit;
        }

        @Override public InputStream getInputStream() { return output; }
        @Override public InputStream getErrorStream() { return error; }
        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public int waitFor() { return exit; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { return true; }
        @Override public int exitValue() { return exit; }
        @Override public Process destroyForcibly() { destroy(); return this; }
        @Override public void destroy() {
            if (output instanceof BlockingStream) ((BlockingStream) output).close();
            if (error instanceof BlockingStream) ((BlockingStream) error).close();
            destroyed.countDown();
        }
    }
}

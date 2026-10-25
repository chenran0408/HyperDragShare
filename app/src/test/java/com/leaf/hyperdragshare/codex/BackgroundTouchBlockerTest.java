package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.Display;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public final class BackgroundTouchBlockerTest {
    @Test
    public void rootServiceCommandUsesTheDynamicallyResolvedTransaction() {
        String command = BackgroundTouchBlocker.rootServiceCallCommand(123);

        assertTrue(command.contains("service call input 123"));
        assertFalse(command.contains("grep"));
    }

    @Test
    public void acceptsTheCurrentRomSuccessfulServiceCallParcel() {
        assertTrue(BackgroundTouchBlocker.isSuccessfulServiceCallResult(
                "Result: Parcel(\t00000000    '....')"));
        assertTrue(BackgroundTouchBlocker.isSuccessfulServiceCallResult(
                "Result: Parcel(\n0x00000000: 00000000 00000000)"));
    }

    @Test
    public void rejectsServiceCallExceptionParcel() {
        assertFalse(BackgroundTouchBlocker.isSuccessfulServiceCallResult(
                "Result: Parcel(\n0x00000000: ffffffff 00000021 00650052)"));
    }

    @Test
    public void usesRootCancellationWhenPortalInputApisAreDenied() {
        AtomicBoolean rootCalled = new AtomicBoolean();
        BackgroundTouchBlocker blocker = new BackgroundTouchBlocker(
                inputContext(new Object()),
                (target, methodName, args) -> {
                    throw new SecurityException("Requires MONITOR_INPUT permission");
                },
                () -> {
                    rootCalled.set(true);
                    return true;
                });

        assertTrue(blocker.start());
        assertTrue(rootCalled.get());
        blocker.stop();
    }

    @Test
    public void keepsCancelMonitorPilferAndRootFallbackOrder() {
        Object inputManager = new Object();
        Object monitor = new Object();
        List<String> calls = new ArrayList<>();
        BackgroundTouchBlocker blocker = new BackgroundTouchBlocker(
                inputContext(inputManager),
                (target, methodName, args) -> {
                    if (target == inputManager && "cancelCurrentTouch".equals(methodName)) {
                        calls.add("cancelCurrentTouch");
                        throw new SecurityException("denied");
                    }
                    if (target == inputManager && "monitorGestureInput".equals(methodName)) {
                        calls.add("monitorGestureInput");
                        return monitor;
                    }
                    if (target == monitor && "pilferPointers".equals(methodName)) {
                        calls.add("pilferPointers");
                        throw new SecurityException("denied");
                    }
                    if (target == monitor && "dispose".equals(methodName)) {
                        calls.add("dispose");
                        return null;
                    }
                    throw new NoSuchMethodException(methodName);
                },
                () -> {
                    calls.add("rootFallback");
                    return true;
                });

        assertTrue(blocker.start());
        assertEquals(Arrays.asList("cancelCurrentTouch", "monitorGestureInput",
                "pilferPointers", "dispose", "rootFallback"), calls);
        blocker.close();
    }

    @Test
    public void currentAsyncRequestPreservesCancelMonitorPilferAndRootFallbackOrder() throws Exception {
        Object inputManager = new Object();
        Object monitor = new Object();
        List<String> calls = new ArrayList<>();
        AtomicBoolean predicateRan = new AtomicBoolean();
        BackgroundTouchBlocker blocker = new BackgroundTouchBlocker(
                inputContext(inputManager),
                (target, methodName, args) -> {
                    if (target == inputManager && "cancelCurrentTouch".equals(methodName)) {
                        calls.add("cancelCurrentTouch");
                        throw new SecurityException("denied");
                    }
                    if (target == inputManager && "monitorGestureInput".equals(methodName)) {
                        calls.add("monitorGestureInput");
                        return monitor;
                    }
                    if (target == monitor && "pilferPointers".equals(methodName)) {
                        calls.add("pilferPointers");
                        throw new SecurityException("denied");
                    }
                    if (target == monitor && "dispose".equals(methodName)) {
                        calls.add("dispose");
                        return null;
                    }
                    throw new NoSuchMethodException(methodName);
                },
                () -> {
                    calls.add("rootFallback");
                    return true;
                });

        try {
            blocker.startAsync(() -> {
                predicateRan.set(true);
                return true;
            });
            worker(blocker).submit(() -> {}).get(5, TimeUnit.SECONDS);

            assertTrue(predicateRan.get());
            assertEquals(Arrays.asList("cancelCurrentTouch", "monitorGestureInput",
                    "pilferPointers", "dispose", "rootFallback"), calls);
        } finally {
            blocker.close();
        }
    }

    @Test
    public void staleQueuedAsyncRequestDoesNotStartCancellationTransaction() throws Exception {
        Object inputManager = new Object();
        List<String> calls = new ArrayList<>();
        AtomicInteger rootCalls = new AtomicInteger();
        BackgroundTouchBlocker blocker = new BackgroundTouchBlocker(
                inputContext(inputManager),
                (target, methodName, args) -> {
                    calls.add(methodName);
                    throw new SecurityException("not expected for stale request");
                },
                () -> {
                    rootCalls.incrementAndGet();
                    return true;
                });
        ExecutorService worker = worker(blocker);
        CountDownLatch workerBlocked = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        AtomicBoolean stillCurrent = new AtomicBoolean(true);
        try {
            worker.execute(() -> {
                workerBlocked.countDown();
                await(releaseWorker);
            });
            assertTrue(workerBlocked.await(5, TimeUnit.SECONDS));
            blocker.startAsync(stillCurrent::get);
            stillCurrent.set(false);
            releaseWorker.countDown();
            worker.submit(() -> {}).get(5, TimeUnit.SECONDS);

            assertTrue(calls.isEmpty());
            assertEquals(0, rootCalls.get());
        } finally {
            releaseWorker.countDown();
            blocker.close();
        }
    }

    @Test
    public void ownerInvalidatedAtFinalCheckPreventsTransactionStart() throws Exception {
        Object inputManager = new Object();
        List<String> calls = new ArrayList<>();
        AtomicInteger rootCalls = new AtomicInteger();
        BackgroundTouchBlocker blocker = new BackgroundTouchBlocker(
                inputContext(inputManager),
                (target, methodName, args) -> {
                    calls.add(methodName);
                    throw new SecurityException("not expected after failed owner check");
                },
                () -> {
                    rootCalls.incrementAndGet();
                    return true;
                });
        CountDownLatch checkStarted = new CountDownLatch(1);
        CountDownLatch allowCheckToReadOwner = new CountDownLatch(1);
        AtomicBoolean stillCurrent = new AtomicBoolean(true);
        ExecutorService worker = worker(blocker);
        try {
            blocker.startAsync(() -> {
                checkStarted.countDown();
                await(allowCheckToReadOwner);
                // This is the last owner read before start() begins the API transaction.
                return stillCurrent.get();
            });
            assertTrue(checkStarted.await(5, TimeUnit.SECONDS));
            stillCurrent.set(false);
            allowCheckToReadOwner.countDown();
            worker.submit(() -> {}).get(5, TimeUnit.SECONDS);

            assertTrue(calls.isEmpty());
            assertEquals(0, rootCalls.get());
        } finally {
            allowCheckToReadOwner.countDown();
            blocker.close();
        }
    }

    @Test
    public void stopDisposesInputMonitorAfterPilfering() {
        Object inputManager = new Object();
        Object monitor = new Object();
        AtomicBoolean disposed = new AtomicBoolean();
        BackgroundTouchBlocker blocker = new BackgroundTouchBlocker(
                inputContext(inputManager),
                (target, methodName, args) -> {
                    if (target == inputManager && "cancelCurrentTouch".equals(methodName)) {
                        throw new SecurityException("Requires MONITOR_INPUT permission");
                    }
                    if (target == inputManager && "monitorGestureInput".equals(methodName)) {
                        return monitor;
                    }
                    if (target == monitor && "pilferPointers".equals(methodName)) {
                        return null;
                    }
                    if (target == monitor && "dispose".equals(methodName)) {
                        disposed.set(true);
                        return null;
                    }
                    throw new NoSuchMethodException(methodName);
                },
                () -> false);

        assertTrue(blocker.start());
        blocker.stop();

        assertTrue(disposed.get());
    }

    private static Context inputContext(Object inputManager) {
        return new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public Display getDisplay() {
                throw new UnsupportedOperationException("Application context has no display");
            }

            @Override
            public Object getSystemService(String name) {
                return Context.INPUT_SERVICE.equals(name) ? inputManager : null;
            }
        };
    }

    private static ExecutorService worker(BackgroundTouchBlocker blocker) throws Exception {
        java.lang.reflect.Field field = BackgroundTouchBlocker.class.getDeclaredField("background");
        field.setAccessible(true);
        return (ExecutorService) field.get(blocker);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for test latch");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }
}

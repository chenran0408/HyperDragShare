package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public final class LatestTaskSlotTest {
    @Test public void replacingAndFinishingAnOperationDoNotLeaveQueuedWork() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger staleRuns = new AtomicInteger();
        AtomicInteger currentRuns = new AtomicInteger();
        LatestTaskSlot slot = new LatestTaskSlot(executor);
        try {
            executor.execute(() -> {
                running.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(running.await(1, TimeUnit.SECONDS));
            slot.submit(staleRuns::incrementAndGet);
            slot.submit(currentRuns::incrementAndGet);
            assertEquals(1, executor.getQueue().size());

            slot.cancel();
            assertEquals(0, executor.getQueue().size());
            release.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
            assertEquals(0, staleRuns.get());
            assertEquals(0, currentRuns.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test public void replacementRunsOnlyTheMostRecentQueuedOperation() throws Exception {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch latestFinished = new CountDownLatch(1);
        AtomicInteger staleRuns = new AtomicInteger();
        AtomicInteger latestRuns = new AtomicInteger();
        LatestTaskSlot slot = new LatestTaskSlot(executor);
        try {
            executor.execute(() -> {
                running.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(running.await(1, TimeUnit.SECONDS));
            slot.submit(staleRuns::incrementAndGet);
            slot.submit(() -> {
                latestRuns.incrementAndGet();
                latestFinished.countDown();
            });
            assertEquals(1, executor.getQueue().size());

            release.countDown();
            assertTrue(latestFinished.await(1, TimeUnit.SECONDS));
            assertEquals(0, staleRuns.get());
            assertEquals(1, latestRuns.get());
            assertEquals(0, executor.getQueue().size());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}

package com.leaf.hyperdragshare.codex;

import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

/** Owns at most one queued task and removes cancelled work before it runs. */
final class LatestTaskSlot {
    private final ThreadPoolExecutor executor;
    private Future<?> task;

    LatestTaskSlot(ThreadPoolExecutor executor) {
        this.executor = executor;
    }

    void submit(Runnable next) {
        cancel();
        task = executor.submit(next);
    }

    void cancel() {
        Future<?> previous = task;
        task = null;
        if (previous == null) return;
        previous.cancel(true);
        if (previous instanceof Runnable) {
            executor.remove((Runnable) previous);
        }
    }
}

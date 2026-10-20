package com.leaf.hyperdragshare.codex;

import java.util.concurrent.atomic.AtomicLong;

/** Request ownership: replaced or closed screens cannot accept old results. */
final class LatestRequest {
    private final AtomicLong generation = new AtomicLong();

    long begin() {
        return generation.incrementAndGet();
    }

    boolean accepts(long request) {
        return request == generation.get();
    }

    void invalidate() {
        generation.incrementAndGet();
    }
}

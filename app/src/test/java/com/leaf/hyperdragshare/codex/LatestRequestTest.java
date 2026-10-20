package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LatestRequestTest {
    @Test
    public void secondInputRejectsLateSuccessAndFailureFromFirstInput() {
        LatestRequest requests = new LatestRequest();
        long first = requests.begin();
        long second = requests.begin();
        assertFalse(requests.accepts(first));
        assertTrue(requests.accepts(second));
    }

    @Test
    public void screenDestructionRejectsPendingResults() {
        LatestRequest requests = new LatestRequest();
        long pending = requests.begin();
        requests.invalidate();
        assertFalse(requests.accepts(pending));
    }

    @Test
    public void retryAfterCancellationDoesNotRevivePreviousRequest() {
        LatestRequest requests = new LatestRequest();
        long cancelled = requests.begin();
        requests.invalidate();
        long retry = requests.begin();
        assertFalse(requests.accepts(cancelled));
        assertTrue(requests.accepts(retry));
    }
}

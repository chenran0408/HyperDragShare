package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Binder;
import android.os.Bundle;

import java.io.IOException;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class RootLeaseFailureTest {
    @Test public void closedLeaseAndBusyRequestHaveDifferentLifecycles() throws InterruptedException {
        assertFailureKind(new IllegalStateException("process exited"), true);
        assertFailureKind(new IllegalStateException("another request is running"), false);
    }

    private void assertFailureKind(RuntimeException failure, boolean fatal) throws InterruptedException {
        IRootNodeLease lease = new IRootNodeLease.Stub() {
            @Override public Bundle capture(float x, float y, String[] blocked, boolean text, boolean images,
                                            boolean press, boolean early, IRootNodeCandidate callback) { throw failure; }
            @Override public boolean verifyWindow(String source, int windowId) { return true; }
            @Override public boolean verifySource(String source) { return true; }
            @Override public boolean isOpen() { return !fatal; }
            @Override public void close() {}
        };
        SharedRootNodeCapture.Connection connection = new SharedRootNodeCapture.Connection(
                RuntimeEnvironment.getApplication(), null, new Binder(), 10000, lease);
        try {
            connection.capturePress(10, 10, Set.of(), true, false, true, source -> true);
            fail("Expected a failed request");
        } catch (IOException error) {
            if (fatal) assertTrue(error instanceof SharedRootNodeCapture.ConnectionFailedException);
            else assertFalse(error instanceof SharedRootNodeCapture.ConnectionFailedException);
        }
    }
}

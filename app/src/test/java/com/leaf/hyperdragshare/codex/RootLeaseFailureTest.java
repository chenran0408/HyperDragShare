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
    @Test public void policyExclusionSurvivesLeaseWithoutBecomingAnEmptyCapture() throws Exception {
        IRootNodeLease lease = new IRootNodeLease.Stub() {
            @Override public Bundle capture(float x, float y, String[] blocked, boolean text, boolean images,
                                            boolean press, boolean early, IRootNodeCandidate callback) {
                Bundle result = new Bundle();
                result.putBoolean(RootNodeCaptureClient.KEY_POLICY_EXCLUDED, true);
                return result;
            }
            @Override public Bundle verifyWindow(String source, int windowId, Bundle requestedBounds) {
                throw new AssertionError("Excluded content must not be converted or verified");
            }
            @Override public Bundle captureOcrRegion(String source, int windowId, Bundle requestedBounds) {
                throw new AssertionError("Excluded content must not be captured");
            }
            @Override public Bundle captureImage(String source, int windowId, Bundle requestedBounds) {
                throw new AssertionError("Excluded content must not be captured");
            }
            @Override public boolean verifySource(String source) { return true; }
            @Override public Bundle insertTextAt(float x, float y, String text) { return null; }
            @Override public boolean isOpen() { return true; }
            @Override public void close() {}
        };
        SharedRootNodeCapture.Connection connection = new SharedRootNodeCapture.Connection(
                RuntimeEnvironment.getApplication(), new Binder(), 10000, lease);
        SharedRootNodeCapture.Connection.PressResult result = connection.capturePress(
                10, 10, Set.of("test.excluded"), true, false, true, source -> true);
        assertTrue(result.policyExcluded);
        org.junit.Assert.assertNull(result.content);
    }

    @Test public void closedLeaseAndBusyRequestHaveDifferentLifecycles() throws InterruptedException {
        assertFailureKind(new IllegalStateException("process exited"), true);
        assertFailureKind(new IllegalStateException("another request is running"), false);
    }

    private void assertFailureKind(RuntimeException failure, boolean fatal) throws InterruptedException {
        IRootNodeLease lease = new IRootNodeLease.Stub() {
            @Override public Bundle capture(float x, float y, String[] blocked, boolean text, boolean images,
                                            boolean press, boolean early, IRootNodeCandidate callback) { throw failure; }
            @Override public Bundle verifyWindow(String source, int windowId, Bundle requestedBounds) {
                return requestedBounds;
            }
            @Override public Bundle captureOcrRegion(String source, int windowId, Bundle requestedBounds) {
                return requestedBounds;
            }
            @Override public Bundle captureImage(String source, int windowId, Bundle requestedBounds) {
                return requestedBounds;
            }
            @Override public boolean verifySource(String source) { return true; }
            @Override public Bundle insertTextAt(float x, float y, String text) { throw failure; }
            @Override public boolean isOpen() { return !fatal; }
            @Override public void close() {}
        };
        SharedRootNodeCapture.Connection connection = new SharedRootNodeCapture.Connection(
                RuntimeEnvironment.getApplication(), new Binder(), 10000, lease);
        try {
            connection.capturePress(10, 10, Set.of(), true, false, true, source -> true);
            fail("Expected a failed request");
        } catch (IOException error) {
            if (fatal) assertTrue(error instanceof SharedRootNodeCapture.ConnectionFailedException);
            else assertFalse(error instanceof SharedRootNodeCapture.ConnectionFailedException);
        }
    }
}

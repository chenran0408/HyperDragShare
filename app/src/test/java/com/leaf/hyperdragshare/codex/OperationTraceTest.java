package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.os.Bundle;
import android.content.ClipData;
import android.os.PersistableBundle;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public final class OperationTraceTest {
    @Before public void prepare() {
        DragShareLog.configure(DragShareSettings.defaults());
        ShadowLog.clear();
    }
    @After public void restore() { DragShareLog.configure(DragShareSettings.defaults()); }

    @Test public void legacyBundleWithoutTraceRemainsUnmarked() {
        assertEquals("-", OperationTrace.read((Bundle) null));
        Bundle input = new Bundle();
        input.putString("text", "private payload");
        assertEquals("-", OperationTrace.read(input));
        OperationTrace.put(input, "press-example-42");
        assertEquals("press-example-42", OperationTrace.read(input));
        assertEquals("private payload", input.getString("text"));
        input.putString(OperationTrace.EXTRA, "content://secret/capability");
        assertEquals("-", OperationTrace.read(input));
        OperationTrace.put(Bundle.EMPTY, "diagnostic-only");
        assertEquals("-", OperationTrace.read(Bundle.EMPTY));
    }

    @Test public void assetFingerprintIsStableAndDoesNotContainCapabilityAddress() {
        Uri first = Uri.parse("content://com.example.share/shared/secret-uuid.png");
        String fingerprint = OperationTrace.asset(first);

        assertEquals(fingerprint, OperationTrace.asset(first));
        assertEquals(32, fingerprint.length());
        assertFalse(fingerprint.contains("secret-uuid"));
        assertFalse(fingerprint.contains(first.toString()));
        assertNotEquals("-", fingerprint);
        assertTrue(fingerprint.matches("[0-9a-f]{32}"));
        assertNotEquals(fingerprint, OperationTrace.asset(Uri.parse("content://private/another")));
        OperationTrace.event("press-example-42", "share", "launch_returned", first);
        OperationTrace.event("bad\nprivate body", "content://private/body", "bad outcome", first);
        String logs = messages();
        assertTrue(logs.contains("proc=" + DragShareLog.processInstance()));
        assertTrue(logs.contains("op=press-example-42 stage=share outcome=launch_returned"));
        assertTrue(logs.contains("op=- stage=- outcome=-"));
        assertFalse(logs.contains("content://"));
        assertFalse(logs.contains("secret-uuid"));
        assertFalse(logs.contains("private body"));
    }

    @Test public void existingRequestAndProcessLifetimeHaveSeparateIdentities() {
        String first = OperationTrace.id("press", 42);
        assertEquals(first, OperationTrace.id("press", 42));
        assertNotEquals(first, OperationTrace.id("press", 43));
        assertTrue(first.contains(DragShareLog.processInstance()));
        assertNotEquals(OperationTrace.fresh("stash"), OperationTrace.fresh("stash"));
        assertEquals("-", OperationTrace.id("press", -1));
    }

    @Test public void clipMetadataDoesNotMutateContentsOrSharedExtras() {
        ClipData clip = ClipData.newPlainText("label", "private clipboard body");
        PersistableBundle original = new PersistableBundle();
        original.putInt("existing", 8);
        clip.getDescription().setExtras(original);
        OperationTrace.put(clip, "stash-example-1");
        assertEquals("stash-example-1", OperationTrace.read(clip));
        assertEquals(8, clip.getDescription().getExtras().getInt("existing"));
        assertFalse(original.containsKey(OperationTrace.EXTRA));
        assertEquals("private clipboard body", clip.getItemAt(0).getText().toString());
        assertEquals(1, clip.getItemCount());
        assertEquals("-", OperationTrace.read((ClipData) null));
    }

    @Test public void fileDescriptorAccessDoesNotClaimContentReadOrDelivery() {
        OperationTrace.imageAccess(Uri.parse("content://private/random-id"), "descriptor_opened", 12345);
        String logs = messages();
        assertTrue(logs.contains("outcome=descriptor_opened"));
        assertTrue(logs.contains("callerUid=12345 read=unknown delivery=unknown"));
        assertFalse(logs.contains("outcome=completed"));
        assertFalse(logs.contains("random-id"));
    }

    @Test public void disabledDiagnosticsStillCarryOptionalMetadataWithoutLogging() {
        DragShareLog.configure(DragShareSettings.defaults().toBuilder()
                .setLogLevel(DragShareSettings.LOG_LEVEL_DISABLED).build());
        ClipData clip = ClipData.newPlainText("label", "payload");
        OperationTrace.put(clip, "stash-off-1");
        OperationTrace.event(OperationTrace.read(clip), "drag", "requested");
        assertEquals("payload", clip.getItemAt(0).getText().toString());
        assertEquals("stash-off-1", OperationTrace.read(clip));
        assertTrue(ShadowLog.getLogsForTag("DragShare/Operation").isEmpty());
    }

    private static String messages() {
        StringBuilder messages = new StringBuilder();
        for (ShadowLog.LogItem item : ShadowLog.getLogsForTag("DragShare/Operation")) {
            messages.append(item.msg).append('\n');
        }
        return messages.toString();
    }
}

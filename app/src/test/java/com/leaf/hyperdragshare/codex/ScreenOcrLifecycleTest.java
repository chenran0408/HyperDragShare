package com.leaf.hyperdragshare.codex;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import kotlin.Unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ScreenOcrLifecycleTest {
    @Test public void closingBeforeCaptureCancelsBothPendingAndAlreadyDispatchedWork() throws Exception {
        AtomicInteger closed = new AtomicInteger();
        AtomicInteger results = new AtomicInteger();
        AtomicBoolean restore = new AtomicBoolean(true);
        ResultReceiver receiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override protected void onReceiveResult(int code, Bundle data) {
                assertEquals(Activity.RESULT_CANCELED, code);
                restore.set(data.getBoolean("restore_panel"));
                results.incrementAndGet();
            }
        };
        ScreenOcrSession session = new ScreenOcrSession(RuntimeEnvironment.getApplication(),
                true, receiver, null, () -> { closed.incrementAndGet(); return Unit.INSTANCE; });
        AtomicBoolean sourceRemoved = new AtomicBoolean();
        session.start(() -> { sourceRemoved.set(true); return Unit.INSTANCE; });
        session.close(false);
        // A callback already dispatched to the main thread must not use the shut-down executor.
        var capture = ScreenOcrSession.class.getDeclaredMethod("captureAndRecognize");
        capture.setAccessible(true);
        capture.invoke(session);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(1));
        session.close(false);
        assertTrue(sourceRemoved.get());
        assertEquals(1, closed.get());
        assertEquals(1, results.get());
        assertFalse(restore.get());
    }
}

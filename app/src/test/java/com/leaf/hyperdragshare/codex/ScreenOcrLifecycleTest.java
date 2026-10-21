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
import kotlin.Unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ScreenOcrLifecycleTest {
    @Test public void closingBeforeCaptureCancelsBothPendingAndAlreadyDispatchedWork() throws Exception {
        AtomicInteger closed = new AtomicInteger();
        ScreenOcrSession session = new ScreenOcrSession(RuntimeEnvironment.getApplication(),
                true, () -> { closed.incrementAndGet(); return Unit.INSTANCE; });
        AtomicBoolean sourceRemoved = new AtomicBoolean();
        session.start(() -> { sourceRemoved.set(true); return Unit.INSTANCE; });
        session.close();
        // A callback already dispatched to the main thread must not use the shut-down executor.
        var capture = ScreenOcrSession.class.getDeclaredMethod("captureAndRecognize");
        capture.setAccessible(true);
        capture.invoke(session);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(1));
        session.close();
        assertTrue(sourceRemoved.get());
        assertEquals(1, closed.get());
    }
}

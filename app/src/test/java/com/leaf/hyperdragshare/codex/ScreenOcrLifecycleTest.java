package com.leaf.hyperdragshare.codex;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import java.time.Duration;

import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, application = android.app.Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ScreenOcrLifecycleTest {
    @Test public void leavingBeforeCaptureDoesNotSubmitToDestroyedExecutor() throws Exception {
        var controller = Robolectric.buildActivity(ScreenOcrActivity.class).create();
        var activity = controller.get();
        controller.destroy();
        // A callback already dispatched by the window must also be harmless after destruction.
        var capture = ScreenOcrActivity.class.getDeclaredMethod("captureAndRecognize");
        capture.setAccessible(true);
        capture.invoke(activity);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofSeconds(1));
        assertTrue(activity.isDestroyed());
    }
}

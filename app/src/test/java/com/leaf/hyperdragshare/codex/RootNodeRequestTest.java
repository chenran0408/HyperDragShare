package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.time.Duration;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAccessibilityNodeInfo;
import org.robolectric.shadows.ShadowAccessibilityWindowInfo;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.shadows.ShadowUiAutomation;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, shadows = RootNodeRequestTest.AutomationShadow.class)
@LooperMode(LooperMode.Mode.PAUSED)
public final class RootNodeRequestTest {
    @Test public void missingWindowEndsOnlyThisRequestAndNextPressCanRead() throws Exception {
        UiAutomation automation = Shadow.newInstanceOf(UiAutomation.class);
        AutomationShadow shadow = Shadow.extract(automation);
        Handler watchdog = new Handler(Looper.getMainLooper());
        JSONObject request = new JSONObject().put("x", 50).put("y", 50)
                .put("density", 1).put("width", 1000).put("height", 2000)
                .put("blocked", new org.json.JSONArray()).put("press_lookup", true);
        JSONObject absent = RootNodeCaptureMain.captureRequest(request, automation,
                new Object(), new int[]{0}, watchdog);
        assertFalse(absent.getBoolean("found"));
        assertEquals("window_not_ready", absent.getString("reason"));

        AccessibilityWindowInfo window = AccessibilityWindowInfo.obtain();
        ShadowAccessibilityWindowInfo windowShadow = Shadow.extract(window);
        windowShadow.setType(AccessibilityWindowInfo.TYPE_APPLICATION);
        windowShadow.setBoundsInScreen(new Rect(0, 0, 1000, 2000));
        shadow.root = AccessibilityNodeInfo.obtain();
        shadow.root.setPackageName("reader");
        shadow.root.setClassName("android.widget.TextView");
        shadow.root.setText("下一次按住仍可读取");
        shadow.root.setVisibleToUser(true);
        shadow.root.setBoundsInScreen(new Rect(0, 0, 300, 200));
        ShadowAccessibilityNodeInfo rootShadow = Shadow.extract(shadow.root);
        rootShadow.setAccessibilityWindowInfo(window);
        JSONObject found = RootNodeCaptureMain.captureRequest(request, automation,
                new Object(), new int[]{0}, watchdog);
        assertTrue(found.getBoolean("found"));
        assertEquals("下一次按住仍可读取", found.getString("text"));
        shadow.root.recycle();
        window.recycle();
    }

    @Implements(UiAutomation.class)
    public static class AutomationShadow extends ShadowUiAutomation {
        AccessibilityNodeInfo root;

        @Implementation protected void clearCache() {}

        @Implementation protected List<AccessibilityWindowInfo> getWindows() { return List.of(); }

        @Implementation protected AccessibilityNodeInfo getRootInActiveWindow() {
            if (root != null) return AccessibilityNodeInfo.obtain(root);
            // Advance the actual request deadline without sleeping or replacing its policy.
            ShadowSystemClock.advanceBy(Duration.ofMillis(1201));
            return null;
        }
    }
}

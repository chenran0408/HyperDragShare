package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.shadows.ShadowToast;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33)
public final class GameCapturePolicyTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Test public void optInPausesDeclaredGamesAndLeavesOtherAppsAvailable() {
        install("test.game", ApplicationInfo.CATEGORY_GAME);
        install("test.video", ApplicationInfo.CATEGORY_VIDEO);
        assertFalse(GameCapturePolicy.shouldPause(context, DragShareSettings.defaults(), "test.game"));
        assertTrue(GameCapturePolicy.shouldPause(context, enabled(), "test.game"));
        assertFalse(GameCapturePolicy.shouldPause(context, enabled(), "test.video"));
    }

    @Test public void missingSourcePreservesOriginalGestureWithAnExplanation() {
        assertTrue(GameCapturePolicy.shouldPause(context, enabled(), "test.missing"));
        ShadowLooper.shadowMainLooper().idle();
        assertTrue(ShadowToast.getTextOfLatestToast().contains("本次保留原操作"));
    }

    private static DragShareSettings enabled() {
        Bundle bundle = DragShareSettings.defaults().toBundle();
        bundle.putBoolean("pause_automatic_in_games", true);
        return DragShareSettings.fromBundle(bundle);
    }

    private void install(String name, int category) {
        PackageInfo info = new PackageInfo();
        info.packageName = name;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = name;
        info.applicationInfo.category = category;
        ShadowPackageManager packages = Shadow.extract(context.getPackageManager());
        packages.installPackage(info);
    }
}

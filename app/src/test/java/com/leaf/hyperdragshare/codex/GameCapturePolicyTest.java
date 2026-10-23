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
        install("com.eg.android.AlipayGphone", ApplicationInfo.CATEGORY_UNDEFINED);
        GameCapturePolicy.SourceAppClassifier classifier = name -> context.getPackageManager()
                .getApplicationInfo(name, 0).category;
        assertFalse(GameCapturePolicy.shouldPause(context, DragShareSettings.defaults(), "test.game", classifier));
        assertTrue(GameCapturePolicy.shouldPause(context, enabled(), "test.game", classifier));
        assertFalse(GameCapturePolicy.shouldPause(context, enabled(), "test.video", classifier));
        assertFalse(GameCapturePolicy.shouldPause(context, enabled(), "com.eg.android.AlipayGphone", classifier));
    }

    @Test public void missingSourcePreservesOriginalGestureWithAnExplanation() {
        assertTrue(GameCapturePolicy.shouldPause(context, enabled(), "test.missing",
                name -> { throw new android.content.pm.PackageManager.NameNotFoundException(name); }));
        ShadowLooper.shadowMainLooper().idle();
        assertTrue(ShadowToast.getTextOfLatestToast().contains("保留原来的长按操作"));
    }

    @Test public void categoryLookupFailurePreservesOriginalGesture() {
        assertTrue(GameCapturePolicy.shouldPause(context, enabled(), "test.denied",
                name -> { throw new SecurityException("test permission denial"); }));
        ShadowLooper.shadowMainLooper().idle();
        assertTrue(ShadowToast.getTextOfLatestToast().contains("保留原来的长按操作"));
    }

    @Test public void builtInGamesDoNotRequireGameCategoryAndMatchExactly() {
        install("com.tencent.tmgp.sgame", ApplicationInfo.CATEGORY_UNDEFINED);
        install("com.tencent.tmgp.sgame.tools", ApplicationInfo.CATEGORY_UNDEFINED);
        GameCapturePolicy.SourceAppClassifier classifier = name -> context.getPackageManager()
                .getApplicationInfo(name, 0).category;
        assertTrue(GameCapturePolicy.shouldPause(context, enabled(), "com.tencent.tmgp.sgame", classifier));
        assertFalse(GameCapturePolicy.shouldPause(context, enabled(), "com.tencent.tmgp.sgame.tools", classifier));
        assertFalse(GameCapturePolicy.shouldPause(context, DragShareSettings.defaults(), "com.tencent.tmgp.sgame", classifier));
    }

    @Test public void manualGamesSurviveStorageAndProviderBundleAndRespectSwitch() {
        install("test.custom", ApplicationInfo.CATEGORY_UNDEFINED);
        Bundle bundle = enabled().toBundle();
        bundle.putStringArrayList("manual_game_packages", new java.util.ArrayList<>(java.util.List.of("test.custom")));
        DragShareSettings configured = DragShareSettings.fromBundle(bundle);
        configured.saveLocal(context);
        DragShareSettings restored = DragShareSettings.readLocal(context);
        assertTrue(restored.manualGamePackages.contains("test.custom"));
        assertTrue(GameCapturePolicy.shouldPause(context, DragShareSettings.fromBundle(restored.toBundle()), "test.custom",
                name -> context.getPackageManager().getApplicationInfo(name, 0).category));
        bundle.putBoolean("pause_automatic_in_games", false);
        assertFalse(GameCapturePolicy.shouldPause(context, DragShareSettings.fromBundle(bundle), "test.custom",
                name -> context.getPackageManager().getApplicationInfo(name, 0).category));
        bundle.putBoolean("pause_automatic_in_games", true);
        bundle.putStringArrayList("manual_game_packages", new java.util.ArrayList<>());
        assertFalse(GameCapturePolicy.shouldPause(context, DragShareSettings.fromBundle(bundle), "test.custom"));
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

package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/** Applies only to automatic capture; explicit point/OCR requests do not call this policy. */
final class GameCapturePolicy {
    private GameCapturePolicy() {}

    static boolean shouldPause(Context context, DragShareSettings settings, String sourcePackage) {
        if (!settings.pauseAutomaticInGames) return false;
        try {
            if (sourcePackage == null || sourcePackage.isBlank()) {
                throw new PackageManager.NameNotFoundException("来源应用信息为空");
            }
            ApplicationInfo app = context.getPackageManager().getApplicationInfo(
                    sourcePackage, PackageManager.ApplicationInfoFlags.of(0));
            boolean game = app.category == ApplicationInfo.CATEGORY_GAME;
            if (game) DragShareLog.i("DragShare/GamePause", "automatic capture paused package=" + sourcePackage);
            return game;
        } catch (PackageManager.NameNotFoundException | SecurityException error) {
            DragShareLog.w("DragShare/GamePause", "Unable to classify source; preserving original gesture", error);
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context,
                    "无法确认来源应用类型，本次保留原操作：" + error.getMessage(), Toast.LENGTH_LONG).show());
            return true;
        }
    }
}

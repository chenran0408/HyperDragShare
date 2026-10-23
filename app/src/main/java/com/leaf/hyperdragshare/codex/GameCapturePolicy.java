package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/** Applies only to automatic capture; explicit point/OCR requests do not call this policy. */
public final class GameCapturePolicy {
    // Exact packages only: never classify a publisher’s chat/store apps as games.
    private static final java.util.Set<String> BUILT_IN_GAMES = java.util.Set.of(
            "com.tencent.tmgp.sgame", // 王者荣耀
            "com.tencent.tmgp.pubgmhd", // 和平精英
            "com.tencent.lolm", // 英雄联盟手游
            "com.tencent.jkchess", // 金铲铲之战
            "com.tencent.tmgp.cf", // 穿越火线
            "com.tencent.tmgp.speedmobile", // QQ飞车
            "com.tencent.tmgp.dfm", // 三角洲行动
            "com.tencent.tmgp.cod", // 使命召唤手游
            "com.miHoYo.hkrpg", // 星穹铁道
            "com.miHoYo.Nap", // 绝区零
            "com.tencent.ig", // PUBG Mobile
            "com.roblox.client", // Roblox
            "com.dts.freefireth", // Free Fire
            "com.riotgames.league.wildrift", // Wild Rift
            "com.miHoYo.GenshinImpact", // Genshin Impact
            "com.HoYoverse.hkrpgoversea", // Honkai Star Rail
            "com.HoYoverse.Nap", // Zenless Zone Zero
            "com.supercell.clashofclans", // Clash of Clans
            "com.supercell.clashroyale", // Clash Royale
            "com.supercell.brawlstars", // Brawl Stars
            "com.mojang.minecraftpe", // Minecraft
            "com.activision.callofduty.shooter" // Call of Duty Mobile
    );

    public static boolean isBuiltInGame(String packageName) {
        return packageName != null && BUILT_IN_GAMES.contains(packageName);
    }
    private GameCapturePolicy() {}

    static boolean shouldPause(Context context, DragShareSettings settings, String sourcePackage) {
        return shouldPause(context, settings, sourcePackage,
                packageName -> classifySourceApp(context, packageName));
    }

    static boolean shouldPause(Context context, DragShareSettings settings, String sourcePackage,
                               SourceAppClassifier classifier) {
        if (!settings.pauseAutomaticInGames) return false;
        try {
            if (sourcePackage == null || sourcePackage.isBlank()) {
                throw new PackageManager.NameNotFoundException("来源应用信息为空");
            }
            if (isBuiltInGame(sourcePackage) || settings.manualGamePackages.contains(sourcePackage)) {
                DragShareLog.i("DragShare/GamePause", "automatic capture paused package=" + sourcePackage);
                return true;
            }
            int category = classifier.categoryFor(sourcePackage);
            boolean game = category == ApplicationInfo.CATEGORY_GAME;
            if (game) DragShareLog.i("DragShare/GamePause", "automatic capture paused package=" + sourcePackage);
            return game;
        } catch (PackageManager.NameNotFoundException | RuntimeException error) {
            DragShareLog.w("DragShare/GamePause", "Unable to classify source; preserving original gesture", error);
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context,
                    "暂时无法确认此应用的类型，已保留原来的长按操作", Toast.LENGTH_LONG).show());
            return true;
        }
    }

    private static int classifySourceApp(Context context, String sourcePackage)
            throws PackageManager.NameNotFoundException {
        Bundle request = new Bundle();
        request.putString(ShareImageProvider.EXTRA_SOURCE_PACKAGE, sourcePackage);
        Bundle result = context.getContentResolver().call(ImageStagingClient.BASE_URI,
                ShareImageProvider.METHOD_CLASSIFY_SOURCE_APP, null, request);
        if (result == null || !result.getBoolean("known", false)) {
            throw new PackageManager.NameNotFoundException(sourcePackage);
        }
        return result.getInt("category", ApplicationInfo.CATEGORY_UNDEFINED);
    }

    interface SourceAppClassifier {
        int categoryFor(String sourcePackage) throws PackageManager.NameNotFoundException;
    }
}

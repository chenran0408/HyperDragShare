package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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
        if (!settings.pauseAutomaticInGames) return false;
        try {
            if (sourcePackage == null || sourcePackage.isBlank()) {
                throw new PackageManager.NameNotFoundException("来源应用信息为空");
            }
            if (isBuiltInGame(sourcePackage) || settings.manualGamePackages.contains(sourcePackage)) {
                DragShareLog.i("DragShare/GamePause", "automatic capture paused package=" + sourcePackage);
                return true;
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

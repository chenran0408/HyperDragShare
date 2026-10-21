# 游戏自动触发避让

169增加精确包名内置名单和独立手动名单，均受“游戏内暂停自动触发”控制。声明CATEGORY_GAME的其他游戏仍被识别。仅暂停已有传送门/无障碍自动抓取与按压确认，不关闭应用、不修改游戏、不新增游戏Hook；手动点取和OCR保留，合并手势的边缘动作不属于本开关。

手动项通过DragShareSettings本地保存和Provider Bundle传给宿主；设置页复用Miuix应用列表，自动游戏只显示状态，其他应用可添加或移除。渠道变体按精确包名匹配，未覆盖的变体可手动添加，不按腾讯/网易等厂商前缀拦截。

## 内置名单与核对来源

2026-10-21核对官方应用商店页面。以下是包名名单，不代表逐游戏实测。

| 游戏 | 包名 | 来源 |
| --- | --- | --- |
| 王者荣耀 | `com.tencent.tmgp.sgame` | [商店页面](https://r.app.xiaomi.com/details?id=com.tencent.tmgp.sgame) |
| 和平精英 | `com.tencent.tmgp.pubgmhd` | [商店页面](https://r.app.xiaomi.com/details?id=com.tencent.tmgp.pubgmhd) |
| 英雄联盟手游 | `com.tencent.lolm` | [商店页面](https://r.app.xiaomi.com/details?id=com.tencent.lolm) |
| 金铲铲之战 | `com.tencent.jkchess` | [商店页面](https://r.app.xiaomi.com/details?id=com.tencent.jkchess) |
| 穿越火线 | `com.tencent.tmgp.cf` | [商店页面](https://r.app.xiaomi.com/details?id=com.tencent.tmgp.cf) |
| QQ飞车 | `com.tencent.tmgp.speedmobile` | [商店页面](https://r.app.xiaomi.com/details?id=com.tencent.tmgp.speedmobile) |
| 三角洲行动 | `com.tencent.tmgp.dfm` | [商店页面](https://sj.qq.com/appdetail/com.tencent.tmgp.dfm) |
| 使命召唤手游 | `com.tencent.tmgp.cod` | [商店页面](https://sj.qq.com/appdetail/com.tencent.tmgp.cod) |
| 星穹铁道 | `com.miHoYo.hkrpg` | [商店页面](https://sj.qq.com/appdetail/com.miHoYo.hkrpg) |
| 绝区零 | `com.miHoYo.Nap` | [商店页面](https://sj.qq.com/appdetail/com.miHoYo.Nap) |
| PUBG Mobile | `com.tencent.ig` | [商店页面](https://play.google.com/store/apps/details?id=com.tencent.ig) |
| Roblox | `com.roblox.client` | [商店页面](https://play.google.com/store/apps/details?id=com.roblox.client) |
| Free Fire | `com.dts.freefireth` | [商店页面](https://play.google.com/store/apps/details?id=com.dts.freefireth) |
| Wild Rift | `com.riotgames.league.wildrift` | [商店页面](https://play.google.com/store/apps/details?id=com.riotgames.league.wildrift) |
| Genshin Impact | `com.miHoYo.GenshinImpact` | [商店页面](https://play.google.com/store/apps/details?id=com.miHoYo.GenshinImpact) |
| Honkai Star Rail | `com.HoYoverse.hkrpgoversea` | [商店页面](https://play.google.com/store/apps/details?id=com.HoYoverse.hkrpgoversea) |
| Zenless Zone Zero | `com.HoYoverse.Nap` | [商店页面](https://play.google.com/store/apps/details?id=com.HoYoverse.Nap) |
| Clash of Clans | `com.supercell.clashofclans` | [商店页面](https://play.google.com/store/apps/details?id=com.supercell.clashofclans) |
| Clash Royale | `com.supercell.clashroyale` | [商店页面](https://play.google.com/store/apps/details?id=com.supercell.clashroyale) |
| Brawl Stars | `com.supercell.brawlstars` | [商店页面](https://play.google.com/store/apps/details?id=com.supercell.brawlstars) |
| Minecraft | `com.mojang.minecraftpe` | [商店页面](https://play.google.com/store/apps/details?id=com.mojang.minecraftpe) |
| Call of Duty Mobile | `com.activision.callofduty.shooter` | [商店页面](https://play.google.com/store/apps/details?id=com.activision.callofduty.shooter) |

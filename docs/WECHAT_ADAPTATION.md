# 微信页面与输入适配初查

## 样本

设备 `b0ba578f` 上的 `com.tencent.mm` 为 8.0.79（versionCode 3182）。本地只读样本位于任务工作目录 `work/settings179/wechat-apk/`，包含设备安装的 base 与五个 split。base SHA-256：`A92EF972D3924A7013244E36AE83B804087877368BFB144ABF2E64DC9B3A913A`。

| APK | SHA-256 |
|---|---|
| `split_config.arm64_v8a.apk` | `0C931A367BD979D96E338829DA0C02B34156FF2AB453F57ABFFCBCF633CFCD54` |
| `split_config.xxxhdpi.apk` | `B11CE8E2669318EEC93DA4698A514967963E2FFD3AAFED79A22C213BCA7FE245` |
| `split_config.zh.apk` | `60616B3802443051979EEEEFEDDBD9650AAC4C75E760D1A6BAD2C12595938897` |
| `split_delivery.apk` | `9EC87B8F91FFD1F877D3E95E2E05B87B31794B836250A74814CAB6DB30923BB7` |
| `split_delivery.config.arm64_v8a.apk` | `8F427125CE458297682A4975B14063FDD63EAEFCA7DCDD274697B1B03D9A9EA8` |

## 已定位的普通页面族

DEX 类索引与 base APK manifest 静态确认了多个彼此独立的界面族，说明只覆盖登录和聊天 Activity 会漏掉常见内容窗口：

| 页面族 | manifest / DEX 中的类 | 状态 |
|---|---|---|
| 主界面 | `com.tencent.mm.ui.LauncherUI` | 已定位 |
| 聊天 | `com.tencent.mm.ui.chatting.ChattingUI`、`ChattingUIFragment` | 已定位；`ChattingUI` 持有并委托给 fragment |
| 网页/H5 | `com.tencent.mm.plugin.webview.ui.tools.WebViewUI`、`MMWebViewUI` | 已定位；`WebViewUI` 在 `:tools` 进程 |
| 小程序 | `com.tencent.mm.plugin.appbrand.ui.AppBrandUI`、`AppBrandUI1`、`AppBrandLauncherUI` | 已定位；两个运行 Activity 分属 `:appbrand0`、`:appbrand1` |
| 朋友圈 | manifest 注册 `com.tencent.mm.plugin.sns.ui.improve.ImproveSnsTimelineUI`；DEX 另有 `SnsTimeLineUI` | 以前者为当前 manifest 页面；后者是否仍由运行时启动未验证 |
| 收藏与内容详情 | `FavoriteIndexUI`、`FavoriteFileDetailUI`、`FavoriteImgDetailUI`、`FavoriteSightDetailUI` | 已定位 |
| 图片查看 | `com.tencent.mm.ui.chatting.gallery.ImageGalleryUI`、`com.tencent.mm.ui.tools.ShowImageUI` | 已定位 |

base manifest 的包名为 `com.tencent.mm`。这些 manifest Activity 都以该包注册；小程序虽运行在独立进程，仍是该应用包的 Activity。由此推断，按两个 Activity 名称开白名单无法代表“微信普通内容页”的整体范围。此处只确认 APK 静态组件；没有打开或截图任何微信页面。

## 文字长按与系统拖放证据

- `com.tencent.mm.ui.widget.MMTextView` 继承 Android `TextView`，覆写 `onTouchEvent` 和 `performLongClick` 并调用父类；`com.tencent.mm.ui.widget.cedit.edit.CustomTextView` 也继承 `TextView`。聊天消息区因此存在原生文本控件路径，实际可选择内容受具体消息视图及其监听器影响。
- `com.tencent.mm.plugin.fav.ui.detail.FavoriteImgDetailUI` 给图片视图安装 `OnLongClickListener` 与 `OnTouchListener`。`WebViewUI` 也配置触摸及长按监听。界面能力由视图/内容类型决定，不由页面 Activity 名称单独决定。
- DEX 中确认 `com.tencent.mm.ui.chatting.component.ib`、`com.tencent.mm.plugin.sns.ui.ev` 和 `SnsUploadUI$2$1` 实现 `View.OnDragListener`，其回调签名为 `onDrag(View, DragEvent)`；`b92.v.onTouch` 调用了 `View.startDragAndDrop`。这是 Android 原生拖放 API 的静态证据。具体监听器挂载的 View、接受的 ClipData 内容和各页面实际可投放区域尚未完整追踪或运行验证。

## 对接边界

适配范围宜按 SystemUI 所见的窗口所有者包名覆盖 `com.tencent.mm` 的普通应用窗口，再按窗口类型排除系统窗口与输入法窗口；不要维护登录/聊天两项 Activity 白名单。小程序独立进程仍归同一包名。窗口带 `FLAG_SECURE` 时应跳过；密码/敏感输入节点应按输入类型和节点属性跳过；IME、系统对话框及非应用窗口不作为可读写内容目标。窗口身份可统一覆盖页面，文本读取、长按和拖放仍要尊重每个控件暴露的原生节点/接收器。

以上是 manifest、DEX 索引和单类静态读取结果，未在设备上逐页验证、未验证无障碍节点实际可见性或每个拖放 View 的运行时注册情况。APK 只用于本机只读分析，未修改或重签。

## 179 已落地的适配

窗口识别已从LauncherUI/ChattingMainUI两项名单改为微信包名与同包组件身份，并沿用应用窗口类型、默认显示、安全旗和实际遮挡核验。可读节点仍优先取文字；根缺失、没有窗口或不可见时才由WMS确认普通微信窗口后取有限区域图，交已有AICR。密码、按住说话、输入法、黑名单、窗口变化等明确拒绝不会被转成再次OCR。

当前ROM的WindowState不提供frame；解析器按同一dump中visible handle的token和完整窗口名关联范围。SurfaceFlinger实际记录确认GestureStubLeft/Right是裁剪的透明手势输入层，故仅对已核验的桌面owner/type/flags组合排除假遮挡；其他覆盖窗不因此放行。导航栏无handle时使用该窗实际navigationBars Insets和默认显示逻辑尺寸扣底边，不推测小窗大小。

WMS捕获与来源复核共用窗口身份，保留`window_id=-2`，不会重新要求微信出现在无障碍窗口树。拒绝原因从Root回复传至模块日志，不记录识别文字。未给微信增加LSPosed注入、不修改其APK或账号状态。404项既有与必要回归在build375通过；最终包、安装与各页面真实操作状态以CURRENT_STATUS为准。

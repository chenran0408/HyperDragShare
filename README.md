# HyperDragShare — HyperOS 增强版

基于 [Leaf-lsgtky/HyperDragShare](https://github.com/Leaf-lsgtky/HyperDragShare) 的开源分支，为 Xiaomi HyperOS 增加拖拽手势、原页分词、OCR 和临时中转站。

[下载正式版 APK](https://github.com/chenran0408/HyperDragShare/releases/latest) · 仅 **arm64-v8a**，使用 **R8 压缩**。

当前正式版 **1.7.179**；具体更新与验证范围见[发布说明](https://github.com/chenran0408/HyperDragShare/releases/tag/v1.7.179)。

## 用户使用

需要 Root 和 LSPosed。当前适配目标是 **Xiaomi 15 Pro、HyperOS 4.0.0.17、Android 17**；其他设备和 ROM 未验收。项目最低 Android 版本为 13，但不代表其他系统已验证。

1. 安装 APK，在 LSPosed 启用模块并勾选推荐作用域，授予 Root 和悬浮窗权限。首次启用后重启；以后设置中可重载相关进程，系统框架代码更新仍需软重启或整机重启。
2. 打开设置，调整取词时间和需要的手势。默认使用传送门；只有选择无障碍来源时才需启用本模块无障碍服务。首页可搜索设置，点结果直接定位；未启用的功能会说明前置开关。
3. 回到其他应用使用，操作在当前页面上方完成，只有设置需要进入本应用。

- **分词**：单指静止按住文字，选词后复制、搜索、查词、分享或直接拖出；全选后可拆词，支持合并和撤销。根据按住时长、位移判断，不检测真实触摸面积。
- **中转站**：临时收集图文，点灰色侧条展开；拖动移动，松手自动贴边。按住内容直接拖给其他应用，多选文字可一起拖出。微信接收后可能直接发送，请先在文件传输助手试用。
- **识字与复制直达**：图片、屏幕/选区识字统一使用系统 AICR V2，不附带其他 OCR 模型；系统服务不可用会说明原因。复制直达需在设置中开启，可显示实体建议并指定网页浏览器。链接、拨号、邮件均由用户主动点按执行。
- **手势与侧栏**：底角、侧边、蜂巢和小窗手势，左右底角可分别关闭；原生 Dock 增加固定功能入口，快捷应用仍保留。游戏避让和应用黑名单可在设置中管理。

推荐作用域：传送门、澎湃 AI 引擎、系统框架、系统界面、系统桌面、安全服务。安全服务 Hook 仅在 `com.miui.securitycenter:ui` 工作；不要加入 `com.miui.contentcatcher`。

设置分为**传送门与大爆炸、手势增强、小窗增强、复制直达**。小窗尺寸在“小窗增强”，蜂巢和应用目标在“手势增强”；主题、权限、重载、日志和关于放在“通用设置”。升级保留原配置，不再使用旧 OCR 引擎选择。

在“通用设置 › 关于 › 检查更新”查看正式版说明并下载；完成后点“安装”，按系统提示授权并确认。只下载本仓库的 arm64 正式包，不自动安装或降级。访问 GitHub 需要可用网络。

**范围说明：** AICR V2 是本项目调用的系统文字识别服务，不等于小爱识屏；小爱识屏私有调用链未接入。识别已有 Bitmap 在目标 ROM 上使用普通应用 UID，不需要 Root 或 LSPosed；获取其他应用截图、Root 取词与 Hook 则是独立能力。所需权限或系统服务不可用时会提示失败，不会静默切换来源。

## 开发者接口

| 集成点 | 当前实现 | 适用范围 |
|---|---|---|
| 传送门 | `PortalHooks.java`、`PortalContentCaptureSource.java` | Portal 5.1.5；Hook 传送门识别与复制卡片入口 |
| 原生侧栏 | `NativeSidebarHooks.java` | 安全中心 40001365、SDK 37 的 `com.miui.securitycenter:ui`；扩展原生 `p0` Dock，不替换其快捷应用列表 |
| AICR 图片 OCR | `AicrOcrClient.java` | AICR Core 视觉服务 V2；普通应用 UID Binder 客户端 |
| AICR 实体识别 | `AicrNerClient.java`、`CopyDirectSuggestions.kt` | AICR Core NER V1；显式文本请求与内联建议 |
| Root 输入 | `RootTouchSource.java`、`RootNodeCaptureClient.java` | 通过 Root evdev 输入与受限节点读取；不依赖触点面积 |

普通应用可参考客户端调用（回调主线程，失败通过 `error` 返回；页面关闭调用请求的 `cancel()`）：

```java
AicrOcrClient.Request ocr = AicrOcrClient.recognize(context, bitmap, (text, error) -> { /* 显示结果或错误 */ });
AicrNerClient.Request ner = AicrNerClient.extract(context, text, (entities, error) -> { /* 显示实体或错误 */ });
```

客户端直接绑定 `com.xiaomi.aicr.access.AiCrCoreService`：OCR 使用 `IVisionService` V2，实体识别使用 `INerService` V1；保持 Bitmap 存活至 OCR 回调。具体绑定参数和 Parcel 协议见对应源码；小爱能力查询 Provider 不能用于 OCR。

以上 ROM 私有接口只针对表中版本做了本机静态/运行核验，不是跨 ROM 的公开稳定 API。系统版本变化后应重新核对 Binder 协议、类名和布局；未核验时让 Hook 明确失败并保留原生行为。更多设计边界与历史验证见[实现说明](docs/IMPLEMENTATION.md)、[设备记录](docs/DEVICE_VALIDATION.md)、[OCR 说明](docs/IMAGE_OCR.md)和[中转站说明](docs/STASH.md)。

## 构建

使用 JDK 21 与 Android SDK 37：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleRelease
```

Release 启用 R8；自行构建需配置自己的签名。构建 APK 位于 `app/build/outputs/apk/release/`；设备验收与发布状态以[当前状态记录](docs/CURRENT_STATUS.md)及 GitHub Releases 为准。

## 许可与致谢

保留 HyperDragShare 原作者及贡献者的著作权与项目历史；合并手势来自用户的 [FanFreeform](https://github.com/chenran0408/FanFreeform)，界面使用 [Miuix](https://github.com/compose-miuix-ui/miuix)。本分支沿用 [GPL-3.0-only](LICENSE)。第三方组件和参考项目的许可证、归属说明以对应源码文件为准；本项目不分发参考 APK，也不复制反编译实现。

## English

HyperDragShare is an open-source HyperOS fork for drag gestures, in-page text selection, OCR, and a temporary drag-and-drop stash. It requires an arm64-v8a device, Root and LSPosed. The verified target is Xiaomi 15 Pro / HyperOS 4.0.0.17 / Android 17; other ROMs are unverified. AICR V2 is an OCR service, not Xiaomi's screen-recognition feature. Release APKs are optimized with R8; check [GitHub Releases](https://github.com/chenran0408/HyperDragShare/releases/latest) for the APK. Licensed under GPL-3.0-only; upstream attribution is preserved.

# HyperDragShare — HyperOS 增强版

基于 [Leaf-lsgtky/HyperDragShare](https://github.com/Leaf-lsgtky/HyperDragShare) 的开源分支，为 Xiaomi HyperOS 增加拖拽手势、原页分词、OCR 和临时中转站。

[下载正式版 APK](https://github.com/chenran0408/HyperDragShare/releases/latest) · 仅 **arm64-v8a**，使用 **R8 压缩**。

当前开发批次为 **1.7.213**，正式版仍以 [GitHub Releases](https://github.com/chenran0408/HyperDragShare/releases/latest) 为准。

## 用户使用

本项目仅面向已取得 **Root** 并安装 **LSPosed** 的用户，不提供无 Root 使用路径。当前适配目标是 **Xiaomi 15 Pro、HyperOS 4.0.0.17、Android 17**；其他设备和 ROM 未验收。项目最低 Android 版本为 13，但不代表其他系统已验证。

1. 安装 APK，在 LSPosed 启用模块并勾选推荐作用域，授予 Root 和悬浮窗权限。首次启用后重启；以后设置中可重载相关进程，系统框架代码更新仍需软重启或整机重启。
2. 打开设置，调整取词时间和需要的手势。长按取词固定使用传送门 Hook、Root evdev 与 Root helper，不需要也不会申请系统无障碍服务。首页可搜索设置，点结果直接定位；未启用的功能会说明前置开关。
3. 回到其他应用使用，操作在当前页面上方完成，只有设置需要进入本应用。

启动或重载时会用 Root 将本模块和传送门加入小米的后台锁定名单，减少一键清理中断；系统“强行停止”仍然有效。

- **分词**：单指静止按住文字，选词后复制、搜索、查词或直接拖出；识别到链接、邮箱或电话时，会出现可点击的直达提示卡。使用系统 ICU 分词，支持多语言混排；全选后可拆词，支持合并和撤销。根据按住时长、位移判断，不检测真实触摸面积。
- **中转站**：临时收集图文，点灰色侧条展开；拖动移动，松手自动贴边。按住内容直接拖给其他应用，多选文字可一起拖出。微信接收后可能直接发送，请先在文件传输助手试用。
- **识字与拖拽**：按住图片后拖向屏幕四边，从菜单选择识字、扫码或其他目标。识字使用系统 AICR V2，二维码和条形码使用系统 AICR 条码插件，不附带分词词典、OCR 模型或扫码引擎。微信优先接收传送门文字/图片结果，取不到文字时再局部识字；系统服务不可用会说明原因。
- **复制直达**：在设置中开启后，系统复制直达入口会显示短暂气泡；识别到链接、邮箱或电话可直接点击取用，旁边的分词图标进入原页分词。气泡不抢键盘，点外部或滑动即可收起，约6秒自动消失。网页可指定浏览器，不会自动打开或发送内容。
- **手势与侧栏**：底角扇形、二段蜂巢和小窗动作，左右底角可分别关闭；原生 Dock 增加固定功能入口，快捷应用仍保留。边缘侧滑交给 HyperOS 原生侧栏，不再由本模块抢占。游戏避让和应用黑名单可在设置中管理。

推荐作用域：传送门、澎湃 AI 引擎、系统框架、系统界面、系统桌面、安全服务。安全服务 Hook 仅在 `com.miui.securitycenter:ui` 工作；不要加入 `com.miui.contentcatcher`。

设置分为**传送门与大爆炸、手势增强、小窗增强、复制直达**。小窗尺寸在“小窗增强”，蜂巢和应用目标在“手势增强”；主题、权限、重载、日志和关于放在“通用设置”。升级保留原配置，不再使用旧 OCR 引擎选择。

在“通用设置 › 关于 › 检查更新”查看正式版说明并下载；完成后点“安装”，按系统提示授权并确认。只下载本仓库的 arm64 正式包，不自动安装或降级。访问 GitHub 需要可用网络。

**范围说明：** AICR V2 是本项目调用的系统文字识别服务，不等于小爱识屏；小爱识屏私有调用链未接入。产品运行需要 Root 与 LSPosed：Root evdev 提供按压判定，传送门 Hook 取得宿主触点，Root helper 在安全边界内读取节点或截取目标区域，AICR V2 识别图片。AICR Binder 客户端本身不额外申请 Root，但这不构成本模块的无 Root 运行方式；也不再提供系统无障碍服务作为替代来源。所需权限或系统服务不可用时会提示失败，不会静默切换来源。

## 开发者接口

| 集成点 | 当前实现 | 适用范围 |
|---|---|---|
| 传送门 | `PortalHooks.java`、`PortalContentCaptureSource.java` | Portal 5.1.5；Hook 传送门识别与复制卡片入口 |
| 原生侧栏 | `NativeSidebarHooks.java` | 安全中心 40001365、SDK 37 的 `com.miui.securitycenter:ui`；扩展原生 `p0` Dock，不替换其快捷应用列表 |
| AICR 图片 OCR | `AicrOcrClient.java` | AICR Core 视觉服务 V2；普通应用 UID Binder 客户端 |
| AICR 实体识别 | `AicrNerClient.java`、`CopyDirectSuggestions.kt` | AICR Core NER V1；显式文本请求与内联建议 |
| 系统条码识别 | `AicrBarcodeClient.java` | AICR 4.12.26 的 `IBarcodeService`；多码结果回调 |
| 系统多语言分词 | `TextSegmenter.java` | Android ICU 词边界与字素边界；保留原文位置 |
| Root 输入 | `RootTouchSource.java`、`RootNodeCaptureClient.java` | 通过 Root evdev 输入与受限节点读取；不依赖触点面积 |

普通应用可参考客户端调用（回调主线程，失败通过 `error` 返回；页面关闭调用请求的 `cancel()`）：

```java
AicrOcrClient.Request ocr = AicrOcrClient.recognize(context, bitmap, (text, error) -> { /* 显示结果或错误 */ });
AicrNerClient.Request ner = AicrNerClient.extract(context, text, (entities, error) -> { /* 显示实体或错误 */ });
AicrBarcodeClient.Request scan = AicrBarcodeClient.scan(context, bitmap, (codes, error) -> { /* 显示码值或错误 */ });
```

客户端直接绑定 `com.xiaomi.aicr.access.AiCrCoreService`：OCR 使用 `IVisionService` V2，实体识别使用 `INerService` V1，扫码使用 `IBarcodeService`，申请版本为空字符串。保持 Bitmap 存活至请求回调。绑定参数和扫码协议见[系统分词与条码接口](docs/SYSTEM_TEXT_AND_BARCODE.md)；小爱能力查询 Provider 不能用于 OCR。

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

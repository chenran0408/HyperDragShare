# HyperDragShare · HyperOS 4 适配分支

这是基于 HyperDragShare 代码继续开发的独立 fork，目标是在小米 HyperOS 4 上增强文字和图片拖拽分享。项目按本分支代码和适配目标维护；MI Big Bang APK 与 BigBang/NovaText 项目只作为行为和技术分析参考，不作为本项目的需求清单。

当前适配目标是 Xiaomi 15 Pro、HyperOS 4.0.0.17、Android 17。传送门长按与 Root evdev 跟手、图片分享和本地 OCR、文本分词与应用内搜索均由本分支实现；屏幕 OCR 磁贴可截取整屏并进入区域选择识字。AICR 文本直取、背部轻敲和 OCR 目前仍待目标设备验证。搜索、OCR、分词和设置页使用 Miuix 主题；拖拽菜单保留专用 Canvas/Compose 绘制。

## 当前开发状态

- 分享菜单提供简洁、流光、环形和现代四种样式。设备上已确认流光和环形可用；本分支已移除简洁/现代样式多出的方向位移门槛，并增加四边触发边界测试，修复仍待目标设备验证。
- 图片、屏幕和所选区域 OCR 使用本地中文 ML Kit，识别结果可进入分词页；分词页可复制、搜索、查词典、翻译、分享和拆分已选词语，也能从原图重新识字。
- 应用内搜索支持多种搜索、词典和翻译入口，可修改查询并调整面板高度；搜索与 OCR/分词页面的操作外壳接入 Miuix 主题。
- 文本面板可选开启选词触感反馈和复制后自动退出，两项默认关闭。
- 新增的系统集成入口默认不接管系统动作：AICR 文本直取默认关闭，背部轻敲默认关闭；屏幕 OCR 由快捷设置磁贴主动启动，只在本机用 ML Kit 识别。
- 当前版本尚未在目标设备完成安装验收。背部轻敲 Hook 只针对 `MiuiBackTapGestureService` 回调，已排除参考 APK 的常驻传感器重注册线程；HyperOS 4 上类名、回调和权限表现仍需实机确认。

## 使用要求

- Android 13 或更高版本；本分支主要面向 Android 17。
- 已安装 LSPosed 并启用模块，作用域选择 `com.miui.contentextension`；启用 AICR 文本直取时再加入 `com.xiaomi.aicr`，启用背部轻敲时再加入 `android`。
- Root 权限用于读取触摸设备输入，使预览在同一拖拽手势中跟随手指。
- 传送门版本和 HyperOS 4 适配状态以本分支的设备验证记录为准；当前目标版本尚未完成本轮实机验收。

## 构建

项目使用 Java/Kotlin 17。Windows 下运行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 开发说明

迁移阶段、静态证据和后续验收点见 [MI Big Bang 功能迁移计划](docs/MIBIGBANG_MIGRATION_PLAN.md)。拖拽输入、悬浮窗和传送门兼容细节见 [实现说明](docs/IMPLEMENTATION.md)。图片识字流程见 [OCR 说明](docs/IMAGE_OCR.md)，应用内搜索见 [搜索面板说明](docs/SEARCH_OVERLAY.md)，目标版本的静态适配记录见 [HyperOS 4 适配记录](docs/HYPEROS4_ADAPTATION.md)。

本分支保留原项目 GPL-3.0-only 许可。参考第三方应用时仅做独立行为分析；引入第三方代码或组件时，按其许可单独确认并注明来源。

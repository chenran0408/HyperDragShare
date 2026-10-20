# HyperDragShare · HyperOS 4 适配分支

这是基于 HyperDragShare 代码继续开发的独立 fork，目标是在小米 HyperOS 4 上增强文字和图片拖拽分享。项目按本分支代码和适配目标维护；MI Big Bang APK 与 BigBang/NovaText 项目只作为行为和技术分析参考，不作为本项目的需求清单。

当前适配目标是 Xiaomi 15 Pro、HyperOS 4.0.0.17、Android 17。设备已安装 1.7.69（versionCode 93），传送门注入与 Root 握手均报告 93。135 项单测、Lint 与构建通过；各功能的真实操作结果和未验收项分开记录。

后续源码 1.7.70（versionCode 94）补充了中转站重复导入控制与图片大小限制，通过 136 项单测、Lint 与构建，尚未安装到设备。

1.7.71（versionCode 95）继续加入分词字符计数和取消选择入口，并区分直接关闭与返回清除选区；尚未真机验收。

## 当前开发状态

- 分享菜单提供简洁、流光、环形和现代四种样式，四种样式共用上、下、左、右方向。1.7.68 的 16 组截图与方向日志已核对正确；1.7.69 继续修正流光与现代的底板可读性、环形顶部/底部预览避让，这些后续修正仍待完成复测。
- 中转站已实现本地持久文字/图片、Miuix 列表、多选分享与确认删除、单条原生拖出和限制读取的图片 Provider。真机已通过文字/图片导入、强停后重开、Chooser 预览、纯文本批量分享预览及批量删除取消/确认；Taplus 实际拖入、拖出接收、多图/混合接收仍待验证。
- 图片、屏幕和所选区域 OCR 使用本地中文 ML Kit，识别结果可进入分词页；分词页可复制、搜索、拆分/合并与分享，也能从原图重新识字。小爱/AICR OCR 尚未接入。
- 应用内搜索支持多种搜索、词典和翻译入口，可修改查询并调整面板高度；搜索与 OCR/分词页面的操作外壳接入 Miuix 主题。
- 文本面板可选开启选词触感反馈和复制后自动退出，两项默认关闭。
- 分词“更多”内已加入存入中转站，实际保存操作待验收；文字分词目前仍由独立透明 Activity 呈现，尚未实现真正的宿主窗口内弹层。
- AICR 文本直取与背部轻敲 Hook 默认关闭。Hook 安装握手有设备日志，但动作效果未验收。拇指面积触发也未可用：设备事件流尚未上报有效接触尺寸，不能用长按时长代替面积。

## 使用要求

- Android 13 或更高版本；本分支主要面向 Android 17。
- 已安装 LSPosed 并启用模块，作用域选择 `com.miui.contentextension`；启用 AICR 文本直取时再加入 `com.xiaomi.aicr`，启用背部轻敲时再加入 `android`。
- Root 权限用于读取触摸设备输入，使预览在同一拖拽手势中跟随手指。
- 当前安装版本、逐项真机证据和待验收清单见 [设备验证记录](docs/DEVICE_VALIDATION.md)。中转站实现和功能边界见 [中转站说明](docs/STASH.md)。

## 构建

构建与本地测试使用 JDK 21，Java/Kotlin 输出目标为 17。Windows 下运行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 开发说明

迁移阶段、静态证据和后续验收点见 [MI Big Bang 功能迁移计划](docs/MIBIGBANG_MIGRATION_PLAN.md)。拖拽输入、悬浮窗和传送门兼容细节见 [实现说明](docs/IMPLEMENTATION.md)。图片识字流程见 [OCR 说明](docs/IMAGE_OCR.md)，应用内搜索见 [搜索面板说明](docs/SEARCH_OVERLAY.md)，目标版本的静态适配记录见 [HyperOS 4 适配记录](docs/HYPEROS4_ADAPTATION.md)。

本分支保留原项目 GPL-3.0-only 许可。参考第三方应用时仅做独立行为分析；引入第三方代码或组件时，按其许可单独确认并注明来源。

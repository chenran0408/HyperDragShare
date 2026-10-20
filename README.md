# HyperDragShare

HyperDragShare 是一个 Android LSPosed 模块，为 HyperOS 传送门的文字和图片长按提供同一手势内的跟手预览与分享菜单。

## 功能

- 使用 Root evdev 输入，在传送门识别长按后继续跟随当前手指。
- 支持文字分享、图片分享、保存图片到本地和文本分词。
- 图片菜单新增“识别图片文字”：在模块进程内识别后进入已有分词页；也可从相册的系统分享菜单发送图片进行识别。
- 提供简洁、流光和环形三种可配置的分享菜单，以及深浅色外观、目标排序和隐藏设置。
- 可选无障碍内容获取模式；该模式仍需要 Root 输入，且不会扩大 LSPosed 作用域。
- 支持可关闭的系统日志或 root 保护的诊断文件导出；调试模式会记录输入节点与运行环境信息。

## 要求

- Android 13 或更高版本。
- 已安装并启用 LSPosed；模块作用域仅选择 `com.miui.contentextension`。
- Root 权限用于读取 Linux evdev，从而可靠地跟随同一次拖拽。
- 已验证传送门版本：`4.2.1`。

## 安装

1. 从 [Releases](https://github.com/Leaf-lsgtky/HyperDragShare/releases) 下载 APK 并安装。
2. 在 LSPosed 中启用 HyperDragShare，作用域只勾选传送门。
3. 重新启动传送门作用域进程后，打开 HyperDragShare 完成设置。

不要将 `com.miui.contentcatcher` 加入 LSPosed 作用域，也不要强行停止它。

## 构建

项目使用 Java/Kotlin 17。Windows 下执行：

本分支使用 AGP 9.1.1 / Gradle 9.3.1、Android SDK 37.0、Build Tools 36.0.0，以及既有 NDK 26.1.10909125 / CMake 3.22.1。中文 OCR 模型随 APK 携带，不需要首次使用时下载模型。

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 发布

向 GitHub 推送形如 `v1.7.12` 的 tag 后，GitHub Actions 会执行测试、Lint，并通过 R8 构建已签名的 Release APK，随后自动创建对应的 GitHub Release 与 APK 附件。

## 许可证

HyperDragShare 以 [GNU General Public License v3.0](LICENSE)（`GPL-3.0-only`）发布。第三方组件的许可详见应用内“开放源代码许可”页面和 `app/src/main/cpp/NOTICE`。

## 说明

实现边界、输入源仲裁和兼容性约束记录在 [docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md)。

本分支 1.7.52 增加了可切换搜索引擎的应用内搜索面板，细节与验证边界见 [docs/SEARCH_OVERLAY.md](docs/SEARCH_OVERLAY.md)；图片 OCR 见 [docs/IMAGE_OCR.md](docs/IMAGE_OCR.md)。传送门 5.1.5 的适配核对见 [docs/HYPEROS4_ADAPTATION.md](docs/HYPEROS4_ADAPTATION.md)；尚未通过本分支实机验收。

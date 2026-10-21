# HyperDragShare · HyperOS 4 增强分支

基于 [Leaf-lsgtky/HyperDragShare](https://github.com/Leaf-lsgtky/HyperDragShare) 独立开发的开源 fork，增强传送门、原页分词、图片识字与临时中转站。日常操作在当前应用上方完成，只有调整设置才进入模块。

本分支正在开发，主要适配 Xiaomi 15 Pro / HyperOS 4.0.0.17 / Android 17；其他设备尚未验收。当前版本 **1.7.147（171）**。

## 可以做什么

- 四种拖拽外观，共用四边呼出：拖到哪一边，菜单就出现在哪一边。
- 原页分词：点选和划选词块，直接复制、搜索、分享；按住内容拖出选区。编辑、合并/拆分等放在已有“更多”中。
- 图片、整屏与选区识字，可选择 ML Kit 或系统 AICR V2；结果继续在原页分词。
- 临时中转站：灰色侧条，展开最多占屏幕六分之一；移动后就近贴边。内容直接拖，多选后按住已选内容拖出整个选区；收起和返回保留选区与预览。
- 按住取词与识字：Root evdev 根据**时长和位移**判定，不依赖接触面积、无需校准；默认300ms，可在设置调整。移动、多指、锁屏和黑名单会取消候选。
- 可选的复制后分词、背部轻敲入口，以及应用热重载、Android软重启和整机重启。
- 页面使用 Miuix 主题，支持系统深浅色。

## 当前验证范围

1.7.149已通过212项本地单测、Lint与调试构建。按住入口不再要求面积数据或校准；提前避让明确的录音长按控件，并取消四秒冷却。149修复页面节点暂未就绪时取词进程退出、后续请求持续失效的问题。**真实手指触发与原应用长按冲突仍待验收**，不能据源码通过宣称手感已通过。

已有对应设备证据：原页选词和直接拖出后恢复选区、中转站两项文字拖到另一应用并保持顺序、收起/返回后重开保留选区，以及部分图片/OCR流程。图片与混合分组的新交互、全部四样式四边落点、横屏、完整深色和复杂应用仍有待验项。

小爱识屏能力查询不是OCR接口。本项目接入的是经目标ROM验证的AICR V2；小爱自身的私有识屏调用链尚未接入。不修改系统权限来掩盖限制。

逐项证据和未完成项见 [设备记录](docs/DEVICE_VALIDATION.md) 与 [功能清单](docs/MIBIGBANG_MIGRATION_PLAN.md)。

## 安装与设置

需要Root和LSPosed。传送门作用域为 `com.miui.contentextension`；使用AICR文本直取时加入 `com.xiaomi.aicr`，使用背部轻敲时加入 `android`。不要加入 `com.miui.contentcatcher`。

允许模块悬浮窗权限，按所选来源给对应进程Root权限。传送门模式通过Root访问无障碍节点，不自动开启手机里的无障碍服务；主动选择无障碍来源时才需要手动启用本模块服务。

这是开发预览分支。APK见本仓库 [Releases](https://github.com/chenran0408/HyperDragShare/releases)，带开发预览标记的版本未完成全部验收。图片/中转站存储格式有历史升级边界，降级前请看 [中转站说明](docs/STASH.md)，不要通过清数据回退。

## 构建

使用JDK21和Android SDK37，Java/Kotlin输出目标17。Android Studio打开项目，或运行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。本地签名与SDK路径放在Git忽略的 `signing/` 和 `local.properties`，不要提交密钥或口令。

推送开发分支、提交PR或手动运行时，[Android checks](https://github.com/chenran0408/HyperDragShare/actions/workflows/android.yml)会执行单测、Lint和调试构建。成功运行的Artifacts中可下载APK与检查报告，保留14天；实际结果以对应运行记录为准。

CI使用临时调试签名，不能直接覆盖本地同签名开发版。已安装用户优先从Releases获取验收包；自动构建不上传本地密钥，也不自动发布稳定版。

## 实现与许可

- [实现说明](docs/IMPLEMENTATION.md)、[按压机制与历史调查](docs/CONTACT_AREA_TRIGGER.md)、[图片识字](docs/IMAGE_OCR.md)、[中转站](docs/STASH.md)。
- 保留HyperDragShare原作者及贡献者的版权与Git历史，沿用 [GPL-3.0-only](LICENSE)。本分支修改同样按该许可公开。
- MI BigBang APK与演示视频仅作行为研究；不在仓库分发这些APK，不直接复制反编译实现，也不将本项目作为其复刻仓库。
- BigBang/NovaText及其他组件的实际引用说明和许可随对应源码保留。

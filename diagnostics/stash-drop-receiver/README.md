# 中转站跨 UID 拖拽诊断

这是独立原生 Android 小程序，包名 `com.leaf.dragdrop.probe`，不接入主工程构建。仅用于验证本 fork 中转站的 Android 全局拖拽、混合多项数据和图片临时授权，不联网、不截图、不修改系统权限。

## 构建与安装

在仓库根目录执行。先配置 `JAVA_HOME` 指向 JDK 21、`ANDROID_HOME` 指向安装了 Android 37 平台和 Build Tools 36.0.0 的 SDK。也可在此诊断目录新建被 Git 忽略的 `local.properties` 配置 `sdk.dir`。本项目仅使用 Java；没有 Kotlin 插件和额外运行时依赖。

```powershell
.\gradlew.bat -p diagnostics/stash-drop-receiver assembleDebug
adb install -r diagnostics/stash-drop-receiver/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.leaf.dragdrop.probe/.MainActivity
```

## 操作

1. 三个按钮分别通过模块原有 `ACTION_SEND` 入口存入生成文字 `STASH-115-A`、`STASH-115-B` 和 640×240 PNG，不替换已有条目。每次导入会打开模块侧窗。
2. 从中转站拖出到大蓝色接收区并松手。可分别验证文字、图片，以及中转站多选后的混合三项拖出。
3. 顶部显示 `DROP OK uid=… items=…`，文字项显示测试文字，图片项显示尺寸、字节数和 PNG 文件头检查。失败显示实际异常；日志标签为 `StashDropProbe`。

图片接收调用 `Activity.requestDragAndDropPermissions()`，随后由接收器自身 UID 打开 URI、读取字节并解码，完成后释放授权。生成图片 Provider 不导出，只提供临时读取授权。本程序不保存接收的数据、不删除中转站内容；测试结束只删除自己生成的条目。

## 已知证据与边界

2026-10-21，小米 15 Pro / HyperOS 4.0.0.17 / Android 17：接收 UID 10228；文字 A、640×240 PNG（8167 字节）和混合三项均已在真机完成接收。具体模块批次及窗口行为以仓库 `docs/DEVICE_VALIDATION.md` 为准。

成功只证明此接收器与当前 ROM 的全局拖拽链路，不代表所有第三方应用都实现拖拽接收，或支持图片、多项与混合数据。它也不是剪贴板、分享、真实手指接触面积或传送门拾取功能的替代验收。

测试结束卸载：

```powershell
adb uninstall com.leaf.dragdrop.probe
```

## 生成图片识字样本

“显示识字样本”在同一Activity显示900×500五行中英Canvas图片，ImageView描述为“图片”，上方普通TextView用作对照。用于主工程debug OverlayInteractionProbe的mode=root_image；先读取实际ImageView边界，再明确传入x/y，不默认采集屏幕。

1.7.117目标机实验取到1392×773节点截图，实际AICR原页分词83字符；“·”误识“-”。accepted只表示OCR请求提交，不能替代检查最终窗口。此入口不模拟触摸面积，也不证明面积校准、Portal UID或原手势接管。

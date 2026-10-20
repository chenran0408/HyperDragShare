# 图片二维码

本 fork 独立实现图片扫码；参考 APK 只作为功能目标，没有复制其反编译代码。

## 入口与交互

- 图片识字窗口提供“识别二维码”，包括正在识字、无文字和失败状态。纯二维码图片不依赖OCR先识出文字。
- 带来源图片的分词窗口，“更多”提供“识别原图二维码”；普通文字窗口不展示无效入口。
- 复用ImageOcrOverlay解码、Miuix主题、错误/进度/取消和独立窗口。扫码在工作线程运行，图片读取结束后释放Bitmap，关闭后不显示迟到结果。
- 结果显示去重后的码内容，支持选中、复制；普通文字可搜索，HTTPS链接可主动在原页搜索窗打开。非HTTPS专用scheme只保留完整内容及复制，不自动唤起其他应用。扫码本身不联网。
- 复制中禁用重复提交，确认写入后按钮显示“已复制”；失败在当前卡片显示原因。目标机按用户设置屏蔽Toast，不能依靠Toast作为唯一反馈。
- 从分词进入时保留原窗口与选词，关闭扫码恢复；来源窗口移除时扫码也关闭。搜索在扫码窗上方，关闭后仍可看码内容。明确选择搜索窗“外部浏览器”才启动外部Activity。

## 实现

`QrImageDecoder`使用[ZXing core 3.5.4](https://github.com/zxing/zxing/releases/tag/zxing-3.5.4)的QRCodeMultiReader、RGBLuminanceSource及HybridBinarizer，仅解码QR Code。NotFoundException为无二维码结果，不用OCR文字猜测码内容，也不堆叠反色/旋转/其他引擎回退。源码依赖声明在app/build.gradle，原上游LICENSE与NOTICE随APK assets/licenses分发。

图片沿用现有最长边4096的解码限制。没有接相机、屏幕区域扫码、新Provider接口或新增Hook作用域；普通屏幕OCR结果没有来源图时不能从更多菜单重扫码。多码、旋转、低光和受损照片的实机适用范围以DEVICE_VALIDATION.md为准。

目标机WRITE_CLIPBOARD的UID模式为foreground，独立浮窗的普通写入实际被静默拒绝。ClipboardWriter读取AppOps原始模式，在真实限制下使用已验证的Root系统UID调用，并严格核对写入结果；正常允许时保留普通API。不修改权限，不把无异常当作成功，不依赖切换到模块Activity。必须用unsafeCheckOpRawNoThrow，普通检查会把FOREGROUND翻译为ALLOWED，见[Android AppOpsManager源码](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/core/java/android/app/AppOpsManager.java)。

RootClipboardMain只在明确复制时启动，原文经stdin传递，不放进命令参数或日志。Root启动UID1000的app_process，用系统上下文写入及读回核对；成功只返回copied，失败记录原因并在卡片显示。整个请求限时5秒，工作线程执行。没有新增常驻服务、通知、作用域或系统设置改动。本批只接入扫码复制，其它旧文字/图片复制点仍需按相同AppOps限制审查，不能据此写为全局已修复。

## 验证

Robolectric生成图片覆盖中文码与打印文字混排、多码去重、空白图无结果。设备验证须同时检查码内容、原页窗口、复制、链接返回和关闭；不把仅解码单测通过写成全部交互通过。

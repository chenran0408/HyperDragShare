# HyperOS 4 / Android 17 适配记录

目标设备：小米 15 Pro（haotian），`OS4.0.0.17.XOBCNXM`，Android 17 / SDK 37。
设备传送门包 `com.miui.contentextension` 为 `5.1.5`（`50015`）。以下是从该设备拉取 APK 后进行的静态核对；没有把静态反编译当作实机运行结果。

## 宿主接口核对

所核对 APK 的 SHA-256 为 `e7810ef31917cd64c80bdd338d4434e9cb6c49f4c823ca9b8f7be6688f0b9c5b`。`TextContentExtensionService` 仍声明 `onCreate`、`onDestroy`、`onStartCommand(Intent,int,int)`、`startPickTextTask`、`startPickImageTask`、`cancelTask`、`getBitmap`、`getFirstTouchPoint`、`getInjectorPoint`。静态字段 `sIsTextMode`、`sContent` 和回调 `mCallback` 仍存在。`BaseFloatView.addToWindow` 仍是原浮窗添加入口。

在 5.1.5 的反编译代码中，回调 `onContentReceived(ClientCatcherResult)` 从 `getPropertyMap()` 取 `observe_control_event`，以 `257` 进入返回/结束路径，以 `258` 进入移动取消路径。`onPickText` 在发起 `startPickTextTask` 前设置 `sContent`；`onPickImage` 在发起 `startPickImageTask` 前设置 `sBitmap`。`cancelTask` 会清空内容并回收 `sBitmap`。这支持沿用当前 Hook 的方法入口，同时要求模块异步暂存图片前保留独立位图。

## 本次修正

- 从传送门取得图片时复制为模块独立位图，使宿主 `cancelTask` 回收 `sBitmap` 后，后台 PNG 暂存仍能读取图片。
- 切换到无障碍模式、传送门模块控制器停止后，不再由仍安装的回调 Hook 拦截宿主消息。
- 只在模块拖拽会话活动时抑制宿主的 `258` 移动取消消息；没有会话时让宿主自行处理并复位任务状态。
- 移除把已安装 Hook 固定标记为传送门 4.2.1 的日志。
- 无障碍长按没有可用文字或图片节点时，在通过安全检查后截取触点附近的有限区域，交给现有图片菜单和识字入口。截图不会跨出来源窗口；区域包含密码节点或节点遍历不完整时放弃后备。选择到候选节点时仍沿用原有取词或图片分享路径。
- 简洁和现代菜单进入边缘热区后立即展开，不再依赖额外的方向位移条件；131已移除现代窗口失败时静默换窗，改为明确取消，见DEVICE_VALIDATION。

## MI Big Bang 系统进程 Hook 静态观察

对用户提供的 `mibigbang.apk` 做了 IDA 静态分析。其 `MainHook.handleLoadPackage` 在包名为 `android` 时，定位 `com.miui.server.input.MiuiBackTapGestureService`，对 `postShortcutFunction`、构造方法及 `updateBackTapFeatureState` 安装 Hook，并启动名为 `dragshare-backtap-keepalive` 的后台线程。日志字符串将该路径标记为 back-tap interception。

这条系统进程路径处理背部轻敲，不属于传送门长按分享主链路。本分支现已新增只针对该服务的双击/三击回调 Hook，并在 Miuix 设置中提供关闭、双击、三击和两者四种模式；默认关闭。实现没有移植 APK 的常驻传感器重注册线程。Hook 还需要用户把 `android` 加入 LSPosed 作用域并授予 Root，以写入自定义 Global 设置。目标 ROM 的方法签名、原快捷动作回退、锁屏表现和启动权限尚未实机验证；这里的分析仍是 APK 字节静态观察，不能替代运行验收。

同一批还加入了 `com.xiaomi.aicr` 文本直取 Hook（默认关闭）、快捷设置屏幕 OCR 磁贴和本地 ML Kit 结果页。LSPosed 作用域仅包含 `com.miui.contentextension`、`com.xiaomi.aicr` 和 `android`；图片暂存等通用 Provider RPC 仍只允许模块自身或传送门，135起AICR可读取开关并通过独立受限RPC请求纯文字原页分词，不获图片/通用Portal RPC权限。

## 验证边界

源码编译、单元测试和 Lint 可验证本分支的构建与纯逻辑。AICR 调用参数、框架回调、屏幕 OCR 捕获权限、是否成功注入传送门 5.1.5、实际拖拽跟手、四种样式、`257/258` 设备时序和 OCR 准确率仍需该机验收。首次设备验收应逐项检查模块状态、Root 输入、传送门文字/图片、AICR 复制、屏幕 OCR 磁贴、背部轻敲关闭与开启模式，以及未接管时系统原动作是否保留。


## 1.7.136 复制卡片接入

真机复制卡片归属Portal的TaplusSplashFloatView，非AICR窗口。索引定位并定点读取当前APK后，确认TaplusSplashFloatView$13.onClick原本把getClipboardContent传到clipboard.EditActivity并收卡。136在“复制文字分词”开启且文字通过校验时改为原页分词，接受后沿用宿主收卡。目标进程com.miui.contentextension，不加作用域。匿名类编号属于5.1.5私有结构，升级需重新核对；不猜测兼容版本。当前Via真实复制与关闭开关保留原编辑均通过。ClipboardReceiver明确只将com.android.settings列入CLIPBOARD_BLOCKED_PACKAGES，实测也因此跳过，未改名单。

AICR4.12.26的自然processText来自内部剪贴板监听，能力1210（CRS_INTELLIGENT_CLIPBOARD_REDIRECT）由eb1.z启动/eb1.A停止，国际构建/Companion Intelligence分支不启动。该静态链与Portal复制卡片分开记录，未强制注册能力或绕过受权限保护的OpenCapabilityProvider。相关历史章节的“待验”只代表当时状态，最新范围以DEVICE_VALIDATION为准。

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

## 验证边界

源码编译、单元测试和 Lint 可验证本分支的构建与纯逻辑。是否成功注入传送门 5.1.5、能否在实际长按时持续跟手、`257/258` 的设备时序、应用内页面覆盖率以及 OCR 准确率仍需在该机上操作验证。首次实机试验应先记录模块激活、Root 输入就绪、传送门回调与 `cancelTask` 日志，再分别检查文字、图片和切换无障碍模式后的原生传送门行为。

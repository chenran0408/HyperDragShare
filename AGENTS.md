# HyperDragShare Agent Guide

本文是后续自动化代理在本工程内工作的约束。当前源码和
`docs/IMPLEMENTATION.md` 是实现事实来源；`E:\workspace\TaplusContentPreview`
只可作为早期传送门内容抓取思路的参考，不代表可运行基线。

如果文档内部分内容与实际工程不符，需实时修订文档。

## 工程基线

- 工程类型：Android LSPosed 模块，Java/Kotlin 17，minSdk 33，targetSdk 34，compileSdk 37。
- 构建及本地测试运行时使用 JDK 21；Java/Kotlin 输出目标仍为 17。当前 Miuix 0.9.3 的宿主 JVM 类版本为 65，直接调用主题色的单测无法用 JDK 17 执行。
- 当前分支版本见 `app/build.gradle`；使用 AGP 9.1.1 / Gradle 9.3.1。
- 已验证宿主：传送门 `5.1.5`，包名 `com.miui.contentextension`。
- LSPosed API：82，入口为 `com.leaf.hyperdragshare.codex.MainHook`。
- 可靠的同手势跟手依赖 root 读取 Linux evdev；MIUI 输入监听仅作回退。
- 当前目标为 Xiaomi 15 Pro / HyperOS 4.0.0.17 / Android 17，设备和测试状态见
  `docs/DEVICE_VALIDATION.md`。历史设备的 1220×2656 参数不适用于当前设备；代码必须动态探测。

## 不可破坏的约束

1. LSPosed 作用域只包含 `com.miui.contentextension`、`com.xiaomi.aicr` 和 `android`，
   分别用于传送门拖拽、AICR 文本直取和可选背部轻敲 Hook。不要加入
   `com.miui.contentcatcher`（Application Extension Service），也不要在部署脚本中强停它；
   已知部分系统上强停它会连带结束其他应用。
2. root 输入源一旦 `isReady()`，它就是一次拖拽的唯一权威输入源。不得把 MIUI
   `MotionEvent`、传送门控制码和 root 事件混在一起决定结束时机。
3. root 拖拽期间，真实的 `ABS_MT_TRACKING_ID=-1` / `BTN_TOUCH=0` 才对应最终
   `ACTION_UP`。传送门的 `cancelTask`、控制码 `257/258` 以及重复的
   `startPick*Task` 不能提前结束活动拖拽。`cancelTask` 和 `257` 不能永久吞掉：应暂存
   原调用并在真实 UP 时回放，以复位传送门的 `sIsTaskFinished` 等内部状态；`258` 才是
   应持续抑制的移动取消信号。
4. 预览窗和菜单必须保持 `FLAG_NOT_TOUCHABLE`。它们通过
   `WindowManager.updateViewLayout()` 被动跟随坐标，不接管当前手势。原页面仍可能滚动是
   当前旁路观察方案的预期行为。
5. 不要把图片 URI 描述成“只靠临时 URI grant 才能读取”。当前 Provider 会给目标包显式
   grant，但 `openFile()` 故意不再额外调用 `checkUriPermission()`，以兼容会丢失 grant
   信息的系统分享代理和应用内二次转发。读取能力同时依赖不可枚举的 UUID URI；
   `stage/grant/revoke` RPC 仍只允许模块自身或传送门 UID。
6. 图片分享 Intent 的 `data`、`EXTRA_STREAM`、`ClipData`、MIME type 和
   `FLAG_GRANT_READ_URI_PERMISSION` 是兼容性组合，不能只保留其中一项。
7. 不要随意修改传送门私有类名、方法名或控制码。它们绑定传送门 5.1.5，变更前需要反编译
   新版本并完成实机日志验证。
8. 设置通过模块 UID 的 SharedPreferences 保存，由 `ShareImageProvider.call(get_settings)`
   受限返回给传送门；不要改成 world-readable 文件。边缘触发值以 dp 保存并在运行时限制
   为不超过屏幕半宽，速度以 dp/s 保存。
9. “拖拽时阻止背景滑动”只在 root 输入源已经送入活动会话后尝试调用隐藏输入 API；不要在
   MIUI 回退通道上直接消费 `ACTION_CANCEL`，否则会让悬浮窗提前结束。所有取消/服务销毁路径
   都必须释放 `InputMonitor`（若 ROM 走 monitor 回退）。
10. 菜单统一跟随拖拽触达的屏幕边缘；四种外观共用四边选边，不再提供固定位置或倾斜选边。布局刷新必须保留本次选中的边。
11. “手指移开时关闭分享菜单”是四种拖拽样式的公共行为。简洁/现代/流光离开菜单与触发带时移除
    线性菜单，环形离开展开面板区域时折叠；同一手势重新进入触发区后都应允许再次展开。
12. 首页激活状态必须按当前来源解释：传送门模式按“无 Root → 当前版本未注入传送门 →
   传送门 Root 权限不可用 → 已激活”，
    无障碍模式按“无 Root → 服务未启用 → 服务/Root 输入连接中 → 已激活”。传送门 Hook 必须通过
    `ModuleActivation.reportInjected()` 上报其编译时 `BuildConfig.VERSION_CODE`；不要把普通的
    `get_settings` 调用当作注入证明，否则 APK 更新后仍运行旧代码的传送门进程会被误报为已激活。
13. 内容获取方式默认是传送门。无障碍模式只由 `DragShareAccessibilityService` 在模块进程中
    工作，仍依赖 Root evdev，不得因此新增 LSPosed 作用域或静默启用无障碍服务。Xposed API
    仅可用于 `MainHook`、`PortalHooks`、`PortalContentCaptureSource`、`MiuiMotionSource`、
    `AicrCopyHooks` 和 `BackTapFrameworkHooks`；AICR 与框架 Hook 必须保持包名、目标类和方法
    的精确限制，普通运行时不得导入 Xposed API。
14. 节点树只在一次明确请求后读取：无障碍长按超时、有效面积确认或用户主动点取。
    准备点取时只连接 Root，不提前遍历；不得在 `onAccessibilityEvent()` 中持续遍历、
    截图或记录文字。密码节点、锁屏、黑名单和无障碍覆盖层必须忽略，图片只截取所选节点区域。
    UiAutomation 只有一个注册位，Root 取词连接存活时不能同时运行 `uiautomator dump`；
    用截图观察，取词完成并确认释放后才 dump。占用失败要明确报告，不能断开别的会话或自动重试。
15. 设备操作和截图需要用户授权，包括 `adb shell am start`、`input`、GUI 自动化和
    `screencap`。用户已在当前开发对话明确授权持续测试与更新，并要求无需重复申请；
    本对话后续轮次沿用该授权，不再按轮询问。其他对话不能仅凭此记录推定已授权。
    若发现用户正在操作，应避免抢占并协调；不将测试截图中的私人内容提交到仓库。
    此约束不改变模块运行时为现代悬浮 View 使用原生局部背景模糊的实现。
    关闭测试浮窗前先确认它仍存在；宿主已自行收起时不再发送返回键，避免Back落到原App。
    136收尾曾因此使Bili休息提醒消失；当前视频仍暂停，不恢复播放，不修改其数据伪造提醒恢复。

## 代码地图

- `MainHook.java`：限制注入包名。
- `PortalHooks.java`：安装传送门 Hook、管理生命周期、输入源仲裁和宿主取消信号。
- `RootTouchSource.java`、`EvdevTouchParser.java`：发现触摸设备、解析 evdev 多点触控帧，稳定生成 DOWN/MOVE/UP/CANCEL。
- `MiuiMotionSource.java`：MIUI 系统监听回退。
- `CapturedContent.java`、`OverlayWindowPolicy.java`：来源无关的文字/图片模型和传送门/无障碍窗口类型。
- `DragShareController.java`：拖拽会话、悬浮预览、方向菜单、边缘滚动、近手传感器和落点选择；不依赖 Xposed。
- `PortalGlowView.java`：流光样式的全屏不可触摸光效、下拉进度和托盘展开绘制。
- `CircleMenuOverlayView.java`、`CircleMenuGeometry.java`：四边环形菜单，240dp 面板跨度、56dp 条目；预览需避让四边面板。
- `LinearMenuGeometry.java`：简洁、流光、现代共用紧凑边界，64×68dp 单元、最多 5 项可见；设置方向必须对四种样式都生效。
- `StashStore.java`、`StashClient.java`、`StashImageProvider.java`、`StashActivity.kt`：私有持久中转站、受限存入 RPC、按次授权读图和 Miuix 管理列表，进度见 `docs/STASH.md`。
  1.7.123起数据库为schema2，带同次图片导入批次；降到schema1旧APK须配套升级前数据备份，不能仅降APK或清数据。任务内备份位置和实际核验见STASH/DEVICE_VALIDATION，不提交用户内容。
- `StashBatchCard.kt`、`StashSearchInput.kt`：共享组内顺序/组卡及窄窗搜索；系统小窗搜索和多选操作位于固定标题区，避免焦点滚动或条目锚点把控件移到标题背后。
- `ModernOverlayViews.kt`、`ModernOverlayWindow.java`、`ModernPreviewSizer.java`：现代 Compose 内容、View outline、公开 Window 局部背景模糊和自适应正方形预览。
- `DragShareSettings.java`：设置默认值、范围校验、本地持久化和 Provider 配置 RPC。
- `BackgroundTouchBlocker.java`、`FrameworkBinderTransactionResolver.java`：可选地通过系统手势监视器取消原前台窗口的触摸流；直接 API 被拒绝或被隐藏 API 策略屏蔽时，从当前 ROM 的 framework DEX 动态解析输入 Binder 事务号并以 root 回退，失败时旁路观察。
- `SettingsScreen.kt`：Miuix 设置页（内容开关、目标可见性、批量操作、拖拽排序、外观和触摸参数）。
- `ModuleActivation.java`：Root 探测、当前版本传送门注入握手和首页激活状态来源。
- `DragAndDrop.kt`：参考 XiaomiHelper 的 LazyColumn 实时换位、边缘自动滚动和回弹状态。
- `PortalContentCaptureSource.java`：唯一读取传送门私有字段和初始触点的适配层。
- `DragShareAccessibilityService.java`、`AccessibilityContentCaptureSource.java`：无障碍生命周期、长按协调和来源隔离。
- `OcrFallbackRegion.java`：无障碍无候选节点时的有限区域截取几何与密码节点重叠检查。
- `LongPressGestureDetector.java`、`AccessibilityNodeClassifier.java`、`AccessibilityCandidateSelector.java`：可单测的长按、节点分类和命中优先级。
- `SharedRootNodeCapture.java`、`IRootNodeLease.aidl`、`IRootNodeCandidate.aidl`：模块进程唯一持有Root取词helper，Provider授权引导、每租约校验所属UID。手动租约暂停自动捕获，IO忙直接报告，不排队错过长按截止；候选同步回调不得等待主线程。最后租约/客户端死亡释放，不抢断其他UiAutomation。图片元数据跨Binder，截图裁剪留在调用方，仍核验窗口。
- `PointCaptureOverlay.kt`、`PointCaptureActivity.kt`、`PointCaptureTileService.java`：控制中心主动点取，透明桥结束后在原页选点，再复用 Root 语义取词/所选图片 OCR。与面积触发分开验收，不能把点取通过写成面积或普通长按修复。范围见 `docs/POINT_CAPTURE.md`。
- `AccessibilityScreenshotter.java`、`RootScreenshotter.java`、`ScreenshotRectMapper.java`：安全的一次性区域截图与 API 28/29 回退。
- `ShareTargetRepository.java`：查询可处理对应 MIME 的导出 Activity，并克隆/着色内置目标的旧矢量图标。
- `BitmapEncoder.java`、`ImageStagingClient.java`、`ShareImageProvider.java`：图片压缩、
  跨 UID 暂存、能力 URI 和授权。
- `SharedImageCleanupJob.java`：系统周期清理与导入时清理共用24小时期限，只遍历cache/shared-images及cache/ocr-input；不可清理files/stash、数据库或相册。目标ROM已观察到冷进程Job绑定被自启动管理器拒绝，不能仅凭schedule成功声称后台可用，也不能自动修改自启动权限。
- `ClipboardWriter.java`、`RootClipboardMain.java`：读取剪贴板AppOps原始模式，受限时文字用Root系统UID、图片用Root Shell UID写入并核对；原文/URI只经stdin，不能把无异常当复制成功，不能修改AppOps来掩盖限制。120已验证侧窗/系统小窗图片跨UID读取及删除源后副本读取。System/Root UID不能作为剪贴板URI授权来源；Shell使用目标ROM实测IClipboard签名与com.android.shell归属，固定主用户/本机剪贴板，其他用户/虚拟设备未支持，不添加猜测兼容。
- `LocalImageSaver.java`：把图片首项保存到系统 Pictures，按秒生成文件名。
- `ShareLauncher.java`：构造显式 `ACTION_SEND` 并启动目标 Activity。
- `GestureMath.java`、`ShareUriToken.java`：可单测的纯逻辑。

## 修改流程

修改前先读 `docs/DEVICE_VALIDATION.md`、`docs/MIBIGBANG_MIGRATION_PLAN.md`、
`docs/IMPLEMENTATION.md` 及相关源码，不要根据历史聊天或参考工程推断现状。
保持现有小类和直接调用风格，只有共享逻辑确实需要单测时才抽取新工具类。不要把设备专用
事件节点、分辨率或旋转写死。

每次行为变更至少运行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

测试数量以当前 Gradle 报告为准，APK 输出到
`app\build\outputs\apk\debug\app-debug.apk`。交付新的可安装行为时同步递增
`versionCode` 和 `versionName`；纯文档修改不要求增版。

本机发布签名使用 Git 忽略的 `signing/` 目录及同样被忽略的 `local.properties`：

```properties
signingStoreFile=signing/my-release-key.jks
signingInfoFile=signing/qianming.txt
```

签名信息文件按 `ALIAS`、`KEY_PASSWORD`、`KEYSTORE_PASSWORD` 的字段名与值交替保存。
不要把 keystore、签名信息、口令或绝对本机路径提交到仓库。配置存在时，`assembleRelease` 会构建
经 R8 压缩且已签名的 APK，输出为 `app\build\outputs\apk\release\app-release.apk`。
云端 Release 使用 `SIGNING_KEYSTORE_PATH`、`SIGNING_KEYSTORE_PASSWORD`、`SIGNING_KEY_ALIAS` 和
`SIGNING_KEY_PASSWORD` 环境变量配置同一 signingConfig；工作流只发布非 `-unsigned` 的 APK。

实机排查优先看以下日志：

```powershell
adb logcat -c
adb logcat -v time | Select-String "DragShare|AndroidRuntime"
```

正常 root 链路应出现 `DragShare/RootInput: ready`、
`root input is authoritative`、`input source=root`，并最终由 root `ACTION_UP` 输出
`gesture finished`。图片链路应看到 `DragShareProvider` 的 `staged`、`granted` 和
目标 UID 的 `open`。

`probe/` 当前不是 `settings.gradle` 的模块，只可能残留历史验证构建缓存；除非用户明确要求，
不要重新接入或围绕它改造正式实现。

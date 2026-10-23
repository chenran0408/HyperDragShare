# HyperDragShare Agent Guide

当前源码版本与运行证据先核对 `app/build.gradle`、`docs/CURRENT_STATUS.md` 和 `docs/DEVICE_VALIDATION.md` 的最新条目。189 基线后的修复位于 `repair-from-189` 分支；`docs/189_BASELINE_PORT_NOTES.md` 是历史参考，不能代替源码验证。旧文档的双引擎/仅LauncherUI限制不是当前产品方向。Bitmap OCR已验证普通应用UID，不需要Root/LSPosed；截图/取词/Hook权限独立。

本产品面向Root/LSPosed用户，保持简短说明、真实运行状态；不增加面向无Root使用的替代入口或多层引导。保留已有有效功能和必要权限失败反馈。普通模块页收起不满足前置开关的从属参数，完整目录仍参与搜索，搜到后可定位并解释前置条件。

最新版本、功能差距和验证层级先读 `docs/CURRENT_STATUS.md`，再读各文档末条。用户最新明确停止真实触摸面积研究；产品沿用Root evdev时长/位移，不强制校准、不把按住称为面积触发。历史只读实验保留记录，不继续解码、实验或消耗时间。优先修复取词、拖放与Miuix交互。

当前按批次任务目录为 `docs/TASK_BATCHES.md`；`NEXT_INTEGRATION_BATCH.md`是历史记录，不能把其中已完成的待办再做一次。子代理Luna high并行处理独立范围，由主任务统一构建、安装和验收。

214将复制提醒替换为系统原生通知，不再恢复213的自绘气泡；HyperOS的copy_direct频道悬浮开关与POST_NOTIFICATIONS是两个独立条件，启动/重载仅配置本模块此频道。分词两排工具包含加入中转站，More仅窗口外观；旧文档“移除分词中转站入口”已被用户本轮需求覆盖。微信OCR完成后保留同次图像候选到真实UP，后续MOVE仍可接管，勿恢复立即回收。小米翻译静态限制见docs/XIAOMI_TRANSLATION.md，不等于已接通；不绕过CTA，不把Root UID0当system UID1000。

本文是后续自动化代理在本工程内工作的约束。当前源码和
`docs/IMPLEMENTATION.md` 是实现事实来源；`E:\workspace\TaplusContentPreview`
只可作为早期传送门内容抓取思路的参考，不代表可运行基线。

如果文档内部分内容与实际工程不符，需实时修订文档。

## 当前合并任务

用户要求完整合并自己的FanFreeform，并迁移已安装Hyper手势数据。来源应使用用户提供的157性能版源码档案，而不是GitHub main131；准确来源、校验和与进度见docs/FANFREEFORM_INTEGRATION.md。核心已经导入com.leaf.hyperdragshare.gestures，工作树已挂SystemUI/桌面运行入口；用户明确授权后续整合SystemUI与com.miui.home所需Hook，原有四作用域约束不应被误读为禁止本次完整功能合并。162已安装，设备五scope已核验；旧手势模块已禁用而保留数据。SystemUI186运行，桌面Rust不执行本Java入口，目录已转SystemUI；详见最新记录。迁移前保留完整备份，原包保留；配置与截图不得提交。不能在旧模块与合并模块同时启用手势接管的情况下验收。

## 工程基线

- 工程类型：Android LSPosed 模块，Java/Kotlin 17，minSdk 33，targetSdk 34，compileSdk 37。
- 构建及本地测试运行时使用 JDK 21；Java/Kotlin 输出目标仍为 17。当前 Miuix 0.9.3 的宿主 JVM 类版本为 65，直接调用主题色的单测无法用 JDK 17 执行。
- 当前分支版本见 `app/build.gradle`；使用 AGP 9.1.1 / Gradle 9.3.1。
- 已验证宿主：传送门 `5.1.5`，包名 `com.miui.contentextension`。
- LSPosed API：82，入口为 `com.leaf.hyperdragshare.codex.MainHook`。
- 可靠的同手势跟手依赖 Root 读取 Linux evdev；210 已移除与其重复的 MIUI 输入监听，Portal 私有回调只负责原宿主流程协调。
- 当前目标为 Xiaomi 15 Pro / HyperOS 4.0.0.17 / Android 17，设备和测试状态见
  `docs/DEVICE_VALIDATION.md`。历史设备的 1220×2656 参数不适用于当前设备；代码必须动态探测。

## 不可破坏的约束

1. LSPosed 作用域包含 `com.miui.contentextension`、`com.xiaomi.aicr`、`android` 、`com.android.systemui`、`com.miui.home` 和原生侧边栏整合所需 `com.miui.securitycenter`（仅 `:ui` 进程）。
   分别用于传送门拖拽、AICR 文本直取、可选背部轻敲与自有 Root 子进程数量裁剪、合并手势运行时、桌面快捷方式和用户交接的原生侧边栏；安全中心入口精确限定已核对40001365/SDK37，不能扩展到其他服务进程。已停止面积研究并从入口移除旧SystemUI面积诊断。手势运行时继承157的方向判定与接管策略；不得与旧Hyper手势模块同时接管。不要加入
   `com.miui.contentcatcher`（Application Extension Service），也不要在部署脚本中强停它；
   已知部分系统上强停它会连带结束其他应用。
2. root 输入源一旦 `isReady()`，它就是一次拖拽的唯一权威输入源。不得把 MIUI
   `MotionEvent`、传送门控制码和 root 事件混在一起决定结束时机。
3. root 拖拽期间，真实的 `ABS_MT_TRACKING_ID=-1` / `BTN_TOUCH=0` 才对应最终
   `ACTION_UP`。传送门的 `cancelTask`、控制码 `257/258` 以及重复的
   `startPick*Task` 不能提前结束活动拖拽。`cancelTask` 和 `257` 不能永久吞掉：应暂存
   原调用并在真实 UP（或 Root 多指 CANCEL 终止）时回放，以复位传送门的 `sIsTaskFinished` 等内部状态；`258` 才是
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
10. 菜单位置由用户在顶部、底部、左侧、右侧、靠近手和跟随拖拽边中选择；默认跟随拖拽边。四种外观共用位置策略；固定位置保持所选边，靠近手按压起点所在屏幕侧，跟随模式按拖拽边切换。布局刷新必须保留本次选中的边。
11. “手指移开时关闭分享菜单”是四种拖拽样式的公共行为。简洁/现代/流光离开菜单与触发带时移除
    线性菜单，环形离开展开面板区域时折叠；同一手势重新进入触发区后都应允许再次展开。
12. 首页激活状态固定按 Root＋传送门解释：“无 Root → 当前版本未注入传送门 →
   传送门前台服务未就绪 → 传送门 Root 通道不可用 → 实际Root输入未就绪/失败 → 已启用按压但取词未就绪/失败 → 已激活”。
   212起设置启动和重载必须启动传送门原有TextContentExtensionService前台服务，不能只查询Provider：已观察到Provider-only进程adj935被umms_pressure回收。服务状态随既有注入RPC上报，不改系统内存策略，不增加定时复活。
   213经用户授权增加Root ProcessManager锁定名单，只锁模块和Portal，保护其免原生一键清理；先读后补、写后确认，失败单独提示，不把前台服务就绪等同保护成功。此状态在系统中持久保存，恢复命令见ROOT_PROCESS_LIFECYCLE；不拦截强行停止、不全局关闭内存回收。气泡走已有AICR复制入口，原页分词走既有窗口，不增加全局剪贴板Hook或测试Activity。
   传送门 Hook 必须通过
    `ModuleActivation.reportInjected()` 上报其编译时 `BuildConfig.VERSION_CODE`；不要把普通的
    `get_settings` 调用当作注入证明，否则 APK 更新后仍运行旧代码的传送门进程会被误报为已激活。
13. 产品只支持 Root＋LSPosed，不提供系统无障碍服务或无 Root 来源。长按取词固定使用传送门
    Hook、Root evdev、Root helper 与 AICR；不得重新声明 `BIND_ACCESSIBILITY_SERVICE`，也不得在
    设置中恢复“传送门／无障碍”选择。旧 `content_capture_mode` 值不得改变运行来源。Xposed API
    仅可用于 `MainHook`、`PortalHooks`、`PortalContentCaptureSource`、
    `AicrCopyHooks`、`BackTapFrameworkHooks`、`RootProcessFrameworkHooks`、`NativeSidebarHooks` 和合并后的 `gestures.xposed` 包；AICR 与框架 Hook 必须保持包名、目标类和方法
    的精确限制，普通运行时不得导入 Xposed API。
14. 节点树只在一次明确请求后读取：用户已启用按压入口且单指静止按住120ms后的分类、按住时长确认或用户主动点取。
    按压分类按MI行为仅缓存本次内容，到确认时才接管并打开窗口；松手、移位或多指取消，不在空闲时读取。
    准备点取时只连接 Root，不提前遍历；不得持续遍历、截图或记录文字。密码节点、锁屏、黑名单和
    Root helper 已报告的安全遮挡窗口必须忽略，图片默认只截取所选节点区域。
    微信不暴露节点时使用按压局部OCR：179按实际WMS可见句柄与窗口所属包覆盖普通页面，不再限制LauncherUI；仍以WMS前台窗口身份与可见范围核验，排除安全窗口、遮挡、锁屏和黑名单，截图后和展示前复核来源，不把它描述成节点取词或完整小爱识屏。
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
- `TextSegmenter.java`：系统ICU多语言分词与字素边界，不再使用jieba/JNI/词典或预载设置。
- `AicrBarcodeClient.java`：系统AICR条码异步插件，申请版本为空字符串；复用原图片结果浮窗，不打包ZXing/MLKit。
- `CapturedContent.java`、`OverlayWindowPolicy.java`：文字/图片模型和传送门悬浮窗口类型。
- `DragShareController.java`：拖拽会话、悬浮预览、方向菜单、边缘滚动、近手传感器和落点选择；不依赖 Xposed。
- `PortalGlowView.java`：流光样式的全屏不可触摸光效、下拉进度和托盘展开绘制。
- `CircleMenuOverlayView.java`、`CircleMenuGeometry.java`：四边环形菜单，240dp 面板跨度、56dp 条目；预览需避让四边面板。
- `LinearMenuGeometry.java`：简洁、流光、现代共用紧凑边界，64×68dp 单元、最多 5 项可见；设置方向必须对四种样式都生效。
- `StashStore.java`、`StashClient.java`、`StashImageProvider.java`、`StashActivity.kt`：私有暂存、受限存入 RPC、按次授权读图和透明分享接收桥，进度见 `docs/STASH.md`。
  1.7.123起数据库为schema2，带同次图片导入批次；降到schema1旧APK须配套升级前数据备份，不能仅降APK或清数据。任务内备份位置和实际核验见STASH/DEVICE_VALIDATION，不提交用户内容。
- `StashOverlayPanel.kt`、`StashBatchCard.kt`、`StashImage.kt`：Miuix临时浮动托盘、分组和图片；标题/灰条拖动并自动贴边，展开面积≤16%。不再有系统小窗、搜索、复制或手动换边入口。
- `ModernOverlayViews.kt`、`ModernOverlayWindow.java`、`ModernPreviewSizer.java`：现代 Compose 内容、View outline、公开 Window 局部背景模糊和自适应正方形预览。
- `DragShareSettings.java`：设置默认值、范围校验、本地持久化和 Provider 配置 RPC。
- `BackgroundTouchBlocker.java`、`FrameworkBinderTransactionResolver.java`：可选地通过系统手势监视器取消原前台窗口的触摸流；直接 API 被拒绝或被隐藏 API 策略屏蔽时，从当前 ROM 的 framework DEX 动态解析输入 Binder 事务号并以 root 回退，失败时旁路观察。
- `SettingsScreen.kt`：Miuix 设置页（内容开关、目标可见性、批量操作、拖拽排序、外观和触摸参数）。
- `ModuleActivation.java`：Root 探测、当前版本传送门注入握手和首页激活状态来源。
- `DragAndDrop.kt`：参考 XiaomiHelper 的 LazyColumn 实时换位、边缘自动滚动和回弹状态。
- `PressTriggerDetector.java`、`PressCaptureSource.java`：Root evdev 按压时长/位移判定与按需 Root 取词，不需要校准，不冒称触摸面积。
- `PortalContentCaptureSource.java`：唯一读取传送门私有字段和初始触点的适配层。
- `AccessibilityNodeClassifier.java`、`AccessibilityCandidateSelector.java`：Root helper 共用的节点分类和命中优先级。
- `SharedRootNodeCapture.java`、`IRootNodeLease.aidl`、`IRootNodeCandidate.aidl`：模块进程唯一持有Root取词helper，Provider授权引导、每租约校验所属UID。手动租约暂停自动捕获，IO忙直接报告，不排队错过长按截止；候选同步回调不得等待主线程。最后租约/客户端死亡释放，不抢断其他UiAutomation。图片元数据跨Binder，截图裁剪留在调用方，仍核验窗口。
- `PointCaptureOverlay.kt`、`PointCaptureActivity.kt`、`PointCaptureTileService.java`：控制中心主动点取，透明桥结束后在原页选点，再复用 Root 语义取词/所选图片 OCR。与面积触发分开验收，不能把点取通过写成面积或普通长按修复。范围见 `docs/POINT_CAPTURE.md`。
- `RootScreenshotter.java`、`ScreenshotRectMapper.java`：Root 安全区域截图与坐标映射。
- `ShareTargetRepository.java`：查询可处理对应 MIME 的导出 Activity，并克隆/着色内置目标的旧矢量图标。
- `BitmapEncoder.java`、`ImageStagingClient.java`、`ShareImageProvider.java`：图片压缩、
  跨 UID 暂存、能力 URI 和授权。
- `SharedImageCleanupJob.java`：系统周期清理与导入时清理共用24小时期限，遍历cache/shared-images及cache/ocr-input；157另按私有清单清理本模块为旧式微信拖放创建的Downloads副本。不可遍历/清理files/stash、数据库或其他媒体。目标ROM已观察到冷进程Job绑定被自启动管理器拒绝，不能仅凭schedule成功声称后台可用，也不能自动修改自启动权限。
- `ClipboardWriter.java`、`RootClipboardMain.java`：读取剪贴板AppOps原始模式，受限时文字用Root系统UID、图片用Root Shell UID写入并核对；原文/URI只经stdin，不能把无异常当复制成功，不能修改AppOps来掩盖限制。120已验证侧窗/系统小窗图片跨UID读取及删除源后副本读取。System/Root UID不能作为剪贴板URI授权来源；Shell使用目标ROM实测IClipboard签名与com.android.shell归属，固定主用户/本机剪贴板，其他用户/虚拟设备未支持，不添加猜测兼容。
- `LocalImageSaver.java`：把图片首项保存到系统 Pictures，按秒生成文件名。
- `ShareLauncher.java`：构造显式 `ACTION_SEND` 并启动目标 Activity。
- `GestureMath.java`、`ShareUriToken.java`：可单测的纯逻辑。

## 传送门启动与重载

144起精确Hook 5.1.5的App.onCreate，仅主进程初始化；无拾取服务时只在按压已启用且来源为传送门时启动Root运行时。144–211曾通过SwitchControlProvider根query拉进程，168证实可使Root输入/取词ready，但211发现仅此启动会处于缓存进程并被内存压力回收。212改用原生TextContentExtensionService前台服务，不打开Activity，不调用getControlStatus，不把CLI零退出码当就绪证明。

既有重载弹窗的应用重载结束Portal/AICR，并重启SystemUI及原生侧栏ui进程；212通过原生前台服务重启Portal，核对新PID/版本/服务/Root输入与按压reader。AICR下次绑定加载。android/system_server的代码更新必须软或整机重启，不宣称应用重载更新全部作用域。未为了验证重启用户手机。

145以Root DOWN标记同一次按压和预览；被接受的分词/OCR交接后继续抑制迟到预览/原生浮窗并暂存宿主结束调用，到真实UP/CANCEL后回放再清标记。排队show复核标记，旧查询/失败回调不得接管或释放下一手势。此为源码回归通过，实际手指手感未验；面积仍无有效数据。

147重要纠正：参考作者经用户明确说明其手机也无尺寸数据，实际Root evdev+时长/位移模拟。当前不再做接触面积识别或校准，不能把参考成功写成硬件面积证据。PressDetector统一确认时间（默认300ms为本fork策略）、固定36px轴向位移、120ms一次分类；存活Portal Source更新settings，下次DOWN取值。旧thumb保存键沿用，面积调查文档仅保留历史。

## 当前交互验收标准

- 168用户明确改为文字默认原生拖放直接发送，不再要求中转站向微信输入框填字；多选文字合成一条交给接收App，不恢复StashTextDropWindow透明拦截层。图片同样拖入当前聊天，不走联系人选择器。接收App决定实际处理，不能将accepted结果当作发送成功。

- 微信中转站目标是将图片直接拖入当前聊天，不可用跳转ShareImgUI重新选择联系人替代；165分享入口方向已被用户否定。没有当前聊天实际接收证据就保持未解决。


- 内容就是入口：词块、图片预览和中转站内容可直接按住拖；多选时按已选内容拖整个选区，不增加专用拖出页面或按钮。
- 常用操作直接可达：151依用户新要求将编辑、字典、合并/拆分、二次爆炸等移至一级操作，更多只保留外观；清晰动作使用Miuix图标、长按提示和无障碍说明，含义不清的操作保留短标签。已有拖出、划选、返回收起手势不增重复按钮；明确关闭与收起仍区分。
- 返回可预测：先退编辑/更多/图片预览，再收起主面板；取消和拒绝拖拽保留原正文与选区，明确关闭不等于取消选择。OCR取消恢复来源窗口，新识别结果可撤销。
- 中转站存入只显示灰条或刷新既有展开托盘，不自动展开遮挡来源；主动打开才展开。142的内容/多选直接拖出仍保留。

146中转站Back先退确认/预览/组内，再收起；多选是主面板状态，Back不清选区，只有清空选择按钮明确取消。收起保留预览，刷新保留现存选择并合并忙时新增通知；全局组卡顺序的同一选区用于拖出/分组/删除，范围全选用集合加减。实际两项文字跨UID与收起/返回重开保留选区已验，不重复相同样本；图片/物理移动等边界见DEVICE_VALIDATION。

## 公开仓库

本fork已公开：https://github.com/chenran0408/HyperDragShare ，默认分支hyperos4-adaptation。origin为本账号仓库，upstream为Leaf-lsgtky/HyperDragShare；不要向upstream推送本fork提交。v1.7.147-dev为开发预览，APK本地/服务器SHA256已核对。用户已授权workflow权限，API已核对生效；android.yml执行JDK21/SDK37的单测、Lint及Debug构建，上传APK和报告14天。云端结果按具体运行核对，不把本地通过等同云端通过；临时CI签名不可冒充设备同签名更新包。不上传本地签名密钥，不自动发稳定版。签名、local.properties、原始APK/视频、私人截图与任务备份继续留在Git之外。后续代码批次公开前检查变更和真实验收范围，不重复建仓库。

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
源码也支持 `SIGNING_KEYSTORE_PATH`、`SIGNING_KEYSTORE_PASSWORD`、`SIGNING_KEY_ALIAS` 和
`SIGNING_KEY_PASSWORD` 环境变量配置同一 signingConfig；当前云端工作流只做Debug构建，未接入发布密钥或自动Release。

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

149运行证据：页面暂未就绪曾使RootNodeCaptureMain.awaitRoot抛IOException并使服务永久失效，现1200ms预算耗尽回window_not_ready，只结束该请求、保留helper，不自动重连或回退；同服务下一请求成功有回归。173已安装/Portal4566与Root ready确认，真实空缺后恢复未验。148已删除四秒冷却与录音子串筛选，在Root取词前排除触点明确录音控件；不得把这些源码检查写成实际录音体验通过。CI首轮39552595165对应148且success，后续版本按自己的run核对。

150目标ROM元数据探针：UiAutomation默认flags65554/caps11，窗口列表0；只增FLAG_RETRIEVE_INTERACTIVE_WINDOWS得到2/3个窗口及应用root。产品已在ready前配置此标志。212项本地检查与build308通过，174已安装；最新Portal12882取词reader ready，但Root输入探测命令退出137并报false。首次4541节点准备退出无stderr，直接Shell Root相同APK可ready；原因未定，不能用重试/修改授权掩盖。149缺窗后helper两次存活有真机证据；完整按住/录音手感仍未验。元数据探针均退出、设备jar已删、未接触私人内容。当前用户仍使用手机，避免开测试页。149 CI39553180987 success。

151依最新用户要求：二次爆炸不是拆分，主操作第二行首位复用CaptureMore→原页区域识屏→前后插入快照，未删此链路。清晰动作改Miuix图标/44dp触区/22dp图形/Tooltip与无障碍说明；有歧义的中转站、合并/拆分等保留短标签。编辑/全选也一级，不重复拖出/划选/返回收起按钮，更多只外观。build311完整212pass/Lint0errors153warnings2hints，175单次安装，Portal15214注入175/input ready，节点reader准备失败；前台微信保持，无新UI验收。系统日志已精确确认Trimming phantom processes杀Portal和模块su，不是Root未授权；下一批依ROOT_PROCESS_LIFECYCLE.md实现窄Hook，不能全局关phantom。归属应使用已持phantom锁保护的mPhantomToAppProcessMap，不读裸PidMap造成锁/数据风险；映射缺失原行为，不补查。当前框架Hook尚未加入151，不能称已解决。

151云端run39555560460对应8f013d0，推送后已启动但此条写入时尚未完成。150run39554086189已success并有两个Artifact。末次继续工作应先核对该live run，不能把150通过当151通过。

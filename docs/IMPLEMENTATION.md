# HyperDragShare 完整实现说明

本文记录 HyperDragShare fork 当前拖拽链路的实现边界；个别历史章节保留早期设计背景，实时安装和行为结论以 [DEVICE_VALIDATION.md](DEVICE_VALIDATION.md) 为准。目标是传送门识别长按文字或图片后，在手指附近立即显示预览，同一手势继续拖动并在有效目标上松手分享。简洁、流光、环形和现代菜单共用上、下、左、右四个方向，并在热区支持滚动。

当前设备Xiaomi15Pro / HyperOS4.0.0.17 / Android17，已安装1.7.151（175）。分词功能已放一级并用Miuix图标，保留二次爆炸独立入口；新UI待实测。按住入口使用时长/位移，148处理录音控件与冷却，149缺窗不退出helper，150补交互窗口标志。最新Portal15214加载175、输入ready，但节点reader准备失败；系统phantom裁剪原因已确认，窄Hook待实现。146文字跨UID及保留选区证据仍有效，完整新手势不称通过。

除设置外，文字、图片OCR、屏幕OCR、搜索与中转站以原页独立浮窗工作；外部分享使用透明接收桥，不打开设置首页。中转站仅保留临时拖放托盘和灰条，标题或灰条拖动、松手就近贴边、展开面积≤16%；系统小窗、搜索、复制和手动左右切换已删除，保留原有数据。141补系统栏避让与显示变化后的重新定位；142直接按住已选条目/组卡拖出整个选区，预览图也能拖出，删除底部专用拖动行并复用列表可见顺序；新交互未实测。

141新增精确StopSelfHandler消息1 Hook，补服务计时器以外的传送门退出路径；只在按压Source存在时抑制该消息。锁屏背敲保留系统动作的修正尚未重载到android。当前差距以[MIBIGBANG_MIGRATION_PLAN.md](MIBIGBANG_MIGRATION_PLAN.md)顶部表为准，设备证据见[DEVICE_VALIDATION.md](DEVICE_VALIDATION.md)，按压机制见[CONTACT_AREA_TRIGGER.md](CONTACT_AREA_TRIGGER.md)，中转站见[STASH.md](STASH.md)。下文旧版本的系统小窗、校准和1220×2656参数均是历史记录，不代表当前入口。

## 1.7.147 按时长与位移触发，取消面积前提

用户向参考应用作者确认：其手机按压没有尺寸数据，实际使用Root evdev加时长/位移判定。这条直接反馈修正了此前同机成功必须依赖面积的假设；旧APK存在尺寸加速分支，不代表作者或视频实际走该分支。历史静态记录保留，当前不再以恢复面积为目标或要求校准。

本fork删除PressTriggerDetector的接触轴阈值、220ms面积加速和按有无文字切换700/300ms逻辑，统一为Root单指停留时长与相对DOWN的36px轴向位移；无尺寸输入也走完整主路径。默认300ms是本fork的体验选择，不冒称作者提供的时间；已有用户确认时间继续沿用，范围250–1200ms。120ms按需分类缓存、锁屏/黑名单/输入法/录音提示过滤、真实来源复核及145的预览交接保持。

修正原传送门Source已存在就直接返回导致配置陈旧的问题：settings引用更新，下一次DOWN读取新时长，当前手势截止不被中途改写；不会因原生start回调重建连接或取消候选。复用原有确认时间设置，移到按压入口旁供两种来源使用；不新增页面。标题改为按住取词与识字，不声称区分拇指。旧thumb_trigger_enabled只作保存键沿用。

本方案仍是受控长按；它不能从相同的时长/位移识别手指身份，也不承诺与每个应用长按同时成立。需要原应用长按的场景使用已有黑名单或调整确认时间，真实手感仍待验。

build304完整testDebugUnitTest/lintDebug/assembleDebug成功：208项测试0失败/错误，Lint0错误/151警告/2提示。删除已取消面积能力对应的8项测试，替换为5项时长/位移/取消/下一次时长回归；配置测试同时校验默认300与保存700。一次覆盖安装1.7.147/171，后台Portal PID21130/注入171/Root171=true确认，微信2387前台不变。没有用ADB普通输入或旧样本冒称新Root手势实测。

## 1.7.146 临时托盘取用与返回

- 收起仅改变窗口形态，保留图片预览、组内位置和选区；Back先退删除确认、预览和组内浏览，再收起托盘，不把多选当成要取消的编辑。按钮明确为清空选择，只有显式清空或完成删除/分组才重置选区。
- 选区按全部组卡顺序及各组batchOrder统一展开，拖出、分组、删除共用；当前组全选只增减当前可见ID，不替换其他组的选择。图片预览隐藏列表多选/分组/删除入口，返回和收起复用同一状态行为。
- 新内容刷新只更新现存条目与剔除已删除ID，不清当前预览和选择，不打断展开列表的阅读位置。正在读取时收到新增通知合并为下一次读取，不丢通知或定时轮询。
- 灰条/标题移动被系统CANCEL时恢复DOWN位置，不执行松手贴边或写入新位置。正常UP仍就近贴边。灰条、≤16%面积、内容本身拖出和原数据格式不变，无新页面。

最终build303：211项测试0失败/错误，Lint0错误/151警告/2提示，assembleDebug成功，1.7.146/170一次覆盖安装。小米15Pro真实Android全局拖拽：按住已选HDS146-B拖出B/A两项，13:27:14.408接收UID10228记录items=2及正确顺序；源模块UID10270。托盘收灰条，展开仍选2项；Back收起后展开仍选2项。此证据是ADB输入进入实际View与系统拖拽，不是物理拇指或面积输入。

仅通过确认清理本次两条生成记录，界面11→9；未操作原有条目。临时接收器已卸载，模块浮窗已明确关闭，回原微信task2387；本次8张本机PNG已删除，不提交用户画面。图片跨UID、多组隐藏选区、刷新期间新增、物理侧条移动/取消、横屏与深色仍待对应实机验收；回归不等于这些全部通过。

## 1.7.145 按压与原生拖拽交接

修正已确认的代码冲突：传送门预览先于700ms确认出现时，旧allowed会因Controller活动而取消本次分词。现在Root DOWN在接收入口标记手势，预览保存同一标记；仅同一手势允许继续确认。移动仍按原36px规则取消分词候选、保留原拖拽，不降低门槛冒充面积。

文字/OCR Provider接受请求后，撤下同手势预览并抑制迟到的排队预览和原生浮窗，保持到真实Root终止。未接受时保留原预览；图片识别RPC现在明确返回accepted。宿主cancelTask/257在交接后仍暂存，258仍抑制；真实UP或多指CANCEL回放原调用，再清标记。多指CANCEL之后parser不会补UP，因此不能只清标记留下宿主状态。旧图片失败回调仅释放本请求接管；新Root DOWN到达但Source主线程尚未更新时，旧查询也不得接管新手势。

不新增页面、操作按钮或校准。三条交互标准沿用143：内容直接拖，常用复制/搜索/分享直接可达，先退子层再收主面板，取消保留正文和选区。208项单测、Lint与构建通过，含5项针对交接的回归；真实物理面积、普通按住与原生菜单冲突手感、跨UID拖放和中转站真实手指交互仍待验。

## 1.7.144 启动与作用域重载

传送门5.1.5的App.onCreate负责主进程按压初始化，不再必须先创建TextContentExtensionService。无服务时仅传送门模式且按压已启用才创建输入源；设置观察保留，关闭/服务销毁不被通用Hook拦截。Root通过无业务副作用的SwitchControlProvider根query拉进程，既有首页握手也使用此路径，不打开Activity、不调用会迁移设置的getControlStatus。

设置沿用同一重载弹窗，提供应用热重载、Android软重启、整机重启；热重载只涉及Portal和AICR，并核对新PID/版本/Root确认。AICR下次绑定加载，android代码更新要后两种方式，不能在热重载中冒称框架已更新。168后台冷启动已真机确认输入/取词ready，三选一UI和全模式重载未验。框架Hook仍待重载；真实面积缺失和普通按住冲突不因启动修复而消失。

## 1.7.143 原页操作与临时托盘完整批次

- 词块长按直接通过原生全局拖拽传出选区。此前仅分享图标长按可用：内容路径依赖固定返回false的SidebarUtils，且短内容处于滚动边缘时不排长按。现在删除该占位类，拖出候选独立于边缘追加/视觉预选；同步ACTION_CANCEL不清空已确认选区，拒绝拖拽或未完成选择取消恢复原快照，正常点击与连续划选仍保留。
- 删除词区原浮动工具条、假工具条和挤动文字的旧实现。Miuix底栏固定复制、搜索、分享；操作对象优先所选文字，无选区时使用全文。编辑、查词、合并/拆分、重新OCR、外观参数放在现有更多；合并/拆分仅在有效选区出现。
- 返回/点外部先退出编辑或更多，之后收起主面板到已有返回条；收起前保存正文、分词结果和选区，重新展开恢复。明确关闭或启用复制后退出会结束窗口，不把返回当成取消选词。原图重识字取消恢复旧面板，成功结果带一层撤销，可恢复重识别前编辑及选区。搜索继续使用原页子窗，不进入模块首页。
- 图片预览去OCR取消时回到同图预览，成功进入原页分词。识别结果/取消只交付一次，重试和识别模式切换移交回调；被新请求替换时不复活旧窗口。边缘追加收到ACTION_CANCEL不再确认插入。
- 保存内容的三条入口（传送门Provider、分词更多、外部分享）只显示灰色侧条；已有展开托盘原位刷新。点击侧条展开、标题/侧条移动和松手就近贴边、≤16%面积及选区直接拖出保持不变，不改用户暂存数据。主动打开托盘仍展开。
- 按压Root查询在读取节点前和来源复核后排除触点命中的输入法窗口，拒绝用键盘下面的应用内容生成OCR；非按压旧接口保持原行为。真实面积缺失仍未解决，本批没有重命名普通分支或要求再校准。

本批的构建、安装和未实测边界见DEVICE_VALIDATION最新条目；不能把单元手势回调当作真实跨应用接收已通过。

## 1. 运行边界

- 传送门版本：`5.1.5`
- 注入包：`com.miui.contentextension`、`com.xiaomi.aicr`、`android`
- 不注入：`com.miui.contentcatcher`（Application Extension Service `4.1.9`）
- Android：minSdk 33，targetSdk 34，compileSdk 37
- LSPosed API：82
- 可靠输入：root + Linux evdev

LSPosed 作用域由 `res/values/arrays.xml` 声明，且 `MainHook` 在运行时再次检查包名。传送门
Hook、AICR 文本直取 Hook 与背部轻敲框架 Hook 分别限制在这三个进程；不加入
`com.miui.contentcatcher`。不要强行停止 Application Extension Service；部分 HyperOS 版本会
因此连带强停其他应用。

## 2. 总体数据流

首页的“内容获取方式”默认是“传送门”，并在模块 SharedPreferences 中以
`content_capture_mode` 保存。设置保存后会通知
`content://com.leaf.hyperdragshare.codex.share/settings`：传送门进程收到无障碍模式时会取消会话并停止
Root/MIUI 输入源；已连接的无障碍服务收到无障碍模式时才启动自己的 Root 输入运行时。两边都在
创建会话前再次校验模式，因此任何时刻只有一个来源可以创建 DragShare 会话。

```text
传送门长按识别
    |
    +-- startPickTextTask / startPickImageTask
    |       |
    |       +-- PortalContentCaptureSource.capture() -> CapturedContent
    |       +-- DragShareController.show(content, point, settings) -> 预览悬浮窗
    |
    +-- RootTouchSource 从 /dev/input/event* 持续旁路读取同一物理触摸
            |
            +-- 原始 ABS 坐标 -> 屏幕坐标 -> 主线程
                    |
                    +-- 可选 cancelCurrentTouch/pilfer -> 原页面 ACTION_CANCEL
                    +-- updateViewLayout() 更新预览位置
                    +-- 底部阈值 -> 简洁/流光菜单
                    +-- 配置边缘热区 -> 环形面板或线性菜单滚动
                    +-- 真实 ACTION_UP -> 命中目标并 ACTION_SEND
```

主要文件职责：

| 文件 | 职责 |
| --- | --- |
| `MainHook.java` | 限制 LSPosed 注入包 |
| `PortalHooks.java` | 传送门 Hook、生命周期、输入源仲裁、取消信号处理 |
| `RootTouchSource.java`、`EvdevTouchParser.java` | evdev 发现、解析、稳定主触点和 DOWN/MOVE/UP/CANCEL |
| `MiuiMotionSource.java` | MIUI MotionEvent 回退通道 |
| `CapturedContent.java`、`OverlayWindowPolicy.java` | 来源无关的文字/图片模型和窗口类型策略 |
| `DragShareController.java` | 会话状态、悬浮窗、菜单、落点与分享调度；不依赖 Xposed |
| `PortalGlowView.java` | 流光样式全屏光效、向下进度和托盘展开动画 |
| `CircleMenuOverlayView.java` | 环形样式的边缘光、贴边面板和进入动画 |
| `CircleMenuGeometry.java` | 四边菜单的边缘命中、锚点和圆周布局纯逻辑 |
| `ModernOverlayViews.kt`、`ModernOverlayWindow.java`、`ModernPreviewSizer.java` | 现代样式的 Compose 内容、Android 公共局部背景模糊窗口和可测的自适应正方形预览尺寸 |
| `DragShareSettings.java` | 设置默认值、范围校验、本地存储、目标可见性/排序和跨进程配置读取 |
| `DragShareLog.java`、`DragShareDiagnostics.java` | 统一日志等级/落盘、文件导出和调试运行环境与输入节点诊断 |
| `BackgroundTouchBlocker.java` | 可选取消原前台窗口的触摸流，失败时回退为旁路观察 |
| `SettingsScreen.kt` | Miuix 设置页面 |
| `ModuleActivation.java` | Root 探测和当前版本传送门注入握手 |
| `PortalContentCaptureSource.java` | 唯一读取传送门私有字段、Bitmap 和初始触点的适配层 |
| `DragShareAccessibilityService.java`、`AccessibilityContentCaptureSource.java` | 无障碍生命周期、Root 长按协调和来源隔离 |
| `AccessibilityBlacklist.java` | 动态内置排除（当前桌面、默认输入法）和用户应用黑名单判定 |
| `LongPressGestureDetector.java`、`AccessibilityNodeClassifier.java`、`AccessibilityCandidateSelector.java` | 可单测的长按、节点分类和触点选择 |
| `AccessibilityScreenshotter.java`、`RootScreenshotter.java`、`ScreenshotRectMapper.java` | API 30+ 无障碍区域截图及 API 28/29 Root 回退 |
| `ShareTargetRepository.java` | 查询可分享 Activity，并注入复制、保存和文本分词内置动作 |
| `BitmapEncoder.java` | 将 Bitmap 无损编码为 PNG |
| `ImageStagingClient.java` | 从传送门进程调用模块 Provider |
| `ShareImageProvider.java` | 模块 UID 下暂存图片并提供 content URI |
| `ShareLauncher.java` | 构造并启动显式 ACTION_SEND |
| `LocalImageSaver.java` | 将图片保存到系统 Pictures 集合并生成时间文件名 |

## 3. 传送门 Hook 与内容抓取

入口 `MainHook.handleLoadPackage()` 根据包名分发到各自限定的适配层：

- `com.miui.contentextension`：由 `PortalHooks.install()` 安装下列传送门 Hook。
- `com.xiaomi.aicr`：由 `AicrCopyHooks.install()` 仅处理 `TextDirectProvider.call("processText", …)`；开关默认关闭。135起通过仅限AICR/模块UID的 `open_aicr_text` 请求原页分词窗口，不启动Activity，只传纯文字。仅单项明确文本，保留原文空白，排除敏感标记、图片/URI和本模块复制标签；同一剪贴板500ms重复请求只确认一次。跨Binder工作显式切为AICR身份并finally恢复；失败记录原因、提示用户并保留原Provider流程。目标AICR 4.12.26的cognitionService已用Root命令触发真实Provider验证，不等于真实应用复制入口或小爱OCR已验收。
- `android`：由 `BackTapFrameworkHooks.install()` 仅处理 `MiuiBackTapGestureService.postShortcutFunction()` 及其双击/三击回调；模式默认关闭，失败时保留 ROM 原动作，不重注册传感器。

AICR 与系统框架入口的静态依据、未验证项和目标设备记录见
[HYPEROS4_ADAPTATION.md](HYPEROS4_ADAPTATION.md)。

136还接入传送门5.1.5复制卡片：`TaplusSplashFloatView$13.onClick(View)`为已核实的“编辑所选文本”按钮，原本启动`clipboard.EditActivity`。设置“复制文字分词”复用copy_direct_enabled和默认关闭值，每次点击读取原Portal受限配置。仅接收与getClipboardContent逐字一致的当前单项、非敏感外部文字；接受原页窗口请求后跳过原Activity，再调用宿主hideSplashFloatView(true)收卡。请求失败保留原编辑；已接受后的收卡失败单独报告，不再启动另一编辑界面。私有访问在PortalContentCaptureSource中。匿名类编号绑定已核对的5.1.5，更新需重新定位；不增作用域，不绕过ClipboardReceiver黑名单。Via空白页真实复制→点击卡片→原页分词及关闭开关后的原编辑已验证，不等于所有应用都会显示卡片。AICR原监听还受能力1210启停约束，模块未强制开启。

传送门 Hook 安装以下入口：

1. `TextContentExtensionService.onCreate()`：先向模块 Provider 上报当前 Hook 的
   `BuildConfig.VERSION_CODE`，注册设置 observer；只有当前模式为传送门时才创建控制器、启动
   MIUI/Root 输入线程，并从服务的 `mCallback` 补充发现真实回调类。
2. `onDestroy()`：停止两个输入源、销毁控制器并立即移除悬浮窗。
3. `startPickTextTask()` / `startPickImageTask()`：传送门确认长按内容时，先预约原浮窗抑制，
   再创建 DragShare 会话和预览。预约态覆盖从回调线程切回主线程的短暂间隙。
4. `BaseFloatView.addToWindow()`：预约态或 DragShare 活动期间都阻止传送门自己的悬浮卡片显示。
   否则宿主会在 `showMainFloat()` 中把全屏 `mMainBg` 设为 `0.3f`，给所有拖拽样式额外叠一层
   不受模块外观设置控制的半透明遮罩。
5. 回调 `onContentReceived()`：提取传送门返回的 `MotionEvent` 和
   `observe_control_event`，并过滤会误终止拖拽的控制消息。
6. `cancelTask()`：无可靠 root 输入时沿用宿主取消；root 拖拽活动时暂存并阻止原调用，
   等真实 UP 后再回放原方法。

`PortalContentCaptureSource.capture()` 先读取传送门静态字段 `sIsTextMode` 和 `sContent`。
不是有效文字时，再调用传送门的静态 `getBitmap()`，并输出不依赖 Xposed 的
`CapturedContent`。这些字段和方法属于传送门 5.1.5 的私有实现，是升级宿主版本时最需要
重新核对的接口。

活动会话中再次收到 `startPick*Task` 会被忽略，防止传送门移动过程中重复初始化并删除已有
预览。

## 4. 为什么原方案需要“松手后再摸一次”

“Android 底层限制，所以新悬浮窗只能等下一次触摸”只说对了一半。

Android 普通 View 触摸分发确实有这个约束：一次手势在 `ACTION_DOWN` 时已经确定窗口和
View 目标。长按识别发生在这条手势中途，此时新加一个系统悬浮窗口，
WindowManager 不会把正在进行的手势重新定向给新窗口。因此，如果实现只监听悬浮窗自己的
`onTouchEvent()`，它收不到当前手势，只能等用户抬手，再以一次新的 `ACTION_DOWN` 触摸
悬浮窗。这就是旧方案表现为“松手后再次移动才跟手”的原因。

但限制的是“中途更换普通事件接收窗口”，不是“程序无法知道当前手指坐标”。DragShare
采用旁路观察来规避：

1. 传送门服务创建时，`RootTouchSource` 就已开始读取触摸设备，而不是等悬浮窗出现后才
   开始监听。
2. 预览窗使用 `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE`，完全不尝试抢占已有手势。
3. root 直接读取 `/dev/input/event*` 的 Linux `input_event` 流。这个流位于 Android
   View 分发之前，仍包含当前那根手指后续的全部坐标帧和物理抬手状态。
4. 每个坐标帧经 ABS 范围、屏幕尺寸和旋转换算后送到主线程，控制器直接调用
   `WindowManager.updateViewLayout()` 改变悬浮窗位置。
5. root 就绪后，它成为唯一权威输入源。MIUI 通道的 MOVE/UP/CANCEL 不再参与会话结束，
   避免两套来源的时序互相打架。
6. 传送门移动时会发出 `cancelTask`、控制码 `258`，还可能发出过早的 `257`。root 活动
   拖拽中这些信号都不会结束 DragShare；`cancelTask` 和 `257` 的原调用会被暂存，在 evdev
   真正抬手时按到达顺序回放，让宿主完成任务状态复位。`258` 只代表移动取消，继续抑制。

控制器即使尚未处于活动拖拽，也会记录 root 最近一次坐标。传送门确认长按并调用
`startPick*Task` 时，预览首先放到这个最近坐标；随后下一帧 root MOVE 继续更新。所以从
用户视角看，悬浮窗在长按识别完成后直接接上同一条手指轨迹，不存在抬手或第二次
`ACTION_DOWN`。

默认情况下原页面仍可能滚动，这是旁路观察方案的预期行为：evdev 读取只是观察事件，
`FLAG_NOT_TOUCHABLE` 的悬浮窗也不消费事件。开启“拖拽时阻止背景滑动”后，控制器会在
会话激活后优先调用隐藏的 `InputManager.cancelCurrentTouch()` 让原窗口收到 `ACTION_CANCEL`；
在没有该方法时再尝试 `monitorGestureInput(...).pilferPointers()`。root evdev 仍作为 DragShare
的唯一坐标源。该隐藏 API 受 ROM 权限控制，
失败时只记录 `DragShare/InputBlocker` 日志并回退到默认旁路行为，不会结束拖拽。

## 5. root 输入解析

`RootTouchSource` 的步骤如下：

1. 读取 `/proc/bus/input/devices`，选择同时具有绝对轴并标记为 direct 或名称包含 touch
   的设备；普通权限不可见时用 `su -c cat` 重试。
2. 用 `su -c getevent -lp <device>` 读取 `ABS_MT_POSITION_X/Y` 最大值。
3. 优先直接打开设备节点；失败时启动 `su -c exec cat <device>` 并读取其 stdout。
4. 根据注入进程位数解析 16 字节或 24 字节 `input_event` 结构。
5. 维护最多 16 个 MT slot，处理 `ABS_MT_SLOT`、`ABS_MT_TRACKING_ID`、
   `ABS_MT_POSITION_X/Y`、`BTN_TOUCH`，并在 `SYN_REPORT` 时提交一帧。
6. 选择第一个具有完整坐标的活动 slot 输出 MOVE；所有 slot 结束并出现 tracking-id/按键
   抬起时，用最后坐标输出 ACTION_UP。
7. `GestureMath.mapRawPoint()` 先归一化，再按屏幕真实像素及 rotation 0/1/2/3 映射。

当前实机探测值是：

```text
设备名：Xiaomi_Touch_Input_0
节点：/dev/input/event7
ABS 最大值：121999 x 265599
屏幕：1220 x 2656
```

因此原始值约为屏幕像素的 100 倍，但代码使用探测到的最大值做比例映射，没有写死 100。
设备节点和范围也都不能硬编码。当前只以第一个有效活动 slot 驱动拖拽，多指中途介入不属于
已验证场景。

`MiuiMotionSource` 通过反射注册
`MiuiInputManager.MiuiMotionEventListener`。在目标 ROM 上可能出现“注册成功但不下发事件”，
所以它只作为 root 未就绪时的回退。root 一旦 ready，`PortalHooks.dispatchMotion()` 会明确
丢弃 MIUI 事件。

root 输入线程使用阻塞式读取，不是高频定时轮询；它随传送门服务生命周期存在，主要开销是一个
等待内核事件的线程，空闲时不会持续计算坐标。近手方向的旋转向量/加速度传感器则只在启用
该选项且拖拽会话活动期间注册，松手、取消或服务销毁时立即注销。

## 6. 悬浮预览与底部菜单

传送门路径的预览、菜单和流光光效层使用宿主已获 `INTERNAL_SYSTEM_WINDOW` 授权的
`TYPE_SYSTEM_DIALOG`；无障碍路径使用
`TYPE_ACCESSIBILITY_OVERLAY`。两种来源的悬浮层都带有：

```text
FLAG_NOT_FOCUSABLE
FLAG_NOT_TOUCHABLE
FLAG_LAYOUT_IN_SCREEN
```

代码运行在具有相应系统能力的传送门进程中。简洁样式的文字预览为 `184 x 112 dp`，最多四行；
图片预览为 `148 x 148 dp`。预览水平居中于手指，垂直放在手指上方 `20 dp`，并根据状态栏、
刘海、导航栏和分享菜单边界做夹取。

拖拽样式设置只影响这些悬浮层，不影响模块主页面。新安装、清除设置或缺少 `ui_style` 时默认使用
现代样式；已有用户保存的样式不会因升级而被覆盖。简洁样式保持紧凑矩形预览；菜单尺寸与方向共用布局单元，当前尺寸记录见本节后文。流光样式是根据 JADX MCP 中
`com.oplus.contentportal` 的拖拽实现还原的独立
渲染分支：预览保持约 `112/116 dp` 的圆角小窗，拖拽开始就挂载一个全屏
`FLAG_NOT_TOUCHABLE` 光效层，光效强度随手指向菜单触发边缘的进度增加；进入配置热区后，光效展开并
在配置的屏幕边缘显示托盘，分享项按错峰弹簧曲线进入。两种样式都保持不可
触摸窗口和相同的命中/边缘滚动逻辑。

现代样式沿用简洁样式的上、下、左、右和近手菜单几何，但预览与菜单根节点改为
`ModernOverlayComposeView` 内的 `ComposeView`。该根节点显式提供 `LifecycleOwner`、
`SavedStateRegistryOwner` 和 `ViewModelStoreOwner`，因此可安全地
作为独立 `TYPE_SYSTEM_DIALOG` / `TYPE_ACCESSIBILITY_OVERLAY` 窗口运行。预览始终为正方形：
文字按 Unicode 字符数量增长，图片按像素几何平均尺寸增长，最终限制为不超过屏幕宽度的三分之一。
模糊不在 Compose 中实现，也不使用 `textureBlur()`、截图采样或
`FLAG_BLUR_BEHIND`。后者会走 WindowManager 的全局 dim layer，不满足局部效果的边界。
`ModernOverlayWindow` 将每个现代根 View 放入独立的透明 `Dialog` 窗口。无障碍和传送门都使用
Android 公共 `Window.setBackgroundBlurRadius()`；传送门运行在宿主进程并采用其
`TYPE_SYSTEM_DIALOG`。2026-07-21 的同进程纯色窗口对照显示：在 View/窗口均为 `alpha=1`、
`PixelFormat.OPAQUE` 的前提下，`2008` 是唯一保持实色的可见候选；`TYPE_PHONE` 等其他可见候选
仍呈现额外半透明。代码不调用隐藏 View API、
不使用截图采样；系统关闭 cross-window blur 或设备不支持时回退为不透明 Card。窗口的透明
`GradientDrawable` 和根 View 均提供 `16 dp` 圆角裁剪，使系统效果只在预览或菜单自己的窗口范围内渲染。Compose 可见背景由 Miuix `Card` 承载，使用零内边距、默认
`16 dp` 圆角和 `surfaceContainer` 颜色；原生模糊可用时按“玻璃不透明度”混合该颜色，不可用时回退为
不透明 Card。预览与菜单仍带
`FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCHABLE`，坐标仍通过 `WindowManager.updateViewLayout()` 更新 Dialog 的
DecorView。入场和退场动画直接作用于 DecorView，因此 Card 内容和原生模糊会以同一淡入淡出/缩放曲线
出现或消失，不会留下滞后一拍的模糊层。系统关闭 cross-window blur（例如省电模式或设备不支持）时，
窗口监听器会让 Compose 切换到实色 Card，不保留截图回退。现代底部菜单两侧固定内缩 `12 dp`，
命中区域随窗口边界同步收窄。现代 View 不设置 elevation 或硬阴影；分享项按 38 ms 错峰出现，
但每个项始终保留固定布局单元，因此边缘滚动保持与简洁样式相同的连续像素偏移，不会因进入动画
重排而跳到首项。复制、保存和文本分词在 Compose 中使用固定的 Miuix 图标/标识，其他应用图标
仍以 `44 dp` 画布快照渲染。手指离开菜单时先播放退出动画，最多约 220 ms 后强制移除窗口；
同一手势重新进入触发区会创建或恢复菜单。命中、边缘滚动和真实 root `ACTION_UP` 的裁决仍由
`DragShareController` 完成。

### 6.1 流光样式与 JADX MCP 的对应关系

这套效果不是凭 UI 颜色猜测出来的。MCP 当前打开的参考 APK 是
`com.oplus.contentportal 16.9.8`，关键反编译类和调用关系如下：

| MCP 类/资源 | 参考行为 | DragShare 对应实现 |
| --- | --- | --- |
| `p334z5.C3850i` (`COEViewImpl`) | 管理底部光效窗口和 enter/process2/exit 状态 | `PortalGlowView` + `DragShareController` |
| `coe_runtime_shader_view.xml` | 全屏 `FrameLayout`，底部 `COETextureViewOwn` | 全屏透明绘制层，仍不可触摸 |
| `glow_effect.coz` | 光效素材，参数 `_HaloAlpha` | Canvas 光带、粒子、波纹，强度由拖动进度控制 |
| `process1_StandardPhone_enter/exit` | 进入/离开底部热区 | 光效从基础亮度平滑增减 |
| `process2_StandardPhone` | 到达托盘时的一次性展开动画 | `expandMenu()` 与分享项错峰进入 |
| `p280v5.C3457i` (`DragViewHelper`) | 预览 200 ms 缩放/透明度动画 | `startPreviewEnterAnimation()` |
| `p320y5.C3755m` (`MainPanelHelper`) | 处理拖动位置、边缘滚动和项目放大 | 既有命中/滚动逻辑 + `portalItemScale()` |
| `p320y5.C3746d` (`AnimationHelper`) | 列表项从约 500 px 下方弹入 | `PORTAL_ITEM_ENTER_OFFSET_DP` + `OvershootInterpolator` |

参考实现还在 `layout_bg_settling.xml` 中用截图层承载托盘，并通过系统窗口/Surface 动画做
屏幕倾斜和导航栏处理。DragShare 的旁路输入窗口不能安全地接管宿主 Surface，因此本版本
保留光效、分段展开、错峰弹入和距离缩放，暂不复制整屏倾斜；这不影响分享命中和跟手坐标。

三种线性样式（简洁、流光、现代）共用 `64 × 68 dp` 的项目布局单元：左右菜单使用 `80 dp` 触发带，上下菜单使用 `76 dp` 触发带，最多显示五项并可滚动。环形面板为 `240 dp`，项目为 `56 dp`。1.7.68 四种样式共 16 组方向截图与日志已检查正确。该截图检查不替代逐项拖拽、落点和目标接收验收。

简洁样式根据“菜单位置”在对应边缘的触发区显示；左右位置使用 `ScrollView`，上下位置使用 `HorizontalScrollView`。简洁和现代样式在本分支中
与流光、环形一样，指针进入配置的触发区后直接展开，不再要求先从长按起点朝菜单方向移动至少
`16 dp`。此修正避免缺失起点坐标或快速越过热区时菜单不展开；触发边界有单元测试覆盖，仍需在目标设备确认。菜单
暂时收起后重新进入热区仍可再次展开。背景不透明度、
背景圆角和菜单到边缘距离只作用于简洁样式；背景不透明度使用绝对 alpha，`100%` 一定是
完全不透明，不会与主题色自身的 alpha 相乘。简洁菜单将背景作为独立图层绘制，使传送门和无障碍
两种窗口来源都使用相同的背景透明度路径。自绘底板和控件颜色跟随 Miuix 色板；流光底板与现代样式透明度当前仍在修正可读性，计划随 1.7.69 设备验收。“图标不透明度”同时作用于所有样式的目标图标
和名称。“手指移开时关闭分享菜单”是四种样式的公共设置：简洁、现代和流光菜单只在
实际菜单或触发带内保持展开，环形菜单只在展开面板及其项目缓冲区内保持展开。离开后会暂时
收起，同一手势重新进入对应触发带仍可再次显示。普通线性菜单会在约 `160 ms` 内淡出后移除；
现代菜单由自身的窗口退出动画收起。流光样式在所选屏幕边缘的触发带展开；收起时光效从
托盘展开态平滑退回当前下拉进度。
`ShareTargetRepository` 以当前 MIME 构造 `ACTION_SEND`，查询默认且 exported 的 Activity，
去重后加载图标和名称，按菜单方向选择横向或纵向滚动容器。“复制文本”和“复制图片”分别是
文字与图片菜单的内置首项；图片菜单随后是“保存到本地”，文字菜单随后是“文本分词”。文字复制
使用普通文本剪贴板，图片复制会等待 PNG 暂存完成后以 `ClipData.newUri()` 写入可读取的图片 URI。
两个复制项在“菜单可见性”页独立控制，所有内置动作也都遵守菜单可见性设置；保存/分词不会出现
在另一种内容类型的菜单中。复制、保存到本地和文本分词在简洁、流光、环形和现代菜单中
都复用模块已有的 `ic_copy`、`ic_download`、`ic_text_segment` 矢量图标，绘制在 `#FF3482FF` 的
圆角瓷片上并统一使用白色图案；圆角和图案内缩沿用原有内置应用图标比例。现代菜单不再显示目标名称的首个字。分词页运行在模块进程，使用本地 `cppjieba` 词典
将文字拆成可选词块，支持按词块复制或通过系统分享所选文本，不会把捕获内容发送到网络服务。

命中判断使用每个菜单项的屏幕坐标。手指进入线性菜单对应滚动轴两端的热区后，主线程每约 16 ms
按当前进入深度滚动：刚进入热区时为设定速度的 `20%`，随后按深度平方缓入加速，距离物理屏幕边缘
约 `8 dp` 时即达到设定的最高速度；滚动后立即重新计算当前手指下面的目标。简洁样式的命中项轻微放大；
流光样式按距离连续缩放，中心项最高约 `1.23x`，相邻项每隔一个项目宽度减少约 `0.13`，
与 MCP 的 `C3755m.m16626x()` 公式一致。真正的 ACTION_UP 到来时，只有落点仍命中某个目标
才会分享；否则只移除预览和菜单。

### 6.2 环形样式与当前 JADX MCP 软件的对应关系

“环形”按当前 MCP 中的 `com.oplus.rom.circlemenuview` 源码和参考应用实机行为重建。MCP
返回的 dex 没有可用的 Manifest/资源列表，但菜单布局类和控制器完整可读。dex 中同时保留
上、下布局类。本 fork 统一四种样式的方向设置，环形菜单也提供四边方向；目标机环形四边截图及 edge 日志已核对方向正确：

| MCP 类 | 从源码确认的行为 | DragShare 对应实现 |
| --- | --- | --- |
| `CircleMenuLeftLayout` | 左边缘容器；起始角 `18°`，每个子项增加 `36°` | 环形左侧几何 |
| `CircleMenuRightLayout` | 右边缘容器；起始角 `198°` | 环形右侧几何 |
| `CircleMenuTopLayout` / `CircleMenuBottomLayout` | 上、下边缘的对应布局实现 | 环形上/下方向显示已核对，预览避让修正待复测 |
| `p111ha.C3154y` | 边缘热区、截图背景、菜单和窗口生命周期 | `DragShareController` 环形分支 |
| `p111ha.C3122a` | 圆菜单适配器，最多排列五个项目 | `CircleMenuOverlayView.setTargets()` |

布局的 `m(float)`/`n(float, boolean)` 使用同一组圆周公式，半径来自
`float_circle_item_to_radio_size`，相邻项目间隔固定 `36°`。侧边布局不是屏幕内部的整圆：
横向深度约为半径、纵向跨度约为直径，圆心落在物理屏幕边缘，屏幕内只显示半圆；本 fork
也实现上、下方向。控制器的 `m17103W`、`m17104X` 对应参考代码左、右打开流程；`ObjectAnimator` 时长为
`500 ms`，菜单从 `0.7` 缩放并沿边缘方向偏移约 `0.3 * float_circle_view_width` 后进入。
项目切换/淡入还使用 `300 ms` 动画，关闭使用约 `400 ms` 加速动画。

DragShare 的实现细节如下：

- 菜单可按设置在上、下、左、右四边触发。圆周项目最多显示五个，项目尺寸 `56 dp`，完整面板尺寸 `240 dp`；方向显示已核对，实际分享、滚动和新版避让仍需验收。
- 菜单窗口仍是全屏 `FLAG_NOT_TOUCHABLE` 覆盖层，项目坐标由 root evdev 事件驱动，避免
  改变 Android 当前手势的事件归属。
- 展开瞬间记录方向和对应边缘锚点，此后 MOVE 只更新命中目标，不移动面板、不切换边缘。
- 开启公共的“手指移开时关闭分享菜单”后，离开圆形面板的扩展区域会折叠；重新进入对应
  触发区并停留 `200 ms` 可以在同一手势内重新展开。
- 菜单最多显示五个分享目标，按圆周间隔布置；项目进入动画为 `500 ms`，命中时放大约 `1.12x`。
- 当目标超过五个且手指停留在圆弧首/尾项目上时，按约 `300 ms`（随滚动速度设置缩放）
  滚动一个目标。重叠目标复用原 View 并沿圆弧移动，离场目标淡出，新目标从圆弧外进入；
  位移动画和命中缩放使用不同 View 层级，不会互相取消。新目标先以 `LayoutParams` 固定到
  目标圆弧坐标，再通过相对位移淡入，不使用首次布局前会以 `(0,0)` 为起点的绝对 `x/y`
  动画，因此不会从屏幕左上角飞入。
- 环形目标 View 在真正展开时才创建并立即定位，未展开的全屏覆盖层没有处于 `(0,0)` 的子项，
  避免触发拖拽时在屏幕左上角闪出半透明应用图标。
- “保存到本地”与应用目标使用同一个五项滑动窗口，不再固定占位；滚动后它也会正常离场。
- 松手仍沿用原有显式分享和图片 Provider 链路。

MCP 还会对截图背景做 `500 ms` 的缩放/平移动画，并在部分设备上配合 Surface 做整屏倾斜。
当前模块不能安全接管传送门的 Surface，因此环形样式复刻了边缘光、圆周几何、分段展开和
项目动画，暂不复制整屏倾斜；这不影响跟手、命中或分享。

## 7. 设置页与运行时参数

启动 Activity 已从旧的原生 `LinearLayout` 页面迁移为 Miuix Compose：

- `MiuixTheme(ThemeController(...))` 使用显式 `ColorSchemeMode.Light` 或 `Dark`；
- 首页顶部状态卡直接对齐 InstallerX 的 Miuix 实现：成功态使用浅绿 `#DFFAE4`（深色为
  `#1A3825`），失败态使用 `#FAEEEE`（深色为 `#381A1A`）。状态图案分别使用 Material
  `CheckCircleOutline` 和 `ErrorOutline`，尺寸为 `170 dp`，在卡片右下区域按 `(50 dp, 38 dp)`
  偏移并裁切，颜色为 `#36D167` / `#D13636`，不是右侧居中的普通操作图标。卡片内容同样采用
  InstallerX 的完整三段结构：`20 sp` 标题、`14 sp` 摘要、`36 dp` 间隔和 `14 sp` 状态来源；
  标题使用 `onSurface`，两行次级文字使用 `onSurface` 的 `0.8` alpha。传送门模式状态严格按“模块
  未获取 Root → 当前版本未注入到传送门 → 传送门未获取 Root 权限 → 已激活”判断；模块 Root 通过限时
  `su -c id -u` 检测，传送门 Root 由已注入进程在后台执行同一探测后受限上报，注入状态来自版本化 Provider 握手。
- 主页面、可见性页、排序页和无障碍应用黑名单页都使用 `Scaffold` + `MiuixScrollBehavior` + 可折叠
  `TopAppBar`；二级页使用 Miuix `miuix-navigation3-ui` 的 `NavDisplay`、`NavKey` 和装饰后
  `NavEntry`，沿用其默认 500 ms 前进、返回和预测返回动画，并保留返回导航图标；
- 页面入口使用 Miuix `ArrowPreference`；可见性应用组使用 Miuix `Card` + 三态 `Checkbox`，
  Activity 子项使用二态 `Checkbox`；
- `OverlayDropdownPreference` 提供颜色和拖拽样式（简洁、流光、环形、现代）选择；
- `SwitchPreference` 提供文字分享、图片分享、拖拽时阻止背景滑动和四种样式共用的离开关闭开关；
  无障碍模式额外提供默认关闭的“横屏启用识别”。
- 无障碍模式还提供“长按时间”（`250..1200 ms`）和“识别灵敏度”（`50..200%`）滑块；未调整
  长按时间时沿用系统 `ViewConfiguration` 的默认值，灵敏度按默认移动容差的倍率计算。
- `SliderPreference` 提供边缘触发距离、滚动速度和公共图标不透明度。
- 简洁样式额外提供菜单位置（上、下、左、右、近手方向）、背景不透明度、背景圆角和菜单到
  边缘距离。近手方向在
  菜单展开前根据倾斜更新：左高右低显示在右边，右高左低显示在左边；第一次展开会锁定侧边
  并立即注销传感器，本次手势后续即使菜单暂时收起也不会换边。
- 现代样式复用菜单位置（底部菜单两侧保留 `12 dp` 空隙），并提供原生局部模糊半径
  （`0..150 dp`）和 UI 标题为“底板不透明度”的滑块（`68..100%`）。底层仍使用
  `modern_glass_opacity_percent` 表示 Card 玻璃颜色混合；设置变化会通过 Provider Bundle
  传给传送门进程，下一次拖拽直接生效。

主页面的“菜单可见性”和“菜单排序”是独立页面：

- 可见性页先把文字和图片 MIME 能处理的 Activity 合并，再按包名分组。列表结构按 KernelSU
  `SuperUserMiuix` 的搜索结果源码实现：应用大类卡使用正常的 `12 dp` 页面边距，不带左侧缩进
  短条和右侧箭头；父图标为 `48 dp`，文字只显示“已显示 x 个”。点击 Checkbox 以外的卡片区域
  展开/折叠 Activity 子项。子项使用 `6 x 24 dp` 左侧短条、`40 dp` 图标以及更小的标题/摘要
  字号。图标不再直接放大 `ResolveInfo.loadIcon()` 的 Drawable，而是和 KernelSU 一样使用
  `AppIconLoader 1.5.0` 先按 `48 dp` Launcher 规则归一化，再分别放入 `48 dp` / `40 dp` 容器；
  Activity 摘要会移除前置应用包名，只显示包内类名。应用 Checkbox 在全部可见、部分可见、全部隐藏时分别显示 On、Indeterminate、Off；
  点击部分选择会全选，点击全选会全部隐藏。子项 Checkbox 仍可独立控制。内置“复制文本”“复制图片”“保存到本地”和“文本分词”
  位于“内置功能”组。
- 可见性页顶部操作菜单提供“全选/全不选/全展开/全折叠”；排序页使用稳定的 `ComponentName.flattenToString()` 作为键，采用 XiaomiHelper 同款的
  `rememberLazyListState`、`dragContainer`、`DraggableItem` 和 `animateItem`：长按后列表会
  实时换位，靠近可视区域边缘会自动滚动，松手后使用弹簧回弹；顺序在拖动结束时保存。未知的
  新应用会在保存顺序之后自动追加。内置“复制文本”“复制图片”“保存到本地”和“文本分词”不进入可排序应用列表，运行时固定
  置于各自菜单的应用项之前（可见性仍可单独关闭）；在可见性页关闭的目标不会进入排序列表。
   四个内置动作与其他应用一样使用固定图标槽位；设置页和注入进程菜单都使用 `#FF3482FF` 圆角
   瓷片承载模块已有的白色复制、下载和分词矢量。
   排序项拖动时只提升 `zIndex` 和跟随位移，不额外绘制阴影。

可见性页和排序页进入后先提交 TopAppBar 与 Miuix `CircularProgressIndicator` 首帧，再在
`Dispatchers.IO` 查询 PackageManager 分享目标；查询完成后才组合列表，避免导航动画被同步
查询阻塞。首页的 `LazyListState` 与 `TopAppBarState` 提升到导航容器外层，因此进入任一子页
再返回时会恢复原滚动位置和标题折叠状态。

`OverlayDropdownPreference` 必须位于 `Scaffold` 内，否则 Miuix 的弹出选项不会渲染。滑块
只在 `onValueChangeFinished` 时写入配置，避免拖动时频繁写磁盘。Activity 使用
`WindowCompat.setDecorFitsSystemWindows(false)`，系统栏透明；所有页面由 Miuix Scaffold 和
TopAppBar 统一消费 system bars、display cutout 与底部导航栏 insets，实现 edge-to-edge。

“应用黑名单”会随内容获取方式切换入口。传送门模式使用 root 执行
`am start --user current -n com.miui.contentextension/com.miui.contentextension.setting.whitelist.BlacklistSettingActivity`，
从而启动传送门未导出的原生黑名单 Activity；启动失败时只提示错误，不会尝试非 root 的普通
`startActivity()`。无障碍模式使用模块自身的 Miuix 子页面：通过 `QUERY_ALL_PACKAGES` 查询已安装应用，
支持名称或包名搜索并显示归一化图标。当前默认桌面和 `Settings.Secure.DEFAULT_INPUT_METHOD` 指向的
当前输入法始终显示为已加入、禁用开关的内置项；其余应用的开关保存到模块 UID 的设置中。无障碍
模式下首页状态卡也可直接打开系统无障碍授权设置页。

### 7.1 激活状态握手

传送门服务加载 Hook 后立即调用 `report_injected`，extras 携带 Hook 代码编译时的
`BuildConfig.VERSION_CODE`；随后在后台以传送门 UID 执行 `su -c id -u`，通过同一受限调用上报
`portal_root_granted`。Provider 只接受传送门或模块自身 UID，并且只有上报版本等于当前 APK
版本时才写入 `module_activation`。首页只有“已注入 + 传送门 Root 探测通过”才显示已激活；任一
检查未通过时，摘要会补充“如果传送门未启动，该提示为正常现象”。APK 更新但传送门仍保留旧进程时，
旧版本值不会匹配，首页会保持“未注入到传送门”，直到传送门以新模块代码重启。普通 `get_settings`
不更新此标记。

首页检测到 Root 但当前版本尚未握手时，会用 root 执行 `am startservice`，目标仅为
`com.miui.contentextension/.services.TextContentExtensionService`。新服务会在 `onCreate` 上报，
已运行服务则在新增的 `onStartCommand(Intent, int, int)` Hook 中再次上报；设置页最多轮询约
`4.5 s`，以等待传送门的 Root 探测回传后更新状态。因此当前 Hook 已加载时，不需要先手动触发一次传送门。该流程不会调用
`force-stop`，也不会启动或停止 `com.miui.contentcatcher`。若传送门进程仍运行着启用模块前或
旧 APK 的代码，启动命令不会伪造激活状态，仍需让 LSPosed 重新启动传送门作用域。

### 7.2 配置跨进程传递

设置保存于模块 UID 的 `drag_share_settings` SharedPreferences。传送门代码运行在
`com.miui.contentextension` UID，不能直接读取该文件，因此传送门运行时创建、设置变化和每次
`startPick*Task` 前都会通过 Provider 读取：

```text
PortalHooks
    -> ContentResolver.call(content://com.leaf.hyperdragshare.codex.share, "get_settings")
    -> ShareImageProvider.enforcePortalCaller()
    -> DragShareSettings.readLocal() -> Bundle
    -> PortalContentCaptureSource -> DragShareController.show(content, point, settings)
```

Provider 只允许模块自身或传送门调用。读取失败时控制器回退到浅色、简洁样式、`56 dp`、
`560 dp/s` 且不抑制背景的默认值；`saveLocal()` 同时通知 settings URI，两个来源都会取消
活动会话并按新模式重建，不需要重启传送门。

### 7.3 边缘触发距离

边缘距离范围为 `24..200 dp`，默认 `56 dp`。运行时先将 dp 转成屏幕像素，再限制为不超过
屏幕宽度的一半，避免左右触发区重叠。将滑块调到最大时，在常见手机上右侧触发区会接近
屏幕右半边，手指越过中心线不远即可开始向右滚动；左侧同理。

环形样式复用同一个边缘距离设置作为当前方向的触发带宽度。它控制的是圆形菜单确认
区域，而不是线性列表滚动，菜单会在热区内停留 `200 ms` 后展开。四边显示的截图与日志证据见设备记录。

### 7.4 滚动速度

速度范围为 `120..1200 dp/s`，默认 `560 dp/s`。该值定义手指进入距物理屏幕边缘约 `8 dp` 的
内缩满速带时的最高速度；进入热区的起点以 `20%` 速度开始，并按进入深度的平方持续加速。边缘滚动任务仍以约 16 ms 调度，
每帧根据实际经过时间计算 `速度 * 深度倍率 * deltaTime` 的像素位移，并累计不足一个像素的小数，
而不是固定每帧移动 9 dp，因此在不同刷新率和设备密度下更接近用户选择的速度。

### 7.5 悬浮窗深色配色

浅色配色保持原有白色预览、浅灰菜单和深色文字。深色配色使用深灰预览/菜单、浅色文字和
流光样式使用蓝青紫光带和低透明度选中态；底板可读性仍在修正，随 1.7.69 验收。颜色只影响 DragShare 自己的窗口，不改变传送门或
目标应用的主题。
配置在创建新预览时读取，因此已经显示的当前会话不会中途换色。

### 7.6 拖拽时阻止背景滑动

开关默认关闭。开启后，`DragShareController` 在 root 输入源第一次送入活动会话时调用
`BackgroundTouchBlocker.start()`；这样不会让无 root 的 MIUI 回退通道误把合成取消当成拖拽结束。
该类通过 Xposed 反射访问传送门 UID 可见的隐藏
`InputManager.cancelCurrentTouch`，必要时再调用 `InputManager.monitorGestureInput` 与
`InputMonitor.pilferPointers`。成功时系统向原
前台窗口发送取消事件，后续物理坐标仍从 root evdev 旁路读取；DragShare 自己的窗口始终
保持 `FLAG_NOT_TOUCHABLE`，因此不会重新接管触摸。结束、取消、服务销毁和下一次会话开始
都会调用 `dispose()` 释放监视器。

当前 HyperOS 3.0 / API 37 的传送门 UID 未声明 `MONITOR_INPUT`，而无障碍模块进程还会被
Android 17 的隐藏 API 策略屏蔽对应 `InputManager` 方法和 Stub 字段。因此直接
`cancelCurrentTouch()` 与 `monitorGestureInput()` 不能作为可靠路径。阻止器在服务创建后预热，
从当前 ROM 的 `framework.jar` DEX 静态值动态解析
`IInputManager$Stub.TRANSACTION_cancelCurrentTouch`，再以 root 执行输入服务调用；不会写死
Binder transaction code、触摸设备节点或分辨率。root 调用只接受返回的零状态 `Parcel`，并在
成功后记录 `foreground touch stream cancelled through root`。如果 ROM 没有该接口或 root 调用
失败，启动会记录失败并继续原有旁路行为。这个开关不能保证所有厂商输入栈都能冻结背景，实机
应同时观察 `DragShare/InputBlocker` 和 `DragShare/UI` 日志。

### 7.7 目标可见性、排序与内容开关

`DragShareSettings` 新增以下独立键：

| 键 | 含义 | 默认值 |
| --- | --- | --- |
| `content_capture_mode` | `0` 为传送门，`1` 为无障碍 | `0` |
| `text_sharing_enabled` | 是否创建文字拖拽会话 | `true` |
| `image_sharing_enabled` | 是否创建图片拖拽会话 | `true` |
| `ui_style` | 拖拽样式：`0` 简洁、`1` 流光、`2` 环形、`3` 现代 | 现代（`3`） |
| `hidden_targets` | 被隐藏的 Activity/内置动作键集合 | 空集合 |
| `target_order` | Activity 键的用户顺序 | 空（沿用系统顺序） |
| `simple_menu_position` | 简洁菜单位置（上/下/左/右/近手） | 下 |
| `simple_menu_opacity_percent` | 简洁菜单背景不透明度 | `100` |
| `simple_menu_corner_radius_dp` | 简洁菜单背景圆角 | `8 dp` |
| `simple_menu_edge_distance_dp` | 简洁菜单到所选屏幕边缘的距离 | `8 dp` |
| `icon_opacity_percent` | 所有样式的分享目标图标与名称不透明度 | `100` |
| `modern_blur_radius_dp` | 现代预览/菜单 View 的原生局部模糊半径 | `60 dp` |
| `modern_glass_opacity_percent` | 现代 View 底板混合不透明度（最低 68%） | `72` |
| `close_menu_when_pointer_leaves` | 四种样式离开菜单/触发区域后暂时收起 | `true` |
| `accessibility_landscape_recognition_enabled` | 无障碍模式在横屏时是否允许长按识别 | `false` |
| `accessibility_blacklisted_packages` | 无障碍模式用户选择的应用包名集合 | 空集合 |
| `accessibility_long_press_timeout_millis` | 无障碍长按超时；`0` 代表沿用系统默认 | `0` |
| `accessibility_recognition_sensitivity_percent` | 无障碍长按期间移动容差倍率 | `100` |
| `log_level` | `0` 禁用、`1` 信息、`2` 调试 | `1` |
| `log_destination` | `0` 系统日志、`1` root 保护的文件 | `0` |

设置页只写模块 UID 的 SharedPreferences；`get_settings` Bundle 将这些集合传给注入进程，
模块进程的无障碍服务直接读取本地设置。
控制器在查询当前 MIME 的目标后先过滤隐藏键，再按 `target_order` 排序，最后追加本次新发现
的目标。关闭某一种内容时，传送门的对应 `startPick*Task` 不会创建 DragShare 悬浮层，另一
种内容不受影响。

### 7.8 设置兼容与版本迁移

新增键都使用独立 SharedPreferences 键，并在缺失时回退到各自默认值。因此从 `1.5.x` 升级
时仍是浅色、简洁样式、两种内容均启用、所有目标可见；Provider Bundle 同步携带完整配置，
旧设置文件和旧会话不会因升级而改变行为。`1.7.41` 会将旧的 `text_copy_enabled`、
`image_copy_enabled` 和共享的 `builtin:copy` 隐藏状态迁移为 `builtin:copy_text` 与
`builtin:copy_image` 的独立可见性；下一次保存设置时会清除旧的两个开关键。

### 7.9 日志与诊断

首页“关于”前的“日志”区块提供“禁用 / 信息（当前）/ 调试”三级输出，以及“系统日志（当前）/
文件”两个保存位置。默认值为信息级别和系统日志，因此升级不会改变既有 `adb logcat` 排查路径。
禁用后模块自身不再输出运行日志；信息级别保留常规生命周期、分享、输入源和错误记录；调试级别
额外记录每个输入源仲裁、传送门回调、root evdev 原始起始帧与解析帧。

文件模式由模块和传送门进程各自通过其 root shell 追加到
`/data/local/tmp/HyperDragShare/hyperdragshare.log`，目录权限为 root 专用。单进程写入超过
`4 MiB` 时会轮转为 `hyperdragshare.1.log`。设置页的“导出日志”使用系统文件选择器，将当前
root 文件复制到用户选择的位置，不需要扩大存储权限或公开 Provider URI。

调试模式在模块/传送门进程启动、日志目的地切换和 root 输入探测时异步记录：模块与传送门版本、进程/UID、
设备型号、系统版本、ABI、Xposed Bridge API、可见的 LSPosed Manager 包版本、root `id`/`su -v`、
`/proc/bus/input/devices`、`/dev/input` 列表、选中节点的 `getevent -lp` 输出，以及无法发现或
解析触摸节点时的完整 `getevent -lp` 转储。为避免泄露用户内容，日志不记录长按选中的文字、
图片像素、截图或完整分享 URI。

## 8. 文字分享

文字直接构造显式 Intent：

```text
action: ACTION_SEND
type: text/plain
component: 用户落点对应的 Activity
extra: EXTRA_TEXT
flags: FLAG_ACTIVITY_NEW_TASK
```

使用显式组件可以绕过二次系统选择器，直接进入拖拽菜单选中的目标。

## 9. 图片分享与“文件不存在”问题

传送门中的 Bitmap 只存在于传送门进程内存，接收应用既不能读取这个对象，也不能读取传送门
或模块的私有文件路径。直接传路径、`file://`，或只设置一个没有正确授权的
`EXTRA_STREAM`，都会表现为“图片不存在”“读取失败”或接收端无内容。

JADX 对传送门 5.1.5 的复核结果是：`viewBitmap` 来源直接赋给静态 `sBitmap`；矩形截图编辑
路径由 `RectScreenshot.getPartialBitmap()` 创建选区宽高的全不透明 Bitmap，再由
`DrawShapeUtil.getResultBitmap()` 截取同一选区像素。`SaveImageUtils` 保存时把该 `sBitmap`
原样传给 `BitmapUtils.saveBitmapToStorage()`，最终由 `FileUtils.cacheBitmap()` 直接压缩为
PNG。保存阶段既不扫描/裁掉透明边，也不把裁剪结果拉伸回原分辨率。

旧模块产生“像素宽高相同，但右侧和下侧多出白边”的原因在 JPEG 转换阶段。ARGB Bitmap 的
`hasAlpha()` 表示它支持 alpha，并不表示边缘实际透明；旧实现因此新建了同宽高白底 Canvas，
再用会考虑 Bitmap density 的 `drawBitmap(bitmap, 0, 0, paint)` 重绘。源 Bitmap 与新画布
density 不一致时，内容会在固定尺寸画布内缩放，未覆盖的右、下区域露出白底。目标 Bitmap 的
宽高从未改变，所以文件分辨率仍与输入相同。当前链路直接压缩源像素，不再经过这次重绘。

图片菜单的第一项不走接收应用 Intent，而是调用 `LocalImageSaver`：Android 10 及以上通过
`MediaStore` 的 `RELATIVE_PATH=Pictures/HyperDragShare` 写入并在完成后清除 `IS_PENDING`；
更旧系统写入同名 Pictures 子目录并触发媒体扫描。文件名由本地时钟格式化为
`yyyyMMdd_HHmmss.png`，精度到秒；同一秒在旧系统上冲突时追加短序号避免覆盖。保存项直接在
工作线程把本次会话持有的原始 Bitmap 无损编码为 PNG，与传送门 5.1.5 的
`FileUtils.cacheBitmap()` 保存格式一致。PNG 也会保留图片原本存在的透明像素，但本问题中的
白边并不是透明边转换而来。宿主的 `cancelTask()` 只清空静态 Bitmap 引用而不回收对象，
会话和保存线程仍持有强引用，因此无需等待跨 UID 暂存 URI。

外部应用分享、图片剪贴板和跨 UID 暂存仍分为四步。

### 9.1 通过管道无损编码

图片暂存工作异步执行。`ImageStagingClient` 创建 `ParcelFileDescriptor` 管道，由独立编码线程
把源 Bitmap 直接写为 PNG，Provider 在另一端边读边落盘。`BitmapEncoder` 只会：

- 把 hardware Bitmap 复制为 `ARGB_8888`；
- 调用 `Bitmap.compress(PNG, 100, stream)` 写出源像素；
- 保持原宽高，不新建白底 Canvas，不按质量或边长降采样。

管道只通过 Binder 传递文件描述符，PNG 字节本身不进入 Bundle，因此不受旧 `700 KiB`
transaction 预算限制。Provider 对单个缓存文件设 `32 MiB` 上限。为兼容 APK 更新后尚未
重启的旧传送门进程，Provider 仍可接收旧的 JPEG byte-array RPC，并按 `.jpg` 暂存；新代码
不会再生成该格式。

### 9.2 跨 UID 暂存

Hook 代码运行在传送门进程，不能把图片留在传送门私有缓存后直接交给第三方。
`ImageStagingClient` 用 `ContentResolver.call()` 调用安装在模块 APK 中的
`ShareImageProvider`。Provider 在模块 UID 的 `cache/shared-images/` 中先写 UUID `.tmp`，
`fsync` 后原子重命名为 `.png`，返回：

```text
content://com.leaf.hyperdragshare.codex.share/shared/<UUID>.png
```

升级前已生成的 `.jpg` URI 和更早的无后缀 JPEG URI 仍可按原 MIME 与文件名读取。

暂存时会顺便清理最后修改时间超过 24 小时的旧图片并撤销授权。清理是“下一次暂存时”触发，
不是精确的 24 小时定时任务。

图片尚未暂存完成就松手时，会话保存待分享目标并提示“正在准备图片”；异步暂存完成后再启动
目标应用，而不会丢掉这次落点。

### 9.3 同时满足不同接收端

图片 Intent 同时设置：

```text
ACTION_SEND
type = image/png
data = content URI
EXTRA_STREAM = content URI
EXTRA_TITLE = drag-share.png
ClipData = content URI
FLAG_GRANT_READ_URI_PERMISSION
FLAG_ACTIVITY_NEW_TASK
显式目标 ComponentName
```

此外，Provider 以文件所有者身份对目标包调用一次 `grantUriPermission()`。同时设置 data、
stream、ClipData、flag 和显式 grant 看似重复，但 QQ、系统分享代理及其他应用读取 URI 的入口
并不一致，这组组合是实机兼容所需。

Provider 还实现了 `getType()` 和 `query()`，为新 URI 返回 PNG MIME、显示文件名和大小，
满足会在打开文件前先探测元数据的接收端；旧 JPEG URI 仍返回 JPEG 元数据。

分享诊断写入统一的 `DragShareLog` 文件，因此设置页导出的日志包含 `prepare`、脱敏后的
Intent URI 摘要、授权结果、`startActivity succeeded`，以及 Provider 收到的 `query`、`getType`、
`open` 或对应失败异常。能力 URI 的 UUID 文件名不会写入日志；`query` 和 `getType` 只在调试等级
记录，关键的启动、授权和文件打开结果在信息等级也会保留。

### 9.4 授权兼容与安全边界

早期实现曾在 `openFile()` 内额外执行 `checkUriPermission()`。部分 HyperOS 分享组件或目标
应用会先由被授权 Activity 接收，再交给另一个 UID/进程处理，并在二次转发中丢失 Intent
grant。URI 和文件实际存在，但 Provider 因调用 UID 不匹配拒绝读取，于是 QQ 显示“图片不
存在”，其他应用出现 `java.lang...` 读取异常。

最终方案仍向选中包提供标准 URI grant，但 `openFile()` 不再做第二次权限检查。安全边界
改为能力 URI：UUIDv4 是 128 位标识，其中约 122 位为随机信息，未持有完整 URI 的调用者
无法现实地枚举文件名；路径不提供目录列表；旧文件按 24 小时阈值机会清理。创建、授权和撤销
的 Provider RPC 仍通过调用 UID 校验，只允许模块自身或传送门。

这个取舍意味着：任何拿到完整 URI 的进程都可在文件被清理前读取图片。这正是兼容二次转发
所需的 bearer-capability 语义。不要重新加入严格的 `checkUriPermission()`，除非同时解决
跨 UID 转发并用独立 UID 接收器验证 QQ 等真实链路。

## 10. 会话结束与资源清理

- root ACTION_UP：先回放暂存的宿主 `cancelTask` / `257` 原方法，使传送门任务可再次触发；
  随后更新最后落点、停止边缘滚动，并在预览/菜单/光效窗口仍附着时请求启动选中目标。普通预览
  再以约 `160 ms` 淡出并略微缩小；现代预览由其独立窗口在约 `220 ms` 内淡出。无障碍服务在
  HyperOS 上失去最后一个可见 `TYPE_ACCESSIBILITY_OVERLAY` 后可能被静默拒绝后台启动，因此外部
  分享和文本分词都必须保持这个启动顺序。
- ACTION_CANCEL：取消会话并使用同一预览淡出路径；Root 路径稳定生成 DOWN/MOVE/UP/CANCEL，停止输入
  或多指介入绝不伪造会触发分享的 ACTION_UP。新的拖拽、窗口创建失败和服务销毁会立即移除尚未
  动画结束的旧预览，避免两个悬浮窗短暂叠加。
- 传送门服务销毁：停止输入线程和 `su cat` 进程，销毁当前会话。
- 启动目标失败：记录公共运行时日志、撤销该目标的 URI grant，并显示“无法打开 <名称>”。
- 命中图片“保存到本地”：无需等待暂存 URI，独立工作线程把会话原 Bitmap 以 PNG 写入
  Pictures；成功或失败均回到主线程显示 Toast，不阻塞传送门的手势线程。
- 命中“复制文本/复制图片”：文字立即写入剪贴板；图片等待同一暂存 URI 后写入图片 `ClipData`，成功或失败均显示 Toast。
- 分享成功：文件保留到后续机会清理，便于目标应用延迟读取。

## 11. 无障碍内容获取

`DragShareAccessibilityService` 不声明独立进程，并且只在当前模式为“无障碍”、屏幕可交互且
设备未锁定时启动运行时。横屏默认保持 Root 输入运行时但拒绝创建长按识别，只有开启
`accessibility_landscape_recognition_enabled` 才允许横屏识别。服务连接状态和 Root 输入就绪状态由 `AccessibilityRuntimeStatus` 提供
给首页状态卡；选择无障碍且服务未启用时，首页只显示 Miuix 对话框并跳转系统无障碍设置，绝不
写入 `Settings.Secure` 或静默启用服务。

无障碍事件只记录窗口变化代号和前台包名。物理手指由 Root evdev 驱动：DOWN 后使用系统
`ViewConfiguration` 的长按超时和 touch slop；超时后仅执行一次窗口/节点读取；MOVE 在截图
等待期间只更新最新手指位置；UP、CANCEL、模式切换、屏幕关闭或服务销毁都会使该 gestureId
失效。预览和菜单继续是 `FLAG_NOT_TOUCHABLE`，无障碍模式仅将窗口类型切为
`TYPE_ACCESSIBILITY_OVERLAY`。

长按超时后，服务先依据窗口根节点包名过滤无障碍应用黑名单，再读取该窗口节点树；候选节点
选出后按候选包名再次校验，避免异常窗口树绕过过滤。捕获完成并确认 root 物理手势仍处于
活动拖拽后，服务会在同一主线程立即启动背景锁，不会把该操作排在后续 root ACTION_UP 之后；
最终结束仍只接受真实 root ACTION_UP。用户黑名单与动态内置黑名单一起生效：
内置项包括当前默认 HOME 启动器（桌面）和当前默认输入法，均不写入可编辑集合，且随系统选择
变化立即重新解析。黑名单过滤不在 `onAccessibilityEvent()` 中遍历节点或读取文字。

无障碍的长按超时默认取系统 `ViewConfiguration.getLongPressTimeout()`，用户可用设置页滑块覆盖为
`250..1200 ms`。识别灵敏度默认 `100%`，在 `50..200%` 范围内将原有
`max(2 * scaledTouchSlop, 16 dp)` 容差等比缩小或放大；更高值更能容忍长按期间的细小位移。
这两个参数变更时服务会取消当前手势并只重建无障碍 Root 运行时，使下一次 DOWN 立即使用新值；
其他设置变更不为此重启输入源。

1.7.1 针对部分 HyperOS 设备补充了三项兼容处理：

- Controller 使用 `AccessibilityService` 自身的 Context 创建 WindowManager，而不是普通
  application Context；这样 WMS 能以 `CREATE_ACCESSIBILITY_OVERLAY` AppOp 建立覆盖层。
- 窗口根节点不再要求自身 bounds 必须包含触点。部分 ROM 的根 bounds 为空或只覆盖内容区，
  但子节点 bounds 正常；窗口本身已通过 `AccessibilityWindowInfo` 过滤后再遍历。
- 长按前的 touch slop 提高到系统值的两倍（至少 16 dp），减少 Root 坐标抖动造成的提前取消。

这台实机的验证轨迹（2026-07-19，Android API 37）为：

```text
gesture=9  selected=TEXT bounds=98 1678 1122 1886
gesture=10 selected=IMAGE_REGION bounds=65 867 360 1590
gesture=10 screenshot captured size=301x729
overlay added kind=TEXT/IMAGE
```

覆盖层由系统记录为 `appop=CREATE_ACCESSIBILITY_OVERLAY`、
`type=ACCESSIBILITY_OVERLAY`、`NOT_FOCUSABLE|NOT_TOUCHABLE`，不需要
`SYSTEM_ALERT_WINDOW` 或“在其他应用上层显示”授权。诊断轨迹仅保存在应用私有的
`files/accessibility-trace.log`，只记录手势状态、坐标、区域和异常类型，不保存文字或像素。

1.7.2 修复图片“保存到本地”成功但看不到 Toast 的问题：保存过程中不再额外排队“正在保存
图片”，每次显示前取消上一条 Toast，且最终成功/失败提示使用应用 Context 和较长显示时长。
1.7.3 进一步处理该设备对后台 Service 系统 Toast 全部抑制的情况：所有 DragShare 提示统一
使用不可触摸的临时 overlay 文本层；无障碍模式使用 `TYPE_ACCESSIBILITY_OVERLAY`，传送门模式
使用 `TYPE_SYSTEM_DIALOG`，系统 Toast 仅作为失败回退。保存文件和命名规则不变。
1.7.4 新增按来源分流的应用黑名单：传送门跳转其 root 启动的原生页面，无障碍提供可搜索的
本地应用页；默认锁定当前桌面和默认输入法，并新增默认关闭的横屏识别开关。
1.7.5 为无障碍模式新增可调长按时间和识别灵敏度，默认保持系统长按时间和原有移动容差。

节点遍历会忽略模块自身覆盖层、不可见/越界/密码节点和非默认显示器窗口，最多读取 4000 个节点、
64 层或约 120 ms。分类先处理输入框和文字，再处理 WebView 暴露子节点，最后才处理显式
`ImageView` 或受 20 dp 和文字覆盖过滤约束的通用非文字叶子。日志只记录手势号、坐标和候选计数，
不记录文字、描述、图片像素或完整 capability URI。

图片在显示悬浮预览前截图：API 30+ 使用 `AccessibilityService.takeScreenshot()`，先复制
`HardwareBuffer` 中的目标区域为软件 `ARGB_8888` Bitmap 后关闭 buffer；API 28/29 使用内存中的
`su -c /system/bin/screencap -p` 回退。候选 Rect 会扩展 1 dp、按截图比例映射并夹取到 bitmap
边界；无法可靠映射、密码/安全窗口错误或提前抬手都会直接取消，不退化为整屏分享。

## 12. 构建与验证

在 `E:\workspace\DragShare` 执行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

现有单元测试覆盖：当前版本注入握手与限定服务启动命令、传送门原浮窗在模块预览挂窗前的
抑制、底部触发边界、左右滚动方向、边缘深度速度渐变、
预览位置夹取、流光进度与项目缩放、近手方向映射、环形菜单边缘触发/贴边面板/自然项目顺序、
新增外观设置和现代原生局部模糊参数的默认值/范围裁剪、原生 Window 局部模糊的圆角背景/降级判定、现代预览按文字与图片尺寸自适应并限制为屏宽三分之一、独立 Compose 悬浮窗的 ViewTree owner 传递、原始坐标与旋转映射、
简洁菜单四边触发区边界、触摸设备发现、UUID URI 解析、设置默认值/范围/内容开关/目标规则以及本地图片时间文件名；
新增测试还覆盖内容获取模式迁移、evdev DOWN/MOVE/UP/CANCEL、长按异步失效、节点文字/图片
候选优先级、截图区域的扩边/缩放/夹取、日志等级/保存位置的 Provider Bundle 同步，以及背景锁开关的 Provider Bundle 同步、root 服务返回
解析、当前 ROM DEX 的动态 Binder 事务解析与 `InputMonitor` 释放。中转站测试覆盖持久化和图片删除失败回滚。测试数量与最近一次执行结果以 [DEVICE_VALIDATION.md](DEVICE_VALIDATION.md) 为准。传送门私有 Hook、WindowManager、root evdev、InputMonitor 权限、菜单可读性、多图/混合分享接收和 Miuix 页面视觉仍须按设备记录逐项验收；搜索面板测试覆盖默认引擎、UTF-8 查询编码、HTTPS 地址识别与 scheme 拒绝。

系统日志位置下建议使用：

```powershell
adb logcat -c
adb logcat -v time | Select-String "DragShare|AndroidRuntime"
```

关键正常日志顺序大致为：

```text
DragShare/RootInput: ready device=...
DragShare/Taplus: root input is authoritative; ignoring MIUI motion events
DragShare/UI: preview shown kind=...
DragShare/UI: input source=root ...
DragShare/UI: menu shown ...
DragShare/UI: gesture finished source=root ...
DragShare/Taplus: replayed host call=...
DragShareProvider: staged ...
DragShareProvider: granted ...
DragShareProvider: open uid=...
```

若再次出现“移动一点就消失”，先查结束日志来自 `root`、`miui`、control 还是 host cancel；不要
先假定是 WindowManager 限制。若图片失败，按 `staged -> granted -> startActivity -> open`
顺序判断是压缩/暂存、Intent 启动还是接收端读取问题。若同一应用只能触发一次，检查每次
真实 UP 后是否同时出现 `deferred host call` 和 `replayed host call`；永久吞掉 `257` 会让
传送门的 `sIsTaskFinished` 保持旧状态，只能靠焦点切换重置。文件位置下在首页选择“导出日志”；
对大屏输入异常，优先查看 `RootInput: ready` 后是否出现 `decoded ACTION_DOWN/MOVE`，以及同一文件中
`DragShare/Diagnostics` 的候选节点、`/dev/input` 和 `getevent -lp` 转储。

## 12. 已知限制

- 跟手输入的可靠路径需要 root；MIUI 回退是否可用取决于 ROM。
- 默认旁路读取不消费原页面手势，因此原页面仍可能滚动；背景锁开关依赖 ROM 的输入 Binder
  是否仍提供可由 root 调用的取消接口，不保证所有设备都能生效。
- 流光样式使用 Canvas 重建 `glow_effect.coz` 的视觉层，素材本身属于参考 APK，未复制进模块；
- 环形样式的纵向跨度以 `300 dp`、项目半径以 `112 dp`、项目尺寸以 `68 dp` 做屏幕自适应；
  屏幕内横向深度仅约跨度的一半。MCP 资源表在当前 JADX 会话不可导出，因此没有复制 Oplus 私有 drawable。
  不同屏幕密度下的光带高度和托盘间距按 dp 动态计算。
- MCP 参考中的整屏倾斜/SurfaceControl 动画暂未实现；光效、托盘分段展开、预览进入和项目
  距离缩放已保留。
- 当前按第一个有效 MT slot 跟踪，不保证复杂多指切换体验。
- 内置保存、外部分享和图片剪贴板都使用原尺寸 PNG；跨 UID 缓存的单文件上限为 `32 MiB`，
  超限时会失败，不会静默改成 JPEG 或降采样。
- 传送门私有类和字段绑定 5.1.5，升级传送门后可能需要重新适配。
- Provider 的能力 URI 优先兼容分享中继；完整 URI 在缓存清理前应被视为可读取凭证。

### 1.7.85：补齐图片识别入口的原页窗口（未安装）

外部 ACTION_SEND 图片接收页改为透明中转，不再绘制应用内 OCR 页面。临时读取授权仍有效时将图片复制到私有缓存，再关闭接收 Activity，由 ImageOcrOverlay 显示进度、错误与分词结果。限制32 MiB，后续接收时清理超过24小时的输入缓存。外部分享仍短暂启动透明 Activity，不能声称整个流程零 Activity。

拇指取图和传统分词页的重新识别直接调用 ImageOcrOverlay；与已修改的拖拽识别共用同一路径。缺少悬浮权限显示明确提示，不回退应用页面。面积数据为零的设备问题未解决。本轮未操作设备，安装版本仍1.7.80；返回原应用、授权生命周期和所有入口的实际浮窗效果待集中实测。

### 1.7.87：保留搜索浏览进度（未安装）

搜索页保存查询、搜索引擎和WebView浏览历史，Activity重建后恢复；恢复成功后标记当前导航请求已处理，避免Compose更新立即把结果重置到搜索首页。WebView无法恢复时明确提示重新搜索并记录原因。主题控制器改为remember，避免输入及网页导航状态变化时反复创建。浏览器启动仅处理ActivityNotFoundException和SecurityException，并记录失败，移除catch Throwable。

现有151项单元测试、Lint和assembleDebug通过，无新测试基础设施。WebView历史恢复未被现有单测覆盖，旋转/进程重建、前进后退和真实网页仍待真机验收。本轮未操作设备，安装版本仍1.7.80；源码1.7.87（111）。搜索仍使用既有透明Activity，不宣称已改为独立搜索浮窗。

### 1.7.89：直接框选识字入口（未安装）

新增可手动添加的区域识字快捷磁贴，与原有屏幕识字共用启动及截图逻辑。点击后只截取一次画面，先展示区域选择，再对所选区域运行当前OCR引擎，省去原来先整屏识别再框选的步骤。区域入口成功识别后打开分词浮窗并结束截图Activity；未授权浮窗时提示并保留结果页供复制。取消初始框选直接退出。磁贴PendingIntent使用独立action，避免与整屏入口互相覆盖参数。

复用现有裁剪映射/选区UI与ML Kit、AICR路径，不增加系统作用域。截图与识别错误记录原异常并在页面显示原因。框选/进度仍是既有Activity，此版不是整条链路零Activity。设备仍1.7.80，用户暂缓测试，本轮没有设备操作。待验：控制中心收起时截图内容、两磁贴分别进入正确流程、选区坐标与结果浮窗、取消和截图释放。

### 1.7.92：传送门上下文保留与Root截图截止时间（未安装）

根据已保存的传送门5.1.5静态证据，PortalContentCaptureSource现在读取getInjectorPackage/getContentRect，随文字或自有图片副本保存到CapturedContent；不再把包名与区域丢弃。读取失败明确记录、提示并返回，让宿主原行为继续；移除文字读取失败后静默尝试图片的混用与空Throwable捕获。初始触点读取也改为明确方法/调用错误日志。尚未把来源区域传入分词面板或完成附近OCR，不宣称主路线已实现。

RootScreenshotter将stdout/stderr分别排空，同一4秒截止时间覆盖进程退出和两路输出；错误流最多保留8KiB，继续排空但不混入PNG。取消保留中断标志；进程清理异步执行，不让管道关闭延长调用者等待。异常包含退出码及错误输出。4秒不覆盖ProcessBuilder.start或图片解码；也不证明设备Root派生进程清理已实测。

新增测试针对进程退出后stdout/stderr仍未结束、取消中断与stderr诊断。没有设备操作，目标机仍1.7.80。后续集中验证真实截图、失败提示、传送门捕获并继续Root主路线附近识字。

### 1.7.93：传送门＋Root主动追加屏幕文字（未安装）

独立分词窗口更多菜单新增追加屏幕文字。保存原文字、词边界和选词，启动独立区域截图流程后关闭分词浮窗；Root截图后由用户框选当前画面，使用当前所选ML Kit/AICR引擎。结果确认可追加全部或单行，后台只分词新文字并拼接旧边界，重开原分词会话；取消返回旧会话。启动/分词错误明确提示，保存的内容不因失败被丢弃。旧请求返回时不会替换后来打开的新分词会话。

该功能不依赖无障碍，传送门文字进入的独立窗口可直接使用。它是用户确认的屏幕区域追加，不是自动定位原触点的邻段列表；不使用可能因滚动失效的旧坐标猜文字。现有上下拉静态邻段仍仅适用于无障碍快照。传统TextSegmentationActivity没有此入口。截图选区与结果确认复用Activity，不宣称全流程零Activity；原面板退出到截图的画面时序仍待真机证明。

用户暂缓设备测试，本轮没有安装或截图。重点待验：原应用仍在截图背景、取消恢复选词、追加后旧合并/拆分不变、单行/全部确认、两种引擎失败、锁屏/返回及新会话覆盖旧请求。该会话恢复仅在模块进程存活时有效。

### 1.7.94：原文区域参考框（未安装）

Controller把已有sourceBounds与当前屏幕尺寸经受限Provider传入分词窗口；搜索返回和追加取消保留这一来源信息。主动追加屏幕文字时传给截图选区页，尺寸一致才将原文范围映射为蓝色虚线参考框；裁剪到截图范围，不将其当成用户选区，不自动OCR或追加。页面明确提示旧位置可能因滚动变化，用户重新框选所需文字。该范围来自系统已有view_rect，不是重新取得的文字节点，亦不能证明自动附近识字已完成。

没有操作设备。待验证真实view_rect坐标系、状态栏/横屏映射、页面滚动后的提示、搜索返回与取消追加后的来源保留。源码可编译不代替这些UI证据。

### 1.7.98：中转站侧边条与段落间距

用户要求中转站像侧边条收起。删除收起状态的60×88dp卡片，使用24×96dp透明点击/拖动区域和5×64dp贴边细条；点击展开、拖动吸边，保留位置持久化。复制成功也收起，错误留在面板；分享和原生拖出继续在成功启动后收起。关闭移除入口，收起保留入口，返回先结束预览或多选。

1.7.97真机图片识别已证实桌面上独立分词浮窗，Root追加取消可恢复，中转站换边和全选可操作。该次截图暴露每个换行额外54dp空白，现改8dp，不更改原文或分词。新改动待安装验证，具体证据和未验项目见DEVICE_VALIDATION.md。

### 1.7.111：统一系统主题与实际AICR客户端实验

DragShareSettings新增COLOR_SYSTEM=2，0/1及默认浅色保持不变。Java按当前uiMode统一解析，Compose通过LocalConfiguration更新并按dark重新创建ThemeController；设置、分词、中转站、OCR、搜索、校准和菜单共用。原生View菜单只保证创建/重绘时读取当前模式，未添加常驻监听。新增主题存储/Provider往返和模式解析测试，合计172项。

debug AicrOcrProbe移除重复Binder协议，调用生产AicrOcrClient，生成中文混排样本、绑定前取消后复用，以及ImageOcrOverlay实际结果入口。目标机普通UID中文成功，原页38字符浮窗和浅深浅即时换色已验；具体耗时、复原设置与剩余范围见DEVICE_VALIDATION.md。生产AICR协议本批未变。短文本居中与旧词块行高问题仍需下一批修正。

### 1.7.112：紧凑词块与选词栏模式修正

去掉BoomChipPage按剩余高度自动添加对称padding的居中逻辑，让正文从滚动区顶部开始；保留28dp滚动内边距和30dp工具栏预留。词块与标点改用无固有高度的圆角shape，分别显式设置9dp/4dp水平padding以匹配现有18dp/8dp宽度计算，最小高度32dp，行外padding由5dp降为2dp；段落间8dp保留。删去不再需要的居中回调和缓存字段。

真机首次选词发现工具栏遮挡：onScrollChanged未判断mEnableFakeSelectBar，仍在现代面板启用旧的33dp顶部固定备用栏。现尊重模式开关，不更改真实工具栏坐标公式，也不增加另一组偏移兜底。设备最终验收见DEVICE_VALIDATION.md。本对话持续测试授权已同步到AGENTS.md，其他对话不继承此授权。

### 1.7.113：内容自适应窗口与原页搜索

分词窗在竖屏普通悬浮状态按原生文字容器实际高度、table/scroller内边距及104dp顶底栏+8dp正文间距计算自然高度，以原45/60/80%设置为上限。短文本缩小，长文本在上限内滚动；不按字符数估高度，不改变旧偏好值。布局监听随Compose窗口销毁解绑。横屏/系统多窗口仍沿用既有满窗口策略，本批未改。分词未完成时标题显示“正在分词”，编辑、全选、更多暂停响应，避免空结果控件。

搜索/词典/翻译从BigBangOverlayLauncher直接进入SearchOverlayWindow；原分词会话保留，关闭后原选择继续。SearchOverlaySession承载唯一WebView/Compose实现；SearchOverlayActivity只保留旧入口兼容，不复制两套业务。键盘优先处理返回；息屏/来源移除释放窗口及WebView；失败保留原面板并显示原因，不以Activity兜底。普通分享的传统TextSegmentationActivity和屏幕OCR的Activity承载仍未收敛，不宣称整个项目只剩设置Activity。

真机发现有道旧fanyi.youdao.com链接不带入文字。改为已在目标WebView验证的官方m.youdao.com/translate?inputtext=，UTF-8编码保留。产品按钮最终将“传送门”带入并显示“Portal”，不靠JS注入、复制代码或多级回退。源码与设备验收细节见DEVICE_VALIDATION.md和SEARCH_OVERLAY.md。


### 1.7.117：Root明确图片节点识字

RootNodeCaptureMain按内容开关返回文字或明确图片节点，图片等待完整密码检查；RootNodeCaptureClient截屏后重验来源窗口/锁屏/完整显示尺寸，裁剪成自有Bitmap。PortalThumbCaptureSource复用ImageStagingClient与recognize_image_window，回调后回收Bitmap、过期请求不打开窗口。未新增作用域或Activity回退。完整显示尺寸使用WindowManager，修正首轮资源DisplayMetrics与整屏不一致导致拒绝的问题。

178项单测、Lint、构建通过，最终141安装/Portal重新加载；生成图片真实Root取图1392×773→AICR→83字符独立分词通过，关闭恢复原页。该实验未覆盖真实面积或原长按接管，图片完整扫描在复杂页仍可能超截止时间；零尺寸问题和115旧ANR均未解决。具体证据及误识字符见DEVICE_VALIDATION.md。


### 1.7.118：二维码原页窗口与剪贴板限制

ImageOcrOverlay新增明确QR模式，复用解码、窗口、Miuix及生命周期；QrImageDecoder离线解码多码并去重。图片无文字/失败仍能扫码，带图分词更多入口保留原窗口，二维码结果支持复制与原页搜索/HTTPS。未加相机、区域扫码或作用域，完整范围见QR_IMAGE.md。

真机纯QR正确解出HTTPS，原页链接及关闭返回通过。发现目标机UID WRITE_CLIPBOARD=foreground导致普通浮窗写入静默拒绝；Root UID0实验也不能写，UID1000系统上下文写及独立UID读取通过。新ClipboardWriter检查原始AppOp，受限时只采用已验证Root调用，stdin传正文、5秒后台截止、Root严格核对；扫码UI显示复制中/确认/失败，避免被禁用Toast掩盖状态。最终不同生成标记→产品复制→独立读取false变true，181项单测、Lint、构建及142安装/Portal握手通过。其它旧复制入口尚未迁移，下一批优先修正，不宣称全局已解决。


### 1.7.119：文字复制统一

BoomActionHandler把复制交给PanelSession，分词可见反馈与成功后退出沿用原偏好；屏幕OCR、中转站管理/侧窗和拖拽文字都使用ClipboardWriter。异步成功后才触发退出/收起，失败保留当前数据，已销毁窗口不处理迟到UI回调。图片分支尚未接共享Writer，不能认为无异常等于成功。

181项单测、Lint、构建及143安装/Portal握手通过。分词全选COPY-119、侧窗COPY-119-STASH、真实磁贴入口Root屏幕OCR单行COPY-119，三个场景均由独立UID严格读回匹配。临时磁贴完整列表恢复；测试记录8→7清理，原数据保留。系统复制编辑浮层覆盖一次关闭区域的共存问题仍待处理，具体误点与未验范围见DEVICE_VALIDATION.md。

### 1.7.120：图片复制与独立快照

ClipboardWriter.copyImage将私有StashImageProvider图片流复制到现有ShareImageProvider，已有分享URI直接使用。stage_image写临时文件后仅解码bounds确定实际MIME，按平台MimeTypeMap取得安全扩展名，不重编码；32MiB上限、24小时机会式清理保持。ShareUriToken支持规范UUID与1–8位小写字母数字后缀，保留裸UUID旧JPEG；格式未知明确报错，不猜PNG。

System UID临时进程没有AMS应用记录，不能通过ContentResolver查询Provider，也不能作为剪贴板URI授权来源。图片Root分支固定启动Shell UID2000，明确MIME构造ClipData，通过目标ROM实测IClipboard签名/com.android.shell归属写入、严格读回URI，让系统正常向接收者授权。文字保留System UID。stdin传内容，后台5秒Root截止，不改权限/可见性策略或堆回退。固定主用户和本机剪贴板，其他用户/虚拟设备未支持。

侧窗、系统小窗、拖拽图片接统一完成回调，失败保留内容，成功才反馈/收起。182项单测、Lint、构建通过；144原生小窗/侧窗产品按钮到独立UID字节一致，删除中转站原图后副本仍一致。JPEG仅单测通过，真机其他格式及拖拽菜单图片入口未验，不能用公共Writer通过替代。具体失败与成功证据见DEVICE_VALIDATION.md。

### 1.7.121：主动点取原页内容

新增控制中心“点取内容”磁贴，透明Activity只作立即结束的入口桥；模块独立窗口显示底部提示条并接收一次显式点击。准备只连接Root，点选撤窗后下一帧才读节点；文字复用分词与来源区域，明确图片复用节点裁剪、暂存和既选OCR引擎。取消/返回释放连接；权限、配置及独占接口失败显示实际原因。内容全关或方向禁用不连接、不显示无效重选。没有新增作用域、常驻服务或整屏OCR兜底。

Root helper仅在serve连接的反射/权限边界返回结构化启动错误，保留别的UiAutomation会话。面积与点取跨进程共享尚未实现。另修复evdev本帧轴先于接触开始时被清零、面积确认后移动/缩小未立即作废迟到结果的错误；4个回归用例，186项测试、Lint及构建通过。

145真机通过生成文字8字、图片AICR83字原页分词、bilipai提醒标题8字、返回取消与真实占用错误。最终构建233确认配置禁用不连接且只显示关闭，设置已恢复；其它正向路径在同代码构建232验证。评论区、面积输入、旋转/息屏等不能据此标为完成。详见POINT_CAPTURE.md和DEVICE_VALIDATION.md。

### 1.7.122：现代菜单换边清理

repositionMenu在丢弃旧现代菜单引用前，取消退出回调并直接removeOverlayView/dispose；避免新菜单show取消旧窗口唯一清理机会。保留同边离开/重入动画与原系统手势，不增加兼容分支或回退。debug-only MenuLifecycleProbe用生成文字与模块悬浮窗复现旧对象未disposed，修复后右/上/左换边、离开、底部重开和取消全部通过。它不模拟系统输入、不读取原页面，也不证明传送门触发或真实分享。

186项单测、Lint和构建通过，146安装/Portal与Root握手通过，设置未改。具体失败对照、APK哈希和未验收范围见DEVICE_VALIDATION.md。

### 1.7.123：图片导入批次与紧凑取用

StashStore schema2持久batch_id/batch_order，addImages一次事务共用批次和时间，输入顺序单独记录。单项和旧库记录保持独立，不按时间窗口推测归组。侧窗按组显示首图和数量，直接长按用既有多项ClipData拖出；点开后按原顺序显示单条，复用预览/复制/多选删除。返回恢复全部组，删空组自动退出。缩略图使用Fit以完整显示边缘，保持原侧窗面积。完整管理/系统小窗保留平铺，不新建组模型或重复存图片。

187项测试、Lint、构建及147/Portal/Root通过。生成组实际展开、系统2项拖拽启动、重开和删空返回已验；独立接收器安装受限，未验证本组跨UID接收或外部分享授权。原7条/4图升级前备份与清理后哈希一致。schema1 APK降级需要配套数据备份，见STASH.md。

### 1.7.124：系统小窗图片组与固定操作区

StashBatchCard和stashBatches由侧窗/系统小窗/管理共用，选择输出保持组内原顺序。StashActivity独立保存当前batchId，侧窗放大通过已有Root系统小窗命令传入该内部UUID；返回先退组，再收起任务；查询/类型切回全局，删空返回父列表。SavedInstanceState显式null优先于旧Intent，避免返回后重建又重新打开原组。数据库保持schema2。

窄窗默认只显示内容，搜索按需展开。共享StashSearchInput自动聚焦，使用Miuix Card/色板；原生窄窗把搜索、筛选及多选操作固定在不透明标题区，修正焦点/旧锚点滚动到标题背后的原因。查询、类型或组变化请求首项，不堆延迟重试。小确认框全宽上下按钮，预览缩短；普通图片Fit，删除冗余预览/分词按钮，正文点击保留同一动作。宽管理页仍用SearchBar。

187项测试、Lint、构建及148安装/Portal/Root通过。实际产品放大保留组、freeform整组拖拽启动、搜索IME/Back、清空回首项、固定多选和2项删除清理均有对应证据；原7条/4图哈希保留。跨UID接收、宽页面/深色及新单条预览/删除弹窗未全部验收。详见DEVICE_VALIDATION.md。
## 2026-10-21：1.7.125 游戏内暂停自动触发

新增默认关闭的 pause_automatic_in_games，SharedPreferences/受限设置Provider/SettingsScreen使用同一值。Miuix开关说明仅按Android声明的游戏分类暂停，未声明游戏使用相应黑名单。GameCapturePolicy仅在明确自动捕获请求时读取ApplicationInfo.category，不在每次MOVE或无障碍事件中查分类/读树。

作用范围没有新增Hook：com.miui.contentextension的既有Portal入口在getInjectorPackage后、读取正文或复制图片前判断，返回null时不接管系统手势；同进程面积候选在BackgroundTouchBlocker.start前判断；模块进程无障碍在根节点进入候选树前判断。Root候选早期通知新增实际来源包名，使面积路径无需猜前台或先取消原手势。手动PointCapture和显式OCR不调用该策略，仍遵守其原有锁屏、密码和黑名单限制。

仅明确PackageManager不存在/权限失败边界记录原因并提示“本次保留原操作”，本次跳过模块，保留系统原动作；不改权限、不猜游戏名单、不设置多级回退。普通分类或未声明分类按非游戏处理，因此不承诺识别全部游戏。默认关闭避免升级改变原手势。目标传送门5.1.5和模块均声明QUERY_ALL_PACKAGES；仍需真实游戏及权限失败验证。

最终构建252：189项单测0失败，Lint0 errors/139 warnings/2 hints，assembleDebug通过；真机设置开/关、重启后保存和Root来源包名回传通过。设备当前声明CATEGORY_GAME的应用为0，不能据元数据实验宣称真实游戏手势已验证。面积输入为0、bilipai评论普通长按仍未修复。详见设备记录。

## 2026-10-21：1.7.126 单一Root取词所有者

SharedRootNodeCapture通过现有ShareImageProvider受限入口返回IRootNodeLease；仅模块/传送门UID可取得。每个capture/verify/close再检查所属UID，candidate回调只接受模块UID并恢复调用方自身Binder身份后做同步决策。ContentProviderClient保持至租约关闭，使远程使用者持有稳定Provider引用。模块使用applicationContext持有Root连接，最后租约清理；不是持有Activity的静态缓存。

PortalThumbCaptureSource借自动租约，PointCaptureOverlay借手动租约；手动存在时自动请求返回空且不调用候选/接管。Root一次请求的candidate/final仍串行完整消费，verify_window使用相同IO锁；并发忙立即明确报告，不排队错过原生长按截止。租约锁不跨Root读取/候选回调，close不等待读取锁。Node结果仅传文字、相邻段落、包名、窗口ID、边界和显示尺寸；不跨Binder传Bitmap，也不增加图片压缩。截图/裁剪在调用方，锁屏、来源窗口和尺寸核验保留。

Root IO失败会关闭其对应Session并明确失效相关租约；不能继续缓存已关闭对象，也不清掉后建的Session。当前请求显示失败，只有后续用户主动重选才新建连接，不自动重试。Portal旧自动租约需用户重载；没有后台自动修复或抢断其他UiAutomation。Binder死亡释放所属租约，保留其他使用者。

构建257的189项测试、Lint0 errors/139 warnings/2 hints与assembleDebug通过。两独立进程的真实租约/手动优先/关闭后继续取词/Binder死亡释放及控制中心点取、受控连接中断后主动重选通过。新共享图片分支、实际Portal UID面积手势、并发忙和模块宿主死亡未实测。细节与证据见DEVICE_VALIDATION。

## 2026-10-21：1.7.127 AICR附近文字

AICR V2保留原始行坐标，recognizeWithLines共用原生命周期/取消/清理，旧String接口保持。先以已知像素位置生成图片核验box为四顶点八值，再接aicrLineBounds与同一nearbyOcrLines；没有按未验证LTRB或poly轮廓长度猜测。只有显式附近请求使用行结果，普通全文仍按total_text原样分行。

新增AICR附近入口沿用原Miuix选区与确认列表。首次真机在横跨屏幕的原节点上误选远处状态栏，修正共用几何规则：除同列、原区域外和最近上下行外，垂直空隙不超过两块高度较大值两倍。不硬编码app/控件/时间字符串。缺失定位或未知格式显示明确原因，原面板可返回，未自动切换OCR。

193项测试、Lint及最终262构建通过；AICR实机附近预选1/9→确认→原页2/19，旧选词保持。ML Kit新间距约束、复杂多列/长图、空几何错误态及取消未全部设备验收。确认结果仍追加末尾，不宣称已做按上下方自动前插。

## 2026-10-21：1.7.128 附近内容前插与后接

ScreenOcrSession保存当前附近请求的source及对应逐行bounds，确认选词时按已展示的OCR行序分成prepend_text/text。只有bottom不超过原区域top的行前插，重叠/无坐标/下方行后接；普通区域识字不带附近锚点仍追加。确认UI说明位置并使用“插入已选”，不增加重复的前/后按钮。每次新识别清空旧结果、选中行和bounds，避免错误态继续展示旧结果与新坐标；该错误态设备分支尚未单独触发验证。

BigBangOverlayLauncher允许仅有prepend_text的有效结果。BigBangPanelSession.insertIntoSavedState只对新增before/after调用native，拼接原当前segments，不把合并/拆分结果重新分词；选区按prefix.length UTF-16偏移并复制int[][]，不原地改变已保存输入，计算完整后写回Bundle。JNI失败沿用明确提示并恢复原快照，不加兜底分词。

263构建、195项测试及Lint0 errors/139 warnings/2 hints通过。真机原生插入探针覆盖合并emoji词块与双侧/单侧；真实产品AICR中间行双侧插入后4/45，连续观看合并词与选中保持，前台Bili。详见DEVICE_VALIDATION；不把原生探针算作独立单侧UI全部通过。

## 2026-10-21：1.7.129 中转站混合手动分组

StashStore.groupEntries/ungroupEntries只在事务中改已有批次字段，调用方当前显示顺序即组内顺序。StashBatchCard共用图片/首段正文预览，stashGroupedEntryIds按实际多成员批次判断可拆开，不能用batch_id!=id判断；旧单图导入本就有独立批次ID。StashGroupingActions只在多选且有有效操作时出现，短按钮适应窄窗。侧窗及原生系统小窗的IO错误明确报告，不新增Provider权限或全屏页面。

最终265构建、197项测试、Lint通过；侧窗混合3项合组/拆开/重开，系统小窗同组接续、拆开与两段文字重新合组/原生拖拽启动实测通过。新组跨UID接收、部分成员抽出、横屏/深色尚未验收；回退旧UI须先拆开文字/混合组，见STASH。

传送门入口只读审计：已有BiliPai长按日志没有startPick，且无证据命中CMS/用户禁用配置。5.1.5确有onStartPick和initPresentationType前置限制，但不能据此直接返回true或绕过用户黑名单/隐私。上游采集器仅遍历真实子View可能遗漏Compose虚拟节点，仍是推断；此次不加入没有根因证据的Hook，不声称普通长按修复。

## 2026-10-21：1.7.130 线性菜单退出与流光重入

简洁/流光隐藏时menuShown先置false，旧View仍在160ms退出。换边必须不论wasShown都取消旧退出并移除previousMenuView，再替换引用；否则随后取消会取消唯一移除回调并遗留旧窗口。现代原有独立释放路径保留，环形仍复用同一View。

同边快速重入复用已附着线性View时，流光需同步恢复pull和expand。expand/collapse先取消并清空原animator，再判断进度端点，避免相反方向刚开始时仍在旧端点而错误提前返回。没有加重试、定时清窗或另一层窗口回退。

266/129未修生产代码在实际手机受控Controller中复现两种菜单残留和光效进度0；267/130同一探针旧View全部detached、重入光效1，现代既有换边/离开重开检查也通过。197项单测、Lint及构建通过。此为模块UID的生产Controller证据，真实Portal触发、完整四样式四边及面积仍未验收；边界和原始结果见DEVICE_VALIDATION。

## 2026-10-21：1.7.131 拖拽窗口失败与真实抬手收尾

移除现代预览/菜单构造、显示失败后改用普通View窗口的回退，以及布局更新和目标查询吞错。首次创建、指针更新与环形延时展开三个UI入口统一记录实际异常、取消当前会话、释放窗口并显示提示；不是循环重试或换另一种窗口。屏幕几何读取归入同一窗口边界。系统正常返回不支持模糊仍使用既有可读色板；模糊查询/注册异常不再伪装成正常不支持。释放监听及Dialog的RuntimeException记录原因并继续剩余释放。

Controller提前取消后仍接受真实UP的宿主完成回调，UP自身触发窗口失败也在finally收尾；CANCEL/窗口失败时不提前回放原生取消。Portal原队列清空后才调用original的规则未变。受控回调计数1不等于实际宿主延迟队列/真实手指已经验收，不能承诺恢复已被接管的原始触摸流。

最终271构建、197项测试和Lint通过。真实设备的无效窗口类型实验从暗中换窗、无提示、取消后UP回调0，变为取消/释放/提示且回调1；正常现代换边/离开重开通过。实验不撤销实际权限，不是权限撤销测试。证据与限制见DEVICE_VALIDATION。

## 2026-10-21：1.7.132 分词选词原生拖出

BoomActionHandler两组分享图标新增长按入口，BoomChipPage/BigBangPanelSession窄Actions转发来源View和真实选中文字；无选择明确提示并消费长按，点击仍走原分享。没有复活失效的Sidebar旧手势，不把未选词替换为全文，也不新增常驻按钮。

BigBangOverlayLauncher使用ClipData.newPlainText和DRAG_FLAG_GLOBAL；阴影为Miuix当前色板、最多三行的真实选词预览。先保存完整Panel状态，仅系统startDragAndDrop返回true后关闭大面板并复用返回条；返回恢复合并/选择和原来源区域。系统返回false/拒绝权限明确提示、原面板保留并消费长按，避免松手落到点击分享。不写中转站、不授图片权限、不打开本App Activity。拖出成功启动不等于目标已接收，成功与未接收分别验收。

272构建、197项测试及Lint通过。真机将合并“小米15”拖进系统设置EditText，读回严格一致；原页仍是Settings，返回后合并单块且已选4/41保留。拖到空白未接收后同样恢复；点击分享显示真实Chooser预览“小米”，未发送。详见DEVICE_VALIDATION。其他接收App、多段/超长/emoji、深色/横屏、拒绝权限分支尚未实测。

## 2026-10-21：1.7.133 私有暂存图片系统清理

SharedImageCleanupJob由模块Application启动时安排同一Job ID133，12小时周期、电量不低约束，系统决定实际时机。没有常驻Root/前台服务、精确闹钟、开机权限或新Hook。作业非持久化，重启后下次模块进程启动重新安排；不是12小时准时保证。onStartJob在工作线程清理，onStopJob中断并放弃本次，不立即重试；主线程按工作线程所有权完成回调，避免已停止作业误收尾。

清理只列cache/shared-images和cache/ocr-input第一层文件，沿用24小时mtime期限，目录不递归。成功删除共享图后撤销对应URI授权，不读数据库、files/stash、相册或外部存储。Provider暂存和IncomingImageCache复用该清理，Binder入口清除调用方身份后以本模块身份撤销，finally恢复；导入时非关键清理失败记录原因并保留正常导入。明确IOException/SecurityException持久记录，在Miuix设置日志区域显示；调度失败也记录，不伪装成已安排。正常系统集成区显示暂存期限/收藏不受影响/后台限制时下次使用清理，不增加开关。

276最终构建/199项测试/Lint通过。真实Job首次启动0项、正常启动后删除3个旧生成文件，再强制同一系统Job0项；verify确认新PNG可读、其它目录保留。冷进程强制Job曾被AutoStartManagerService拒绝并取消，未改变权限或换Root代扫，因此只证明进程可启动时的真实系统Job，不声称本机冷后台可靠。细节见DEVICE_VALIDATION。

## 2026-10-21：1.7.134 引擎项排版与自身来源隔离

识字引擎项沿用Miuix0.9.3官方OverlayDropdownPreference，showValue=false隐藏挤压左侧的尾部值；当前引擎置于summary首行，后接简短完整说明。名称列表同时用于弹出选项和当前值，不新增组件库fork/长度猜测/兼容层。其它短选项仍保留原布局。真机浅深色、选择切换、Back与保存验证见DEVICE_VALIDATION；没有更改OCR请求/失败回退策略。

PortalContentCaptureSource在读取getInjectorPackage后，来源为BuildConfig.APPLICATION_ID直接返回null，早于正文/Bitmap复制与窗口抑制，宿主原逻辑继续。不要比较宿主Context.getPackageName()，它属于传送门。无障碍rootsAtPoint若实际activeRoot属于本模块，回收并返回空，避免addRootIfUsable丢弃自身后继续枚举底层窗口；无候选的OCR入口也复用该方法。Root原有按active root包及完成后窗口核验保持。

两条新自身隔离分支未完成真机触发验收；不能说已全面覆盖自身Activity前台，更不能把残留Portal来源别包当当前前台证据。没有前后台Provider/计数/轮询，也没有新增Hook或开启无障碍。277构建/199项测试/Lint通过，未为一行判断扩张mock或写镜像测试。


## 2026-10-21：1.7.137 连续追加原文附近文字

修正确认插入后仍沿用旧原文区域的问题：仅成功插入时合并选中行的位置，下一次从已扩展区域查找相邻文字。默认距离继续使用初始原文高度，避免范围越大越误选远处状态栏。取消或插入失败保留原选区与范围，复用原有菜单和窗口，没有新增用户界面。


## 2026-10-21：1.7.138 拇指默认门槛与免校准接入

取消“先校准成功才可开启”的硬门禁：设置解释、Provider、传送门运行时和无障碍运行时均按用户开关接入。默认长轴0.30、宽度0.095分别判断，任一达标；有效metric2自定义校准沿用原归一化指标。保存校准不改变开关，旧单位阈值使用默认门槛，不再要求反复重校准。既有校准页仅为可选诊断，没有新增页面。

检测仍要求真实正尺寸；两路分别除门槛后送入原检测器，连续180ms与锁屏、多指、移动取消保持。没有抄入反编译代码，也没有加入参考APK的700ms普通长按或300ms无文字分支来冒充面积触发。默认门槛修复不等于原始零数据已修。


## 2026-10-21：1.7.139 临时拖放托盘

按用户新要求删除中转站系统小窗、左右切换、搜索和复制入口及其执行逻辑。标题增加直接拖动把手，灰条也使用屏幕raw坐标相对起点移动，避免Window跟手移动后局部坐标反馈；松手按窗口中心自动贴左右侧并记住高度。展开面积仍不超过16%，拖出内容成功启动后收起，保留展开/收起、分组与批量拖出和明确移除。

StashActivity删除原全屏/系统小窗页面，只保留透明分享接收、串行导入和错误提示，成功后转同一托盘。共享图片组件移入StashImage.kt，删除无引用搜索组件。不改数据库、不清理用户已有暂存内容，临时托盘定位不等于擅自清空数据。


## 2026-10-21：1.7.140 按参考行为重写按压触发

删除旧校准Activity、校准计算/阈值迁移、180ms检测器以及Portal/无障碍各一套CaptureSource。PressTriggerDetector统一默认面积两轴门槛、220ms面积/700ms普通按住、120ms分类、安全无文字300ms、分轴36px移动/松手/多指取消和已有峰值保留。分类结果只能提前截止，不能把220ms反向延到300ms。开关保留原键和用户值；不读取旧校准偏好，不再提供校准入口。

PressCaptureSource共用一次Root读取、缓存、确认、接管和原页文字/OCR窗口。普通路径先读安全叶或完整TextView子树中的原文，确认时只核验前台同源；原生菜单换ID不再让缓存正文直接失效。图片仍严格核验截图前后源窗口ID/尺寸。无文字必须全树检查明确安全区域才截图，不能null→OCR；早截图为92%×92%，晚截图78%×34%，都限制来源应用窗口与屏幕并保护密码矩形及余量。新增Binder参数按显式press/early类型传递，旧点取接口默认false,false。成功交接25ms触感，4秒防重复，录音按住界面多项提示退出。

传送门5.1.5仅Hook私有scheduleStopSelfJob：按压开启时取消旧空闲计时器并阻止新计时，关闭或换来源恢复原生安排；系统onDestroy、用户禁用及进程终止仍清理。未Hook通用stopSelf/killProcess，未增加SystemUI作用域、周期拉起或猜测ROM分支。用户Hyper手势源码只读核对的是已有inputMonitor确认后pilfer的接管方式，没有面积通道；本实现仍由Root evdev独立决定手势结束。

普通700ms路径仍可能晚于原App原生长按，接管不能撤回已经发生的菜单；缓存解决取错原文的问题，不承诺所有页面都无冲突。零面积可走普通路径，不能写成物理面积数据已恢复。完整手指触发、录音界面、原生菜单共存、空闲超过60秒及新托盘贴边手感尚未实测。

## 1.7.148：按住入口补充

RootNodeCaptureMain在按压点遍历后、文字与图片选择前排除明确的录音长按控件，含文字关闭但图片开启的路径；PressCaptureSource旧文本子串过滤与四秒冷却已删。每次DOWN确认一次仍由PressTriggerDetector保证。RootTouchSource不再因缺失尺寸轴记录触发失败。211项检查、Lint和构建通过；172已安装，Portal尚未重载，新完整手势未实测，详见设备记录。

## 1.7.149：暂未就绪窗口属于单次请求结果

真实Root helper因awaitRoot的1200ms节点就绪等待抛出IOException退出，导致共享租约失效和后续取词持续失败。当前只将这个明确的无窗口结果改为found=false/window_not_ready；保留服务供下一次用户手势，不增加自动重连或本次请求回退。captureRequest单测覆盖同一服务对象的缺窗后成功请求，212项检查及构建通过，173安装并后台加载。真机空缺后恢复与完整手势仍待验。

## 1.7.151：一级操作与图标

BigBangOverlayContent使用两行主操作；Miuix IconButton/TooltipBox承载常见图标，少量不易解释的动作保留短标签。CaptureMore入口明确为二次爆炸并沿用ScreenOcrOverlay追加快照流程，编辑/翻译/合并/拆分等不再藏在更多。更多只保留外观，Back与点外部继续按子层→收起处理，不增收起按钮。工具条和反馈高度计入短内容自然高度；词块仍由原生View负责拖放/划选，但颜色、sp字体与选中表现使用Miuix主题。212项本地检查及build311通过，175单次安装；未实测新界面，不把编译等同交互通过。

系统phantom数量裁剪已由真实日志确认会杀两宿主su。精确Hook与锁信息已定位，尚未实现，见ROOT_PROCESS_LIFECYCLE.md；最新输入ready不代表取词连接ready。

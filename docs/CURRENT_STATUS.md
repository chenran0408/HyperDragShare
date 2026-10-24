# MMGG 当前状态与接手

核对日期：2026-10-24。本页是唯一当前状态入口；历史调查见[归档](archive/2026-10-24/README.md)。本轮完成退出/销毁资源与异步回调审计及最小修复；设置拆分、配置更新和动作提取已完成，不重复执行。没有操作手机、安装、推送或发布版本。

## 在哪里继续

| 职责 | 工作树 | 分支 / 有效源码基线 | 版本 |
|---|---|---|---|
| 后续修复、新功能、Beta验收 | `D:/CODEX/Tasks/2026-10-22/new-chat-3/work/HyperDragShare` | `beta`；当前生命周期源码 `9e7bf9a130ffcca22735be82b2f7eb5b4b3bd870`，后续纯文档HEAD用git log核对 | 1.7.219-beta.19 / 261（验证构建，无新版交付） |
| 已发布正式版维护 | `D:/CODEX/Tasks/2026-10-20/new-chat-2/work/HyperDragShare` | `repair-from-189` / `e25c66725cda51f23bdb8d95de036f7811eba017`（仅新增CI，正式产品源码仍等同b40abaf） | 1.7.218 / 242 |

开始前执行 `git log -1`、`git status --short`；文档提交会使HEAD推进，不能将上表源码哈希误当永远不变的分支HEAD。两个工作树在提交前后按实际状态核对；构建缓存、本机配置和.learnings保留，不属于待合入功能修复。

正式tag `v1.7.218^{}`=`c03e880cf67ce4c87e81bbcb45fa1fbcf0a2e247`；稳定HEAD比它多3个历史文档提交与设置拆分批次的CI提交，产品源码相同。此前确认origin默认为 `hyperos4-adaptation@3520884`，已建立两维护线并配置同名upstream；本轮未联网核对或修改远程，本地beta从origin/beta所指69828c4继续推进、尚未推送。历史路线的archive标签和原分支/tag/worktree保留。完整关系见[BRANCHES](BRANCHES.md)，不要从默认clone或`v1.9.3`实验tag直接开始。

## 交付身份与验证边界

最近可安装交付仍是（本轮没有替换该文件）：`D:/CODEX/Tasks/2026-10-23/mmgg-feedback-10-23/outputs/beta19/MMGG-1.7.219-beta.19.apk`。

- 产品源码d6d1f3a；4,881,913 bytes；SHA256 `FB87509F1FCCDB6224CA6C1CA5CCD7A518A5CAC485EE9A8DC8BF127AEB1E06D5`。
- 既有交付记录：572项JVM/Robolectric测试0失败/错误/跳过；Lint0错误、178警告、2提示；Beta/R8、版本与v2验签通过。签名证书与beta18一致，不能据此推定与正式发布密钥一致。
- beta19没有安装或实机/完整Android模拟器交互证据。历史4597c7a完成设置职责移动，a064f17完成配置更新；动作提取批次与本轮生命周期均只生成编译/R8验证包，保留旧元数据，不提供为新版交付，不替换上述安装包。
- 既有正式包1.7.218发布/设备证据保留于DEVICE_VALIDATION及归档。本轮没有重新查询GitHub Release资产/Latest状态、没有重新验签正式APK，不能用tag存在替代资产核验。

原始功能任务唯一完整依据：`D:/CODEX/Tasks/2026-10-23/mmgg-feedback-10-23/TASK_BRIEF.md`；同目录TASK_CHECKLIST和HANDOFF保留全部历史编号/证据（旧标题/前言是历史版本，最新状态按本页和提交核对）。原beta4任务位于 `D:/CODEX/Tasks/2026-10-23/mmgg-1-7-219-beta-4`。历史调查位于 `D:/CODEX/Tasks/2026-10-24/mmgg-baseline-audit`；备份/CI/设置拆分记录位于 `D:/CODEX/Tasks/2026-10-24/mmgg-settings-foundation`；配置记录位于 `D:/CODEX/Tasks/2026-10-24/mmgg-config-updates`；动作批次记录位于 `D:/CODEX/Tasks/2026-10-24/mmgg-controller-actions`；本轮完整要求、清单、结果和恢复入口位于 `D:/CODEX/Tasks/2026-10-24/mmgg-exit-lifecycle`。

## 退出/销毁生命周期（本轮）

从3af1c4a继续，Luna high分组审计/实施，主Agent运行修复前测试、审查最小实现与集中验证。稳定e25c667保持不动。未改DragShareActions、Root权威/UP/CANCEL、宿主取消回放、配置/UI或Bitmap/文件/URI授权释放策略。

- **已证实并修复**：预热窗destroy遗漏dispose；Honeycomb/TaskSwitcher在已移除View上构造method reference导致重复cancel/dismiss空指针；SideWheel取消snap后end回调重挂timeout；图片接收切换hosted/BigBang未使旧copy代次失效；交互窗首帧前/posted后退出未完整解绑，以及observer迁移后layout listener清理遗漏。窗口detach/dispose幂等解绑并使旧UI callback失效；正常一次首帧确认和动作时序保留。
- **本地提交**：b971633手势overlay退出；1845768窗口监听/预热；9e7bf9a图片输入代次及解码退出测试。实际受验app tree为`bc458d77f866d8c8ad5688471d08c5105212443b`，文档提交不改变它。
- **实际验证**：`testDebugUnitTest lintDebug assembleDebug assembleBeta assembleRelease`成功，2m35s；120类618项0失败/错误/跳过，新增21项；Debug、Beta `minifyBetaWithR8`、Release `minifyReleaseWithR8`及mapping均完成。Release产物是unsigned；版本元数据保留，只作验证包，不替换原beta19交付、不提供新版安装。
- **Lint**：0错误、180警告、2提示。比历史增加两条StaticFieldLeak，位于BigBangOverlayLauncher和SearchOverlayWindow的既有静态active持有窗口，因新增observedView字段被规则识别。已审查正常dispose清该字段、onDismiss清active和监听解绑，并有行为测试；未进行堆可达性测量，不把提示等同已证实泄漏，也未屏蔽规则。
- **测试证据**：预热/退出动画/旧代次/重复销毁/模糊通知；Dialog首帧前、已post后、直接dismiss/cancel和observer迁移；snap中detach；图像copy迟到成功/失败遇到新intent、正常成功与destroy；OCR/QR decode晚于重复退出。既有动作/授权调用/存图/中转站/分词/输入/AICR/生命周期断言完整保留。
- **失败处理**：测试先暴露缺陷再修改；新增夹具的Shadow/observer存储和异步Dialog收尾问题均单独修正。全量曾由新测试未等待onDismiss引入静态touch-region污染，修夹具后旧测试恢复；未修改旧断言或生产通知时机。所有失败XML与日志保留，不作为通过证据。
- **未覆盖/未改风险**：真实ROM WindowManager/Surface/Binder/旋转多屏锁屏/IME、URI实际授予撤销及目标发送；没有实机、完整模拟器或堆内存测试。BoomChipPage迟到布局/动画、阻塞任务持有Context、WebViewClient迟到及进程级runtime teardown只有代码风险记录，未强行修复。

资源地图新增[Exit / Destroy Resource Ownership](EXIT_LIFECYCLE.md)，列出creator → owner → async borrower → release point及取消/移除/guard策略。当前结果在本页维护，完整交付见任务`outputs/DELIVERY.md`，机器结果见`outputs/verification/RESULTS.json`；没有运行云端CI。

## 设置页职责（已完成，不重复拆分）

| 文件 | 当前职责 |
|---|---|
| `SettingsScreen.kt` | 导航/页面组装、设置目录、搜索定位路由；根回调在执行时读取最新配置并同步内存状态；已移除copySettings |
| `ActivationMonitor.kt` | 激活快照、CONFLATED刷新/代次、首次启动有限等待、偏好监听、生命周期恢复与注销 |
| `ActivationDiagnosticsPage.kt` | 首页状态卡、诊断页面、证据说明与处理入口 |
| `ShareTargetSettingsPages.kt` | 分享目标可见性/批量切换、排序、目标查询/图标和分组 |
| `ApplicationSettingsPages.kt` | 应用查询、黑名单和游戏选择页及专用行组件 |
| `SettingsComponents.kt` | 共用加载、返回按钮、设置导航组件 |
| `SettingsEffectNotice.kt` | 字段mutation更新与原保存/生效提示；不持有独立配置状态 |
| `DragShareSettings.java` | 默认/校验/读写/Bundle及builder/toBuilder；具名setter替代调用方整组位置参数 |

设置拆分40个声明的对照证据保留于settings-foundation交付。配置批次迁移28处更新调用：标量只设目标字段，集合增删使用mutation的current；搜索/导航/滑条完成保存时机未改。本轮设置源码、配置key/默认值/协议/迁移、手势/宿主适配均未改。开发说明和示例见[CONFIGURATION](CONFIGURATION.md)。

- **配置批次逻辑验证（历史）**：模型新增7项，覆盖默认、非默认对象单字段复制、连续修改、prefs/Bundle往返、缺失字段、集合快照和原边界归一化；Kotlin新增1项，通过UI实际委托的更新函数连续修改标量、显隐/排序/黑名单/游戏集合，并检查读回和提示。闭包行为经代码审阅，测试未挂载完整设置页。
- **配置批次全量（历史）**：a064f17对应app树运行`testDebugUnitTest lintDebug assembleDebug assembleBeta`成功，1m56s，113类583测试0失败/错误/跳过；Lint0错误/178警告/2提示。证据位于mmgg-config-updates/outputs/verification/RESULTS.json；本轮结果另列，不用旧报告代替。
- **CI职责**：Beta提交36a1bb1，稳定e25c667，各自覆盖push/PR到两维护线；均执行单测/Lint/Debug，Beta额外实际assembleBeta/R8，稳定不调用不存在的Beta任务。产物标CI验证包，记录提交/版本/SHA；没有发布或使用本地密钥。
- **历史云端两线**：稳定e25c667的[run 40012602021](https://github.com/chenran0408/HyperDragShare/actions/runs/40012602021)与Beta4597c7a的[run 40013175304](https://github.com/chenran0408/HyperDragShare/actions/runs/40013175304)均success，任务/元数据/产物和报告上传已在设置拆分批次核对；Beta实际包含R8。配置和本轮动作提取均未推送/触发或查询云端CI，不能称新源码已云端通过。
- **未覆盖**：完整NavDisplay搜索滚动/Back触摸、真实Miuix视觉/主题/字体/屏幕矩阵、真机跨进程功能。监测恢复测试不是完整页面交互验收。此前所有功能未完项如下保留。

上轮本地备份bundle与5个tag在BRANCHES落实记录有入口；当时已显式推送两维护线/tag到用户origin。本轮只作本地提交，不新增或删除引用。

## 动作执行职责与既有验证（001942e批次）

源码001942e：`DragShareActions`承接外部文字/图片分享、复制、存图、中转站、分词、OCR/扫码。入口只接收payload/target/stagedUri/x/y，依赖同一Context/main Handler/DragShareToast及固定来源布尔；异步worker持有本次动作数据，不访问Controller/Session。Controller保留选目标、结束/取消、pending暂存与会话结束标记、全部预览/菜单/动画和清理；2653→2506行，新执行器202行，没有把会话模型搬进执行器。

资源与线程地图见[DRAG_ACTIONS](DRAG_ACTIONS.md)。ShareLauncher、ClipboardWriter、StashClient、LocalImageSaver、Provider、设置和输入代码均未改；没有新增Bitmap recycle、URI提前撤销/删除、线程调度/超时/重试。成功提交的旧图片动作可在新手势期间继续使用原URI，完成回调只提示，不清新窗口。

- **动作批次基线（历史）**：提取前79fbf72上的PreviewLifecycle/LocalImageSaver/StashStore/ShareImageProvider四类26项通过，18秒，旧断言完整保留。
- **动作批次全量本地（历史）**：`testDebugUnitTest lintDebug assembleDebug assembleBeta`成功1m49s；115类597项0失败/错误/跳过，Lint0错误/178警告/2提示，Debug与实际minifyBetaWithR8完成、mapping存在。源码提交前受验工作树与001942e的app tree相同：`48de1d5a1c525aef7c2a2c2f02ae48872205fd65`。
- **动作批次新增证据（历史）**：ActionsTest11项验证真实外部分享Intent/Provider授权调用与读图、ClipboardWriter系统边界替身、真实StashClient/Provider/Store、存图线程/Bitmap替身、分词Bundle和OCR/QR；ActionLifecycleTest3项使用真实Controller/Actions与可控stage/clipboard，覆盖重复回调、取消/销毁迟到、旧已接受动作在新手势中的数据与失败隔离/preview不变。旧输入/生命周期测试随全量通过。
- **失败与修正记录**：新增测试先遇夹具编译问题，后定位框架自动补ClipData与模拟权限查询边界；仅修新夹具，未改旧断言/生产行为。专用诊断证明未grant前及revoke后查询均为允许，所以只对真实Context调用的包/URI/flags作断言，不声称系统权限撤销已验证。临时诊断输出未进入最终测试文件。
- **证据位置**：mmgg-controller-actions/outputs/verification/RESULTS.json、baseline-junit.zip、junit-results.zip、lint-results-debug.xml与uri-permission-diagnostic.xml；原日志在work。未推送或运行云端CI、未操作手机/安装/发布；验证APK不作为新版交付。
- **未覆盖**：真实接收端读取/发送、系统URI权限实际授予/撤销、Root剪贴板/AppOp、AICR/宿主启动限制及完整Miuix交互/视觉。Bitmap与暂存文件继续原所有权/GC和24小时清理策略，未新增运行中租约；没有声称本批修复原功能待办。

## 当前有效实现

源码主要在 `app/src/main/java/com/leaf/hyperdragshare/codex/`、`app/src/main/kotlin/com/leaf/hyperdragshare/codex/` 和 `gestures/`，保留Java/Kotlin混合、LSPosed API82、Miuix0.9.3。

| 能力 | 当前入口/关键路径 | 可以确认及不能扩大解释的部分 |
|---|---|---|
| 首页分层诊断 | `ActivationMonitor.kt`、`ActivationDiagnosticsPage.kt`、`ActivationState.kt`、`ModuleActivation.java` | Root/存活注入/服务/Portal Root/输入/reader/悬浮权限分别取证；五态与按功能依赖；conflated请求＋代次检查；超时未知。不是API102框架binder方案 |
| 按压取词/拖拽 | `RootTouchSource`→`EvdevTouchParser`→`PressTriggerDetector`/`PressCaptureSource`→`DragShareController` | 时长600ms默认、250–3000可调；Root UP/CANCEL收尾；无物理DOWN不接管被动预览；输入设备按自然显示比例选择且排除蓝牙，歧义失败 |
| 已选目标执行 | `DragShareController.launchShare`→`DragShareActions`→既有ShareLauncher/ClipboardWriter/StashClient/LocalImageSaver及分词/OCR入口 | 明确动作参数，异步数据独立；控制器保留会话和窗口，线程/授权/资源策略不变。测试分层边界见上文 |
| Root语义节点/局部识字/主动点取 | `SharedRootNodeCapture`、`RootNodeCaptureMain`、`RootScreenshotter`、`PointCapture*` | 按请求节点/安全窗口核验；图片节点/OCR区域用截图；不是微信私有原媒体获取，也不是系统无障碍服务 |
| 传送门媒体适配 | `PortalHooks`、`PortalContentCaptureSource`、`CapturedContent.ImageOrigin` | 验证Taplus当前媒体载荷标PORTAL_MEDIA并交接同次按压；截图/未知来源不冒充原图。旧版成功来源是候选，需要现场证据 |
| 分词及选中操作 | ICU `TextSegmenter`→`BigBangPanelSession`/`BigBangOverlayContent`→`BoomChipPage`/`SwipeSelectView` | 上游资源/左右动作排布已部分复用；按下至抬手冻结选区布局/旧动画/padding。完整Leaf样式、定位与真实体验仍未验收 |
| 图片识字/扫码 | `AicrOcrClient`、`AicrBarcodeClient` | 复用系统AICR，不内置MLKit/ZXing双引擎；服务不可用与权限是不同问题。小米翻译接口未接通 |
| 中转站 | `StashStore`/`StashOverlayPanel`、`NativeSidebarHooks`、`EmbeddedStashDragBridge`、`SidebarDragWindowFlags` | 独立/原生侧栏路径并存；嵌入式拖影、连接占位清理与拖动期宿主穿透已有逻辑修复；目标接收/发送未闭环 |
| 菜单/配置/界面 | `DragShareSettings`、`SettingsScreen`、`LinearMenuGeometry`/`CircleMenuGeometry` | 六位置/四外观、近手按左右拖向选边；底边触发带优先；合法配置保留；保存后提示生效范围；现代玻璃有回退 |
| 手势/小窗 | `gestures.xposed.FanFreeformHook`、运行时/ConfigStore | FanFreeform157来源整合；恢复侧滑入口/目标，窗外点击总开关AND方向键；底角DOWN修复用户报告已改善；设备矩阵仍不完整 |
| 复制直达/进程支持 | `AicrCopyHooks`、`RootProcessFrameworkHooks`、`TrustServiceRiskHooks`、`OverlayNotificationHooks` | 系统原生复制卡片/精确Hook；作用域加载与实际功能分别验收，不自动扩大系统保护 |

## 未完成、待验证与暂停

这些仍是原任务的一部分，本轮文档整理不取消、不代替修复。

| 原编号 | 当前状态 | 下一步具体缺口 |
|---|---|---|
| H02-IMAGE-FROZEN-1024 | beta19两处代码缺陷修复、逻辑通过；用户现场待验 | 59fd022修触摸源选择、5e5a4e3阻止show(-1)。beta17日志缺启动选中源，event7错误选择是强候选而非实机证实；复测同页面拖动/松手、ready源/显示/旋转。运行中切屏、同形屏/异常坐标比例未覆盖 |
| N03/B06及PANEL/FLICKER/PRESS-STABILITY | 部分实现，未验收 | fd80f87部分上游资源/排布；4dcd4d5/1bcabc6防跳动逻辑通过。继续按用户角度模拟滑选/取消/拖出/恢复、工具栏位置/边缘/滚动；不能宣称完整移植 |
| N05/B04/WX-ORIGIN/HISTORY | 微信分词用户确认有效，原媒体能力未完成 | edbb9d9只拒绝截图/未知来源。对照旧Portal原生Bitmap及同手势交接条件；缺现场payload来源/接受时序；不伪装系统朗读服务、不把截图改名原图 |
| N06/END2END | 用户已能拖出，但微信/QQ松手无变化 | 51e8d08宿主穿透候选已实施，须分清start/transfer/drop/accepted、目标内容读取与最终发送；独立中转站曾成功是历史对照，不等于嵌入式已成功 |
| N07、B04接收/原图比对 | 受N06影响待验 | 底边几何修复不替代目标接收；待中转站闭环后比对 |
| B03/N02/B07/REGRESSION | beta18恢复beta12旧语音/倍速disabled态避让，逻辑证实 | db41204收紧enabled导致代码回退，585c162修复。实际应用节点状态未取得；音视频拖条、录音/拍摄、无元数据自绘控件仍需实测；不要按关键词屏蔽普通正文 |
| F03/N09 | 侧滑/窗外点击开关源码和逻辑已修 | 83048f8；单/双击、方向、横竖屏、系统侧栏冲突与真实手感未覆盖 |
| B05/GH01/GH02/H03 | 方向/现代样式/玻璃/主题配置实现保留 | 四样式真实落点、硬件玻璃、Compose深浅/大字体/长文/屏幕/IME矩阵待验；没有对外关闭issue |
| N04 | 用户本机目前未再误退桌面 | 保留观察，不称全设备保证；f676b4f |
| H01/N08/N01/B09 | 已有诊断闪退/生效提示/时长/收起清窗逻辑修复 | beta10诊断曾实机往返通过；不等于beta19全部UI和宿主已实测。N08不自动重启；B09不恢复灰条 |
| F02 | 未开始 | 小米搜索联动按DOCX需求保留后续 |
| N10-AICR-MISSING | 用户明确暂停 | 既有组件提示分析保留；不扩大为已解决或恢复排查 |
| R01/R02/B01/B08及其余原清单 | 保留已有分块整理/替身证据与边界 | 无真实CPU/帧率基准、不量化性能提升；历史触控探针已隔离，不能恢复面积研究；原三图/应用图标卡没有统一根因证据 |

## 下一批候选：文字面板退出验证

预览退出、旧代次、重复销毁与迟到回调已在本轮补测，不再从零重复；没有为了减少行数提取通用窗口owner。下一批最小范围是BigBangPanelSession.close与BoomChipPage待执行的布局、行/chip动画和选区通知：先用可控队列验证退出后不访问View，不启动拖出，不改变新session；只修稳定复现的未撤销任务。保留现有分词/选区/拖放交互，不同时拆Root输入、会话模型、宿主适配或动作执行。真实接收发送/微信原图/上游面板体验等原未完项继续保留。

## 后续开始顺序

1. 读根AGENTS、本页和DECISIONS，再读完整任务原文；核对beta真实HEAD/状态与其他写入者。
2. 后续按用户指示推进最小控制器拆分或N03/N06/N05功能缺口，不把记录当自动执行授权。系统指针监听仍只是候选，未采用，不叠加到Root会话。
3. 受影响检查/集中回归命令见[AGENTS](../AGENTS.md)；Beta交付与远程/CI后续建议见[RELEASE_CHANNELS](RELEASE_CHANNELS.md)和[BRANCHES](BRANCHES.md)。

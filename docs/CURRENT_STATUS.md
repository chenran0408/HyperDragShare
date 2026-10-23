# MMGG 当前状态与接手

核对日期：2026-10-24。本页是唯一当前状态入口；历史调查见[归档](archive/2026-10-24/README.md)。本轮落实备份/独有文档/CI，并完成设置页职责拆分；没有改变产品行为、操作手机、安装或发布版本。

## 在哪里继续

| 职责 | 工作树 | 分支 / 有效源码基线 | 版本 |
|---|---|---|---|
| 后续修复、新功能、Beta验收 | `D:/CODEX/Tasks/2026-10-22/new-chat-3/work/HyperDragShare` | `beta`；当前设置拆分源码 `4597c7ab2eea00fc50b7307a66ced951e71daa20`，后续纯文档HEAD用git log核对 | 1.7.219-beta.19 / 261（验证构建，无新版交付） |
| 已发布正式版维护 | `D:/CODEX/Tasks/2026-10-20/new-chat-2/work/HyperDragShare` | `repair-from-189` / `e25c66725cda51f23bdb8d95de036f7811eba017`（仅新增CI，正式产品源码仍等同b40abaf） | 1.7.218 / 242 |

开始前执行 `git log -1`、`git status --short`；文档提交会使HEAD推进，不能将上表源码哈希误当永远不变的分支HEAD。两个工作树在提交前后按实际状态核对；构建缓存、本机配置和.learnings保留，不属于待合入功能修复。

正式tag `v1.7.218^{}`=`c03e880cf67ce4c87e81bbcb45fa1fbcf0a2e247`；稳定HEAD比它多3个历史文档提交与本轮CI提交，产品源码相同。origin默认仍是 `hyperos4-adaptation@3520884`，没有改默认；`origin/beta`和`origin/repair-from-189`现已建立，两本地线已配置各自upstream。历史路线用archive标签标记，原分支/tag/worktree没有删除。完整关系与保留内容见[BRANCHES](BRANCHES.md)，不要从默认clone或`v1.9.3`实验tag直接开始。

## 交付身份与验证边界

最近可安装交付仍是（本轮没有替换该文件）：`D:/CODEX/Tasks/2026-10-23/mmgg-feedback-10-23/outputs/beta19/MMGG-1.7.219-beta.19.apk`。

- 产品源码d6d1f3a；4,881,913 bytes；SHA256 `FB87509F1FCCDB6224CA6C1CA5CCD7A518A5CAC485EE9A8DC8BF127AEB1E06D5`。
- 既有交付记录：572项JVM/Robolectric测试0失败/错误/跳过；Lint0错误、178警告、2提示；Beta/R8、版本与v2验签通过。签名证书与beta18一致，不能据此推定与正式发布密钥一致。
- beta19没有安装或实机/完整Android模拟器交互证据。本轮4597c7a仅职责移动/必要可见性，不新增可安装行为；验证APK保留旧元数据但字节/SHA不同，只用于构建检查，不冒充上述交付包或正式发布。
- 既有正式包1.7.218发布/设备证据保留于DEVICE_VALIDATION及归档。本轮没有重新查询GitHub Release资产/Latest状态、没有重新验签正式APK，不能用tag存在替代资产核验。

原始功能任务唯一完整依据：`D:/CODEX/Tasks/2026-10-23/mmgg-feedback-10-23/TASK_BRIEF.md`；同目录TASK_CHECKLIST和HANDOFF保留全部历史编号/证据（旧标题/前言是历史版本，最新状态按本页和提交核对）。原beta4任务位于 `D:/CODEX/Tasks/2026-10-23/mmgg-1-7-219-beta-4`。上一轮调查记录位于 `D:/CODEX/Tasks/2026-10-24/mmgg-baseline-audit`；本轮执行/备份/恢复记录位于 `D:/CODEX/Tasks/2026-10-24/mmgg-settings-foundation`。

## 设置页职责与本轮检查

| 文件 | 本轮职责 |
|---|---|
| `SettingsScreen.kt` | 导航/页面组装、设置目录构造、搜索定位路由、持久化回调及原copySettings；2694→1214行 |
| `ActivationMonitor.kt` | 激活快照、CONFLATED刷新/代次、首次启动有限等待、偏好监听、生命周期恢复与注销 |
| `ActivationDiagnosticsPage.kt` | 首页状态卡、诊断页面、证据说明与处理入口 |
| `ShareTargetSettingsPages.kt` | 分享目标可见性/批量切换、排序、目标查询/图标和分组 |
| `ApplicationSettingsPages.kt` | 应用查询、黑名单和游戏选择页及专用行组件 |
| `SettingsComponents.kt` | 共用加载、返回按钮、设置导航组件 |

40个原顶层声明的函数体/注解对照一致，只有必要private→internal和import变动。`SettingsCatalog.kt`、`ActivationState.kt`、配置key/默认值/协议/迁移、DragShareController/手势/宿主适配均未改；原copySettings只为跨文件使用改internal，更新入口改造留下一批。

- **实际逻辑验证**：`ActivationMonitorTest`用现有ComposeView/Robolectric和测试专用Root shadow，3项通过；覆盖旧代次抑制、偏好刷新、pause/resume恢复、detach/dispose后旧结果和监听失效。测试替身不进入正式源码。
- **全量本地**：4597c7a上`testDebugUnitTest lintDebug assembleDebug assembleBeta`成功，575测试0失败/错误/跳过，Lint0错误/178警告/2提示，Debug和Beta/R8完成，mapping存在。结果与JUnit/Lint证据见本轮outputs/verification/RESULTS.json及work/final-regression-build.log。
- **CI职责**：Beta提交36a1bb1，稳定e25c667，各自覆盖push/PR到两维护线；均执行单测/Lint/Debug，Beta额外实际assembleBeta/R8，稳定不调用不存在的Beta任务。产物标CI验证包，记录提交/版本/SHA；没有发布或使用本地密钥。
- **云端两线**：稳定e25c667的[run 40012602021](https://github.com/chenran0408/HyperDragShare/actions/runs/40012602021)与Beta4597c7a的[run 40013175304](https://github.com/chenran0408/HyperDragShare/actions/runs/40013175304)均success，任务/元数据/产物和报告上传均通过；Beta实际包含R8。产物名分别带1.7.218、1.7.219-beta.19及对应完整SHA。后续仅文档提交按path过滤跳过，不能写成该文档HEAD另跑了CI。
- **未覆盖**：完整NavDisplay搜索滚动/Back触摸、真实Miuix视觉/主题/字体/屏幕矩阵、真机跨进程功能。监测恢复测试不是完整页面交互验收。此前所有功能未完项如下保留。

本地备份bundle与5个tag在BRANCHES落实记录有入口；已显式推送两维护线/本轮tag到用户origin，upstream、默认分支和旧引用没有改动。

## 当前有效实现

源码主要在 `app/src/main/java/com/leaf/hyperdragshare/codex/`、`app/src/main/kotlin/com/leaf/hyperdragshare/codex/` 和 `gestures/`，保留Java/Kotlin混合、LSPosed API82、Miuix0.9.3。

| 能力 | 当前入口/关键路径 | 可以确认及不能扩大解释的部分 |
|---|---|---|
| 首页分层诊断 | `ActivationMonitor.kt`、`ActivationDiagnosticsPage.kt`、`ActivationState.kt`、`ModuleActivation.java` | Root/存活注入/服务/Portal Root/输入/reader/悬浮权限分别取证；五态与按功能依赖；conflated请求＋代次检查；超时未知。不是API102框架binder方案 |
| 按压取词/拖拽 | `RootTouchSource`→`EvdevTouchParser`→`PressTriggerDetector`/`PressCaptureSource`→`DragShareController` | 时长600ms默认、250–3000可调；Root UP/CANCEL收尾；无物理DOWN不接管被动预览；输入设备按自然显示比例选择且排除蓝牙，歧义失败 |
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

## 下一批：统一配置更新入口（本轮未实施）

1. 限定DragShareSettings的不可变更新/复制入口，集中现有`copySettings`字段传递责任，迁移设置目录、显隐/排序、黑名单/游戏和主题/日志等调用；不要在各页面增加新的大构造函数副本。
2. 保持`saveDragShareSettingsWithNotice(previous,next)`保存/提示和根页面内存态同步顺序；一次操作只更新目标字段，其他字段/合法旧值保持，取消不保存。
3. SharedPreferences key、Provider Bundle/跨进程协议、默认值、旧0时长迁移保持不变；IntegrationSettings、BigBangSettings、LauncherIcon和手势ConfigStore是独立存储职责，不为“统一”强行合并。
4. 复用本轮配置合约/升级保留/提示测试，新增只改一字段、连续更新、隐藏目标/排序互不丢失等行为断言；不写只验证文件或类名的测试。
5. 不夹带DragShareController/宿主/手势行为、依赖升级或面板功能修复。具体API以调用调查后最直接方案为准，不预建通用更新框架。

## 后续开始顺序

1. 读根AGENTS、本页和DECISIONS，再读完整任务原文；核对beta真实HEAD/状态与其他写入者。
2. 本轮职责拆分完成后，后续按用户指示推进上面的配置更新批次或N03/N06/N05功能缺口，不把记录当自动执行授权。系统指针监听仍只是候选，未采用，不叠加到Root会话。
3. 受影响检查/集中回归命令见[AGENTS](../AGENTS.md)；Beta交付与远程/CI后续建议见[RELEASE_CHANNELS](RELEASE_CHANNELS.md)和[BRANCHES](BRANCHES.md)。

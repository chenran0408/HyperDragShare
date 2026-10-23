# MMGG 当前状态与接手

核对日期：2026-10-24。本页是唯一当前状态入口；版本史与当时结论已移入[历史归档](archive/2026-10-24/README.md)。本轮只整理文档，没有修改功能、构建、连接手机或改变分支/远程。

## 在哪里继续

| 职责 | 工作树 | 分支 / 有效源码基线 | 版本 |
|---|---|---|---|
| 后续修复、新功能、Beta验收 | `D:/CODEX/Tasks/2026-10-22/new-chat-3/work/HyperDragShare` | `beta`；产品源码 `d6d1f3a0c88591126879a553960c9133d2dc50a7`，本页所在整理提交仅文档 | 1.7.219-beta.19 / 261 |
| 已发布正式版维护 | `D:/CODEX/Tasks/2026-10-20/new-chat-2/work/HyperDragShare` | `repair-from-189` / `b40abafd1a1143b0ebb4869599fc6979ce7ae8de` | 1.7.218 / 242 |

开始前执行 `git log -1`、`git status --short`；整理提交会使beta HEAD推进，不能将上表产品源码哈希误当永远不变的分支HEAD。两个工作树调查时均干净，无未暂存/已暂存/非忽略未跟踪文件；构建缓存、本机配置和.learnings保留，不属于未提交功能修复。

正式tag `v1.7.218^{}`=`c03e880cf67ce4c87e81bbcb45fa1fbcf0a2e247`；稳定HEAD仅比它增加3个文档提交，源码相同。origin默认仍是 `hyperos4-adaptation@3520884`，比Beta源码少58提交；两本地工作线均无upstream，也尚无同名远程头。完整关系/独有内容见[BRANCHES](BRANCHES.md)，不要从默认clone或`v1.9.3`实验tag直接开始。

## 交付身份与验证边界

最近交付：`D:/CODEX/Tasks/2026-10-23/mmgg-feedback-10-23/outputs/beta19/MMGG-1.7.219-beta.19.apk`。

- 产品源码d6d1f3a；4,881,913 bytes；SHA256 `FB87509F1FCCDB6224CA6C1CA5CCD7A518A5CAC485EE9A8DC8BF127AEB1E06D5`。
- 既有交付记录：572项JVM/Robolectric测试0失败/错误/跳过；Lint0错误、178警告、2提示；Beta/R8、版本与v2验签通过。签名证书与beta18一致，不能据此推定与正式发布密钥一致。
- beta19没有安装或实机/完整Android模拟器交互证据。View触摸/测量替身不是Compose全屏视觉，也不是澎湃系统行为验收。本轮复核既有manifest/日志，不重跑测试和打包。
- 既有正式包1.7.218发布/设备证据保留于DEVICE_VALIDATION及归档。本轮没有重新查询GitHub Release资产/Latest状态、没有重新验签正式APK，不能用tag存在替代资产核验。

原始功能任务唯一完整依据：`D:/CODEX/Tasks/2026-10-23/mmgg-feedback-10-23/TASK_BRIEF.md`；同目录TASK_CHECKLIST和HANDOFF保留全部历史编号/证据（其旧标题/前言有历史版本，最新状态以本页和对应提交核对）。原beta4任务位于 `D:/CODEX/Tasks/2026-10-23/mmgg-1-7-219-beta-4`。本次整理记录位于 `D:/CODEX/Tasks/2026-10-24/mmgg-baseline-audit`。

## 当前有效实现

源码主要在 `app/src/main/java/com/leaf/hyperdragshare/codex/`、`app/src/main/kotlin/com/leaf/hyperdragshare/codex/` 和 `gestures/`，保留Java/Kotlin混合、LSPosed API82、Miuix0.9.3。

| 能力 | 当前入口/关键路径 | 可以确认及不能扩大解释的部分 |
|---|---|---|
| 首页分层诊断 | `SettingsScreen.kt`、`ActivationState.kt`、`ModuleActivation.java` | Root/存活注入/服务/Portal Root/输入/reader/悬浮权限分别取证；五态与按功能依赖；conflated请求＋代次检查；超时未知。不是API102框架binder方案 |
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

## 后续开始顺序

1. 读根AGENTS、本页和DECISIONS，再读完整任务原文；核对beta真实HEAD/状态与其他写入者。
2. 用户授权恢复功能工作后，沿beta接着做N03/N06/N05及对应缺口；本轮仍是文档任务。系统指针监听只是候选设计，未采用，不直接叠加到Root会话。
3. 受影响检查/集中回归命令见[AGENTS](../AGENTS.md)；Beta交付与远程/CI后续建议见[RELEASE_CHANNELS](RELEASE_CHANNELS.md)和[BRANCHES](BRANCHES.md)。

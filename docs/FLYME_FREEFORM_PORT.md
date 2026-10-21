# Flyme 小窗参考与 HyperOS 接入

## 目标与基线

在当前 HyperDragShare 开源分支内独立实现参考交互，复用已合并的 Hyper 手势、HyperOS 小窗桥及原生侧栏；产物是本项目的 arm64-v8a、R8 APK，不修改或分发参考 APK，不复制反编译代码。目标仍为小米15 Pro / HyperOS 4.0.0.17 / Android17，依赖 Root 与 LSPosed。

本批起点：源码 d2d499f（版本176/200，包含尚未打包的 AICR 默认值变更）；设备最后确认安装176/200。各进程实际 Hook 版本沿用 CURRENT_STATUS 的分别记账，不能用 APK 版本推定所有进程已更新。

参考样本：FlymeFreeform-0.1.8.apk，包名 io.github.mangi.flymefreeform，versionCode=5，4,682,204 bytes，SHA256 `4ad70f82278ef7113330d8e849d4bb9a0d748e403e78e91ce3a5aaff4443b2ac`。样本只作静态分析，未安装或启用，不将它在 HyperOS 上运行作为前提。

## 已确认的依赖与对应关系

“静态确认”只表示样本或本仓库中存在实现，不表示新功能已在设备操作通过。

| 参考能力 | 参考依赖（静态确认） | 本项目复用入口 | 状态 / 下一步 |
|---|---|---|---|
| 左右底角扇形快捷应用 | corner_left_enabled / corner_right_enabled / corner_targets_v2，斜向内上滑、扫选后松手启动 | FanRuntime、FanTriggerCapture、RuntimeTarget 与现有目标设置 | 复用原扇形；本批加入独立左右开关，默认均开，关闭的一侧不截触摸 |
| 固定应用与快捷方式 | pinned_targets、侧栏目录桥及桌面入口 | 现有原生 Dock、原生完整应用列表、已合并的应用/快捷方式目标 | 已有对应核心；不重新增加重复应用按钮或独立侧栏 |
| 窗外点击操作 | outside_tap_close_mode_v1、FlexibleTaskController / WindowState 相关 Hook | 现有 FanRuntime.performOutsideAction 与 HyperOsFreeformBridge | 已有关闭、迷你、全屏、贴边动作；本批不新增同类监听 |
| 拖条上滑转迷你窗 | handle_swipe_up_to_mini_enabled、FlexibleTaskScaleManager.getGestureMode | 小米原生底部拖条与 MiuiFreeformModeMoveHandler | 静态确认小米本身已有底部上滑迷你窗，保留系统手势；不重复安装识别器。顶部拖条不等同此行为，未改写 |
| 侧栏收放/目录 | com.coloros.smartsidebar:ui，UIService、PanelMainView | 本项目 NativeSidebarHooks 与小米原生 Dock | 小米侧已有接入；保留原应用列表、动画和中转站 Surface |

参考模块名虽为 Flyme 小窗，实际作用域是 system、SystemUI、com.android.launcher、com.coloros.smartsidebar，入口 tw0。目标平台依赖 Oplus FlexibleTask 系列及 ColorOS 侧栏，不能把这些类名直接加入本项目。其现代 libxposed 远端偏好与拒绝热重载策略也不替换本项目已有配置/重载通路。

参考关闭模式为 Disabled / SingleTap / DoubleTap，左右角默认均启用、触发范围默认84dp（24–160dp）。其暂停横屏/游戏开关与本项目既有方向/游戏避让对应；保留本项目用户配置，不导入参考默认值。

资源文案与Launcher输入跟踪共同支持底角径向扇形、跟手选择后抬手以小窗启动，普通点按不是该入口。参考最多六项、首次默认最近六应用；本项目保留既有目标和数量设置，不以此重置用户选择。参考资料没有显示本项目的二段蜂巢和独立侧滑组，二者是现有额外能力。188 起独立侧滑组已从产品入口和输入仲裁中移除，边缘侧滑由 HyperOS 原生侧边栏独占；旧的侧滑覆盖代码和偏好键暂留作回滚、兼容，不再可达。未运行参考模块确认动画或高亮，不能称视觉1:1一致。

### 拖条动作核对

参考 sd0 case0 在原 getGestureMode 调用后，仅当开关开启、原结果0、第二参数10且内部 task 处于 flexible 状态时返回4；样本本身没有提供系统返回码4的完整语义，也未自行计算轨迹阈值。不能把这些数字映射成小米模式。

小米样本为202602260 / 17.03.260226.r，与当前安装版本一致；两份本地 SystemUI APK SHA256 均为 `a0e738e41fe599b97950cbf52a9e2ddc6ae2ceff986efbacb1c9840bea78768d`。实际控制器代码在 Miui-WindowManager-Shell.jar，本地与设备 `/system_ext/framework/Miui-WindowManager-Shell.jar` 的 SHA256 同为 `6a03b772d0fbf16b9305f34da4c2006dec8d5cef66c4df99810c2fad0771eeb6`，已现场只读核对。定点源码确认 `MiuiFreeformModeController.handleTouchEvent(MotionEvent, InputMonitor)` 返回 `MiuiFreeformModeTouchResult`，底部拖条 touchMode=2 进入 MoveHandler.onBottomCaptionHandleMotionEvents；UP 时按负向位移及目标 scale 条件进入 mini。快速上甩另有关闭分支，不能把任何上滑都称为迷你窗。顶部 touchMode=1 走移动/贴边路径，不把两者混称。

返回对象中的 getFreeformModeTaskInfo 带有触摸选中的真实 taskId，MulWinSwitchEventHandler 也据此记住 mTouchedFreeformTaskId。后续若扩展动作，应使用该身份并与原生手势仲裁；当前 after-hook 只刷新几何，直接在其后追加 mini 会与原生动作竞争。代码以 getX/getY 比较缩放边界；静态材料不能单独证明这些值恒等于 raw 屏幕坐标。

## 分析证据与工具边界

APK manifest、Xposed scope、索引交叉引用及定点类反编译位于工作目录 work/flyme018，原始 APK、反编译文本和设备资料不入 Git。索引确认配置由 vw0.b / vo.E 读写，tw0 注册系统/侧栏 Hook；原生库为 DexKit 和图形路径组件，没有据此推定额外的 Flyme 原生窗口库。

分类结论：样本为可读取的 R8 DEX，Application 是自身类；所需行为主要在客户端 Java/Kotlin Hook。任务不涉及登录、会员、服务器授权、签名绕过或参考 APK 重签名；这些补丁路线不适用。本 fork 的既有签名与权限边界保留。

已运行 doctor 和只读 preflight。doctor 的通用能力汇总受 PATH 和无关原生工具项影响；本任务实际使用的本地 JDK21、SDK37 build-tools、droidasc 与 jadx 均有明确路径及调用证据。需要的层为 DEX 引用查询、定点反编译、Gradle/R8 构建和 Root 设备实测，不需要 native/Frida 补丁。起初 ADB 无在线设备，随后 preflight 确认设备恢复连接、Root 可用、SDK37/arm64、无代理且时钟一致。已有 Frida 进程属于当前环境，未停止或注入它。当前安装仍为176/200，SystemUI 为202602260 / 17.03.260226.r；尚未进行本批新功能验收，旧176记录仅作为对照。

## 不重复走的路线

- 不安装 ColorOS 参考模块来替代 HyperOS 实现，也不直接复制 Oplus Hook。
- 不以整窗顶部固定 dp 猜拖条命中区域；不使用“最近交互窗口”替代触摸所属 taskId。
- 不把源码存在、构建通过、安装完成、Hook 加载和真实操作通过混写。
- 不重新研究已停止的触摸面积，不新增 OCR、校准或操作页面。

## 当前批次记录

第一批源码177/201：现有 Miuix 底角组增加左右开关，ConfigContract / ConfigStore / ConfigProvider / GestureConfig 完整传递；原生输入 DOWN 按所在角落判断，FanTriggerCapture 仅创建启用角落的捕获 View，两角关闭时移除。重放/移交恢复保留左右状态，窗外/蜂巢原会话路径未新增 reset。默认两角均开；补缺省时不覆盖已有 false 或目标列表。默认系统 AICR V2 的上一批源码修改一并打包，保留用户已存选择。

build371整批构建成功（Debug/Release、R8、Lint），394项测试无失败/错误/跳过，Lint0错误。177仅arm64包20.48MiB，同签名一次覆盖安装，设备APK哈希与本地一致。SystemUI明确加载201，Portal激活/Root输入/reader201就绪；其他作用域未本批重载。

设备实际查看Miuix左右开关，关闭左角后保存false/true，配置生效后左右同位置点击只有右角armed；已恢复true/true。首次即时点击赶在异步偏好落盘前仍是旧配置，已与后续证据分开；日志epoch解析漏空格曾得空结果，修正后用原始日志确认。备用捕获通路未在本机强切，真实手指扫选/迷你窗/游戏横屏/侧栏拖出本轮未重新实测。Flyme对应能力在本批分别复用或记录，本文不代表参考模块的全部交互已在HyperOS实机验收。

178/202：复用原生触摸返回的TaskInfo（仅normal、非mini/pin），有效DOWN记录精确taskId，模块/原生侧栏区域不改目标；后续别的窗口状态回调不能盖掉它，失效/销毁/关闭/新启动匹配时清理。统一供几何、窗外操作、mini/fullscreen/close使用；不从after-hook新发上滑动作。build373回归与R8通过，真实多窗与遮挡场景尚未验；设备安装状态继续以CURRENT_STATUS为准。

188：FlymeFreeform 的底角扇形、应用/快捷方式/双开目标、HyperOS 自由窗和窗外动作已有对应实现，保留并继续验收。参考模块的 ColorOS 自由窗引擎、`com.coloros.smartsidebar` Binder 和底部横条重映射不移植；它们依赖 Oplus 私有服务，并会与 HyperOS 原生窗口和侧边栏重复。静态解包中未找到蜂巢交互证据，因此二段蜂巢仍标为本项目可选扩展，不称 Flyme 功能移植。参考模块“首次默认最近六个应用”值得后续单独实现；当前首次目标仍为随机可启动应用，本轮不改写既有用户目标。

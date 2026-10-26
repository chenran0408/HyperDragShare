# MMGG 当前状态与接手

核对日期：2026-10-26。唯一当前状态入口。Candidate A 仍冻结于 Phase 1A baseline `080c30c`。Candidate B 为 **1.7.219-beta.29 / 271**，源码`e49f48de9746254e1ca2922e7f02253c4c61f572`，APK SHA-256 `B920A46BDAC355C5CE1802F0344ACC5E04CD2330EE4D2B9E5D53F85564B17011`；设备验证结论是 **DEVICE NO-GO**：系统双指模式下单指仍触发MMGG面板。该结论供后续输入根因调查使用，本轮侧栏图标UI提交不改写Candidate B代码或产物。Beta29 APK与完整设备证据见`D:/CODEX/Tasks/2026-10-26/mmgg-candidate-b/outputs/`和`D:/CODEX/Tasks/2026-10-26/mmgg-beta29-device-validation/outputs/DEVICE_EVIDENCE.md`。本轮只处理安全中心侧栏中转站图标浅色背景对比度，详情见下方“侧栏中转站图标可见性”。没有连接设备、安装、发布或push。

## 在哪里继续

|职责|目录|分支/基线|
|---|---|---|
|Beta开发|`D:/CODEX/Tasks/2026-10-22/new-chat-3/work/HyperDragShare`|`beta`；Candidate B冻结输入基线`e49f48d`；其后仅有独立侧栏图标UI改动；双指实机结论DEVICE NO-GO|
|正式维护|`D:/CODEX/Tasks/2026-10-20/new-chat-2/work/HyperDragShare`|`repair-from-189@e25c66725cda51f23bdb8d95de036f7811eba017`；1.7.218/242，未改|

开始前核对status/log/worktree和并发写入。origin仅beta与repair-from-189，默认正式线；upstream原作者仓库不推送。分支策略见[BRANCHES](BRANCHES.md)。本轮整合了先前保留的RootNodeCaptureMain/Client差异，没有覆盖或丢弃它们。

## 双指长按触发资格修复（`f8213cf`；未增版、未生成Beta包）

- **现象与根因**：设置中的 `twoFingerLongPressEnabled` 会被 `PortalPressSettings`（有效系统模式优先、不可用时模块设置后备）读取并传给 Root parser，但 `PressCaptureSource.handlePointer(ACTION_DOWN)` 原先仍无条件启动普通单指 detector；`schedule()`因此安排120ms分类和确认时长 fire。未改生产代码时，回归测试复现单指在TWO_FINGER模式下 601ms/3601ms fire、单指→双指设置后的新手势仍 fire，以及WeChat Portal图片takeover绕过确认。Root capture结果原本会等 detector fire，但Portal image takeover有独立直达窗口路径。
- **修改**：Root DOWN时快照模式。SINGLE沿用原 detector；TWO_FINGER使用不安排分类的第一指候选。Detector自身拒绝候选阶段分类/fire；只有在候选窗口内合法第二指加入后，才沿用原双指时长/位移判断获得资格。Portal内容在确认前可缓存以接住宿主回调，但不启动pipeline、preview或feature；资格确认后再消费。Root capture worker与主线程返回、自动来源选择、display均校验资格及既有请求代次；失效的迟到图片被回收。OCR与Blocker只从已确认的图片分支启动。
- **指针与配置语义**：第二指加入后仍从其DOWN开始完整确认时长，这是当前 `handleContactEvent` 调用 `startTwoFinger(...eventTime, configuredTimeout...)` 的实际语义，代码和行为测试一致。第二指未确认时离开会取消候选；`twoFingerContactSequence`继续抑制余下第一指事件，不能退化成单指。模式在DOWN读取；设置变更会取消未确认的当前候选，新模式由下一次DOWN读取。
- **范围**：没有修改Controller、32ms/257、Root UP/CANCEL、Blocker、evdev参数、600ms/120ms/36px、阈值、控件避让、ControlRisk/Feature Gate、微信Hook或侧栏UI；Candidate A仍冻结于`080c30c`，其worktree未改。模块/系统双指时长、键和默认值未变。
- **本轮验证**：在未改生产代码时，22项先行测试有5项按预期失败，证明资格缺口。完成后触发/配置/evdev定向集75项、Phase1A等价回归52项及全量784项均零失败/错误/跳过；Lint 0错误、181警告、2提示；`assembleDebug`成功。DEBUG APK仅用于构建验证，SHA-256 `70E35F7F46C51C4A1D844C460A39B569D6EE81894CDC15B42C4B99EB4BCD64D9`，不作为交付。未运行Beta/R8、未增版、未生成Candidate B、未进行模拟器UI或实机验证。
- 细化证据、M1–M10与命令见 `D:/CODEX/Tasks/2026-10-25/mmgg-two-finger-trigger-qualification/outputs/DELIVERY.md`。本轮代码提交为`f8213cf`；Candidate B仍须单独跑Beta/R8并确认签名/产物后才成立。

## Phase 1A delayed callback owner 修复（实现提交 4a8ec68，尚未增版打包）

- **现象与代码证据**：旧 `onHostTaskCancelled` 在32ms回调执行时读取当前`active`，并在inactive分支停止当前 blocker、清理当前窗口；现有测试曾直接证明它可能取消回调排队期间新建的Session。`finishFromControlEvent` 同样在主队列执行时才读取当前active/session。
- **修改**：Controller主队列接受新的Root `ACTION_DOWN`时递增本地 generation。32ms回调捕获generation与Session身份；失主则直接返回，已结束但仍被引用的旧Session不再重复清理当前资源。257回调只接受到达时捕获的非空Session且执行时仍为同一Session。Root UP/CANCEL队列语义未过滤。`BackgroundTouchBlocker`生产代码和fallback顺序未改。
- **验证**：H1-H5、C1-C3、K1-K3、E1/E2针对性逻辑覆盖及生命周期回归52项通过；全量单测773项0失败/错误/跳过，`lintDebug` 0错误/181警告/2提示，`assembleDebug`成功。没有Beta/R8增版构建、实机测试、安装或发布；项目版本仍1.7.219-beta.28/270，既有APK不含本修复。
- **结论边界**：K3验证owner检查读到失效时不启动取消事务；检查返回true之后至底层系统API作用之间仍有无法原子消除的窗口，API不接受Session token。没有宿主View收到`ACTION_CANCEL`的回执，本修复也不证明微信长按菜单冲突根因。E3源码审查未发现旧MOVE重排到B的证据，`acceptPointerEvent`不变。完整任务记录：`D:/CODEX/Tasks/2026-10-25/mmgg-phase-1a-root-owner`。

## AICR 版本无关访问兼容（Beta28，当前）

- 静态比较设备活动更新版4.13.2与系统内置4.12.18：Core AIDL描述符/事务保持一致；4.13.2在Core `onTransact`前做调用方签名校验，4.12.18 Core Stub没有这道门。
- Beta27设备复测发现错误Hook使用了JADX显示的`defpackage.u8`，但设备DEX类描述符实际为根类`Lu8;`，因此Hook没有安装，AICR仍拒绝。该失败已明确记录，Beta27不作为可用修复交付。
- Beta28改为Hook Android `ApplicationPackageManager.checkSignatures(int,int)`；默认仅当Binder真实调用UID所属包包含MMGG、比较参数是该UID与system UID、调用栈属于AICR Core Binder `onTransact`时返回签名匹配。新增默认关闭的“全局兼容澎湃 AI 引擎签名校验”设置；开启时只放宽该AICR Core应用UID/system UID比较，仍校验普通应用UID、已解析调用包和Core Binder栈。设置通过AICR专用受限Provider RPC按请求读取；读取失败保持系统原结果。不会绕过AICR插件Status、版本、能力或其他权限检查。AICR不存在/不支持目标方法时fail-closed，保留原逻辑。
- 设备复测观察到Hook在AICR Core进程加载并命中；Core接口匹配、引擎就绪、Vision V2匹配、图片接受、检测完成、结果读取及释放均成功；用户确认页面已显示识别结果。4.13.2当前设备链路验证通过。4.12.18旧版没有该Core授权门，静态证据预期兼容但未在设备上降级安装验证。任意未来AICR内部调用方式变化不保证兼容；若不再用系统签名比较，Hook会不命中并保留原拒绝行为。全局开关只改变该已确认比较，不能保证越过其他AICR包/插件策略，也不代表AICR承诺支持所有调用方。
- 本轮全量758项测试0失败/错误/跳过，Lint 0错误/181警告/2提示，Debug与Beta/R8通过。Beta28 v2签名与Beta26相同；APK SHA-256见页首。源码提交`cdbaa4f`先加入版本无关Hook草案，Beta27实测发现并定位类名问题；`827bf2a`改为框架签名比较边界并增版Beta28。Beta27安装包失败草案留在任务目录，不作为候选。

## AICR 全局签名兼容开关（源码已完成，未增版）

- 在Integration设置中增加默认关闭的“全局兼容澎湃 AI 引擎签名校验”。开启后仅扩展上述 AICR Core 签名比较Hook到普通应用 UID；只在 AICR Core `onTransact`、Binder调用UID与待比较应用UID一致、另一端为system UID、且调用UID对应已解析包名时生效。未读到设置、Provider异常或证据不完整时都保留系统原结果。开关通过原有AICR专用 `get_aicr_settings` RPC读取，不新增公开入口。
- 这不是对澎湃 AI 引擎全部白名单或权限的绕过：插件包策略、版本、能力、引擎就绪状态及其他服务检查继续运行；无法保证所有Rom/应用/接口都能调用成功。启用后应用提交给AICR的内容仍由AICR处理。
- 本轮仅完成源码和Debug验证，没有修改版本号或生成Beta候选。全量789项单测零失败/错误；Lint 0错误、181警告、2提示；`assembleDebug`成功。定向AICR Hook与Provider设置测试强制重新编译后通过。源码改动以`0ed47a5`和后续静态引用收敛提交保存；未进行设备验证、Beta/R8、安装或发布。

## Beta26诊断批次（历史；由Beta28设备复测更新）

- **图片识字不支持**：源码已确认绑定→接口→引擎/Status→V2→传图→检测→结果读取链路。Status7是MMGG本地文案映射，反馈文案本身不能证明设备返回7；实际设备原因未知。新增每请求op和受控阶段，提示区分系统能力/接口、图片、连接失败，避免异常正文进入日志。没有加模型回退，原Bitmap和绑定释放策略不变。固定报告AICR/传送门版本，不扫描应用；版本相同不证明能力相同。
- **取词连接/占用**：review整合已有结构日志，补attempt与MMGG进程实例、请求/启动/拒绝/占用/成功/lease/Binder death/释放/重试间隔。模块拥有Root Session，Portal拥有lease；既有认证RPC汇入模块最近80条内存摘要，ZIP新增root-connection.txt，不依赖文件日志。不更改权限、Root权威、重试或资源释放策略。已有会话的占用者未知，不归因GKD或Scene。
- 能证明的改进是诊断和错误表达；没有设备复现，未声称修复供应商能力或真实竞争。日志缺失不直接判失败。调用链、ownership、阶段与上限见[OCR_CONNECTION_DIAGNOSTICS](OCR_CONNECTION_DIAGNOSTICS.md)，ZIP格式见[DIAGNOSTICS](DIAGNOSTICS.md)。

## 交付与实际验证

APK：`D:/CODEX/Tasks/2026-10-25/mmgg-aicr-connection-feedback/outputs/beta26/MMGG-1.7.219-beta.26.apk`；4,963,877 bytes。
SHA256：`36A8E3A36A769B2772E4CC654593AE392B477CC8AA5A34EE6A683573BA561759`。

- 产品fa3f60b；app tree `a13cccea867f47731f73ab3af13cc53ee2b73d51`。aapt核对268/beta26；v2验签通过，与留存Beta25同Android Debug证书SHA256 `b86a4a1d5ca870ea986585714fd2e9d349a6679fc8d7e1e682e1594d7c2c89c0`，不推定与正式包同签。
- 本轮针对性11类84项通过41s；全量137类751项零失败/错误/跳过，Lint0错误181警告2提示，Debug和实际Beta/R8成功2m44s。之后只补测试文件，实际Press协调器3秒重试/注册死亡回调立即恢复/ID关联1项单独通过16s，不重复运行无变更产品的全包。
- 源码提交：c1dbdd5（连接）、2e1c2db（AICR与版本）、fa3f60b（增版）；5619495仅补重试测试，APK仍对应fa3f60b。本轮完整任务、证据、JUnit、日志、mapping、签名及交付在`D:/CODEX/Tasks/2026-10-25/mmgg-aicr-connection-feedback`。
- 未覆盖真实ROM供应商Status、真实Binder进程死亡、自动化竞争和占用者、完整模拟器UI、设备、云端CI。安全摘要在模块进程重启后可缺失；完整运行日志仍需用户主动开启文件日志后复现。

## 侧栏中转站图标可见性（UI修复；实机视觉待验）

- `NativeSidebarHooks.Panel`是SecurityCenter侧栏中的模块自绘入口，不是`StashOverlayPanel`内容窗。图标为固定白色描边Drawable；旧闲置/active底板分别为低alpha白色`0x18ffffff`/`0x44ffffff`，没有稳定局部底色，因此在白色、亮壁纸和玻璃背景上前景与底板/背景融合。图标alpha本身为全不透明。
- 修复后沿用白色Glyph与48dp按钮/28dp图标尺寸；仅在该按钮下增加半透明深色scrim，选中时提高scrim强度并用宿主theme `colorAccent`细描边区分。点击、拖放、窗口、尺寸、位置及手势逻辑未变。颜色合成行为由`SidebarStashIconStyleTest`覆盖。
- 本地验证通过：全量786项单测、Lint 0错误/181警告/2提示、Debug构建；没有截图框架或设备视觉验证。明确标记`VISUAL DEVICE VALIDATION PENDING`。该UI修复独立于Candidate B冻结输入版本；提交号保存在本轮任务交付记录。

## 保留待办

- 侧栏中转站拖出此前复测正常；图标低对比度已有上述局部代码修复，但不同HyperOS浅色/玻璃主题下实机视觉仍待验证。普通文字拖拽仍缺具体现场原因，现有逻辑回归通过不等于所有设备通过。
- 微信原媒体/图片节点分类、单条消息载荷、Alook现场跳转、参考分词面板完整交互等旧待办保留。32ms回调已完成代码级owner修复，但微信菜单冲突是否由该竞态造成仍未确认，需后续Beta现场复测。
- 评论/消息正文原生长按意图冲突按用户决定延期，不重启触摸面积研究。Beta25双指/IME/实时预览的真实ROM交互仍需用户验收。

## Beta25 历史批次（已完成，非当前排查进度）

以下为Beta25当时证据。随后RootNode诊断已在Beta26单独整合；不再是待归属改动。
## Beta25 实际完成和证据边界

- 侧边栏中转站：用户复测正常，记录未复现，没有猜测性修改；不能据此关闭其他入口/接收方尚未确认的问题。
- 双指长按（Beta25当时实现，当前资格问题由`f8213cf`修正）：模块后备开关默认false及独立600ms/250–3000ms时长。优先只读当前用户Settings.System的`support_double_press_mode`、`support_long_press_mode`（Portal5.1.5离线源码证据）；不可读/缺失/未知回退模块。Beta25当时意图是系统双指模式不取消旧单指能力，但此实现被证明遗漏了普通单指detector路径；当前SINGLE与TWO_FINGER互斥，具体现状见本页“**双指长按触发资格修复**”。没有已证实的系统时长接口，时长由模块控制。活动拖拽仍按原多指取消，Root保持权威。确认后OCR正常抬指保留已接受请求，真正取消仍丢弃旧结果。
- 横屏小窗/IME：取消IME出现前排队的窗外点击；IME可见期间阻止普通窗外关闭，键盘隐藏恢复原规则，保留原隐藏键盘操作。沿用现有可见性/Insets和恢复尺寸，不以focus改变独断关闭。
- 拖拽菜单边距：复用原8dp/0–64dp字段，现代菜单原已消费该字段，修复设置禁用；适用于现代/简洁线性菜单，没有改圆形或原生传送门布局。
- 触发区域：新增`hot_bottom_gap_dp`默认0、0–200dp，与区域高度独立；实际命中、捕获窗口和预览共享几何。屏上被动预览实时画方向、范围、间隔与菜单近似位置；不拦截正常触摸，注册现有排除区域避免触发MMGG，退出页面/后台释放。
- 触控报告：每次重查当前算法库哈希；已知匹配不附库，不匹配尝试附库，缺失/读取失败保留报告和原因。复制仍执行原哈希/ELF/尺寸校验，不绕过采样校验、不恢复面积研究。
- 文字拖拽：现有普通文字、微信完成OCR后的文字接管和动作测试仍在且通过；缺少具体现场链路，未确认全局回归，不改全局手势。最小复现见任务交付。

配置/源码地图：[CONFIGURATION](CONFIGURATION.md)、[EXIT_LIFECYCLE](EXIT_LIFECYCLE.md)、[DIAGNOSTICS](DIAGNOSTICS.md)、[TOUCH_STREAM_DIAGNOSTICS](TOUCH_STREAM_DIAGNOSTICS.md)。本轮完整依据、逐项证据与恢复在 `D:/CODEX/Tasks/2026-10-25/mmgg-beta25-edge-settings`。

## 交付身份与本轮验证

APK：`D:/CODEX/Tasks/2026-10-25/mmgg-beta25-edge-settings/outputs/beta25/MMGG-1.7.219-beta.25.apk`，4,963,877 bytes。
SHA256：`2EB8F1EC159D47E9CA25BE074B4E0AC0A29CB425D9BB881ADD3B53B2B6B04BFA`。

- 源码b0d7c77；app tree `884c787a372842da16fff41120c8fa3ba257ac98`。从已提交快照隔离构建，456个跟踪app文件归一化hash全部一致；未混入原工作树中来源未确认的RootNodeCaptureClient/Main两份连接诊断改动。用户后续已授权单独review整合这两份改动。
- 针对性17类187项通过。隔离快照全量135类736项零失败/错误/跳过；首次完整链在Lint发现新增预览Typeface常量错误，修为语义相同的Typeface.NORMAL后，重跑受影响预览4项通过及Lint/Debug/Beta。最终Lint **0错误/181警告/2提示**；Debug与实际Beta/R8成功1m38s。完整736项与最终源码仅相差该命名常量修正，未重复运行不受影响全量。
- aapt核对267/beta25；apksigner v2通过，与留存Beta24同Android Debug证书：SHA256 `b86a4a1d5ca870ea986585714fd2e9d349a6679fc8d7e1e682e1594d7c2c89c0`。不推定正式版同签。
- 没有模拟器完整UI、设备、云端CI或Release任务结果；系统Portal键读取、ROM双指事件、IME真实时序和大字体/完整Miuix页面仍需实机回归。Canvas位图与逻辑测试不能替代设备交互。
- 本轮源码提交：7414ca8、d7b8e13、c2da433、d21620e、25e27ce、b0d7c77。没有push/安装/发布。构建日志、JUnit、Lint、mapping、签名和manifest见本任务outputs。

### Beta24 历史交付

Beta24/266源码bdd6bdf，tag `delivered/mmgg-1.7.219-beta.24`；APK在 `D:/CODEX/Tasks/2026-10-25/mmgg-beta-upload-cleanup/outputs/beta24`，SHA256 `B932D60E7CB3C2329AFD911BF099DC8418381C0FAE732412872BAFE67BC4A5B6`。历史本地仅assembleBeta/R8与签名核对；该轮未执行全量单测/完整Lint。历史云端Android checks run40112982633已成功（前版文档in_progress已失效）。Beta24历史推送不代表Beta25已推送。
### beta23交付（历史证据）

历史候选：`D:/CODEX/Tasks/2026-10-24/mmgg-usability-controls-alook/outputs/beta23/MMGG-1.7.219-beta.23.apk`，beta23/265，4,947,489 bytes；SHA256 `3A764D859C4A4FC9FB85DDFF0FDF66F959F111E4562633933D3591669C7B853C`。

- 源码fb55167，app tree `5ec6e6d1676b391ea4284fb0c0524f07a39d13e1`，main tree `5f10f2c9a83447b3f01a7e7b6c1708f50f4806a2`；本地tag `delivered/mmgg-1.7.219-beta.23`固定源码。aapt核对包名、265/beta23；v2验签通过，与留存beta22重新验签同Android Debug证书（SHA256 b86a4a1d…7c2c89c0），不推定正式包同签。
- 本轮实际`testDebugUnitTest lintDebug assembleDebug assembleBeta`成功2m14s，131类702项测试0失败/错误/跳过，Lint0错180警告2提示，Debug和实际Beta/R8完成。先前针对性9类100项通过20s。首次两次失败均为新测试夹具编译：SaverScope调用receiver和SDK37移除FingerprintManager导致Shadows重载解析；修夹具而不改依赖、生产逻辑或旧断言。完整日志、JUnit、Lint、mapping、签名、hash和源码身份见本任务`outputs/verification`。
- 085d2a3：手势页分通用、底角斜滑、侧边滑动、蜂巢、启动方式、外观布局。稳定目录ID、配置key/default和更新路径不变；分类目标分别归入对应触发方式，搜索类别名可定位。根层按模块/类别保存列表index/offset，退出再保存最后位置，搜索仅首次定位；分类返回先回目录，隐藏模块不拦截子页返回。测试实际生成生产目录、验证唯一归类和Saver往返；没有真实NavDisplay/大字体/深浅色页面交互验收。
- 246abe5：简短倍速标签补入原局部交互规则；精确复制/回复/转发等动作词只在明确Button/MenuItemView角色和局部边界下避让。裸TextView即使clickable+longClickable且正文恰为“转发”也不当菜单；“图片”ImageButton保留图片候选。原enabled=false语音/倍速兼容与滑杆/拍摄规则保留，Root权威、时长/slop、intercept/CANCEL、会话和资源释放不改。不代表无节点自绘倍速区或正文原生菜单冲突已解决。
- d5584b7：允许无限制通配host的通用浏览器进入选择列表，继续排除限定站点/路径/端口的处理器；路由仍沿用原implicit或selected package，不猜Alook内部组件。设置文案说明“跟随系统默认”会随系统变化，手动选择则固定；不重置用户选择。原生卡片PendingIntent和MIUI Intent URI的目标/数据/flags一致有替身测试；新增受控copy_direct_prepare记录，只证明准备，不能证明点击、打开或发送。Alook现场故障缺复制内容/选择模式/实际点击证据，仍待用户回归。
- e1d70bc独立保留上一轮迟到的诊断测试：idle host cancel不误取消、STATE保留输入间隔并区分event age；fb55167增版。主Agent review收窄了全Button/纯关键词避让、两个站点试探浏览器、准备过早记成功等中间方案，最终没有引入这些泛化。
- 最新用户决定：先做简单控件避让。长按评论/消息正文与宿主菜单共用输入，当前没有意图位；用户明确延期，未来在触摸面积适配完成后再评估，本轮不接回probe或启动研究。32ms竞态仍按beta22第一阶段记录保留；微信、中转站、436面板旧任务没有被此决定取消。
- 未覆盖：实机/完整模拟器UI、抖音和聊天实际节点、系统默认/Alook真实启动、跨UID/ROM作用域加载。没有本轮云端CI、Release构建、手机操作、安装、push或发布。后续先按本任务`outputs/DELIVERY.md`的小清单验收。

### beta22交付（历史证据）

历史候选：`D:/CODEX/Tasks/2026-10-24/mmgg-touch-stream-diagnostics/outputs/beta22/MMGG-1.7.219-beta.22.apk`，beta22/264，4,931,109 bytes；SHA256 `080551B689323EC365A2434056CF3D016964A7EA0321F4D03EF697A2DC3E0F03`。

- 源码f0464e6，app tree `a8852e090057d4303a5a939d22a3421cb3510b9a`，main tree `fa9960dfa9f1b6af662de8ceb6c7fecc95bcd22c`；本地交付tag固定该源码。aapt核对264/beta22与既有包名；v2验签通过，与留存beta21重新验签同Android Debug证书（SHA256 b86a4a1d…7c2c89c0），不能据此推定正式包同签。
- 最终`testDebugUnitTest lintDebug assembleDebug assembleBeta`通过1m56s，129类690项测试0失败/错误/跳过，Lint0错180警告2提示，Debug与实际Beta/R8完成。较早7类68项针对性通过33s；首次1失败为新focus日志断言错误要求字段相邻，修为同事件行匹配，active断言未弱化。之后补缺DOWN、重复cleanup日志边界并纳入最终690项；早期中间态构建不当最终结果。
- 9880ff6加入受控TouchStream、各输入/停止/阻断/焦点观察、导出与测试；332c45a增版；f0464e6仅限制重复空清理日志。日志默认关闭（既有DEBUG开启），MOVE每通道100ms采样，终端/状态不采样；Root evdev、Controller、Taplus MotionEvent观察与SystemUI明确分开。没有新微信Hook或作用域，时长/touchSlop/intercept/CANCEL策略与资源释放均不改。
- 逻辑层证据：无active时排host cancel32ms，16ms后新Root Session激活并MOVE，32ms时旧回调因只检查active取消新Session。第一阶段刻意保留原行为；不能把这个可复现代码竞态等同微信菜单现场已定因。没有正常/异常App的成对实机或完整模拟器UI记录；宿主View.dispatch/TouchTarget/focus未Hook，未知不能伪造。
- 架构、全部停止分组、10项结论、日志字段和第二阶段最小方案见[TOUCH_STREAM_DIAGNOSTICS](TOUCH_STREAM_DIAGNOSTICS.md)。完整要求/恢复/原始清单/构建证据在`D:/CODEX/Tasks/2026-10-24/mmgg-touch-stream-diagnostics`。前一反馈ZIP仍为beta20/262，用户已确认；微信文本载荷/独立及安全中心中转站问题保留在`mmgg-beta21-feedback`和原清单，不因这轮调查取消。

### 上一份beta21交付（历史证据）

`D:/CODEX/Tasks/2026-10-24/mmgg-beta20-wechat-image/outputs/beta21/MMGG-1.7.219-beta.21.apk`，beta21/263，4,914,721 bytes；SHA256 `E8825825E88120C8C07DF27A258374A1C117DDE437D1FFFFB83E9B03C5CB0F54`。

- 源码6669d17，app tree `0448ebda42368122f857bf23d341f86753e2aac2`，main tree `7433ff25ecbab027e1d882aa6e3cf16f39e5d0d8`；交付tag固定该源码，不以之后文档HEAD代替APK身份。aapt实际核对包名/版本，apksigner v2通过，证书SHA256 `b86a4a1d5ca870ea986585714fd2e9d349a6679fc8d7e1e682e1594d7c2c89c0`，与留存beta20重新验签一致（Android Debug，不推定与正式包同签）。
- 本轮最终`testDebugUnitTest lintDebug assembleDebug assembleBeta`通过1m56s，127类678项JVM/Robolectric测试0失败/错误/跳过，Lint0错误180警告2提示，Debug与实际minifyBetaWithR8完成。受影响7类77项先通过43s。早期诊断测试夹具4失败及编译失败有记录，修夹具不改旧断言；ef76f85中间态整链不充作最终源码结果。
- f651f89补固定图片来源/Portal/拒绝原因进入诊断ZIP，避免原自由日志过滤后无法分辨；ef76f85增版；6669d17按用户后续决定新增NODE_THUMBNAIL，只接收现有校验的微信图片节点裁剪，所有预览持续标“缩略图”。没有修改Root权威、RPC、配置、授权、释放、窗口触摸和动画策略；Portal媒体仍优先，泛OCR/普通截图/UNKNOWN继续拒绝。
- 没有实机、完整模拟器交互、现代Compose渲染或接收方读/发送证据。节点被微信隐藏时仍可能不可拖；缩略图不是原文件。独立中转站→微信失败暂无本次stash阶段，未宣称已修；旧MediaStore兼容副本保持。
- 参考与后续面板地图见[WECHAT_IMAGE_REFERENCE](WECHAT_IMAGE_REFERENCE.md)。用户参考App本身没有中转站，不能把残留源码视为实际运行入口。436面板移植/交互验收仍待做。完整任务、清单、恢复、构建/测试/签名/哈希及用户回归说明在`D:/CODEX/Tasks/2026-10-24/mmgg-beta20-wechat-image`。

### 上一份beta20交付（历史证据）

`D:/CODEX/Tasks/2026-10-24/mmgg-unified-diagnostics/outputs/beta20/MMGG-1.7.219-beta.20.apk`，beta20/262，4,914,725 bytes；SHA256 `1FC0D43850FDEF6453D82018CF185B9385FDECFE9EC6569C226A92FEE4D4F90A`。

- 源码118835b，交付app tree `c2dd5527224bb4e52d1750e06fa02255368dae8f`；本地tag `delivered/mmgg-1.7.219-beta.20`固定该提交。后续文档或单独测试不改变此已交付APK，源码身份以tag/清单为准。
- 实际aapt核对包名com.leaf.hyperdragshare.codex、版本beta20/262；apksigner v2通过，证书SHA256 `b86a4a1d5ca870ea986585714fd2e9d349a6679fc8d7e1e682e1594d7c2c89c0`，Android Debug，与留存beta19实际证书一致。未推定与正式包同签，未安装手机。
- 本轮125类660项JVM/Robolectric测试0失败/错误/跳过；Lint0错误180警告2提示，Debug与实际Beta/R8构建成功2m28s。没有本轮完整模拟器交互、实机、Release构建或云端CI结果。
- 普通用户更新说明/安装指导、构建清单、哈希在同目录；机器结果、JUnit、Lint、命令、aapt、签名在本任务outputs/verification。报告格式合成示例在outputs/diagnostic-bundle-format-example.zip，明确不是设备证据。

上一份beta19历史交付（保留原文件）：`D:/CODEX/Tasks/2026-10-23/mmgg-feedback-10-23/outputs/beta19/MMGG-1.7.219-beta.19.apk`。

- 产品源码d6d1f3a；4,881,913 bytes；SHA256 `FB87509F1FCCDB6224CA6C1CA5CCD7A518A5CAC485EE9A8DC8BF127AEB1E06D5`。
- 既有交付记录：572项JVM/Robolectric测试0失败/错误/跳过；Lint0错误、178警告、2提示；Beta/R8、版本与v2验签通过。签名证书与beta18一致，不能据此推定与正式发布密钥一致。
- beta19没有安装或实机/完整Android模拟器交互证据。历史4597c7a完成设置职责移动，a064f17完成配置更新；动作提取批次与本轮生命周期均只生成编译/R8验证包，保留旧元数据，不提供为新版交付，不替换上述安装包。
- 既有正式包1.7.218发布/设备证据保留于DEVICE_VALIDATION及归档。本轮没有重新查询GitHub Release资产/Latest状态、没有重新验签正式APK，不能用tag存在替代资产核验。

原始功能任务唯一完整依据：`D:/CODEX/Tasks/2026-10-23/mmgg-feedback-10-23/TASK_BRIEF.md`；同目录TASK_CHECKLIST和HANDOFF保留全部历史编号/证据（旧标题/前言是历史版本，最新状态按本页和提交核对）。原beta4任务位于 `D:/CODEX/Tasks/2026-10-23/mmgg-1-7-219-beta-4`。历史调查位于 `D:/CODEX/Tasks/2026-10-24/mmgg-baseline-audit`；备份/CI/设置拆分记录位于 `D:/CODEX/Tasks/2026-10-24/mmgg-settings-foundation`；配置记录位于 `D:/CODEX/Tasks/2026-10-24/mmgg-config-updates`；动作批次记录位于 `D:/CODEX/Tasks/2026-10-24/mmgg-controller-actions`；退出生命周期记录位于 `D:/CODEX/Tasks/2026-10-24/mmgg-exit-lifecycle`；操作追踪记录位于 `D:/CODEX/Tasks/2026-10-24/mmgg-operation-tracing`；本轮完整要求、清单、结果和恢复入口位于 `D:/CODEX/Tasks/2026-10-24/mmgg-unified-diagnostics`。

## 统一反馈与诊断、触控导出（beta20历史）

从ed97696干净Beta线继续，三组Luna high按后端、导航页面、触控专项分工，主Agent review和集中验证；稳定e25c667未改。无手机操作、安装、push或发布；本地交付tag只保存源码身份。

- **入口**：通用设置只有一条“问题反馈与诊断”，旧日志/兼容性报告/反馈/触控入口统一，搜索日志/报告/反馈/触控均使用生产目录元数据；状态诊断页有进入链接。保留原日志配置，未自动开启或切换日志目的地。
- **ZIP**：独立收集版本/环境、已有ActivationSnapshot枚举状态、兼容报告的受控字段和过滤运行日志/操作追踪。日志关闭、系统目的地、不可读或无可用记录时仍输出已有资料，manifest注明缺失原因和手动开启后复现步骤。自由正文、异常详情、图片、敏感URI/令牌不进入包；大小预算内保留最新受控日志，避免历史前缀挤掉最近操作。
- **触控确定缺陷**：旧UI在初始/查找状态隐藏导出，动作位于长列表后，非精确但有代码签名候选时适配选项也未显示。本轮固定底部按钮，只有实际按压采集和导出中暂不可点；无样本/连接失败/未知库/哈希不匹配/中断仍可导出已有基础报告。哈希不匹配用户现场本轮没有设备复现，不能把旧文案条件认作唯一原因。安全采样与导出分开，原校验、算法库选项/智能默认、机型适配和报告内容契约保持，没有重启算法研究。
- **一致操作/生命周期**：统一ZIP与专项报告均生成一次→SAF保存→打开分享面板，复用同一个文件和原read grant/ClipData/EXTRA_STREAM。重复点击与Picker结果只消费一次，取消可重来，退出后迟到结果不更新UI。保存与chooser打开失败分别简短提示；chooser打开不等于发送完成。使用应用Context与原有限worker，不修改图片拖拽资源/权限策略。
- **提交**：42e815e后端/隐私过滤/新窄报告路径，aecb3d5页面/旧入口/真实目录搜索，6c32691触控固定出口/状态和保存分享，118835b增版beta20/262。代码/报告地图见[DIAGNOSTICS](DIAGNOSTICS.md)，此前生命周期与追踪包含在这条源码祖先中。
- **测试与失败证据**：先抽取原显示条件，真实运行2项1失败，确认初始入口缺失；更早新导航测试缺图标import导致编译失败单独修正。首轮修复后8类36项通过，最终扩展后完整125类660项通过。复用既有测试，增加完整/缺失ZIP、配置不变、真实header/PFD未知字段、隐私过滤、大旧日志前缀/受控记录超预算保留最新、真实入口搜索、重复/取消/退出/迟到/save/chooser失败及触控各失败状态实际报告数据。原断言未改弱；失败日志/XML保存，不混作通过。
- **未覆盖**：真实Miuix布局/大字体底部Insets、系统保存选择器/分享面板、跨UID授予实际效果、真实算法库读取/特殊ROM生命周期；没有实机或完整Android模拟器交互结果。当前特殊机型适配仍保留，用户哈希不匹配现场和微信/QQ最终接收/发送需按交付清单自行回归。

## 中转站无变化调查（本轮，尚未确认为已修复）

新版已先交付。已对比独立入口与安全中心嵌入入口、旧成功版本与当前的真实差异，未找到可确认的图片准备/MIME/条目/flags回归，因此未凭猜测修改生产拖放代码。46b36db引入的MediaStore Downloads兼容副本与当前cache代码一致，docs/DEVICE_VALIDATION的1.7.166/190曾记录单图微信当前聊天接收；本轮没有新设备证据。b2f306e主要改文字原生拖放，270925e刷新长按时选区，895d179/ec038f5以追踪/证据措辞为主。嵌入入口确有来源UID/真实窗口变为SecurityCenter、module→host临时授权和透传的差异，但仍只是需要区分的现场假设。

交付后独立test-only提交e7c8d25补StashDragImageCacheTest，使用真实StashStore源文件与Robolectric假Downloads Provider，验证MIME、pending→ready、字节复制、复用、失效row重建、ready失败清理。新测试最初使用当前SDK不存在的android.test.mock，主review改为现有ShadowContentResolver注册，无依赖或生产变更；中转站5类37项针对性通过。随后testDebugUnitTest/lintDebug补跑54s成功，126类664项0失败/错误/跳过，Lint仍0错误180警告2提示，见outputs/verification/POST_DELIVERY_RESULTS.json。Beta20的生产main tree与补测试后相同：`d2be8b6a942c0b2fd4e427e4b8d7a9db8d71ea7b`，原APK/tag/哈希不变；实际Beta/R8证据沿用交付源码的2m28s完整链，不把本次test-only补跑写成再次R8构建。

后续按用户导出的beta20 ZIP对照drag_prepare/asset_ready、source/native start、bridge/native END和目标实际变化；需要区分图片未准备、未启动、drop未接受、已接受但后继不可观察。保留全部原功能待办，本轮不恢复旧填框透明窗或联系人选择器。

## 跨进程/异步操作追踪（上一轮历史）

从ce857e2继续，三组Luna high分别负责取词/动作、Provider/识别窗口、中转站边界，主Agent审查和集中验证。前轮生命周期四个提交保持独立，不重做；稳定e25c667未改。未操作手机、安装、push、发布或触发云端CI。

- **实现与证据修正**：沿用Root downTime、已有请求代次和logger进程实例，在会话/异步动作、Bundle/Intent/ClipDescription边界传递可选op；stage/asset、窗口请求/显示、动作本地结果、native拖放和Provider访问可关联。`startActivity succeeded`改为activity_launch_returned；PFD创建后才记descriptor_opened，明确read/delivery未知；主动host释放不同于实际观察到END。没有发现“已发送”成功Toast的代码依据，没有把历史用户现象直接认定同源或已修复。
- **保留兼容**：中转站仍使用MediaStore Downloads兼容副本，目标访问通常不经MMGG Provider；缺少MMGG读取日志不代表失败。原Root权威/取消回放、手势状态、配置/协议、URI权限校验/grant/revoke、Bitmap/文件释放/TTL、线程/超时/重试均未改。diagnostics缺失/关闭不阻断操作；未新增全局状态机、外部依赖或逐MOVE日志。
- **本地提交**：93e7560可选追踪基础与隐私测试；a07e5c8取词/识图/动作交接及生命周期关联；895d179中转站跨进程阶段证据；ec038f5将补发启动回调保留为reported_true/false、不将请求提前称为转发完成，读取失败提示也不保证原图仍存在。受验app tree=`fea2a81612ee68cdbe6f07ae5d33db0b610caf35`，后续文档提交不改变它。代码地图、责任/资源表、迟到/取消/超时判读及无私人内容合成示例见[OPERATION_TRACING](OPERATION_TRACING.md)。
- **本轮实际验证**：针对性12类82项通过；随后`testDebugUnitTest lintDebug assembleDebug assembleBeta`最终重跑成功1m52s，121类631项0失败/错误/跳过（比前轮新增13项）。Lint0错误/180警告/2提示；Debug及实际minifyBetaWithR8、Beta APK/mapping完成。Release不是本轮命令，不用前轮Release结果充作本轮通过。
- **行为覆盖**：默认旧动作/Bundle入口和追踪缺失、metadata/ClipData/extras内容保留、关闭日志、隐私过滤；拒绝/启动异常不误报launch返回；原授权调用/内置动作；取消迟到、新旧操作乱序/重复回调、旧动作失败不清新窗口、已进入执行器的动作在Controller销毁后仍完成；既有退出/输入/Provider/资源测试全部纳入全量。
- **review与失败处理**：修复捕获/拖拽ID前缀不一致；适配旧反射/Shadow夹具而不改原行为断言；恢复原失败回调测试，另增成功关窗测试。首次针对性81项中2项失败分别为新PNG夹具缺平台MIME映射、新Bridge阶段断言虚构START；补正确映射，并在END先到时记录resolved_from_end_unknown，保留原callback顺序。失败XML保留，不作通过证据。最终review又识别到END先到会合成START回调，已将Shelf回调日志改为reported_true/false并重新执行整条验证链；早先全量2m9s通过记录只作中间证据。
- **验证包身份**：元数据仍为beta.19/261，只作本地编译/R8验证，不提供为新安装版本、不替换原beta19交付。APK/mapping哈希、JUnit、Lint、日志、app tree和命令保存在本任务`outputs/verification`。没有本轮实机、完整Android模拟器或云端CI证据。
- **未覆盖/无法确认**：系统真实权限授予/撤销，SecurityCenter/Surface/Binder及ROM窗口时序，目标应用真正展示、读取完整字节及最终发送。独立/嵌入Shelf UI回调主要源码审阅，Bridge逻辑有替身测试。同asset可被多次操作复用，缺op时访问归因存在歧义；日志关闭/采集缺口不直接判失败。没有新增接收端回执，读取/发送仍保留未知。

本批完成诊断边界整理，不等于修复此前微信原图、分词面板、避让或中转站发送等全部功能待办；下方原任务列表继续保留。下一轮最小范围仍可按EXIT_LIFECYCLE记录，仅对BigBangPanelSession.close与BoomChipPage布局/动画退出补可控证据，不扩到输入框架；若优先修中转站发送，先用本轮阶段记录区分native交接与接收端处理，再决定必要局部适配。

## 退出/销毁生命周期（上一轮历史）

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
| `SettingsScreen.kt` | 导航/页面组装、设置目录、搜索定位路由和模块/类别滚动存档；根回调在执行时读取最新配置并同步内存状态；已移除copySettings |
| `SettingsCatalog.kt` / `SettingsCatalogPage.kt` | 目录元数据/搜索、手势用途分类和UI位置Saver；Miuix模块/分类页面、首次搜索定位与分类内返回；不持有独立配置副本 |
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
| 图片识字/扫码 | `AicrOcrClient`、`AicrBarcodeClient`；[系统接口复用手册](SYSTEM_TEXT_AND_BARCODE.md) | 复用系统AICR，不内置MLKit/ZXing双引擎；服务不可用与权限是不同问题。小米翻译接口未接通 |
| 中转站 | `StashStore`/`StashOverlayPanel`、`NativeSidebarHooks`/`SidebarStashIconStyle`、`EmbeddedStashDragBridge`、`SidebarDragWindowFlags` | 独立/原生侧栏路径并存；入口低对比度局部修复；嵌入式拖影、连接占位清理与拖动期宿主穿透已有逻辑修复；目标接收/发送未闭环 |
| 菜单/配置/界面 | `DragShareSettings`、`SettingsScreen`、`LinearMenuGeometry`/`CircleMenuGeometry` | 六位置/四外观、近手按左右拖向选边；底边触发带优先；合法配置保留；保存后提示生效范围；现代玻璃有回退 |
| 手势/小窗 | `gestures.xposed.FanFreeformHook`、运行时/ConfigStore | FanFreeform157来源整合；恢复侧滑入口/目标，窗外点击总开关AND方向键；底角DOWN修复用户报告已改善；设备矩阵仍不完整 |
| 复制直达/进程支持 | `AicrCopyHooks`、`RootProcessFrameworkHooks`、`TrustServiceRiskHooks`、`OverlayNotificationHooks` | 系统原生复制卡片/精确Hook；作用域加载与实际功能分别验收，不自动扩大系统保护 |

## 未完成、待验证与暂停

这些仍是原任务的一部分，本轮文档整理不取消、不代替修复。

| 原编号 | 当前状态 | 下一步具体缺口 |
|---|---|---|
| H02-IMAGE-FROZEN-1024 | beta19两处代码缺陷修复、逻辑通过；用户现场待验 | 59fd022修触摸源选择、5e5a4e3阻止show(-1)。beta17日志缺启动选中源，event7错误选择是强候选而非实机证实；复测同页面拖动/松手、ready源/显示/旋转。运行中切屏、同形屏/异常坐标比例未覆盖 |
| N03/B06及PANEL/FLICKER/PRESS-STABILITY | 部分实现，未验收 | fd80f87部分上游资源/排布；4dcd4d5/1bcabc6防跳动逻辑通过。继续按用户角度模拟滑选/取消/拖出/恢复、工具栏位置/边缘/滚动；不能宣称完整移植 |
| N05/B04/WX-ORIGIN/HISTORY | 微信分词用户确认有效；原媒体未完成；新增已授权缩略图兼容候选 | 6669d17只开放校验后的图片节点裁剪并持续标缩略图，泛OCR/普通截图/UNKNOWN仍拒绝。用户现场与节点可读性待验证；不把截图改名原图，不恢复系统朗读服务 |
| N06/END2END | 用户明确独立中转站拖进微信无变化，接收端仍未修复确认 | beta20包未观察到stash阶段，不能归因安全中心。旧版独立入口单图成功是历史对照；现版准备/native/drop/read/display/send仍须分阶段核对，MediaStore读不经过MMGG Provider |
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

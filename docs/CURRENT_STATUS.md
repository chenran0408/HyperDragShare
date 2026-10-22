# 当前功能与验证状态

## 218 原生复制直达、MiTrustService 与社区入口

源码、R8 Release和设备安装均为`1.7.216/240`。Release APK为4,220,129 bytes，SHA256 `E935AFE246CC82A0AB65E273BCBC66B797044722C40892C03358F6AB112B87AB`；设备base.apk回读哈希一致。Debug包SHA256为`06DAC8ECFE11993C123CB016E8026C778C4A75D6B410273C03A052BDFD3C93F2`，用于同一源码的Hook定位验证。

- AICR复制链路不再只按单个固定版本安装。已核对`4.12.18`与`4.12.26`的文本复制事件、复制直达通知和动作数据结构，分别识别`ji9/d86/a86`与`ul9/ka6/ha6`；运行时按类和签名选择，不把AICR versionCode当作启用门槛。后续若内部映射变化，Hook会记录缺失并保留系统原行为，不模糊匹配无关方法。
- 定位到日志拒绝点`TextCopyEvent`的来源判定：模块自己的`clipSource`与原前台包不同，被当作后台写入跳过。现在只改写来源为模块自身的事件，把它归属到AICR当前感知到的真实前台包；其他后台应用写入仍走原安全检查。
- 真机以程序生成URL点击分词复制后，LSPosed日志记录`module clipboard event accepted foreground=com.coolapk.market`，原页顶部出现系统原生复制直达卡片，包含“编辑所选文本”“打开链接”和“发送到平板”。没有读写用户聊天或正文。
- 关于页新增Telegram讨论群入口；全局搜索`MMGG`可定位关于页，实机截图确认“Telegram 讨论群”卡片显示。
- 通用设置的“运行与权限”新增“禁用风险检测”。MiTrustService实现改为拦截目标进程内对`vendor.xiaomi.hardware.mrm.IMrm/default`的`ServiceManager.waitForService`，不再依赖`Y0.h.b()`这类会随版本变化的混淆类名。开关切换后通过Root重启MiTrustService进程，让Hook状态重新加载；服务缺失时保持系统原行为。
- 当前设备的LSPosed作用域尚未勾选`com.xiaomi.trustservice`，因此只完成设置项、搜索、构建与Hook源码验证；风险检测Hook未在本轮加载，不能宣称真机功能通过。用户在LSPosed中启用该作用域并打开开关后才生效。
- `testDebugUnitTest assembleDebug`与`lintDebug assembleRelease`通过；R8及资源压缩后的Release已同签名覆盖安装。安装后AICR仍报告`1.7.216/240`的复制来源Hook已加载，混淆压缩没有移除该入口。

## 217 分词行距、溢出换行与复制直达回归检查

源码、Debug包与设备安装仍为`1.7.215/239`，本批没有改版本号。

- 修复布局测量画笔未使用默认15sp的问题；字符宽度现在同时参考系统`TextPaint`和按文字脚本计算的保守宽度，中文、日文、韩文及长链接不会再因估算过窄而在横排末尾被压成竖排。
- 横向溢出改为按词与句末标点组成换行单元：放不下的词连同其后的标点一起移到下一行，保持原文顺序，不让逗号、句号等孤立出现在新行开头。
- 词块使用布局计算出的实际宽度，横排中的普通词块垂直居中。行上下留白从`2dp`调整为`4dp`，段落间距从`8dp`调整为`12dp`，长词块内部额外增加`2dp`行距。
- `BoomWordsLayoutTest`、`CopyDirectTextTest`、`BoomAnimatorTest`共27项通过，`assembleDebug`通过。新增检查确认重新换行不会改变原文、分词边界或复制使用的选区范围。
- 真机使用77字符公开测试文本检查：长链接后的短词稳定进入下一行，句末标点跟随前词，没有词块被裁切或压成竖排；点击“中文”后显示“已选2/77”，复制日志长度为2，说明复制仍只取当前选区。
- 分词面板的直达浮窗可从视觉上被拆开的完整原始链接中重新识别出`https://example.com/...`，关闭直达后仍保留分词面板；本轮布局改动没有改变直达、复制或选区调用链。
- APK为`app/build/outputs/apk/debug/app-debug.apk`，75,292,626 bytes，SHA256 `FD9DE1B18B64430760E892A271AA6BA771DC869F3F3A19846878B9EA1270579D`，已Root同签名覆盖安装。

历史限制：217时系统AICR曾因`clipSource=模块`与前台应用不同而拒绝生成卡片；该问题已在218以来源限定的方式修复并真机确认。

## 216 四边菜单固定中点与现代菜单首帧优化

源码、Debug包与设备安装仍为`1.7.215/239`；本批未改版本号。Portal PID`5444`已加载注入、Root输入和取词reader，启动空闲期的现代浮窗预热日志返回`modern overlay prewarm completed elapsed_ms=856`，随后进程保持运行，没有重现Android 17禁止在`onDraw`内移除监听所导致的崩溃。

- 简洁、流光和现代线性菜单不再跟随触点沿边缘滑动：左右菜单固定在安全区域纵向中点，上下菜单固定在安全区域横向中点；拖动方向只决定菜单出现哪一边。
- 环形菜单展开后固定在所选边的安全区域中点，后续指针移动只更新选中项，不再重算圆盘位置或触发无效重绘。
- 现代菜单由一次性`Row/Column`布局改为`LazyRow/LazyColumn`，只组合当前可见目标；开场错峰延迟限制在前五项，滚动到后续目标时不会继承超过180ms的启动延迟。
- 现代窗口先准备内容再显示，避免“先显示空窗口、再切为有内容”的额外合成；分享目标枚举改到预览首帧绘制完成后的后台线程，避免79个目标图标首次加载和预览首帧争用主线程。
- Portal进程启动后使用屏幕外2×2透明窗口完成一次Miuix/Compose/窗口边界预热，把冷进程约0.8–1秒的首次合成成本移出用户拖拽；Root输入与取词连接仍在同一启动阶段继续完成。
- `LinearMenuGeometryTest`、`CircleMenuGeometryTest`和`assembleDebug`通过；最终APK为`app/build/outputs/apk/debug/app-debug.apk`，75,291,358 bytes，SHA256 `71718D318760FB72F4562AA0242DC49924356EF3F78A219CF1C943981CE9604C`，已Root同签名覆盖安装。

未确认：ADB注入不能可靠模拟真实手指持续拖动，因此本轮只确认菜单几何、进程就绪、预热首帧和崩溃修复；四种样式在左、右、上、下四边的真实落点与手感仍待物理操作验收。

## 215 中转站选中项拖出、底部输入区让位与链接直达入口

源码、Debug包与设备安装均为`1.7.215/239`。真实触摸日志确认旧Portal进程`20299`从15:28持续运行到安装后，仍在执行安装前菜单代码；后续单独重启作用域。最终性能优化包安装后，Portal进程`3072`已自报注入、前台服务、Root输入与取词reader均为239。本批没有改版本号，也没有把Debug安装写成Release验证。

- 中转站长按拖出改用`rememberUpdatedState`读取当前条目，避免LazyColumn复用槽位时闭包继续持有旧列表。日志只记录kind与8位条目ID，不记录正文或图片路径。
- 真机定向验证：从界面顶部第一条图片拖出，日志为`image:006747ae`；滚动后从文字卡拖出，日志为`text:36876219`，两次均进入系统`startDragAndDrop`。此前“拖文字固定变成某张图片”的条目绑定问题未复现。该证据来自App已知条目和系统拖放链，不涉及聊天正文。
- 所有菜单样式在底边中央共同留出输入栏直落区：距屏幕底部110dp以内且横向15%–85%会关闭已打开菜单并禁止再次打开；底边两角和其他边仍可打开。用户已在新Portal上确认输入栏处不再弹出菜单。
- 分词面板的链接直达图标统一先打开现有直达建议浮窗；点击图标不再绕过建议浮窗直接启动目标，具体链接仍由同一浮窗执行。
- 真实手指日志进一步确认旧链路会同时启动中转站系统拖放和Root按压识别：`startDragAndDrop`后约71ms，Root `InputBlocker`取消源触摸流，系统随即`DRAG_ENDED accepted=false`，此后只剩模块自带预览，因此微信没有可领取的内容。根因不是菜单让位或`ClipData`变化，而是两个输入所有者重叠。
- 中转站/分词浮窗等交互窗口把可见边界发布到模块Provider；Portal现在读取同一份边界，并在Root evdev的`ACTION_DOWN`落入模块浮窗时让出整次手势，不启动取词、不取消原触摸流。Provider只向SystemUI和传送门开放这份边界，未扩大其他配置读取权限。
- 16:30定向验证：中转站范围内按下时Root明确记录`ignoring gesture started inside module overlay bounds`；随后文字拖放从`16:30:22.238`开始，直到松手后的`16:30:22.902`才由系统广播`DRAG_ENDED`，未再被Root提前取消。该次投向空白处，因此`accepted=false`符合预期，没有发送内容。
- 微信真实静止长按基线为：按压确认301ms、区域截图与复核114ms、AICR 286ms、分词申请到首帧165ms；从确认到首帧约518ms，从按下到首帧约818ms。界面卡顿主要发生在首帧前的词块构建和开场动画，不在分词算法本身。
- 词块构建不再为每个词重复inflate`boom_chip_layout`/`boom_punc_layout`和外层容器，改为直接创建与原样式等价的TextView；文字测量使用独立TextPaint。长文本开场动画由最多12行降为前6行，保留首屏效果并减少大量并行View动画。
- 预热进程生成文本测试中，文本分词为1–6ms；180个词块、18行布局为62ms，简单文本布局为6–8ms。冷启动Activity另观察到首帧前的系统级跳过帧，不能把这部分与微信热路径混算；真实微信长按的优化后总首帧耗时仍需复测。
- 针对性`RootTouchSourceTest`、`OverlayTouchRegionsTest`、`BoomWordsLayoutTest`与`assembleDebug`通过并同签名Root覆盖安装。APK为`app/build/outputs/apk/debug/app-debug.apk`，75,280,350 bytes，SHA256 `1525562B0F70F27AC78E84312A1515AC8948957DFB9E4298C3A69A950513C5FA`；设备已安装base.apk哈希与本地包一致。

未确认：优化后的真实微信长按总首帧耗时和持续手感；真实手指拖到当前微信输入栏后文字/图片是否被微信接收；四种菜单外观在同一落点的实际手感。本批不以系统拖放存活或ADB注入拖拽代替这些物理交互结论。

## 214 微信拖图接管、两排分词工具与原生复制提醒

最终源码、交付包与设备均为`1.7.214/238`，应用显示名MMGG；Portal PID22138、注入/Root输入/reader均238，FGS就绪且错误为空。AICR/SystemUI/框架本批未专门重载，不据安装本身推定这些Hook为238。下文213及更早记录仅代表对应历史版本。

- 微信区域OCR完成后不再立即丢弃同次图片候选，保留至Root真实UP；之后超过36dp的MOVE可收起该次分词窗并接管图片拖拽。截图结果晚于MOVE时也记录本次拖图意图，避免只能比OCR更快地滑动。关闭请求带本次按压确认时间，不关闭后续窗口；原生Portal同次图片结果可接管，重复文本不另开窗口。不增加OCR等待时间，不把区域截图说成原图片文件。
- 分词工具改为两排Miuix图标：全选/取消、拆词、合并、复制、搜索；编辑、撤销、词典、翻译、直达、加入中转站。44dp点击区域、22dp图标，禁用态与无障碍说明保留。“更多”只保留窗口外观。用户本轮“加入回收站”按前文语境落实为加入已有中转站，不新增回收站或管理页面。
- 浮窗按实际可用窗口宽度限制，并保留两侧12dp余量；默认居中，旧偏移限制在可见范围内。保存操作复用原中转站API，加载/失败反馈放在现有标题区，不清空选区。
- 删除213自绘复制气泡，改用系统原生高优先级通知，约6秒关闭，点击直达目标或选择分词。Root启动/重载配置本模块POST_NOTIFICATIONS及`copy_direct`频道的悬浮提醒；不改其他频道、勿扰或全局设置。真实测试发现频道原先`canShowFloat=false`，精确设置并读回true后，系统横幅实际显示。
- 自有搜索/词典结果浮窗打开期间，复制直达入口不再重入弹窗。Root取词既有的本模块排除规则保留，网页原生选词复制不受这条通知抑制影响；完整键盘/返回物理交互仍待体验。
- 工程与应用显示名统一为MMGG（Make Mi Great Genesis，第二个G为Genesis），不使用备选昵称“米米GG”。首页、首次指南、关于、下载提示与中转站标题同步更新；包名、签名、设置键、用户数据及现有GitHub仓库不变。更新检查同时接受已发布HyperDragShare和后续MMGG同版本arm64资产，仍校验原包名与签名。
- 复制通知禁用系统自动上下文建议，只保留我们定义的打开和分词操作，避免添加获取应用/商店推荐入口。静态确认Portal还存在独立commerce复制卡路径，但尚不能证明用户看到的“小米浏览器”文案来自它；不凭猜测禁用其他通知或原生服务。此项无真实广告样本复验，不能称已消除所有系统广告。

最终`testDebugUnitTest lintDebug assembleDebug assembleRelease`用时1分45秒，417项测试零失败/错误，Lint、Debug、Release R8与资源压缩通过。APK仅arm64-v8a，4,221,449 bytes（4.03MiB），比213同条件增加36 bytes；SHA256 `806FC98AC4F586AEC77F2706100262B6B95DBC71FFB7A69EF0DDD220299755DA`，证书不变、设备base.apk哈希一致。交付`D:\CODEX\Tasks\2026-10-20\new-chat-2\outputs\MMGG-1.7.214-arm64-v8a.apk`。

已确认：214初包分词浮窗居中且两排图标完整可见。最终包安装后将本模块频道临时设为false，再从产品设置启动流程读回`canShowFloat=true`，验证Root配置实际生效；程序生成URL从受限入口触发原生横幅，实际只显示链接和分词，没有商店推荐按钮。没有读写真实剪贴板或发送用户页面内容。更名后检查更新的网络下载、完整广告场景尚未实测。

本批总共三次构建：首包、定位频道权限后的增量包、收齐广告开关与MMGG改名后的最终包；中间增量包未安装。共两次覆盖安装，未碎片增版。Git回退标签`snapshot-2026-10-22-214`。

未确认：本版微信真实静止/慢滑动手感与耗时改善，中转站新增入口实际存入/拖出，搜索结果内原生复制及完整返回顺序；不因417测试或Hook就绪宣称通过。小米翻译仅静态分析到签名权限/UID校验的文本翻译Binder SDK，尚未接入，见[XIAOMI_TRANSLATION.md](XIAOMI_TRANSLATION.md)。

本轮发现未跟踪的`docs/MMGG_INTRO.md`，不是本批创建，保留且不纳入本次提交。

## 213 可操作复制气泡与Root后台锁定（已安装，部分真机验证）

复制直达的受限`open_aicr_text`入口改为短时Miuix气泡，显示识别类别、链接及明确点击动作，支持选定浏览器、分词入口、滑动/外部点击/灭屏关闭和6秒超时；不占整屏、不接管键盘焦点。分词选区的直达小图标改为同一提示卡，删除重复图标，卡片高度参与布局。沿用现有AICR剪贴板监听，不新增system_server剪贴板Hook、通知权限或操作页面。参考APK只做行为分析，未复制反编译代码。

新增Root `ProcessManager`应用锁定，只针对本模块与Portal，启动/重载前补足锁定并读回确认，保护失败提示且不阻断FGS恢复。调用目标ROM原生锁定API，不改OOM文件、不定时复活；持久状态及恢复见ROOT_PROCESS_LIFECYCLE。已确认OneKeyClean曾杀adj200的宿主，不能用212前台服务修复替代这条独立原因。

源码/安装包`1.7.213/237`，Portal PID8376、注入/输入/reader均237，FGS就绪、错误为空。415项现有测试、Lint、R8与资源压缩通过；初次整批构建1分22秒，清理资源关闭的空catch后增量检查1分5秒，一次同签名覆盖安装。APK `D:\CODEX\Tasks\2026-10-20\new-chat-2\outputs\HyperDragShare-1.7.213-arm64-v8a.apk`，4,221,413 bytes，比212同条件增加16,388 bytes；SHA256 `BDA1934F26DDF57FAC65A5650DEA9F9436244C0377F18F908D7C8955CC06B7CD`，已安装base.apk完全一致。

真机已确认Root锁定`changed=2`，再次从安装APK执行`changed=0`；生成URL的受限RPC气泡可见，焦点仍为原MainActivity，点击打开Via，分词图标可打开原页分词与既有直达结果。未改剪贴板或聊天数据。未执行一键清理/内存压力/整机重启，未重验自然复制入口、键盘展开、手指滑动、完整返回层级及分词选区新卡片的真实使用；这些不称通过。微信速度与误拖依旧由用户统一反馈，本批没改微信分流。

## 212 重载启动与服务存活状态（已构建、安装；短时驻留已核对）

针对211日志中的Provider-only传送门以`adj935/umms_pressure`被回收，本批将设置页启动和作用域重载统一改为启动宿主已有`TextContentExtensionService`前台服务。删除旧Provider-only启动命令；服务创建/销毁随既有注入RPC上报状态，进程Binder死亡仍清空就绪报告。首页与重载完成条件现在核对当前版本、PID、前台服务及Root输入，按压开启时还要reader就绪。没有修改系统内存策略、增加定时复活或改动微信分流。

源码、安装包和Portal Hook均为`1.7.212/236`。415项测试零失败/错误，Lint、R8与资源压缩通过；本批一次完整构建1分15秒，一次同签名覆盖安装。APK：`D:\CODEX\Tasks\2026-10-20\new-chat-2\outputs\HyperDragShare-1.7.212-arm64-v8a.apk`，4,205,025 bytes，SHA256 `4D4E14620CE77AD58D5F7993E60350BA2D0B6F9816A56653C92408E741D08C38`。与211同构建条件仅差4 bytes，不是新一轮功能瘦身。

后台仅重启Portal并运行产品相同启动命令；PID22926、服务`isForeground=true`、`oom_score_adj=200`，注入/输入/reader均236，Root输入229ms、reader515ms就绪，错误为空。短时驻留结果见DEVICE_VALIDATION。未操作微信、未重启SystemUI/系统框架或整机；完整作用域重载弹窗本批未实点，不能将Portal部分核对写成所有作用域已验证。微信速度与误拖由用户统一实测反馈；长期内存压力及用户一键清理后的行为仍未验证。

## 211 系统分词/条码精简与微信按压优先级（已构建、安装；新交互待验）

用户本轮确认210真实手指结果为“文字单窗，图片可拖”，同时反馈微信文字仍慢且易误拖；系统 ICU 可正常分词、AICR 条码可由普通应用 UID 调用均已有实机结论，不再重复接口实验。211以系统 ICU 替换 jieba，删除 JNI/CMake、字典/HMM、复制解压初始化、预载设置及旧接口，不重置其他用户配置。ICU使用ROOT词边界与字素边界，保留混排原文偏移、组合字符和emoji。词典划分与jieba可能不同。

扫码以 `AicrBarcodeClient` 替换 `QrImageDecoder`/ZXing，沿用图片浮窗、扫码结果列表和Wi-Fi等取用逻辑。按已核对AICR4.12.26协议申请 `IBarcodeService`，版本为空字符串；注册结果监听、传Bitmap、消费BarcodeBean完整Parcel、取消/结束后注销释放。无本地引擎、模型或回退。OCR V2/NER V1协议保持原样。源码及使用方法见SYSTEM_TEXT_AND_BARCODE.md。

微信改为优先接收同一次按压中传送门已取得的真实文字/图片结果，废弃旧Root查询与OCR回调；没有宿主结果才继续现有窗口区域OCR。删除210额外400ms等候，区域拖拽门槛从36px改为36dp，减少手指漂移误拖。此分流已构建，**传送门在微信的实际命中率、速度改善和拖图防误触尚未实测**。

源码、APK与设备安装 `1.7.211`/235；414项测试零失败/错误/跳过，Lint与R8 arm64 Release通过，完整成功构建1分7秒。首次检查发现删除预载构造参数造成一处递归委托，已修正后重新完整通过。APK `D:\CODEX\Tasks\2026-10-20\new-chat-2\outputs\HyperDragShare-1.7.211-arm64-v8a.apk`，4,205,029 bytes，SHA256 `C2B1AB68CD6584AD9D94FD971B23E9601DAFE5355FDDBCB4B69868E6A78C3E1F`。与210同ABI、R8、签名条件比较减少3,206,243 bytes（43.26%）；ZIP无jieba/dict/HMM/ZXing项目，DEX无`com/google/zxing`，仅余arm64 AndroidX graphics path库。既有分词操作、扫码UI端到端本版未复测。

后台同签名覆盖安装未切换用户页面。先只用Provider唤醒的Portal21655在13:06:14被系统以 `adj935 umms_pressure` 回收，激活记录随Binder死亡清空，不能声称当时仍已激活。随后启动传送门自身现有前台服务（不改系统策略或用户设置），Portal8736报告注入/Root/reader版本均235，Root输入220ms、reader629ms就绪，错误为空；服务isForeground=true。**后续应改进重载命令仅唤醒Provider的启动方式，避免缓存进程被回收；211源码尚未修改这条重载命令。**用户正在支付宝/淘宝页面，不抢占作UI测试。

## 210 微信按压分流与构建耗时核对（已安装；新版物理手势待验）

后续用户已确认210“文字单窗，图片可拖”；211基于这一真实结果继续改进。下文“待验”为210安装当时记录。

209 的真实手指反馈：非微信页面静止按住文字和按住滑动图片均可用；微信文字只出现一个分词窗，但明显较慢；微信图片被当作 `ocr_region` 送去文字分词。对应 logcat 在酷安显示图片预览与四边菜单；微信语义查找约 43–158ms，确认后截图/复核约 90–140ms，AICR 约 233–601ms，首帧通常在确认后 323–718ms。微信连续触发曾遇 AICR 单请求忙，未见本轮 Root reader 因安全区域拒绝而断连。图像区域没有稳定节点，不能单靠 `ocr_region` 判断内容类型。

210 对微信的这一类按压并行启动现有 AICR，同时观察已确认手势：保持静止则一次性显示分词窗；在文字窗展示前滑动超过 36px 则取消 OCR 展示并把已截区域交给图片拖拽，保留原有 Root/WMS 窗口验证。OCR 完成后最多等到确认后 400ms 再决定，松手可提前结束等待。此路由针对用户的“静止取词、按住滑动拖图”行为，截到的仍是安全窗口区域，不保证等于原图文件边界。Root 已就绪时重复接收、随后丢弃的 MIUI 触摸监听已移除；传送门原有取词回调和 Root evdev 仍在。

源码、APK、已安装为 `1.7.210` / 234。416 项既有测试、Lint、R8 arm64 Release 均通过；签名包 `D:\CODEX\Tasks\2026-10-20\new-chat-2\outputs\HyperDragShare-1.7.210-arm64-v8a.apk`，7,411,272 bytes，SHA256 `1622258AF9101BC0CCACB6898769B175E30FF68A47D9E9A494DBD97CC2D0CBEA`。同签名覆盖安装后只重启传送门，Portal PID13949，Root输入205ms、reader488ms就绪，激活版本均234、错误为空；微信前台未切换，未操作聊天。**210 的微信静止/滑动分流、真实图片内容与系统设置页 OCR 宿主仍未实际验收。**

本批构建首轮因删除备用监听时漏保留一处 Root-ready 查询而编译失败，修正后完整 `testDebugUnitTest lintDebug assembleRelease` 在热 Gradle Daemon/4 workers 上用54秒完成；209 冷启动同组检查用1分45秒。Profile 中 Lint 分析29.6秒、R8 27.2秒、单测18.8秒并行，Kotlin 编译不足1秒。日常改动先用增量编译，完整批次再集中跑单测、Lint、R8；不为假设性加速改动 Gradle 配置。

## 209 按压、截图拒绝与识字窗口合批修复（已安装；手势已有反馈）

针对用户在 206 上报告的微信两阶段弹窗、按住滑动无法拖图、其他应用失败后取词断连，209 删除微信 OCR 的提前等待面板及回填 RPC：先在原页完成 AICR，再一次性打开分词窗。确认长按后的 MOVE 不再取消同一手势，图片预览从手指最新位置开始；图片捕获晚于松手则取消本次拖拽。Root helper 的截图安全区域拒绝和无效图片现在只拒绝该次请求，不再关闭整个 Root 连接。208 的 Provider 非稳定握手与 Binder 死亡重连一并进入本安装包。控制中心屏幕 OCR 的透明入口现在承载区域选择、结果与后续分词窗口，避免 Settings 页的普通悬浮窗被系统隐藏；该可见性仍需真机确认。

源码、APK、已安装版本均为 `1.7.209` / 233。`testDebugUnitTest lintDebug assembleRelease` 通过，416 项测试零失败，Release R8 与 arm64-v8a 签名检查通过。APK：`D:\CODEX\Tasks\2026-10-20\new-chat-2\outputs\HyperDragShare-1.7.209-arm64-v8a.apk`，7,411,276 bytes，SHA256 `D2059B809A089D159D2BFB062804FE3488DBEAE12A3C0B7B9606ED5E55C32759`。同签名后台覆盖安装后，仅重启传送门作用域；设备显示 233、Portal PID 11141，Root 输入 232ms ready，取词 reader 539ms ready，报错字段为空。前台 TIM 保持不变，未触碰聊天内容。**尚未实际按住文字/图片，不把通道就绪称为交互通过。**

用户随后完成真实手势，结果与日志见 210 条。Root 文件日志最后写于 10 月 20 日，不能用于解释 10 月 22 日手势；本次使用当日 logcat。raw 截图失败后的 PNG 重试和分享边界的宽泛捕获仍只是审查候选，未证明造成现故障前不盲删。

## 208 取词断连自恢复与桥接目标修正（构建通过，设备未安装）

核对旧分支的 `am_kill ... depends on provider ... in dying proc` 记录后，确认当前分支仍在整个取词租赁期间持有稳定 `ContentProviderClient`。208 改为仅在打开 Root 租赁时短暂使用非稳定 Provider 客户端；后续只保留租赁 Binder。模块进程退出时，传送门监听该 Binder 死亡并立即重连，连接失败按 3–30 秒退避，持续失败才提示，不再要求用户手动重载作用域。原生侧栏的 Provider 握手也使用非稳定客户端，避免同类进程依赖。

207 图片菜单从传送门进程启动 `ImageOcrActivity` 时曾使用传送门 Context 构造显式 Intent，组件包名会落到传送门自身。208 改为明确指向本模块包名；另在锁屏恰好发生于透明 Activity 已启动、分词面板尚未创建时结束宿主，避免留下空白窗口。微信 `ocr_region` 分流、图片快截图和原页拖拽未改。

源码 `1.7.208` / versionCode 232；`testDebugUnitTest lintDebug assembleRelease` 通过，415 项测试 0 失败/错误/跳过，Lint 无阻断错误，Release 执行 R8、资源压缩与 arm64-v8a 打包。签名 APK：`D:\CODEX\Tasks\2026-10-20\new-chat-2\outputs\HyperDragShare-1.7.208-arm64-v8a.apk`，7,411,272 bytes，SHA256 `20CD897F3C4B6C63921F8331CA36770351D61942D050B2D1A65FC8B2AD3F7AC2`。预检设备最后安装仍为 206/230，屏幕点亮且前台有系统界面，因此本批没有后台覆盖安装或重载。208 的真实进程断连恢复、Settings 桥可见性和图片菜单识字待设备空闲时验收。

## 207 按压交接与系统设置窗口桥接（源码和构建通过，未安装）

从 206 接续：Root 按压确认后以同一次 `downTime` 标记取词流水线，直到分词或拖拽交接结束；传送门在此期间不再重复启动宿主卡片。控制器和回调的 MOVE 逐帧日志已收敛，避免冲掉关键时序记录。新增一个针对按压归属与释放的单测。

WorkBuddy 的 `D:\project\workbuddy\2026-10-22-02-56-31` 记录了旧分支 190–205 的尝试，只作为线索核对。其设备窗口记录显示系统设置页上模块的 `APPLICATION_OVERLAY` 可创建但策略未显示；这不是当前 207 的实测。207 对系统设置来源的文字分词，以及拖拽菜单的图片识字/扫码，改用现有透明 `ImageOcrActivity` 承载 `TYPE_APPLICATION` 窗口。图片识字后继续在同一宿主显示分词；该宿主上的搜索结果也用相同窗口类型。普通图片拖拽预览、四边菜单仍保留非焦点悬浮窗；微信 `ocr_region → AICR → 原分词浮窗` 路径未改道。

源码 `1.7.207` / versionCode 231。完整 `testDebugUnitTest lintDebug assembleRelease` 通过：415 项测试、0 失败/错误/跳过，Lint 无阻断错误，R8/资源压缩/arm64-v8a Release 成功。签名 APK 位于 `D:\CODEX\Tasks\2026-10-20\new-chat-2\outputs\HyperDragShare-1.7.207-arm64-v8a.apk`，7,411,272 bytes，SHA256 `017A117500454CBBB02B7CC325EBC49E3D8B0EF581BF29B91F7FF543FE79D236`，证书 SHA256 `b86a4a1d5ca870ea986585714fd2e9d349a6679fc8d7e1e682e1594d7c2c89c0`。用户今晚不便真机操作，207 **未安装、Hook 未加载、系统设置页窗口实际可见性未验证**；设备目前仍是 206/230。本次没有操作手机或私人内容。

下一次集中验收：在系统设置页拖拽图片到识字/扫码，核对透明宿主、结果与搜索是否可见；从系统设置文字按压打开分词；再测普通图片拖拽与微信文字分词不回归。若系统限制后台启动透明 Activity，按实际 Activity 启动日志定位，不以 `startActivity` 返回当作窗口已显示。

## 206 从 189 恢复图片拖拽主链（源码、构建、安装和 Portal 就绪；物理拖拽待验）

分支 `repair-from-189` 从 `5970268` / 1.7.189 建立，没有把 190–205 的整段历史直接合入。核对 `189_BASELINE_PORT_NOTES.md` 后，保留微信 `ocr_region → AICR V2 → 分词浮窗`；普通图片优先由 Root helper 在目标窗口截图、复核包名和窗口 ID、裁剪后交给既有拖拽预览。helper 不可用时，Root `screencap` 优先读取 raw RGBA，设备不返回该格式时使用原 PNG。日志只记录触摸状态变化，异步取词结果丢弃时记录原因，不记录正文。图片扫码结果新增 Wi‑Fi 二维码信息和复制入口，仍使用 189 的 ZXing 识别链。

源码 `1.7.206` / versionCode 230。`testDebugUnitTest lintDebug assembleRelease` 一次完整构建成功：414 项测试、0 失败、0 错误；Release 执行 R8、资源压缩和 arm64-v8a。签名包 `outputs/HyperDragShare-1.7.206-arm64-v8a.apk` 为 7,411,276 bytes，SHA256 `25107F8F1D790651F25544665A946A946C8E4054DBE6AC1F1703BEE57B6A4E18`，证书与 189 相同。设备同包名覆盖安装成功；仅重启 Portal 后 PID 25068 的 Root 输入 216ms ready、取词 reader 449ms ready。设备仍停留微信 LauncherUI，没有自动发送内容、截图或改动聊天。

尚未实测：物理手指在图片上按住并拖到四边、菜单落点、目标应用接收、Wi‑Fi 扫码展示，以及本包微信识字无回归。Portal ready 只证明运行入口就绪，不代表这些交互通过。系统框架、SystemUI 和原生侧栏的当前 Hook 版本未因本轮热重载核验。

## 189 图片按压接入拖拽预览（已构建、已安装，物理拖拽待验）

用户在小屏 WLAN 分享页反馈“当前界面无法拖拽”。日志显示 Root 取词已经成功取得二维码附近图片，但非微信图片路径固定调用 `ImageOcrOverlay`，因此只能进入图片识字，未创建可移动预览，也没有机会到达四边分享菜单。本批修正的是这条实际可达的工作流。

当前行为：微信普通页面继续使用已经验证的 `ocr_region → AICR V2 → 同一分词浮窗` 快速路径；其他应用的图片按压结果直接交给现有 `DragShareController`，按压后显示图片拖拽预览，拖向屏幕边缘打开菜单。图片识字和扫码仍可从同一菜单选择，没有新增页面或按钮。

源码 `1.7.189` / versionCode 213。整批 `testDebugUnitTest lintDebug assembleRelease` 通过：406 项测试，0 失败、0 错误、0 跳过；Lint 0 错误、188 警告、2 提示；Release 实际执行 R8、资源压缩且只包含 arm64-v8a。APK 为 7,413,599 bytes，SHA256 `1DA13F528CB29188E75661988D04BDB9D7C557F730AB73C80B9AFD64461C9C29`，已同证书覆盖安装；设备 `base.apk` 哈希与交付包一致。

安装后通过无副作用 Provider 查询重启 Portal，PID 9040 加载运行时；Root 输入 218ms ready，取词连接 531ms ready。当前前台仍是原 WLAN 分享 Activity，未打开模块页面或读取用户内容。旧 188 的真实日志已证明该页面可捕获图片，但新 189 的物理手指“按住后拖向边缘”尚未验收，因此只能写为源码、构建、安装和 Hook 已就绪，不能写成真实拖拽已通过。

## 187 Root-only、分词直达与界面收敛（已构建、已安装，界面最小验收）

用户明确本应用只面向 Root 用户。187 从发布包移除系统无障碍服务声明及两份服务配置，删除无障碍服务运行时、活动状态、截图适配、长按状态机和对应测试；“传送门／无障碍”来源选择、无障碍授权弹窗、横屏识别与识别灵敏度入口也一并删除。旧 `content_capture_mode` 即使仍为无障碍值，也不会再改变产品运行路径。

保留的 Root 主链不变：传送门 Hook 提供当前宿主触点，Root evdev 判定按压时长与位移，Root helper 内的 `UiAutomation` 读取节点或截取 WMS 核验后的微信安全区域，AICR V2 识别图片并把结果回填同一分词浮窗。这里没有把 Root helper 的 `UiAutomation` 误当成系统无障碍服务；其屏幕、Binder、文件和系统进程 Hook 边界继续保留明确失败反馈。

分词界面同批改为单行五个高频图标：全选／取消全选、拆词、合并、复制、搜索。分享和“清除部分选词”不再出现在界面；需要清除部分内容时先全选，再点掉不需要的词块。编辑、撤销、词典、翻译、直达和窗口外观收进现有“更多”，直达结果使用 Miuix 浮层并保留原选区。复制反馈显示在标题区，不再与开窗后的 Toast 时序错位。

选中的内容能明确解析为链接、邮箱或电话时，主工具栏会按需增加一个对应图标，并在同一浮窗内一键执行；没有选区时不增加按钮，避免识别按钮常驻或把整段普通正文当成链接。混合文本仍可从“更多 › 链接、邮箱与电话直达”查看可选实体。点击直达结果会先关闭结果层，搜索关闭或直达取消后回到原分词与选区，不再经过中间页。

二维码入口从文字分词及“更多”删除。普通文字不携带 OCR 来源图片，也不再为二维码额外复制图片 URI；二维码只保留在图片识字预览中，入口含义改为“扫描此图二维码”，由当前原图直接解码，不开新 Activity。间隔空格形成的链接、Unicode URL、邮箱和电话改为在用户执行对应动作时重建，界面显示、复制、拖出和中转站仍使用未改写原文。

`testDebugUnitTest lintDebug assembleRelease` 成功：406 项测试，0 失败、0 错误、0 跳过；Lint 0 错误、188 警告、2 提示；Release 实际执行 R8、资源压缩和仅 arm64-v8a。签名 APK 为 7,409,447 bytes，SHA256 `100E7E101DE5A19B004446BB0B4D89E69B90E3C85421E213658340059A586ED7`。已同证书覆盖安装 1.7.187 / versionCode 211，设备 `base.apk` 与最终交付 APK 哈希一致。

显式 `TextSegmentationActivity` 使用合成文字验收：普通文本主栏显示全选、拆词、合并、复制、搜索五个图标；“更多”显示 Miuix“文本工具／窗口外观”，不含分享、清除部分选词或二维码。使用 `https://example.com` 验证按需直达时，未选词保持五项；点全选后显示“已选 19 / 19”，并增加链接图标与搜索图标，没有执行外跳。首轮实现曾在选词摘要帧把尚未生成的空正文赋给非空状态并崩溃；R8 映射还原到 `BigBangPanelSession.updateSelectionSummary()`，已用显式空字符串修正根因。最终构建重新安装后正常显示和返回。图片预览中的真实二维码扫描未在本轮重测，Root/Portal Hook 未因本轮 UI 安装重载。

## 186 微信直接 OCR 真机通过（已安装）

1.7.186 将微信安全窗口的按压截图移到常驻 Root helper，由同一 helper 在 `UiAutomation.takeScreenshot()` 后复核微信窗口并直接裁出安全区域，经 JPEG/Base64 返回 Portal。常规微信长按不再启动独立 `su screencap`、不再暂存图片或创建中间识字窗；AICR V2 结果直接回填已打开的同一分词浮窗。

设备为小米 15 Pro / HyperOS 4.0.0.17 / Android 17。已覆盖安装 1.7.186 / versionCode 210，安装时间 2026-10-22 01:44:26，未清数据。Portal PID 23660 自报注入、Root 输入、PressReader 和 Portal Root 均为 210，输入与 reader ready。测试使用 Root evdev 注入长按，不是物理手指验收，也未读取或输出聊天正文。

微信普通页面实测两次有效完整链路。首轮冷路径中，120ms 预分类查询 55ms，按压在 251ms 确认，Root helper 截图 219ms、窗口复核 58ms，截图总耗时 382ms；之后 AICR 587ms，已识别文字并回填浮窗。热路径中，查询 45ms，确认后 65ms 完成截图交接，156ms 面板首绘，AICR 294ms 完成，84 字直接交付现有浮窗。另一条同会话记录为 192ms 截图捕获、250ms 确认、156ms 首绘、288ms AICR，84 字回填。相比旧 `<screencap>` + 模块侧复核约 797–1066ms 的捕获阶段，新 helper 路径在本轮记录中约 190–382ms。

日志明确出现 `WeChat OCR direct start`、`window first draw`、AICR `stage=image_accepted/text_detected/text_recognized/released`、`WeChat OCR finished characters=84` 和 `WeChat OCR result delivered to existing panel`。结论限定为源码实现、构建通过、已安装、Portal/Root Hook 已加载、Root evdev 注入操作通过；物理手指手感、长时间稳定性和其他聊天页面仍需后续验收。

APK：`outputs/HyperDragShare-1.7.186-arm64-v8a.apk`，7,446,367 bytes，SHA256 `03048CF12E9EB86E5250F7D93FF00F18A40783696362BCFC12E233E15A88C77D`。Release 启用 R8 与资源压缩，仅 arm64-v8a，签名证书 SHA256 `b86a4a1d5ca870ea986585714fd2e9d349a6679fc8d7e1e682e1594d7c2c89c0`。

## 184 微信按住识字直达分词浮窗（构建完成，设备待加载与实测）

用户反馈微信长按比其他应用慢，且先打开独立“图片识字”窗口。根路径是微信安全窗口的 `ocr_region` 捕获结果被当成普通图片，先暂存图片，再经 Provider 打开图片识字浮窗；识别后又关闭该窗并新建分词浮窗。本版仅为 Root press 返回的 `ocr_region` 增加来源标记，直接把同一截图交给现有 AICR V2，并立即显示分词浮窗内的识别进度；识别文本填回同一个浮窗。普通图片识字路径不变，不按包名把所有图片改道。取消仍遵守 AICR 的 bitmap 生命周期：调用取消后等 Binder 识别回调再回收图片。

回查用户提供的428 APK（包 `com.mengting.bigbang`，版本1.7.0/code428）发现其长按后约4秒触发Root截屏分支：`s6` 使用Root `/system/bin/screencap -p`，裁触点附近区域；先探测文本，未读到文字再做OCR，识别结束后才打开分词。旧图片OCR链还会按图像特征查找微信本地图片缓存原图，这不属于文本长按主路径。

183直接AICR路径第一次构建并安装后，源码复核发现识别前已出现可交互全屏面板，但识别结束又调用 `verify_source`。该检查要求焦点仍是微信，窗口已被本模块浮窗接管，因此会自我否决。184修正：保留捕获阶段已完成的微信窗口与可见区域复核，移除弹窗后的重复焦点校验和额外Root往返；仍跳过图片暂存与中间识字窗。

184执行 `testDebugUnitTest lintDebug assembleDebug assembleRelease` 全部通过。R8/arm64候选包签名并由apksigner校验通过：`outputs/HyperDragShare-1.7.184-arm64-v8a.apk`，7,446,367 bytes，SHA256 `143A64D4A6A0EF26A4C181B5B78BCE68FD83EE247ABE67B7D132026FDD586BD7`。设备已覆盖安装184并确认Portal PID 13444加载：日志见portal hooks installed/runtime started；Root helper PID 12729连接成功，press reader ready，约465ms。AICR将在下次绑定时载入新代码；SystemUI未重载。设备真实微信长按日志已多次显示AICR结果回填同一分词面板；首帧仍约0.89–1.22s，用户观感待确认。AICR日志只记录识别耗时与字符数，不记聊天文字。428 APK SHA256 `5C18D11B3169CED658809039473320FA7D0F6EAAC05DEC393E739C24B362441D`.

最新发布：**v1.7.179 正式版 / Latest**，源码tag指向 `87fd4957eff3d093f856a83d74bf7ed62288367b`。已上传arm64 R8 APK及SHA256文件，服务端APK大小7,429,983bytes、digest `88a293ee4adf49d90b2c07dd052fbd8c298963a3de0a201bd35cfae255af29bb` 与本地一致。发布地址：https://github.com/chenran0408/HyperDragShare/releases/tag/v1.7.179 。下文“Latest仍176”是发布前历史状态，不再代表最新正式版；发布没有增加新的功能实测结论。

## 181 微信按住取词日志定位与修正（已安装，重载后待实测）

用户反馈HyperOS 4微信不能使用普通无障碍。适配不依赖微信无障碍服务：Portal内Root evdev监听按压时长/位移，Root `UiAutomation`读取可访问窗口内容；微信不向其公开文字时，使用WMS窗口核验、Root截图安全区域和AICR OCR。不要将系统无障碍作为微信功能前提。

日志确认：设备端按压确认约250ms，Root语义查询47–61ms；失败查询明确返回 `press_region_obscured`。微信长按出现原生选区浮层后，按压点被其遮挡，旧安全区域选择器拒绝整片OCR区域，故弹出“当前位置没有可读取的文字或安全识字区域”。此外，若WeChat Root树存在但不暴露候选文字，旧逻辑只对 `root_window_missing/root_not_visible` 转WMS OCR，遗漏“窗口树有但没有文字候选”。

181改动：对WeChat无候选文字也调用WMS安全窗口路径；按压点落在可见遮挡窗时，从同一WeChat窗口选择最近的无遮挡邻区，仍通过原窗口核验、截图裁切与AICR识别，不放宽secure/密码/输入法/黑名单限制。为日志增加OCR分流原因和 `ocr_region` 来源标记。`PressWindowSnapshotTest`定向用例通过，Lint及R8 `assembleRelease`通过。APK已签名、覆盖安装，系统显示versionCode 205 / versionName 1.7.181，路径`outputs/HyperDragShare-1.7.181-arm64-v8a.apk`，大小7,429,983 bytes，SHA256 `177B8618641FD8F78091C5BAD79180855D06A5016ABC157619A1A92C6D212D2F`。

安装时前台为微信会话。为避免打断页面，没有重载Portal/SystemUI，因此设备上新APK已安装，但旧Portal Hook和Root helper尚未确认加载181；本次真实微信按压未复测，不能称修复通过。待Portal作用域重载后，在不发送消息的前提下实测按住已有文本，并从 `DragShare/Press` 与 `DragShare/RootNodes` 日志确认 `WeChat window OCR fallback`、`kind=ocr_region` 与分词浮窗结果。181不是正式Release，不替代179。

## 182 微信按压截图复核误拒绝（源码与构建完成，设备待安装）

用户提供的 `HyperDragShare-20261021-230137.log(1).txt` 只到10月21日23:01，不包含截图所示10月22日00:23的操作。随后从设备只读日志确认10月22日00:25一次真实事件：`semantic lookup elapsedMs=40`、`press capture source=com.tencent.mm kind=ocr_region`，并在Portal记录 `press confirmed ... elapsedMs=250`。这证明触发与微信窗口区域候选已命中，但没有AICR识别或分词窗显示记录，不能称识别成功。子代理只读检查确认微信不在内置黑名单，源码仅按安全窗口、锁屏、窗口身份及遮挡做限制。

根因落在捕获交接后的WMS复核：代码把之前计算的矩形当成不可变区域；原生选区菜单/手柄改变遮挡后，Root helper会返回false，调用方把它静默转成null，最后只显示通用提示。182改为复核当前微信窗口身份后，按最新WMS布局重新选触点附近的可见区域，并用新矩形裁图；仍保留锁屏、系统`FLAG_SECURE`、黑名单和前台微信窗口身份约束。增加模块日志 `image capture rejected ... reason=window_or_region_changed`，避免再无声地把区域拒绝伪装成“没有文字”。

`PressWindowSnapshotTest` 7项、`RootLeaseFailureTest` 2项通过；`lintDebug`与Release R8/资源压缩构建成功。签名候选包 `outputs/HyperDragShare-1.7.182-arm64-v8a.apk`，7,429,983 bytes，SHA256 `61199F073076C9FA9D95D832D9C79A7672A2F5A43AE195054237F25C6BC62B7E`。未安装：设备前台仍是微信会话，避免中断。正式Latest仍为v1.7.179；本次区域重定位尚未真机验证，微信按住取词仍未通过。

## 180 微信长按与原生侧栏首帧优化（设备验收待进行）

设备仅热重载后真实长按日志显示PressReader已ready，识别请求进入Root并在约54–59ms完成语义查询；失败原因重复为 `window_visible_occlusion_bounds_missing`。只读WMS解析发现安全中心窗口的WindowState名为`com.miui.securitycenter/.FloatingWindow`，同一窗口令牌在visible handles里显示`DockAssistantView`并含真实矩形。原来token和名字都要求相等，错过遮挡范围并让整次微信识别保守拒绝。

修复按窗口令牌关联当前显示的visible handle矩形，保留实际遮挡裁剪与未知可见窗拒绝；针对同令牌异名的安全中心侧栏回归用例7项全通过。热重载不需要整机重启：移除旧版保留 Root lease 的行为，Portal退出后旧app_process会随lease释放，新Portal从当前APK启动Root读取helper。原生侧栏在容器已attach时直接完成扩展挂接，未attach才post，避免额外等一帧；增加一次性挂接耗时日志供设备确认。

源码/已安装APK为1.7.180/204；定向7项 `PressWindowSnapshotTest`、`lintDebug`、R8 `assembleRelease`通过。输出包仅arm64-v8a，7,429,983 bytes；项目同签名校验通过，SHA256 `B26345E18CCA3FA5CE3751D945A585E842ACFC5202905438E3352FDBBA70530E`，路径`outputs/HyperDragShare-1.7.180-arm64-v8a.apk`。设备覆盖安装成功；启动模块后新Root helper/PressReader日志显示ready，Root helper启动269ms、读取连接272ms。ADB模拟长按能触发原生选择菜单；日志出现Press时长确认及语义查询（47–61ms），但没有确认新版分词面板显示，故微信长按仍未通过实测。ADB模拟事件没有稳定对应真实evdev触摸链路。热重载 shell 对Provider/SystemUI操作被设备SELinux拒绝；`com.android.systemui`仍为PID14951，`com.miui.securitycenter:ui`仍为PID14913，因此侧栏挂接代码204未加载，首次展开性能未实测。正式Latest仍v1.7.179，未发布180。

Flyme小窗对照的后续清单见子代理只读审计：优先实测同屏双小窗触摸目标与窗外操作，确保关闭/最小化后不会误作用于另一个窗口；底部拖条上滑mini由HyperOS原生处理，不另复制ColorOS Hook。侧栏首次展开应用列表的冷启动耗时也待设备验收。

事实来源为当前源码、具体构建报告、安装元数据与对应运行日志；构建通过、已安装、Hook加载和实际操作分别记账。旧逐版记录的“当前”仅代表当时。

## 179 设置重整、单引擎精简、更新检查与微信修复

源码1.7.179/203；本轮开始核验设备仍177/201，178未安装。178旧验收心跳已通过工具暂停，不能再按旧提示安装178。用户新增的检查更新、微信深度适配与解包分析并入本批，覆盖原“本轮不做微信专项”的早先范围；不恢复面积实验或原生侧栏研究。

四模块设置、公共设置与共享SettingEntry目录已实现；首页离线搜索，精确行滚动高亮、前置开关说明与返回保留查询/位置已接入。主标题、分组与条目使用Miuix图标，沿用0.9.3；原键/值保留。中转站混合卡横向图文、单图预览、多选触区和行距已调整，灰条/贴边/≤16%面积/源窗保留沿用。源码说明见SETTINGS_CATALOG.md/STASH.md。按用户Root群体定位压缩首次指南、按住/磁贴/识字等重复说明；悬浮窗入口显示真实授权状态。Root输入、无障碍来源和必要的权限失败反馈仍保留，不因精简UI删除有效能力。

图片/屏幕OCR已删除ML Kit依赖与识别分支、选择键读写、许可与旧调试探针，仅使用现有AICR V2。依赖报告 `work/settings179/release-dependencies.txt` 位于仓库父级任务work，确认无ML Kit/Google OCR/Play Services相关依赖。ZIP/mapping中无ML Kit，移除28项专属模型/库，三个jieba字典与178逐字节相同；唯一ABI仍arm64，保留graphics.path和dragshare_jieba两个原生库。首个179签名R8包7,429,983bytes（7.09MiB），对比同为Release/R8/arm64的178的21,475,042bytes（20.48MiB），减少65.4%；最终修复包数字在本节后补。

build374完成Debug/Release编译，402单测中1项新增权限测试在FakeContext初始化失败，修正为super.getPackageManager。build375共404项单测0失败/错误/跳过，Lint发现主题块缩进错误；修正后build376 lintDebug/assembleDebug/assembleRelease成功，Lint0错误185警告2提示，执行R8和资源压缩。没有为后续纯文字改动重复跑404单测。

用户23:01微信提示已定位到PressCaptureSource收到空内容，并非时长检测未触发。实际WMS证实当前WindowState无frame，窗口bounds在visible handles；实际聊天Activity为ChattingMainUI而非仅LauncherUI。已修正窗口解析、关联真实Insets，以及精确识别已核对透明GestureStub窗口，微信按窗口所属包识别普通页面，不再限制两种Activity；保留锁屏/secure/密码/IME等真实边界。微信8.0.79/3182 APK与split已只读提取Manifest/DEX页面族和触摸/拖放入口，详见WECHAT_ADAPTATION.md，原始材料不入Git。不改微信APK、不新增微信Hook作用域；真实手指按住及各页面仍未在179验证。

检查更新组件已接入通用设置，固定官方正式Release、arm64包、显式下载/系统安装；当前最新正式版176，比本地179旧，不应提示降级。首轮实测发现匿名GitHub API HTTP403，正确请求头复核响应明确X-RateLimit-Remaining=0；已改为官方releases/latest最终重定向确定正式tag，再读取该页明确指向的assets列表，只接受同tag的arm64包。不以Atom数字tag猜测正式版，不加入token或多级回退；错误详情仅显示一次。手机curl已确认页面和资产列表返回200，修复后的完整应用检查、下载和安装新版链路尚未真实操作通过。

首个179包已同签名覆盖安装，设备base.apk与交付APK哈希一致，未清数据；四份用户偏好完全不变，fan_config只更新运行诊断。仅重载Portal，PID16497，注入/输入/reader均203且ready=true；SystemUI仍201，其他Hook未重载，不称全部203。设备仅在本模块设置检查：首页四模块可见，搜索OCR显示两项并直接滚动高亮系统AICR行，Back保留查询；搜索release直接定位靠底部的检查更新。没有操作私人微信内容；长结果列表滚动恢复、中转站新布局和OCR识别本批未实测。

## 178 大批源码检查完成：待集中安装验收

用户追加“大更新，再测试”：177检查/安装已完成并以222c716推送；此后小窗、分词、图片拖动和UI合成178，不按单按钮出包。三名Luna high代理负责独立范围，主任务统一接口、动画减负、构建及验收。当前178源码不等于设备已更新。

源码178/202，回退点85a10ef已推送，设备仍177/201。build372在新增动画测试编译处失败：Robolectric Shadows重载解析引用当前compileSDK缺失的FingerprintManager；改用项目已有ShadowLooper入口，build373整批重跑成功。398项测试0失败/错误/跳过，Lint0错误184警告3提示（新增缩略图数量文案SetTextI18n提示），Debug/Release/R8与资源压缩通过。APK outputs/HyperDragShare-1.7.178-arm64-v8a.apk，21,475,042 bytes/20.48MiB，仅arm64，同证书，SHA256 `b8b26b0b7a06fa11ed067176e95b7375e905b23e4b5225210a8869b135110358`。检测手机持续触摸，未覆盖或重载，等待空闲后集中安装。

按用户此前的持续推进要求，已创建本对话heartbeat `hyperdragshare`（每小时，ACTIVE），仅在设备空闲适合测试时继续本包集中安装/验收；使用中/锁屏/离线则跳过且不反复提醒。完成验收或已明确仅缺用户物理输入后暂停心跳，防止重复。调度不等于保证无间断运行。当前未发布178正式Release。

小窗从当前ROM handleTouchEvent返回对象取得实际触摸normal任务，排除mini/pin与模块/原生侧栏DOWN，优先于最近状态回调；退出normal、销毁、关闭与新启动匹配清理。接口失败记录原因，原生事件/几何不改写。

本批分词按帧合并选区摘要，用实际布局原文引用缓存总字符数，直接统计选区码点而非拼临时字符串；入场改用单个ViewPropertyAnimator，不再每词强制硬件图层，取消恢复可见状态。中转站新增首图缩略拖影/多图数量，IO限256px软件解码；可选预览失败明确文字影子并记文件/权限原因，原载荷不变。混合组卡非多选时图/文各自直接长按拖对应子集，不新增混合载荷。同类组/已选整组选区保持；搜索拖柄44dp、引擎选中语义与Miuix图标、托盘选中描边一起合批。纯选择/Unicode计数/动画取消回归通过，不等于真实多窗/拖出或FPS改善通过。执行目录见TASK_BATCHES.md，集中设备验收后另记。

## 177 Flyme参考首批 / 系统OCR默认值（2026-10-21）

源码/安装1.7.177/201。静态核对FlymeFreeform 0.1.8后复用现有扇形扫选、应用/快捷方式、小窗桥及原生侧栏，不复制参考反编译实现。新增左右底角独立开关，沿用现有Miuix设置组；配置保存、Provider Bundle、运行时DOWN与备用捕获窗全部接通，关闭的一侧不创建捕获窗，两侧均关则释放。补缺省保留已有false/目标列表，未新增操作页面。详见FLYME_FREEFORM_PORT.md。

上一批默认AICR变更本次已打包：aicr_ocr_enabled不存在时默认true，已有显式选择不覆盖，ML Kit仍可手选；不改识别客户端或重复OCR样本。本机原有AICR=true不变，未通过清数据模拟新装。

build371一次整批testDebugUnitTest/lintDebug/assembleDebug/assembleRelease通过，394项测试0失败/错误/跳过，Lint0错误183警告3提示，明确执行minifyReleaseWithR8及资源压缩。APK outputs/HyperDragShare-1.7.177-arm64-v8a.apk，21,475,042 bytes/20.48MiB，仅arm64-v8a，SHA256 `9472c897a0d7a99e69d71aaf7c3a82b3b2310c41d4943b063be7672d3c1d1859`；证书不变，设备base.apk哈希相同。

本批一次覆盖安装。起初ADB无设备，随后只读preflight确认Root/SDK37/arm64恢复，页面在桌面后才开始操作。仅重载SystemUI及Portal：SystemUI PID8688日志明确合并手势version201；Portal PID8761，激活/Root输入/按压reader版本均201且ready=true。其他Hook未重载，不称全作用域201。

实机Miuix底角组显示左右新开关，UI关闭左角后持久化left=false/right=true。待异步配置生效，分别在桌面(50,3130)/(1390,3130)注入点击，只记录右角armed；已恢复两角true，保留原横屏关闭和目标。第一次点击发生在偏好异步落盘前，两角仍按旧值工作；不将该次旧配置结果混入生效后的证据。测试用日志时间解析曾漏掉epoch行首空格，修正解析后从原始日志提取证据，未据空输出判断通过。没有操作或提交私人内容。截图/日志仅存work/flyme018，不重复页面矩阵。

静态确认当前小米系统已实现底部拖条上滑mini（快速上甩另有关闭分支），SystemUI版本及窗口管理JAR哈希与设备一致；本批保留系统原生行为，没有追加与之竞争的识别器。顶部拖条额外上滑改写未实现；参考ColorOS返回码不能直接映射。真实手指扫选、备用捕获路径的单侧禁用、横屏/游戏及侧栏拖出未在本批重新实测。当前GitHub正式Latest仍为176，不将177本地候选写为已发布或Flyme全部交互验收完成。

## 176 仅arm64-v8a打包（2026-10-21）

按用户要求，defaultConfig.ndk.abiFilters仅arm64-v8a，源码/最终安装1.7.176/200；未修改功能或移除ML Kit。R8/资源压缩继续启用。build369 lintDebug/assembleRelease成功，Lint0错误183警告3提示；本次仅打包配置变化，未重复393项既有单测或页面矩阵。

APK outputs/HyperDragShare-1.7.176-arm64-v8a.apk，21,475,042 bytes（20.48MiB），比175通用包小60.5%。签名证书不变；aapt/ZIP确认唯一ABI为arm64-v8a，3个原生库及28个OCR模型/词典文件逐字节与175一致。首次全assets相同断言发现生成的assets/dexopt/baseline.prof随版本变化，改为核对真实模型/词典，未忽略产品文件差异。SHA256 fc48775c88e5c620715da9b017c9a59ca9f4e3dcc701f9c0c62ae08a48d7f2a9。

一次后台覆盖安装成功，未切换用户页面；设备ABI也确认为arm64-v8a。仅重载Portal1670，输入223ms/reader576ms就绪。其他系统Hook保持此前版本，未宣称全部作用域200或重新完成功能实测。正式v1.7.176已发布并标记Latest，tag源089bb95，APK服务端digest/大小与本地一致：https://github.com/chenran0408/HyperDragShare/releases/tag/v1.7.176 。175历史通用包保留为回退点，后续构建仅生成arm64。

## 175 原生Dock、首次说明与首个正式R8发布（2026-10-21）

源码/最终安装1.7.175/199。原生Dock仅增加固定首项中转站，保留原快捷应用、四格按钮、完整应用搜索与场景工具；删除重复应用/搜索及普通场景空工具入口。原生y2.c统一预留48dp，原生高度限制、布局与动画使用一致尺寸；移除Turbo额外padding/负边距/独立粉色底板。最终图标为28dp纯白线形、48dp原生按钮触区、圆角背景、选中反馈和Ripple，保留无障碍名称/长按提示。

初版四入口setLayoutParams方案被用户截图证实失败：原四格与工具重叠，快捷应用被裁。367修复后21:12用户物理打开侧栏，截图确认中转站＋原2快捷应用＋原四格在同一底板且无重叠；21:15原应用面板推荐/完整应用三列可见、右侧不再裁切。21:14中转站Surface mounted，原有条目显示，再点入口切回且Surface released。没有发送、编辑或删除用户内容。368仅追加纯图标样式，安装/重载已确认，未再要求用户重复打开。最终纯图标实感、原生Surface内拖出、键盘/游戏视频场景、横屏未验。

首次Miuix指南在主设置Scaffold内，点“知道了”保存独立confirmed键，升级用户首次未读也会提示；关于页可再看，无新增页面。366本机已实际显示指南并读回confirmed=true，未清数据；关于复看未点验。修正PressWindowSnapshot两个闭花括号，真实Android app_process加载当前源码的全部正则初始化通过；临时dex已删除，不冒称微信真实按压全流程通过。

365/366完整检查，366为393项测试0失败/错误/跳过、Lint0错误183警告3提示、Debug/Release/R8成功；具体布局修复367与纯图标368仅复跑Lint/Release，均成功，未重复样本矩阵。最终R8包54,350,297 bytes，outputs/HyperDragShare-1.7.175.apk，SHA256 0B8DDF479D2DBBA77FADC5DE2C9EFAE853CAF5E93F97368785A9724F6CE8BFA5；证书不变。本批365/366/367/368共4次安装，先后包含追加新手指南、用户指出的真实布局故障修复和追加纯图标，未碎片增版。

21:16最终安全服务:ui PID559明确Hook199；Portal645输入220ms、取词reader334ms ready。SystemUI沿用198相同仲裁实现，框架/桌面/AICR未在本批重载。不将安装199写成全部作用域199。私有截图与定点分析留work/sidebar175，不入Git。README已重写为普通用户安装/操作与开发者私有接口/普通UID客户端调用示例，保留上游/FanFreeform/Miuix归属；正式发布结果见下条。

发布确认：代码回退点/正式tag源为 `ff66401` / `v1.7.175`，已推送用户公开仓库。正式Release（非Draft、非Pre-release）已上线：https://github.com/chenran0408/HyperDragShare/releases/tag/v1.7.175 。APK及SHA256文件均uploaded，GitHub资产digest与本地0B8DDF47…CE8BFA5完全相同。README的latest下载链接现指该正式包。云端Actions39606816340仍in_progress，本地检查通过不冒称云端通过。原生Surface拖出、横屏/深色与游戏视频场景、关于说明复看、指定浏览器外跳仍未实测。

## 174 连接复用、复制直达与分词显示（2026-10-21）

源码/最终安装1.7.174/198。设置变更复用既有按压源和Root取词连接；禁用/切换来源仍释放。设备表+坐标探测合并一次su；连接失败明确上报Provider IllegalStateException，新增阶段耗时。热重载在已有reader就绪时持临时无读取租约，新Portal接上后释放，避免销毁同版本取词helper；Portal启动先于SystemUI/侧栏重载。

实机：候选360冷启动输入198ms、reader448ms；20:09热重载前后helper PID14772不变，reader重新取租约4ms，输入249ms，连接确认2313ms。单次样本，不是持续性能承诺。最终364静默安装后仅重载Portal，PID27781输入214ms、reader503ms ready；没有再切换用户页面。SystemUI/侧栏沿用本批已加载198的原生整合代码，框架与桌面/AICR Hook具体版本没有重测，不称全部作用域运行新代码。

新增AICR NER V1普通模块UID客户端（Core明确注册），不替换未实现的系统cut；复制卡片入口自动显示内联建议，普通分词可选词点链接图标。整值HTTP(S)链接本地直达，复合文本NER，二者不混称。电话/邮箱/URL类型和值校验后才提供显式动作，其他实体进入已有原页搜索。Miuix设置可选复制直达浏览器，保留系统默认，卸载/不可用明确提示；仅直达网页及对应搜索外部打开采用该选择。借鉴HyperCopy的URL/规则路由方向，未复制反编译实现，未加入短链WebView/通知/剪贴板回写回退。

原页正文空白根因是旧XML根FrameLayout默认invisible且Compose挂载从未恢复可见；已删除该遗留状态。NER卡改紧凑100–120dp并计入面板自然高度，避免挤掉正文；移除重复重新OCR入口及旧路径。用户追加要求后去掉主面板右上角X，保留外部点击/系统返回；子结果卡X保留用于只关闭结果。手势设置高频入口统一Miuix图标及56dp行。

实机最终363：URL正文显示、本地“查看链接”建议可见，全选19/19后系统Back仅关建议且选区保留；测试号码由普通UID NER识别出1个实体，316ms，拨号入口显示。未自动拨打/发信/打开网址。先前URL的系统NER返回0，说明不能把系统NER当URL解析器。复制卡片自动入口、邮件/地址、多实体、浏览器指定应用跳转未整体验收；浏览器UI测试时用户在使用手机，停止后未再抢占。最终去X后不重复截图验收。

build359图标导入缺失失败，补齐后360全套通过；361显示修复检查通过。用户追加浏览器后362因误用非公开ResolveInfo字段失败，改为公开IntentFilter查询后363完整393项测试0失败/错误/跳过，Lint/Debug/Release成功。364仅去主X后跑Lint/Release成功，未重复单测；明确执行minifyReleaseWithR8及资源压缩。APK outputs/HyperDragShare-1.7.174.apk，SHA256 7A0DA02D0E86468FCEBB11E205955B575BAB80024F6B4119B64B1E45C2CEDA35，同一证书。本批候选360、修复/浏览器363、追加去X364共三次安装，无碎片增版。私有证据work/reload174、反编译work/hypercopy174留本机，不提交。

下一批175：用户截图已证实原生四区轨与快捷应用条分离、应用面板右侧裁切、主题不统一，要求合并为一条侧栏。此前173只证Hook加载，不再把本轮真机截图问题列为纯未验证。独立Miuix中转站/灰侧条既有数据与拖放保留。

## 173 原生侧边栏整合（2026-10-21）

源码1.7.173/197：精确接入安全服务40001365/SDK37的com.miui.securitycenter:ui，在原应用/搜索/场景工具之间增加中转站区。Surface承载复用当前Miuix托盘、文字ClipData和MediaStore图片链路；不创建新的操作Activity、不主动获取焦点。拖出期间保持源Surface，结束后才处理关闭。独立灰侧条仍保留，原数据不迁移、不清空。

SystemUI只对上报的原生把手/实际可见面板DOWN让位；不把整个窗口容器作为排除区域。Binder死亡清理区域和Surface，关闭请求/返回先退出预览或编辑。热重载只重启安全服务的:ui进程，不强停整个安全服务。普通目标排序按钮改为48dp Miuix图标，保留名称无障碍描述。

已通过通知栏“LSPosed已加载”打开寄生管理器，并在UI启用安全服务作用域；这不代表197 Hook已加载。build357因旧测试构造参数位置不匹配失败，改为具名content参数，build358随后成功，部署结果见下。原生四区、Surface触摸/拖出、焦点、返回、键盘与手势协同尚未实测。NER、二维码和复制直达实体动作增强仍未实现。


173交付：build358成功，389项单测0失败/错误/跳过，Lint、Debug、Release通过。APK outputs/HyperDragShare-1.7.173.apk，SHA256 6E45EDF729BE85E748D24DCD5F8A76372180C0324066BA11B6986D3D0650C04F；同签名覆盖安装成功，本批一次安装。通过通知栏LSPosed寄生管理器启用安全服务作用域，保留原有作用域。重载SystemUI、Portal及安全服务:ui后，19:43:17日志明确PID21536加载exact sidebar hooks installed build=197。只证明新入口Hook安装成功，尚未确认rail/Surface实际显示、拖放、键盘和返回行为。系统框架/AICR未重载；SystemUI/Portal已发重载但未另采新版本诊断，不能推定全作用域运行197。设备截图/日志保留本机work/sidebar173，不提交私人截图。

## 172 中转站Miuix重绘与浮窗触摸归属（2026-10-21）

源码1.7.172/196。中转站改为Miuix圆角卡片、主题选中反馈、组件复选框、统一加载/错误/空态，短标题拖柄及44dp控件；后续同批补统一圆角底板和1dp深浅色描边。灰侧条、≤1/6面积、按住内容拖出与多选、MediaStore图片/原生文字链路不变。

新增键盘与底部mandatorySystemGestures避让：从WindowMetrics读取屏幕坐标IME边界，临时上移不写回保存位置。独立浮窗发布真实屏幕可触摸矩形，SystemUI仅在DOWN命中后整段让位；不可触摸的拖出窗口撤区，关闭/模块死亡清缓存。窗外捕获排除这些矩形，角落fallback通过既有重放路径放行DOWN；没有原小窗时不因模块窗口创建额外捕获。

build353缺size导入编译失败，修复后build354全套成功：389项测试0失败/错误/跳过，Debug/Release及Lint通过。候选172已同签名覆盖安装一次，19:28 Portal自报196且Root输入/reader ready，SystemUI诊断明确196与OS4/sdk37；未重载framework/AICR。实机确认新托盘能显示；发现透明底板视觉混杂，已补圆角底板与用户要求的细描边，最终构建/交付信息在本节后续追加。build355 Lint的RepeatOnLifecycleDetector内部崩溃，未禁用规则，冻结源码后重跑356。

最终build356 Lint/Release成功，未禁用检查规则。最终APK outputs/HyperDragShare-1.7.172.apk，SHA256 2F4DC982B01AE6BEBC32766AF76F8B87519248B6C653CD5EDCE970507CCC487E，同签名覆盖安装成功；本批候选+最终共两次安装（第二次包含底板与用户追加描边）。最终仅重载Portal，SystemUI保留本批已加载196。描边/底板最终包未再跑截图或拖放验收。

用户要求减少检查，本批不重复微信拖放/截图矩阵。键盘真实避让、完整DOWN让位与死亡清理、暗色/横屏仍未实际验收；不能将安装和自动测试替代它们。172未合原生侧边栏/NER/二维码。下一批代理在独立暂存目录准备剩余UI和侧边栏适配，避免混入172构建。

## 171 首页重新分组（2026-10-21）

1.7.171/195：首页改用Miuix SmallTopAppBar，紧凑激活卡；页签为首页/拖拽/更多。首页依次为状态、手势和中转站、按住取词与避让、拖拽内容、分词操作。黑名单仅保留统一入口；磁贴添加放在按压开关/时间之后。重载、权限、预加载、OCR引擎/复制直达/背部轻敲及图片副本说明移到已有更多页，未新增路由，未改配置/触发语义。

build351成功，实机确认重排后Miuix默认大标题仍存在，改用库内SmallTopAppBar后build352 Lint/Release成功（0错误179警告3提示）。此批纯UI未重跑单元测试。候选与最终同版本共安装两次；最终APK SHA256 07008F6689E53E67834C26C90771C45AC8482F09CEE435D4989F89CA0628537C，outputs/HyperDragShare-1.7.171.apk。

19:15最终首页已实机确认紧凑标题、常用入口和按压控件；更多页重载/权限/OCR入口已检查。Portal24241自报195，Root输入/reader ready；SystemUI仍是170/194（本批未改手势逻辑），框架/AICR未重载。不重复声称游戏/拖放/识别通过。暗色/横屏/大字号布局未验。待办更新于NEXT_INTEGRATION_BATCH，区分尚未实现与未实测。

## 170 蜂巢同页编辑、Miuix入口与应用避让（2026-10-21）

源码1.7.170/194。蜂巢目标页内嵌共享预览，直接长按移动/交换并保存；移除该列表误导性的上移/下移（列表顺序本来不改变蜂巢位置）。独立放大编辑页保留，添加/删除刷新预览；列表使用Miuix组件。紧凑预览不常驻绘制名称，名称保留在下方目标列表与拖动提示，避免网格重叠。

首页先显示激活状态，常用入口合组，第三页签改“其他”。手势、中转站、权限、游戏和黑名单入口补Miuix图标，导航使用右箭头，摘要最多两行。首页黑名单统一指向本模块名单，避免Portal系统名单与本模块名单混淆。全设置/手势页面的布局精简仍非全部完成。

修复：Portal自动抓取漏黑名单；无障碍运行中按压源未接收游戏/黑名单变更；配置变化取消旧候选；Root明确excluded_app结果不再弹无内容错误。Hyper手势从既有Provider获取用户黑名单与启用时的游戏集合，配置通知立即刷新，任务变化撤捕获区，DOWN复核整段让位，MOVE无新增查询。桌面/输入法内置排除仅用于取词，不禁用桌面手势。

build347因MotionEvent非公开getDisplayId调用失败，改为既有context显示屏来源；build348完整成功386测试、0失败/错误/跳过，Lint0错误179警告3提示，Debug/Release成功。随后实测发现紧凑预览名称重叠、导航误用下拉箭头以及黑名单入口分叉，修正后349/350只重跑必要Lint与Release打包，未重复全套测试。

候选170已安装且Portal/输入/reader194就绪，SystemUI14813明确加载194并读取配置。实机蜂巢交换两图标保存成功，再交换恢复，honeycomb_positions_v1与原备份一致（仅运行诊断/界面时间变化）。19:05王者荣耀加载页边角手势日志明确Gesture excluded package=com.tencent.tmgp.sgame；未登录或进入游戏局。19:07通过UI临时加入com.android.settings黑名单，SystemUI立即命中，后已通过UI移除；用户名单与手动游戏集合恢复原空集合（manual_game_packages由缺省首次存为空集合）。未更改原目标或用户内容。

最终build350 Lint/Release成功（0错误、179警告、3提示），同签名覆盖安装170/194。APK SHA256 BA63CE636A02FFA531413A8E911314CB08D90C48CC50D4750629F459FC8B18DD，交付outputs/HyperDragShare-1.7.170.apk。19:09首页右箭头、图标及两行摘要实机可见；最终Portal/Root输入/reader194就绪、错误为空。SystemUI保持本批已加载194，后续两次仅UI修正未改变手势逻辑。候选和最终共覆盖安装2次，未碎片增版；第二次用于修正真实UI问题。

未验证：真实手指Root按压的游戏/黑名单阻断与提示、无障碍模式完整交互、手动游戏入口完整操作、应用切换恰逢OutsideTouchCapture已拿到DOWN的竞态、横屏/暗色全页面。ADB注入不是evdev真实触摸，不冒称Root按压已实测通过。系统框架/AICR没有重载，不称其运行194。底部导航避让、复制直达扩展与原生侧边栏候选仍在NEXT_INTEGRATION_BATCH待执行。

## 169 游戏避让名单与手动入口（2026-10-21）

已实现：在CATEGORY_GAME基础上增加22个国内外常见游戏精确包名；新增独立manualGamePackages，本地保存与Provider Bundle同步。主开关同时控制类别、内置、手动三类；只暂停既有自动抓取，不暂停手动点取/OCR或合并Hyper手势。Miuix手动添加页复用应用图标、搜索和列表，自动识别项为只读状态，其他应用可添加/移除，已选置前。详见GAME_AVOIDANCE.md。

build346：383测试通过，0失败/错误/跳过；Lint0错误179警告3提示，Debug/Release成功。已同签名安装1.7.169/193，SHA256 104A02122285CBE0AFC4815A5EE0CE43CB317D50F65D0ABE6EE449B83492B775。Portal32255明确自报193，Root输入/reader ready。其他Hook未重启。

已核对设备安装王者荣耀包名命中内置表，但未启动游戏、未实测游戏内阻断。设置首页已打开；检查期间页面转入菜单分类，停止点击避免抢占。新选择页真实添加/移除及暗色尚未验；持久化/Bundle/主开关/精确匹配有针对性自动测试。默认开关与用户原配置保持。

新增后续任务已读取并记入NEXT_INTEGRATION_BATCH.md：底部导航区与Hyper手势让位、AI引擎复制直达复用、原生四区侧边栏候选合并。候选未合入；不得把旧165候选APK覆盖当前版。

## 168 文字改为原生拖放（2026-10-21）

用户明确选择默认直接发送。中转站删除StashTextDropWindow及其异步插入回调，不再用自定义MIME和全屏透明层阻止微信接收文字。文字通过text/plain原生拖放，多选按顺序用换行合为一项；图片沿用166系统MediaStore副本链路。保持167不抢焦点与键盘共存。具体发送或填入由目标App接收器决定，不把所有App都称作自动发送。

167补充实测：18:41键盘打开，生成测试图拖到文件传输助手输入栏，新增对应图片消息，键盘仍显示。用户反馈的图片失败本次未复现；文字旧链路报无输入框已确认。

build345：381测试全部通过，Lint0错误179警告3提示，Debug/Release成功。已安装1.7.168/192，同签名APK SHA256 0C8DB562FE9136AEED6DB91066F75C3F3B797AC203BAF771D3B1E38FE5D9405A。Portal15479自报192，Root输入/reader ready且错误空；167曾退出的原因未修复，不将本次就绪当成稳定性解决。

18:44实测仅把既有生成测试文字HDS157_TEXT_DROP_TEST拖向文件传输助手输入栏，微信当前聊天出现对应消息，没有联系人选择器、没有填框报错。私有截图/log在work/wechat168。多选合并文字、其他应用和混合内容未实测；图片在本轮167键盘打开状态已确认接收。保留原内容、侧条收起。

## 167 中转站不再占用输入焦点（2026-10-21）

展开和收起均保持FLAG_NOT_FOCUSABLE，窗外触摸交给原应用；移除不会再收到事件的中转站系统BackHandler，托盘预览/分组/删除确认沿用现有返回按钮，收起按钮保留。系统返回与键盘交给当前应用，不新增控件。修正166展开时关闭微信键盘的问题。

build344：381测试全部通过，Lint0错误180警告3提示，Debug/Release成功。已安装1.7.167/191，同签名APK SHA256 AB3F98BEABCA55406D7755ED19FED4643B2AA0189683B1E87801FD02FA8EF69C。

18:39：文件传输助手中托盘展开时，点击窗外输入框成功打开键盘；点击收起按钮后键盘仍显示，再点右侧条展开后键盘仍显示，WMS当前焦点始终为微信LauncherUI。未输入/发送任何消息，未触碰私人图片；截图私存work/wechat167。面板位置过低时仍可能局部遮挡，用户可移动；本批未实现键盘避让或验横屏。

Portal32078明确注入191，Root输入ready；reader上报Root node process exited: Killed、ready=false，未通过重复重载掩盖。其他作用域未重启。该取词子进程退出需继续排查，不影响本轮侧条焦点实测结论。微信文字填框仍未解决。

## 166 当前聊天拖放修正（2026-10-21）

撤销165的微信分享入口：不再跳转联系人选择器。修正拖放成功后先恢复中转站焦点再收起的顺序，避免目标输入框被二次焦点切换打断；取消拖放仍恢复原面板和选区。文字插入的异步结束流程保持原样。

本机165及166候选1日志证实DROP已送到微信LauncherUI，但微信无法解析私有图片Provider；显式单次URI授权也未解决。微信返回accepted=true不能证明图片收到。新候选改为按住时在IO线程准备受控的MediaStore副本，以系统图片URI原生拖放；松手或窗口离开后不再迟到启动拖拽。沿用仅清理自己记录的24小时副本，不修改中转站原图。

build343完整通过381项测试（0失败/错误/跳过），Lint0错误180警告3提示、Debug/Release成功。已同签名覆盖安装1.7.166/190候选2；APK SHA256 B250B3D6C59FC2A8AEF2439BE910288898B087CDC1DCCCFD898669712994AA63。Portal21923已自报190，Root输入/reader ready且错误为空；其他作用域未重启，不称190运行。

18:35手机已回到文件传输助手：仅把既有生成测试图HDS157 DRAG IMAGE TEST从中转站拖向当前聊天输入栏。WMS将DROP送到LauncherUI，未再出现私有Provider解析错误；截图确认18:35新增对应测试图片消息，没有联系人选择器。拖放后当前对话仍显示，中转站变为灰侧条。私有证据保存在工作区wechat166/drop166-final.log和.png，不提交截图或聊天内容。真实单图当前聊天接收通过；多图、大图、其他接收App、键盘展开保持未验证。打开中转站时键盘仍会收起，不能将拖放结束焦点修正宣称为全过程键盘保持。文字拖入微信输入框仍未解决。

## 165 中转站微信图片分享入口（2026-10-21）

源码与已安装APK1.7.165/189；新增现有图片预览/纯图片多选中的Miuix分享图标，直接交给微信ShareImgUI，由用户选联系人及确认发送；不新增本模块页面、不删除原图，收起保留预览和选区。目标设备PackageManager只读查询已确认其单图/多图入口存在。

build341完整通过381项测试（0失败/错误/跳过），Lint0错误180警告3提示，Debug/Release成功。已同证书覆盖安装；Portal23978明确自报189，Root输入/reader ready且错误空。其余Hook未重启，不能称189运行。手机仍在淘宝并持续切页，没有抢占操作。**未实测微信读取图片、显示发送预览或实际发送；仅接通代码与核对入口，不能宣称已能发图。原生图片拖入当前聊天、文字填框故障仍未解决。**

## 164 蜂巢预览直接拖动与弹窗修复

源码与已安装APK为1.7.164/188。build340：381项测试全部通过，Lint0错误180警告3提示，Debug/Release构建成功。Portal31048明确自报188，Root输入/取词reader ready；其他作用域未重启，不称已更新。

本批实现：蜂巢预览直接长按拖动、跟手图标/原位淡影/落点提示、取消不保存；修正结束回调读取旧位置。目标选择/恢复预设及蜂巢紧凑确认移入Miuix Scaffold，修复弹窗宿主缺失；各手势目标入口前置。微信无节点时增加WMS身份核验后的局部OCR，继续现有客户端和原页浮窗。

旧163实机已确认目标页“添加”点击无弹窗、上下移图标呈缩放形；本批针对根因修改。手机转入淘宝且持续切页，暂不抢占操作。新蜂巢拖动、选择器显示和微信真实按压尚未实测；微信中转站收图/文字填框仍未解决。

## 最新调查：439 微信按压对照

用户确认439测试包在微信“按住＋传送门”可用。静态分析确认其传送门按压链路在无障碍文字为空时仍可Root截图OCR；本fork在根节点缺失时提前返回，现有区域OCR到不了。具体证据与实现缺口见REFERENCE_439_WECHAT.md。调查阶段未安装参考包、未改运行核心（164后续实现见上条）；不能将用户反馈推定为已验证该次实际走OCR。

## 163 设置界面精简

源码与安装包为1.7.163/187，build337完成376项测试（0失败/错误/跳过）、Lint0错误180警告3提示以及Debug/Release构建。已同包名、同证书后台覆盖安装；本批没有重启作用域，不能将安装187写成Hook运行187，最后明确自报的SystemUI/Portal版本仍见162记录。

- 主设置用“功能／菜单／应用”同页分类；手势设置用七个固定分类，切换回顶部，保留原设置与回调。
- 目标页短标题、48dp图标按钮及提示；选择器统一Miuix分类、搜索、复选框和弹窗。蜂巢位置编辑、重载方式、关于和许可页一并收敛。
- 已确认：编译、现有自动测试、安装版本。未确认：本批页面实际切换、目标弹窗键盘布局、蜂巢编辑反馈、深色/横屏/大字体。本轮手机前台持续从分享页切换到系统设置/侧边栏设置，为避免打断，没有切页或执行重载。
- 原微信取词/中转站接收和按压首帧实感问题仍在，本批只修改设置UI。

## 162 合并基线

源157运行核心、Miuix设置与目标/蜂巢编辑已接入单一应用；162/186已安装并迁移原95键与285图标（完整备份保留），底角10/蜂巢22/侧边2目标保持原样。376测试通过、Lint0错误；原Hyper手势模块禁用、原应用数据保留，避免双重接管。

当前SystemUI27757/186、Portal7428/186，后者Root/输入/取词reader就绪；system_server2044和既有AICR仍160/184，本批未改对应逻辑。桌面为Rust/hyos_spawner，本项目Java桌面Hook未加载；已改由SystemUI发布235快捷方式，保留原157的竖屏清除键覆盖层。161底角呼出/取消及阅读应用系统小窗已真机通过；162设置深链接、目标管理/选择窗口及快捷方式显示通过。详细证据和边界见FANFREEFORM_INTEGRATION.md及DEVICE_VALIDATION末条。

未实测：蜂巢布局拖动、横屏/双开、完整窗外动作、深色视觉、参数重置与报告保存。目标管理长标题换行待后续精简。原微信取词/中转站接收故障与按压首帧实感仍在，不因合并完成而关闭。

## 159 弹窗掉帧修复（2026-10-21）

- 158真实用户操作日志：模块进程跳过58帧，确认到首帧903/464/447/328ms；这是旧版基线，不是159改善证据。
- 修正动画逐帧重组触发SideEffect、递归重设全部词块主题的问题：主题仅随颜色变化更新，新增词块仍沿原回调着色。缩放在graphicsLayer读取、遮罩透明度在drawBehind读取；不再由动画每帧重组工具栏和AndroidView。移除旧View淡入和额外硬件层，保留Compose统一入场动画。
- build326完整成功，228测试0失败/错误/跳过，Lint0错误163警告2提示。159/183已按用户“尽快更新，之后一次性测试”集中安装成功并重载Portal；未切页或做功能矩阵，掉帧改善尚待集中实测。
- APK SHA256：37BC1C6FE7C1A421E3F6AE75F4D25AA903B0576526945DB63410EBF1E604BA27。
- 用户指出旧版微信曾能取词；按代码回归继续排查，不能仅凭微信无障碍缺窗认定其过去一直不可用。微信取词和中转站问题仍未修复。真实面积研究保持停止。

## 158 本批记录（2026-10-21）

- 源码与已安装APK：1.7.158/182，同签名覆盖安装一次；SHA256 47611C31B0ED327DEA84F81EAA08AFC7D69EA77EF8D3434DAC06E803FF00699B。Portal30839已自报182，Root输入和取词reader ready；系统框架/SystemUI/AICR仍沿用既有179，未重启这些作用域。
- 按压反馈：删除取词宿主在开窗请求前直接震动；文字窗口及图片识字窗口首次绘制后才调用系统CONFIRM反馈，遵从系统触感设置。仅按压入口携带确认时间，其他开窗不增加震动。新增confirmedToFrameMs日志用于定位等待，不宣称已缩短取词时间或物理手感已通过。
- 中转站：灰条改用Miuix次级主题色；文字拖放结束同步移除自有接收遮罩再查询落点，消除本地延迟移除的竞态，但微信缺少无障碍窗口的限制仍在。图片Provider增加只含操作和调用UID的记录，不输出图文、URI或路径。
- build324首次因灰条修改误删仍使用的Color导入失败，恢复导入后build325完整成功：228项测试、0失败/错误/跳过，Lint0错误/163警告/2提示。未禁用检查。
- 安装/Portal加载已确认；新首帧反馈的物理时序、遮罩竞态改善、图片接收尚未实测。微信取词及文字填框未修复，不把同步移除遮罩视作解决系统限制。当前不新增页面、不取消系统限制。
- 用户新增取词速度优化：继续依据语义查询耗时与确认到首帧耗时定位；尚未得到新版实测延迟。真实面积研究已停止，使用Root evdev时长/位移。

## 前一批版本账本：1.7.157 / 181

- 源码：hyperos4-adaptation，本批提交；前一回退点156为f1b95d1。
- APK与设备：157/181，同签名覆盖安装一次。SHA256：F5CCFE48287D8A986304DB5746D6132E5AB92705EA74D9B20D9DB8CE014F9FE0。
- Portal：16:20:45后30012/181，Root、输入与reader就绪，错误空。156已有真实Binder死亡撤下激活的证据。本批安装与测试清理会结束模块租约，出现一次旧租约DeadObject关闭日志，后续新租约ready；不把时刻就绪称后台稳定性通过。
- system_server18477、SystemUI20665及AICR三个进程此前软重启已明确自报155/179。本批没有改这些Hook代码或再重启系统，不将181安装冒充全部作用域运行181。
- build323：testDebugUnitTest、lintDebug、assembleDebug通过，228tests/0failures/0errors/0skips，Lint0errors/163warnings/2hints。321旧AIDL测试桩缺新方法，322并行修改期间Lint内部分析异常；补桩并固定源码后323完整通过，没有禁用检测。
- 156云端39564471507/headf1b95d1已completed/success；157按新push独立核验。公开Release仍为147-dev，不把最新源码/本机包混称Release。
- 用户恢复面积研究。官方源码＋本机驱动IDA核对后，有限只读实验取得硬件参数及20秒3235帧、3234次变化；未解码面积。产品仍为Root evdev时长/位移。179原生23组真实手势四轴全0，不能称物理面积实现。

## 功能差距

| 功能 | 已实现内容 | 已安装并实测的行为 | 未完成或已知问题 | 下一步 |
|---|---|---|---|---|
| Root触发/原生输入 | evdev时长/位移入口；停止面积诊断；SystemUI改为合并手势运行时 | 184原生手势注册/底角扇形已验；既有Portal取词证据见下文 | 真实面积无有效证据，按用户要求已停止；微信取词仍有缺口 | 不再研究面积，继续取词与拖放修复 |
| 激活状态/框架 | ModuleActivation附当前宿主Binder存活凭证，死亡清就绪，旧死亡不清新报告；精确phantom数量Hook保留 | 156真机已激活→结束Portal18074→偏好清空/首页立即未连接；没有自动反复拉起；恢复22290/180成功 | 修复状态误报不等于解决LMK回收。模块自己重启丢token而宿主仍活时，现no-op查询不保证重新报告，需已有显式重载 | 如真实后台触发中断，针对宿主生命周期分析，不扩大系统进程保护 |
| AICR V2图片/屏幕OCR | 现有AicrOcrClient与ML Kit可选，结果原页浮窗 | 111/114等既有AICRV2真实识别证据；155新界面已安装沿用156 | 156未重复OCR；AICR不是完整小爱识屏，能力Provider不是OCR | 保留已验证客户端，只处理实际缺陷 |
| 全选/拆词/合并/撤销 | 157全选/取消改为四角选框＋词块图标，拆词用Miuix剪刀，合并Miuix Merge；44dp触区/22dp图形，原语义/选区/禁用态保留；Unicode字形边界与撤销沿用156 | 157构建并已安装；156全选取消、合并3→1保留16字符及选区已实测 | 157图标实感未验，Unicode字形/撤销主要是自动回归证据 | 只针对实际反馈，不重复同样本矩阵 |
| 连续选词/边缘滚动 | 155已修横选纵滚区分、固定手指边缘续选、回拉恢复初始选区 | 156包含已通过方向/边缘回归；旧132/144有拖出/取消证据 | 新连续选词的完整物理手感未实测 | 只针对具体反馈验收 |
| 复制/搜索/词典/分享/拖出 | 常用操作一级、词典书本图标、内容长按直拖；分词无中转站入口；157中转站纯文字改为Root按落点填编辑框，不将正文交给聊天原生drop | 157已安装；微信8.0.79实际DEX确认原生文字drop直接发送、图片解析依赖_data；旧156生成图drop accepted但图片未出现 | 157自有文件传输助手实测：纯文字填框报“拖入位置被系统窗口遮挡”，图片drop accepted但未出现图片、未触发副本查询；分词本身仍为原生文本拖放，不冒称也改为填框 | 手机空闲时仅自有文件传输助手验证，不动私人会话 |
| 工具栏/返回 | 无返回关闭中间层；拆词原窗更新；搜索Back先退IME再退结果，原分词session保留 | 156词窗实际关闭返回底下自有设置，无新增操作页 | 搜索/词典新完整返回与编辑键盘顺序仍未实测 | 保留原页架构及选区 |
| 中转站 | 157参考华为直接拖内容/分组选取，紧凑单行标题；拖出显示灰条、源树保持原尺寸，取消恢复原选区；≤16%。纯文字Root填框，图片按次私有URI，微信_data查询创建受控Downloads副本 | 已构建安装；旧156针对图拖放证明源窗仍attached/可见，但微信未收图。157实际图拖后显示灰条但微信未收图；纯文字填框失败后恢复原托盘并保留内容 | 图文混选拖出暂明确拒绝，需分别选取，避免文字意外发送；新灰条/取消/填框/图片接收未验；副本24小时机会式清理不保证准时 | 优先空闲时单图/纯文字输入框实测；后续再实现安全混合取用，不恢复多余页面与控件 |
| 四边菜单 | 四种外观均跟随本次触达边，删除其他位置模式 | 既有部分四边操作证据 | 四种外观的完整落点未验 | 有具体故障再针对性核查 |
| Miuix一致性 | 157托盘单行标题、44dp/22dp图标、统一主题；常态无底栏，多选才出现必要操作，移除易误关的底部关闭入口 | 157构建并已安装；156浅色词窗选中/禁用有真实证据 | 157托盘实感、深色/横屏/大字号未验 | 随实际反馈继续，不新增页面或工具栏 |

本批旧156针对微信测试只用生成内容和自有文件传输助手；3个生成中转站条目已按确切ID删除，恢复原4条/3图，未动原内容。157后台覆盖安装；观察20秒12次真实接触及多次前台切换，停止UI测试，不操作用户当前页面。截图只在任务本机暂存，不提交Git。持续测试授权有效，无需重复申请。

## 157 微信故障补充（2026-10-21）

- 同机对照：微信WMS窗口可见且聚焦，UiAutomation窗口列表没有微信；模块自己的应用窗口能正常出现。用户明确说明HyperOS 4因智能体操控相关限制关闭了微信无障碍。系统限制的具体实现尚未反编译核验；停止排查FreeMoe，不修改其他模块或系统限制。
- 取词依赖活动无障碍窗口，当前因此不能在微信成功取词。现有AICR客户端可复用，但无节点树时的安全窗口来源核验及按压识图尚未接通，不能把已有OCR能力写成微信按压已修复。
- 中转站文字的ACTION_SET_TEXT路径同样依赖微信暴露输入节点，当前不成立。原生微信文本drop直接发送而非填框，不能为消除报错而恢复该行为。
- 图片原生drop与无障碍取词是不同链路：157生成图实际drop accepted但接收方没有查询图片Provider，也没有生成兼容副本。继续查接收监听与事件路由，不能据此声称_data兼容已生效。
- 157完整构建228项通过、单次安装181已确认；这些行为故障仍未解决。尚有两个157生成测试条目留在中转站供针对性复测，后续只按确切ID删除，保留用户新增条目。截图及私有元数据仅本机暂存。

## 最新范围调整（2026-10-21）

用户明确停止真实触摸面积研究，继续Root evdev时长/位移触发。历史原始帧不代表有效面积，不再继续实验或校准。优先微信取词、内容拖放、中转站及Miuix统一；另一个完整项目待用户后续交付，本批不预建合并结构。现有依赖已使用compose-miuix-ui/miuix 0.9.3，不是新引入组件库。

179最终收尾：build377 lintDebug/assembleRelease成功（1m39s，Lint0错误185警告2提示），明确执行minifyReleaseWithR8。最终APK仍7,429,983 bytes/7.09MiB，比178同条件Release/R8/arm64减少65.4%；SHA256 `88a293ee4adf49d90b2c07dd052fbd8c298963a3de0a201bd35cfae255af29bb`，签名不变，设备base.apk哈希相同。首包后发现真实更新API403，修复并追加Root用户精简后同版本第二次覆盖安装；不伪称整批仅安装一次。最终重载Portal PID25488，注入/Root输入/reader203且ready=true；重载SystemUI PID25885，持久诊断明确Merged gesture hook version=203，输入注册/小窗Hook安装。只结束安全服务:ui旧PID12373，下次打开才加载新侧栏代码，未称其已加载203；框架/桌面/AICR未重载。手机锁屏，不唤醒、不发消息、不再跑截图矩阵。

已确认：首包四模块首页、OCR/release搜索直达高亮及Back保留查询；最终包构建/安装/Portal与SystemUI加载。未确认：最终Root精简页面与更新弹窗实际显示、下载/系统安装新版本、微信各页面真实按住与新中转站拖放、OCR删除分支后的完整设备识别、长结果列表滚动恢复。7.09MiB包已交付outputs/HyperDragShare-1.7.179-arm64-v8a.apk；正式Latest仍176，179尚未发布Release。旧178心跳保持暂停。

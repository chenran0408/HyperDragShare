# AICR 可选 OCR 入口核验

## 2026-10-21 静态证据

目标为手机当前安装的 com.xiaomi.aicr 4.12.26。本地分析 APK 与设备 base.apk SHA-256 均为 `6e960db290329cd96d567cb62be340656243412b39ec20a0ca5b630e26f696e5`，不是仅按文件名或版本推定一致。

- AAPT 读取 Manifest：`com.xiaomi.aicr.access.AiCrCoreService` 与 `AiCrCognitionService` 均 enabled/exported=true，分别位于 `:coreService` / `:cognitionService`。两项和 application 均未声明 android:permission。
- Core action 为 `com.xiaomi.aicr.access.AICR_ENGINE`。Cognition action 原样为 `com.xiaomi.aicr.access.AICR_CONGNITION_ENGINE`，不要自行更正其拼写。
- 对已定位的 AiCrCoreService 进行单类读取：普通 onBind 路径检查客户端连接数量和频率后返回 mBinder；没有在所读路径发现签名权限校验。`ai_rest` 为不同分支，不作为 OCR 实验入口。
- mBinder 的 getPluginBinder 接收回调、address、desc、minVersion 和返回 Bundle；检查插件管理器就绪、客户端字段、连接数量、插件注册和版本后向插件管理器请求 Binder。这里只见调用 UID 记录，不足以排除插件管理器、插件 Binder 或模型加载中的授权检查。
- 插件管理器字段类型为混淆类 w87。下一步定位其插件描述符及视觉插件取得路径，再核验 OCR Binder 的数据格式与权限；不要猜测 transaction code 或复制反编译实现进入本项目。

## 证据限制及实验顺序

REA 接口缺少调用者提供的 jadx-headless-mcp JAR，未能执行；使用已有 JADX 1.5.6 单类读取。第一次 JVM 默认堆申请失败，限制 768 MiB/单工作线程后生成目标类，但工具总退出码仍为 1，报告一项错误。因此 Java 输出仅用于定位，不作为精确 DEX 协议证明；关键事务与参数必须进一步核对字节码。

本轮仅只读包信息与静态分析，没有绑定或启动服务，没有截图、读取当前屏幕内容或修改权限。上述证据支持继续研究直接绑定方案，但**不证明普通应用可完成 OCR**。

接入前需依次确认：普通模块 UID 绑定结果、视觉插件描述符与 Binder 获取、合成图片 OCR 文本结果、断开/超时及失败反馈、真机耗时和与现有引擎的体验比较。真实调用必须保留返回状态与明确失败，不将 root 身份的成功当作普通 UID 成功。小爱 ScreenRecognitionProvider 仍只是能力查询，不接收图片、不返回 OCR 文本。

## 视觉插件路径与会话约束

继续沿上述同哈希 APK 定位，未运行系统服务：

- w87.j 注册 `com.xiaomi.aicr.plugin.IVisionService`，版本 `V2`；工厂 fr0 的分支 10 创建 `com.xiaomi.aicr.plugin.VisionPlugIn`。w87.a 在插件初始化后返回其 getBinder；VisionPlugIn.getBinder 返回自身。
- IVisionService 的单类反编译此次正常退出。接口包含 setImage(Bitmap)、setImageParcelFileDesciptor(ParcelFileDescriptor)、doOCRDetect(int)、doOCRRecognize(int)、getOCRVersion()。方法名中的 Desciptor 为 APK 原拼写。
- Java Stub 显示事务号分别为 1、2、3、4、6；这些是静态定位证据，正式实现前仍需用 DEX/生成协议核验 Parcel 格式，不能仅据表格手写盲目 transact。
- VisionPlugIn 的 setImage 返回递增 session，并替换/回收上一份图片缓存。doOCRDetect 按 session 匹配图片后保存位置结果，doOCRRecognize 使用图片和此前位置结果。因此识别必须遵守 setImage → detect → recognize，不可跳过检测。
- 所读实现是插件实例内单份图片和位置缓存，不是按 UID 分组；各方法分别加锁，不是跨三次 IPC 的事务锁。客户端自身串行仍不能排除小爱等其他客户端覆盖缓存；session 不匹配应作为明确失败，不读取其他会话或循环无上限重试。
- 所读普通 Core/插件取用/视觉方法路径没有发现调用 UID 的授权拒绝，但这不是所有底层模型入口和运行时策略无权限限制的证明。需在模块普通 UID 上验证绑定、V2、合成图片与返回结果；root 调用成功不能代替该证据。

分析工具限制：w87、fr0、VisionPlugIn 单类读取仍各有非零总退出状态，输出可用于定位但不作为完整还原保证；IVisionService 单类读取正常完成。没有复制输出代码进应用，也没有据这些静态结果新增“可用小爱 OCR”选项。下一实验应先用自建合成图片，记录每阶段状态与耗时，再决定是否进入正式 OCR 引擎选择。

## 核心连接协议与结果格式补充

IAiCrCoreService、ICoreServiceCallback 和 OCRRes$OCRResult 三个已定位类均由 JADX 单类读取正常完成；这里只记录协议观察，不移植其实现。

| 核心事务 | 输入顺序（接口令牌之后） | 输出（异常头之后） |
|---|---|---|
| 1：getPluginBinder | callback Binder、address 字符串、desc 字符串、minVersion 字符串 | 插件 Binder、Bundle 存在标记与 Bundle 内容 |
| 2：releaseConnect | address 字符串 | 无结果值 |
| 3：isSupport | desc 字符串 | int 表示布尔 |

核心接口令牌为 `com.xiaomi.aicr.IAiCrCoreService`。回调令牌为 `com.xiaomi.aicr.ICoreServiceCallback`：事务 1 onDownload 携带 Binder，事务 2 speed 携带 String/long，两者为 oneway。实验回调应明确记录事件，不把下载完成回调等同于 OCR 已完成。实验结束要按自己创建的 address 释放连接并 unbind，不操作其他客户端连接。

返回 Bundle 的 Status 静态枚举顺序：0 OK、1 DOWNLOADING_FIRST_USE、2 NEED_UPGRADE、3 SERVICE_NOT_CONNECTED、4 SERVICE_ERROR、5 NOT_FOUND、6 SET_IMAGE_FAIL、7 UNSUPPORTED_CAPABILITY、8 PERMISSION_DENIED。枚举反编译存在恢复警告，后续须以 DEX 构造参数复核；不能用无依据的默认值把缺失 Status 当成功。

OCRResult Parcelable 按顺序写入 paragraphs typed array、total_text String、entities typed array。这说明不能从 Parcel 首部直接 readString 获取全文，也不能扫描字节寻找文字冒充协议解码。需要继续核对段落、行、字符、实体的 Parcelable 布局及对应 Creator，再独立实现受版本限定的读取方式，或使用合法可获取且匹配 V2 的接口库。

当前没有实现或运行实验客户端；没有新增应用权限、引擎入口或构建产物。下一步优先补全结果结构的确切布局，再准备一次可释放连接、使用合成图片且带阶段错误的普通 UID 实验。

## 调试结果读取器（未连接服务）

已按观察到的字段协议独立编写 `app/src/debug/java/.../AicrV2ResultReader.java`，不复制厂商 Parcelable 类、不加载厂商类，也不加入 release 源集。读取 typed result 存在标记，完整消费段落/行/字符/实体后返回 total_text；未知位置类型、异常存在标记和不可能的数组长度明确报错。没有引擎调用或产品入口。

本地 4 项 Robolectric 测试使用手工构造的合成 Parcel，验证嵌套结果后的尾部哨兵位置、null 结果区别于空字符串、异常长度、未知类型拒绝。全部 140 项测试、Lint、assembleDebug 通过。合成测试证明读取器与当前协议描述一致，不证明已匹配真实厂商输出；正式接入前仍需真实返回包与 Creator/DEX 对照。段落、行、字符、实体、位置 5 个类的单类读取此次均正常退出。

本轮未改变用户可安装行为，保留版本 1.7.72；此前 outputs 中归档的 1.7.72 不覆盖，最新本地构建多了未调用的 debug 读取器。设备仍为 1.7.71。下一步准备带连接清理的普通 UID 实验客户端，用合成图片验证完整链路，不能把本地 140 项测试当作 OCR 已通过。

## 1.7.73 调试实验入口（待真机运行）

新增 debug-only Instrumentation `com.leaf.hyperdragshare.codex.AicrOcrProbe`，以目标应用普通 UID 运行，不请求 root。生成 640×160 黑白图片“HyperDragShare 123”，按核心绑定 → 插件与 Status → V2 → setImage → detect → recognize 执行；返回阶段、UID、耗时、全文及连接释放结果。成功条件暂为全文含测试标记 123，只证明基础链路，不代表中文、版面或性能验收。

使用前确认当前设备测试窗口和安装版本，再运行：

```text
adb shell am instrument -w com.leaf.hyperdragshare.codex/.AicrOcrProbe
```

该入口不启动页面、不截屏、不读取相册；Instrumentation 会影响模块自身进程，不能与其他模块操作测试并行。15 秒仅限制等待 service connection；同步 Binder transact 无可取消超时，不宣称全链路有时间上限。发生阻塞时记录最后阶段，结束本模块实验进程，不强停 AICR。退出正常/异常路径尝试 releaseConnect 与 unbind，释放失败单独报告。不会自动重试下载或变更系统权限。

代码来自协议描述的独立实现，仅位于 src/debug；release 不含入口或读取器。暂未调用设备服务，也没有将其接入正式 OCR 设置。实际服务行为与结果 Parcel 仍待本机验证。

## 真机基础实验通过（1.7.76 / 100）

经用户明确授权后，两次普通模块 UID 10270 Instrumentation 成功：核心 Status=0、OCR V2，生成图片全文均返回 HyperDragShare 123；总耗时 1493ms / 280ms，releaseConnect 成功，正常 unbind 完成。没有 root 调用、没有小爱进程转发、没有改权限。由此可选择直接绑定 AICR 作为下一步可选引擎实现路线。

该结果只覆盖合成英文/数字图片，两次调用不代表长期稳定或基准性能。中文与复杂版面、同其他客户端并发导致的缓存覆盖、Binder 阻塞与产品取消流程仍待验证。正式引擎 UI 尚未接入；现有其他 OCR 路径继续保留。

## 1.7.77：可选 AICR V2 引擎（源码验证完成，未安装）

图片、整屏及区域 OCR 接入“文字识别引擎”选择：默认 ML Kit，可主动选择系统 AICR V2（实验）。独立客户端使用普通应用 UID，校验 V2 协议，明确显示连接、权限、模型状态及识别错误，不静默切换引擎。结果读取器从 debug 移入 main；仅实验 Instrumentation 仍限 debug。

同进程只允许一项 AICR 请求。取消不会假装中断同步 Binder；等待其返回并释放连接后才回调、回收调用方图片。连接等待限制 15 秒，不是整次识别超时；其他系统客户端仍可能覆盖服务单一图片缓存。

145 项单元测试、Lint、assembleDebug 通过。新增三项客户端测试覆盖成功后的释放与主线程回调、阻塞调用取消及忙碌拒绝、绑定前取消。尚未安装 1.7.77：中文、实际图片/屏幕/区域页面、返回取消、暗色和系统客户端并发仍待设备验收。设备已安装版本仍为 1.7.76；此前两次生成英文图片成功不能替代本版产品验证。

传送门基线改正为 5.1.5，证据见 [PORTAL_515_AUDIT.md](PORTAL_515_AUDIT.md)。
## 当前产品证据：1.7.111 / 135

前文为按时间保留的研究记录，旧“未接入/未安装”只对应当时版本。当前普通UID的实际AicrOcrClient已经接入可选引擎，未使用小爱内部混淆类、未改变系统权限。默认ML Kit保留，AICR失败明确显示原因。

debug-only实验现直接调用生产客户端，生成960×420的三行中文/英文/数字图片。UID10270返回“传送门文字识别 / 小米15 Pro HyperOS 4 / 复制搜索分享12345”，首次738ms；绑定前取消收到一次CancellationException，随后识别281ms成功，证明本次取消完成后槽位恢复。并不证明同步Binder可被中断，也不覆盖产品取消按钮。

实际ImageOcrOverlay选择AICR识别同一生成图片，进入38字符BigBang独立浮窗；Activity前景仍bilipai。实验退出恢复原引擎设置。浮窗深浅色动态切换也已通过。172项测试、Lint、assembleDebug完成，安装135。

这关闭了合成中文及图片产品入口的证据缺口；复杂版面、屏幕/区域、外部系统客户端并发覆盖、长时间Binder阻塞仍未验，不将少量耗时当性能基准。结果读取器当前只向产品提供全文，不提供可选行几何框。

## 1.7.127：AICR行位置已核验并用于附近识字

AicrV2ResultReader.readResult返回AicrOcrResult(total_text及有序Line text/poly/box)，字符/entity仍完整消费，未知Parcelable/错误presence继续报错。AicrOcrClient.recognizeWithLines复用原请求、取消、忙碌与释放语义；旧recognize(String callback)只从同一结构结果取text，图片及普通全文接口没有换引擎。

普通应用UID以1200×720生成中英文图片的三个已知位置实验通过：Paint文字bounds为76,84–318,131 / 624,334–917,381 / 83,568–437,625；对应AICR四顶点box外包63,71–323,142 / 610.83,319.37–925.26,394.04 / 76,560–443,632，有检测留白但覆盖相应文字。poly是长度可变轮廓，box也不是LTRB/XYWH。原始证据work/hds127-aicr-geometry.txt。产品aicrLineBounds只解释本机已验证的8值box四顶点，不依赖poly点序、不加版本猜测或另一几何回退。

ScreenOcrSession在显式附近请求中使用行位置，其余AICR全文仍沿用total_text。缺失全部位置明确提示可手动框选，非法格式报错；不静默切ML Kit。实际整屏1440×3200附近预选及确认追加通过，见DEVICE_VALIDATION；不代表小爱私有链已接入、Provider能力查询可OCR或真实面积已工作。

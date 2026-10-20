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

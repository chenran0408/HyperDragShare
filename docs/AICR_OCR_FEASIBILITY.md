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

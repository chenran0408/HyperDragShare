# 图片与屏幕识字

当前图片和屏幕 OCR 统一使用普通应用 UID 下的 `AicrOcrClient`，要求目标系统提供 AICR V2。没有另一套 OCR 引擎，也不会在连接、权限或识别失败时自动切换。AICR 连接及识别错误会显示给用户。

## 图片识字

图片拖拽菜单、中转站图片、系统图片分享及显式按压取图都进入 `ImageOcrOverlay`。图片在模块进程解码，软件 Bitmap 最长边限制为 4096 像素，再交给 AICR。识别完成或明确取消后才释放正在被读取的 Bitmap；关闭窗口后忽略迟到结果。成功后进入原页分词浮窗，失败在当前窗口说明原因。图片二维码/条码识别当前使用 AICR `IBarcodeService`，与 OCR V2 是不同插件协议；详见[系统文本、OCR、NER 与条码接口复用手册](SYSTEM_TEXT_AND_BARCODE.md)。

悬浮窗权限或锁屏检查失败时不打开识别窗口。系统分享先在透明接收 Activity 中读取并暂存授权图片，再退出 Activity 并交给悬浮窗。图片 URI 授权与接收边界见 `docs/IMPLEMENTATION.md`。

## 屏幕与区域识字

全屏与区域快捷磁贴、默认关闭的背敲入口会显式发起一次截图。Root 截图在工作线程执行；锁屏时拒绝。区域模式显示冻结截图供用户框选，然后只识别所选区域。结果按文字行展示，支持选择单行或全部分词、复制和分享；识别不持续截图或后台读取页面。当前分词工具栏的“拆词”只处理已有文字，不再次截图或 OCR。

屏幕识字由 `ScreenOcrOverlay` 和 `ScreenOcrSession` 承载。快捷入口的透明 `ScreenOcrActivity` 仅负责转交请求。关闭或息屏会撤掉待执行截图、取消 AICR 请求并移除窗口。AICR 的同步 Binder 识别无法被强制中断，因此请求标记取消后仍保留 Bitmap，直到实际回调完成，再释放资源。

截图仍受系统 Root、悬浮窗、锁屏和来源窗口策略约束。此能力只做用户明确请求的图片或有限屏幕 OCR，不等同于完整小爱识屏，也不绕过安全窗口限制。AICR V2 结构化接口 `AicrOcrClient.recognizeWithLines` 会保留每一行返回的原始文字、polygon 和 box 坐标，不解释或转换这些几何数据。当前图片与屏幕窗口调用兼容的 `recognize` 文本接口，因此界面按文字展示结果，不将行框映射到原屏位置。

协议与目标 ROM 验证记录见 `docs/AICR_OCR_FEASIBILITY.md` 和 `docs/DEVICE_VALIDATION.md`。设备验证记录可能包含历史版本信息；当前引擎行为以本页和源码为准。

# 图片 OCR 与无障碍后备区域

版本：1.7.51 / 75。基线：HyperDragShare `1a042132194643d470fa58163abf7504c8963f88`。

无障碍长按点找不到可用文字或图片节点时，模块在通过应用黑名单、锁屏、图片分享开关和密码节点检查后，截取触点附近、限制在来源窗口内的区域。该图片进入既有拖拽菜单，用户可选择“识字”再进入分词页。截图范围上限为 360 × 220 dp；这是长按附近的后备区域，不是任意框选。离开手势或取消任务后，旧截图不会启动新会话。

## 用户入口

- 传送门或无障碍来源已经获得图片后，在图片分享菜单选择“识别图片文字”。现有无障碍对图片节点的区域截图也作为图片进入同一入口。
- 从相册等应用的系统分享菜单，把一张图片发送给“识别图片文字”。
- 识别成功后打开现有分词页，可选择、复制、搜索或分享文字。
- 无文字、读取失败或识别失败时给出结果，支持重试和取消。

当前版本不提供任意区域框选，也没有接通分词页原有的“重新 OCR”占位按钮。无障碍模式下只有长按后两次节点选择均无候选，且后备检查通过，才会截取附近区域；传送门模式不会自动截图。

## 实现边界

图片仍由现有 ImageStagingClient / ShareImageProvider 暂存及授权，ShareLauncher 构造完整分享 Intent。无障碍后备沿用原有长按手势和图片分享菜单，不改变 Root 跟手、宿主取消语义或 LSPosed 作用域。

ImageOcrActivity 在模块进程解析 content URI，后台解码，最长边限制为 4096 像素。ImageDecoder 负责图像方向处理，解码后使用软件 Bitmap，交给随包中文 ML Kit 模型。没有复制 MI Big Bang 的反编译代码。

每个请求持有识别器和 Bitmap，直到异步识别真正完成才释放；关闭页面不提前回收正在被原生识别读取的图片。同一 Activity 最多有一张解码图片进入识别，新输入排队时丢弃已过期请求，避免积压多个大图。

LatestRequest 由 OCR 和原有文本分词共用：新输入与销毁都会使旧结果失效；迟到的成功和失败都不能覆盖新会话。OCR 结果在 Activity 恢复前台后才打开分词页。日志只记录失败类型，不记录识别文字或完整图片 URI。

## 来源与依赖

- 对照阅读 Nova Text `76d4278a2b6c193572cbe0725182efdf6857dd66` 的 MlKitOcrEngine；本阶段采用独立的入口与生命周期实现，不导入其整套悬浮球、截图和启动代理。
- ML Kit：`com.google.mlkit:text-recognition-chinese:16.0.1`，使用随包模型。
- 官方集成文档：https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- SDK 条款与第三方声明：https://developers.google.com/ml-kit/terms
- Android 17 构建工具要求：https://developer.android.com/build/releases/agp-9-1-0-release-notes

## 验证要求与限制

构建检查：`gradlew testDebugUnitTest lintDebug assembleDebug`。自动测试覆盖请求替换、销毁取消、重试隔离、URI 输入契约，以及图片菜单中的新增动作。

这些检查不验证原生 OCR 的识别率。实机仍需测试中文、英文混排、长图、旋转、取消后迟到回调、从相册分享、两种捕获模式和连续触发。宿主 5.1.5 只有接口存在性的静态证据，未完成兼容验收。

调试 APK 与原版 HyperDragShare 使用相同 applicationId，不能假定可覆盖不同签名的原版。设备上的 com.mengting.bigbang 是另一个包。没有自动安装或更改设备设置。

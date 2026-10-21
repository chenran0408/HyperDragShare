# ColorOS Big Bang 1.0.4：适配参考与边界

样本SHA256：`4fbe38df9fe872040d5dcae3c2521a283e762b28a77742dfeb81aef99bb7c458`。2026-10-21只读分析Manifest、DEX与native，未安装或调用参考软件。反编译只作为行为证据，没有将其源码或模型复制到本项目。

## 已观察到的静态事实

| 功能 | 参考软件实际入口 | 本fork现状与决定 |
| --- | --- | --- |
| 自动触发 | `ModuleMain.onSystemServerStarting`在WMS就绪后注册OplusGestureMonitorManager raw observer；t0/ga/w读取触屏source、finger、raw坐标、displayId、MotionEvent.size及静止时长 | Oplus私有类不能移植到HyperOS。保留作者确认的MI BigBang式Root evdev时长/位移入口；不恢复失效面积校准，不冒称物理拇指识别 |
| 取词 | BigBangAccessibilityService事件回调不遍历；每次触发才按display/坐标从interactive windows查文本、触点偏移；密码/敏感内容拒绝 | 已有Root UiAutomation按触点查询及敏感节点限制，150已设置retrieve interactive windows；目前先修输入/helper生命周期，不能靠取得Root权限就称取词可用 |
| OCR | SystemTriggerProvider仅UID1000且要求MANAGE_ACTIVITY_TASKS，接收截图HardwareBuffer；无文本时交应用内PaddleOCR PP-OCRv6 tiny/small | 没找到可直接移植的小布或Oplus OCR/AICR接口。本fork保留ML Kit及可选AICR，不导入样本模型、不调用其私有Provider |
| 原页界面 | BoomActivity透明全屏、空taskAffinity、不进最近任务，实际通过PendingIntent启动 | 本fork继续用既有悬浮窗，不为参考架构增加Activity操作页 |
| 选词 | chip点击切换、跨词滑动、边缘滚动、选择index保存；搜索/分享先结束动画 | 本fork沿用内容直拖及选区状态；新界面真实手感仍待针对性验收，不以静态相似宣称完成 |

Oplus样本size阈值默认23/255，确认时长默认325ms；这些是它自己的输入和设置映射，不能作为小米无面积数据的阈值补丁。MI BigBang作者经用户确认的输入是Root evdev时长/位移；两份参考不能混写。

IDA检查`libonnxruntime4j_jni.so`，可见标准JNI_OnLoad与Java_ai_onnxruntime接口；未见应用/Oplus自定义OCR JNI。其业务管线位于DEX，继续逆向通用推理库不能补出HyperOS厂商接口。

上述均为静态观察，不是ColorOS或HyperOS真机触发证据。具体取词/窗口/OCR方法定位和原始分析留在任务外部reverse目录；不提交反编译源文件、APK、截图或私人数据。

## 当前可验证改动

152停止复用失效lease并加入精确phantom裁剪Hook；153完成独立输入Run/EOF清理和真实状态反馈。用户澄清要拆词而不要再次识屏追加，专用流程已整段删除；保留初次OCR及现有AICR/ML Kit。拆词只拆原词块，与OCR无关。

用户HyperGesture源码和目标小米WM Shell已核对，已加入不拦截的原生尺寸旁路，尚未启用设备SystemUI scope、未取得有效尺寸证据，详见NATIVE_PRESS_INPUT.md。

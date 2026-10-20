# 主动点取当前界面

## 用途与操作

1.7.121 增加独立的“点取内容”控制中心磁贴。可从模块设置的“添加点取内容磁贴”申请添加，也可在控制中心编辑磁贴。

打开后只显示准备窗和原页选点层。Root 准备完成，底部显示紧凑提示条；点选需要的文字或图片。该次点击由选点层接收，不传给原应用，不占用它的普通长按。返回或取消退出，原页面保留。文字直接进入现有分词浮窗；明确图片节点裁剪后交给用户已选择的 OCR 引擎。

这是额外的主动入口，不能替代拇指面积触发的完成证据，也不代表 bilipai 评论普通长按已经修好。

## 实现边界

- PointCaptureTileService 复用磁贴收起逻辑；PointCaptureActivity 是立即结束的透明桥，不打开设置页。
- PointCaptureOverlay 在模块进程使用既有 InteractiveOverlayWindow/Miuix 主题。准备阶段只连接 Root；用户点选后撤窗，下一 Choreographer 帧才请求节点，避免读取自己。坐标来自该次 MotionEvent.rawX/rawY，不猜最近触点。
- RootNodeCaptureClient/RootNodeCaptureMain 按显式请求读取一次语义树，复用密码、锁屏、黑名单与窗口变化检查。图片仍只取明确节点的实际屏幕区域，不自动切整屏 OCR；无可用节点会显示失败和重选。
- 文本传入 before/after 与 source_area={bounds,width,height}，复用分词/附近追加；图片复用暂存与 ImageOcrOverlay，Bitmap 在暂存回调后回收。
- 返回、取消、息屏、显示移除、旋转或尺寸改变结束该会话，晚回连接/结果丢弃并释放。未增加常驻后台服务、无障碍服务或 LSPosed 作用域。
- 内容开关全关或当前方向禁用时直接显示原因，不建立 Root 连接，也不提供无效的重选按钮。

## 独占系统接口

UiAutomation 只有一个注册位。点取准备期间不能同时使用 uiautomator dump；已有面积取词预热会话或其它自动化也可能占用它。当前版本明确显示占用错误，保留原操作，不重试、不断开别的会话。尚未实现面积与点取跨进程共享同一 Root 连接。

Root helper 的 serve 启动只在反射连接/权限边界处理失败，返回 ready:false/error 并记录原因；不是通用请求吞错。只有成功连接才接收取词请求，结束本次 helper 后保留别的连接。测试期间用截图观察活动取词窗，释放后再做节点 dump。

## 设备证据

目标机已验证：控制中心实际服务入口→生成文字 COPY-119→8字分词；生成 ImageView→Root 区域图→AICR→83字原页分词；bilipai 当前提醒标题→8字原页分词。前台 Activity 均保持来源应用，Root 连接完成后释放。

返回取消后无取词 helper/UiAutomation 残留；占用实验保留控制 helper，模块显示具体占用原因，控制 helper 正常 EOF 退出后无残留。设置申请添加已在控制中心的磁贴返回明确状态；内容全关仅显示关闭按钮、无 Root 连接。

评论区实际点取、真实面积、普通长按、识别中取消、旋转/息屏与完整深色切换仍未全部验收。具体构建、截图和清理证据见 DEVICE_VALIDATION.md。

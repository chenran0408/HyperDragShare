# 439 测试包：微信按压分词对照

## 样本与证据层级

2026-10-21用户提供439测试包，并明确反馈：微信中“按住，传送门”可以分词。包名com.mengting.bigbang，versionName 1.7.0，versionCode 439，100,038,814 bytes；SHA256 D3D2121C216724251D256C6FB024CADFBE81410A2D48418ACB81469CB9655CCE。

这是用户真实操作反馈加本地静态分析；本轮没有安装样本、启用其作用域或取得成功那次的运行日志，不能断言当次一定使用OCR。原APK和反编译输出只存任务work/reference439，不进入本项目。

## 确认的调用链

- `PortalHooks.dispatchRootPointer` → `feedLongPressFallback`：传送门进程消费Root触摸事件，记录按下位置、时长和位移。
- `scheduleLpClassify`启动的任务约120ms后通过Provider的`a11y_text_at`查询文字；文本命中会保存供确认触发时使用。该接口不是小爱能力查询Provider。
- 查询空时仍调用`RootGateway.captureScreenshot` → `RootScreenshotter.capture`，后者运行`su -c /system/bin/screencap -p`。截图不以取得可读无障碍根节点为前提。早期截图按配置裁剪触点附近或大范围区域，供后续触发复用；不能直接照搬其宽泛截图范围。
- `maybeFireLongPressFallback`/工作任务使用保存文字或截图；未保存截图时也可重新截图并裁剪。`runLpFallbackOcr`先尝试文字探测，空结果进入`OcrBoomLauncher.extractTextBlocking`，该方法调用`LocalOcr.recognizeFullText`。此特定路径不应误写为AICR V2。
- 识别文字通过`openLpFallbackSegmentation`启动`TextSegmentationActivity`。本fork仍应复用原页浮窗，不移植该Activity流转。
- 样本该链路有输入法、按住说话等排除，另有可配置AI识图后续路径；不需要把其多级回退、宽泛Throwable和远程识图引入本fork。

上述关键链路由DEX索引定位并用针对PortalHooks的JADX单类输出复核；简单反编译中的浮点类型和异常变量错配不作为源码依据。

## 当前fork差距

当前`PressCaptureSource.lookup` → Root租约 → `RootNodeCaptureMain.captureRequest`，活动根节点不存在时返回`window_not_ready`。现有`safeEmptyOcrRegion`需要先遍历有效窗口，因此“没有节点窗口”与“窗口存在但取不到词”没有共享可用OCR入口。结合此前微信不出现在UiAutomation窗口列表的设备记录，这是与439的关键行为差异，而不是“Root没有截图能力”。

修复方向：保留Root evdev时长/位移；节点可读时直接取词；节点不可读时，另行确认当前前台窗口归属、触点范围、锁屏/系统/键盘遮挡等实际边界，再将本次按压的有限区域截图交给已有AICR V2或用户所选ML Kit，结果交现有分词浮窗。窗口判定不能再次以必须取得微信无障碍根节点作为前提，否则只会重复原阻断。截图与结果必须关联同一次按压，移动、取消或窗口变化时丢弃，不能靠层层重试掩盖失败。

尚未完成：上述独立窗口核验与按压OCR入口的源码实现、构建及微信实测。此文只确认样本差异，未新增产品能力。

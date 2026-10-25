# 退出/销毁资源生命周期

跨进程回调的op关联和完成证据见 [OPERATION_TRACING](OPERATION_TRACING.md)。追踪只借用ID和资源地址生成指纹，不持有Window/View/Context或新增释放点；既有generation/owner guard、资源释放和授权时机继续决定功能行为。

本页维护退出边界与资源约定；当前提交、实际测试结果和功能待办只在 [CURRENT_STATUS](CURRENT_STATUS.md) 维护。动作执行约定见 [DRAG_ACTIONS](DRAG_ACTIONS.md)。正常动作、Bitmap/临时文件/URI授权的释放策略保持原样。

## Exit / Destroy Resource Ownership

| 资源 | creator → owner → async borrower → release point | 退出语义 |
|---|---|---|
| 设置页屏上触发区预览 | GestureSettingsContent创建 → 页面级BottomCornerPreviewOverlay → 无后台借用 → 类别/路由离开、ON_STOP、dispose或结束按钮 | 列表滚动不销毁owner；非触摸窗口登记既有OverlayTouchRegions以抑制模块手势。stop幂等，移窗失败也注销区域；add部分失败清窗口/登记 |
| 传送门模式设置订阅 | PortalPressSettings.observe → PressCaptureSource或设置页面各自持有Subscription → Settings通知队列 → stop/dispose.close | 只读当前用户System键；订阅自有closed guard，注销前排队的迟到通知也不再更新owner |
| IME期间待执行窗外点击 | OutsideGestureRecognizer → 当前识别器 → 单击Handler延时 → IME显隐转换clearAll、窗口消失或原cancel | IME显示取消待关闭操作，普通窗外动作暂缓；已核对的隐藏键盘即时路径保持；输入法隐藏后恢复原关闭规则 |
| 活动预览、菜单、流光、环形窗 | Controller创建 → Controller字段 → 目标查询/动画 → removeGestureViews、各Window.dispose | 退出取消动画/查询，窗口释放，旧代次不得清理新窗口 |
| 退出中的预览 | startPreviewExit移交 → exiting*字段 → 160/220ms退出回调 → finishPreviewExit或立即移除 | generation＋对象身份；先清owner字段再移窗；回调允许短时完成但失效后不访问UI |
| 现代预热窗/首帧监听 | prewarmModernOverlay → Controller → draw/post → finish或disposeModernOverlayPrewarm | 本轮补destroy漏调；旧回调先检查destroyed/owner身份 |
| 现代窗口/模糊监听/Drawable | ModernOverlayWindow/ComposeView → Window/NativeGlassBackdrop → blur listener/属性动画 → dispose | disposed guard、取消动画、移listener、disposeComposition，重复释放不重复注销 |
| 交互窗首帧、触摸区、拖放监听、ViewModelStore | InteractiveOverlayWindow → 同一window实例 → draw/post/drag events → dispose | 保留原拖放结束后退出；首帧和布局监听跟踪实际observer，detach/dispose撤销注册及已排callback；不改变嵌入式权限策略 |
| 图片暂存Bitmap/pipe/URI | 捕获方/ImageStagingClient/Provider → 原payload、编码任务与Provider → 被接受动作 → 原流关闭与TTL策略 | 未接受动作完成时检查cancelled/destroyed；已接受分享可继续；不提前recycle/delete/revoke |
| OCR输入副本 | IncomingImageCache → 本次Activity请求/成功后缓存 → copy线程和主线程结果 → 过期结果删除、成功副本原TTL | onDestroy失效；新intent所有分支先推进代次 |
| OCR/扫码解码Bitmap | ImageDecoder → ImageOcrOverlay.Job → AICR/Barcode请求 → 取消时迟到decode回收/识别完成后回收 | close不强行回收Binder借用中的Bitmap；结果有cancelled门控 |
| Screen OCR截图/裁剪 | Screenshotter/Session → ScreenOcrSession → capture executor/AICR → close或finishReading | readingBitmap在client完成后回收；close取消pending capture/识别，不提前回收输入 |
| 点取Root租约/Bitmap/Window | PointCapture Session → Session → Root executor/staging → close/迟到结果callback | connection迟到即关、Bitmap按原callback释放、closed后不启动OCR；暂存URI仍TTL |
| 分词页面/选区/动画 | BigBangPanelSession → session/InteractiveWindow → segmentation/adjacent/copy/stash → close＋composition dispose/detach | closed/token guard，撤销feedback与frame回调；SwipeSelectView detach移long press/autoscroll；BoomChipPage动画风险待证实 |
| 搜索WebView | SearchOverlaySession → session → WebViewClient/network → close stop/pause/remove/destroy | owner detach/screen-off/Activity destroy均关闭；迟到client风险未在真实WebView调度中证实 |
| 中转站scope/receiver/display listener | StashOverlayPanel.Shelf → Shelf/window → coroutine IO/drag → onDismiss取消scope与注销 | Compose取消抑制交付；已经接受导入按原存储策略，不删除用户条目 |
| 嵌入式Surface/URI grant/Binder callback | StashSurfaceHost/EmbeddedStashDragBridge → host/bridge → Binder/drag → abort、release、原结束回调 | released/ownerAlive/generation guard；Surface释放一次；保持原grant/revoke协议 |
| SideWheel timeout/动画/VelocityTracker | View → View → snap/fling/Handler → detach stopMotion后remove timeout和recycle | Animator.cancel会调用end：取消完成后最后清timeout，不改180ms/5s策略 |
| Honeycomb/TaskSwitcher窗和preview | Controller → 当前View → 帧/动画/背景回调 → removeNow/releaseResources | remove先清owner、撤销帧和token消息；外部cancel/dismiss空View须在guard后解引用 |
| Fan窗与触发捕获 | Controller/FanRuntime → 当前View/capture → 延时确认/timeout/MotionEvent → removeNow | 对象身份/代次、移callbacks、回收MotionEvent；进程级runtime不当短命window处理 |
| Recents壁纸/任务缩略图 | Cache/Repository → Cache/Repository → cover/View借用 → 原cache/repository规则 | cover.detach清引用，迟到callback核对View；不回收借用缓存Bitmap |

横屏/IME命中范围由`FanRuntime`结合既有Hook、WindowInsets与HyperOS状态，`HyperOsFreeformBridge.trackedBounds(true)`纳入IME调整前restore范围；无可靠浮动键盘矩形时暂停窗外捕获。最终窗外动作再检查IME可见性，不以焦点false关闭。真实ROM的信号顺序/尺寸准确性须独立验收。
| BackgroundTouchBlocker InputMonitor | blocker → blocker → 原executor → synchronized stop/dispose | Controller退出调用stop；executor本身未close是待验证持有风险，不据此改输入策略 |
| 主界面/设置/首次引导/更新弹窗 | Activity/Compose → lifecycle/composition → lifecycleScope/effects → onDestroy/onDispose | observer/receiver/Jobs按原路径取消，DownloadManager系统下载可继续；TouchProbe关闭reader |

待验证限制：Robolectric不等于ROM WindowManager/Binder/输入/真实渲染验证；不以弱引用GC或截图缺失认定无泄漏。审计候选清单、分组源码记录和全部实际结果保存在本任务目录。

## 确认的问题与最小修复

| 已复现的代码问题 | 处理 |
|---|---|
| Controller销毁未释放已显示但未完成首帧的预热窗 | 调用已有disposeModernOverlayPrewarm；draw/finish先检查销毁与owner身份 |
| Honeycomb/TaskSwitcher移除后cancel/dismiss构造空对象method reference抛NPE | 用lambda延迟解引用，沿用现有runOnViewThread的空值/身份/线程guard |
| SideWheel detach先remove timeout、后cancel动画，onAnimationEnd又排timeout | 先stopMotion，再最终removeCallbacks；180ms/5秒策略不改 |
| ImageOcrActivity切到hosted/BigBang时旧copy代次仍有效 | 每个receiveImage入口先begin，旧成功/失败不覆盖/结束新请求；原过期副本删除规则保留 |
| 首帧监听、posted确认与迁移后layout监听未由退出路径完整解绑 | Window保存本次监听/待执行callback、跟踪实际observer；detach/dispose清理；旧回调先核对owner |

这是代码/替身时序证据，不足以认定历史截图中所有残留都同源。没有添加延时隐藏来掩盖问题。

## 已审计的入口与保留策略

检查dismiss、close、detach、destroy、cancel、screen-off、显示变化、host Activity销毁及Binder死亡，以及新窗口替换旧窗口。覆盖拖拽预览/菜单/现代窗/提示，交互分词与子弹窗，图片OCR/扫码、屏幕OCR、点取、搜索WebView，中转站独立/嵌入式Surface、侧轮/蜂巢/任务切换/Fan/Recents、捕获触摸层，以及主Activity/设置/首次引导/更新对话框。完整源码索引与分组证据在当轮任务目录的work，入口由CURRENT_STATUS链接。

- **取消/移除**：退出动画、View帧任务、timeout、首帧/布局/拖放/owner监听、receiver/display监听、InputMonitor，沿用各自owner清理。
- **允许完成但拒绝迟到UI交付**：stage/decode/copy、分词/adjacent、已取消识别、目标查询、壁纸载入，以closed/cancelled、请求代次或对象身份隔离。回调可以做约定的资源释放，不能重开窗口或清理新会话。
- **允许动作继续**：已接受的分享/复制/存图/中转站worker持有自己捕获的数据。不能为了销毁窗口提前释放Binder正在借用的Bitmap或成功分享的URI/文件。
- **composition生命周期**：Compose动画、设置加载与协程随composition/lifecycle取消；StashActivity导入的withContext结果受lifecycle取消，系统DownloadManager下载可继续。透明PointCapture/StashWindow/TextSegmentation桥Activity退出不等于关闭独立浮窗。

## 行为测试地图

- `DragShareExitLifecycleTest`：预热销毁/迟到draw，退出动画中destroy，旧退出不清新预览、重复清理，Modern Window模糊监听注销一次且迟到通知无效。
- `GestureOverlayExitTest`：两菜单重复remove/cancel/dismiss；SideWheel snap中detach后无timeout/迟到dismiss。
- `InteractiveOverlayExitTest`：首帧前退出、正常首帧一次确认、已post后退出再回放callback、实际observer迁移后的解绑及Dialog直接退出。
- `ImageOcrActivityExitTest`：新hosted/BigBang替换旧输入的迟到成功/失败隔离；destroy后回收副本；正常成功副本保留供overlay读取。
- `ImageOcrOverlayExitTest`：OCR和QR解码未完重复退出；迟到Bitmap回收、不启动识别、receiver/完成通知一次。
- 既有动作、staging迟到、AICR取消与Bitmap借用、Provider/cleanup/store、拖拽结束/取消、布局/选区、Screen OCR/TouchProbe生命周期测试保留原断言，随全量检查。

## 未证实风险与未覆盖

没有为这些推测修改生产逻辑：BoomChipPage部分posted layout/属性动画缺少独立close接口；分词/导入/provider/AICR同步工作可保留Context至返回；BackgroundTouchBlocker executor未随Controller close；WebViewClient销毁后迟到页面通知；进程级FanRuntime无独立teardown。代码分析不等于泄漏测量，尚无稳定现场证据要求改其行为。

真实WindowManager/Surface/Binder死亡与ROM特殊detach顺序、配置重建/旋转/多屏/锁屏/IME、大字体主题视觉、实际URI授权撤销及接收应用发送仍未设备验证；没有操作手机或完整Android模拟器，也未量化堆内存/线程增长。Root输入权威、宿主取消回放、DragShareActions、原Bitmap/文件/URI策略未重设计。

下一批最小范围：针对BigBangPanelSession.close与BoomChipPage待执行布局/动画补可控退出测试；仅在证明closed后仍访问View时撤销对应任务。不同时拆输入/会话或窗口框架。

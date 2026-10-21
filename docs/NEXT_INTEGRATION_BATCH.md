# 后续整批：导航区域、复制直达与原生侧边栏

2026-10-21用户在169游戏避让期间新增，尚未实现/合并。

1. 底部小白条与Hyper手势：不通过隐藏系统导航或全面禁止底角动作解决。先核对当前独立托盘/分词窗口的systemGestures、mandatorySystemGestures、navigationBars边界；可移动托盘须避开系统导航区。对模块面板/把手触点，在SystemUI的DOWN时让位并保持整段归属，不在MOVE中争抢。仅靠Compose.systemGestureExclusion不能约束独立Root/Hyper监听。白条具体所属窗口仍需实机核对。
2. 澎湃AI引擎复制直达：当前AicrCopyHooks已Hook com.xiaomi.aicr.copydirect.TextDirectProvider.call/processText，按用户开关读复制文字并进入已有分词浮窗。先与4.12.26/2030041226的实际调用链核对：参数、返回结果、权限、系统实体动作是否可保留；不把OCR接口当全功能复制直达。不自动扫剪贴板或读取私人索引。
3. 已读取用户提供的《给主任务的接口复用与侧边栏交接.txt》。候选native-sidebar-tools提交2b67208a0056c63ca54da3ed3623f2c428758227基于165/189，目前主线已169/193；不能安装旧候选覆盖新版。先审查并适配最新StashOverlayPanel、原生文字拖放、MediaStore图片URI、成功DROP不抢焦点、NOT_FOCUSABLE策略，再整批合入。来源worktree由其他任务维护，主任务不修改其工作树。

候选侧边栏以安全服务com.miui.securitycenter:ui/40001365为目标，保留原应用条/搜索/场景工具，通过Surface嵌入本模块中转站。其NER、二维码、Surface四区和手势仲裁只有静态/候选构建证据，未安装验收；不能借用本项目OCR成功的证据。新作用域与生命周期边界在合并前核对。先四区和焦点/返回，再NER与二维码普通UID能力验证。不复制反编译实现，不破坏系统权限。

下一轮先读CURRENT_STATUS、GAME_AVOIDANCE和此记录。保留168文字直接发送的新用户决定；不要恢复旧填框透明拦截层、分享联系人入口或无效校准。

# 原页交互窗口迁移：代码边界

2026-10-21，基于当前源码审计，尚未创建或真机验证新窗口。

## 已确认

- OverlayWindowPolicy.portal 使用 TYPE_SYSTEM_DIALOG，现有传送门宿主拥有对应系统窗口能力；accessibility 使用 TYPE_ACCESSIBILITY_OVERLAY。普通模块当前没有 SYSTEM_ALERT_WINDOW 声明，不能直接把这两种权限用于模块 Activity/Service。
- ModernOverlayWindow/ModernOverlayComposeView 为旁路拖拽预览设计：不可触摸，生命周期由窗口附着管理。交互面板必须独立窗口，不能改变预览 NOT_TOUCHABLE 的不变量。
- 现有 Compose owner 提供 Lifecycle/SavedState/ViewModelStore，但交互页的 BackHandler 还需要返回分发 owner。
- BoomChipPage 接受 Activity，使用其资源、LayoutInflater、Context 和 BigBangSettings；BoomActionHandler 直接判断 TextSegmentationActivity 并调用搜索、复制后退出。因此不能简单在 Taplus new 一个页面并期待原有行为全部正确。
- BigBangSettings 使用 Context 读取本地设置；Taplus Context 不等于模块 UID，必须显式取得模块配置，不能把错误宿主的默认设置当兼容兜底。

## 分词实施顺序

1. 将词块视图依赖的资源 Context 与操作回调分开：复制后退出、搜索、分享由宿主明确提供，先让当前 Activity 使用同一接口保持行为，再建立窗口宿主。
2. 提供独立可交互窗口的 owner、焦点/IME、返回分层和销毁路径；继续复用现有面板布局与词块组件。失败记录原因并保留原系统操作，不把透明 Activity 改名当原页窗口完成。
3. 传送门入口在原手势结束后显示；无障碍入口仅由已启用服务使用自身窗口类型。系统分享/其他入口不能假定这些宿主存在。
4. 真机核对原应用前台不变、可连续选词、弹窗返回先取消再关闭、编辑键盘焦点、分享/搜索后恢复、锁屏与宿主退出清理。源码与单测不替代这些行为。

## 中转站侧边小窗的不同约束

StashStore 属于模块私有存储；StashClient 目前仅提供受限 stash_put。StashImageProvider 不导出，外部图片读取需要显式 URI grant。因此传送门侧边窗口还需要模块提供受限列表/操作入口和按次读图授权；不能直接访问私有数据库或放宽 Provider 导出绕过。

侧边窗口应复用条目内容与操作，同时与完整管理列表区分：收起只留最近内容与数量，展开支持取用，管理按钮放次要位置。当前尚无该窗口、跨 UID 列表接口或唤回机制；不把完整 StashActivity 当侧边中转站完成。

审计补充：上述方案是不切换 Activity 的独立系统覆盖窗口，不是原应用 token 下的子窗口；后者当前三作用域并不具备。首批解耦限定于 BoomChipPage/BoomActionHandler/TextSegmentationActivity，把 dismiss/afterCopy/search/share 四个宿主动作变成显式回调。TextSegmenter 还会把 Context 转为 applicationContext 后读取 assets/filesDir，窗口接入时必须分别处理模块词典资源与宿主缓存，不能仅覆盖 getResources。尚未修改这些代码。

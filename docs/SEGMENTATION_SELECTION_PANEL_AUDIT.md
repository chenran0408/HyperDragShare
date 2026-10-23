# 分词选区操作栏审计

## 参考来源

- 上游：`upstream/main`，`1a042132194643d470fa58163abf7504c8963f88`。
- 对照文件：`app/src/main/res/layout/multi_select_bar.xml` 与 `BoomActionHandler.java`。
- 上游选区栏提供搜索、词典、剪切/拆词、分享和复制五项常用动作；其动作处理器将这些按钮连到选中文本操作。

## MMGG 当前入口和桥接

- 系统面板路径：`BigBangPanelSession` 调用 `BigBangOverlayContent`；选区状态及动作由该 session 传入。
- 内容路径：Compose 的 `AndroidView` 承载 `BoomChipPage`/`SwipeSelectView`；选区工具栏在 `BigBangOverlayContent` 的 `hasSelectionToolbar` 分支绘制。
- 常用动作继续使用 MMGG 现有回调：搜索 `onSearch`、词典 `onMoreAction(Dictionary)`、拆词 `onMoreAction(Split)`、分享 `onMoreAction(Share)`、复制 `onCopy`。
- 合并、翻译、链接/电话直达和加入中转站仍在“更多选中操作”二级列表，分别调用现有 `BigBangMoreAction` 或 `onStash`。没有改动作语义和桥接。

## 本次改动

- 选中后显示带图标和短文字标签的常用操作栏，避免仅靠图标和长按提示辨认。
- “更多”固定在栏尾，避免与常用项一起横向滚动后被藏到屏幕外。
- 工具栏高度及顶部预留从 48dp 调为 56dp，以容纳图标和标签；选区定位仍使用同一锚点与 8dp 间距。
- 范围仅限 `BigBangOverlayContent.kt`、几何测试和本审计记录；`BigBangPanelSession.kt` 的动作桥接保持原样。

## 验证与限制

- **逻辑测试通过**：执行 `:app:testDebugUnitTest --tests com.leaf.hyperdragshare.codex.BigBangSelectionToolbarGeometryTest --no-daemon`，BUILD SUCCESSFUL；覆盖标签栏高度增加后，首行词块与工具栏仍保留 8dp 间距，以及已有的锚点/边界夹取用例。
- Gradle 配置期间报告当前环境未安装 platform-tools 且 SDK 许可未接受；本次不需要设备工具，目标测试仍成功完成。
- 本次没有运行设备交互、Gradle 全量回归或构建。Compose 实际触控命中、不同屏幕宽度/字体缩放下的视觉效果仍待模拟界面或用户验收。

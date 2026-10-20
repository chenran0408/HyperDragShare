# 附近扩展取词：接线证据与实施方案

状态：2026-10-21，源码1.7.89。本文是源码核验后的实施方案，尚未实现或真机验证。没有操作设备，没有读取新的屏幕内容。不能用已有下拉动画宣称已经支持“炸了又炸”。

## 当前缺口

- `TextSessionCoordinator.java` 的 `peekAdjacentText()` 永远返回 null，且没有会话数据来源。
- `BoomChipPage.java` 已有上下边缘拉动、提示与 `OnAdjacentRequestListener`，但 `BigBangPanelSession` 没有注册监听。`replaceWords(segment, text, targetWordIndex, charOffset)` 已能在插入前文时平移原选择区间。
- **默认传送门路径不支持相邻文本获取。** `PortalContentCaptureSource` 只读取 `sContent`、图片和初始触点；没有相邻节点、段落或边界。不得猜一个私有接口或默认启用无障碍来补齐。
- `DragShareController.openTextSegmentation()` 当前发送的是 `lastX/lastY`，这是拖拽结束坐标，不能作为原文触点。附近识字必须另存初始触点和捕获时的屏幕坐标条件。
- `DragShareAccessibilityService.selectCandidateAt()` 在长按后已有一次同窗口快照及分类结果，但最终只返回命中项，附近候选被丢弃。
- OCR两条产品路径目前均只传递最终字符串。ML Kit 原始结果有文本块/行边界；`AicrV2ResultReader` 已读取但丢弃每行文字与 Location 多边形/box，最后只返回全文。因此仅把全文字符串传给面板，无法正确识别“触点附近”。

## 优先方案：显式截图，为传送门提供真实附近内容

技术上可以复用现有 Root 截图与 OCR，但当前不能直接接个按钮就算完成。建议做成用户明确点击“附近识字”的动作，按以下端到端步骤实现：

1. 保存传送门捕获时的初始触点；不要复用拖拽菜单落点。该信息随现有受限 Provider 请求进入当前分词会话。
2. 用户点击附近识字时暂时隐藏当前分词窗，再等待窗口移除后的绘制时机执行一次截图；不能截到自己的面板，也不能每次拖拽自动截图。当前 `InteractiveOverlayWindow` 只有 show/dismiss，dismiss会销毁session，必须增加可恢复的暂时隐藏，或显式保存/重建当前session。
3. 截图期间检查锁屏/息屏并取消；截图完成后恢复窗口。画面可能已经滚动或切换，原触点不能直接可信：至少把所识别的候选区域及文字给用户确认；若不能定位原块，应让用户框选，不能自动把不相关页面文字加进来。
4. 保留 OCR 行/块及坐标，以原触点或用户选区为锚点，选择附近实际文字；只保存本次结果，不后台持续采集。ML Kit可直接使用 boundingBox；AICR必须将已有V2字段解析成明确的行与坐标结果，并先验证目标ROM字段含义。不要在用户选择AICR时静默换成ML Kit。
5. 初次展示可添加的附近段落，确认后加入当前面板；随后上下拉可在这份静态截图结果中继续取邻段，不再截图。必须说明这是当前截图中的可见文字，不是滚动加载更多页面。
6. 扩展成功保留原选择；不要把已有合并/拆分的词重新切碎。实现时应拼接新段的分词边界与旧 `currentSegments`，只对新段分词。`BoomWordsLayout` 分词数组包含起止位置及 `-1` 分隔，不能直接把两个数组相连。
7. 截图、识别、取消、结果为空均要有明确状态；失败保留现有文字与选择。原图在任务结束且引擎不再引用时回收。

### 需要修改的接口与文件

| 文件 | 最小职责 |
|---|---|
| `DragShareController.java` | Session保存初始触点及当时尺寸；请求中分别传原触点与面板定位信息 |
| `BigBangOverlayLauncher.kt` | 传递捕获上下文，协调显式截图及当前窗口恢复 |
| `InteractiveOverlayWindow.kt` | 安全暂时隐藏/恢复，不能用dismiss伪装隐藏；息屏继续关闭 |
| `BigBangPanelSession.kt` | 管理本会话附近段落、加载状态、用户确认和保留选择的追加 |
| `BigBangOverlayContent.kt` | 仅有可执行来源时提供附近识字入口，显示进度/失败/候选确认 |
| `BoomChipPage.java` | 将预览查询和拉动请求交给实例监听；删除全局伪协调器依赖 |
| `TextSessionCoordinator.java` | 删除永远null占位；若保留名称，必须改为每会话实例数据而非全局单例 |
| `AicrV2ResultReader.java`、`AicrOcrClient.java` | 保留并返回V2实际行文本/坐标，维持已有释放语义 |
| OCR识别共享代码 | 仅当两条现有路径确实需要复用时抽出结果结构；ML Kit和AICR提供同一种有边界结果 |

`RootScreenshotter` 另有真实边界问题：当前先阻塞读stdout，再执行waitFor(timeout)，4秒超时不能覆盖读流阻塞。显式截图循环依赖它时应先修正该进程边界，不能用多级重试掩盖。

本方案需要一次集中真机验收来确认隐藏窗口后的截图内容、屏幕旋转/坐标、AICR结构结果、取消后恢复与Bitmap生命周期。当前只能认定技术可行，不能认定产品可用。

## 可并行的较小方案：已有无障碍长按快照

这条路径不解决默认传送门限制，只适用于用户自己选择并开启的无障碍捕获。

1. `selectCandidateAt()` 已经得到同窗口 `Buckets`；从非编辑文字候选中保留同包、与命中块横向相交且位于上/下的最近可见块。排除包含命中块的父聚合节点和重复文本；仍遵守密码/锁屏/黑名单过滤。
2. `AccessibilityCandidateSelector.Selection` 增加只含文字的before/after上下文；`CapturedContent` 随内容携带它，不持有AccessibilityNodeInfo。
3. `AccessibilityContentCaptureSource`、`ThumbCaptureSource` 传递该上下文；拖拽路径通过Controller→受限Provider→Launcher，直接拇指路径进入Launcher。
4. `BigBangPanelSession` 的实例监听向BoomChipPage提供可用邻段，拉动后只对新段分词并拼接旧分词边界；成功后消费对应邻段，无相邻内容就不出现提示。
5. 关闭会话即释放上下文；不能把同一全局单例用于多个分词窗口/返回分词标签，以免串入别的应用文字。

具体涉及：`AccessibilityCandidateSelector.java`、`DragShareAccessibilityService.java`、`CapturedContent.java`、`AccessibilityContentCaptureSource.java`、`ThumbCaptureSource.java`、`DragShareController.java`、`BigBangOverlayLauncher.kt`、`BigBangPanelSession.kt`、`BoomChipPage.java`；清理 `TextSessionCoordinator.java` 占位。

## 验收标准

- 主路径必须分别标明传送门显式截图、无障碍静态节点快照、纯文本/中转站无上下文，不能统称全部支持。
- 原选择与合并/拆分结果在前插/后加后保留；无邻段时没有可点击的假入口。
- 不默认开启无障碍，不增加系统Hook作用域，不持续截图或遍历。
- 真机证明截图没有自身窗口、定位正确、取消不丢原文字；自动测试只覆盖段落筛选及分词位置偏移，不能代替设备证据。

### 1.7.91：真实邻段扩展，限定无障碍快照来源（未安装）

无障碍长按已有的同窗口快照保留同包、横向相交、非编辑的最近上/下文字块，排除重叠父块、同文和异列；不新增节点遍历时机。邻段随CapturedContent、受限Provider传入分词会话，拇指路径复用同一入口（不表示面积输入问题已解决）。上下拉每侧最多加入一个邻段；无数据时禁用扩展，移除恒返回null的全局占位。只分词新增段落并拼接既有边界，保留合并/拆分和原选择，消费的邻段不重复加入；搜索返回快照保存剩余邻段。

155项单元测试、Lint、assembleDebug通过。新增4项覆盖真实邻段筛选与带标点/换行的前插后加保留原分词边界；未覆盖真实窗口拉动。没有设备操作，设备仍1.7.80，源码1.7.91（115）。传送门、图片OCR、纯分享文本尚不支持该邻段来源，不把补充路径算作默认主路径完成。

主路线仍为传送门＋Root。静态复核5.1.5发现服务解析view_rect/touch_points并提供getContentRect，模块尚未利用；这是进一步定位原文/附近截图的依据，不代表邻段列表已存在，也不证明所有能力都已被排除。后续追踪ClientCatcherResult/内容捕获端并实现主路线，详见PORTAL_515_AUDIT和ADJACENT_TEXT_PLAN。

### 1.7.103：默认引擎的附近行建议已接线

在主动Root截图追加流程，ML Kit保留识别行的boundingBox用于本次筛选：同列、与原区域不重叠、上/下最近行各一条。选区页新增用户主动点击的“查找原文附近文字”，使用同一张截图，不持续截图；预选后仍需核对确认。缺失原区域或选择AICR时不显示此入口，仍可手动框选。没有候选保持零选择。

这一步不是完整自动邻段：不证明原区域在当前画面仍准确，不扩展完整段落，上方候选确认后仍按当前追加流程放到原文末尾，自动前插尚缺。真机未验收。

AICR只读审计：work/reverse/Location.java为poly float[]、box float[]；OCRLine.java为location、line_text、line_id、Character[]、line_confidence；OCRParagraph为lines后paragraph_text；OCRResult为paragraphs、total_text、entities。VisionPlugIn.scaleOCRResult将所有坐标乘mSampleSize，setImage(Bitmap)为1，但无法证明box是LTRB还是XYWH或poly点序。当前测试数组和英文探测只有协议/文本证据，不是几何证据。后续可保留原始结构，先用已知位置合成中文真机验证，不能猜Rect或强制切换引擎。

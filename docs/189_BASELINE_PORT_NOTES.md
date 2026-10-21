# 从 189 基线继续开发：可移植方案清单

> 历史交接材料，包含未实测建议和当时的 HEAD/版本信息；当前源码与设备状态以 `CURRENT_STATUS.md` 为准。206 仅核对并采纳了局部截图、raw 回退、日志降噪与 Wi‑Fi 扫码展示等独立改动，未整体合并 190–205。

写给接手的人：本文只做事实与方案梳理，不含未完成的代码。
所有"实测"均指小米 15 Pro / HyperOS 4.0.0.17 / Android 17 真机 adb 取证，
未实测的部分已显式标注。

## 1. 基线坐标

| 项 | 值 |
|---|---|
| 交接基线 | `versionName 1.7.189` / `versionCode 213` |
| 提交 | `5970268` — "Route image presses into draggable sharing" |
| 分支 | `hyperos4-adaptation` |
| 当前 HEAD（已停手） | `2068195` — 229 / 1.7.205 |
| 189..HEAD 提交数 | 17 |

回退到基线：

```bash
git checkout -b from-189 5970268
```

仓库里另有两个可能有用的索引：`docs/TASK_BATCHES.md`（按批次记账，含 189 那一节的原始设计说明）、
`docs/CURRENT_STATUS.md`（435 行，设备实测流水）。

## 2. 最先要理解的一条语义（这是本轮踩坑的根源）

引擎 `RootNodeCaptureMain` 对按压点的判定只有三种 `kind`：

- `text` — 无障碍树里取到了文本节点，直接分词，不需要截图。
- `image` — 取到了图片节点，`pressOcrRegion = false`。
- **`ocr_region`** — **兜底路径**：`selectContent()` 返回 null（按压点周围既没有文本节点
  也没有图片节点）时，走 `safeEmptyOcrRegion()` 按按压点扩出一个矩形区域，
  `pressOcrRegion = true`。

**`ocr_region` 是"文字"，不是"图片"。** 微信自己绘制的聊天文字在无障碍树里不可读，
长按它必然落到这里；它是微信分词的**唯一入口**。
（`RootNodeCaptureMain` 有两处返回 `kind=ocr_region`：一处在 `WINDOW_MANAGER_CAPTURE`
路径，一处在主判定末尾的兜底。）

189 的分发逻辑，也是正确的语义：

```java
private void openImage(long request, CapturedContent content) {
    if (content.pressOcrRegion && "com.tencent.mm".equals(content.sourcePackage)) {
        openWeChatOcrDirect(request, content);   // OCR → 分词浮窗
        return;
    }
    controller.show(content, downX, downY, settings);  // 真图片 → 拖拽预览
}
```

`docs/TASK_BATCHES.md` 里 189 那一节的原文也印证：
"微信 `ocr_region` 继续直达 AICR；其他图片可从四边菜单选择识图、扫码、复制、保存、中转站和外部目标。"

## 3. 值得移植的方案（按价值排序）

### A1. 图片截图快路径：用引擎内截图替代 `su -c screencap`（205，`2068195`）

**解决的问题**：非微信图片按压的截图太慢，payload 在手指抬起后才到，拖拽菜单来不及创建。

**实测数据（这是选方案的依据）**：

| 路径 | 实测耗时 |
|---|---|
| `su -c /system/bin/screencap -p`（整屏 PNG，传送门进程内） | **0.97s**（其中 0.81s 是 PNG 编码 CPU） |
| `su -c /system/bin/screencap`（raw，无 `-p`） | **0.30s**（18,432,016 字节） |
| Root helper 引擎内 `UiAutomation.takeScreenshot()` | **39~62ms**，verify 另计 ~47~55ms |

也就是说：189 原本的链路确实在 300ms 量级，慢的是后加的 `su screencap -p`。
最优解不是优化 screencap，而是**根本不出进程**——helper 引擎里已经有 `UiAutomation`，
直接在里面截图并按 bounds 裁剪，只回传裁剪后的 JPEG。

**改动点**：

1. `app/src/main/aidl/com/leaf/hyperdragshare/codex/IRootNodeLease.aidl`
   新增 `Bundle captureImage(String sourcePackage, int windowId, in Bundle requestedBounds);`
2. `RootNodeCaptureMain.java` — 新增 `operation == "capture_image"` 分发与
   `captureImageRegion(request, automation)`：`takeScreenshot()` → `clearCache()` +
   `getRootInActiveWindow()` 校验 `windowId` 与 package → 裁剪 → JPEG 95 → Base64 回传。
   失败时返回 `{same_window:false, reason}`（reason 文案已覆盖"按压窗口已变化""图片区域已移出屏幕"等）。
3. `RootNodeCaptureClient.java` — 新增 `ImageRegionCapture` 接口与 `Session.captureImage()`；
   `contentFromNode` 增加第 4 个入参 lambda，图片分支优先用它，拿不到再回退整屏 `RootScreenshotter`。
4. `SharedRootNodeCapture.java` — `Lease.captureImage()`（注意 `enforceOwner()`、io 锁、
   `Binder.clearCallingIdentity()`），并把 `captureResult` 的 lambda 传进去；
   `IllegalStateException`（busy）时返回 null 让调用方回退，不要抛给用户。

**移植时必须注意**：`app/src/test/.../RootLeaseFailureTest.java` 里有两处匿名
`IRootNodeLease.Stub()`，新增 AIDL 方法后两边都要补 `captureImage`，否则单测编译失败
（205 就是这么炸的）。

**状态**：代码完成，`testDebugUnitTest` / `lintDebug` / `assembleRelease` 全绿，已装到 229。
**但图片分支的提速没有真机实测**——229 上微信长按走的是 `ocr_region`，没走到图片分支；
X/Twitter 长按图片也还没在这版上验过。移植后请单独验一次。

### A2. `RootScreenshotter` 读 raw 流（204，`21bdd72`）

**解决的问题**：A1 不可用时（helper busy / 窗口校验失败）的兜底仍然要 0.97s。

**做法**：优先执行不带 `-p` 的 `screencap`，自己解析 16 字节头
（width / height / format / depth，小端），`format == 1` 即 `HAL_PIXEL_FORMAT_RGBA_8888`，
然后 `Bitmap.copyPixelsFromBuffer()`；解析失败才回退 `-p`。
970ms → 300ms，改动集中在单个文件，风险低，建议无条件带上。

### A3. 日志卫生：MOVE 事件不再打日志（203，`ddb813c`）

**解决的问题**：MOVE 曾每 3ms 打一条，把 logcat main 缓冲冲到只剩约 1 分钟，
出问题时根本取证不到前因。**这是本轮所有定位困难的前提原因。**
只记状态迁移（DOWN / 确认 / 抬手 / 结果），不记移动。改动极小，强烈建议先做。

### A4. 丢弃原因可观测：`DropReason`（204）

**解决的问题**：payload 被 `request != generation` 静默 `recycle()`，现象是"按了没反应"，日志里什么都没有。

**做法**：在 `PressCaptureSource` 加 `DropReason` 枚举
（`SUPERSEDED` / `STOPPED` / `READER_DOWN` / `SHARING_OFF` / `PRESS_CLOSED` / `LOCKED` / `UNKNOWN`），
在 `lookup()` 的 `main.post` 与 `display()` 两处把静默 recycle 换成
`press payload dropped kind=... reason=... elapsedFromDownMs=...`。
诊断必备，成本极低。

### B1. 微信双弹窗去重（203）

宿主传送门会自己弹卡片，与我们的面板叠成两个。做法：

- `PortalHooks.suppressOriginalFloatWindows` 钩
  `com.miui.contentextension.text.floatview.BaseFloatView.addToWindow`；
- `DragShareController.isPressGestureHandling()` 判定当前是否处于我们的按压处理中，
  `shouldSuppressPortalHostFloatWindow()` 据此抑制宿主卡片；
- `hookShareStart` 钩宿主 `startPickTextTask` / `startPickImageTask`；
- 每次 `addToWindow` 记 `host float window addToWindow suppressed=... view=<类名>`，
  每次取词触发记 `portal pick trigger method=... handling=...`。

**状态**：**未真机验证**。229 的日志里没有捕获到 `portal pick trigger` 与 `addToWindow` 命中记录，
无法确认钩子是否真的命中。移植后第一件事就是抓这两条日志确认命中，别假定它生效。

### B2. 按压流水线窗口（204）

`DragShareController` 加 `pressPipelineUntilMillis`：从按压确认到 handoff 之间
（`PRESS_PIPELINE_WINDOW_MS = 2500`）持续宣称"我在处理按压"，
防止 Taplus / 宿主在截图期间插进第二张卡。`PressCaptureSource.firePress()` 里
`notePressPipeline()`，`cancelGesture()` 里 `endPressPipeline()`。
**未验证**，与 B1 配套，一起验。

### B3. Portal 重启保护 + 自身浮窗不参与取词（8c8c9f3）

模块重启不再把 Portal 带崩；`OverlayPressGuard` / `OverlayWindowPolicy` 让按压落在
我们自己的浮窗上时不走取词链路。**未验证**。

### C. 功能类（与按压主链路无关，按需取）

| 提交 | 内容 | 备注 |
|---|---|---|
| `4143e09` `3bb3007` `626d7ed` | 用系统 AICR 条码插件解码二维码，支持 WiFi 载荷 | 替代 ZXing |
| `a7ab622` | 删除内置分词器与 ZXing | −352,387 行 |
| `6430a2f` `f296890` `c57f663` | 面板改由透明 bridge Activity（`ImageOcrActivity`）承载 | **风险较高，见第 4 节** |
| `8349f10` | `gradle.properties` 加速、分词面板收窄、动作栏拆分 | UI 口味，非必需 |

## 4. 别做的事（本轮已踩）

1. **不要删 `pressOcrRegion && com.tencent.mm → openWeChatOcrDirect` 这个分支。**
   204 删了它，直接后果是微信长按文字被当成图片送进拖拽预览菜单，
   **分词功能整体失效**（229 上就是这个现象）。用户反馈的原话是"连分词都用不了了"。

   ⚠️ 注意一个易混淆点：用户曾抱怨"图片触发选词"。
   那是因为微信的**真图片**在无障碍树未暴露 image 节点时也会落到 `ocr_region` 兜底，
   于是被送进 OCR 选词。这是 189 就有的行为，不是后加的。
   如果要改这个，正确做法是改**引擎对图片节点的识别**（让真图片稳定返回 `kind=image`），
   而不是删掉 `ocr_region` 的分词路由——后者会让分词一起没掉。

2. **不要加截图预取。** 204 加了 `RootScreenshotter.prefetch()` / `take(maxAge)` /
   `dropCache()` / `setScreenshotProvider()`，205 已全部撤销——A1 快路径下它纯属多余机制，
   只增加状态与竞态。

3. **不要把 `ocr_region` 当成图片内容处置。** 见第 2 节。

4. **bridge Activity 托管面板（`6430a2f` / `f296890` / `c57f663`）风险未收盘。**
   改动的动机是：display-context overlay 在别的应用占屏时会被窗口策略隐藏，
   所以改用我们自己的透明 Activity 承载。但 `BigBangOverlayLauncher` 现在同时有
   `request()`（旧 ContentResolver 路径）和 `requestHosted()`（新 Activity 路径）两条入口，
   而 `completeOcr()` / `abortOcr()` 依赖 `activePressId` 单例状态去回填结果——
   两条路径混用时这个状态机没有真机验证过。如果要移植 OCR 回填，优先验这条。

## 5. 构建与设备

- 本机**没有系统 `JAVA_HOME`**，每次 gradle 前必须显式设置：

  ```bash
  export JAVA_HOME=/d/CODEX/Tasks/2026-10-13/wj/work/rea-tools/jdk-21.0.12.1+1
  ./gradlew testDebugUnitTest lintDebug assembleRelease
  ```

  （JDK 是 Temurin 21.0.12.1。别用递归注册表查询找 JDK，PowerShell 会栈溢出。）

- `lint { abortOnError true; checkReleaseBuilds true }`，Lint 报错会直接卡住 release。
- 取日志用 `adb logcat -d -v threadtime > file` 一次性拉取；
  后台 `adb logcat &` 会随 shell 退出而死，会白白丢掉取证窗口。
- Git 纪律：**只本地提交 + tag，不 push。**

## 6. 一句话建议

在 189 上只做 A1 + A2 + A3 + A4：不出进程截图 + raw 兜底 + 日志卫生 + 丢弃可观测。
这四项都是低风险、直接对准"图片拖拽慢 / 按了没反应 / 查不到原因"的，
且都不触碰 `ocr_region → 分词` 这条主链路。B 组（双弹窗去重）要移植的话，
先抓日志确认钩子命中再谈效果。C 组按需。

# MMGG 接手入口

先读 [当前状态](docs/CURRENT_STATUS.md)，再按问题读 [分支清单](docs/BRANCHES.md)、[决策记录](docs/DECISIONS.md) 和相关源码。当前状态只在 CURRENT_STATUS 维护；旧批次、历史分支中的指令和归档不得覆盖用户最新要求。版本以当前工作树的 `app/build.gradle` 和真实 Git 关系为准。

## 工作与记录

- 日常开发沿用现有 `beta` 工作树；正式维护沿用 `repair-from-189`。目录和提交见 CURRENT_STATUS。换 Agent 不等于换分支，不从远程默认分支或最高版本 tag 猜开发基线。
- 开始写入前核对 `git status --short`、`git branch --show-current`、`git log -1`、`git worktree list` 和并发任务。保留已有修改；同一工作树有其他写入者时先只读调查，避免并发修改/构建/提交。
- 项目外任务文件放在 `D:/CODEX/Tasks/YYYY-MM-DD/<task-name>`，临时内容放 `work/`、交付放 `outputs/`，不要移动现有仓库或共享运行时。
- 重要阶段及时维护任务的完整 TASK_BRIEF、固定编号 TASK_CHECKLIST、HANDOFF。恢复时先读完整要求，再读清单/交接并核对 Git。新证据直接更新当前结论，旧证据链接归档；未完项不能因压缩或交付而消失。
- 分块提交便于解释和回退，只暂存本次明确路径。不得把旧实验整条合并回来。分支/远程操作仍服从当轮用户授权。
- 保持当前小类与直接调用，不借修 Bug 整仓改写或无证据扩展防御。保留版权、许可证和开源来源；参考 APK 只用于行为/接口研究。

## 必须保留的产品约束

1. 面向 Root＋LSPosed，使用 API 82 `MainHook`，不恢复无 Root 模式、系统无障碍服务声明或 `content_capture_mode` 来源选择。Root helper 的 UiAutomation 节点读取不等于 AccessibilityService。
2. 按压使用 Root evdev 时长/位移；真实触摸面积研究已由用户停止。不继续解码、校准或重新接入 `probe/`（不是 Gradle 模块）。默认确认时长600ms，可调250–3000ms；保留合法旧配置与旧键。
3. Root 流进入活动拖拽后是唯一权威输入。真实 UP/CANCEL决定结束；Portal `cancelTask`、257/258、重复 pick不能提前结束活动会话。cancelTask/257须在真实结束后回放以复位宿主，258移动取消继续抑制。不得混入旧 MIUI 监听或凭私有控制码伪造松手。
4. 拖拽预览和边缘菜单保持 `FLAG_NOT_TOUCHABLE`、被动跟手；交互分词/中转站窗口按各自策略处理，不能把上述规则扩成所有窗口永久穿透。所有退出/取消/服务销毁释放窗口、任务、Bitmap、InputMonitor；旧回调不能影响新会话。
5. Hook精确限制包、进程、类和方法；当前作用域以 `MainHook.java` 与 `res/values/arrays.xml`核对（包括可选MiTrustService）。安全中心只适配已核对版本40001365/SDK37的`:ui`。不加入/强停 `com.miui.contentcatcher`，不批量杀系统进程，不全局关闭phantom/内存回收。普通运行时不导入Xposed；仅现有Hook入口/适配层使用。
6. 传送门5.1.5私有接口不可凭猜测改名/控制码；跨版本先核对目标实现。启动用其原生 `TextContentExtensionService` 前台服务，不恢复Provider-only缓存进程保活。模块注入以当前构建＋存活Binder/PID证据判断，不把进程存在、普通get_settings、历史报告或启动命令零退出码当功能可用。
7. Root、模块注入、服务、输入、取词、悬浮窗与OCR依赖分别判断；证据不足/超时是未知，不能写成未授权。保留代次隔离、有限等待和按功能说明影响的诊断。
8. 配置由模块UID私有SharedPreferences保存，Provider受限读取；不改world-readable、不清用户数据/重置配置掩盖问题。边缘距离按dp，速度按dp/s；数据库schema2不能直接搭配schema1旧包降级，先保留对应数据备份。
9. 分享图片保留 `data`、`EXTRA_STREAM`、`ClipData`、MIME与read grant组合。ShareImageProvider的UUID能力URI＋显式grant及允许系统代理二次转发的openFile语义不能随意收紧；stage/grant/revoke仍限制模块/Portal UID。勿把公开图库副本默认开启。
10. 用户目标是内容直接拖放至当前聊天，接收方决定处理；不恢复StashTextDropWindow填框透明层或ShareImgUI联系人选择器替代。drop accepted/startActivity成功不等于接收或发送成功。取消/拒绝保留内容和选区；图文混选未支持时明确拒绝。
11. 不把截图/OCR区域冒充微信原媒体。当前只信任验证过的Portal媒体来源；无节点时局部OCR仍核验WMS包名、可见范围、安全遮挡、锁屏和黑名单。节点只在明确请求、按压分类/确认或主动点取时读取，不空闲遍历或截图。
12. UiAutomation只有一个注册位；Root取词租约存在时不运行 `uiautomator dump`、不抢断其他会话。点取和普通按压分别验收。剪贴板文字/URI仅经stdin，不改AppOps掩盖失败，不清理用户暂存或照片。
13. 采用现有Miuix主题和组件，保留功能/配置及返回、编辑、取消选择语义。近手菜单依据左右拖向选边；固定边及跟随边模式保留。动作栏当前实现不代表完整上游交互已验收，详见CURRENT_STATUS。
14. 原生复制提醒/复制直达沿用系统入口；不恢复旧自绘全局气泡或全局剪贴板Hook。不绕过小米翻译CTA，不把Root UID0当system UID1000。现代玻璃有支持限制与回退，不能承诺所有ROM。

## 必要检查

构建运行JDK21，输出Java/Kotlin17；SDK37/build-tools36。AGP/Gradle版本以项目文件为准。不要读取/输出签名口令。

```powershell
git status --short
git branch --show-current
git log -1 --oneline
git worktree list
git diff --check
```

行为变更按受影响入口做逻辑/替身验证，关键阶段必要检查，主要修改后集中执行项目回归：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
# 交付Beta时集中执行（含R8）
.\gradlew.bat assembleBeta
```

纯文档修改检查事实、链接和diff即可，不增版、不构建。新可安装行为递增versionCode/版本；APK路径和发布门槛见 [RELEASE_CHANNELS](docs/RELEASE_CHANNELS.md)。Beta签名在本地未配置release密钥时会回退debug，须核对实际证书，不能概称与正式版同签。密钥、local.properties、私人配置、用户原始日志/截图/视频和参考APK不提交。

实机操作以当轮用户授权为准；历史持续授权不能移植到其他聊天。用户正在操作时不抢占，不发送私人消息、不清数据；关闭测试窗先确认仍存在，避免Back落到原应用。仅应用Hook改变时优先应用热重载；android/system_server代码更新需软或整机重启，不把安装/热重载等同所有宿主已加载。

## 证据入口

- 当前能力、待办与验证范围：[CURRENT_STATUS](docs/CURRENT_STATUS.md)。
- 分支拓扑、tag和后续Git操作：[BRANCHES](docs/BRANCHES.md)。
- 已替代方案、不能恢复的行为：[DECISIONS](docs/DECISIONS.md)。
- 配置修改入口、字段增加步骤与兼容检查：[CONFIGURATION](docs/CONFIGURATION.md)。
- 拖拽动作执行、线程与资源边界：[DRAG_ACTIONS](docs/DRAG_ACTIONS.md)。
- 窗口退出、异步回调与资源归属：[EXIT_LIFECYCLE](docs/EXIT_LIFECYCLE.md)。
- 实现专题：[IMPLEMENTATION](docs/IMPLEMENTATION.md)、[STASH](docs/STASH.md)、[FANFREEFORM_INTEGRATION](docs/FANFREEFORM_INTEGRATION.md)、[DEVICE_VALIDATION](docs/DEVICE_VALIDATION.md)。这些包含历史阶段，先核对日期/提交，不把旧“下一步”当当前任务。
- 整理前完整AGENTS与旧进展：[历史归档索引](docs/archive/2026-10-24/README.md)。

# 分支、工作树与独有改动清单

## 2026-10-25 Beta24 推送与远程清理（当前状态）

核对日期：2026-10-25。GitHub 仓库 `chenran0408/HyperDragShare` 为公开仓库；默认分支已从`hyperos4-adaptation`改为`repair-from-189`。origin 仅保留两条长期远程分支；上文旧调查表和命令是2026-10-24历史快照，不是当前远程状态或待执行步骤。

| origin ref | HEAD | 状态 |
|---|---|---|
| `beta` | 远端实时HEAD请用 `git ls-remote --heads origin beta` 查询 | Beta24源码提交为 `bdd6bdf5901b6dafc24f667e287069f90f39ab56`；`delivered/mmgg-1.7.219-beta.24` 固定该源码。文档提交可继续推进分支HEAD |
| `repair-from-189` | `e25c66725cda51f23bdb8d95de036f7811eba017` | 稳定维护线及 GitHub 默认分支，未改动 |

- Beta24 APK `D:/CODEX/Tasks/2026-10-25/mmgg-beta-upload-cleanup/outputs/beta24/MMGG-1.7.219-beta.24.apk`，versionCode 266，4,947,493 bytes，SHA256 `B932D60E7CB3C2329AFD911BF099DC8418381C0FAE732412872BAFE67BC4A5B6`。源码提交只将`app/build.gradle`从beta23/265增至beta24/266。实际`assembleBeta`成功（R8、资源收缩、lintVitalBeta；1m14s），APK v2签名通过，证书为Android Debug（SHA256 `b86a4a1d5ca870ea986585714fd2e9d349a6679fc8d7e1e682e1594d7c2c89c0`），不是正式签名证明。单测、完整Lint、模拟器/实机验收、设备安装未运行。
- Beta24 push触发GitHub Actions Android checks run `40112982633`（SHA `bdd6bdf`），最终 `success`；这是云端检查结果，不包含设备验收。
- 已逐条删除远程`hyperos4-adaptation`（原HEAD `3520884f8296f4b2215ad79ca822d3e0f1864ffd`）、`kotlin-libxposed-102`（`a6ee01b615ca5bec32aa3a6914b43ccb92734b89`）、`main`（`1a042132194643d470fa58163abf7504c8963f88`）。删除前重新核对了各ref、默认分支和归档tag；当时open PR为0。两条维护线都包含共同基线`3520884`；历史main/Kotlin实验分别由远程tag `archive/origin-main-2026-10-24`、`archive/kotlin-libxposed-102-2026-10-24`保留。Kotlin archive tag peel仍为`a6ee01b`。未删除或移动任何tag、upstream分支、稳定/开发本地分支或工作树。
- 已`fetch --prune origin`并更新`origin/HEAD -> origin/repair-from-189`。远程复核恰有beta和repair-from-189两heads，default/HEAD为repair-from-189。远程tag ref共18个；本地共48个。保留所有历史tag，不批量推送其他本地tag。
- 当次分支检查显示没有受保护分支或仓库rulesets；移除前无open PR。origin公开、非归档。upstream仍指向`Leaf-lsgtky/HyperDragShare`，没有对upstream执行写入。
- 清理文件检查仍未发现可确认的无用tracked文件。历史文档和诊断源码有产品边界、复现材料及明确链接；`.gradle/`、`.kotlin/`、`build/`、`local.properties`、`.learnings/`是ignored本地项，不会随Git push上传。本阶段没有删除tracked文件或清理忽略缓存。
- `docs/CURRENT_STATUS.md`是当前产品/交付状态；本次 Beta24 APK/build 证据及 task checklist/handoff 在 `D:/CODEX/Tasks/2026-10-25/mmgg-beta-upload-cleanup/outputs/beta24` 与其任务目录。若 Actions run 后续完成，更新该run结论，不以本地构建代替云端结果。

## 2026-10-24 落实记录（历史快照；当前执行状态见上节）

本轮在`f65de2e`继续落实上一轮结论；以下旧调查表仍是整理前快照，不再作为实时HEAD。

- 已建立本地`backup/beta-before-settings-2026-10-24`→f65de2e、`backup/stable-before-settings-2026-10-24`→b40abaf；`delivered/mmgg-1.7.219-beta.19`→d6d1f3a。
- 历史路线用`archive/kotlin-libxposed-102-2026-10-24`→a6ee01b及`archive/origin-main-2026-10-24`→1a04213标记，只保留研究证据，不整线合入；原分支/tag/worktree保留。
- 本地完整bundle位于任务目录`D:/CODEX/Tasks/2026-10-24/mmgg-settings-foundation/outputs/backups/MMGG-before-settings-20261024.bundle`；bundle verify、隔离镜像读取上述引用和完整fsck均通过。SHA及大小见同目录MANIFEST.json，不含工作树未提交内容。
- 稳定独有b40abaf中的ACKNOWLEDGEMENTS原文保留到Beta，USER_GUIDE按当前入口/选区/侧滑/签名边界适配；README和MMGG补对应链接并移除已失效承诺。没有把稳定README或旧交互方案覆盖回来。
- origin账号与仓库owner均核对为chenran0408（repo ID1427928487），只向origin显式备份；远程备份的最终提交/CI结果统一见CURRENT_STATUS。默认分支继续保持hyperos4-adaptation，本轮不作选择或改动。
- MediaStore公开副本/API102/指针监听未决候选保持原状，不为“独有”而合并。下一步配置更新职责另批推进。

以下为上轮2026-10-24只读调查快照，整理前beta=`d6d1f3a`。下表计数固定于调查时，后续文档/CI/职责拆分提交均不回写历史计数。实时工作入口与执行结果见[CURRENT_STATUS](CURRENT_STATUS.md)，不能将下文旧“未执行”视为本轮状态。

## 仓库与并发状态

- origin：`https://github.com/chenran0408/HyperDragShare.git`；upstream：`https://github.com/Leaf-lsgtky/HyperDragShare.git`。
- 两本地分支、两工作树；没有其他本地历史分支/游离工作树/锁定工作树。两个工作树均无已暂存/未暂存/非忽略未跟踪文件；本机配置/缓存与学习记录被忽略，未清理、未读签名秘密。
- 两本地分支未配置upstream。`git ls-remote --heads`、`--symref ... HEAD`、`--tags`直接核对服务端，与现有remote-tracking refs相符；没有fetch/prune或任何ref改写。
- 同一团队仅主Agent活动；相关MMGG聊天空闲或未加载，写回前还要核对。未观察到并发写入不代表未来可以共享写树。

## 全部分支逐项

计数“Beta/该线”=`rev-list --left-right --count beta...REF`；“稳定/该线”同理。共同基线同时列出，避免把独有提交数当必须合并数。

| ref | HEAD | 与Beta共同基线；Beta/该线 | 与稳定共同基线；稳定/该线 | 分类、位置、独有内容与建议 |
|---|---|---|---|---|
| 本地`beta` | `d6d1f3a0c88591126879a553960c9133d2dc50a7` | 自身；0/0 | `3520884`；1/58 | 当前唯一开发线；`D:/CODEX/Tasks/2026-10-22/new-chat-3/work/HyperDragShare`占用。58提交包括beta渠道、诊断、生命周期、手势/面板/图源等修复。继续维护并备份远程；不是稳定验收完成 |
| 本地`repair-from-189` | `b40abafd1a1143b0ebb4869599fc6979ce7ae8de` | `3520884`；58/1 | 自身；0/0 | 稳定维护；`D:/CODEX/Tasks/2026-10-20/new-chat-2/work/HyperDragShare`占用。独有1提交为使用指南/致谢；继续保留，不用Beta覆盖它。指南部分行为已过时，不能原样复制 |
| `origin/hyperos4-adaptation` | `3520884f8296f4b2215ad79ca822d3e0f1864ffd` | 自身；58/0 | 自身；1/0 | 远程默认/已纳入两本地线的历史稳定节点，无工作树占用、无独有源码。短期保持不动；远程策略确认后用两维护线取代其职责，不在此继续新功能 |
| `origin/kotlin-libxposed-102` | `a6ee01b615ca5bec32aa3a6914b43ccb92734b89` | `1a04213`；243/23 | `1a04213`；186/23 | 历史Kotlin/API102实验，无工作树、本地同名分支或未保存树。确有独有功能，但分叉早；建议tag归档、按下表保留参考，绝不整线合并 |
| `origin/main` | `1a042132194643d470fa58163abf7504c8963f88` | 自身；243/0 | 自身；186/0 | 较早祖先，已被两维护线包含；无工作树/独有提交。建议作为历史引用，确认后可用旧tag保存而收尾远程头 |
| `upstream/main` | 同`1a04213` | 自身；243/0 | 自身；186/0 | 原作者参考线；无本地工作树，不是本fork交付线。保留只读来源，不向upstream推送，也不把它的HEAD当MMGG最新 |
| `upstream/kotlin-libxposed-102`（仅服务端头，本地无tracking ref） | 同`a6ee01b` | `1a04213`；243/23 | `1a04213`；186/23 | 在线ls-remote发现；与origin实验头完全相同，独有改动同下表。不创建额外工作线，不管理原作者远程分支 |

`origin/HEAD`是`origin/hyperos4-adaptation`符号别名，`upstream/HEAD`是`upstream/main`别名，不是额外开发分支。本地没有`origin/beta`或`origin/repair-from-189`，不能配置成旧默认分支的跟踪关系冒充备份。

```text
1a04213 ──(185提交)── 3520884 ── b40abaf  repair-from-189
    │                      └──(58提交)── d6d1f3a  beta产品源码
    └──(23提交)── a6ee01b  Kotlin/API102历史路线

正式发布tag v1.7.218剥离到c03e880，是3520884之前2提交的祖先。
```

## 独有提交实际取舍

检查方法：merge-base、left/right计数、`git cherry beta origin/kotlin-libxposed-102`、逐组提交diff与当前源码。23项cherry均为`+`，仅表示patch-id未直接包含；语言/接口不同，不能据此断言没有等效实现。历史分支的AGENTS/版本号不是当前指令。

### 稳定独有1项

`b40abaf`仅修改`MMGG.md`、`README.md`并新增`docs/USER_GUIDE.md`、`docs/ACKNOWLEDGEMENTS.md`，没有功能源码。值得保留致谢、来源和使用说明；当前Beta缺这两新文件。后续单独适配文档：保留版权/致谢，更新灰条、两排工具、侧滑让给系统等已变行为，再按文档单元提交。**本轮不合并该提交或覆盖Beta README**（后者已有Beta渠道说明）。

### Kotlin/API102独有23项（全部覆盖）

“替代”指实现目标已有当前路径，不表示字节等效；“候选”不表示用户已授权产品变更。

| 提交 | 实际改动 | 分类/后续建议 |
|---|---|---|
| `02de2b2` | ActivationMonitor进程级StateFlow、偏好监听、pid判断和阶段等待 | 当前`SettingsScreen`合流请求/代次隔离＋`ModuleActivation`存活Binder已实现相近诊断目标；生命周期所有权不同，保留参考，不移植旧持久报告判定 |
| `0c89029` | 改API102拦截器、ORIGIN回放、META-INF描述符、DragShareModule | 整体技术迁移，当前仍API82/MainHook/assets；没有用户批准采用新API的证据。归档候选，不整并，不称新主线 |
| `1ea1f30` | 用libxposed service查询启用/作用域/运行目标 | API102专有能力；当前活Binder＋构建/PID握手替代就绪取证。框架侧作用域精确诊断可保留设计参考，未接入 |
| `d079836` | 叶子helper由Java转Kotlin | 语言改写，无独立产品需求；当前对应helper已多次演进，不搬旧文件 |
| `12fe339` | 系统/IO层转Kotlin | 同上，当前Root/图片/生命周期不同；归档 |
| `8feb935` | 输入与无障碍层转Kotlin | 含当前已停止的无障碍服务路线；不恢复，仅保留历史 |
| `6a3764d` | View/Drawable转Kotlin | 当前样式/菜单几何已变化；无需要整并的独有产品行为 |
| `bb6a026` | 设置模型转Kotlin | 当前模型包含更多键/迁移；不以旧构造函数替换 |
| `f2a53f2` | DragShareAccessibilityService转Kotlin | 已被Root-only产品决定替代，不能重新声明服务 |
| `f1e3c35` | DragShareController转Kotlin | 旧控制器缺后续会话/预览/菜单修复；不拿语言改写覆盖当前功能 |
| `ee0a58a` | 剩余Java测试转Kotlin | 历史测试，不代表覆盖当前572用例；只按具体测试契约参考 |
| `5c38ae8` | 去机械移植留下的Java互操作注解 | 仅适用该实验树，当前混合语言不能照删 |
| `0447ff1` | Kotlin惯用写法整理 | 同上，无独有功能需求 |
| `8c0321b` | 升1.8.0、重写API102文档 | 版本/文档随实验；不作为更新顺序证据，归档 |
| `4f374a2` | 并发Root探测/框架binder等待，分阶段发布状态 | 当前不依赖libxposed service；有限等待/独立证据目标已有实现，并发优化须有实际瓶颈再评估 |
| `41a33f8` | 允许接入已在进行的触摸、rootPointerSeen后权威；区分Root已报告拒绝与未报 | 中途采用裸坐标与当前beta19必须物理DOWN的约束冲突，不直接恢复。拒绝/未知区分已在RootAccessOutcome实现 |
| `15d8b51` | 旧构建提前退出等待、各阶段耗时日志 | 当前构建匹配/有限等待已实现目标；可参考阶段计时，不抄API102状态分支 |
| `da68567` | Provider优先启动Portal，服务作为回退 | 已被本fork`16cc404`前台服务替代：曾观察Provider-only缓存进程被回收，不恢复 |
| `6728588` | 从模块降到Portal UID探su，report序列复验，框架结果优先于历史报告 | 当前Portal自身探Root、活Binder/PID/当前构建验证；跨UID su假设不是当前契约。不拿模块su结果冒充Portal功能可用 |
| `3903cc7` | 作用域缺Portal优先于已注入报告，跳过无效等待 | 原则有保留价值；当前API82无同一框架service来源。保留诊断用例思路，未经接口验证不写作用域确定结论 |
| `86cef21` | 依据HookedTarget.State而非loadedVersionCode，旧PID判旧代码 | API102限定；当前存活凭证＋编译版本判定是另一实现，不能简单替换。若未来迁移API再评估 |
| `8fd0f4a` | 移除逐MOVE坐标、按边界记日志和原始帧采样 | 当前`49ef314`等已做采样/轮转，但仍有坐标诊断，并非完全等效。隐私和降噪思路值得保留，后续独立审查，不本轮改日志源码 |
| `a6ee01b` | 用户可选MediaStore公开副本、分享重试、10分钟清理任务、OutcomeProbe | 当前私有能力URI/显式grant/24小时私有缓存清理，LocalImageSaver只是明确保存，不等同此功能。保留独有方案但未采纳；公开相册副本有可见性/清理代价，不能当N06现成修复或默认开启 |

Beta独有58提交均在当前开发线上；不是应反向整并稳定线的清单。值得继续保留的关键组：`efb6ce6`分层诊断、`4c06227/0435d12`生命周期、`f676b4f`底角、`83048f8`侧滑/窗外开关、`edbb9d9`图源、`585c162`旧避让恢复、`1bcabc6`词块稳定、`59fd022/5e5a4e3`预览输入归属。独有≠已验收，未完见CURRENT_STATUS。

## 分支策略与上轮操作提案（执行状态见顶部）

长期只维护现有`beta`（开发/候选验收）与`repair-from-189`（已发布稳定维护），不新增第三条长期线。普通独立修复可直接分块提交Beta；需要并行隔离或有风险实验时，从准确维护线建短期`fix/<问题>`、`feat/<目标>`、`experiment/<假设>`，完成审查/验证后合入一次并收尾；放弃时tag保留证据后停止，不因更换Agent新建分支。稳定紧急修复从稳定线出发，之后只移植必要差异回Beta；Beta晋升正式版必须先验收，不能今天整线合入。

以下命令仅为2026-10-24历史提案，已被上面的2026-10-25操作覆盖；不要重复执行。Beta24源和tag已推送、默认分支已迁移、三个历史远程头已删除。保留此处只为追溯当时决策顺序，当前远程只以本节列表为准。

1. 在Beta工作树核对现场并审查本次文档提交；列出本地/远程，确认没有同名tag：

   ```powershell
   git status --short
   git log -1 --oneline
   git ls-remote --heads origin
   git ls-remote --tags origin
   ```

2. 固定已交付源码和实验（先`git show-ref --verify refs/tags/<名称>`确认缺失；若已存在仅验证，禁止-f）。Beta19只标源码与已有本地交付，不自动创建GitHub Pre-release：

   ```powershell
   git tag -a delivered/mmgg-1.7.219-beta.19 d6d1f3a0c88591126879a553960c9133d2dc50a7 -m 'beta19 source; device acceptance pending'
   git tag -a archive/kotlin-libxposed-102-2026-10-24 a6ee01b615ca5bec32aa3a6914b43ccb92734b89 -m 'Historical API102 experiment; do not merge wholesale'
   git tag -a archive/origin-main-2026-10-24 1a042132194643d470fa58163abf7504c8963f88 -m 'Historical common baseline'
   ```

3. 明确备份两条本地维护线并建立正确upstream（显式refspec，不用`--all/--mirror/--tags`）：

   ```powershell
   git push -u origin beta:beta
   git push -u origin repair-from-189:repair-from-189
   git push origin refs/tags/delivered/mmgg-1.7.219-beta.19 refs/tags/archive/kotlin-libxposed-102-2026-10-24 refs/tags/archive/origin-main-2026-10-24
   git ls-remote --heads --tags origin
   ```

4. 独立CI变更：将push与PR基线覆盖beta、repair-from-189；Beta执行testDebugUnitTest/lintDebug/assembleBeta（R8）并保存报告/映射/版本/SHA，正式维护执行对应检查和发布门槛。不要借此步骤发布未经验收的正式版；签名策略见RELEASE_CHANNELS。
5. 默认分支建议设稳定线`repair-from-189`，开发任务/PR明确选Beta。需要用户选择默认面向稳定下载还是开发协作，确认后才执行`gh repo edit chenran0408/HyperDragShare --default-branch repair-from-189`，然后`git remote set-head origin -a`更新本地别名。
6. 默认/CI/文档入口迁移和tag远程核验完成后，确认历史远程头是否仍有PR/外部消费者，再另行批准收尾origin的`kotlin-libxposed-102`、`main`、`hyperos4-adaptation`。本轮不删除、不提供可误跑的批量删除脚本；upstream分支不属于本fork清理范围。
7. 稳定独有文档按前述逐项适配到Beta，未来发布准备再处理稳定与Beta的必要内容整合；不整并Kotlin实验，不删除仍承载维护的两工作树。

## 上轮tag逐项快照（38个；本轮新增见顶部）

以下由本次本地refs与在线ls-remote生成。注解tag以剥离提交判断代码；local-only不等于已远程备份，snapshot不等于已交付/安装。`v1.7.218`注解对象`bc3f29f`不能误当发布源码提交；`v1.9.3`只是实验路线的版本号。

| tag | ref对象/类型 | 剥离提交 | 在线存在位置 | 用途/建议 |
|---|---|---|---|---|
| `delivered/mmgg-1.7.219-beta.10` | `9356463e` / commit | `9356463e` | 仅本地 | 候选交付记录；保留 |
| `delivered/mmgg-1.7.219-beta.12` | `95ff3aa2` / commit | `95ff3aa2` | 仅本地 | 候选交付记录；保留 |
| `delivered/mmgg-1.7.219-beta.14` | `bfdad715` / commit | `bfdad715` | 仅本地 | 候选交付记录；保留 |
| `delivered/mmgg-1.7.219-beta.5` | `cb6cdbdd` / tag | `5a1cb461` | 仅本地 | 候选交付记录；保留 |
| `delivered/mmgg-1.7.219-beta.6` | `0435d126` / commit | `0435d126` | 仅本地 | 候选交付记录；保留 |
| `delivered/mmgg-1.7.219-beta.7` | `6a3b04ae` / tag | `f526ba51` | 仅本地 | 候选交付记录；保留 |
| `snapshot-2026-10-22-194` | `dadd55f4` / tag | `c57f663a` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-195` | `358341a4` / tag | `3bb30076` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-195b` | `e36d2568` / tag | `8c54bca9` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-197` | `95f0a89e` / tag | `4143e09b` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-202` | `8c8c9f38` / commit | `8c8c9f38` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-203` | `ddb813c6` / commit | `ddb813c6` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-204` | `21bdd728` / commit | `21bdd728` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-205` | `2068195c` / commit | `2068195c` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-206` | `26ae0835` / commit | `26ae0835` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-207` | `85ad626a` / commit | `85ad626a` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-208` | `ff9469f0` / commit | `ff9469f0` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-209` | `dd55ae1e` / commit | `dd55ae1e` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-210` | `1c036c15` / commit | `1c036c15` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-211` | `00025413` / commit | `00025413` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-212` | `16cc404f` / commit | `16cc404f` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-213` | `ed091fc3` / commit | `ed091fc3` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-214` | `089800d9` / commit | `089800d9` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-216` | `f306bd8b` / commit | `f306bd8b` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-217` | `0b142ede` / tag | `5985dd14` | 仅本地 | 历史快照，不等于交付；保留 |
| `snapshot-2026-10-22-218` | `1f63b6be` / tag | `5c0761f9` | 仅本地 | 历史快照，不等于交付；保留 |
| `v1.7.12` | `b467abcb` / tag | `43dd16d5` | origin, upstream | 历史版本/开发预览；保留 |
| `v1.7.147-dev` | `7427e6bf` / commit | `7427e6bf` | origin | 历史版本/开发预览；保留 |
| `v1.7.17` | `ae591895` / tag | `595a0900` | origin, upstream | 历史版本/开发预览；保留 |
| `v1.7.175` | `41321482` / tag | `ff664010` | origin | 历史版本/开发预览；保留 |
| `v1.7.176` | `908ddd20` / tag | `089bb956` | origin | 历史版本/开发预览；保留 |
| `v1.7.179` | `87fd4957` / commit | `87fd4957` | origin | 历史版本/开发预览；保留 |
| `v1.7.218` | `bc3f29fe` / tag | `c03e880c` | origin | 正式发布源码锚点；保留 |
| `v1.7.43` | `b7fa9539` / commit | `b7fa9539` | origin, upstream | 历史版本/开发预览；保留 |
| `v1.7.46` | `c1b78834` / tag | `37dab224` | origin, upstream | 历史版本/开发预览；保留 |
| `v1.7.47` | `f1867feb` / commit | `f1867feb` | origin, upstream | 历史版本/开发预览；保留 |
| `v1.7.48` | `1a042132` / commit | `1a042132` | origin, upstream | 历史版本/开发预览；保留 |
| `v1.9.3` | `a6ee01b6` / commit | `a6ee01b6` | origin, upstream | API102实验，不是当前正式版；保留 |

共38个本地tag：6个delivered、20个snapshot、12个v版本；origin在线12个、upstream在线7个，均包含在本地清单。没有Beta15–19交付tag，不能假称已远程备份。保留所有历史tag；本轮不移动/删除任何tag。

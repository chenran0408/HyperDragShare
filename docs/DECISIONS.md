# 有效约束与被替代方案

核对日期2026-10-24；代码基线d6d1f3a。`用户决定`、`代码事实`、`调查推测`分开列；本页不是新的产品需求。当前待办只在[CURRENT_STATUS](CURRENT_STATUS.md)维护，历史原文见[归档](archive/2026-10-24/README.md)及原任务TASK_BRIEF。

| ID | 类型与状态 | 停止/替代的方案及原因 | 现用方案、证据与约束 |
|---|---|---|---|
| D01 | 用户决定；已停止 | 真触摸面积研究、强制校准、把原始帧当面积证据 | Root evdev时长/位移；原AGENTS及旧CURRENT_STATUS“最新范围调整”；PressTriggerDetector/DragShareSettings。600ms默认，合法旧值保留。probe与历史面积文档仅研究证据，不接回正式模块 |
| D02 | 用户既有产品方向＋代码事实；已替代 | 无Root/AccessibilityService、传送门/无障碍双来源及伪装朗读服务 | Manifest无BIND_ACCESSIBILITY_SERVICE；API82 Hook＋Root helper/UiAutomation。用户给的微信文章是方案线索，不是授权恢复服务。不能将Root语义节点读取错误描述为已声明的服务 |
| D03 | 代码事实；已替代 | 两种输入同时决定结束、Portal257/258当Root UP | Root唯一权威及真实UP/CANCEL；PortalHooks延后回放cancel/257。`59fd022/5e5a4e3`强化显示匹配和DOWN归属，不允许用更短固定延时遮住输入丢失 |
| D04 | 代码事实；已替代 | Provider-only拉起Portal就认为正常 | `16cc404`启动TextContentExtensionService前台服务；历史观察缓存进程受umms_pressure回收。进程、注入、服务、Root、reader分别判断；不增加定时复活或全局保活 |
| D05 | 用户决定＋代码事实；撤回 | 48dp小图/启动器/交互图标一刀切过滤，旧Beta2方案 | 用户要求保留小图拖拽；`20b8e68`删除PortalCapturePolicy与对应尺寸/来源限制。当前精确交互控件避让不是该尺寸方案，不能“防残留”时恢复旧过滤 |
| D06 | 用户决定；已替代 | 中转站文字Root填输入框透明拦截层、微信ShareImgUI重选联系人 | 旧AGENTS168条、旧CURRENT_STATUS168条明确改原生拖放到当前聊天。文本合并一条，接收方决定；StashTextDropWindow不恢复。历史独立托盘收件成功不代表现嵌入式发送完成 |
| D07 | 用户决定＋代码事实；已替代 | 灰色侧条/残留把手、后台新增主动展开 | `63f49d2`收起彻底移窗，后台更新不自动展开；当前独立/原生侧栏路径按各自入口打开，保留数据/选区。旧指南的“点灰条”已不适用 |
| D08 | 代码事实；已替代 | jieba/JNI/字典预载、MLKit/ZXing双引擎计划 | `0002541`系统ICU分词＋AICR条码；app/build.gradle/TextSegmenter/AicrBarcodeClient无这些引擎依赖。旧OCR/扫码/分词计划不等于现用实现；不重新打包这些依赖 |
| D09 | 用户要求＋代码事实；进行中 | 将截图/OCR区域当微信原图 | `756d0d3/edbb9d9`明确来源、只接受验证Portal媒体；RootNodeCaptureMain.takeScreenshot仍是截图。真正按压取原媒体没有闭环，不可仅改标记。旧`85ad626`拦Portal及`0002541`交接为竞态候选，不是现场根因定论 |
| D10 | 用户要求；未完成，不能称废弃 | 指定Leaf项目分词完整样式/排布/体验复用 | 用户多次要求直接采用，不能以Miuix局部改栏替代验收。`fd80f87`资源/排布和`4dcd4d5/1bcabc6`冻结只是部分；Compose外壳＋BoomChipPage/SwipeSelectView仍在。上游已在对象库，不等于全部交互移植完成 |
| D11 | 用户要求更新＋代码事实；旧规则被替代 | 近手只按按压起点半屏选边、只留跟随四边；空侧滑停留开关 | `6ff3414`按左右拖向选边/抖动过滤，六位置保留；`83048f8`恢复FanFreeform侧滑DOWN/目标与窗外master AND方向键。旧“侧滑已交给系统所以删除”不是当前要求 |
| D12 | 代码事实；回退修复 | `db41204`把enabled过滤扩到原语音/倍速规则 | `585c162`恢复明确hold控件disabled态避让；2个用例修前失败，46定向修后过。滑条/拍摄规则仍要求对应启用语义；不以误触为由屏蔽正文/整包应用 |
| D13 | 代码事实；已替代 | 213自绘复制气泡、全局剪贴板Hook或测试Activity代替原生复制链 | `089800d`等使用系统原生提醒；AicrCopyHooks/原页分词保留。通知权限与copy_direct频道悬浮开关不同；不绕过小米翻译CTA，翻译静态接口未接入 |
| D14 | 代码事实；已替代 | 全局ServiceManager.waitForService Hook、扩大系统进程保护/强停contentcatcher | `5c0761f`精确MiTrust DexKit风险入口；RootProcessFrameworkHooks只裁自有Root helper相关数量；ProcessManager锁定只模块/Portal。范围失配保留原行为，不全局绕过/批量杀进程 |
| D15 | 用户方向＋调查结论；历史线不作为开发基线 | Kotlin/API102整体改造自动成为“更新版本” | 当前两维护线已约定，API82代码事实不变；实验23独有项的取舍见BRANCHES。不认定全部独有功能无价值；API102或MediaStore方案若要采用，需后续独立目标/兼容验证 |
| D16 | 用户决定；暂停 | AICR缺失组件用户反馈继续排查 | 用户明确“这个反馈先不管了”；保留既有组件名/UID/用户/可见性调查，不能写已解决，也不以暂停推定服务不必要 |
| D17 | 调查推测；未采用 | 注册系统pointer listener替换/叠加当前输入 | 用户提出关联问题；源码未见registerPointerEventListener直接调用，BackgroundTouchBlocker monitor用于取消背景触摸，FanFreeform有SystemUI InputEventHandler。不能据此认定监听抢走evdev，也不能当已实施的新架构 |
| D18 | 调查推测；待确认 | 原三张文字/图片/图标悬浮都同源、录屏导致卡住、多个input设备等于多个物理屏幕 | 缺统一现场日志；H02曾依用户要求停止后被新虎扑反馈重开。beta19修复两个代码缺陷，但日志未含选中源；设备库存不能证明多个物理屏幕，用户未录屏也可触发。保留原生预览NOT_TOUCHABLE，不加关闭按钮掩盖归属问题 |

## 不应从历史材料恢复的操作

历史安装授权、设备PID、CI run、作用域版本、旧测试数和“当前已安装”仅属于当时记录，不构成新任务的手机/发送授权或当前验收证据。框架未变时按用户偏好只热重载；框架代码变化才评估软/整机重启。当前轮明确禁止手机操作、安装和发布；本地构建/备份/推送按当轮授权，不沿用上轮只读限制。

2026-10-24落实：D15实验路线已用archive标签保留研究引用；正式独有的致谢/指南按当前行为适配到Beta。没有改变D01–D18的产品取舍；公开图库副本、API102和系统pointer listener仍未采用。

保留图片能力URI代理兼容与受限RPC、数据库与旧配置、原生触摸结束回放、隐私日志限制和用户内容，不借“清理屎山”删除功能或扩写架构。用户普通更新说明简短，技术细节放诊断/交付证据，不堆到首页。

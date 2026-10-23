# 致谢与参考来源

MMGG 建立在许多开源项目、系统接口研究和参考应用的交互经验之上。以下列表区分了
“并入或适配代码”“Hook 与接口研究”“交互与行为参考”和“系统能力与社区支持”。

未公开源码的 APK 只用于观察行为、接口和故障表现。本仓库不包含这些 APK、反编译输出，
也不把反编译代码复制进产品。第三方代码的版权与许可证仍归原作者，具体文件中的版权头、
许可证和来源说明优先。

## 特别致谢

- **@Leaf_lsg**：HyperDragShare 为本项目提供了坚实基础。感谢其对上游项目的持续投入，
  以及为本 fork 保留的项目历史与开源基础。
- **@久I零**：感谢其前期投入，为大爆炸和临时中转站搭建了功能框架，并为快速触发大爆炸
  验证出可行路径。
- **@反方向得钟**：感谢其 Hyper 手势项目，为本项目的手势能力和交互设计提供了帮助。
- **@蛮吉1664**：感谢其在大爆炸与 Flyme 小窗交互上的设计思路，以及对复用系统能力的启发。
- **@zz1812**：感谢其复制直达项目，为本项目的链接、号码等规则识别与快捷动作提供了参考。
- **CashewTeam 及全体贡献者**：感谢 BigBang_NovaText 在 Big Bang 分词、选词和取词
  交互上的开源工作。

## 项目基础与并入代码

- [Leaf-lsgtky/HyperDragShare](https://github.com/Leaf-lsgtky/HyperDragShare)：
  感谢 @Leaf_lsg 及项目贡献者。HyperDragShare 是 MMGG 的上游基础，本 fork 保留其项目历史、
  著作权和 GPL-3.0 许可。
- [chenran0408/FanFreeform](https://github.com/chenran0408/FanFreeform)：
  感谢项目维护者和贡献者。扇形菜单、侧滑、蜂巢、自由窗协调等手势运行时经适配后并入
  MMGG，按 GPL-3.0 继续分发。
- [HowieHChen/XiaomiHelper](https://github.com/HowieHChen/XiaomiHelper)：
  感谢 HowieHChen 及贡献者。MMGG 的
  [DragAndDrop.kt](../app/src/main/kotlin/com/leaf/hyperdragshare/codex/DragAndDrop.kt)
  明确改编自 XiaomiHelper 的 Compose 拖放列表实现，保留来源说明并按 GPL-3.0 使用。
- [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix)：
  感谢 compose-miuix-ui 贡献者。项目使用 Miuix 构建设置、浮窗、图标和交互组件，并改编
  其公开示例中的 OS3 背景效果；相关文件保留 Apache-2.0 版权头与来源说明。

## 系统 Hook 与接口研究

以下项目仅用于公开实现或 Hook 方向核对，MMGG 针对本机版本独立实现，未复制其代码。

- ReChronoRain/HyperCeiler：
  感谢维护者和贡献者；其 TrustService 与 MRM Hook 帮助定位 MiTrustService。AGPL-3.0。
- TakeKazeX/HyperTweak：
  感谢 TakeKazeX 及贡献者；其风险监控 Hook 用于交叉核对 MiTrustService。
- tigergalaxy1st/MiTrustBuster：
  感谢作者和贡献者；其关闭 MRM 风险检测的思路供技术核对。仓库未声明 SPDX 许可证。
- HowieHChen/XiaomiHelper：
  感谢 HowieHChen 及贡献者；其风险检查与 HyperOS 接口研究帮助核对 Hook 边界。

相关核对记录见 `docs/MITRUST_RESEARCH.md`。

## 交互与行为参考

- [CashewTeam/BigBang_NovaText](https://github.com/CashewTeam/BigBang_NovaText)：
  感谢 CashewTeam 及全体贡献者。该开源项目为 Big Bang 的分词、取词和操作流程提供了参考。
- MI Big Bang，包名 `com.mengting.bigbang`，版本 1.7.0 的 428、439 构建：
  感谢 @久I零、测试参与者和构建分享者。其长按触发、微信取词、OCR、中转站与
  浮窗交互用于行为对照。项目源码未公开；MMGG 只独立实现相应能力，未导入反编译代码。
- ColorOS Big Bang 1.0.4 参考构建：
  感谢参考应用作者和分享者。该样本用于比较私有手势监听、识别流程和透明承载方式；
  MMGG 将这些目标改接到 HyperOS 上已核验的系统能力。
- [Mangi-11/FlymeFreeform](https://github.com/Mangi-11/FlymeFreeform)：
  感谢 Mangi-11 及贡献者。自由窗、底角轮盘和窗体行为为 MMGG 的小窗与手势设计提供了
  交互参考，按 GPL-3.0 保留开源来源。
- [1812z/HyperCopy](https://github.com/1812z/HyperCopy)：
  感谢作者 1812z。复制直达的目标识别、原生提示和打开方式用于产品行为分析。该项目在
  GitHub API 中未声明可识别的 SPDX 许可证，因此 MMGG 没有复制其代码。

## 系统能力与社区支持

- 感谢小米 HyperOS、传送门、系统界面、桌面、安全中心、澎湃 AI 引擎与 AICR 的系统能力
  开发者和维护者。MMGG 与小米公司没有隶属、合作或背书关系。
- 感谢通过 GitHub、酷安、Telegram 等渠道提交问题、日志、截图、使用视频与适配经验的
  社区用户和测试者。涉及私人内容的原始材料不进入仓库。
- 感谢 MMGG 的所有贡献者，以及上游 HyperDragShare 和 FanFreeform 的历代贡献者。

## 维护说明

如果项目名称、作者署名、许可证或引用用途需要更正，请提交 Issue 或 Pull Request。
无法从公开资料可靠确认的个人账号不会凭猜测填写。

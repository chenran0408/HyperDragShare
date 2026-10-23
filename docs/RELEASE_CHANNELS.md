# 两条维护线与交付规则

当前版本/提交只见[CURRENT_STATUS](CURRENT_STATUS.md)，分支/ref与可执行Git提案只见[BRANCHES](BRANCHES.md)。本文件只维护渠道和发布规则；旧版本示例已[归档](archive/2026-10-24/RELEASE_CHANNELS.md)。

## 职责与签名

- `beta`承接新功能、修复与候选验收；用`assembleBeta`生成R8包，版本名带`-beta.N`，产物`app/build/outputs/apk/beta/app-beta.apk`。如创建GitHub Release，必须Pre-release，不能标Latest。
- `repair-from-189`保留已发布正式版及必要维护；`assembleRelease`产物`app/build/outputs/apk/release/app-release.apk`。Beta未验收不自动晋升，不因交付候选就发布正式版。
- 包名和现有配置不变，但**覆盖安装要求实际签名一致且versionCode满足升级规则**。`app/build.gradle`中Beta优先用已配置release签名，未配置会回退debug；不能笼统承诺Beta/正式包同签。新Agent须对照实际证书，不自动生成替代密钥、不卸载/清数据硬装。
- 正式版versionCode必须高于拟替换的已安装候选。不要恢复旧文档245等固定下限；按所有目标安装包实际版本判断。数据库schema2到schema1降级另需对应数据备份。
- 当前Beta更新检查只读正式线，正式版本号和versionCode都更高才允许切回；Debug更新检查停用。自行编译不等于已发布或可覆盖用户包。

## 修改后的检查

运行JDK21；普通行为变更按根AGENTS做受影响逻辑检查，主要修改后集中`testDebugUnitTest lintDebug assembleDebug`；交付Beta集中`assembleBeta`含R8。失败修复后重跑受影响检查，不把旧构建结果冒充新源码结果。纯文档不构建。

Beta交付保留源码提交/tag、versionName/versionCode、APK大小/SHA256、实际签名摘要、测试报告、构建日志/R8映射与未覆盖范围。分别写逻辑/替身通过、模拟器交互、设备实测、代码分析；未执行的层级保持未覆盖。

正式发布门槛：必要逻辑/Lint/R8和APK元数据/签名检查 → 授权设备的关键功能及渠道验收 → 用户确认稳定性 → 递增版本并构建正式包 → 正式包安装/作用域加载/关键路径复核 → 获得发布授权后创建正式tag/Release。发布说明面向普通用户，简述变化与下载方式。

## tag与短分支

长期仅Beta与稳定两线；换Agent沿用工作线。交付以不可变tag保存，不为每个版本永久留分支。沿用`delivered/mmgg-<版本>`记录候选源码，正式发布沿用`v<版本>`；tag消息写清“候选、未设备验收”等边界。实验用`archive/<主题>-<日期>`固定最后提交和停止原因，不移动已有tag，不把旧高版本实验当更新候选。

修复/功能/实验短分支仅在确需隔离/并行时建，基于明确开发或稳定提交；合入/停止后按授权收尾并更新决策。稳定紧急修复之后按实际差异回流Beta；不要把历史实验整线merge来补单个功能。

## CI规则

`.github/workflows/android.yml`的push/PR覆盖`beta`与`repair-from-189`，push忽略Markdown/docs；支持workflow_dispatch。PR按base branch选任务，push/manual按ref_name选任务：两线均执行JDK21/SDK37下的`testDebugUnitTest lintDebug assembleDebug`，Beta额外执行`assembleBeta`（含R8）；稳定线没有beta buildType时不调用该任务。对应实际run结果只在CURRENT_STATUS记录。

验证产物名含实际APK版本与完整提交SHA，元数据记录ref/目标分支/版本码，附SHA256、Beta R8 mapping与测试/Lint报告，保留14天；普通失败仍尝试上传报告。文档-only提交可跳过构建，不把相同源码下的文档提交跳过记成“CI通过”。CI包明确标为验证用，不自动交付或发布正式版。

CI未配置正式密钥时产生的Beta可能是debug签名，只可标验证产物，不能替换已交付签名包；正式密钥若未来接入，应独立授权、受保护环境/最小权限，密钥不入Git。云端通过必须核对该run对应提交；不以本地通过替代云端结果。CI不自动创建稳定Release或变更远程默认分支。

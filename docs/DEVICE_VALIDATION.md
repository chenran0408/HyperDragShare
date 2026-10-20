# 目标设备验证记录

继续工作前先阅读本文件与 MIBIGBANG_MIGRATION_PLAN.md。代码与设备现状优先于历史计划；未执行的行为不得标成通过。

## 当前设备与安装

- Xiaomi 15 Pro / 2410DPN6CC，OS4.0.0.17.XOBCNXM，Android 17 / API 37，1440×3200。
- MI Big Bang 已卸载。Root（KernelSU）已授权；SELinux enforcing。
- 当前包：`com.leaf.hyperdragshare.codex`，1.7.62 / versionCode 86。沿用原包名和 debug 签名，原位升级成功。
- 小爱 8.3.30.4420，AICR 4.12.26。能力 Provider 仅返回 `name=screenrecognition, enable=1`，不是 OCR 接口。

## 2026-10-20：1.7.62 作用域软重启

源码验证：`testDebugUnitTest lintDebug assembleDebug` 通过；102 项单测，0 失败。构建与安装成功。

实际通过：

- 设置页的“重载全部作用域”打开 Miuix 确认弹窗，明确说明会关闭应用。
- 取消后 zygote64 PID 2388、system_server PID 4251 均不变。
- 确认后执行一次 `setprop ctl.restart zygote`；zygote64 PID 变为 7494，system_server PID 变为 7826。
- 前后内核 boot ID 同为 `19ab20f7-09f4-40e9-8d2d-c156d1c196c9`。Android 运行环境已重启，没有执行整机重启。
- 22:56:28 LSPosed 日志：新 system_server（7826）安装 `MiuiBackTapGestureService` Hook，默认关闭。
- 22:57:06 传送门（19441）记录 callback Hook 安装成功；22:57:07 记录 runtime started。
- 22:57:00 AICR cognitionService（15206）、22:57:08 actionprovider（19926）及 22:57:10 主进程（20752）记录 text-direct Hook 安装成功。
- 设置页返回“已激活”，显示当前模块已加载。

边界：以上证明重启与 Hook 安装成功；不证明背敲动作、AICR 文本结果、OCR 或拖拽菜单行为通过。Root 拒绝/超时提示已有源码处理，未通过撤销设备权限刻意测试。

## 2026-10-20：1.7.61 桌面入口与文本面板观察

- 102 项单测、Lint、构建通过；设备从 1.7.52 原位升级到 1.7.61 成功，证书指纹一致。
- 图标开关隐藏/恢复、Toast、PackageManager 别名 disabled/enabled 状态以及恢复后 MAIN/LAUNCHER 解析均通过。隐藏后通过 LSPosed 打开入口尚未实测。
- 通过显式文本 Intent 打开分词：原设置页留在后方，前景圆角面板显示文本；系统返回键关闭面板后回到原页。内部仍是透明 Activity/独立任务，尚未满足真正同宿主窗口的弹层架构要求。
- 单词选中态可出现；随后观察到旧选词工具栏只剩灰圆，复制/搜索等图标不可辨。根因是对包含图标与底色的 PNG 整体设置 imageTintList。连续选词、合并、复制/搜索/分享与输入焦点仍待修复后实测。

## 下一批

1. 修复选词工具栏的图标与主题处理，实际验收选词、复制、搜索和返回。
2. 在已激活状态复现简洁/现代菜单，定位触发/窗口/命中的真实故障。
3. 实现本地持久中转站（当前临时图片 Provider 不等同中转站）。
4. 验证 AICR 导出 Service 的绑定与 Binder OCR 方法权限，再决定小爱可选引擎接入；保留 ML Kit。


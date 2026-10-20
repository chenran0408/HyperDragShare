# Root 语义节点实验（尚未接入产品）

用途：确认目标 ROM 可通过独立 app_process 和 UiAutomation 读取当前应用语义节点。不输出节点文字，只输出计数及指定屏幕点的命中边界/文字长度。不是长按功能或后台服务。

使用 Android 37 android.jar 编译 Java 17 字节码，再用 d8 生成 classes.dex；推送到 /data/local/tmp/hds-node-probe.dex 后执行：

```sh
su -c 'CLASSPATH=/data/local/tmp/hds-node-probe.dex /system/bin/app_process /system/bin RootNodeProbe 600 2200'
```

坐标仅示例，必须对应当前页面。实验会连接一次 UiAutomation、读取三次当前根节点并断开；不要与 UIAutomator 或 instrumentation 并发运行。使用 FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES，不修改辅助服务开关。密码节点不输出内容，但当前探针不是完整产品隐私过滤实现。

必须初始化主 Looper，否则本机 AccessibilityInteractionClient 创建 Handler 时发生 NPE、进程被终止。UiAutomation 连接成功不代表节点已就绪，初次 getRootInActiveWindow 返回 null 是本机实测行为。

2026-10-21，小米15 Pro / HyperOS4.0.0.17 / Android17，Root 实测连接5–6ms；bilipai评论页读取168个节点、36个文字节点，共448字符，遍历53–71ms。指定点命中 TextView 语义节点，不代表该节点对应真实 TextView 实例。未输出评论正文。退出后无自动化连接残留；辅助服务仍为空，旋转设置仍1/0。

正式集成前仍需处理：节点就绪事件、触点及前台请求关联、锁屏/密码/黑名单、连接资源释放，以及 UiAutomationConnection 断开时恢复旋转状态的副作用。尚未证明持久连接、长按触发或拖拽体验。

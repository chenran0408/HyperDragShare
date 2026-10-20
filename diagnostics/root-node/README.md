# Root 语义节点实验与运行时入口

用途：确认目标 ROM 可通过独立 app_process 和 UiAutomation 读取当前应用语义节点。不输出节点文字，只输出计数及指定屏幕点的命中边界/文字长度。不是长按功能或后台服务。

使用 Android 37 android.jar 编译 Java 17 字节码，再用 d8 生成 classes.dex；推送到 /data/local/tmp/hds-node-probe.dex 后执行：

```sh
su -c 'CLASSPATH=/data/local/tmp/hds-node-probe.dex /system/bin/app_process /system/bin RootNodeProbe 600 2200'
```

坐标仅示例，必须对应当前页面。实验会连接一次 UiAutomation、读取三次当前根节点并断开；不要与 UIAutomator 或 instrumentation 并发运行。使用 FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES，不修改辅助服务开关。密码节点不输出内容，但当前探针不是完整产品隐私过滤实现。

必须初始化主 Looper，否则本机 AccessibilityInteractionClient 创建 Handler 时发生 NPE、进程被终止。UiAutomation 连接成功不代表节点已就绪，初次 getRootInActiveWindow 返回 null 是本机实测行为。

2026-10-21，小米15 Pro / HyperOS4.0.0.17 / Android17，Root 实测连接5–6ms；bilipai评论页读取168个节点、36个文字节点，共448字符，遍历53–71ms。指定点命中 TextView 语义节点，不代表该节点对应真实 TextView 实例。未输出评论正文。退出后无自动化连接残留；辅助服务仍为空，旋转设置仍1/0。

上述早期探针没有覆盖运行时的请求关联、隐私边界和连接生命周期；1.7.110的持久连接与旋转清理证据见下方。真实面积触发和拖拽体验仍待验证。

## 运行时入口（1.7.110）

上文RootNodeProbe是早期独立诊断，只有计数输出。产品源码入口为RootNodeCaptureMain，APK构建已保留其main名称；RootNodeCaptureClient负责模块/传送门进程中的私有管道。使用已构建APK可启动：

```sh
CLASSPATH=/data/local/tmp/hds-node-test.apk app_process /system/bin com.leaf.hyperdragshare.codex.RootNodeCaptureMain --serve
```

须在Root下执行，APK路径指向待测构建。启动返回ready和pid；随后逐行发送x/y/density/width/height/blocked字段的JSON，early_candidate=true启用候选分阶段通知。candidate通知不含正文；最终结果含原文字及相邻候选。日志/报告只记录计数、耗时和边界，不应把用户正文提交到仓库。关闭stdin结束进程并释放Binder连接。

最终实现空闲时保持连接，但不遍历节点。不要与UIAutomator/instrumentation同时运行。当前ROM已验证正常EOF清理与旋转新值保留；实机证据和未完成项见CONTACT_AREA_TRIGGER.md与DEVICE_VALIDATION.md。

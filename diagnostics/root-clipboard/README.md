# 剪贴板调用身份实验

仅用于目标机生成内容实验，不读出或打印用户剪贴板正文。RootClipboardProbe写入固定测试HTTPS地址或命令行传入的生成标记，再在同一调用身份核对完全一致；只输出匹配布尔值。测试会替换当前剪贴板，需要沿用设备测试授权。

用项目compileSdk的android.jar编译Java、D8转为classes.dex后推到/data/local/tmp/hds-clipboard-probe.dex：

```shell
su -c 'CLASSPATH=/data/local/tmp/hds-clipboard-probe.dex app_process /system/bin com.leaf.hyperdragshare.diagnostics.RootClipboardProbe'
su 1000 -c 'CLASSPATH=/data/local/tmp/hds-clipboard-probe.dex app_process /system/bin com.leaf.hyperdragshare.diagnostics.RootClipboardProbe'
```

目标机AppOps将模块UID的WRITE_CLIPBOARD设为foreground。实际独立浮窗调用setPrimaryClip不抛异常，但写入被拒绝，前台诊断接收端严格匹配false。仅使用UID0+SystemContext的实验也返回false；使用Root启动UID1000+SystemContext后核对true，另一个前台UID也读到匹配true。未修改AppOps、包权限或系统组件。

产品只采用实测可行的调用身份，不把实验过程中的多个候选作为运行时多级回退。根因、最终产品验证及限制见docs/DEVICE_VALIDATION.md。验收前先写入另一个生成标记，再操作产品复制，以避免旧剪贴板恰好相同产生假成功。

# 传送门 5.1.5 核验

2026-10-21 核验用户提供的传送门 APK。包名 com.miui.contentextension，versionName 5.1.5、versionCode 50015、targetSdk 35；设备 dumpsys 同样报告 5.1.5 / 50015。

用户 APK 与此前保存的设备基线 APK SHA-256 相同：E7810EF31917CD64C80BDD338D4434E9CB6C49F4C823CA9B8F7BE6688F0B9C5B。此前文档标注 4.2.1 是版本记录错误，本次纠正，不意味着新增了另一套版本兼容实现。

采用 Manifest 和 JADX 定向读取 Java/DEX 类；未使用 IDA，也未把反编译实现复制入项目。

| 观察入口 | 对模块设计的影响 |
|---|---|
| TextContentExtensionService、其匿名回调 onContentReceived(ClientCatcherResult)、mCallback | 当前 Hook 入口仍匹配 |
| startPickTextTask/startPickImageTask/cancelTask 无参方法 | 保留精确方法限制 |
| 控制码 257 对应 back，258 对应 move，均调用 quitByDefault | 保留真实手指 UP 后回放取消与抑制移动取消的区别 |
| sContent、sIsTextMode、getBitmap、getFirstTouchPoint、getInjectorPoint | 当前内容与初始触点读取仍有依据 |
| 清理流程回收并清空来源 Bitmap | 模块异步使用必须持有自己的图片副本 |
| BaseFloatView.addToWindow 检查悬浮权限，处理 BadTokenException | 窗口失败应在真实边界报告，不能据此推断任意进程均可添加窗口 |
| TaplusMainFloatView 原生窗口为 TYPE_PHONE，全屏，BOTTOM/START | 原生宿主布局不能直接套用到模块；不据此修改模块 TYPE_SYSTEM_DIALOG |
| 服务要求 hyperos.permission.START_TAPLUS_EXTENSION_SERVICE | 普通应用不能假设可直接启动服务 |

这些回调没有提供触摸面积的证据，不能解释或修复当前设备 size/长短轴为零。面积触发仍未验收。静态入口匹配也不等于新版模块注入、拖拽和落点分享已经实测。

下一步继续验证四方向菜单、取消恢复与图片生命周期，再推进独立可交互分词窗口；不扩大作用域，也不把现有不可触摸拖拽预览改成触摸窗口。2026-10-21补充：已定向读取的TextContentExtensionService.onPickText从text_pick/pick_mode Bundle取得viewContent，并解析touch_points、view_rect；getContentRect()返回sContentRect。当前适配器仅取文字/图片，尚未利用此边界。此证据支持进一步定位Root截图范围；没有证明任意邻段获取接口不存在。内容捕获端仍需跟踪，不能把权限大小与已有API返回内容混为一谈。

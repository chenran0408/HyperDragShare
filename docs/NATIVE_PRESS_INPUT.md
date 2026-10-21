# 小米原生触摸：153旁路核验

目标SystemUI17.03.260226.r，ROM4.0.0.17/SDK37，PackageManager确认共享库/system_ext/framework/Miui-WindowManager-Shell.jar。参考用户HyperGesture通过createEventReceiver后注册proxy，可能移到handlers首位。目标receiver按顺序调用onEvent；处理结果及interceptEventWhenHandled都true时break，因此原生handler的onEvent会漏掉代理提前消费的事件。

本fork精确before Hook：com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.MulWinSwitchEventController$EventReceiver.onInputEvent(android.view.InputEvent)。先于所有handler/边缘/小窗筛选，不复制反编译代码。只记录至多32个持续至少200ms的触屏手势汇总size/touchMajor/touchMinor/duration；不读坐标、文字，不改事件/结果/finishInputEvent，不重排handlers、不调用InputMonitor/pilfer。

APK推荐scope加com.android.systemui，MainHook精确包名接线；缺类/Hook失败记录原因，保留系统原行为。153构建安装通过，但只读LSPosed实际scope仍仅Portal/AICR/system，SystemUI未启用/重载，不能称面积有效或触发完成。既有Root入口仍明确叫时长/位移按住，不能冒称拇指面积。

下一步手机空闲时通过现有LSPosed配置启用SystemUI并重载；先看真实尺寸是否非零并能变化，再实现尺寸＋稳定时长确认，确认前不拦截、不校准。后续确认后接管需尊重HyperGesture手势所有权，不能把其事件与活动Root拖拽UP混合。

## 用户重启后的真实证据（178）

15:10:00.052 SystemUI 记录 passive pre-filter MotionEvent sampling installed，故旧的“未启用/加载”已被此证据取代。15:10:14 至 15:11:15 的五个 ≥200ms 实际手势，size/touchMajor/touchMinor 峰值均 0；未确认是指尖还是拇指，不推定所有物理输入均为零。178 未记录 toolMajor。155 源码补采该轴并在 Hook 安装日志附编译版本，需新作用域加载后验证；仍然完全旁路，不抢触摸、不校准。

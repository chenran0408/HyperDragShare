# 小米原生触摸：153旁路核验

目标SystemUI17.03.260226.r，ROM4.0.0.17/SDK37，PackageManager确认共享库/system_ext/framework/Miui-WindowManager-Shell.jar。参考用户HyperGesture通过createEventReceiver后注册proxy，可能移到handlers首位。目标receiver按顺序调用onEvent；处理结果及interceptEventWhenHandled都true时break，因此原生handler的onEvent会漏掉代理提前消费的事件。

本fork精确before Hook：com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.MulWinSwitchEventController$EventReceiver.onInputEvent(android.view.InputEvent)。先于所有handler/边缘/小窗筛选，不复制反编译代码。只记录至多32个持续至少200ms的触屏手势汇总size/touchMajor/touchMinor/duration；不读坐标、文字，不改事件/结果/finishInputEvent，不重排handlers、不调用InputMonitor/pilfer。

APK推荐scope加com.android.systemui，MainHook精确包名接线；缺类/Hook失败记录原因，保留系统原行为。153构建安装通过，但只读LSPosed实际scope仍仅Portal/AICR/system，SystemUI未启用/重载，不能称面积有效或触发完成。既有Root入口仍明确叫时长/位移按住，不能冒称拇指面积。

下一步手机空闲时通过现有LSPosed配置启用SystemUI并重载；先看真实尺寸是否非零并能变化，再实现尺寸＋稳定时长确认，确认前不拦截、不校准。后续确认后接管需尊重HyperGesture手势所有权，不能把其事件与活动Root拖拽UP混合。

## 用户重启后的真实证据（178）

15:10:00.052 SystemUI 记录 passive pre-filter MotionEvent sampling installed，故旧的“未启用/加载”已被此证据取代。15:10:14 至 15:11:15 的五个 ≥200ms 实际手势，size/touchMajor/touchMinor 峰值均 0；未确认是指尖还是拇指，不推定所有物理输入均为零。178 未记录 toolMajor。155 源码补采该轴并在 Hook 安装日志附编译版本，需新作用域加载后验证；仍然完全旁路，不抢触摸、不校准。

## 179软重启已加载

15:29:38.935 SystemUI20665明确记录build=1.7.155/179，toolMajor采样代码已加载；仍待真实手势。此前178累计10个手势三个尺寸全0，不能据其推定新轴。无触摸拦截或校准。

## 179原生面积实际核验：当前四轴全零

SystemUI20665明确加载1.7.155/179后，15:36:15至15:37:32记录7个真实触屏手势（含UP与CANCEL），持续256至1382ms；size、touchMajor、touchMinor、toolMajor峰值均0。采样位于WM Shell EventReceiver.onInputEvent before，先于小窗/边缘handlers，不抢触摸，不混用Root结束事件。真实指尖/拇指身份没有标注，不把它写成完成区分手指对照，但已经证明该入口目前未得到可用面积值。

只读getevent -lp确认Xiaomi_Touch_Input_0/event7声明ABS_MT_TOUCH_MAJOR、ABS_MT_TOUCH_MINOR，范围0..255；没有声明工具宽度轴。能力声明不等于实际数值。15:38:13至15:38:33有限20秒旁路观察无任何SYN_REPORT/接触起点，故不能据此判断驱动不发尺寸；没有坐标/原始触点ID持久化，无注入、校准、抓取页面、权限或设置修改。当前结论：SystemUI现有通道没有可用面积输入，不能启用或声称物理拇指面积触发完成。系统样本与内核观察摘要仅本机reboot155。

后续同一179进程只读复核累计23组真实手势，时长225至1382ms，四项尺寸峰值仍全部0；这是对最初7组结果的补充，不改变当前通道无有效面积的结论。

## 2026-10-21：恢复有限只读面积输入研究

此前156暂停研究的决定已由用户本轮明确更新。产品触发仍使用Root evdev时长/位移；以下工作是独立只读探查，不启用面积触发、校准、驱动开关或输入拦截。

官方MiCode `touch-driver`仓库的`dada-v-oss`实现仅覆盖Android V，不能当作当前Android 17设备驱动的精确源码。当前设备`/vendor_dlkm/lib/modules/xiaomi_touch.ko` SHA256为`76F9A664B3552764AB4682480BDF019AAE1ED0E106D43D6D42C55622CC9DA23D`。本机IDA只读核对定位到ioctl `0x191e8`、mmap `0x1970c`、poll `0x19058`及open/release路径。

只对本次打开的只读文件描述符查询`0x5403 + 0x80d65401`，取得214字节硬件参数：1440×3200、rx17、tx38、factor100、1页×10 buffers、5×5 raw网格。只映射本FD的area1，`PROT_READ`、40960字节（4096字节页）；20秒以poll PRI及本FD cursor读取case4，共3235帧、3234次变化。未保存原始帧、坐标或触点ID，也未解码接触面积。没有改raw开关/模式、没有执行UPDATE_REPORT_POINT、case5 WRITE或IRQ操作，没有修改驱动。设备探查bin已删除；分析材料留在任务`work/research157`，不纳入Git。

结论仍有限：SystemUI原生旁路累计23组真实手势的size、touchMajor、touchMinor、toolMajor四轴均为0；这次内核只读查询及帧读取证明存在可查询硬件参数和变化帧，不证明帧编码包含接触面积或可用于区分指尖/拇指。产品保持时长/位移触发；解码前不改变触发策略。华为官方用户交互参考[长按内容并拖动](https://consumer.huawei.com/cn/support/content/zh-cn16001724/)仅用于交互参考，不是本设备行为或面积能力证据。

## 最新范围调整（2026-10-21）

用户明确停止真实触摸面积研究，继续Root evdev时长/位移触发。历史原始帧不代表有效面积，不再继续实验或校准。优先微信取词、内容拖放、中转站及Miuix统一；另一个完整项目待用户后续交付，本批不预建合并结构。现有依赖已使用compose-miuix-ui/miuix 0.9.3，不是新引入组件库。

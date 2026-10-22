# Root输入和取词子进程：目标机故障

## 已确认原因

150后台启动记录：14:11:12.300，ActivityManager以`Trimming phantom processes`终止归属Portal12882的Root su12957、12960；设备发现命令同期退出137。这是数量裁剪，不能再推定Root权限未授予，也不能归因于窗口列表标志。此前4541归属su也有同类退出。未修改全局phantom阈值、监控开关或授权。

目标ROM只读services.jar SHA256：`E67000C7F004E5590BA923511473CE1812F0470294C573C9C3EB2DF7490C9015`。对以下入口和锁的判断同时核对了DEX指令；反编译Java仅用于定位，没有复制到产品。

## 152已实现的窄Hook

沿用android/system_server作用域，精确入口`com.android.server.am.PhantomProcessRecord.killLocked(String, boolean)`。只考虑原因完全等于上述数量裁剪、record.mUid为0的条目。

归属从`record.mService.mPhantomProcessList.mPhantomToAppProcessMap.get(record.mPid)`读取；这是实际childPid到ProcessRecord映射。要求owner存在、getPid与record.mPpid一致、未isKilled/isKilledByAm，且ApplicationInfo.packageName精确为模块或传送门，才允许跳过此次数量裁剪。映射缺失保持系统原行为，不再查其他映射补猜归属。

trim在持有AMS.mProcLock与phantom.mLock时调用killLocked，已有归属映射受该phantom锁保护。排序阶段持有的PidMap锁在kill前已经释放；PidMap.get本身不同步，不能当作线程安全查询。使用已受保护的归属映射可避免在Hook中新增系统锁、Binder调用或/proc查询。尚未完成整个services.jar的全局锁图证明，不宣称所有调用路径都已验。

其余Process error、Stale process、CPU/进程组处置及普通停止均保持原实现。不得Hook通用killProcessGroup、保护其他应用Root进程或全局关闭phantom限制。条目仍计入总数，可能再次被尝试裁剪，不伪造计数。此Hook不能解决LMKD、主动停止、崩溃或Root管理器终止。

**152已加入RootProcessFrameworkHooks并通过构建，android作用域尚未重载，未证明真机裁剪跳过。** 新框架Hook需要对应系统进程重载；用户正在使用手机，不能以应用热重载冒充已加载系统Hook。后续验收等待一次自然裁剪命中并核对自有输入/取词链持续工作，不造进程洪峰或改全局阈值。

## 独立的生命周期欠项

RootTouchSource仍需修正停止时关闭阻塞读流、旧线程finally清理新会话以及su子进程退出归属。旧reader5237曾确认属于Portal4541、后来自行消失；未执行按进程名或event节点批量清理。不能仅以phantom故障解释全部reader生命周期问题。

## 155软重启后证据

179框架Hook已于15:29:23随system_server18477加载。15:30:22的新故障是LMK杀cached模块和Portal宿主（adj905），不是phantom子进程数量裁剪；不扩大Hook阻止LMK。历史握手/ready偏好在进程消失后仍使设置实际误报激活，下一批修Binder存活状态。前述153已修输入Run归属及EOF清理，旧“仍需修”是历史记录；仍未证明框架保护自然命中。

## 156状态存活修复

ModuleActivation以受限ProviderRPC的进程Binder token判当前连接，死亡清运行状态，旧death按token/pid隔离。模块重建丢token时旧pref不能冒称当前激活，显式重载恢复。Settings死亡通知不触发自动握手。真机停止18074后立即撤下已激活并清pref、恢复22290/180输入/reader就绪。此修复不阻止LMK，也不扩大phantom保护范围。

## 213 ProcessManager的一键清理保护（已构建安装、锁定写入已验证）

目标ROM本机`miui-framework-device.jar`与`/system_ext/framework/miui-services.jar`已核对：`miui.process.ProcessManager`提供`updateApplicationLockedState(String,int,boolean)`、`isLockedApplication(String,int)`与`getLockedApplication(int)`。`ProcessManagerService.checkPermission()`拒绝shell UID 2000，但允许UID 0；所以只能由Root `app_process`调用。当前主用户为0。`ProcessPolicy`将锁定状态持久写入Settings.System的`locked_apps`，更新时也会提升已运行进程的maxAdj/maxProcState。

OneKeyClean日志确认曾终止adj=200的传送门。目标ROM的一键清理对应ProcessManager policy 1；其`isInWhiteList`对policy 1查询locked-app状态，命中后`ProcessCleanerBase`走trimMemory路径，不请求杀进程。实现仅在设置页显式启动或作用域重载前检查模块`com.leaf.hyperdragshare.codex`与传送门`com.miui.contentextension`；逐包先读状态，仅补未锁定项，再用`isLockedApplication`复核。FGS即使在锁定操作失败时仍继续启动，但设置页会提示并记录诊断日志，重载会将保护失败作为独立错误返回，不把FGS ready当作锁定成功。

这项状态会保存在系统的锁定应用名单中；不会定时重拉进程、修改OOM文件或拦截force-stop。写入前设备这两个包均未锁定；213真机Root调用返回changed=2，从已安装APK再次调用返回changed=0，已确认锁定。恢复到原状态可用Root shell执行（模块APK绝对路径按安装位置替换）：

```sh
CLASSPATH='/data/app/<module-install-dir>/base.apk' /system/bin/app_process /system/bin com.leaf.hyperdragshare.codex.RootProcessKeepAliveMain --disable 0
```

`--disable 0`会移除主用户这两个包的锁定项；若之后手动把其中任一包加入锁定名单，请勿执行该恢复命令，以免移除用户自己的锁定设置。213构建安装与状态读回已通过，一键清理后驻留仍未实测；不承诺对LMKD、Root管理器终止或显式force-stop提供保护。锁定后本批force-stop Portal仍可结束旧宿主并启动新PID8376，未阻止显式停止。

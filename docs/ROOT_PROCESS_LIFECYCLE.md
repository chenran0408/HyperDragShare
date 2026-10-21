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

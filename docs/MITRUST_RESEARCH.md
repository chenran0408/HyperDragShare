# MiTrustService 风险检测公开实现核对

目标设备：Xiaomi 15 Pro，HyperOS 4.0.0.17，Android 17。
目标组件：`com.xiaomi.trustservice` `1.2.260825`。

## 结论

提示“当前设备系统被篡改，存在安全隐患，无法进行连接”来自
`string/system_tempering`。当前版本在 `Y0.h.b(): boolean` 中初始化
`vendor.xiaomi.hardware.mrm.IMrm/default`；MRM 返回风险状态 `1` 后，
`MiTrustService` 使用该字符串显示系统警告。

本项目应 Hook 这个方法并在 MRM 初始化前返回 `false`。全局拦截
`ServiceManager.waitForService` 的范围过大，且若目标已经缓存 MRM 服务就无效。

## 公开实现

- [HyperCeiler `TrustService.java`](https://github.com/ReChronoRain/HyperCeiler/blob/main/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/app/TrustService.java)
  限定目标包为 `com.xiaomi.trustservice`。
- [HyperCeiler `DisableMrm.java`](https://github.com/ReChronoRain/HyperCeiler/blob/main/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/trustservice/DisableMrm.java)
  使用 DexKit，以 `MiTrustService/statusEventHandle`、`try init mrmd Service` 和
  `boolean` 返回值定位初始化方法，并返回 `false`。
- [XiaomiHelper `DisableRiskCheck.kt`](https://github.com/HowieHChen/XiaomiHelper/blob/main/app/src/main/kotlin/dev/lackluster/mihelper/hook/rules/mitrust/DisableRiskCheck.kt)
  使用同一方法特征，第二个字符串改为
  `vendor.xiaomi.hardware.mrm.IMrm/default`。
- [HyperTweak `MiTrustRiskMonitoringHooker.kt`](https://github.com/TakeKazeX/HyperTweak/blob/main/app/src/main/kotlin/com/takekazex/hypertweak/hook/rules/trustservice/MiTrustRiskMonitoringHooker.kt)
  同样使用 DexKit、无参 `boolean` 和两个字符串组合，明确要求唯一匹配。
- [MiTrustBuster `MainHook.kt`](https://github.com/tigergalaxy1st/MiTrustBuster/blob/main/app/src/main/java/com/mitrustbuster/hook/MainHook.kt)
  独立实现也采用相同 DexKit 特征，但其后续按混淆类名扫描的回退不可靠。

这些结果的共同点是 Hook 具体初始化方法，而不是隐藏 Binder 服务。

## 本 fork 实现

- 使用 `org.luckypray:dexkit:2.2.0`，不依赖精确混淆类名。
- 首先匹配 `MiTrustService/statusEventHandle` 与
  `vendor.xiaomi.hardware.mrm.IMrm/default`。
- 若未唯一命中，再匹配 `MiTrustService/statusEventHandle` 与
  `try init mrmd Service`。
- 只接受无参、返回 `boolean` 的唯一下一方法。
- 设置关闭或匹配不唯一时不安装 Hook，保留系统原行为。
- 不采用按类名或方法名模糊扫描的回退。

## 本机验证

目标设备 `com.xiaomi.trustservice 1.2.260825` 中实际命中的方法为
`Y0.h#b`。重启目标进程后的 LSPosed 日志：

```text
DragShare/TrustService: risk-detection hook installed; bypassed Y0.h#b
```

该日志证明 DexKit 匹配和 Hook 安装成功。真实互联业务中是否调用该方法仍以
`MRM risk gate bypassed`日志和选择已配对设备后的界面结果为准。

## 边界

Hook 只在用户开启“禁用风险检测”、LSPosed 已勾选
`com.xiaomi.trustservice` 且重启该进程后加载。弹窗消失不等于设备已通过硬件认证；
该开关只跳过本地 MRM 风险门禁，不伪造远端证书、账号或设备身份。

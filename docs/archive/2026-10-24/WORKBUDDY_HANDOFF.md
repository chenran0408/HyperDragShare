# WorkBuddy 交接

## 当前版本

- 仓库：`D:\CODEX\Tasks\2026-10-20\new-chat-2\work\HyperDragShare`
- 分支：`hyperos4-adaptation`
- 源码：`1.7.189` / versionCode `213`
- APK：`D:\CODEX\Tasks\2026-10-20\new-chat-2\outputs\HyperDragShare-1.7.189-arm64-v8a.apk`
- APK SHA256：`1DA13F528CB29188E75661988D04BDB9D7C557F730AB73C80B9AFD64461C9C29`
- 目标设备：Xiaomi 15 Pro，HyperOS 4.0.0.17，Android 17

## 已实现

- 产品只面向 Root 与 LSPosed；系统无障碍服务已从产品来源移除。
- 长按由 Root evdev 时长/位移判定，传送门 Hook 取得宿主触点，Root helper 在 WMS 核验后读节点或截图，图片识别使用 AICR V2。
- 微信 `ocr_region` 直接回填同一分词浮窗；不暂存图片、不先开图片识字窗。
- 非微信图片按压进入现有拖拽预览。按住图片后可拖向四边菜单，菜单包含中转站、复制、保存、图片识字、扫码和外部应用。
- 分词主栏为全选/取消、拆词、合并、复制、搜索；更多层保留编辑、撤销、词典、翻译、直达和窗口外观。
- 中转站保持灰色侧条、拖动、自动贴边、最大约占 1/6 屏幕；内容可直接拖出。
- 图片菜单扫码直接调用 ZXing，不先 OCR。文字分词不再提供文字转二维码。
- 底角扇形、二段蜂巢、小窗和原生侧栏扩展保留；Hyper 手势侧滑入口已弃用，边缘侧滑交给 HyperOS 原生侧栏。

## FlymeFreeform 参考结论

可借鉴的是底角应用选择、固定快捷方式、自由窗和窗外动作，这些能力已经存在。ColorOS `smartsidebar`、Oplus 自由窗 Binder 与底部拖条重映射不移植。后续可补“首次按最近六个应用生成底角目标”，但不能覆盖已有用户目标。

## 当前验证层级

- 源码：已实现。
- 构建：`testDebugUnitTest lintDebug assembleRelease` 成功，406 tests，Lint 0 errors，R8、资源压缩、arm64-only 成功。
- 安装：1.7.189 已覆盖安装，设备 `base.apk` 与交付包哈希一致。
- Hook：Portal PID 9040 已加载；Root input 218ms ready，press reader 531ms ready。
- 真机：188 日志已确认 WLAN 分享页可捕获二维码图片。189 的真实物理“按住图片并拖向边缘”尚未执行，不能写成通过。

## 下一步

1. 在非微信页面按住图片，拖向四边确认预览和菜单出现，再拖到外部应用或中转站。
2. 在微信普通页面确认快速 OCR 路径未回归。
3. 检查系统分享目标是否实际接收图片；`DragEvent accepted` 不能单独证明目标收到内容。
4. 如出现具体失败，先记录来源包、捕获阶段、窗口首帧、菜单边和目标 UID，再修改对应链路。

## 常用命令

```powershell
$env:JAVA_HOME='D:\CODEX\Tasks\2026-10-13\wj\work\rea-tools\jdk-21.0.12.1+1'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat testDebugUnitTest lintDebug assembleRelease
```

```powershell
adb shell su -c 'content query --user $(am get-current-user) --uri content://com.miui.contentextension.provider.switchcontrolprovider'
adb logcat -d -v time | Select-String 'DragShare/(Press|UI|RootInput|Taplus|RootNodes)'
```

真实手指输入无法用 `adb input` 可靠代替；不应把 ADB 注入、Portal ready 或安装成功写成物理拖拽通过。私人截图、聊天内容、原始参考 APK 和签名材料不进入 Git。

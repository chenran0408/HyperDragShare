# 小米翻译接口静态发现

本记录基于目标小米15 Pro安装 APK 的只读提取与静态分析。没有启动翻译界面、提交文本、触发网络翻译、改设备设置或安装组件。按用户要求暂停后续接入。

## 样本

| 包名 | 版本 | 安装 APK 路径 | SHA-256 |
|---|---|---|---|
| `com.xiaomi.aiasst.vision`（系统标签 `Translate`） | 5.14.3.40，code 540140340，target 36 | `/product/app/AiasstVision/AiasstVision.apk` | `4B5F215FE4D4D4F53076F47C951E02E27789E9A7875098A6A7E8149E2B2EEB25` |
| `com.xiaomi.aiasst.service` | 6.2.2，code 2732，target 35 | `/product/app/MIUIAiasstService/MIUIAiasstService.apk` | `688AA8D98B32357FCE4F4DDB8BC916549DDE9CE0F09FB8027EB0EF6573C1B782` |
| `com.xiaomi.aicr` | 4.12.26，code 2030041226 | 安装路径由 Package Manager 返回 | `6E960DB290329CD96D567CB62BE340656243412B39EC20A0CA5B630E26F696E5` |

主要翻译实现位于 `com.xiaomi.aiasst.vision`；另外两个包是设备包名清单里的候选，并非下述文本翻译 Binder 的宿主。样本副本位于 `work/reverse/xiaomi-translation-20261022/`。

## Binder 文本接口

Vision Manifest 声明 `com.xiaomi.aiasst.vision.sdk.TranslateSdkService` 为 exported service，进程 `:tss`，Intent action 为 `com.xiaomi.aiasst.vision.ACTION_TRANSLATE_SDK_SERVICE`。绑定时需要非空 `from` extra。服务导出权限为 `xiaomi.permission.aivision.MI_AI_TRANSLATE`，该权限在 Manifest 中的 protectionLevel 为 `0x12`（signature | privileged）；包本身使用 `android.uid.system` shared UID。

静态反编译到的 `ITranslateSdkInterface` 调用形状：先调用 `doFunc(0, clientName)` 附加客户端；之后可调用 `startTextTranslate(mode, text, srcLang, destLang)`，返回 dialog ID。实现把它转到包含额外 session/channel 参数的重载，再返回异步会话 ID。结果通过 `ITranslateSdkCallback.onTextTranslateResult(AiTranslateResult)` 回传。结果 Parcelable 带有 `srcStr`、`destStr`、`srcLang`、`destLang`、`isFinal` 等字段。

`TranslateSdkNativeProxy.verifyCallingFrom()` 允许 Binder caller UID 为 1000 或服务自己的 UID；其他 UID 必须能由 Package Manager 映射到包含已附加 `activeClient` 的包名。普通模块 UID受 Manifest 权限限制。UID 0 没有被这段校验显式放行，所以不能把 `su` 下的默认 `app_process` 当成可用调用方。运行在 Vision/system UID 的 Hook，或经验证以 UID 1000 运行的 helper，是候选方案；两者均未实测。

SDK 会检查 Xiaomi CTA 同意状态；未同意时 `verifyAvailability()` 通过 callback 返回错误码 2000（`not agreed cta`）。SDK 本身依赖 `sdk.online.TranslateEnginePresenter`；DEX 还包含 `AivsTranslator` 调用和 `https://translate.xiaomixiaoai.com`、`/nlp/translate/v1/general` 等远程端点字符串。APK 另有独立 offline translation engine，但本次没有证据证明 Binder SDK 的会话会选择本地模型或已下载模型，也没有核对实际网络选路。

## 原生界面与接入边界

`WordsTransActivity` 是 exported activity，静态代码读取 `EditTextContent` extra 填入文本编辑框；这只证明可预填原生界面，未证明它会自动翻译或向调用方返回结果。`IntentAiTranslateServiceActivity` 有公开翻译相关 Intent actions，但其主路由读取 `from`/`function` 并转入自身界面或 service；没有发现 arbitrary text 到译文 callback 的公开 Activity 契约。翻译详情 Activity 也受同一 signature/privileged 权限保护。

若要把译文嵌入现有 Search/分词浮窗，优先验证 system-UID Hook/adapter：绑定上述 Binder、按需满足 CTA、用 callback 的 `destStr` 更新现有面板。不要把启动原生 UI、权限探测或固定云 URL 当作已验证的文本翻译接口。运行时绑定、权限授予状态、语言代码、模式取值、callback 生命周期、在线请求及现有浮窗接线均未验证；本记录只证明 APK 中存在该接口形状与调用限制。

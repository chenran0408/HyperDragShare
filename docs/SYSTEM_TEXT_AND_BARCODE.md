# 系统分词与条码接口

目标：小米15 Pro / HyperOS 4.0.0.17 / Android17，AICR 4.12.26。系统 ICU 和 AICR 条码的普通应用 UID 调用已由此前实机验证，本轮用户再次确认；本批客户端的具体构建、安装和操作证据见 CURRENT_STATUS、DEVICE_VALIDATION，不将旧接口验证等同新版本端到端通过。

## 分词

`TextSegmenter` 使用 `android.icu.text.BreakIterator.getWordInstance(Locale.ROOT)`。ICU 按文本脚本使用系统词典，支持中、英、日、泰、阿拉伯等混排，不以手机界面语言强制整段只选一种语言。词边界再对齐 `getCharacterInstance(Locale.ROOT)` 的字素边界，避免拆坏组合重音、代理对、肤色和 ZWJ emoji。

输出仍为原文 UTF-16 的包含尾端范围，词与标点以 `-1` 分隔，兼容选词、拆词、撤销和原文拼接。空白留在原文，不变成选词块。ICU 划分与 jieba 不保证完全相同，并随系统词典版本变化；没有自带词典、语言识别器或回退分词器。

## 条码

```java
AicrBarcodeClient.Request request = AicrBarcodeClient.scan(context, bitmap, (codes, error) -> {
    // 主线程：codes 是去重后按返回顺序排列的码值，空列表表示没有识别到码。
    // error 非空时展示原因；到这里即可释放调用者持有的 bitmap。
});
// 所属窗口关闭：request.cancel(); 仍在完成回调里释放 bitmap。
```

绑定 `com.xiaomi.aicr/com.xiaomi.aicr.access.AiCrCoreService`，action 为 `com.xiaomi.aicr.access.AICR_ENGINE`，extra `bind_type` 为此次唯一 address。Core 描述符 `com.xiaomi.aicr.IAiCrCoreService`，事务1申请插件：callback、address、`com.xiaomi.aicr.plugin.IBarcodeService`、**空字符串版本**；读取插件 Binder 和输出 Bundle 的 `Status`。扫码接口没有 OCR 的 `getOCRVersion()` 流程，不能传 `V2`。

| IBarcodeService 事务 | 参数 | 调用方式 |
|---|---|---|
| 1 注册监听 | listener Binder、address | 同步 |
| 2 移除监听 | address | oneway |
| 3 识别图片 | typed Bitmap、address | oneway |

监听器描述符 `com.xiaomi.aicr.barcode.IBarcodeResultListener`，事务1同步返回 typed `BarcodeBean` 列表。每条顺序：valueType String、rawValue String、cornerPoints 数量与逐个 Parcelable Point、Parcelable Rect、barcodeFormat int。客户端消费完整记录后显示码值，不加载系统私有 Java 类。

每次请求在结果/取消/失败后移除监听，调用 Core 事务2释放 address，并解绑服务。没有绑定失败重试、静默切换引擎或打包系统模型；插件缺失、权限限制、状态失败和 Parcel 不匹配都反馈原因。

本客户端传入已有 Bitmap，不截图、不申请 Root，也不读取小爱能力查询 Provider。截图、Root 取词和 Hook 是本产品另外的能力。扫码复用原图片浮窗和既有 Wi-Fi/链接取用流程。

协议证据：目标 AICR 样本的 `BarcodeManager.registerPlugin`、`IBarcodeService.Stub.Proxy`、`IBarcodeResultListener.Stub` 和 `BarcodeBean` 读写顺序。客户端独立实现，未复制反编译代码或系统 ML Kit 模型。

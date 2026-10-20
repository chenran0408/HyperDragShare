# 应用内搜索面板

版本：1.7.52 / 76。

## 行为

- 分词页的“搜索”和“词典”按钮打开应用内底部面板，默认分别使用百度搜索和有道词典。
- 可切换百度、必应、Google、维基百科、有道词典、必应词典、金山词霸、有道翻译和百度百科；可编辑查询，也可输入 HTTPS 网址。
- 顶部拖动条可调整面板高度，偏好保存在模块 UID 的私有 SharedPreferences 中。
- 提供网页前进、后退、刷新以及转到系统浏览器的按钮。
- WebView 只加载 HTTPS 地址，禁用文件和 content URI 访问、混合内容、弹窗和 JavaScript bridge；JavaScript 仅用于搜索站点自身页面。

## 逆向参考与证据范围

对 `mibigbang.apk`（SHA-256：`5C18D11B3169CED658809039473320FA7D0F6EAAC05DEC393E739C24B362441D`）做了静态分析。IDA 中的 `BoomSearchOverlayActivity` 显示底部 WebView 面板、查询输入、引擎列表、大小调整和网页导航；其静态初始化包含多个搜索、词典和百科 URL 模板。这是样本字节中的静态观察，不代表已验证其运行效果。本项目只独立实现对应的交互能力，没有复制 APK 中的类或代码。

此面板会把用户提交的查询发送给当前选中的搜索站点。网站内容、可用性和搜索结果由该站点决定。面板尚未在目标手机上进行实机验收。

## 检查

`SearchEngineTest` 覆盖默认引擎、UTF-8 查询编码、HTTPS 地址识别以及非 HTTPS scheme 拒绝。项目行为变更检查：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

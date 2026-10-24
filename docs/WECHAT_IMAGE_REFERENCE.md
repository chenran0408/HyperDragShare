# 微信图片与参考源码对照

## 已确认现象

- 用户在beta20遇到微信内图片拖出与独立中转站拖入微信均无效，其他常见App可用。
- 截图确认源端进入“未取得可用原图、取消截图拖拽”的MMGG保护分支；不能由此认定微信或Root权限缺失。
- 用户确认参考App在微信聊天缩略图上能拖出纯图片；参考App本身没有中转站，能拖入微信的是基于它修改的旧版中转站。

## 参考身份

用户提供《全部源码（清理版）.zip》，SHA256：`310601DBB8BE87BA5C46BADD1AAC1557C9F5A740FC5F23A8B7B65F9FE53E491A`。外发说明标为Mi Big Bang V2，452源码、436分词样式；应用ID `com.mengting.bigbang`。解压仅用于源码比较，没有运行、构建或安装。鉴权、签名、本机配置不移入MMGG。

## 源端结论

参考有两条捕获路径：Portal结果直接读取；Accessibility节点定位后截图裁剪。它没有MMGG的ImageOrigin来源分类，不能仅由“无聊天边框”证明运行时走的是原文件或裁剪图；也不能反过来认定用户成功拖出的图一定是截图。`WeChatOriginalFinder`缓存匹配只由小图OCR调用，不是已建立的原图拖拽接口。

参考在`onPickText/onPickImage`方法执行前读取ClientCatcherResult，并忽略较晚的`startPick*`。其“旧start一定读旧内容”注释与已核验的Portal5.1.5实际实现不符：onContentReceived先写mCatcherResult，onPickImage先处理imgUrl/viewBitmap和边界，再向主线程post startPickImageTask。参考before-hook还先于宿主presentation检查；因此本轮没有照搬该入口或随意信任propertyMap的任意Bitmap。

MMGG继续只把已核对的Portal媒体视为微信原媒体。用户在看到参考路径分析后，明确允许“标注为缩略图”的兼容拖拽；这更新了旧绝对禁止截图拖拽规则的有限范围。Root已有图片节点裁剪路径保留安全检查，结果独立标为NODE_THUMBNAIL，预览持续显示“缩略图”；它不是原文件，不能改成PORTAL_MEDIA。没有图片节点时的泛OCR区域、普通截图和UNKNOWN来源仍拒绝，Portal媒体仍可接管同一按压。没有新增无障碍服务、缓存原图模糊匹配或绕过宿主presentation检查。

现有报告过滤自由日志，使beta20的具体来源、拒绝原因没有进入诊断ZIP，这是已证实的**诊断缺口**，不是已证实的原图捕获根因。本轮补受控阶段记录及实际导出测试；兼容缩略图另记为wechat_image_node_thumbnail，不改授权/释放行为。

## 接收端结论

参考源码残留StashStationOverlay使用私有URI和显式MIME，从卡片窗口直接发起global+read拖放；该残留源码不能当作用户正在使用的旧版中转站。MMGG使用既有MediaStore Downloads兼容副本，MIME来自存储项，独立入口由模块浮窗起拖。历史166版本的单PNG当前聊天接收曾有设备证据；beta20诊断包没有任何可关联的stash准备/起拖/结束记录。

没有证据证明此次停在图片准备、原生拖放、目标接收或微信内部读取中的哪一步。没有修改URI来源、源窗口或权限作为试错；MediaStore访问不经过MMGG Provider，缺少其openFile日志不等于微信没有读取。

## 后续分词面板（待实施）

以此档实际436样式为参考：`boom_activity_layout.xml`、`BoomWordsLayout`、`BoomChipPage`、`SwipeSelectView`、`BoomActionHandler`、`BoomAnimator`。先移植用户需要的选择、拖选、控件位置与排版，保留MMGG的BigBangPanelSession动作接口、Miuix主题和退出所有权。逐项做交互对照，不整包恢复旧Activity/鉴权/无障碍/JNI引擎。本轮仅完成代码地图，尚未修改或验收面板。

详细边界和文件行号见任务`work/reference-receive-panel-audit.md`与`work/wechat-source-audit.md`。本文件不含私人诊断原文、图片或URI。

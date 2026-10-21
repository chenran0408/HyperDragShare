# Hyper 手势合并记录

## 已核对来源

- 用户仓库：https://github.com/chenran0408/FanFreeform 。main为131，os4-compatibility为156；合并使用用户提供的完整157源码档案。
- 157源提交：2256ebe676c265b9756a881e64884aa46cc0f67d；源码ZIP SHA256：83fb3ccfb546cee78b59ba96a1d9a0951d57d7f3e1ed71fa013ecebbdcf12178。
- FanFreeform与本仓库均为GPLv3；保留原作者来源，衍生核心改用com.leaf.hyperdragshare.gestures命名空间。

## 合并次序与当前事实

1. 已导入157配置、手势/小窗运行核心、诊断、共享角标绘制及相应测试；复用单个Android应用、已有Xposed API与Miuix 0.9.3，不引入第二套Gradle应用/Compose依赖或重复原生库。
2. 尚未注册新Provider/Hook或接入设置入口；这只是源码整合检查点，不是功能已启用。原Hyper手势仍独立工作，避免两套手势同时接管。
3. 下一批用现有Miuix设置架构重建分组页面，保留扇形、侧边、蜂巢、任务中心、窗口和快捷目标的完整配置。不能用原生旧Activity伪装UI已统一。
4. 接入SystemUI与桌面入口时统一事件仲裁、包名/Provider与广播身份；原项目现有SystemUI尺寸旁路诊断应停用，不重复注册。主项目新作用域com.miui.home仅在入口/冲突验证完成后加入。
5. 设备配置已在本机完整备份shared_prefs和files并验证tar可读，286文件、2155008字节，SHA256 4f29de7cf2f7e6141287169e147f7daf08b3284ebadae5d6f7f346d7dddd2d24。私有备份不提交。尚未导入目标。
6. 数据迁移保留fan_config.xml的所有字段和原始类型、JSON字符串、迁移标记及shortcut-icons。以原始XML为依据，不使用Provider裁剪后的Bundle重建；目标初始化前迁移，导入后做键/类型/值及图标校验，原应用数据保留。
7. 源157已包含侧边几何布局缓存、减少MOVE分配与重复alpha更新等改进。合并后的速度和包体积需实际测量，不能引用源项目微基准作为本项目已提速证据。

此前微信取词回归、中转站微信接收和分词弹窗159真机验收仍未完成，随合并批次继续处理，不抹掉这些缺口。

## 核心合并检查点

65个核心Java文件、31个源项目测试文件已纳入主app；build328通过376项测试（原228+源148）、Lint0错误175警告2提示和Debug构建。build327暴露源测试需要真实org.json实现，已只在testImplementation补入源项目同版本org.json，不增加APK运行依赖。没有安装此未接入口的内部构建；设备仍为159/183。

正在实现Miuix设置页；未启用新运行时、未迁移数据到目标、未声明功能完成。后续需要对继承代码的空catch/Throwable处理逐个按真实边界整理，避免把历史源码整体导入当可维护性已达标。

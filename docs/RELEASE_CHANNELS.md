# Beta 与正式版发布线

MMGG 使用同包名、同签名和同一套用户配置，分为 Beta 与正式版两条构建线。

## Beta

- 构建类型为 `beta`，命令为 `.\gradlew.bat assembleBeta`。
- 版本名带 `-beta.N`，当前为 `1.7.219-beta.2`；APK 位于
  `app/build/outputs/apk/beta/app-beta.apk`。
- GitHub 发布应使用 `Pre-release`，例如 tag `v1.7.219-beta.2`，不能标记为 Latest。
- Beta 只用于本机或测试用户验收。未得到明确稳定性确认前，不创建对应正式 Release。
- Beta 的“检查更新”只读取正式线。只有正式版版本号和 `versionCode` 都高于当前
  Beta 时，才允许下载并切回正式版，避免被旧正式包反向覆盖。

## 正式版

- 构建类型为 `release`，命令为 `.\gradlew.bat assembleRelease`。
- 版本名不带后缀，APK 位于 `app/build/outputs/apk/release/app-release.apk`。
- 发布前必须提高 `versionCode`。Beta `1.7.219-beta.2/244` 验收通过后，
  正式版应为 `1.7.219/245` 或更高。
- 正式 Release 使用普通 tag，例如 `v1.7.219`，确认非 Draft、非 Pre-release 后
  才标记为 Latest。

## 发布门槛

1. 完成单测、Lint、R8 Beta 构建和同签名安装。
2. 由用户或测试者在真实设备上确认目标功能、更新通道和基本交互。
3. 保留 Beta APK、SHA256、设备版本与验证边界记录。
4. 只有步骤 1–3 完成后，才递增 `versionCode` 并构建正式版。
5. 正式包完成安装、签名、版本和关键路径复核后，才推送 GitHub 正式更新。

开发构建的更新检查直接停用，避免 Debug 包误下载或覆盖正式安装。

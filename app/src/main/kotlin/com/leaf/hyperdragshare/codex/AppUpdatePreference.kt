package com.leaf.hyperdragshare.codex

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.text.Html
import android.util.Log
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.regex.Pattern

private const val UPDATE_TAG = "HyperDragShare/Update"
private const val RELEASE_PAGE = "https://github.com/chenran0408/HyperDragShare/releases/latest"
private const val RELEASES_PREFIX = "https://github.com/chenran0408/HyperDragShare/releases/download/"
private const val UPDATE_PREFS = "app_update"
private const val KEY_DOWNLOAD_ID = "download_id"
private const val KEY_DOWNLOAD_NAME = "download_name"
private const val MIME_APK = "application/vnd.android.package-archive"
private val RELEASE_ASSET_PATTERN = Pattern.compile("HyperDragShare-(\\d+(?:\\.\\d+){1,3})-arm64-v8a\\.apk")

private data class ReleaseInfo(
    val versionName: String,
    val notes: String,
    val publishedAt: String,
    val assetName: String,
    val downloadUrl: String,
)

private data class DownloadSnapshot(
    val status: Int,
    val reason: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
)

private data class VerifiedApk(val uri: Uri?, val error: String? = null)

/** Checks the official stable release and offers a user-confirmed DownloadManager/install flow. */
@Composable
internal fun AppUpdatePreference(title: String, enabled: Boolean = true) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var checkRevision by remember { mutableLongStateOf(0L) }
    var checking by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf<String?>(null) }
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    var downloadId by remember { mutableLongStateOf(preferences.getLong(KEY_DOWNLOAD_ID, -1L)) }
    var downloadName by remember { mutableStateOf(preferences.getString(KEY_DOWNLOAD_NAME, null)) }
    var downloadSnapshot by remember { mutableStateOf<DownloadSnapshot?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var installMessage by remember { mutableStateOf<String?>(null) }
    var forceRedownload by remember { mutableStateOf(false) }

    ArrowPreference(
        title = title,
        summary = "查看正式版更新说明并下载安装",
        enabled = enabled,
        onClick = {
            showDialog = true
            checkError = null
            release = null
            checkRevision++
        },
    )

    LaunchedEffect(showDialog, checkRevision) {
        if (!showDialog || checkRevision == 0L) return@LaunchedEffect
        checking = true
        checkError = null
        release = null
        try {
            release = withContext(Dispatchers.IO) { fetchLatestRelease() }
        } catch (error: IOException) {
            Log.w(UPDATE_TAG, "Release check failed", error)
            checkError = when (error) {
                is SocketTimeoutException -> "连接 GitHub 超时，请检查网络后重试。"
                is UnknownHostException -> "无法连接 GitHub，请检查网络或稍后重试。"
                else -> error.message ?: "连接 GitHub 失败，请检查网络后重试。"
            }
        } catch (error: IllegalArgumentException) {
            Log.w(UPDATE_TAG, "Release metadata was invalid", error)
            checkError = error.message ?: "GitHub 返回的版本信息不完整。"
        } finally {
            checking = false
        }
    }

    LaunchedEffect(showDialog, downloadId) {
        if (!showDialog || downloadId < 0L) return@LaunchedEffect
        val manager = context.getSystemService(DownloadManager::class.java)
        if (manager == null) {
            downloadError = "系统下载服务不可用，无法查看下载状态。"
            downloadSnapshot = DownloadSnapshot(DownloadManager.STATUS_FAILED, DownloadManager.ERROR_UNKNOWN, 0, -1)
            return@LaunchedEffect
        }
        try {
            while (isActive) {
                val snapshot = withContext(Dispatchers.IO) { queryDownload(manager, downloadId) }
                if (snapshot == null) {
                    downloadError = "系统已移除该下载任务记录，请重新下载。"
                    downloadSnapshot = DownloadSnapshot(DownloadManager.STATUS_FAILED, DownloadManager.ERROR_UNKNOWN, 0, -1)
                    break
                }
                downloadSnapshot = snapshot
                if (snapshot.status == DownloadManager.STATUS_SUCCESSFUL ||
                    snapshot.status == DownloadManager.STATUS_FAILED) break
                delay(900)
            }
        } catch (error: SecurityException) {
            Log.w(UPDATE_TAG, "Could not query system download state", error)
            downloadError = "系统拒绝读取下载状态，请重新下载。"
            downloadSnapshot = DownloadSnapshot(DownloadManager.STATUS_FAILED, DownloadManager.ERROR_UNKNOWN, 0, -1)
        } catch (error: IllegalArgumentException) {
            Log.w(UPDATE_TAG, "System download query was invalid", error)
            downloadError = "系统无法识别此下载任务，请重新下载。"
            downloadSnapshot = DownloadSnapshot(DownloadManager.STATUS_FAILED, DownloadManager.ERROR_UNKNOWN, 0, -1)
        }
        val finalSnapshot = downloadSnapshot
        if (finalSnapshot?.status == DownloadManager.STATUS_FAILED) {
            if (downloadError == null) downloadError = downloadFailureMessage(finalSnapshot.reason)
        } else if (finalSnapshot?.status == DownloadManager.STATUS_SUCCESSFUL) {
            downloadError = null
        }
    }

    val releaseState = release
    val currentVersionComparison = releaseState?.let { compareVersions(BuildConfig.VERSION_NAME, it.versionName) }
    val summary = when {
        checking -> "正在检查官方正式版…"
        checkError != null -> "检查失败"
        releaseState == null -> "检查 chenran0408/HyperDragShare 正式版更新"
        currentVersionComparison == null -> "当前版本号无法比较，请通过 GitHub Releases 手动核对。"
        currentVersionComparison > 0 -> "本地版本 ${BuildConfig.VERSION_NAME} 比正式版 ${releaseState.versionName} 更新。"
        currentVersionComparison == 0 -> "当前已是正式版 ${releaseState.versionName}。"
        else -> "发现正式版 ${releaseState.versionName}，可下载安装。"
    }

    OverlayDialog(
        show = showDialog,
        title = "检查更新",
        summary = summary,
        onDismissRequest = { showDialog = false },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                checking -> CircularProgressIndicator()
                checkError != null -> Text(checkError!!, color = MiuixTheme.colorScheme.onSurface)
                releaseState != null -> {
                    Text("正式版 ${releaseState.versionName} · ${formatPublishedAt(releaseState.publishedAt)}",
                        color = MiuixTheme.colorScheme.onSurface)
                    if (releaseState.notes.isNotBlank()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(cleanReleaseNotes(releaseState.notes), color = MiuixTheme.colorScheme.onSurface)
                        }
                    } else {
                        Text("此版本没有发布说明。", color = MiuixTheme.colorScheme.onSurface)
                    }
                }
            }

            downloadError?.let { Text(it, color = MiuixTheme.colorScheme.onSurface) }
            installMessage?.let { Text(it, color = MiuixTheme.colorScheme.onSurface) }
            val activeDownload = downloadSnapshot
            if (activeDownload != null && (activeDownload.status == DownloadManager.STATUS_RUNNING ||
                activeDownload.status == DownloadManager.STATUS_PENDING ||
                activeDownload.status == DownloadManager.STATUS_PAUSED)) {
                CircularProgressIndicator()
                Text(downloadProgressText(activeDownload), color = MiuixTheme.colorScheme.onSurface)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = if (checkError != null) "重试" else "关闭",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (checkError != null) checkRevision++ else showDialog = false
                    },
                )
                val comparison = currentVersionComparison
                val downloadActive = activeDownload?.status == DownloadManager.STATUS_PENDING ||
                    activeDownload?.status == DownloadManager.STATUS_RUNNING ||
                    activeDownload?.status == DownloadManager.STATUS_PAUSED
                val canDownload = releaseState != null && comparison != null && comparison < 0 && !downloadActive
                val matchingDownload = releaseState != null && downloadName?.startsWith(
                    "${releaseState.assetName.removeSuffix(".apk")}-") == true
                val canInstall = activeDownload?.status == DownloadManager.STATUS_SUCCESSFUL && matchingDownload &&
                    comparison != null && comparison < 0 && !forceRedownload
                if (canInstall) {
                    TextButton(
                        text = "安装",
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            val filename = downloadName
                            val info = releaseState
                            if (filename.isNullOrBlank()) {
                                installMessage = "找不到与当前正式版匹配的安装包，请重新下载。"
                                forceRedownload = true
                            } else {
                                scope.launch {
                                    installMessage = null
                                    val verification = withContext(Dispatchers.IO) {
                                        verifyDownloadedApk(context, filename, info)
                                    }
                                    if (verification.uri == null) {
                                        installMessage = verification.error
                                        withContext(Dispatchers.IO) { deleteDownloadedUpdate(context, filename) }
                                        forceRedownload = true
                                    } else {
                                        launchSystemInstaller(context, verification.uri) { message ->
                                            installMessage = message
                                        }
                                    }
                                }
                            }
                        },
                    )
                } else if (canDownload) {
                    TextButton(
                        text = if (activeDownload?.status == DownloadManager.STATUS_SUCCESSFUL || forceRedownload) "重新下载" else "下载更新",
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            val info = releaseState
                            scope.launch {
                                downloadError = null
                                installMessage = null
                                try {
                                    val (id, filename) = withContext(Dispatchers.IO) { enqueueDownload(context, info) }
                                    downloadId = id
                                    downloadName = filename
                                    forceRedownload = false
                                    downloadSnapshot = DownloadSnapshot(DownloadManager.STATUS_PENDING, 0, 0, -1)
                                    preferences.edit().putLong(KEY_DOWNLOAD_ID, id)
                                        .putString(KEY_DOWNLOAD_NAME, filename).apply()
                                } catch (error: IOException) {
                                    Log.w(UPDATE_TAG, "Could not start release download", error)
                                    downloadError = error.message ?: "无法开始下载，请稍后重试。"
                                } catch (error: SecurityException) {
                                    Log.w(UPDATE_TAG, "DownloadManager rejected release download", error)
                                    downloadError = "系统下载服务拒绝了请求，请检查下载权限或存储空间。"
                                } catch (error: IllegalStateException) {
                                    Log.w(UPDATE_TAG, "Download destination was unavailable", error)
                                    downloadError = "下载目录不可用，请检查设备存储状态。"
                                } catch (error: IllegalArgumentException) {
                                    Log.w(UPDATE_TAG, "DownloadManager rejected the release URL", error)
                                    downloadError = "官方安装包下载地址无效，请稍后重试。"
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun fetchLatestRelease(): ReleaseInfo {
    val (page, finalPageUrl) = fetchGithubPage(RELEASE_PAGE)
    val finalUrl = URL(finalPageUrl)
    val tag = finalUrl.path.substringAfterLast('/')
    if (finalUrl.protocol != "https" || finalUrl.host != "github.com" ||
        finalUrl.path.substringBeforeLast('/') != "/chenran0408/HyperDragShare/releases/tag" ||
        !tag.startsWith("v") || !VERSION_PATTERN.matcher(tag.removePrefix("v")).matches()) {
        throw IllegalArgumentException("GitHub 没有返回可识别的正式版页面。")
    }
    val version = tag.removePrefix("v")
    val fragmentSource = Regex("""<include-fragment[^>]+src=["'](https://github\.com/chenran0408/HyperDragShare/releases/expanded_assets/v${Regex.escape(version)})["']""")
        .find(page)?.groupValues?.get(1)
        ?: throw IllegalArgumentException("正式版页面没有提供可验证的安装包列表。")
    val (assetsHtml, assetsUrl) = fetchGithubPage(fragmentSource)
    val assetsPage = URL(assetsUrl)
    if (assetsPage.protocol != "https" || assetsPage.host != "github.com" ||
        assetsPage.path != "/chenran0408/HyperDragShare/releases/expanded_assets/v$version") {
        throw IllegalArgumentException("安装包列表不是当前正式版的官方页面。")
    }
    val assetPattern = Regex(
        """<a[^>]+href=["'](/chenran0408/HyperDragShare/releases/download/v${Regex.escape(version)}/([^"']+))["']""",
        RegexOption.IGNORE_CASE,
    )
    val asset = assetPattern.findAll(assetsHtml).firstOrNull { match ->
        val name = match.groupValues[2]
        RELEASE_ASSET_PATTERN.matcher(name).matches() && name == "HyperDragShare-$version-arm64-v8a.apk"
    } ?: throw IllegalArgumentException("正式版没有 arm64-v8a APK，已停止下载以避免安装错误架构。")
    val assetName = asset.groupValues[2]
    val downloadUrl = "https://github.com${asset.groupValues[1]}"
    val notesMeta = Regex(
        """<meta[^>]+property=["']og:description["'][^>]+content=["']([^"']*)["'][^>]*>""",
        RegexOption.IGNORE_CASE,
    ).find(page)?.groupValues?.get(1).orEmpty()
    val notes = Html.fromHtml(notesMeta, Html.FROM_HTML_MODE_LEGACY).toString().trim().take(12_000)
    val publishedAt = Regex("""<relative-time[^>]+datetime=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        .find(page)?.groupValues?.get(1).orEmpty()
    return ReleaseInfo(
        versionName = version,
        notes = notes,
        publishedAt = publishedAt,
        assetName = assetName,
        downloadUrl = downloadUrl,
    )
}

private fun fetchGithubPage(url: String): Pair<String, String> {
    val requested = URL(url)
    if (requested.protocol != "https" || requested.host != "github.com" ||
        !requested.path.startsWith("/chenran0408/HyperDragShare/releases/")) {
        throw IllegalArgumentException("请求地址不属于官方 GitHub Releases 页面。")
    }
    val connection = (requested.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 12_000
        readTimeout = 15_000
        instanceFollowRedirects = true
        setRequestProperty("Accept", "text/html")
        setRequestProperty("User-Agent", "HyperDragShare-Android-Updater")
    }
    try {
        val response = connection.responseCode
        if (response !in 200..299) throw IOException("GitHub Releases 返回 HTTP $response，请稍后重试。")
        val content = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return content to connection.url.toString()
    } finally {
        connection.disconnect()
    }
}

private val VERSION_PATTERN = Pattern.compile("\\d+(?:\\.\\d+){1,3}")

private fun compareVersions(first: String, second: String): Int? {
    if (!VERSION_PATTERN.matcher(first).matches() || !VERSION_PATTERN.matcher(second).matches()) return null
    val left = first.split('.').map { it.toLongOrNull() ?: return null }
    val right = second.split('.').map { it.toLongOrNull() ?: return null }
    for (index in 0 until maxOf(left.size, right.size)) {
        val a = left.getOrElse(index) { 0L }
        val b = right.getOrElse(index) { 0L }
        if (a != b) return a.compareTo(b)
    }
    return 0
}

private fun enqueueDownload(context: Context, release: ReleaseInfo): Pair<Long, String> {
    if (!isTrustedReleaseAssetUrl(release.downloadUrl)) {
        throw IOException("下载链接不是官方 GitHub Releases 地址，已停止下载。")
    }
    val manager = context.getSystemService(DownloadManager::class.java)
        ?: throw IOException("系统下载服务不可用。")
    val filename = "${release.assetName.removeSuffix(".apk")}-${System.currentTimeMillis()}.apk"
    val request = DownloadManager.Request(Uri.parse(release.downloadUrl))
        .setTitle("HyperDragShare ${release.versionName}")
        .setDescription("正在下载 arm64-v8a 官方安装包")
        .setMimeType(MIME_APK)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(false)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename)
    return manager.enqueue(request) to filename
}

private fun isTrustedReleaseAssetUrl(value: String): Boolean = try {
    val url = URL(value)
    url.protocol == "https" && url.host == "github.com" &&
        url.path.startsWith("/chenran0408/HyperDragShare/releases/download/")
} catch (_: java.net.MalformedURLException) {
    false
}

private fun queryDownload(manager: DownloadManager, id: Long): DownloadSnapshot? {
    val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return null
    cursor.use {
        if (!it.moveToFirst()) return null
        return DownloadSnapshot(
            status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
            reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
            downloadedBytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
            totalBytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
        )
    }
}

private fun downloadFailureMessage(reason: Int): String = when (reason) {
    DownloadManager.ERROR_CANNOT_RESUME -> "下载中断且无法续传，请重新下载。"
    DownloadManager.ERROR_DEVICE_NOT_FOUND -> "找不到下载存储位置，请检查设备存储。"
    DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "同名文件已存在，请重新下载或清理旧文件。"
    DownloadManager.ERROR_FILE_ERROR -> "写入安装包失败，请检查存储空间。"
    DownloadManager.ERROR_HTTP_DATA_ERROR -> "下载数据传输失败，请检查网络后重试。"
    DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足，无法下载安装包。"
    DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "下载地址跳转过多，已停止下载。"
    DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "服务器无法提供安装包，请稍后重试。"
    DownloadManager.ERROR_UNKNOWN -> "系统下载服务发生未知错误。"
    DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "等待 Wi-Fi 网络。"
    DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "等待网络连接。"
    DownloadManager.PAUSED_WAITING_TO_RETRY -> "网络暂时不可用，系统正在等待续传。"
    else -> "下载失败（${reason}），请重新下载。"
}

private fun downloadProgressText(snapshot: DownloadSnapshot): String {
    if (snapshot.status == DownloadManager.STATUS_PAUSED) return downloadFailureMessage(snapshot.reason)
    if (snapshot.totalBytes <= 0L) return "正在下载…"
    val percent = (snapshot.downloadedBytes * 100L / snapshot.totalBytes).coerceIn(0L, 100L)
    return "下载中 $percent%"
}

private fun verifyDownloadedApk(context: Context, filename: String, release: ReleaseInfo): VerifiedApk {
    val expectedPrefix = "${release.assetName.removeSuffix(".apk")}-"
    if (File(filename).name != filename || !filename.startsWith(expectedPrefix) || !filename.endsWith(".apk")) {
        return VerifiedApk(null, "缓存安装包与当前正式版不匹配，请重新下载。")
    }
    val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?: return VerifiedApk(null, "下载目录不可用，请检查设备存储状态。")
    val file = File(directory, filename)
    if (!file.isFile || !file.canRead()) return VerifiedApk(null, "已下载文件不可读取，请重新下载。")
    return try {
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: return VerifiedApk(null, "安装包无法读取，文件可能已损坏，请重新下载。")
        if (archive.packageName != context.packageName) {
            Log.w(UPDATE_TAG, "Downloaded APK package name did not match this app")
            return VerifiedApk(null, "安装包校验失败：文件不是 HyperDragShare 安装包，请重新下载。")
        }
        if (archive.versionName != release.versionName) {
            return VerifiedApk(null, "安装包版本与当前正式版不匹配，请重新下载。")
        }
        val installedVersionCode = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        if (archive.longVersionCode <= installedVersionCode) {
            return VerifiedApk(null, "已阻止安装：下载包版本不高于当前版本 ${BuildConfig.VERSION_NAME}。")
        }
        VerifiedApk(FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.updates", file))
    } catch (error: SecurityException) {
        Log.w(UPDATE_TAG, "Could not verify downloaded APK", error)
        VerifiedApk(null, "系统拒绝读取安装包，请重新下载或检查文件权限。")
    } catch (error: IllegalArgumentException) {
        Log.w(UPDATE_TAG, "Downloaded APK or its content URI was invalid", error)
        VerifiedApk(null, "安装包文件无效，请重新下载。")
    } catch (error: android.content.pm.PackageManager.NameNotFoundException) {
        Log.w(UPDATE_TAG, "Could not inspect installed package version", error)
        VerifiedApk(null, "无法核对当前应用版本，请稍后重试。")
    }
}

private fun launchSystemInstaller(context: Context, uri: Uri, onMessage: (String) -> Unit) {
    if (!context.packageManager.canRequestPackageInstalls()) {
        try {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            onMessage("请在系统设置中允许本应用安装未知应用，返回后再点“安装”。")
        } catch (error: ActivityNotFoundException) {
            Log.w(UPDATE_TAG, "Unknown-app install settings were unavailable", error)
            onMessage("无法打开安装授权设置，请到系统设置中允许本应用安装未知应用。")
        } catch (error: SecurityException) {
            Log.w(UPDATE_TAG, "Opening unknown-app install settings was denied", error)
            onMessage("无法打开安装授权设置，请检查系统设置权限。")
        }
        return
    }
    try {
        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(uri, MIME_APK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(installIntent)
        onMessage("已交给系统安装器。若证书或版本不兼容，系统会显示原因。")
    } catch (error: IllegalArgumentException) {
        Log.w(UPDATE_TAG, "Could not open package installer URI", error)
        onMessage("无法安全打开安装包，请重新下载。")
    } catch (error: ActivityNotFoundException) {
        Log.w(UPDATE_TAG, "No package installer activity was found", error)
        onMessage("系统没有可用的安装器，请从系统文件管理器打开安装包。")
    } catch (error: SecurityException) {
        Log.w(UPDATE_TAG, "System package installer rejected APK handoff", error)
        onMessage("系统安装器拒绝打开该文件，请检查安装授权。")
    }
}

private fun deleteDownloadedUpdate(context: Context, filename: String) {
    if (File(filename).name != filename) return
    val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
    val file = File(directory, filename)
    if (file.exists() && !file.delete()) {
        Log.i(UPDATE_TAG, "Could not remove rejected update download")
    }
}

private fun formatPublishedAt(value: String): String {
    if (value.isBlank()) return "发布时间未知"
    return try {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault()).format(Instant.parse(value))
    } catch (error: DateTimeParseException) {
        "发布时间未知"
    }
}

private fun cleanReleaseNotes(markdown: String): String = markdown
    .replace(Regex("(?m)^#{1,6}\\s*"), "")
    .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
    .replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")
    .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
    .trim()

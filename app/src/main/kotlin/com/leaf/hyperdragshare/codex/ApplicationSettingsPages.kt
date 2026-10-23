package com.leaf.hyperdragshare.codex

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.android.appiconloader.AppIconLoader
import java.util.LinkedHashSet
import java.util.Locale
import kotlin.math.roundToInt

private data class AccessibilityBlacklistApp(
    val packageName: String,
    val label: String,
    val iconBitmap: Bitmap?,
    val builtInReason: String?,
    val isGameCategory: Boolean = false,
)

@Composable
internal fun AccessibilityBlacklistPage(
    context: Context,
    settings: DragShareSettings,
    onBack: () -> Unit,
    persist: (DragShareSettings) -> Unit,
) {
    var apps by remember(context) { mutableStateOf<List<AccessibilityBlacklistApp>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(context) {
        apps = null
        apps = withContext(Dispatchers.IO) {
            queryAccessibilityBlacklistApps(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "应用黑名单",
                largeTitle = "应用黑名单",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        val allApps = apps
        if (allApps == null) {
            LoadingContent(paddingValues)
        } else {
            val query = searchQuery.trim()
            val filteredApps = allApps.filter { app ->
                query.isEmpty()
                        || app.label.contains(query, ignoreCase = true)
                        || app.packageName.contains(query, ignoreCase = true)
            }
            val blacklistedApps = filteredApps.filter { app ->
                app.builtInReason != null
                        || settings.isAccessibilityPackageBlacklisted(app.packageName)
            }
            val availableApps = filteredApps.filter { app ->
                app.builtInReason == null
                        && !settings.isAccessibilityPackageBlacklisted(app.packageName)
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = paddingValues,
            ) {
                item(key = "blacklist-search") {
                    SearchBar(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        inputField = {
                            InputField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onSearch = { searchExpanded = false },
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                                label = "搜索 ${allApps.size} 个应用",
                            )
                        },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                    ) {}
                }
                if (filteredApps.isEmpty()) {
                    item(key = "blacklist-empty") {
                        Text(
                            text = "没有匹配的应用",
                            modifier = Modifier.padding(28.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    if (blacklistedApps.isNotEmpty()) {
                        item(key = "blacklist-enabled-title") {
                            SmallTitle(text = "${blacklistedApps.size} 个应用已加入黑名单")
                        }
                        items(
                            items = blacklistedApps,
                            key = { app -> "blacklist:${app.packageName}" },
                        ) { app ->
                            AccessibilityBlacklistAppRow(
                                app = app,
                                blacklisted = true,
                                onCheckedChange = { checked ->
                                    if (app.builtInReason == null) {
                                        val packages = LinkedHashSet(
                                            settings.accessibilityBlacklistedPackages,
                                        )
                                        if (checked) {
                                            packages.add(app.packageName)
                                        } else {
                                            packages.remove(app.packageName)
                                        }
                                        persist(
                                            copySettings(
                                                settings,
                                                accessibilityBlacklistedPackages = packages,
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }
                    if (availableApps.isNotEmpty()) {
                        item(key = "blacklist-available-title") {
                            SmallTitle(text = "${availableApps.size} 个应用未加入黑名单")
                        }
                        items(
                            items = availableApps,
                            key = { app -> "blacklist:${app.packageName}" },
                        ) { app ->
                            AccessibilityBlacklistAppRow(
                                app = app,
                                blacklisted = false,
                                onCheckedChange = { checked ->
                                    val packages = LinkedHashSet(
                                        settings.accessibilityBlacklistedPackages,
                                    )
                                    if (checked) {
                                        packages.add(app.packageName)
                                    } else {
                                        packages.remove(app.packageName)
                                    }
                                    persist(
                                        copySettings(
                                            settings,
                                            accessibilityBlacklistedPackages = packages,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
                item(key = "blacklist-spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
internal fun GameAppsPage(
    context: Context,
    settings: DragShareSettings,
    onBack: () -> Unit,
    persist: (DragShareSettings) -> Unit,
) {
    var apps by remember(context) { mutableStateOf<List<AccessibilityBlacklistApp>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(context) {
        apps = null
        apps = withContext(Dispatchers.IO) { queryAccessibilityBlacklistApps(context) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "手动添加游戏",
                largeTitle = "手动添加游戏",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        val allApps = apps
        if (allApps == null) {
            LoadingContent(paddingValues)
        } else {
            val query = searchQuery.trim()
            val filteredApps = allApps.filter { app ->
                query.isEmpty()
                        || app.label.contains(query, ignoreCase = true)
                        || app.packageName.contains(query, ignoreCase = true)
            }
            val recognizedGames = filteredApps.filter { app ->
                app.isGameCategory || GameCapturePolicy.isBuiltInGame(app.packageName)
            }
            val otherApps = filteredApps.filterNot { app ->
                app.isGameCategory || GameCapturePolicy.isBuiltInGame(app.packageName)
            }.sortedBy { app ->
                if (app.packageName in settings.manualGamePackages) 0 else 1
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = paddingValues,
            ) {
                item(key = "game-picker-description") {
                    Text(
                        text = "系统游戏类别和常见游戏会自动识别。也可手动选择应用；自动取词和 Hyper 手势都会避让所选应用。",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp,
                    )
                    SearchBar(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        inputField = {
                            InputField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onSearch = { searchExpanded = false },
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                                label = "搜索 ${allApps.size} 个应用",
                            )
                        },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                    ) {}
                }
                if (filteredApps.isEmpty()) {
                    item(key = "game-picker-empty") {
                        Text(
                            text = "没有匹配的应用",
                            modifier = Modifier.padding(28.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    if (recognizedGames.isNotEmpty()) {
                        item(key = "game-picker-recognized-title") {
                            SmallTitle(text = "已按类别或常见名单识别")
                        }
                        items(recognizedGames, key = { "game:${it.packageName}" }) { app ->
                            GamePickerAppRow(app, settings.manualGamePackages) { checked ->
                                val packages = LinkedHashSet(settings.manualGamePackages)
                                if (checked) packages.add(app.packageName)
                                else packages.remove(app.packageName)
                                persist(copySettings(settings, manualGamePackages = packages))
                            }
                        }
                    }
                    if (otherApps.isNotEmpty()) {
                        item(key = "game-picker-other-title") {
                            SmallTitle(text = "其他应用 · 手动添加")
                        }
                        items(otherApps, key = { "game:${it.packageName}" }) { app ->
                            GamePickerAppRow(app, settings.manualGamePackages) { checked ->
                                val packages = LinkedHashSet(settings.manualGamePackages)
                                if (checked) packages.add(app.packageName)
                                else packages.remove(app.packageName)
                                persist(copySettings(settings, manualGamePackages = packages))
                            }
                        }
                    }
                }
                item(key = "game-picker-spacer") { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun GamePickerAppRow(
    app: AccessibilityBlacklistApp,
    manualGamePackages: Set<String>,
    onCheckedChange: (Boolean) -> Unit,
) {
    val knownGame = app.isGameCategory || GameCapturePolicy.isBuiltInGame(app.packageName)
    val checked = app.packageName in manualGamePackages
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        BasicComponent(
            startAction = {
                AccessibilityBlacklistAppIcon(app, modifier = Modifier.padding(end = 10.dp))
            },
            endActions = {
                if (knownGame) {
                    Text(
                        text = "自动识别",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    Switch(checked = checked, onCheckedChange = onCheckedChange)
                }
            },
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            onClick = if (knownGame) null else { { onCheckedChange(!checked) } },
        ) {
            Text(
                text = app.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (knownGame) "已按类别或常见名单识别"
                    else if (checked) "已加入手动名单" else app.packageName,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AccessibilityBlacklistAppRow(
    app: AccessibilityBlacklistApp,
    blacklisted: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val editable = app.builtInReason == null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        BasicComponent(
            startAction = {
                AccessibilityBlacklistAppIcon(
                    app = app,
                    modifier = Modifier.padding(end = 10.dp),
                )
            },
            endActions = {
                Switch(
                    checked = blacklisted,
                    onCheckedChange = if (editable) onCheckedChange else null,
                    enabled = editable,
                )
            },
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            onClick = if (editable) {
                { onCheckedChange(!blacklisted) }
            } else {
                null
            },
        ) {
            Text(
                text = app.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.builtInReason ?: app.packageName,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AccessibilityBlacklistAppIcon(
    app: AccessibilityBlacklistApp,
    modifier: Modifier = Modifier,
) {
    if (app.iconBitmap != null) {
        Image(
            bitmap = app.iconBitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(40.dp),
        )
    } else {
        Box(
            modifier = modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = app.label.firstOrNull()?.toString() ?: "?")
        }
    }
}

@Suppress("DEPRECATION")
private fun queryAccessibilityBlacklistApps(context: Context): List<AccessibilityBlacklistApp> {
    val packageManager = context.packageManager
    val builtInReasons = AccessibilityBlacklist.builtInReasons(context)
    val packageNames = LinkedHashSet<String>()
    try {
        packageManager.getInstalledApplications(0).forEach { applicationInfo ->
            applicationInfo.packageName
                ?.takeIf { it.isNotBlank() }
                ?.let(packageNames::add)
        }
    } catch (_: Throwable) {
        // The built-in exclusions below are still shown when package queries are restricted.
    }
    packageNames.addAll(builtInReasons.keys)
    if (packageNames.isEmpty()) return emptyList()

    val targetSizePx = (48f * context.resources.displayMetrics.density)
        .roundToInt()
        .coerceAtLeast(1)
    val loader = AppIconLoader(targetSizePx, false, context.applicationContext)
    val apps = mutableListOf<AccessibilityBlacklistApp>()
    packageNames.forEach { packageName ->
        val builtInReason = builtInReasons[packageName]
        val info = try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (_: Throwable) {
            null
        }
        if (info == null) {
            if (builtInReason != null) {
                apps += AccessibilityBlacklistApp(
                    packageName = packageName,
                    label = packageName,
                    iconBitmap = null,
                    builtInReason = builtInReason,
                )
            }
            return@forEach
        }
        val icon = try {
            loader.loadIcon(info).also { it.prepareToDraw() }
        } catch (_: Throwable) {
            null
        }
        apps += AccessibilityBlacklistApp(
            packageName = packageName,
            label = info.loadLabel(packageManager).toString().ifBlank { packageName },
            iconBitmap = icon,
            builtInReason = builtInReason,
            isGameCategory = info.category == android.content.pm.ApplicationInfo.CATEGORY_GAME,
        )
    }
    return apps.sortedWith(
        compareBy<AccessibilityBlacklistApp> {
            it.label.lowercase(Locale.getDefault())
        }.thenBy { it.packageName },
    )
}

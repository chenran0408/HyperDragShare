package com.leaf.hyperdragshare.codex

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt


private sealed interface SettingsRoute : NavKey {
    data object Main : SettingsRoute
    data object Visibility : SettingsRoute
    data object Order : SettingsRoute
    data object Blacklist : SettingsRoute
    data object Games : SettingsRoute
    data object About : SettingsRoute
    data object Diagnostics : SettingsRoute
    data object ProblemDiagnostics : SettingsRoute
    data class Module(val module: SettingModule, val focusId: String? = null) : SettingsRoute
    data object BottomTargets : SettingsRoute
    data object SideTargets : SettingsRoute
    data object HoneycombLayout : SettingsRoute
    data object HoneycombTargets : SettingsRoute
    data object Licenses : SettingsRoute
}



@Composable
fun DragShareSettingsApp(context: Context, openGestures: Boolean = false) {
    var settings by remember { mutableStateOf(DragShareSettings.readLocal(context)) }
    val backStack = remember { mutableStateListOf<NavKey>(SettingsRoute.Main).apply {
        if (openGestures) add(SettingsRoute.Module(SettingModule.GESTURES))
    } }
    val settingsScrollPositions = rememberSaveable(saver = SettingsScrollPositionStore.Saver) {
        SettingsScrollPositionStore()
    }
    val mainListState = rememberLazyListState()
    val mainTopAppBarState = rememberTopAppBarState()
    val dark = settings.isDarkThemeInComposition()
    val themeController = remember(dark) {
        ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
    }

    val persist: (DragShareSettingsMutation) -> DragShareSettings = { mutation ->
        val next = updateDragShareSettingsWithNotice(context, settings, mutation)
        settings = next
        next
    }
    MiuixTheme(controller = themeController) {
        var searchQuery by rememberSaveable { mutableStateOf("") }
        val activationMonitor = rememberActivationMonitor(context)
        val searchListState = rememberLazyListState()
        val searchScope = rememberCoroutineScope()
        val openTargets: (String) -> Unit = { group ->
            backStack.add(when (group) {
                "bottom" -> SettingsRoute.BottomTargets
                "side" -> SettingsRoute.SideTargets
                "honeycomb" -> SettingsRoute.HoneycombTargets
                else -> error("Unknown gesture target group: $group")
            })
        }
        val directory = (portalSettingEntries(context, settings,
            onOpenVisibility = { backStack.add(SettingsRoute.Visibility) },
            onOpenOrder = { backStack.add(SettingsRoute.Order) },
            onOpenBlacklist = { backStack.add(SettingsRoute.Blacklist) },
            onOpenGames = { backStack.add(SettingsRoute.Games) },
            onOpenAbout = { backStack.add(SettingsRoute.About) }, persist = persist,
            onOpenProblemDiagnostics = { backStack.add(SettingsRoute.ProblemDiagnostics) },
        ) + gestureSettingEntries(context, onManageTargets = openTargets)).sortedWith(
            compareBy({ it.module.ordinal }, { it.module.groups.indexOf(it.group) }))
        val currentDirectory = rememberUpdatedState(directory)
        val currentQuery = rememberUpdatedState(searchQuery)
        val currentSettings = rememberUpdatedState(settings)
        val currentPersist = rememberUpdatedState(persist)
        val currentDark = rememberUpdatedState(dark)
        val currentActivationMonitor = rememberUpdatedState(activationMonitor)
        val settingsEntryProvider = remember(backStack) {
            entryProvider<NavKey> {
                entry(SettingsRoute.Main) {
                    SettingsHomePage(
                        context, currentSettings.value, currentDirectory.value,
                        currentQuery.value, { searchQuery = it; searchScope.launch { searchListState.scrollToItem(0) } }, mainListState, searchListState,
                        mainTopAppBarState,
                        activation = currentActivationMonitor.value,
                        onOpenDiagnostics = { backStack.add(SettingsRoute.Diagnostics) },
                        onOpenModule = { backStack.add(SettingsRoute.Module(it)) },
                        onOpenResult = { entry ->
                            if (isUnifiedDiagnosticsEntryId(entry.id)) backStack.add(SettingsRoute.ProblemDiagnostics)
                            else if (entry.enabled && entry.open != null) entry.open.invoke()
                            else backStack.add(SettingsRoute.Module(entry.module, entry.id))
                        },
                    )
                }
                entry<SettingsRoute.Module> { route ->
                    SettingsModulePage(route.module, currentDirectory.value.filter { it.module == route.module },
                        route.focusId, settingsScrollPositions, backStack.lastOrNull() == route,
                        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) })
                }
                entry(SettingsRoute.BottomTargets) {
                    GestureTargetsContent(context, "bottom", onBack = {
                        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                    })
                }
                entry(SettingsRoute.SideTargets) {
                    GestureTargetsContent(context, "side", onBack = {
                        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                    })
                }
                entry(SettingsRoute.HoneycombTargets) {
                    GestureTargetsContent(context, "honeycomb", onBack = {
                        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                    }, onEditHoneycombLayout = { backStack.add(SettingsRoute.HoneycombLayout) })
                }
                entry(SettingsRoute.HoneycombLayout) {
                    val store = remember(context) {
                        com.leaf.hyperdragshare.gestures.config.ConfigStore(context)
                    }
                    GestureHoneycombLayout(store, onBack = {
                        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                    })
                }
                entry(SettingsRoute.Visibility) {
                    VisibilityPage(
                        context = context,
                        settings = currentSettings.value,
                        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                        persist = currentPersist.value,
                    )
                }
                entry(SettingsRoute.Order) {
                    OrderPage(
                        context = context,
                        settings = currentSettings.value,
                        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                        persist = currentPersist.value,
                    )
                }
                entry(SettingsRoute.Blacklist) {
                    AccessibilityBlacklistPage(
                        context = context,
                        settings = currentSettings.value,
                        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                        persist = currentPersist.value,
                    )
                }
                entry(SettingsRoute.Games) {
                    GameAppsPage(
                        context = context,
                        settings = currentSettings.value,
                        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                        persist = currentPersist.value,
                    )
                }
                entry(SettingsRoute.About) {
                    DragShareAboutPage(
                        context = context,
                        dark = currentDark.value,
                        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                        onOpenLicenses = { backStack.add(SettingsRoute.Licenses) },
                    )
                }
                entry(SettingsRoute.Licenses) {
                    DragShareOpenSourceLicensePage(
                        dark = currentDark.value,
                        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                    )
                }
                entry(SettingsRoute.Diagnostics) {
                    ActivationDiagnosticsPage(
                        context = context,
                        monitor = currentActivationMonitor.value,
                        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                        onOpenGeneralSettings = {
                            backStack.add(SettingsRoute.Module(SettingModule.GENERAL, "runtime.reload"))
                        },
                        onOpenProblemDiagnostics = { backStack.add(SettingsRoute.ProblemDiagnostics) },
                    )
                }
                entry(SettingsRoute.ProblemDiagnostics) {
                    ProblemDiagnosticsPage(
                        context = context,
                        settings = currentSettings.value,
                        monitor = currentActivationMonitor.value,
                        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                        onOpenLogSettings = {
                            backStack.add(SettingsRoute.Module(SettingModule.GENERAL, "log.level"))
                        },
                        onOpenTouchDiagnostics = {
                            context.startActivity(
                                Intent(context, TouchProbeActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    )
                }
            }
        }
        val entries = rememberDecoratedNavEntries(
            backStack = backStack,
            entryProvider = settingsEntryProvider,
        )

        SyncSystemBars(dark)
        NavDisplay(
            entries = entries,
            modifier = Modifier.fillMaxSize(),
            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        )
    }
}

@Composable
private fun portalSettingEntries(
    context: Context,
    settings: DragShareSettings,
    onOpenVisibility: () -> Unit,
    onOpenOrder: () -> Unit,
    onOpenBlacklist: () -> Unit,
    onOpenGames: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenProblemDiagnostics: () -> Unit,
    persist: (DragShareSettingsMutation) -> DragShareSettings,
): List<SettingEntry> {
    val coroutineScope = rememberCoroutineScope()
    var copyDirectEnabled by remember(context) {
        mutableStateOf(IntegrationSettings.isCopyDirectEnabled(context))
    }
    val copyDirectBrowserChoices = remember(context) { CopyDirectBrowser.choices(context) }
    var copyDirectBrowserPackage by remember(context) {
        mutableStateOf(IntegrationSettings.getCopyDirectBrowserPackage(context))
    }
    val copyDirectBrowserUnavailable = copyDirectBrowserPackage.isNotEmpty() &&
        copyDirectBrowserChoices.none { it.packageName == copyDirectBrowserPackage }
    val copyDirectBrowserItems = remember(copyDirectBrowserChoices, copyDirectBrowserUnavailable) {
        buildList {
            if (copyDirectBrowserUnavailable) add("已选浏览器不可用，请重新选择")
            addAll(copyDirectBrowserChoices.map { it.label })
        }
    }
    val copyDirectBrowserIndex = if (copyDirectBrowserUnavailable) {
        0
    } else {
        copyDirectBrowserChoices.indexOfFirst { it.packageName == copyDirectBrowserPackage }
            .coerceAtLeast(0)
    }
    var backTapMode by remember(context) {
        mutableIntStateOf(IntegrationSettings.getBackTapMode(context))
    }
    var trustServiceRiskDisabled by remember(context) {
        mutableStateOf(IntegrationSettings.isTrustServiceRiskDetectionDisabled(context))
    }
    var selectionHapticEnabled by remember(context) {
        mutableStateOf(BigBangSettings.isSelectionHapticEnabled(context))
    }
    var exitAfterCopyEnabled by remember(context) {
        mutableStateOf(BigBangSettings.isExitAfterCopyEnabled(context))
    }
    var launcherIconEnabled by remember(context) {
        mutableStateOf(LauncherIconSettings.isEnabled(context))
    }
    var edgeTriggerDp by remember(settings.edgeTriggerDp) {
        mutableFloatStateOf(settings.edgeTriggerDp.toFloat())
    }
    var scrollSpeed by remember(settings.scrollSpeedDpPerSecond) {
        mutableFloatStateOf(settings.scrollSpeedDpPerSecond.toFloat())
    }
    var simpleOpacity by remember(settings.simpleMenuOpacityPercent) {
        mutableFloatStateOf(settings.simpleMenuOpacityPercent.toFloat())
    }
    var simpleCornerRadius by remember(settings.simpleMenuCornerRadiusDp) {
        mutableFloatStateOf(settings.simpleMenuCornerRadiusDp.toFloat())
    }
    var simpleEdgeDistance by remember(settings.simpleMenuEdgeDistanceDp) {
        mutableFloatStateOf(settings.simpleMenuEdgeDistanceDp.toFloat())
    }
    var iconOpacity by remember(settings.iconOpacityPercent) {
        mutableFloatStateOf(settings.iconOpacityPercent.toFloat())
    }
    var modernBlurRadius by remember(settings.modernBlurRadiusDp) {
        mutableFloatStateOf(settings.modernBlurRadiusDp.toFloat())
    }
    var modernGlassOpacity by remember(settings.modernGlassOpacityPercent) {
        mutableFloatStateOf(settings.modernGlassOpacityPercent.toFloat())
    }
    val cleanupPrefs = remember(context) { context.getSharedPreferences(SharedImageCleanupJob.PREFS, Context.MODE_PRIVATE) }
    var cleanupError by remember(cleanupPrefs) { mutableStateOf(cleanupPrefs.getString(SharedImageCleanupJob.LAST_ERROR, null)) }
    DisposableEffect(cleanupPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == SharedImageCleanupJob.LAST_ERROR) cleanupError = prefs.getString(key, null)
        }
        cleanupPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { cleanupPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val entries = mutableListOf<SettingEntry>()
    val dragSharingEnabled = settings.textSharingEnabled || settings.imageSharingEnabled
    val dragSharingPrerequisite = if (dragSharingEnabled) null else "先启用文字分享或图片分享"
    fun row(
        id: String, title: String, module: SettingModule, group: String,
        summary: String = "", keywords: String = "", icon: androidx.compose.ui.graphics.vector.ImageVector = settingGroupIcon(module, group),
        enabled: Boolean = true, prerequisite: String? = null, open: (() -> Unit)? = null,
        body: @Composable (String, String, Boolean) -> Unit,
    ) {
        entries += SettingEntry(id, title, module, group, summary, keywords, icon, enabled, prerequisite, open) {
            body(title, summary, it)
        }
    }
    fun navigationRow(
        id: String,
        title: String,
        module: SettingModule,
        group: String,
        summary: String,
        keywords: String = "",
        icon: androidx.compose.ui.graphics.vector.ImageVector = settingGroupIcon(module, group),
        enabled: Boolean = true,
        prerequisite: String? = null,
        onClick: () -> Unit,
    ) {
        row(id, title, module, group, summary, keywords, icon, enabled, prerequisite, onClick) {
            rowTitle, rowSummary, rowEnabled ->
            ArrowPreference(
                title = rowTitle,
                summary = rowSummary,
                enabled = rowEnabled,
                onClick = onClick,
            )
        }
    }
    entries += thumbTriggerEntries(context, settings) { timeout ->
        persist { _ ->
            setAccessibilityLongPressTimeoutMillis(timeout)
        }
    }
    row("ocr.system", "系统 AICR 识字", SettingModule.PORTAL, "触发与识别",
        summary = "图片与屏幕识字使用系统 AICR V2",
        keywords = "OCR 识字 图片 屏幕 AICR 小米", icon = MiuixIcons.Show,
    ) { title, summary, _ -> BasicComponent(title = title, summary = summary) }
    val openStash = { StashOverlayPanel.show(context); Unit }
    navigationRow("stash.open", "打开侧边中转站", SettingModule.PORTAL, "中转站",
        summary = "临时收集图文，按住内容拖出",
        icon = MiuixIcons.Folder, onClick = openStash,
    )
    row("runtime.reload", context.getString(R.string.scope_restart_title), SettingModule.GENERAL, "运行与权限",
        summary = context.getString(R.string.scope_restart_summary), keywords = "热重载 重启 作用域 Root LSPosed",
    ) { title, _, _ -> ScopeRestartPreference(context, title) }
    row("trustservice.disableRisk", "禁用风险检测", SettingModule.GENERAL, "运行与权限",
        summary = "解除“当前设备系统被篡改，存在安全隐患，无法进行连接”；需在 LSPosed 中勾选 MiTrustService",
        keywords = "MiTrustService MRM 风险 root 系统服务 Hook",
    ) { title, summary, enabled ->
        SwitchPreference(
            title = title,
            summary = summary,
            checked = trustServiceRiskDisabled,
            onCheckedChange = { disabled ->
                IntegrationSettings.setTrustServiceRiskDetectionDisabled(context, disabled)
                trustServiceRiskDisabled = disabled
                coroutineScope.launch {
                    val failure = withContext(Dispatchers.IO) {
                        try {
                            ScopeRestart.reloadTrustService()
                            null
                        } catch (error: java.io.IOException) {
                            DragShareLog.w(
                                "DragShare/TrustService",
                                "MiTrustService restart failed",
                                error,
                            )
                            error.message ?: error.javaClass.simpleName
                        }
                    }
                    Toast.makeText(
                        context,
                        if (failure == null) {
                            "设置已保存，MiTrustService 已重启"
                        } else {
                            "设置已保存，但 MiTrustService 重启失败：$failure"
                        },
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            enabled = enabled,
        )
    }
    cleanupError?.let { message ->
        row("stash.cleanupError", "上次暂存图片清理未完成", SettingModule.GENERAL, "工具与诊断", summary = message) {
            title, summary, _ -> BasicComponent(title = title, summary = summary)
        }
    }
    row("ocr.backtap", "背部轻敲启动屏幕识字", SettingModule.PORTAL, "触发与识别",
        summary = "使用系统框架作用域",
        keywords = "识字 OCR 屏幕 双击 三击",
    ) { title, summary, enabled ->
        OverlayDropdownPreference(
            title = title,
            summary = summary,
            items = listOf("关闭", "双击", "三击", "双击和三击"),
            selectedIndex = backTapMode,
            onSelectedIndexChange = { selected ->
                val previousMode = backTapMode
                val requestedMode = BackTapMode.normalize(selected)
                IntegrationSettings.setBackTapMode(context, requestedMode)
                backTapMode = requestedMode
                coroutineScope.launch {
                    val applied = withContext(Dispatchers.IO) {
                        IntegrationSettings.syncBackTapModeAsRoot(context)
                    }
                    if (!applied) {
                        IntegrationSettings.setBackTapMode(context, previousMode)
                        backTapMode = previousMode
                        Toast.makeText(
                            context,
                            "写入系统设置失败，请确认 Root 已授权",
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        Toast.makeText(
                            context,
                            "已应用到系统背部轻敲设置；下次轻敲生效，无需重启框架。",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            enabled = enabled,
        )
    }

    navigationRow("avoid.blacklist", "应用黑名单", SettingModule.PORTAL, "应用避让",
        summary = "自动取词和 Hyper 手势共用此名单；桌面、输入法的内置排除只影响取词",
        keywords = "黑名单 取词 手势 排除", icon = MiuixIcons.Blocklist,
        onClick = onOpenBlacklist,
    )

    row("avoid.games", "游戏内暂停自动触发", SettingModule.PORTAL, "应用避让",
        summary = "游戏中暂停自动取词和 Hyper 手势；手动点取、主动识屏仍可用",
        keywords = "游戏 取词 手势 避让",
    ) { title, summary, enabled ->
        SwitchPreference(
            title = title,
            summary = summary,
            checked = settings.pauseAutomaticInGames,
            onCheckedChange = { enabled ->
                persist { _ ->
                    setPauseAutomaticInGames(enabled)
                }
            },
            enabled = enabled,
        )
    }

    navigationRow("avoid.gameApps", "手动添加游戏", SettingModule.PORTAL, "应用避让",
        summary = if (settings.manualGamePackages.isEmpty()) {
            "手动选择应用，自动取词和 Hyper 手势均会避让"
        } else {
            "已选 ${settings.manualGamePackages.size} 个；自动取词和 Hyper 手势均会避让"
        },
        keywords = "游戏 名单 手动 添加",
        onClick = onOpenGames,
    )

    row("segmentation.haptic", "选词触感反馈", SettingModule.PORTAL, "分词与取用",
        summary = "手指选中或取消词语时轻振一次",
        keywords = "选词 震动 触感",
    ) { title, summary, enabled ->
        SwitchPreference(
            title = title,
            summary = summary,
            checked = selectionHapticEnabled,
            onCheckedChange = { enabled ->
                BigBangSettings.setSelectionHapticEnabled(context, enabled)
                selectionHapticEnabled = enabled
                Toast.makeText(context, "已保存：下次分词选词时生效，无需重启。", Toast.LENGTH_SHORT).show()
            },
            enabled = enabled,
        )
    }

    row("segmentation.exitCopy", "复制后退出", SettingModule.PORTAL, "分词与取用",
        summary = "复制所选文字后关闭分词面板",
        keywords = "分词 复制 退出",
    ) { title, summary, enabled ->
        SwitchPreference(
            title = title,
            summary = summary,
            checked = exitAfterCopyEnabled,
            onCheckedChange = { enabled ->
                BigBangSettings.setExitAfterCopyEnabled(context, enabled)
                exitAfterCopyEnabled = enabled
                Toast.makeText(context, "已保存：下次分词复制时生效，无需重启。", Toast.LENGTH_SHORT).show()
            },
            enabled = enabled,
        )
    }

    row("drag.text", "启用文字分享", SettingModule.PORTAL, "拖拽分享",
        summary = "长按文字时显示分享菜单",
        keywords = "文字 分享 拖拽",
    ) { title, summary, enabled ->
        SwitchPreference(
            title = title,
            summary = summary,
            checked = settings.textSharingEnabled,
            onCheckedChange = { checked ->
                persist { _ ->
                    setTextSharingEnabled(checked)
                }
            },
            enabled = enabled,
        )
    }

    row("drag.image", "启用图片分享", SettingModule.PORTAL, "拖拽分享",
        summary = "长按图片时显示分享菜单",
        keywords = "图片 分享 拖拽",
    ) { title, summary, enabled ->
        SwitchPreference(
            title = title,
            summary = summary,
            checked = settings.imageSharingEnabled,
            onCheckedChange = { checked ->
                persist { _ ->
                    setImageSharingEnabled(checked)
                }
            },
            enabled = enabled,
        )
    }

    navigationRow("drag.targets", "拖拽分享目标", SettingModule.PORTAL, "拖拽分享",
        summary = "按应用分别控制每个分享入口",
        keywords = "分享目标 应用 显示 隐藏 菜单可见性",
        icon = MiuixIcons.Show, onClick = onOpenVisibility,
    )

    navigationRow("drag.order", "拖拽分享目标排序", SettingModule.PORTAL, "拖拽分享",
        summary = "拖动应用条目调整显示顺序",
        keywords = "分享目标 应用 排序 菜单排序",
        icon = MiuixIcons.Sort, onClick = onOpenOrder,
    )

    row("stash.storage", "图片暂存", SettingModule.PORTAL, "中转站",
        summary = "分享副本满24小时后在使用时清理，保留中转站原内容",
        keywords = "图片 暂存 清理",
    ) { title, summary, enabled ->
        BasicComponent(
            title = title,
            summary = summary,
        )
    }

    row("drag.style", "拖拽样式", SettingModule.PORTAL, "拖拽菜单外观",
        summary = "现代样式含选中弹起、发光与柔光玻璃；系统不支持时使用普通模糊",
        keywords = "简洁 流光 环形 现代 选中目标 弹起 动画 发亮 柔光 玻璃",
        enabled = dragSharingEnabled, prerequisite = dragSharingPrerequisite,
    ) { title, summary, enabled ->
        OverlayDropdownPreference(
            title = title,
            summary = summary,
            items = listOf("简洁", "流光", "环形", "现代"),
            selectedIndex = settings.uiStyle.coerceIn(0, 3),
            onSelectedIndexChange = { selected ->
                persist { _ ->
                    setUiStyle(selected.coerceIn(0, 3))
                }
            },
            enabled = enabled,
        )
    }

    row("drag.position", "菜单出现位置", SettingModule.PORTAL, "拖拽菜单外观",
        summary = "靠近手：向左拖在左侧、向右拖在右侧；跟随拖拽边可切换四边",
        keywords = "菜单 顶部 底部 左侧 右侧 靠近手 跟随拖拽边 位置",
        enabled = dragSharingEnabled, prerequisite = dragSharingPrerequisite,
    ) { title, summary, enabled ->
        val positions = listOf(
            DragShareSettings.SIMPLE_MENU_POSITION_TOP,
            DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM,
            DragShareSettings.SIMPLE_MENU_POSITION_LEFT,
            DragShareSettings.SIMPLE_MENU_POSITION_RIGHT,
            DragShareSettings.SIMPLE_MENU_POSITION_NEAR_HAND,
            DragShareSettings.SIMPLE_MENU_POSITION_DRAG_EDGE,
        )
        OverlayDropdownPreference(
            title = title,
            summary = summary,
            items = listOf("顶部", "底部", "左侧", "右侧", "靠近手", "跟随拖拽边"),
            selectedIndex = settings.simpleMenuPosition.coerceIn(0, positions.lastIndex),
            onSelectedIndexChange = { selected ->
                persist { _ ->
                    setSimpleMenuPosition(positions[selected.coerceIn(0, positions.lastIndex)])
                }
            },
            enabled = enabled,
        )
    }

    row("drag.simple.opacity", "背景不透明度", SettingModule.PORTAL, "拖拽菜单外观",
        summary = "",
        keywords = "简洁 背景 透明",
        enabled = dragSharingEnabled && settings.uiStyle == DragShareSettings.STYLE_SIMPLE,
        prerequisite = dragSharingPrerequisite ?: "选择简洁拖拽样式后可调整",
    ) { title, summary, enabled ->
        SliderPreference(
            title = title,
            value = simpleOpacity,
            valueText = "${simpleOpacity.roundToInt()}%",
            valueRange = DragShareSettings.MIN_SIMPLE_MENU_OPACITY_PERCENT.toFloat()
                ..DragShareSettings.MAX_SIMPLE_MENU_OPACITY_PERCENT.toFloat(),
            steps = 15,
            showKeyPoints = true,
            keyPoints = listOf(20f, 40f, 60f, 80f, 100f),
            onValueChange = { simpleOpacity = it },
            onValueChangeFinished = {
                persist { _ ->
                    setSimpleMenuOpacityPercent(simpleOpacity.roundToInt())
                }
            },
            enabled = enabled,
        )
    }

    row("drag.simple.radius", "背景圆角", SettingModule.PORTAL, "拖拽菜单外观",
        summary = "",
        keywords = "简洁 背景 圆角",
        enabled = dragSharingEnabled && settings.uiStyle == DragShareSettings.STYLE_SIMPLE,
        prerequisite = dragSharingPrerequisite ?: "选择简洁拖拽样式后可调整",
    ) { title, summary, enabled ->
        SliderPreference(
            title = title,
            value = simpleCornerRadius,
            valueText = "${simpleCornerRadius.roundToInt()} dp",
            valueRange = DragShareSettings.MIN_SIMPLE_MENU_CORNER_RADIUS_DP.toFloat()
                ..DragShareSettings.MAX_SIMPLE_MENU_CORNER_RADIUS_DP.toFloat(),
            steps = 16,
            showKeyPoints = true,
            keyPoints = listOf(0f, 8f, 16f, 24f, 32f),
            onValueChange = { simpleCornerRadius = it },
            onValueChangeFinished = {
                persist { _ ->
                    setSimpleMenuCornerRadiusDp(simpleCornerRadius.roundToInt())
                }
            },
            enabled = enabled,
        )
    }

    row("drag.simple.edge", "菜单到边缘距离", SettingModule.PORTAL, "拖拽菜单外观",
        summary = "",
        keywords = "简洁 菜单 边缘 距离",
        enabled = dragSharingEnabled && settings.uiStyle == DragShareSettings.STYLE_SIMPLE,
        prerequisite = dragSharingPrerequisite ?: "选择简洁拖拽样式后可调整",
    ) { title, summary, enabled ->
        SliderPreference(
            title = title,
            value = simpleEdgeDistance,
            valueText = "${simpleEdgeDistance.roundToInt()} dp",
            valueRange = DragShareSettings.MIN_SIMPLE_MENU_EDGE_DISTANCE_DP.toFloat()
                ..DragShareSettings.MAX_SIMPLE_MENU_EDGE_DISTANCE_DP.toFloat(),
            steps = 16,
            showKeyPoints = true,
            keyPoints = listOf(0f, 8f, 16f, 32f, 64f),
            onValueChange = { simpleEdgeDistance = it },
            onValueChangeFinished = {
                persist { _ ->
                    setSimpleMenuEdgeDistanceDp(simpleEdgeDistance.roundToInt())
                }
            },
            enabled = enabled,
        )
    }

    row("drag.modern.blur", "模糊半径", SettingModule.PORTAL, "拖拽菜单外观",
        summary = "现代样式柔光玻璃的背景模糊强度",
        keywords = "现代 柔光 玻璃 背景 模糊",
        enabled = dragSharingEnabled && settings.uiStyle == DragShareSettings.STYLE_MODERN,
        prerequisite = dragSharingPrerequisite ?: "选择现代拖拽样式后可调整",
    ) { title, summary, enabled ->
        SliderPreference(
            title = title,
            summary = summary,
            value = modernBlurRadius,
            valueText = "${modernBlurRadius.roundToInt()} dp",
            valueRange = DragShareSettings.MIN_MODERN_BLUR_RADIUS_DP.toFloat()
                ..DragShareSettings.MAX_MODERN_BLUR_RADIUS_DP.toFloat(),
            steps = 30,
            showKeyPoints = true,
            keyPoints = listOf(0f, 20f, 40f, 60f, 100f, 150f),
            onValueChange = { modernBlurRadius = it },
            onValueChangeFinished = {
                persist { _ ->
                    setModernBlurRadiusDp(modernBlurRadius.roundToInt())
                }
            },
            enabled = enabled,
        )
    }

    row("drag.modern.opacity", "底板不透明度", SettingModule.PORTAL, "拖拽菜单外观",
        summary = "现代样式柔光玻璃底板的不透明度",
        keywords = "现代 柔光 玻璃 底板 透明 不透明度",
        enabled = dragSharingEnabled && settings.uiStyle == DragShareSettings.STYLE_MODERN,
        prerequisite = dragSharingPrerequisite ?: "选择现代拖拽样式后可调整",
    ) { title, summary, enabled ->
        SliderPreference(
            title = title,
            value = modernGlassOpacity,
            valueText = "${modernGlassOpacity.roundToInt()}%",
            valueRange = DragShareSettings.MIN_MODERN_GLASS_OPACITY_PERCENT.toFloat()
                ..DragShareSettings.MAX_MODERN_GLASS_OPACITY_PERCENT.toFloat(),
            steps = 8,
            showKeyPoints = true,
            keyPoints = listOf(68f, 76f, 84f, 92f, 100f),
            onValueChange = { modernGlassOpacity = it },
            onValueChangeFinished = {
                persist { _ ->
                    setModernGlassOpacityPercent(modernGlassOpacity.roundToInt())
                }
            },
            enabled = enabled,
        )
    }

    row("drag.icons.opacity", "图标不透明度", SettingModule.PORTAL, "拖拽菜单外观",
        summary = "同时影响应用图标和名称",
        keywords = "分享菜单 图标 透明",
        enabled = dragSharingEnabled, prerequisite = dragSharingPrerequisite,
    ) { title, summary, enabled ->
        SliderPreference(
            title = title,
            summary = summary,
            value = iconOpacity,
            valueText = "${iconOpacity.roundToInt()}%",
            valueRange = DragShareSettings.MIN_ICON_OPACITY_PERCENT.toFloat()
                ..DragShareSettings.MAX_ICON_OPACITY_PERCENT.toFloat(),
            steps = 20,
            showKeyPoints = true,
            keyPoints = listOf(0f, 25f, 50f, 75f, 100f),
            onValueChange = { iconOpacity = it },
            onValueChangeFinished = {
                persist { _ ->
                    setIconOpacityPercent(iconOpacity.roundToInt())
                }
            },
            enabled = enabled,
        )
    }

    row("drag.blockTouch", "阻止背景滑动", SettingModule.PORTAL, "拖拽触摸与滚动",
        summary = "MMGG 开始后尝试取消原页面触摸",
        keywords = "拖拽 背景 触摸 滑动",
        enabled = dragSharingEnabled, prerequisite = dragSharingPrerequisite,
    ) { title, summary, enabled ->
        SwitchPreference(
            title = title,
            summary = summary,
            checked = settings.blockBackgroundScroll,
            onCheckedChange = { checked ->
                persist { _ ->
                    setBlockBackgroundScroll(checked)
                }
            },
            enabled = enabled,
        )
    }

    row("drag.close", "离开菜单区域时关闭", SettingModule.PORTAL, "拖拽触摸与滚动",
        summary = "离开菜单或触发区域后关闭；重新进入可再次打开",
        keywords = "分享菜单 离开 关闭",
        enabled = dragSharingEnabled, prerequisite = dragSharingPrerequisite,
    ) { title, summary, enabled ->
        SwitchPreference(
            title = title,
            summary = summary,
            checked = settings.closeMenuWhenPointerLeaves,
            onCheckedChange = { checked ->
                persist { _ ->
                    setCloseMenuWhenPointerLeaves(checked)
                }
            },
            enabled = enabled,
        )
    }

    row("drag.scrollEdge", "边缘触发距离", SettingModule.PORTAL, "拖拽触摸与滚动",
        summary = "手指靠近屏幕左右边缘时开始滚动",
        keywords = "边缘 滚动 触发 距离",
        enabled = dragSharingEnabled, prerequisite = dragSharingPrerequisite,
    ) { title, summary, enabled ->
        SliderPreference(
            title = title,
            summary = summary,
            value = edgeTriggerDp,
            valueText = "${edgeTriggerDp.roundToInt()} dp",
            valueRange = DragShareSettings.MIN_EDGE_TRIGGER_DP.toFloat()
                ..DragShareSettings.MAX_EDGE_TRIGGER_DP.toFloat(),
            steps = 21,
            showKeyPoints = true,
            keyPoints = listOf(24f, 56f, 100f, 150f, 200f),
            onValueChange = { edgeTriggerDp = it },
            onValueChangeFinished = {
                persist { _ ->
                    setEdgeTriggerDp(edgeTriggerDp.roundToInt())
                }
            },
            enabled = enabled,
        )
    }

    row("drag.scrollSpeed", "滚动速度", SettingModule.PORTAL, "拖拽触摸与滚动",
        summary = "设置分享菜单横向滚动速度",
        keywords = "分享菜单 滚动 速度",
        enabled = dragSharingEnabled, prerequisite = dragSharingPrerequisite,
    ) { title, summary, enabled ->
        SliderPreference(
            title = title,
            summary = summary,
            value = scrollSpeed,
            valueText = "${scrollSpeed.roundToInt()} dp/s",
            valueRange = DragShareSettings.MIN_SCROLL_SPEED_DP_PER_SECOND.toFloat()
                ..DragShareSettings.MAX_SCROLL_SPEED_DP_PER_SECOND.toFloat(),
            steps = 26,
            showKeyPoints = true,
            keyPoints = listOf(120f, 320f, 560f, 800f, 1200f),
            onValueChange = { scrollSpeed = it },
            onValueChangeFinished = {
                persist { _ ->
                    setScrollSpeedDpPerSecond(scrollSpeed.roundToInt())
                }
            },
            enabled = enabled,
        )
    }

    row("copy.enabled", "复制直达与分词", SettingModule.COPY_DIRECT, "启用与触发",
        summary = "使用系统复制直达卡片，保留分词和指定浏览器，去除应用商店推广；需启用传送门/AICR作用域",
        keywords = "剪贴板 复制卡片 实体 识别 执行动作",
    ) { title, summary, enabled ->
        SwitchPreference(
            title = title,
            summary = summary,
            checked = copyDirectEnabled,
            onCheckedChange = { enabled ->
                IntegrationSettings.setCopyDirectEnabled(context, enabled)
                copyDirectEnabled = enabled
                if (enabled) coroutineScope.launch {
                    val ready = withContext(Dispatchers.IO) {
                        ModuleActivation.ensureCopyDirectCardReady(context)
                    }
                    Toast.makeText(
                        context,
                        if (ready) {
                            "已保存：传送门卡片和 AICR 在下次识别/点击时读取；无需重启。"
                        } else {
                            "设置已保存，但系统复制直达卡片准备失败，请查看诊断日志。"
                        },
                        if (ready) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "已保存：传送门卡片和 AICR 在下次识别/点击时读取；无需重启。",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            enabled = enabled,
        )
    }

    row("copy.browser", "复制直达浏览器", SettingModule.COPY_DIRECT, "目标应用",
        summary = if (copyDirectBrowserUnavailable) {
            "当前已选浏览器未安装；请重新选择，不会自动切换"
        } else {
            "选择复制直达打开链接时使用的浏览器"
        },
        keywords = "链接 网页 浏览器 默认",
        enabled = copyDirectEnabled,
        prerequisite = "先启用复制直达与分词",
    ) { title, summary, enabled ->
        OverlayDropdownPreference(
            title = title,
            summary = summary,
            items = copyDirectBrowserItems,
            selectedIndex = copyDirectBrowserIndex,
            onSelectedIndexChange = { selected ->
                val choiceIndex = selected - if (copyDirectBrowserUnavailable) 1 else 0
                copyDirectBrowserChoices.getOrNull(choiceIndex)?.let { choice ->
                    IntegrationSettings.setCopyDirectBrowserPackage(context, choice.packageName)
                    copyDirectBrowserPackage = choice.packageName
                    Toast.makeText(
                        context,
                        "已保存：下次复制直达打开链接时使用新浏览器，无需重启。",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            enabled = enabled,
        )
    }

    row("launcher.icon", "桌面显示应用图标", SettingModule.GENERAL, "主题与显示",
        summary = stringResource(R.string.launcher_icon_summary),
        keywords = "桌面 图标 隐藏",
    ) { title, summary, enabled ->
        SwitchPreference(
            title = title,
            summary = summary,
            checked = launcherIconEnabled,
            onCheckedChange = { enabled ->
                if (LauncherIconSettings.setEnabled(context, enabled)) {
                    launcherIconEnabled = enabled
                    Toast.makeText(
                        context,
                        if (enabled) {
                            R.string.launcher_icon_restored
                        } else {
                            R.string.launcher_icon_hidden
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        R.string.launcher_icon_change_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
            enabled = enabled,
        )
    }

    row("theme", "主题", SettingModule.GENERAL, "主题与显示",
        summary = "设置与所有悬浮面板共用",
        keywords = "深色 暗色 浅色 跟随系统",
    ) { title, summary, enabled ->
        OverlayDropdownPreference(
            title = title,
            summary = summary,
            items = listOf("浅色", "深色", "跟随系统"),
            selectedIndex = settings.colorMode,
            onSelectedIndexChange = { selected ->
                persist { _ ->
                    setColorMode(selected)
                }
            },
            enabled = enabled,
        )
    }

    val openOverlaySettings = {
        context.startActivity(Intent(
            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    navigationRow("permission.overlay", "悬浮窗权限", SettingModule.GENERAL, "运行与权限",
        summary = if (android.provider.Settings.canDrawOverlays(context)) "已授权" else "未授权，点此开启",
        keywords = "悬浮窗 权限 浮窗",
        onClick = openOverlaySettings,
    )

    row("log.level", "日志输出等级", SettingModule.GENERAL, "工具与诊断",
        summary = when (settings.logLevel) {
            DragShareSettings.LOG_LEVEL_DISABLED -> "不输出模块日志"
            DragShareSettings.LOG_LEVEL_DEBUG -> "记录运行环境与输入诊断步骤"
            else -> "记录当前常规运行信息"
        },
        keywords = "调试 信息 日志",
    ) { title, summary, enabled ->
        OverlayDropdownPreference(
            title = title,
            summary = summary,
            items = listOf("禁用", "信息（当前）", "调试"),
            selectedIndex = settings.logLevel.coerceIn(
                DragShareSettings.LOG_LEVEL_DISABLED,
                DragShareSettings.LOG_LEVEL_DEBUG,
            ),
            onSelectedIndexChange = { selected ->
                persist { _ -> setLogLevel(selected) }
                DragShareDiagnostics.captureRuntimeOnce(
                    context,
                    "logging level changed",
                    null,
                )
                DragShareDiagnostics.captureInputInventory(
                    context,
                    "logging level changed",
                    null,
                    null,
                )
            },
            enabled = enabled,
        )
    }

    row("log.destination", "日志保存位置", SettingModule.GENERAL, "工具与诊断",
        summary = if (settings.logDestination == DragShareSettings.LOG_DESTINATION_FILE) {
            "存储到 ${DragShareLog.LOG_DIRECTORY}"
        } else {
            "写入系统日志（Logcat）"
        },
        keywords = "文件 系统 Logcat 日志",
    ) { title, summary, enabled ->
        OverlayDropdownPreference(
            title = title,
            summary = summary,
            items = listOf("系统日志（当前）", "文件"),
            selectedIndex = settings.logDestination.coerceIn(
                DragShareSettings.LOG_DESTINATION_SYSTEM,
                DragShareSettings.LOG_DESTINATION_FILE,
            ),
            onSelectedIndexChange = { selected ->
                persist { _ -> setLogDestination(selected) }
                DragShareDiagnostics.captureRuntimeOnce(
                    context,
                    "logging destination changed",
                    null,
                )
                DragShareDiagnostics.captureInputInventory(
                    context,
                    "logging destination changed",
                    null,
                    null,
                )
            },
            enabled = enabled,
        )
    }

    entries += unifiedDiagnosticsEntry(onOpenProblemDiagnostics)

    row("app.update", "检查更新", SettingModule.GENERAL, "关于",
        summary = "查看正式版更新说明，下载并安装新版本",
        keywords = "更新 版本 升级 下载 安装 release", icon = MiuixIcons.Update,
    ) { title, _, enabled -> AppUpdatePreference(title = title, enabled = enabled) }
    navigationRow("about", "关于 MMGG", SettingModule.GENERAL, "关于",
        summary = "版本 ${BuildConfig.VERSION_NAME}",
        keywords = "版本 说明 许可 使用指南",
        onClick = onOpenAbout,
    )

    return entries
}


@Suppress("DEPRECATION")
@Composable
private fun SyncSystemBars(dark: Boolean) {
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val window = activity.window
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.isAppearanceLightStatusBars = !dark
        insetsController.isAppearanceLightNavigationBars = !dark
    }
}

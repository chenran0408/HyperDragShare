package com.leaf.hyperdragshare.codex

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.appiconloader.AppIconLoader
import java.util.LinkedHashSet
import java.util.Locale
import kotlin.math.roundToInt

private const val ORDER_LIST_INDEX_OFFSET = 1
private const val PORTAL_ACTIVATION_CHECK_ATTEMPTS = 45
private const val PORTAL_ACTIVATION_CHECK_INTERVAL_MS = 100L

private sealed interface SettingsRoute : NavKey {
    data object Main : SettingsRoute
    data object Visibility : SettingsRoute
    data object Order : SettingsRoute
    data object Blacklist : SettingsRoute
    data object Games : SettingsRoute
    data object About : SettingsRoute
    data class Module(val module: SettingModule, val focusId: String? = null) : SettingsRoute
    data object BottomTargets : SettingsRoute
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
    val mainListState = rememberLazyListState()
    val mainTopAppBarState = rememberTopAppBarState()
    val dark = settings.isDarkThemeInComposition()
    val themeController = remember(dark) {
        ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
    }

    val persist: (DragShareSettings) -> Unit = { next ->
        next.saveLocal(context)
        settings = next
    }
    MiuixTheme(controller = themeController) {
        var searchQuery by rememberSaveable { mutableStateOf("") }
        val searchListState = rememberLazyListState()
        val searchScope = rememberCoroutineScope()
        val openTargets: (String) -> Unit = { group ->
            backStack.add(when (group) {
                "bottom" -> SettingsRoute.BottomTargets
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
        ) + gestureSettingEntries(context, onManageTargets = openTargets)).sortedWith(
            compareBy({ it.module.ordinal }, { it.module.groups.indexOf(it.group) }))
        val currentDirectory = rememberUpdatedState(directory)
        val currentQuery = rememberUpdatedState(searchQuery)
        val currentSettings = rememberUpdatedState(settings)
        val currentPersist = rememberUpdatedState(persist)
        val currentDark = rememberUpdatedState(dark)
        val settingsEntryProvider = remember(backStack) {
            entryProvider<NavKey> {
                entry(SettingsRoute.Main) {
                    SettingsHomePage(
                        context, currentSettings.value, currentDirectory.value,
                        currentQuery.value, { searchQuery = it; searchScope.launch { searchListState.scrollToItem(0) } }, mainListState, searchListState,
                        mainTopAppBarState,
                        onOpenModule = { backStack.add(SettingsRoute.Module(it)) },
                        onOpenResult = { entry ->
                            if (entry.enabled && entry.open != null) entry.open.invoke()
                            else backStack.add(SettingsRoute.Module(entry.module, entry.id))
                        },
                    )
                }
                entry<SettingsRoute.Module> { route ->
                    SettingsModulePage(route.module, currentDirectory.value.filter { it.module == route.module },
                        route.focusId, onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) })
                }
                entry(SettingsRoute.BottomTargets) {
                    GestureTargetsContent(context, "bottom", onBack = {
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
    persist: (DragShareSettings) -> Unit,
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
    val exportLogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { destination ->
        if (destination != null) {
            coroutineScope.launch {
                val failure = withContext(Dispatchers.IO) {
                    try {
                        DragShareLog.exportTo(context, destination)
                        null
                    } catch (error: java.io.IOException) {
                        error.message ?: error.javaClass.simpleName
                    } catch (error: SecurityException) {
                        error.message ?: error.javaClass.simpleName
                    }
                }
                Toast.makeText(
                    context,
                    if (failure == null) "日志已导出" else "导出日志失败：$failure",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
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
        persist(copySettings(settings, accessibilityLongPressTimeoutMillis = timeout))
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
        summary = "实验性系统 Hook，让 MiTrustService 将 MRM 检测视为不可用；需在 LSPosed 中勾选 MiTrustService",
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
        row("stash.cleanupError", "上次暂存图片清理未完成", SettingModule.GENERAL, "日志与诊断", summary = message) {
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
                persist(copySettings(settings, pauseAutomaticInGames = enabled))
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
                persist(copySettings(settings, textSharingEnabled = checked))
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
                persist(copySettings(settings, imageSharingEnabled = checked))
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
        summary = "仅影响拖拽时的悬浮菜单",
        keywords = "简洁 流光 环形 现代",
        enabled = dragSharingEnabled, prerequisite = dragSharingPrerequisite,
    ) { title, summary, enabled ->
        OverlayDropdownPreference(
            title = title,
            summary = summary,
            items = listOf("简洁", "流光", "环形", "现代"),
            selectedIndex = settings.uiStyle.coerceIn(0, 3),
            onSelectedIndexChange = { selected ->
                persist(copySettings(settings, uiStyle = selected.coerceIn(0, 3)))
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
                persist(
                    copySettings(
                        settings,
                        simpleMenuOpacityPercent = simpleOpacity.roundToInt(),
                    ),
                )
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
                persist(
                    copySettings(
                        settings,
                        simpleMenuCornerRadiusDp = simpleCornerRadius.roundToInt(),
                    ),
                )
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
                persist(
                    copySettings(
                        settings,
                        simpleMenuEdgeDistanceDp = simpleEdgeDistance.roundToInt(),
                    ),
                )
            },
            enabled = enabled,
        )
    }

    row("drag.modern.blur", "模糊半径", SettingModule.PORTAL, "拖拽菜单外观",
        summary = "HyperOS 局部背景模糊强度",
        keywords = "现代 模糊",
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
                persist(
                    copySettings(
                        settings,
                        modernBlurRadiusDp = modernBlurRadius.roundToInt(),
                    ),
                )
            },
            enabled = enabled,
        )
    }

    row("drag.modern.opacity", "底板不透明度", SettingModule.PORTAL, "拖拽菜单外观",
        summary = "",
        keywords = "现代 底板 透明",
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
                persist(
                    copySettings(
                        settings,
                        modernGlassOpacityPercent = modernGlassOpacity.roundToInt(),
                    ),
                )
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
                persist(
                    copySettings(
                        settings,
                        iconOpacityPercent = iconOpacity.roundToInt(),
                    ),
                )
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
                persist(copySettings(settings, blockBackgroundScroll = checked))
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
                persist(
                    copySettings(
                        settings,
                        closeMenuWhenPointerLeaves = checked,
                    ),
                )
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
                persist(copySettings(settings, edgeTriggerDp = edgeTriggerDp.roundToInt()))
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
                persist(
                    copySettings(
                        settings,
                        scrollSpeedDp = scrollSpeed.roundToInt(),
                    ),
                )
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
                    if (!ready) Toast.makeText(context, "系统复制直达卡片准备失败，请查看诊断日志", Toast.LENGTH_LONG).show()
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
                persist(
                    copySettings(
                        settings,
                        colorMode = selected,
                    ),
                )
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

    row("log.level", "日志输出等级", SettingModule.GENERAL, "日志与诊断",
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
                val next = copySettings(settings, logLevel = selected)
                persist(next)
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

    row("log.destination", "日志保存位置", SettingModule.GENERAL, "日志与诊断",
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
                val next = copySettings(settings, logDestination = selected)
                persist(next)
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

    val openLogExport = { exportLogLauncher.launch(DragShareLog.exportFileName()); Unit }
    navigationRow("log.export", "导出日志", SettingModule.GENERAL, "日志与诊断",
        summary = "将当前日志保存到所选位置",
        keywords = "保存 导出 日志",
        enabled = settings.logDestination == DragShareSettings.LOG_DESTINATION_FILE,
        prerequisite = "先将日志保存位置设为文件",
        onClick = openLogExport,
    )

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

private enum class ActivationUiState {
    Checking,
    NoRoot,
    NotInjected,
    PortalServiceUnavailable,
    PortalRootUnavailable,
    RootInputConnecting,
    RootInputUnavailable,
    PressReaderConnecting,
    PressReaderUnavailable,
    Active,
}

private fun runtimeActivation(context: Context): ActivationUiState {
    val inputReady = ModuleActivation.isRootInputReady(context)
    if (!inputReady) {
        val error = ModuleActivation.rootInputError(context)
        return if (error.isEmpty()) ActivationUiState.RootInputConnecting else ActivationUiState.RootInputUnavailable
    }
    if (!ThumbTriggerSettings.read(context).enabled) return ActivationUiState.Active
    val ready = ModuleActivation.isPressReaderReady(context)
    val error = ModuleActivation.pressReaderError(context)
    return when {
        ready -> ActivationUiState.Active
        error.isNotEmpty() -> ActivationUiState.PressReaderUnavailable
        else -> ActivationUiState.PressReaderConnecting
    }
}

private fun portalActivation(context: Context): ActivationUiState = when {
    !ModuleActivation.isCurrentBuildInjected(context) -> ActivationUiState.NotInjected
    !ModuleActivation.isPortalServiceReady(context) -> ActivationUiState.PortalServiceUnavailable
    !ModuleActivation.isCurrentBuildPortalRootGranted(context) -> ActivationUiState.PortalRootUnavailable
    else -> runtimeActivation(context)
}

@Composable
internal fun ActivationStatusCard(
    context: Context,
) {
    var refreshGeneration by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(ActivationUiState.Checking) }
    val lifecycleOwner = LocalView.current.context as? LifecycleOwner

    DisposableEffect(context) {
        val preferences = context.getSharedPreferences(ModuleActivation.PREFS_NAME, Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            // Death/status reports update the card. Only entering settings requests startup.
            if (state != ActivationUiState.Checking && state != ActivationUiState.NoRoot) {
                state = portalActivation(context)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshGeneration++
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    LaunchedEffect(refreshGeneration) {
        state = ActivationUiState.Checking
        val rootAvailable = withContext(Dispatchers.IO) {
            ModuleActivation.hasRootAccess()
        }
        if (!rootAvailable) {
            state = ActivationUiState.NoRoot
            return@LaunchedEffect
        }
        var injected = ModuleActivation.isCurrentBuildInjected(context)
        var portalRootGranted = ModuleActivation.isCurrentBuildPortalRootGranted(context)
        var serviceReady = ModuleActivation.isPortalServiceReady(context)
        if (!injected || !portalRootGranted || !serviceReady) {
            val startAndProtectionReady = withContext(Dispatchers.IO) {
                ModuleActivation.requestPortalRuntimeStart(context)
            }
            if (!startAndProtectionReady) {
                Toast.makeText(
                    context,
                    "启动或运行权限未确认，请查看诊断日志",
                    Toast.LENGTH_LONG
                ).show()
            }
            repeat(PORTAL_ACTIVATION_CHECK_ATTEMPTS) {
                injected = ModuleActivation.isCurrentBuildInjected(context)
                portalRootGranted = ModuleActivation.isCurrentBuildPortalRootGranted(context)
                serviceReady = ModuleActivation.isPortalServiceReady(context)
                if (injected && portalRootGranted && serviceReady) {
                    state = portalActivation(context)
                    return@LaunchedEffect
                }
                delay(PORTAL_ACTIVATION_CHECK_INTERVAL_MS)
            }
        }
        state = portalActivation(context)
    }

    val active = state == ActivationUiState.Active
    val title = when (state) {
        ActivationUiState.Checking -> "正在检测"
        ActivationUiState.NoRoot -> "未获取 Root 权限"
        ActivationUiState.NotInjected -> "未连接传送门"
        ActivationUiState.PortalServiceUnavailable -> "传送门服务未就绪"
        ActivationUiState.PortalRootUnavailable -> "传送门 Root 通道不可用"
        ActivationUiState.RootInputConnecting -> "Root 输入正在连接"
        ActivationUiState.RootInputUnavailable -> "Root 输入不可用"
        ActivationUiState.PressReaderConnecting -> "按压取词正在连接"
        ActivationUiState.PressReaderUnavailable -> "按压取词连接中断"
        ActivationUiState.Active -> "已激活"
    }
    val summary = when (state) {
        ActivationUiState.Checking -> "正在检查运行环境"
        ActivationUiState.NoRoot -> "Root 输入通道当前不可用"
        ActivationUiState.NotInjected -> "确认作用域已启用，在通用设置中重载模块"
        ActivationUiState.PortalServiceUnavailable -> "前台服务尚未启动，请在通用设置中重载模块"
        ActivationUiState.PortalRootUnavailable -> "传送门的 Root 检测未完成或未成功，请检查作用域运行状态"
        ActivationUiState.RootInputConnecting -> "正在连接触摸输入，连接成功后才能按住取词"
        ActivationUiState.RootInputUnavailable -> ModuleActivation.rootInputError(context)
        ActivationUiState.PressReaderConnecting -> "正在准备取词连接，就绪后才能按住取词"
        ActivationUiState.PressReaderUnavailable -> ModuleActivation.pressReaderError(context)
        ActivationUiState.Active -> "传送门已加载当前模块"
    }
    val activationMethod = when (state) {
        ActivationUiState.Checking -> "正在检测"
        ActivationUiState.NoRoot -> "ROOT"
        ActivationUiState.NotInjected -> "LSPosed"
        ActivationUiState.PortalServiceUnavailable -> "传送门服务"
        ActivationUiState.PortalRootUnavailable -> "传送门 ROOT"
        ActivationUiState.RootInputConnecting, ActivationUiState.RootInputUnavailable -> "ROOT · 输入"
        ActivationUiState.PressReaderConnecting, ActivationUiState.PressReaderUnavailable -> "ROOT · 取词"
        ActivationUiState.Active -> "ROOT · LSPosed"
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (active) MiuixIcons.Ok else MiuixIcons.Info,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface)
                Text(summary, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                if (!active) Text(activationMethod, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}

@Composable
private fun VisibilityPage(
    context: Context,
    settings: DragShareSettings,
    onBack: () -> Unit,
    persist: (DragShareSettings) -> Unit,
) {
    var pageData by remember(context) { mutableStateOf<VisibilityPageData?>(null) }
    var expandedGroupKeys by remember { mutableStateOf(emptySet<String>()) }
    val scrollBehavior = MiuixScrollBehavior()
    val groups = pageData?.groups.orEmpty()
    val allKeys = pageData?.targets?.mapNotNull { it.key() }.orEmpty()
    val bulkEntry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = "全选",
                onClick = { persist(copySettings(settings, hiddenTargetKeys = emptySet())) },
            ),
            DropdownItem(
                text = "全不选",
                onClick = {
                    persist(copySettings(settings, hiddenTargetKeys = LinkedHashSet(allKeys)))
                },
            ),
            DropdownItem(
                text = "全展开",
                onClick = { expandedGroupKeys = groups.map { it.key }.toSet() },
            ),
            DropdownItem(
                text = "全折叠",
                onClick = { expandedGroupKeys = emptySet() },
            ),
        ),
    )

    LaunchedEffect(context) {
        pageData = null
        pageData = withContext(Dispatchers.IO) {
            val targets = querySettingsTargets(context)
            VisibilityPageData(
                targets = targets,
                groups = buildGroups(context, targets),
                iconBitmaps = loadSettingsIcons(context, targets),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "拖拽分享目标",
                largeTitle = "拖拽分享目标",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
                actions = {
                    if (pageData != null) {
                        OverlayIconDropdownMenu(entry = bulkEntry) {
                            Icon(
                                imageVector = MiuixIcons.More,
                                contentDescription = "批量操作",
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        if (pageData == null) {
            LoadingContent(paddingValues)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = paddingValues,
            ) {
                if (groups.isEmpty()) {
                    item(key = "visibility-empty") {
                        Text(
                            text = "没有找到可用的分享应用",
                            modifier = Modifier.padding(28.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    groups.forEach { group ->
                        item(key = "visibility-group:${group.key}") {
                            val expanded = expandedGroupKeys.contains(group.key)
                            VisibilityGroup(
                                group = group,
                                settings = settings,
                                expanded = expanded,
                                appIconBitmap = pageData?.iconBitmaps?.get(group.key),
                                onToggleExpanded = {
                                    expandedGroupKeys = if (expanded) {
                                        expandedGroupKeys - group.key
                                    } else {
                                        expandedGroupKeys + group.key
                                    }
                                },
                                onGroupCheckedChange = { checked ->
                                    val hidden = LinkedHashSet(settings.hiddenTargetKeys)
                                    group.targets.forEach { target ->
                                        if (checked) hidden.remove(target.key()) else hidden.add(target.key())
                                    }
                                    persist(copySettings(settings, hiddenTargetKeys = hidden))
                                },
                                onTargetCheckedChange = { target, checked ->
                                    val hidden = LinkedHashSet(settings.hiddenTargetKeys)
                                    if (checked) hidden.remove(target.key()) else hidden.add(target.key())
                                    persist(copySettings(settings, hiddenTargetKeys = hidden))
                                },
                            )
                        }
                    }
                }
                item(key = "visibility-spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun VisibilityGroup(
    group: TargetGroup,
    settings: DragShareSettings,
    expanded: Boolean,
    appIconBitmap: Bitmap?,
    onToggleExpanded: () -> Unit,
    onGroupCheckedChange: (Boolean) -> Unit,
    onTargetCheckedChange: (ShareTarget, Boolean) -> Unit,
) {
    val visibleCount = group.targets.count { settings.isTargetVisible(it.key()) }
    val groupState = when (visibleCount) {
        0 -> ToggleableState.Off
        group.targets.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    Column {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            onClick = onToggleExpanded,
            showIndication = true,
            insideMargin = PaddingValues(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TargetIcon(
                    target = group.targets.first(),
                    modifier = Modifier.padding(end = 10.dp),
                    size = 48.dp,
                    normalizedBitmap = appIconBitmap,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.title,
                        modifier = Modifier.basicMarquee(),
                        fontWeight = FontWeight(550),
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = "已显示${visibleCount}个",
                        modifier = Modifier.basicMarquee(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight(550),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Checkbox(
                    state = groupState,
                    onClick = {
                        onGroupCheckedChange(groupState != ToggleableState.On)
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                group.targets.forEach { target ->
                    val checked = settings.isTargetVisible(target.key())
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .width(6.dp)
                                .height(24.dp)
                                .squircleBackground(
                                    color = if (checked) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.primaryContainer
                                    },
                                    cornerRadius = 16.dp,
                                ),
                        )
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 6.dp, end = 12.dp, bottom = 6.dp),
                        ) {
                            BasicComponent(
                                startAction = {
                                    TargetIcon(
                                        target = target,
                                        modifier = Modifier.padding(end = 2.dp),
                                        size = 40.dp,
                                        normalizedBitmap = appIconBitmap,
                                    )
                                },
                                endActions = {
                                    Checkbox(
                                        state = ToggleableState(checked),
                                        onClick = { onTargetCheckedChange(target, !checked) },
                                    )
                                },
                                insideMargin = PaddingValues(horizontal = 9.dp),
                                onClick = { onTargetCheckedChange(target, !checked) },
                            ) {
                                Text(
                                    text = target.label?.toString() ?: target.key(),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = targetSummary(target),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

private data class AccessibilityBlacklistApp(
    val packageName: String,
    val label: String,
    val iconBitmap: Bitmap?,
    val builtInReason: String?,
    val isGameCategory: Boolean = false,
)

@Composable
private fun AccessibilityBlacklistPage(
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
private fun GameAppsPage(
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

@Composable
private fun OrderPage(
    context: Context,
    settings: DragShareSettings,
    onBack: () -> Unit,
    persist: (DragShareSettings) -> Unit,
) {
    val items = remember { mutableStateListOf<ShareTarget>() }
    var loading by remember { mutableStateOf(true) }
    var iconBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    val listState = rememberLazyListState()
    val currentSettings = rememberUpdatedState(settings)
    val currentPersist = rememberUpdatedState(persist)
    val dragDropState = rememberDragDropState(
        lazyListState = listState,
        onMove = { fromIndex, toIndex ->
            val from = fromIndex - ORDER_LIST_INDEX_OFFSET
            val to = (toIndex - ORDER_LIST_INDEX_OFFSET).coerceAtLeast(0)
            if (from in items.indices && to in items.indices && from != to) {
                items.add(to, items.removeAt(from))
            }
        },
        onDragFinished = {
            currentPersist.value(
                copySettings(
                    currentSettings.value,
                    targetOrder = items.map { it.key() },
                ),
            )
        },
    )
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(context, settings.hiddenTargetKeys) {
        loading = true
        val loadedSettings = settings
        val (queried, loadedIcons) = withContext(Dispatchers.IO) {
            val targets = querySettingsTargets(context)
                .filterNot { it.isBuiltIn() }
                .filter { loadedSettings.isTargetVisible(it.key()) }
            targets to loadSettingsIcons(context, targets)
        }
        items.clear()
        items.addAll(ShareTargetRepository.orderForSettings(queried, loadedSettings))
        iconBitmaps = loadedIcons
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "拖拽分享目标排序",
                largeTitle = "拖拽分享目标排序",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        if (loading) {
            LoadingContent(paddingValues)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .dragContainer(dragDropState),
                contentPadding = paddingValues,
            ) {
                item(key = "order-title") {
                    SmallTitle(text = "分享应用顺序")
                }
                if (items.isEmpty()) {
                    item(key = "order-empty") {
                        Text(
                            text = "没有找到可排序的分享应用",
                            modifier = Modifier.padding(28.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    itemsIndexed(
                        items = items,
                        key = { _, target -> target.key() ?: target.hashCode().toString() },
                    ) { index, target ->
                        DraggableItem(
                            dragDropState = dragDropState,
                            index = index + ORDER_LIST_INDEX_OFFSET,
                        ) { isDragging ->
                            Card(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 3.dp),
                                cornerRadius = if (isDragging) 16.dp else 12.dp,
                                holdDownState = isDragging,
                            ) {
                                BasicComponent(
                                    title = target.label?.toString() ?: target.key(),
                                    summary = target.packageName(),
                                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                    startAction = {
                                        TargetIcon(
                                            target = target,
                                            normalizedBitmap = iconBitmaps[target.packageName()],
                                        )
                                    },
                                    endActions = {
                                        DropdownArrowEndAction(
                                            actionColor = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                item(key = "order-spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun BackNavigationIcon(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = MiuixIcons.Back,
            contentDescription = "返回",
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

@Composable
internal fun SettingsNavigationPreference(
    title: String,
    summary: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        summary = summary,
        startAction = {
            Icon(icon, contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.padding(end = 8.dp).size(22.dp))
        },
        onClick = onClick,
    )
}

private data class TargetGroup(
    val key: String,
    val title: String,
    val targets: List<ShareTarget>,
)

private data class VisibilityPageData(
    val targets: List<ShareTarget>,
    val groups: List<TargetGroup>,
    val iconBitmaps: Map<String, Bitmap>,
)

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

private fun querySettingsTargets(context: Context): List<ShareTarget> {
    val targets = try {
        ShareTargetRepository.queryAll(context)
    } catch (_: Throwable) {
        emptyList()
    }
    return targets + listOf(
        ShareTarget.stash(context.getDrawable(R.drawable.ic_stash)),
        ShareTarget.copyTextToClipboard(ShareTargetRepository.loadCopyIcon(context)),
        ShareTarget.copyImageToClipboard(ShareTargetRepository.loadCopyIcon(context)),
        ShareTarget.saveToLocal(ShareTargetRepository.loadSaveIcon(context)),
        ShareTarget.textSegmentation(ShareTargetRepository.loadTextSegmentationIcon(context)),
        ShareTarget.imageOcr(ShareTargetRepository.loadTextSegmentationIcon(context)),
        ShareTarget.qrScan(ShareTargetRepository.loadQrScanIcon(context)),
    )
}

private fun buildGroups(context: Context, targets: List<ShareTarget>): List<TargetGroup> {
    return targets
        .groupBy { it.packageName() }
        .map { (packageName, values) ->
            TargetGroup(
                key = packageName,
                title = if (values.firstOrNull()?.isBuiltIn() == true) {
                    "内置功能"
                } else {
                    applicationLabel(context, packageName, packageName)
                },
                targets = values.sortedWith(
                    compareBy<ShareTarget>({ !it.isBuiltIn() }, { it.label?.toString() ?: it.key() }),
                ),
            )
        }
        .sortedWith(compareBy<TargetGroup>({ it.key != "builtin" }, { it.title }))
}

private fun applicationLabel(context: Context, packageName: String, fallback: String): String {
    return try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        info.loadLabel(context.packageManager).toString()
    } catch (_: Throwable) {
        fallback
    }
}

private fun loadSettingsIcons(
    context: Context,
    targets: List<ShareTarget>,
): Map<String, Bitmap> {
    val packageNames = targets
        .asSequence()
        .filterNot { it.isBuiltIn() }
        .map { it.packageName() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
    if (packageNames.isEmpty()) return emptyMap()

    val targetSizePx = (48f * context.resources.displayMetrics.density)
        .roundToInt()
        .coerceAtLeast(1)
    val loader = AppIconLoader(targetSizePx, false, context.applicationContext)
    val packageManager = context.packageManager
    val result = LinkedHashMap<String, Bitmap>()
    packageNames.forEach { packageName ->
        try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            result[packageName] = loader.loadIcon(applicationInfo).also { it.prepareToDraw() }
        } catch (_: Throwable) {
            // Keep the ResolveInfo drawable as a per-item fallback.
        }
    }
    return result
}

private fun targetSummary(target: ShareTarget): String {
    return when {
        target.isCopyTextToClipboard() -> "将文字复制到剪贴板"
        target.isCopyImageToClipboard() -> "将图片复制到剪贴板"
        target.isSaveToLocal() -> "仅在图片分享菜单中显示"
        target.isTextSegmentation() -> "仅在文字分享菜单中显示"
        target.isImageOcr() -> "识别图片文字后进入分词，仅在图片菜单中显示"
        target.isQrScan() -> "直接从图片解出二维码内容，仅在图片菜单中显示"
        else -> {
        target.component?.let { component ->
            component.className
                .removePrefix("${component.packageName}.")
        } ?: target.key()
        }
    }
}

@Composable
private fun TargetIcon(
    target: ShareTarget,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    normalizedBitmap: Bitmap? = null,
) {
    val builtInAction = target.isCopyToClipboard()
        || target.isSaveToLocal()
        || target.isTextSegmentation()
        || target.isImageOcr()
        || target.isQrScan()
    val targetSizePx = with(LocalDensity.current) {
        size.roundToPx().coerceAtLeast(1)
    }
    val bitmap = remember(target, normalizedBitmap, targetSizePx, builtInAction) {
        if (builtInAction) {
            drawableBitmap(ShareTargetRepository.iconForDisplay(target), targetSizePx)
        } else {
            normalizedBitmap ?: drawableBitmap(target.icon)
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(size),
        )
    } else {
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = target.label?.firstOrNull()?.toString() ?: "?")
        }
    }
}

private fun drawableBitmap(drawable: Drawable?, squareSizePx: Int? = null): Bitmap? {
    if (drawable == null) return null
    val snapshot = try {
        drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
    } catch (_: Throwable) {
        drawable
    }
    val width = squareSizePx ?: snapshot.intrinsicWidth.coerceAtLeast(1)
    val height = squareSizePx ?: snapshot.intrinsicHeight.coerceAtLeast(1)
    return try {
        createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            snapshot.setBounds(0, 0, width, height)
            snapshot.draw(canvas)
        }
    } catch (_: Throwable) {
        null
    }
}

private fun copySettings(
    current: DragShareSettings,
    colorMode: Int = current.colorMode,
    uiStyle: Int = current.uiStyle,
    edgeTriggerDp: Int = current.edgeTriggerDp,
    scrollSpeedDp: Int = current.scrollSpeedDpPerSecond,
    blockBackgroundScroll: Boolean = current.blockBackgroundScroll,
    contentCaptureMode: Int = current.contentCaptureMode,
    textSharingEnabled: Boolean = current.textSharingEnabled,
    imageSharingEnabled: Boolean = current.imageSharingEnabled,
    simpleMenuPosition: Int = current.simpleMenuPosition,
    simpleMenuOpacityPercent: Int = current.simpleMenuOpacityPercent,
    simpleMenuCornerRadiusDp: Int = current.simpleMenuCornerRadiusDp,
    simpleMenuEdgeDistanceDp: Int = current.simpleMenuEdgeDistanceDp,
    iconOpacityPercent: Int = current.iconOpacityPercent,
    modernBlurRadiusDp: Int = current.modernBlurRadiusDp,
    modernGlassOpacityPercent: Int = current.modernGlassOpacityPercent,
    closeMenuWhenPointerLeaves: Boolean = current.closeMenuWhenPointerLeaves,
    hiddenTargetKeys: Set<String> = current.hiddenTargetKeys,
    targetOrder: List<String> = current.targetOrder,
    accessibilityLandscapeRecognitionEnabled: Boolean =
        current.accessibilityLandscapeRecognitionEnabled,
    accessibilityBlacklistedPackages: Set<String> = current.accessibilityBlacklistedPackages,
    accessibilityLongPressTimeoutMillis: Int = current.accessibilityLongPressTimeoutMillis,
    accessibilityRecognitionSensitivityPercent: Int =
        current.accessibilityRecognitionSensitivityPercent,
    logLevel: Int = current.logLevel,
    logDestination: Int = current.logDestination,
    pauseAutomaticInGames: Boolean = current.pauseAutomaticInGames,
    manualGamePackages: Set<String> = current.manualGamePackages,
): DragShareSettings = DragShareSettings(
    colorMode,
    uiStyle,
    edgeTriggerDp,
    scrollSpeedDp,
    blockBackgroundScroll,
    textSharingEnabled,
    imageSharingEnabled,
    simpleMenuPosition,
    simpleMenuOpacityPercent,
    simpleMenuCornerRadiusDp,
    simpleMenuEdgeDistanceDp,
    iconOpacityPercent,
    closeMenuWhenPointerLeaves,
    hiddenTargetKeys,
    targetOrder,
    contentCaptureMode,
    accessibilityLandscapeRecognitionEnabled,
    accessibilityBlacklistedPackages,
    accessibilityLongPressTimeoutMillis,
    accessibilityRecognitionSensitivityPercent,
    modernBlurRadiusDp,
    modernGlassOpacityPercent,
    logLevel,
    logDestination,
    pauseAutomaticInGames,
    manualGamePackages,
)

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

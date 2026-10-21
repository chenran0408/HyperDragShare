package com.leaf.hyperdragshare.codex

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.leaf.hyperdragshare.gestures.config.AppTarget
import com.leaf.hyperdragshare.gestures.config.ConfigContract
import com.leaf.hyperdragshare.gestures.config.ConfigStore
import com.leaf.hyperdragshare.gestures.config.ShortcutIconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.TooltipDefaults
import top.yukonga.miuix.kmp.basic.PlainTooltip
import top.yukonga.miuix.kmp.basic.rememberTooltipState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt

private data class GestureTargetRow(
    val target: AppTarget,
    val label: String,
    val summary: String,
    val icon: Bitmap?,
    val systemApp: Boolean = false,
)

private data class GestureTargetPageData(
    val rows: List<GestureTargetRow>,
    val maximum: Int,
)

private enum class TargetPickerTab { Apps, Shortcuts }
private enum class AppCategory { All, User, System, Dual }

@Composable
fun GestureTargetsContent(
    context: Context,
    group: String,
    onBack: () -> Unit,
    onEditHoneycombLayout: (() -> Unit)? = null,
) {
    val appContext = remember(context) { context.applicationContext }
    val targets = remember(group) { mutableStateListOf<AppTarget>() }
    var pageData by remember(group) { mutableStateOf<GestureTargetPageData?>(null) }
    var store by remember(group) { mutableStateOf<ConfigStore?>(null) }
    var showPicker by remember(group) { mutableStateOf(false) }
    var showResetDialog by remember(group) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val validGroup = remember(group) { group in setOf("bottom", "honeycomb") }
    val pageTitle = when (group) {
        "bottom" -> "底部扇形"
        "honeycomb" -> "蜂巢目标"
        else -> "手势目标"
    }
    val moreEntry = remember {
        DropdownEntry(items = listOf(
            DropdownItem(text = "恢复本组预设目标", onClick = { showResetDialog = true }),
        ))
    }

    LaunchedEffect(appContext, group) {
        if (!validGroup) return@LaunchedEffect
        val initial = withContext(Dispatchers.IO) {
            val configStore = ConfigStore(appContext)
            val loadedTargets = readTargets(configStore, group)
            val maximum = maximumFor(configStore, group)
            val rows = loadSavedTargetRows(appContext, configStore, loadedTargets)
            Triple(configStore, loadedTargets, GestureTargetPageData(rows, maximum))
        }
        store = initial.first
        pageData = initial.third
        targets.clear()
        targets.addAll(initial.second)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = pageTitle,
                largeTitle = pageTitle,
                navigationIcon = {
                    TargetIconAction("返回", MiuixIcons.Back, onBack)
                },
                actions = {
                    if (group == "honeycomb" && onEditHoneycombLayout != null) {
                        TargetIconAction("编辑蜂巢位置", MiuixIcons.Edit, onEditHoneycombLayout)
                    }
                    if (pageData != null && validGroup) {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                            tooltip = { PlainTooltip { Text("更多目标选项") } },
                            state = rememberTooltipState(),
                        ) {
                            OverlayIconDropdownMenu(
                                entry = moreEntry,
                                modifier = Modifier.size(48.dp).semantics {
                                    contentDescription = "更多目标选项"
                                },
                                minWidth = 48.dp,
                                minHeight = 48.dp,
                            ) {
                                Icon(MiuixIcons.MoreCircle, "更多目标选项",
                                    tint = MiuixTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                    TargetIconAction(
                        description = "添加应用或快捷方式",
                        imageVector = MiuixIcons.AddCircle,
                        enabled = pageData != null && validGroup,
                        onClick = { showPicker = true },
                    )
                },
            )
        },
    ) { paddingValues ->
        Box(Modifier.fillMaxSize()) {
            if (!validGroup) {
                Text(
                    text = "无法识别这组手势目标",
                    modifier = Modifier.padding(paddingValues).padding(24.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else if (pageData == null) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            } else {
                val data = pageData!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    if (group == "honeycomb" && store != null) {
                        item(key = "honeycomb-preview") {
                            HoneycombInlineEditor(
                                configStore = store!!,
                                revision = targets.toList(),
                                modifier = Modifier.fillMaxWidth().height(400.dp),
                            )
                        }
                    }
                    item(key = "target-count") {
                        SmallTitle(
                            text = if (group == "honeycomb") "应用与快捷方式 · ${targets.size} / ${data.maximum}"
                                else "已选 ${targets.size} / ${data.maximum} · 用“上移/下移”调整顺序",
                        )
                    }
                    if (targets.isEmpty()) {
                        item(key = "target-empty") {
                            Text(
                                text = "还没有添加目标。点击右上角“添加”，可选择应用或快捷方式。",
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    } else {
                        items(targets.toList(), key = { targetKey(it) }) { target ->
                            val row = data.rows.firstOrNull { it.target == target }
                                ?: fallbackRow(target)
                            val index = targets.indexOf(target)
                            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)) {
                                BasicComponent(
                                    startAction = { TargetIcon(row, Modifier.padding(end = 12.dp)) },
                                    endActions = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (group != "honeycomb") {
                                                TargetIconAction(
                                                    description = "上移 ${row.label}",
                                                    imageVector = MiuixIcons.ExpandLess,
                                                    enabled = index > 0,
                                                    onClick = {
                                                        val moved = targets.removeAt(index)
                                                        targets.add(index - 1, moved)
                                                        saveTargets(store, group, targets, pageData) { pageData = it }
                                                    },
                                                )
                                                TargetIconAction(
                                                    description = "下移 ${row.label}",
                                                    imageVector = MiuixIcons.ExpandMore,
                                                    enabled = index < targets.lastIndex,
                                                    onClick = {
                                                        val moved = targets.removeAt(index)
                                                        targets.add(index + 1, moved)
                                                        saveTargets(store, group, targets, pageData) { pageData = it }
                                                    },
                                                )
                                            }
                                            TargetIconAction(
                                                description = "移除 ${row.label}",
                                                imageVector = MiuixIcons.Delete,
                                                enabled = group != "bottom" || targets.size > ConfigContract.MIN_FAN_MAX_TARGETS,
                                                onClick = {
                                                    targets.remove(target)
                                                    saveTargets(store, group, targets, pageData) { pageData = it }
                                                },
                                            )
                                        }
                                    },
                                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(row.label, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(row.summary, fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    item(key = "target-footer") { Spacer(Modifier.height(12.dp)) }
                }
            }

            if (showPicker && validGroup && pageData != null && store != null) {
                TargetPickerDialog(
                    context = appContext,
                    store = store!!,
                    savedTargets = targets.toList(),
                    maximum = pageData!!.maximum,
                    onDismiss = { showPicker = false },
                    onApply = { selectedRows ->
                        targets.clear()
                        val selected = selectedRows.map { it.target }.take(pageData!!.maximum)
                        targets.addAll(selected)
                        saveTargets(store, group, targets, pageData) { pageData = it }
                        pageData = pageData?.let {
                            it.copy(rows = mergeRows(it.rows + selectedRows, targets))
                        }
                        showPicker = false
                    },
                )
            }

            OverlayDialog(
                show = showResetDialog,
                title = "恢复预设目标",
                summary = "这会用当前设备可用的预设应用替换${when (group) {
                    "bottom" -> "底部扇形"
                    "honeycomb" -> "蜂巢"
                    else -> "当前手势"
                }}清单，当前排序和快捷方式会被覆盖。",
                onDismissRequest = { showResetDialog = false },
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        text = "取消",
                        modifier = Modifier.weight(1f),
                        onClick = { showResetDialog = false },
                    )
                    TextButton(
                        text = "恢复预设",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showResetDialog = false
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    val configStore = store ?: return@withContext null
                                    when (group) {
                                        "honeycomb" -> configStore.resetHoneycombTargets()
                                        else -> configStore.resetBottomTargets()
                                    }
                                    val newTargets = readTargets(configStore, group)
                                    newTargets to loadSavedTargetRows(appContext, configStore, newTargets)
                                }
                                if (result != null) {
                                    targets.clear()
                                    targets.addAll(result.first)
                                    pageData = GestureTargetPageData(result.second, maximumFor(store!!, group))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetPickerDialog(
    context: Context,
    store: ConfigStore,
    savedTargets: List<AppTarget>,
    maximum: Int,
    onDismiss: () -> Unit,
    onApply: (List<GestureTargetRow>) -> Unit,
) {
    var tab by remember { mutableStateOf(TargetPickerTab.Apps) }
    var category by remember { mutableStateOf(AppCategory.User) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var pickerRows by remember { mutableStateOf<List<GestureTargetRow>>(emptyList()) }
    val selection = remember(savedTargets) { mutableStateListOf<AppTarget>().also { it.addAll(savedTargets) } }
    val catalogChanges = remember(context, store) { Channel<Unit>(Channel.CONFLATED) }

    DisposableEffect(context, catalogChanges) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                catalogChanges.trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            ConfigContract.CATALOG_URI,
            false,
            observer,
        )
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            catalogChanges.close()
        }
    }
    LaunchedEffect(context, store, catalogChanges) {
        suspend fun reloadCatalog() {
            loading = true
            val loadedRows = withContext(Dispatchers.IO) { loadPickerRows(context, store) }
            pickerRows = loadedRows
            loading = false
        }
        reloadCatalog()
        for (unusedEvent in catalogChanges) reloadCatalog()
    }

    val filteredRows = remember(pickerRows, tab, category, query) {
        val needle = query.trim().lowercase(Locale.ROOT)
        val entries = pickerRows.filter { row ->
            val matchesType = if (tab == TargetPickerTab.Shortcuts) row.target.isShortcut
            else !row.target.isShortcut
            val matchesCategory = tab == TargetPickerTab.Shortcuts || when (category) {
                AppCategory.All -> true
                AppCategory.User -> row.target.userId == 0 && !row.systemApp
                AppCategory.System -> row.target.userId == 0 && row.systemApp
                AppCategory.Dual -> row.target.userId != 0
            }
            matchesType && matchesCategory && (needle.isEmpty()
                    || row.label.lowercase(Locale.ROOT).contains(needle)
                    || row.summary.lowercase(Locale.ROOT).contains(needle))
        }
        entries.sortedWith(compareBy<GestureTargetRow>({ !selection.contains(it.target) }, { it.label.lowercase(Locale.ROOT) }))
    }

    OverlayDialog(
        show = true,
        title = "添加目标",
        summary = "已选 ${selection.size} / $maximum",
        onDismissRequest = onDismiss,
        outsideMargin = androidx.compose.ui.unit.DpSize(16.dp, 24.dp),
        insideMargin = androidx.compose.ui.unit.DpSize(20.dp, 16.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
            val listHeight = (maxHeight - 320.dp).coerceIn(0.dp, 420.dp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TabRow(
                    tabs = listOf("应用", "快捷方式"),
                    selectedTabIndex = if (tab == TargetPickerTab.Apps) 0 else 1,
                    onTabSelected = {
                        if (it == 0) tab = TargetPickerTab.Apps else {
                            tab = TargetPickerTab.Shortcuts
                            store.requestShortcutCatalog()
                        }
                    },
                )
                if (tab == TargetPickerTab.Apps) {
                    val categories = listOf(AppCategory.All, AppCategory.User, AppCategory.System, AppCategory.Dual)
                    TabRow(
                        tabs = listOf("全部", "个人", "系统", "双开"),
                        selectedTabIndex = categories.indexOf(category),
                        onTabSelected = {
                            category = categories[it]
                            if (category == AppCategory.Dual) store.requestActivityCatalog()
                        },
                        height = 40.dp,
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "刷新目录",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        TargetIconAction(
                            description = "刷新快捷方式目录",
                            imageVector = MiuixIcons.Refresh,
                            onClick = { store.requestShortcutCatalog() },
                        )
                    }
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = if (tab == TargetPickerTab.Apps) "搜索应用" else "搜索快捷方式",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default,
                    leadingIcon = {
                        Icon(MiuixIcons.Basic.Search, contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        { TargetIconAction("清除搜索", MiuixIcons.Close, onClick = { query = "" }) }
                    } else null,
                )
                when {
                    loading -> Box(
                        Modifier.fillMaxWidth().height(listHeight),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    filteredRows.isEmpty() -> Box(
                        Modifier.fillMaxWidth().height(listHeight).padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (tab == TargetPickerTab.Shortcuts) {
                                "目录中没有可用快捷方式。请在桌面刷新目录后，再点“刷新目录”。"
                            } else "没有匹配的应用",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = listHeight),
                    ) {
                        items(filteredRows, key = { targetKey(it.target) }) { row ->
                            val checked = selection.contains(row.target)
                            val canSelect = checked || selection.size < maximum
                            BasicComponent(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = canSelect,
                                startAction = { TargetIcon(row, Modifier.padding(end = 8.dp)) },
                                endActions = {
                                    Checkbox(
                                        state = if (checked) androidx.compose.ui.state.ToggleableState.On
                                            else androidx.compose.ui.state.ToggleableState.Off,
                                        enabled = canSelect,
                                        onClick = {
                                            if (checked) selection.remove(row.target)
                                            else if (selection.size < maximum) selection.add(row.target)
                                        },
                                    )
                                },
                                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                onClick = {
                                    if (checked) selection.remove(row.target)
                                    else if (selection.size < maximum) selection.add(row.target)
                                },
                            ) {
                                Text(row.label, color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(row.summary, fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(text = "取消", onClick = onDismiss)
                    Spacer(Modifier.width(8.dp))
                    TextButton(text = "添加", enabled = selection.isNotEmpty(), onClick = {
                        onApply(selection.map { target ->
                            pickerRows.firstOrNull { it.target == target } ?: fallbackRow(target)
                        })
                    })
                }
            }
        }
    }
}

@Composable
private fun TargetIconAction(
    description: String,
    imageVector: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(48.dp).semantics { contentDescription = description },
            minWidth = 48.dp,
            minHeight = 48.dp,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = description,
                tint = if (enabled) MiuixTheme.colorScheme.onSurface
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private suspend fun loadPickerRows(context: Context, store: ConfigStore): List<GestureTargetRow> {
    val pm = context.packageManager
    val rows = LinkedHashMap<String, GestureTargetRow>()
    val iconSizePx = (44f * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    @Suppress("DEPRECATION")
    val launcherActivities = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
    )
    launcherActivities.forEach { result ->
        val info = result.activityInfo ?: return@forEach
        if (info.packageName == context.packageName) return@forEach
        val component = ComponentName(info.packageName, info.name)
        val target = AppTarget(component.flattenToString())
        val (label, labelIssue) = try {
            (result.loadLabel(pm)?.toString()?.takeIf(String::isNotBlank) ?: info.packageName) to null
        } catch (_: Resources.NotFoundException) {
            info.packageName to "名称资源不可用"
        }
        val (icon, iconIssue) = try {
            drawableToBitmap(result.loadIcon(pm), iconSizePx) to null
        } catch (_: Resources.NotFoundException) {
            null to "图标资源不可用"
        }
        rows[targetKey(target)] = GestureTargetRow(
            target = target,
            label = label,
            summary = info.packageName + (labelIssue?.let { " · $it" } ?: "") +
                (iconIssue?.let { " · $it" } ?: ""),
            icon = icon,
            systemApp = isSystemApp(info.applicationInfo),
        )
    }
    store.getActivityCatalog().forEach { activity ->
        val component = ComponentName.unflattenFromString(activity.component) ?: return@forEach
        val target = AppTarget(activity.component, activity.userId)
        if (rows.containsKey(targetKey(target))) return@forEach
        val (icon, iconIssue) = try {
            drawableToBitmap(pm.getApplicationIcon(component.packageName), iconSizePx) to null
        } catch (_: PackageManager.NameNotFoundException) {
            null to "双开应用已卸载"
        } catch (_: SecurityException) {
            null to "系统未开放双开应用图标"
        } catch (_: Resources.NotFoundException) {
            null to "图标资源不可用"
        }
        rows[targetKey(target)] = GestureTargetRow(
            target, activity.label,
            "${component.packageName} · 双开" + (iconIssue?.let { " · $it" } ?: ""), icon,
        )
    }
    appendShortcuts(context, store, rows, iconSizePx)
    return rows.values.sortedWith(compareBy(Collator.getInstance(Locale.CHINA)) { it.label })
}

private fun appendShortcuts(
    context: Context,
    store: ConfigStore,
    rows: MutableMap<String, GestureTargetRow>,
    iconSizePx: Int,
) {
    store.getShortcutCatalog().forEach { shortcut ->
        val target = if (shortcut.launcherShortcut) {
            AppTarget.launcherShortcut(shortcut.packageName, shortcut.shortcutId,
                shortcut.label, shortcut.intentUri, shortcut.userId)
        } else AppTarget.shortcut(
            shortcut.packageName, shortcut.shortcutId, shortcut.label, shortcut.userId,
        )
        val (icon, iconIssue) = try {
            drawableToBitmap(ShortcutIconLoader.load(
                context, shortcut.packageName, shortcut.shortcutId, shortcut.userId,
            ), iconSizePx) to null
        } catch (_: PackageManager.NameNotFoundException) {
            null to "快捷方式所属应用已卸载"
        } catch (_: Resources.NotFoundException) {
            null to "快捷方式图标资源不可用"
        }
        rows[targetKey(target)] = GestureTargetRow(
            target,
            shortcut.label,
            shortcut.packageName +
                (if (shortcut.launcherShortcut) " · 桌面快捷方式" else " · 快捷方式") +
                (iconIssue?.let { " · $it" } ?: ""),
            icon,
        )
    }
}

private fun loadSavedTargetRows(
    context: Context,
    store: ConfigStore,
    targets: List<AppTarget>,
): List<GestureTargetRow> {
    val pm = context.packageManager
    val shortcuts = HashMap<String, GestureTargetRow>()
    val iconSizePx = (44f * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    appendShortcuts(context, store, shortcuts, iconSizePx)
    return targets.map { target ->
        shortcuts[targetKey(target)]?.let { return@map it }
        val component = target.componentName()
        val packageName = target.packageName()
        val label: String
        val icon: Bitmap?
        var status = targetSummary(target)
        if (component != null) {
            var activityIssue: String? = null
            val resolved = try {
                pm.getActivityInfo(component, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                activityIssue = "应用活动已卸载"
                null
            } catch (_: SecurityException) {
                activityIssue = "系统未开放此应用活动"
                null
            }
            label = if (resolved == null) packageName else try {
                resolved.loadLabel(pm)?.toString() ?: packageName
            } catch (_: Resources.NotFoundException) {
                status += " · 名称资源不可用"
                packageName
            }
            icon = if (resolved == null) {
                status += " · ${activityIssue ?: "应用活动不可用"}"
                null
            } else try {
                drawableToBitmap(resolved.loadIcon(pm), iconSizePx)
            } catch (_: Resources.NotFoundException) {
                status += " · 图标资源不可用"
                null
            }
        } else {
            label = target.shortcutLabel.ifBlank { packageName }
            icon = null
            status += " · 快捷方式不在当前目录"
        }
        GestureTargetRow(target, label, status, icon)
    }
}

private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap {
    return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
    }
}

private fun isSystemApp(info: ApplicationInfo?): Boolean = info != null &&
    (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0

private fun readTargets(store: ConfigStore, group: String): List<AppTarget> = when (group) {
    "honeycomb" -> store.honeycombTargets
    else -> store.targets
}

private fun maximumFor(store: ConfigStore, group: String): Int {
    val prefs = store.preferences()
    return when (group) {
        "honeycomb" -> prefs.getInt(ConfigContract.KEY_HONEYCOMB_MAX_TARGETS,
            ConfigContract.DEFAULT_HONEYCOMB_MAX_TARGETS)
        else -> prefs.getInt(ConfigContract.KEY_FAN_MAX_TARGETS,
            ConfigContract.DEFAULT_FAN_MAX_TARGETS)
    }
}

private fun saveTargets(
    store: ConfigStore?,
    group: String,
    targets: List<AppTarget>,
    pageData: GestureTargetPageData?,
    onDataChanged: (GestureTargetPageData?) -> Unit,
) {
    val configStore = store ?: return
    when (group) {
        "honeycomb" -> configStore.setHoneycombTargets(targets)
        else -> configStore.setTargets(targets)
    }
    onDataChanged(pageData?.copy(rows = mergeRows(pageData.rows, targets)))
}

private fun mergeRows(rows: List<GestureTargetRow>, targets: List<AppTarget>): List<GestureTargetRow> {
    val byKey = rows.associateBy { targetKey(it.target) }
    return targets.map { target -> byKey[targetKey(target)] ?: fallbackRow(target) }
}

private fun fallbackRow(target: AppTarget) = GestureTargetRow(
    target,
    target.shortcutLabel.ifBlank { target.packageName() },
    targetSummary(target),
    null,
)

private fun targetSummary(target: AppTarget): String = when {
    target.isShortcut -> target.packageName() + if (target.isLauncherShortcut) " · 桌面快捷方式" else " · 快捷方式"
    target.userId != 0 -> target.packageName() + " · 双开"
    else -> target.packageName()
}

private fun targetKey(target: AppTarget): String = target.toJson().toString()

@Composable
private fun TargetIcon(row: GestureTargetRow, modifier: Modifier = Modifier) {
    val bitmap = row.icon
    if (bitmap != null) {
        Image(bitmap = bitmap.asImageBitmap(), contentDescription = null,
            modifier = modifier.size(44.dp))
    } else {
        Box(modifier = modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Text(row.label.firstOrNull()?.toString() ?: "?", color = MiuixTheme.colorScheme.primary)
        }
    }
}

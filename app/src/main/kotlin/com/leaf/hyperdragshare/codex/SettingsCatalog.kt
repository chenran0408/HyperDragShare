package com.leaf.hyperdragshare.codex

import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import java.util.Locale

internal enum class SettingModule(val title: String, val icon: ImageVector, val aliases: String = "") {
    PORTAL("传送门与大爆炸", MiuixIcons.Layers),
    GESTURES("手势增强", MiuixIcons.GridView),
    FREEFORM("小窗增强", MiuixIcons.HorizontalSplit, "悬浮窗 窗口"),
    COPY_DIRECT("复制直达", MiuixIcons.Copy),
    GENERAL("通用设置", MiuixIcons.Settings);

    val groups: List<String> get() = when (this) {
        PORTAL -> listOf("触发与识别", "应用避让", "分词与取用", "拖拽分享", "中转站", "拖拽菜单外观", "拖拽触摸与滚动")
        GESTURES -> listOf("总开关与通用", "底角触发", "侧边手势", "动作与目标", "蜂巢布局", "布局动效", "高级设置")
        FREEFORM -> listOf("启动行为", "窗体布局", "窗外操作")
        COPY_DIRECT -> listOf("启用与触发", "目标应用")
        GENERAL -> listOf("运行与权限", "主题与显示", "工具与诊断", "关于")
    }
}

/** User-facing categories for the long gesture settings list. Membership is UI-only. */
internal data class SettingGroupCategory(
    val id: String,
    val title: String,
    val summary: String,
    val groups: Set<String> = emptySet(),
    val entryIds: Set<String> = emptySet(),
)

internal val GESTURE_SETTING_CATEGORIES = listOf(
    SettingGroupCategory(
        id = "general", title = "通用设置", summary = "总开关、反馈和图标显示",
        groups = setOf("总开关与通用"),
    ),
    SettingGroupCategory(
        id = "bottom", title = "底角斜滑", summary = "屏幕底角触发、上滑和扇形动作",
        groups = setOf("底角触发"), entryIds = setOf("bottom_targets"),
    ),
    SettingGroupCategory(
        id = "side", title = "侧边滑动", summary = "左右边缘滑入、停留和列表布局",
        groups = setOf("侧边手势"), entryIds = setOf("side_targets"),
    ),
    SettingGroupCategory(
        id = "honeycomb", title = "蜂巢布局", summary = "二段蜂巢、滑选方式和应用排列",
        groups = setOf("蜂巢布局"), entryIds = setOf("honeycomb_targets"),
    ),
    SettingGroupCategory(
        id = "launch", title = "启动方式", summary = "全屏或小窗启动及关联目标",
        groups = setOf("动作与目标"),
    ),
    SettingGroupCategory(
        id = "appearance", title = "外观与布局", summary = "图标尺寸、扇形排布和动画",
        groups = setOf("布局动效", "高级设置"),
    ),
)

internal fun gestureCategoryFor(entry: SettingEntry): SettingGroupCategory? =
    GESTURE_SETTING_CATEGORIES.firstOrNull { category ->
        entry.id in category.entryIds || entry.group in category.groups
    }

internal fun entriesForGestureCategory(
    entries: List<SettingEntry>,
    category: SettingGroupCategory,
): List<SettingEntry> = entries.filter { gestureCategoryFor(it)?.id == category.id }

/** LazyColumn index including one group header before each contiguous group. */
internal fun settingEntryListIndex(entries: List<SettingEntry>, entryId: String): Int? {
    var index = 0
    entries.groupBy { it.group }.forEach { (_, rows) ->
        index++
        val rowIndex = rows.indexOfFirst { it.id == entryId }
        if (rowIndex >= 0) return index + rowIndex
        index += rows.size
    }
    return null
}

internal data class SettingsListPosition(val index: Int, val offset: Int)

/** Remembers UI scroll coordinates independently of dynamic settings content and route instances. */
internal class SettingsScrollPositionStore(
    private val positions: MutableMap<String, SettingsListPosition> = mutableMapOf(),
) {
    fun get(key: String): SettingsListPosition = positions[key] ?: SettingsListPosition(0, 0)

    fun createListState(key: String): LazyListState = get(key).let { LazyListState(it.index, it.offset) }

    fun save(key: String, index: Int, offset: Int) {
        positions[key] = SettingsListPosition(index.coerceAtLeast(0), offset.coerceAtLeast(0))
    }

    fun save(key: String, state: LazyListState) {
        save(key, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
    }

    fun snapshot(): Map<String, SettingsListPosition> = positions.toMap()

    companion object {
        val Saver = androidx.compose.runtime.saveable.mapSaver<SettingsScrollPositionStore>(
            save = { store -> store.snapshot().mapValues { (_, position) -> listOf(position.index, position.offset) } },
            restore = { saved ->
                SettingsScrollPositionStore(saved.mapValues { (_, value) ->
                    val pair = value as List<*>
                    SettingsListPosition((pair.getOrNull(0) as Number).toInt(), (pair.getOrNull(1) as Number).toInt())
                }.toMutableMap())
            },
        )
    }
}

internal fun settingGroupIcon(module: SettingModule, group: String): ImageVector = when {
    group.contains("识别") || group.contains("取词") -> MiuixIcons.Scan
    group.contains("避让") -> MiuixIcons.Blocklist
    group.contains("分词") -> MiuixIcons.Cut
    group.contains("外观") || group.contains("布局") || group.contains("动效") -> MiuixIcons.Background
    group.contains("分享") -> MiuixIcons.Share
    group.contains("中转站") -> MiuixIcons.Folder
    group.contains("滚动") || group.contains("窗外") -> MiuixIcons.Tune
    group.contains("目标") -> MiuixIcons.GridView
    group.contains("主题") -> MiuixIcons.Theme
    group.contains("权限") -> MiuixIcons.Lock
    group.contains("日志") || group.contains("诊断") -> MiuixIcons.Report
    group.contains("关于") -> MiuixIcons.Info
    group.contains("底角") -> MiuixIcons.ExpandLess
    else -> module.icon
}

/** A row is declared once: its metadata drives navigation/search and its body renders the control. */
internal data class SettingEntry(
    val id: String,
    val title: String,
    val module: SettingModule,
    val group: String,
    val summary: String = "",
    val keywords: String = "",
    val icon: ImageVector = settingGroupIcon(module, group),
    val enabled: Boolean = true,
    val prerequisite: String? = null,
    val open: (() -> Unit)? = null,
    val content: @Composable (enabled: Boolean) -> Unit,
) {
    val path: String get() = "${module.title} › $group › $title"
    fun matches(query: String): Boolean {
        val category = if (module == SettingModule.GESTURES) gestureCategoryFor(this)?.title.orEmpty() else ""
        val haystack = "$path $category ${module.aliases} $summary $keywords".lowercase(Locale.ROOT)
        return query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).all(haystack::contains)
    }
}

/** Existing searchable IDs that now lead to the single feedback and diagnostics page. */
internal val UNIFIED_DIAGNOSTICS_ENTRY_IDS = setOf(
    "problem.diagnostics",
    "log.export",
    "gesture_compatibility_report",
    "gesture_feedback",
    "touch.probe",
)

internal fun isUnifiedDiagnosticsEntryId(id: String): Boolean =
    id in UNIFIED_DIAGNOSTICS_ENTRY_IDS

internal fun unifiedDiagnosticsEntry(onOpen: () -> Unit): SettingEntry {
    val title = "问题反馈与诊断"
    val summary = "导出诊断 ZIP、触控诊断或提交问题反馈"
    return SettingEntry(
        id = "problem.diagnostics",
        title = title,
        module = SettingModule.GENERAL,
        group = "工具与诊断",
        summary = summary,
        keywords = "日志 报告 反馈 触控 touch probe export diagnostic compatibility",
        icon = MiuixIcons.Report,
        open = onOpen,
        content = { enabled ->
            top.yukonga.miuix.kmp.preference.ArrowPreference(
                title = title,
                summary = summary,
                enabled = enabled,
                onClick = onOpen,
            )
        },
    )
}

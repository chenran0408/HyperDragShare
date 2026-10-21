package com.leaf.hyperdragshare.codex

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.leaf.hyperdragshare.gestures.config.ConfigContract as C
import com.leaf.hyperdragshare.gestures.config.ConfigStore
import com.leaf.hyperdragshare.gestures.diagnostics.CompatibilityReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.basic.TextButton
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Register Hyper gesture/freeform settings for the shared searchable settings catalog. */
@Composable
internal fun gestureSettingEntries(
    context: Context,
    onManageTargets: (String) -> Unit,
): List<SettingEntry> {
    val store = remember(context) { ConfigStore(context) }
    val prefs = remember(store) { store.preferences() }
    val revision = remember(store) { mutableIntStateOf(0) }
    val localValues = remember(store) { mutableStateMapOf<String, Any>() }
    val entries = mutableListOf<SettingEntry>()
    var currentModule = SettingModule.GESTURES
    var currentGroup = "总开关与通用"

    fun dependencies(key: String): List<String> {
        if (currentModule == SettingModule.GENERAL || key == C.KEY_ENABLED) return emptyList()
        val result = mutableListOf<String>()
        if (currentModule == SettingModule.GESTURES) result += C.KEY_ENABLED
        when {
            key.startsWith("side_task_") -> result += C.KEY_SIDE_GESTURE_ENABLED
            key.startsWith("side_") && key != C.KEY_SIDE_GESTURE_ENABLED -> result += C.KEY_SIDE_GESTURE_ENABLED
            key.startsWith("honeycomb_") && key != C.KEY_HONEYCOMB_ENABLED -> result += C.KEY_HONEYCOMB_ENABLED
            key == C.KEY_BOTTOM_PORTRAIT_SECOND_STAGE_ENABLED -> {
                result += C.KEY_BOTTOM_PORTRAIT_ENABLED
                result += C.KEY_HONEYCOMB_ENABLED
            }
            key == C.KEY_BOTTOM_LANDSCAPE_SECOND_STAGE_ENABLED -> {
                result += C.KEY_BOTTOM_LANDSCAPE_ENABLED
                result += C.KEY_HONEYCOMB_ENABLED
            }
            key == C.KEY_BOTTOM_PORTRAIT_HONEYCOMB_FREEFORM -> {
                result += C.KEY_BOTTOM_PORTRAIT_ENABLED
                result += C.KEY_BOTTOM_PORTRAIT_SECOND_STAGE_ENABLED
            }
            key == C.KEY_BOTTOM_LANDSCAPE_HONEYCOMB_FREEFORM -> {
                result += C.KEY_BOTTOM_LANDSCAPE_ENABLED
                result += C.KEY_BOTTOM_LANDSCAPE_SECOND_STAGE_ENABLED
            }
        }
        if (currentModule == SettingModule.FREEFORM) {
            when (key) {
                C.KEY_CUSTOM_WINDOW_BOUNDS_ENABLED, C.KEY_OUTSIDE_ENABLED -> Unit
                C.KEY_CUSTOM_WINDOW_PORTRAIT_ENABLED,
                C.KEY_CUSTOM_WINDOW_LANDSCAPE_ENABLED -> result += C.KEY_CUSTOM_WINDOW_BOUNDS_ENABLED
                C.KEY_PORTRAIT_PROPORTIONAL_SIZE_ENABLED,
                C.KEY_WIDTH_PERCENT, C.KEY_HEIGHT_PERCENT, C.KEY_POSITION_X, C.KEY_POSITION_Y,
                C.KEY_PORTRAIT_NATIVE_SCALE_PERCENT -> {
                    result += C.KEY_CUSTOM_WINDOW_BOUNDS_ENABLED
                    result += C.KEY_CUSTOM_WINDOW_PORTRAIT_ENABLED
                }
                C.KEY_LANDSCAPE_PROPORTIONAL_SIZE_ENABLED,
                C.KEY_LANDSCAPE_WIDTH_PERCENT, C.KEY_LANDSCAPE_HEIGHT_PERCENT,
                C.KEY_LANDSCAPE_POSITION_X, C.KEY_LANDSCAPE_POSITION_Y,
                C.KEY_LANDSCAPE_NATIVE_SCALE_PERCENT -> {
                    result += C.KEY_CUSTOM_WINDOW_BOUNDS_ENABLED
                    result += C.KEY_CUSTOM_WINDOW_LANDSCAPE_ENABLED
                }
                C.KEY_OUTSIDE_PORTRAIT_ENABLED, C.KEY_OUTSIDE_LANDSCAPE_ENABLED,
                C.KEY_OUTSIDE_SINGLE_ACTION, C.KEY_OUTSIDE_DOUBLE_ACTION,
                C.KEY_OUTSIDE_TAP_WINDOW_MS -> result += C.KEY_OUTSIDE_ENABLED
            }
        }
        if (currentGroup == "布局动效") {
            when {
                key.startsWith("bottom_") && key != C.KEY_BOTTOM_TRIGGER_HAPTIC ->
                    result += C.KEY_FAN_ANIMATIONS_ENABLED
                key.startsWith("fan_") && key != C.KEY_FAN_ANIMATIONS_ENABLED && key != C.KEY_FAN_SHADOW ->
                    result += C.KEY_FAN_ANIMATIONS_ENABLED
                key.startsWith("side_") && key != C.KEY_SIDE_ANIMATIONS_ENABLED ->
                    result += C.KEY_SIDE_ANIMATIONS_ENABLED
            }
        }
        return result.distinct()
    }

    fun dependencyDefault(key: String): Boolean = when (key) {
        C.KEY_ENABLED -> C.DEFAULT_ENABLED
        C.KEY_SIDE_GESTURE_ENABLED -> C.DEFAULT_SIDE_GESTURE_ENABLED
        C.KEY_HONEYCOMB_ENABLED -> C.DEFAULT_HONEYCOMB_ENABLED
        C.KEY_BOTTOM_PORTRAIT_ENABLED -> C.DEFAULT_BOTTOM_PORTRAIT_ENABLED
        C.KEY_BOTTOM_LANDSCAPE_ENABLED -> C.DEFAULT_BOTTOM_LANDSCAPE_ENABLED
        C.KEY_BOTTOM_PORTRAIT_SECOND_STAGE_ENABLED -> C.DEFAULT_BOTTOM_PORTRAIT_SECOND_STAGE_ENABLED
        C.KEY_BOTTOM_LANDSCAPE_SECOND_STAGE_ENABLED -> C.DEFAULT_BOTTOM_LANDSCAPE_SECOND_STAGE_ENABLED
        C.KEY_CUSTOM_WINDOW_BOUNDS_ENABLED -> C.DEFAULT_CUSTOM_WINDOW_BOUNDS_ENABLED
        C.KEY_CUSTOM_WINDOW_PORTRAIT_ENABLED -> C.DEFAULT_CUSTOM_WINDOW_PORTRAIT_ENABLED
        C.KEY_CUSTOM_WINDOW_LANDSCAPE_ENABLED -> C.DEFAULT_CUSTOM_WINDOW_LANDSCAPE_ENABLED
        C.KEY_OUTSIDE_ENABLED -> C.DEFAULT_OUTSIDE_ENABLED
        C.KEY_FAN_ANIMATIONS_ENABLED -> C.DEFAULT_FAN_ANIMATIONS_ENABLED
        C.KEY_SIDE_ANIMATIONS_ENABLED -> C.DEFAULT_SIDE_ANIMATIONS_ENABLED
        else -> false
    }

    fun dependencySummary(keys: List<String>): String? = keys.takeIf { it.isNotEmpty() }
        ?.joinToString("、") { key ->
            when (key) {
                C.KEY_ENABLED -> "启用手势总开关"
                C.KEY_SIDE_GESTURE_ENABLED -> "启用侧滑手势"
                C.KEY_HONEYCOMB_ENABLED -> "启用蜂巢"
                C.KEY_BOTTOM_PORTRAIT_ENABLED -> "竖屏底角手势"
                C.KEY_BOTTOM_LANDSCAPE_ENABLED -> "横屏底角手势"
                C.KEY_BOTTOM_PORTRAIT_SECOND_STAGE_ENABLED -> "竖屏二段蜂巢"
                C.KEY_BOTTOM_LANDSCAPE_SECOND_STAGE_ENABLED -> "横屏二段蜂巢"
                C.KEY_CUSTOM_WINDOW_BOUNDS_ENABLED -> "启用自定义窗体尺寸"
                C.KEY_CUSTOM_WINDOW_PORTRAIT_ENABLED -> "启用竖屏自定义尺寸"
                C.KEY_CUSTOM_WINDOW_LANDSCAPE_ENABLED -> "启用横屏自定义尺寸"
                C.KEY_OUTSIDE_ENABLED -> "启用窗外点击"
                C.KEY_FAN_ANIMATIONS_ENABLED -> "启用扇形动效"
                C.KEY_SIDE_ANIMATIONS_ENABLED -> "启用侧滑动效"
                else -> key
            }
        }?.let { "需先启用：$it" }

    fun keywords(group: String, title: String, summary: String): String = buildString {
        append(title).append(' ').append(summary)
        when {
            group.contains("底角") || group.contains("扇形") || group == "高级设置" ||
                title.contains("扇形") || summary.contains("扇形") || title.contains("底角") -> append(" 扇形 底角 上滑 角落")
            group.contains("蜂巢") || title.contains("蜂巢") || summary.contains("蜂巢") -> append(" 蜂巢 蜂窝 网格")
            group.contains("侧滑") -> append(" 侧滑 边缘 侧边")
            group.contains("小窗") || group.contains("窗体") -> append(" 悬浮窗 小窗 窗口")
            group.contains("任务") -> append(" 最近任务 任务中心 切换")
        }
    }

    fun register(
        id: String,
        title: String,
        summary: String,
        icon: ImageVector = settingGroupIcon(currentModule, currentGroup),
        open: (() -> Unit)? = null,
        draw: @Composable (Boolean) -> Unit,
    ) {
        val required = dependencies(id)
        revision.intValue
        val registryEnabled = required.all {
            (localValues[it] as? Boolean) ?: prefs.getBoolean(it, dependencyDefault(it))
        }
        entries += SettingEntry(
            id = id,
            title = title,
            module = currentModule,
            group = currentGroup,
            summary = summary,
            keywords = keywords(currentGroup, title, summary),
            icon = icon,
            enabled = registryEnabled,
            prerequisite = dependencySummary(required),
            open = open,
            content = draw,
        )
    }

    fun bool(title: String, summary: String, key: String, default: Boolean) {
        register(key, title, summary) { enabled ->
            GestureBooleanPreference(store, prefs, localValues, revision, key, title, summary, default, enabled)
        }
    }

    fun chooser(
        title: String,
        options: List<String>,
        key: String,
        default: Int,
        booleanValue: Boolean = false,
        values: List<Int> = options.indices.toList(),
    ) {
        register(key, title, options.joinToString("、")) { enabled ->
            GestureChooserPreference(store, prefs, localValues, key, title, options, default, booleanValue, values, enabled)
        }
    }

    fun slider(title: String, key: String, min: Int, max: Int, suffix: String, speedMultiplier: Boolean = false) {
        register(key, title, if (speedMultiplier) "调整任务拖动速度" else "范围 $min–$max$suffix") { enabled ->
            GestureSliderPreference(store, prefs, localValues, revision, key, title, min, max, suffix, speedMultiplier, enabled)
        }
    }

    fun targetAction(id: String, title: String, summary: String, target: String) {
        val open = { onManageTargets(target) }
        register(id, title, summary, MiuixIcons.GridView, open) { enabled ->
            GestureActionPreference(title, summary, enabled, open)
        }
    }

    fun addUtilityEntries() {
    currentModule = SettingModule.GENERAL
    currentGroup = "公共工具"
        val reportTitle = "导出兼容性报告"
        val reportSummary = "生成设备与模块运行环境信息，不含已选应用"
        entries += SettingEntry(
            id = "gesture_compatibility_report", title = reportTitle,
            module = currentModule, group = currentGroup,
            summary = reportSummary,
            keywords = "诊断 日志 报告 导出",
            icon = MiuixIcons.Report,
            content = { enabled -> GestureReportPreference(context, store, reportTitle, reportSummary, enabled) },
        )
        val resetTitle = "恢复手势与小窗参数"
        val resetSummary = "恢复手势和小窗参数；保留已选应用、排序与快捷方式"
        entries += SettingEntry(
            id = "gesture_reset_tuning", title = resetTitle,
            module = currentModule, group = currentGroup,
            summary = resetSummary,
            keywords = "重置 默认 恢复",
            icon = MiuixIcons.Refresh,
            content = { enabled -> GestureResetPreference(context, store, localValues, revision, resetTitle, resetSummary, enabled) },
        )
        val feedbackTitle = "问题反馈"
        val feedbackSummary = "打开 GitHub 新问题页面，附上报告供你检查"
        entries += SettingEntry(
            id = "gesture_feedback", title = feedbackTitle,
            module = currentModule, group = currentGroup,
            summary = feedbackSummary,
            keywords = "帮助 兼容 故障 bug",
            icon = MiuixIcons.Help,
            content = { enabled -> GestureFeedbackPreference(context, store, feedbackTitle, feedbackSummary, enabled) },
        )
    }

    currentModule = SettingModule.GESTURES
    currentGroup = "总开关与通用"
    bool("启用手势", "总开关", C.KEY_ENABLED, C.DEFAULT_ENABLED)
    bool("触发震动", "手势成功触发时反馈", C.KEY_HAPTIC, C.DEFAULT_HAPTIC)
    bool("显示当前应用名称", "手势选择过程中显示应用名称", C.KEY_SHOW_SELECTED_APP_NAME, C.DEFAULT_SHOW_SELECTED_APP_NAME)
    bool("强制圆形图标", "将目标图标裁切为圆形", C.KEY_FORCE_CIRCULAR_ICONS, C.DEFAULT_FORCE_CIRCULAR_ICONS)
    chooser("图标旋转形变", listOf("关闭", "轻微", "标准", "明显"), C.KEY_SELECTION_TRANSFORM_LEVEL, C.DEFAULT_SELECTION_TRANSFORM_LEVEL)
    currentGroup = "底角触发"
    bool("左底角触发", "启用屏幕左下角的底角手势", C.KEY_BOTTOM_LEFT_ENABLED, C.DEFAULT_BOTTOM_LEFT_ENABLED)
    bool("右底角触发", "启用屏幕右下角的底角手势", C.KEY_BOTTOM_RIGHT_ENABLED, C.DEFAULT_BOTTOM_RIGHT_ENABLED)
    bool("竖屏底角手势", "启用竖屏底部触发", C.KEY_BOTTOM_PORTRAIT_ENABLED, C.DEFAULT_BOTTOM_PORTRAIT_ENABLED)
    bool("横屏底角手势", "启用横屏底部触发", C.KEY_BOTTOM_LANDSCAPE_ENABLED, C.DEFAULT_BOTTOM_LANDSCAPE_ENABLED)
    bool("竖屏二段蜂巢", "底角扇形外继续上滑打开蜂巢", C.KEY_BOTTOM_PORTRAIT_SECOND_STAGE_ENABLED, C.DEFAULT_BOTTOM_PORTRAIT_SECOND_STAGE_ENABLED)
    bool("横屏二段蜂巢", "底角扇形外继续上滑打开蜂巢", C.KEY_BOTTOM_LANDSCAPE_SECOND_STAGE_ENABLED, C.DEFAULT_BOTTOM_LANDSCAPE_SECOND_STAGE_ENABLED)
    currentGroup = "动作与目标"
    bool("竖屏扇形全屏启动", "第一段手势启动应用", C.KEY_BOTTOM_PORTRAIT_FULLSCREEN, C.DEFAULT_BOTTOM_PORTRAIT_FULLSCREEN)
    bool("横屏扇形全屏启动", "第一段手势启动应用", C.KEY_BOTTOM_LANDSCAPE_FULLSCREEN, C.DEFAULT_BOTTOM_LANDSCAPE_FULLSCREEN)
    bool("竖屏蜂巢以小窗启动", "第二段手势启动应用", C.KEY_BOTTOM_PORTRAIT_HONEYCOMB_FREEFORM, C.DEFAULT_BOTTOM_PORTRAIT_HONEYCOMB_FREEFORM)
    bool("横屏蜂巢以小窗启动", "第二段手势启动应用", C.KEY_BOTTOM_LANDSCAPE_HONEYCOMB_FREEFORM, C.DEFAULT_BOTTOM_LANDSCAPE_HONEYCOMB_FREEFORM)
    currentGroup = "底角触发"
    slider("触发区宽度", C.KEY_HOT_WIDTH_PERCENT, 5, 20, "%")
    slider("触发区高度", C.KEY_HOT_HEIGHT_PERCENT, 3, C.MAX_HOT_HEIGHT_PERCENT, "%")
    slider("一段扇形距离", C.KEY_TRIGGER_PERCENT, 6, 24, "%")
    slider("蜂巢停顿时间", C.KEY_BOTTOM_HONEYCOMB_SETTLE_MS, C.MIN_BOTTOM_HONEYCOMB_SETTLE_MS, C.MAX_BOTTOM_HONEYCOMB_SETTLE_MS, " ms")
    slider("图标选择距离", C.KEY_SELECTION_RADIUS_PERCENT, C.MIN_SELECTION_RADIUS_PERCENT, C.MAX_SELECTION_RADIUS_PERCENT, "%")
    currentGroup = "高级设置"
    slider("图标大小", C.KEY_ICON_SIZE_DP, C.MIN_ICON_SIZE_DP, C.MAX_ICON_SIZE_DP, " dp")
    slider("扇形目标上限", C.KEY_FAN_MAX_TARGETS, C.MIN_FAN_MAX_TARGETS, C.MAX_FAN_MAX_TARGETS, " 个")
    chooser("扇形排列", listOf("智能排布", "自定义排数"), C.KEY_FAN_LAYOUT_MODE, C.DEFAULT_FAN_LAYOUT_MODE, values = listOf(C.FAN_LAYOUT_SMART, C.FAN_LAYOUT_CUSTOM))
    slider("内排数量", C.KEY_FAN_CUSTOM_INNER_COUNT, C.MIN_FAN_CUSTOM_ROW_COUNT, C.MAX_FAN_CUSTOM_INNER_COUNT, " 个")
    slider("中排数量", C.KEY_FAN_CUSTOM_MIDDLE_COUNT, C.MIN_FAN_CUSTOM_ROW_COUNT, C.MAX_FAN_CUSTOM_MIDDLE_COUNT, " 个")
    slider("外排数量", C.KEY_FAN_CUSTOM_OUTER_COUNT, C.MIN_FAN_CUSTOM_ROW_COUNT, C.MAX_FAN_CUSTOM_OUTER_COUNT, " 个")
    currentGroup = "侧滑触发"
    bool("启用侧滑手势", "侧边向内长滑打开目标", C.KEY_SIDE_GESTURE_ENABLED, C.DEFAULT_SIDE_GESTURE_ENABLED)
    bool("竖屏侧滑", "启用竖屏侧滑", C.KEY_SIDE_PORTRAIT_ENABLED, C.DEFAULT_SIDE_PORTRAIT_ENABLED)
    bool("横屏侧滑", "启用横屏侧滑", C.KEY_SIDE_LANDSCAPE_ENABLED, C.DEFAULT_SIDE_LANDSCAPE_ENABLED)
    bool("横向向内", "允许水平长滑", C.KEY_SIDE_DIRECTION_HORIZONTAL, C.DEFAULT_SIDE_DIRECTION_HORIZONTAL)
    bool("斜向上", "允许向内斜上长滑", C.KEY_SIDE_DIRECTION_UP, C.DEFAULT_SIDE_DIRECTION_UP)
    bool("斜向下", "允许向内斜下长滑", C.KEY_SIDE_DIRECTION_DOWN, C.DEFAULT_SIDE_DIRECTION_DOWN)
    slider("顶部安全距离", C.KEY_SIDE_TOP_SAFE_MARGIN_PERCENT, 8, 35, "%")
    slider("侧滑距离", C.KEY_SIDE_TRIGGER_PERCENT, C.MIN_SIDE_TRIGGER_PERCENT, C.MAX_SIDE_TRIGGER_PERCENT, "%")
    slider("反向取消距离", C.KEY_SIDE_REVERSE_CANCEL_PERCENT, C.MIN_SIDE_REVERSE_CANCEL_PERCENT, C.MAX_SIDE_REVERSE_CANCEL_PERCENT, "%")
    bool("侧滑停留触发", "达到距离后停住才打开列表", C.KEY_SIDE_HOLD_ENABLED, C.DEFAULT_SIDE_HOLD_ENABLED)
    slider("侧滑停留时间", C.KEY_SIDE_HOLD_DELAY_MS, C.MIN_SIDE_HOLD_DELAY_MS, C.MAX_SIDE_HOLD_DELAY_MS, " ms")
    bool("跟随手指", "列表位置随手指移动", C.KEY_SIDE_FOLLOW_FINGER, C.DEFAULT_SIDE_FOLLOW_FINGER)
    bool("侧滑打开蜂巢布局", "侧滑目标使用蜂巢排列", C.KEY_SIDE_FOLLOW_HONEYCOMB, C.DEFAULT_SIDE_FOLLOW_HONEYCOMB)
    slider("侧滑应用上限", C.KEY_SIDE_MAX_TARGETS, C.MIN_SIDE_MAX_TARGETS, C.MAX_SIDE_MAX_TARGETS, " 个")
    slider("侧滑图标大小", C.KEY_SIDE_ICON_SIZE_DP, 34, 64, " dp")
    slider("侧滑环形大小", C.KEY_SIDE_RING_SIZE_PERCENT, C.MIN_SIDE_RING_SIZE_PERCENT, C.MAX_SIDE_RING_SIZE_PERCENT, "%")
    currentGroup = "动作与目标"
    chooser("竖屏侧滑启动方式", listOf("小窗", "全屏"), C.KEY_SIDE_PORTRAIT_FULLSCREEN, 0, booleanValue = true)
    chooser("横屏侧滑启动方式", listOf("小窗", "全屏"), C.KEY_SIDE_LANDSCAPE_FULLSCREEN, 0, booleanValue = true)
    chooser("竖屏侧滑布局", listOf("纵向列表", "环形", "扇形", "蜂巢", "Hyper任务中心", "HyperOS 系统任务中心"), C.KEY_SIDE_PORTRAIT_LAYOUT_MODE, C.DEFAULT_SIDE_LAYOUT_MODE, values = listOf(C.SIDE_LAYOUT_LIST, C.SIDE_LAYOUT_RING, C.SIDE_LAYOUT_FAN, C.SIDE_LAYOUT_HONEYCOMB, C.SIDE_LAYOUT_TASKS, C.SIDE_LAYOUT_SYSTEM_RECENTS))
    chooser("横屏侧滑布局", listOf("纵向列表", "环形", "扇形", "蜂巢", "Hyper任务中心", "HyperOS 系统任务中心"), C.KEY_SIDE_LANDSCAPE_LAYOUT_MODE, C.DEFAULT_SIDE_LAYOUT_MODE, values = listOf(C.SIDE_LAYOUT_LIST, C.SIDE_LAYOUT_RING, C.SIDE_LAYOUT_FAN, C.SIDE_LAYOUT_HONEYCOMB, C.SIDE_LAYOUT_TASKS, C.SIDE_LAYOUT_SYSTEM_RECENTS))
    currentGroup = "蜂巢布局"
    bool("启用蜂巢", "底角第二段或侧滑布局使用", C.KEY_HONEYCOMB_ENABLED, C.DEFAULT_HONEYCOMB_ENABLED)
    bool("横屏蜂巢", "允许横屏打开蜂巢", C.KEY_HONEYCOMB_LANDSCAPE_ENABLED, C.DEFAULT_HONEYCOMB_LANDSCAPE_ENABLED)
    chooser("操作方式", listOf("浏览模式", "按住滑选"), C.KEY_HONEYCOMB_MODE, C.DEFAULT_HONEYCOMB_MODE)
    bool("空白处点击关闭", "点击蜂巢空白区域时收起", C.KEY_HONEYCOMB_EMPTY_TAP_CLOSE, C.DEFAULT_HONEYCOMB_EMPTY_TAP_CLOSE)
    bool("跟随手指", "蜂巢中心跟随触点", C.KEY_HONEYCOMB_FOLLOW_FINGER, C.DEFAULT_HONEYCOMB_FOLLOW_FINGER)
    slider("固定水平位置", C.KEY_HONEYCOMB_FIXED_X_PERCENT, 0, 100, "%")
    slider("固定垂直位置", C.KEY_HONEYCOMB_FIXED_Y_PERCENT, 0, 100, "%")
    slider("应用数量上限", C.KEY_HONEYCOMB_MAX_TARGETS, C.MIN_HONEYCOMB_MAX_TARGETS, C.MAX_HONEYCOMB_MAX_TARGETS, " 个")
    slider("基础图标大小", C.KEY_HONEYCOMB_ICON_SIZE_DP, C.MIN_HONEYCOMB_ICON_SIZE_DP, C.MAX_HONEYCOMB_ICON_SIZE_DP, " dp")
    slider("蜂巢圆盘大小", C.KEY_HONEYCOMB_DISC_SIZE_PERCENT, C.MIN_HONEYCOMB_DISC_SIZE_PERCENT, C.MAX_HONEYCOMB_DISC_SIZE_PERCENT, "%")
    slider("蜂巢间距", C.KEY_HONEYCOMB_SPACING_DP, C.MIN_HONEYCOMB_SPACING_DP, C.MAX_HONEYCOMB_SPACING_DP, " dp")
    bool("显示当前应用名称", "按住滑选时固定显示在圆盘上方", C.KEY_HONEYCOMB_SHOW_SELECTED_NAME, C.DEFAULT_HONEYCOMB_SHOW_SELECTED_NAME)
    chooser("背景样式", listOf("系统壁纸模糊", "纯黑"), C.KEY_HONEYCOMB_BACKGROUND_STYLE, C.DEFAULT_HONEYCOMB_BACKGROUND_STYLE, values = listOf(C.HONEYCOMB_BACKGROUND_BLUR, C.HONEYCOMB_BACKGROUND_BLACK))
    slider("壁纸模糊强度", C.KEY_HONEYCOMB_BLUR_DP, 0, 60, " dp")
    slider("壁纸压暗", C.KEY_HONEYCOMB_DIM_PERCENT, 0, 60, "%")
    bool("使用当前应用背景色", "独立的纯色背景", C.KEY_HONEYCOMB_APP_BACKGROUND_ENABLED, C.DEFAULT_HONEYCOMB_APP_BACKGROUND_ENABLED)
    bool("实时模糊当前界面", "优先使用实时界面模糊背景", C.KEY_HONEYCOMB_LIVE_BLUR_ENABLED, C.DEFAULT_HONEYCOMB_LIVE_BLUR_ENABLED)
    slider("界面模糊强度", C.KEY_HONEYCOMB_LIVE_BLUR_DP, 0, 60, " dp")
    slider("界面模糊压暗", C.KEY_HONEYCOMB_BACKGROUND_DIM_PERCENT, 0, 60, "%")
    chooser("蜂巢动画速度", listOf("很快", "较快", "标准", "柔和", "慢速"), C.KEY_HONEYCOMB_ANIMATION_SPEED, C.DEFAULT_HONEYCOMB_ANIMATION_SPEED)
    chooser("惯性强度", listOf("低", "中", "高"), C.KEY_HONEYCOMB_INERTIA, C.DEFAULT_HONEYCOMB_INERTIA)
    slider("中心凸起", C.KEY_HONEYCOMB_CENTER_SCALE, C.MIN_HONEYCOMB_CENTER_SCALE, C.MAX_HONEYCOMB_CENTER_SCALE, "%")
    slider("边缘缩小", C.KEY_HONEYCOMB_EDGE_SCALE, C.MIN_HONEYCOMB_EDGE_SCALE, C.MAX_HONEYCOMB_EDGE_SCALE, "%")
    slider("选中放大", C.KEY_HONEYCOMB_SELECTION_SCALE, C.MIN_HONEYCOMB_SELECTION_SCALE, C.MAX_HONEYCOMB_SELECTION_SCALE, "%")
    bool("中心展开系统动画", "展开时从屏幕中心缩放", C.KEY_HONEYCOMB_CENTERED_SYSTEM_ANIMATION, C.DEFAULT_HONEYCOMB_CENTERED_SYSTEM_ANIMATION)
    currentGroup = "任务切换"
    chooser("任务布局", listOf("横向卡片", "横向图标"), C.KEY_SIDE_TASK_LAYOUT_MODE, C.DEFAULT_SIDE_TASK_LAYOUT_MODE)
    slider("任务数量上限", C.KEY_SIDE_TASK_MAX_COUNT, C.MIN_SIDE_TASK_MAX_COUNT, C.MAX_SIDE_TASK_MAX_COUNT, " 个")
    slider("卡片宽度", C.KEY_SIDE_TASK_CARD_WIDTH_DP, C.MIN_SIDE_TASK_CARD_WIDTH_DP, C.MAX_SIDE_TASK_CARD_WIDTH_DP, " dp")
    slider("卡片高度", C.KEY_SIDE_TASK_CARD_HEIGHT_DP, C.MIN_SIDE_TASK_CARD_HEIGHT_DP, C.MAX_SIDE_TASK_CARD_HEIGHT_DP, " dp")
    slider("卡片圆角", C.KEY_SIDE_TASK_CARD_CORNER_DP, C.MIN_SIDE_TASK_CARD_CORNER_DP, C.MAX_SIDE_TASK_CARD_CORNER_DP, " dp")
    slider("任务图标大小", C.KEY_SIDE_TASK_ICON_SIZE_DP, C.MIN_SIDE_TASK_ICON_SIZE_DP, C.MAX_SIDE_TASK_ICON_SIZE_DP, " dp")
    slider("图标间距", C.KEY_SIDE_TASK_ICON_GAP_DP, C.MIN_SIDE_TASK_ICON_GAP_DP, C.MAX_SIDE_TASK_ICON_GAP_DP, " dp")
    slider("手指偏移", C.KEY_SIDE_TASK_FINGER_OFFSET_DP, C.MIN_SIDE_TASK_FINGER_OFFSET_DP, C.MAX_SIDE_TASK_FINGER_OFFSET_DP, " dp")
    bool("反向排列最近任务", "只反转图标顺序", C.KEY_SIDE_TASK_REVERSE_ORDER, C.DEFAULT_SIDE_TASK_REVERSE_ORDER)
    bool("显示任务名称", "在任务预览中显示名称", C.KEY_SIDE_TASK_SHOW_NAME, C.DEFAULT_SIDE_TASK_SHOW_NAME)
    bool("扩大下移容错", "放宽向下拖动命中范围", C.KEY_SIDE_TASK_EXTENDED_DOWNWARD_TOLERANCE, C.DEFAULT_SIDE_TASK_EXTENDED_DOWNWARD_TOLERANCE)
    chooser("任务切换动效", listOf("苹果流畅 · 轻吸附、近似 1:1 跟手", "磁吸轨道 · 吸入、上浮与短回弹", "弹珠磁场 · 明显吸附与连带波动"), C.KEY_SIDE_TASK_MOTION_MODE, C.DEFAULT_SIDE_TASK_MOTION_MODE)
    slider("任务拖动速度", C.KEY_SIDE_TASK_SWIPE_SPEED_PERCENT, C.MIN_SIDE_TASK_SWIPE_SPEED_PERCENT, C.MAX_SIDE_TASK_SWIPE_SPEED_PERCENT, "", speedMultiplier = true)
    chooser("整体动画速度", listOf("很快", "较快", "标准", "柔和", "慢速"), C.KEY_SIDE_TASK_ANIMATION_SPEED, C.DEFAULT_SIDE_TASK_ANIMATION_SPEED)
    bool("隐藏系统任务中心清除键", "竖屏任务中心遮住清除键；横屏不生效", C.KEY_HIDE_SYSTEM_RECENTS_CLEAR, C.DEFAULT_HIDE_SYSTEM_RECENTS_CLEAR)
    currentGroup = "布局动效"
    bool("底角扇形动效", "图标呼出、选择放大并回弹", C.KEY_FAN_ANIMATIONS_ENABLED, C.DEFAULT_FAN_ANIMATIONS_ENABLED)
    bool("扇形背景阴影", "呼出时显示扇形区域的渐变暗影", C.KEY_FAN_SHADOW, C.DEFAULT_FAN_SHADOW)
    slider("底角动画速度", C.KEY_BOTTOM_ANIMATION_SPEED, C.MIN_FAN_ANIMATION_SPEED, C.MAX_FAN_ANIMATION_SPEED, "%")
    bool("底角上滑触发震动", "关闭后只取消呼出时震动，选择反馈保留", C.KEY_BOTTOM_TRIGGER_HAPTIC, C.DEFAULT_BOTTOM_TRIGGER_HAPTIC)
    slider("扇形图标呼出幅度", C.KEY_FAN_REVEAL_AMOUNT, C.MIN_FAN_REVEAL_AMOUNT, C.MAX_FAN_REVEAL_AMOUNT, "%")
    slider("扇形图标旋转幅度", C.KEY_FAN_ROTATION_DEGREES, C.MIN_FAN_ROTATION_DEGREES, C.MAX_FAN_ROTATION_DEGREES, "°")
    slider("扇形应用选择放大", C.KEY_FAN_SELECTION_SCALE_PERCENT, C.MIN_FAN_SELECTION_SCALE_PERCENT, C.MAX_FAN_SELECTION_SCALE_PERCENT, "%")
    bool("扇形应用选择蓝色圈", "显示选中应用的圆圈反馈", C.KEY_FAN_SELECTION_RING, C.DEFAULT_FAN_SELECTION_RING)
    bool("侧滑动效", "侧滑图标呼出并在选择时回弹", C.KEY_SIDE_ANIMATIONS_ENABLED, C.DEFAULT_SIDE_ANIMATIONS_ENABLED)
    slider("侧滑动画速度", C.KEY_SIDE_ANIMATION_SPEED, C.MIN_FAN_ANIMATION_SPEED, C.MAX_FAN_ANIMATION_SPEED, "%")
    slider("侧滑图标呼出幅度", C.KEY_SIDE_REVEAL_AMOUNT, C.MIN_FAN_REVEAL_AMOUNT, C.MAX_FAN_REVEAL_AMOUNT, "%")
    slider("侧滑图标旋转幅度", C.KEY_SIDE_ROTATION_DEGREES, C.MIN_FAN_ROTATION_DEGREES, C.MAX_FAN_ROTATION_DEGREES, "°")
    slider("侧滑应用选择放大", C.KEY_SIDE_SELECTION_SCALE_PERCENT, C.MIN_FAN_SELECTION_SCALE_PERCENT, C.MAX_FAN_SELECTION_SCALE_PERCENT, "%")
    bool("侧滑应用选择蓝色圈", "显示选中应用的圆圈反馈", C.KEY_SIDE_SELECTION_RING, C.DEFAULT_SIDE_SELECTION_RING)
    currentModule = SettingModule.FREEFORM
    currentGroup = "启动行为"
    bool("启用自定义窗体尺寸", "控制竖屏与横屏小窗", C.KEY_CUSTOM_WINDOW_BOUNDS_ENABLED, C.DEFAULT_CUSTOM_WINDOW_BOUNDS_ENABLED)
    bool("竖屏自定义尺寸", "使用竖屏单独配置", C.KEY_CUSTOM_WINDOW_PORTRAIT_ENABLED, C.DEFAULT_CUSTOM_WINDOW_PORTRAIT_ENABLED)
    bool("横屏自定义尺寸", "使用横屏单独配置", C.KEY_CUSTOM_WINDOW_LANDSCAPE_ENABLED, C.DEFAULT_CUSTOM_WINDOW_LANDSCAPE_ENABLED)
    currentGroup = "窗体布局"
    bool("竖屏跟随系统原生尺寸", "按系统默认尺寸比例调整", C.KEY_PORTRAIT_PROPORTIONAL_SIZE_ENABLED, C.DEFAULT_PORTRAIT_PROPORTIONAL_SIZE_ENABLED)
    slider("竖屏宽度", C.KEY_WIDTH_PERCENT, 40, 90, "%")
    slider("竖屏高度", C.KEY_HEIGHT_PERCENT, 35, 85, "%")
    slider("竖屏水平位置", C.KEY_POSITION_X, 0, 100, "%")
    slider("竖屏垂直位置", C.KEY_POSITION_Y, 0, 100, "%")
    slider("竖屏原生缩放", C.KEY_PORTRAIT_NATIVE_SCALE_PERCENT, C.MIN_NATIVE_WINDOW_SCALE_PERCENT, C.MAX_NATIVE_WINDOW_SCALE_PERCENT, "%")
    bool("横屏跟随系统原生尺寸", "按系统默认尺寸比例调整", C.KEY_LANDSCAPE_PROPORTIONAL_SIZE_ENABLED, C.DEFAULT_LANDSCAPE_PROPORTIONAL_SIZE_ENABLED)
    slider("横屏宽度", C.KEY_LANDSCAPE_WIDTH_PERCENT, C.MIN_LANDSCAPE_WIDTH_PERCENT, C.MAX_LANDSCAPE_WIDTH_PERCENT, "%")
    slider("横屏高度", C.KEY_LANDSCAPE_HEIGHT_PERCENT, 35, 85, "%")
    slider("横屏水平位置", C.KEY_LANDSCAPE_POSITION_X, 0, 100, "%")
    slider("横屏垂直位置", C.KEY_LANDSCAPE_POSITION_Y, 0, 100, "%")
    slider("横屏原生缩放", C.KEY_LANDSCAPE_NATIVE_SCALE_PERCENT, C.MIN_NATIVE_WINDOW_SCALE_PERCENT, C.MAX_NATIVE_WINDOW_SCALE_PERCENT, "%")
    currentGroup = "窗外操作"
    bool("启用窗外点击", "允许点击小窗外区域执行动作", C.KEY_OUTSIDE_ENABLED, C.DEFAULT_OUTSIDE_ENABLED)
    bool("竖屏窗外点击", "竖屏时生效", C.KEY_OUTSIDE_PORTRAIT_ENABLED, C.DEFAULT_OUTSIDE_PORTRAIT_ENABLED)
    bool("横屏窗外点击", "横屏时生效", C.KEY_OUTSIDE_LANDSCAPE_ENABLED, C.DEFAULT_OUTSIDE_LANDSCAPE_ENABLED)
    chooser("窗外单击", listOf("无操作", "关闭小窗", "挂起到右上角", "全屏", "小窗贴边"), C.KEY_OUTSIDE_SINGLE_ACTION, C.DEFAULT_OUTSIDE_SINGLE_ACTION)
    chooser("窗外双击", listOf("无操作", "关闭小窗", "挂起到右上角", "全屏", "小窗贴边"), C.KEY_OUTSIDE_DOUBLE_ACTION, C.DEFAULT_OUTSIDE_DOUBLE_ACTION)
    slider("单双击判定时间", C.KEY_OUTSIDE_TAP_WINDOW_MS, C.MIN_OUTSIDE_TAP_WINDOW_MS, C.MAX_OUTSIDE_TAP_WINDOW_MS, " ms")

    currentModule = SettingModule.GESTURES
    currentGroup = "动作与目标"
    targetAction("bottom_targets", "底角应用与快捷方式", "选择底角扇形可打开的应用和快捷方式", "bottom")
    targetAction("side_targets", "侧滑应用与快捷方式", "选择侧滑可打开的应用和快捷方式", "side")
    targetAction("honeycomb_targets", "蜂巢应用与快捷方式", "选择蜂巢可打开的应用和快捷方式", "honeycomb")
    addUtilityEntries()
    return entries
}

@Composable
private fun GestureBooleanPreference(
    store: ConfigStore,
    prefs: android.content.SharedPreferences,
    localValues: MutableMap<String, Any>,
    revision: MutableIntState,
    key: String,
    title: String,
    summary: String,
    default: Boolean,
    enabled: Boolean,
) {
    var checked by remember(key, revision.intValue) {
        mutableStateOf((localValues[key] as? Boolean) ?: prefs.getBoolean(key, default))
    }
    SwitchPreference(
        title = title,
        summary = summary,
        enabled = enabled,
        checked = checked,
        onCheckedChange = { value ->
            checked = value
            localValues[key] = value
            store.putBoolean(key, value)
        },
    )
}

@Composable
private fun GestureChooserPreference(
    store: ConfigStore,
    prefs: android.content.SharedPreferences,
    localValues: MutableMap<String, Any>,
    key: String,
    title: String,
    options: List<String>,
    default: Int,
    booleanValue: Boolean,
    values: List<Int>,
    enabled: Boolean,
) {
    val stored = if (booleanValue) {
        if ((localValues[key] as? Boolean) ?: prefs.getBoolean(key, default != 0)) 1 else 0
    } else (localValues[key] as? Int) ?: prefs.getInt(key, default)
    val selected = values.indexOf(stored).let { if (it < 0) 0 else it }
    OverlayDropdownPreference(
        title = title,
        items = options,
        selectedIndex = selected.coerceIn(options.indices),
        enabled = enabled,
        onSelectedIndexChange = { index ->
            val value = values[index]
            if (booleanValue) {
                localValues[key] = value != 0
                store.putBoolean(key, value != 0)
            } else {
                localValues[key] = value
                store.putInt(key, value)
            }
        },
    )
}

@Composable
private fun GestureSliderPreference(
    store: ConfigStore,
    prefs: android.content.SharedPreferences,
    localValues: MutableMap<String, Any>,
    revision: MutableIntState,
    key: String,
    title: String,
    min: Int,
    max: Int,
    suffix: String,
    speedMultiplier: Boolean,
    enabled: Boolean,
) {
    val currentRevision = revision.intValue
    var value by remember(key, currentRevision) {
        mutableFloatStateOf(((localValues[key] as? Int) ?: prefs.getInt(key, gestureSettingDefault(key, min))).coerceIn(min, max).toFloat())
    }
    SliderPreference(
        title = title,
        enabled = enabled,
        value = value,
        valueText = if (speedMultiplier) String.format(Locale.CHINA, "%.1f×", value / 100f)
            else "${value.toInt()}$suffix",
        valueRange = min.toFloat()..max.toFloat(),
        steps = (max - min - 1).coerceAtLeast(0),
        onValueChange = { value = it },
        onValueChangeFinished = {
            localValues[key] = value.toInt()
            store.putInt(key, value.toInt())
        },
    )
}

@Composable
private fun GestureActionPreference(
    title: String,
    summary: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ArrowPreference(title = title, summary = summary, enabled = enabled, onClick = onClick)
}

@Composable
private fun GestureReportPreference(
    context: Context,
    store: ConfigStore,
    title: String,
    summary: String,
    enabled: Boolean,
) {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { destination ->
        if (destination != null) scope.launch {
            val failure = withContext(Dispatchers.IO) {
                try {
                    val output = context.contentResolver.openOutputStream(destination, "w")
                        ?: throw IOException("无法打开所选文件")
                    output.bufferedWriter(StandardCharsets.UTF_8).use { it.write(CompatibilityReport.build(context, store)) }
                    null
                } catch (error: IOException) { error }
                catch (error: SecurityException) { error }
            }
            Toast.makeText(context,
                if (failure == null) "兼容性报告已导出" else "导出兼容性报告失败：${failure.message ?: failure.javaClass.simpleName}",
                Toast.LENGTH_LONG).show()
            if (failure != null) Log.w("HyperDragShare/GestureSettings", "Compatibility report export failed", failure)
        }
    }
    GestureActionPreference(title, summary, enabled) {
        launcher.launch(CompatibilityReport.FILE_NAME)
    }
}

@Composable
private fun GestureResetPreference(
    context: Context,
    store: ConfigStore,
    localValues: MutableMap<String, Any>,
    revision: MutableIntState,
    title: String,
    summary: String,
    enabled: Boolean,
) {
    var showConfirmation by remember { mutableStateOf(false) }
    GestureActionPreference(title, summary, enabled) {
        showConfirmation = true
    }
    OverlayDialog(
        show = showConfirmation,
        title = "恢复手势与小窗默认设置",
        summary = "将手势和小窗参数恢复为默认值。已选应用、应用排序和快捷方式不会更改。",
        onDismissRequest = { showConfirmation = false },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(text = "取消", modifier = Modifier.weight(1f), onClick = { showConfirmation = false })
            TextButton(text = "恢复默认", modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary(), onClick = {
                store.resetTuning()
                localValues.clear()
                revision.intValue++
                showConfirmation = false
                Toast.makeText(context, "手势与小窗参数已恢复默认；目标和快捷方式未更改", Toast.LENGTH_SHORT).show()
            })
        }
    }
}

@Composable
private fun GestureFeedbackPreference(
    context: Context,
    store: ConfigStore,
    title: String,
    summary: String,
    enabled: Boolean,
) {
    val scope = rememberCoroutineScope()
    GestureActionPreference(title, summary, enabled) {
        scope.launch {
            try {
                val report = withContext(Dispatchers.IO) { CompatibilityReport.build(context, store) }
                val issueUri = Uri.parse("https://github.com/chenran0408/HyperDragShare/issues/new")
                    .buildUpon().appendQueryParameter("title", "Hyper 手势兼容性反馈")
                    .appendQueryParameter("body", report).build()
                context.startActivity(Intent(Intent.ACTION_VIEW, issueUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (error: ActivityNotFoundException) {
                Log.w("HyperDragShare/GestureSettings", "No feedback handler", error)
                Toast.makeText(context, "无法打开浏览器或 GitHub 客户端", Toast.LENGTH_LONG).show()
            } catch (error: SecurityException) {
                Log.w("HyperDragShare/GestureSettings", "Feedback access denied", error)
                Toast.makeText(context, "打开问题反馈页面失败：${error.message ?: "系统拒绝启动"}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun gestureSettingDefault(key: String, fallback: Int): Int = when (key) {
    C.KEY_HOT_WIDTH_PERCENT -> C.DEFAULT_HOT_WIDTH_PERCENT
    C.KEY_HOT_HEIGHT_PERCENT -> C.DEFAULT_HOT_HEIGHT_PERCENT
    C.KEY_TRIGGER_PERCENT -> C.DEFAULT_TRIGGER_PERCENT
    C.KEY_SELECTION_RADIUS_PERCENT -> C.DEFAULT_SELECTION_RADIUS_PERCENT
    C.KEY_BOTTOM_HONEYCOMB_SETTLE_MS -> C.DEFAULT_BOTTOM_HONEYCOMB_SETTLE_MS
    C.KEY_ICON_SIZE_DP -> C.DEFAULT_ICON_SIZE_DP
    C.KEY_FAN_MAX_TARGETS -> C.DEFAULT_FAN_MAX_TARGETS
    C.KEY_FAN_CUSTOM_INNER_COUNT -> C.DEFAULT_FAN_CUSTOM_INNER_COUNT
    C.KEY_FAN_CUSTOM_MIDDLE_COUNT -> C.DEFAULT_FAN_CUSTOM_MIDDLE_COUNT
    C.KEY_FAN_CUSTOM_OUTER_COUNT -> C.DEFAULT_FAN_CUSTOM_OUTER_COUNT
    C.KEY_SIDE_TOP_SAFE_MARGIN_PERCENT -> C.DEFAULT_SIDE_TOP_SAFE_MARGIN_PERCENT
    C.KEY_SIDE_TRIGGER_PERCENT -> C.DEFAULT_SIDE_TRIGGER_PERCENT
    C.KEY_SIDE_REVERSE_CANCEL_PERCENT -> C.DEFAULT_SIDE_REVERSE_CANCEL_PERCENT
    C.KEY_SIDE_HOLD_DELAY_MS -> C.DEFAULT_SIDE_HOLD_DELAY_MS
    C.KEY_SIDE_MAX_TARGETS -> C.DEFAULT_SIDE_MAX_TARGETS
    C.KEY_SIDE_ICON_SIZE_DP -> C.DEFAULT_SIDE_ICON_SIZE_DP
    C.KEY_SIDE_RING_SIZE_PERCENT -> C.DEFAULT_SIDE_RING_SIZE_PERCENT
    C.KEY_HONEYCOMB_FIXED_X_PERCENT -> C.DEFAULT_HONEYCOMB_FIXED_X_PERCENT
    C.KEY_HONEYCOMB_FIXED_Y_PERCENT -> C.DEFAULT_HONEYCOMB_FIXED_Y_PERCENT
    C.KEY_HONEYCOMB_MAX_TARGETS -> C.DEFAULT_HONEYCOMB_MAX_TARGETS
    C.KEY_HONEYCOMB_ICON_SIZE_DP -> C.DEFAULT_HONEYCOMB_ICON_SIZE_DP
    C.KEY_HONEYCOMB_DISC_SIZE_PERCENT -> C.DEFAULT_HONEYCOMB_DISC_SIZE_PERCENT
    C.KEY_HONEYCOMB_SPACING_DP -> C.DEFAULT_HONEYCOMB_SPACING_DP
    C.KEY_HONEYCOMB_BLUR_DP -> C.DEFAULT_HONEYCOMB_BLUR_DP
    C.KEY_HONEYCOMB_DIM_PERCENT -> C.DEFAULT_HONEYCOMB_DIM_PERCENT
    C.KEY_HONEYCOMB_LIVE_BLUR_DP -> C.DEFAULT_HONEYCOMB_LIVE_BLUR_DP
    C.KEY_HONEYCOMB_BACKGROUND_DIM_PERCENT -> C.DEFAULT_HONEYCOMB_BACKGROUND_DIM_PERCENT
    C.KEY_HONEYCOMB_ANIMATION_SPEED -> C.DEFAULT_HONEYCOMB_ANIMATION_SPEED
    C.KEY_HONEYCOMB_INERTIA -> C.DEFAULT_HONEYCOMB_INERTIA
    C.KEY_HONEYCOMB_CENTER_SCALE -> C.DEFAULT_HONEYCOMB_CENTER_SCALE
    C.KEY_HONEYCOMB_EDGE_SCALE -> C.DEFAULT_HONEYCOMB_EDGE_SCALE
    C.KEY_HONEYCOMB_SELECTION_SCALE -> C.DEFAULT_HONEYCOMB_SELECTION_SCALE
    C.KEY_SIDE_TASK_MAX_COUNT -> C.DEFAULT_SIDE_TASK_MAX_COUNT
    C.KEY_SIDE_TASK_CARD_WIDTH_DP -> C.DEFAULT_SIDE_TASK_CARD_WIDTH_DP
    C.KEY_SIDE_TASK_CARD_HEIGHT_DP -> C.DEFAULT_SIDE_TASK_CARD_HEIGHT_DP
    C.KEY_SIDE_TASK_CARD_CORNER_DP -> C.DEFAULT_SIDE_TASK_CARD_CORNER_DP
    C.KEY_SIDE_TASK_ICON_SIZE_DP -> C.DEFAULT_SIDE_TASK_ICON_SIZE_DP
    C.KEY_SIDE_TASK_ICON_GAP_DP -> C.DEFAULT_SIDE_TASK_ICON_GAP_DP
    C.KEY_SIDE_TASK_FINGER_OFFSET_DP -> C.DEFAULT_SIDE_TASK_FINGER_OFFSET_DP
    C.KEY_SIDE_TASK_SWIPE_SPEED_PERCENT -> C.DEFAULT_SIDE_TASK_SWIPE_SPEED_PERCENT
    C.KEY_WIDTH_PERCENT -> C.DEFAULT_WIDTH_PERCENT
    C.KEY_HEIGHT_PERCENT -> C.DEFAULT_HEIGHT_PERCENT
    C.KEY_POSITION_X -> C.DEFAULT_POSITION_X
    C.KEY_POSITION_Y -> C.DEFAULT_POSITION_Y
    C.KEY_PORTRAIT_NATIVE_SCALE_PERCENT -> C.DEFAULT_NATIVE_WINDOW_SCALE_PERCENT
    C.KEY_LANDSCAPE_WIDTH_PERCENT -> C.DEFAULT_LANDSCAPE_WIDTH_PERCENT
    C.KEY_LANDSCAPE_HEIGHT_PERCENT -> C.DEFAULT_LANDSCAPE_HEIGHT_PERCENT
    C.KEY_LANDSCAPE_POSITION_X -> C.DEFAULT_LANDSCAPE_POSITION_X
    C.KEY_LANDSCAPE_POSITION_Y -> C.DEFAULT_LANDSCAPE_POSITION_Y
    C.KEY_LANDSCAPE_NATIVE_SCALE_PERCENT -> C.DEFAULT_NATIVE_WINDOW_SCALE_PERCENT
    C.KEY_OUTSIDE_TAP_WINDOW_MS -> C.DEFAULT_OUTSIDE_TAP_WINDOW_MS
    C.KEY_BOTTOM_ANIMATION_SPEED -> C.DEFAULT_BOTTOM_ANIMATION_SPEED
    C.KEY_SIDE_ANIMATION_SPEED -> C.DEFAULT_SIDE_ANIMATION_SPEED
    C.KEY_FAN_REVEAL_AMOUNT -> C.DEFAULT_FAN_REVEAL_AMOUNT
    C.KEY_SIDE_REVEAL_AMOUNT -> C.DEFAULT_SIDE_REVEAL_AMOUNT
    C.KEY_FAN_ROTATION_DEGREES -> C.DEFAULT_FAN_ROTATION_DEGREES
    C.KEY_SIDE_ROTATION_DEGREES -> C.DEFAULT_SIDE_ROTATION_DEGREES
    C.KEY_FAN_SELECTION_SCALE_PERCENT -> C.DEFAULT_FAN_SELECTION_SCALE_PERCENT
    C.KEY_SIDE_SELECTION_SCALE_PERCENT -> C.DEFAULT_SIDE_SELECTION_SCALE_PERCENT
    else -> fallback
}

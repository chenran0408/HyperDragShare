package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.ComponentName
import android.app.StatusBarManager
import android.graphics.drawable.Icon
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import kotlin.math.roundToInt

@Composable
internal fun thumbTriggerEntries(context: Context,
    settings: DragShareSettings, onDurationChanged: (Int) -> Unit): List<SettingEntry> {
    var addingTile by remember { mutableStateOf(false) }
    var tileMessage by remember { mutableStateOf<String?>(null) }
    var thumb by remember { mutableStateOf(ThumbTriggerSettings.read(context)) }
    var duration by remember(settings.accessibilityLongPressTimeoutMillis, thumb.enabled) {
        mutableFloatStateOf(settings.resolvePressConfirmationTimeoutMillis().toFloat())
    }
    var portalInjected by remember { mutableStateOf(ModuleActivation.isCurrentBuildInjected(context)) }
    var portalRootGranted by remember { mutableStateOf(ModuleActivation.isCurrentBuildPortalRootGranted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        thumb = ThumbTriggerSettings.read(context)
        portalInjected = ModuleActivation.isCurrentBuildInjected(context)
        portalRootGranted = ModuleActivation.isCurrentBuildPortalRootGranted(context)
    }
    val triggerTitle = "按住取词与识字"
    val durationTitle = "按住确认时间"
    val tileTitle = "添加“点取内容”磁贴"
    val keywords = "按压 长按 evdev 点取 识字"
    val triggerSummary = when {
        !portalInjected -> "请先重载传送门"
        !portalRootGranted -> "请先为传送门授予 Root 权限"
        else -> "静止按住取词，移动取消"
    }
    val durationEnabled = thumb.enabled
    val durationPrerequisite = if (durationEnabled) null else "请先开启按住取词与识字"
    val tileSummary = tileMessage ?: "从控制中心点取文字或图片"

    return listOf(
        SettingEntry(
            id = "press.enabled",
            title = triggerTitle,
            module = SettingModule.PORTAL,
            group = "触发与识别",
            summary = triggerSummary,
            keywords = keywords,
            prerequisite = when {
                !portalInjected -> "请先重载传送门"
                !portalRootGranted -> "请先为传送门授予 Root 权限"
                else -> null
            },
            content = { enabled ->
                SwitchPreference(
                    title = triggerTitle,
                    summary = triggerSummary,
                    enabled = enabled,
                    checked = thumb.enabled,
                    onCheckedChange = { checked ->
                        if (checked && (!portalInjected || !portalRootGranted)) {
                            Toast.makeText(context,
                                if (!portalInjected) "请先重载传送门" else "请先为传送门授予 Root 权限",
                                Toast.LENGTH_LONG).show()
                        } else {
                            ThumbTriggerSettings.setEnabled(context, checked)
                            thumb = ThumbTriggerSettings.read(context)
                        }
                    },
                )
            },
        ),
        SettingEntry(
            id = "press.duration",
            title = durationTitle,
            module = SettingModule.PORTAL,
            group = "触发与识别",
            summary = "时间越短，越容易误触",
            keywords = keywords,
            enabled = durationEnabled,
            prerequisite = durationPrerequisite,
            content = { enabled ->
                SliderPreference(
                    title = durationTitle,
                    summary = "时间越短，越容易误触",
                    enabled = enabled && durationEnabled,
                    value = duration,
                    valueText = "${duration.roundToInt()} ms",
                    valueRange = DragShareSettings.MIN_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS.toFloat()
                        ..DragShareSettings.MAX_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS.toFloat(),
                    steps = 18,
                    showKeyPoints = true,
                    keyPoints = listOf(250f, 300f, 400f, 500f, 600f, 800f, 1000f, 1200f),
                    onValueChange = { duration = it },
                    onValueChangeFinished = { onDurationChanged(duration.roundToInt()) },
                )
            },
        ),
        SettingEntry(
            id = "press.tile",
            title = tileTitle,
            module = SettingModule.PORTAL,
            group = "触发与识别",
            summary = tileSummary,
            keywords = keywords,
            enabled = !addingTile,
            content = { enabled ->
                ArrowPreference(
                    title = tileTitle,
                    summary = tileSummary,
                    enabled = enabled && !addingTile,
                    onClick = {
                        addingTile = true
                        tileMessage = "正在请求添加…"
                        try {
                            context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                                ComponentName(context, PointCaptureTileService::class.java),
                                context.getString(R.string.point_capture_tile_label),
                                Icon.createWithResource(context, R.drawable.ic_point_capture_tile),
                                context.mainExecutor,
                            ) { result ->
                                addingTile = false
                                tileMessage = when (result) {
                                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> "已添加，可从控制中心点取当前页面"
                                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> "已在控制中心，可直接使用"
                                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> "未添加，可再次申请或在控制中心编辑磁贴"
                                    else -> {
                                        DragShareLog.w("DragShare/PointCapture", "tile add request failed code=$result")
                                        "系统未能添加（$result），可在控制中心编辑磁贴"
                                    }
                                }
                            }
                        } catch (error: SecurityException) {
                            addingTile = false
                            tileMessage = "系统拒绝添加磁贴：${error.message}"
                            DragShareLog.w("DragShare/PointCapture", "tile add request denied", error)
                        } catch (error: IllegalArgumentException) {
                            addingTile = false
                            tileMessage = "磁贴配置不可用：${error.message}"
                            DragShareLog.w("DragShare/PointCapture", "tile component unavailable", error)
                        }
                    },
                )
            },
        ),
    )
}

package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.app.StatusBarManager
import android.graphics.drawable.Icon
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun ThumbTriggerPreference(context: Context, onOpenBlacklist: () -> Unit, portalMode: Boolean) {
    var addingTile by remember { mutableStateOf(false) }
    var tileMessage by remember { mutableStateOf<String?>(null) }
    var thumb by remember { mutableStateOf(ThumbTriggerSettings.read(context)) }
    var serviceEnabled by remember { mutableStateOf(AccessibilityRuntimeStatus.isServiceEnabled(context)) }
    var portalInjected by remember { mutableStateOf(ModuleActivation.isCurrentBuildInjected(context)) }
    var portalRootGranted by remember { mutableStateOf(ModuleActivation.isCurrentBuildPortalRootGranted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        thumb = ThumbTriggerSettings.read(context)
        serviceEnabled = AccessibilityRuntimeStatus.isServiceEnabled(context)
        portalInjected = ModuleActivation.isCurrentBuildInjected(context)
        portalRootGranted = ModuleActivation.isCurrentBuildPortalRootGranted(context)
    }
    val openCalibration = {
        context.startActivity(Intent(context, ThumbCalibrationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    ArrowPreference(
        title = "添加“点取内容”磁贴",
        summary = tileMessage ?: "从控制中心打开后点选文字或图片，不使用普通长按；需要 Root",
        enabled = !addingTile,
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
    SwitchPreference(
        title = "拇指面积触发",
        summary = when {
            thumb.requiresRecalibration -> "采集方式已更新，请重新校准指尖与拇指"
            !thumb.calibrated -> "先校准指尖与拇指的接触大小"
            portalMode && !portalInjected -> "请先重载传送门，让当前版本的设置生效"
            portalMode && !portalRootGranted -> "请先为传送门授予 Root 权限"
            portalMode -> "由传送门读取 Root 接触尺寸，在当前页面打开分词"
            !serviceEnabled -> "需要 Root 与无障碍服务；按压确认后松手打开分词或识字"
            else -> "按压确认后松手打开分词或识字；启用后取代无障碍长按触发"
        },
        checked = thumb.enabled,
        onCheckedChange = { enabled ->
            if (enabled && !thumb.calibrated) {
                Toast.makeText(context, "请先完成拇指面积校准", Toast.LENGTH_SHORT).show()
                openCalibration()
            } else if (enabled && portalMode && (!portalInjected || !portalRootGranted)) {
                Toast.makeText(context,
                    if (!portalInjected) "请先重载传送门" else "请先为传送门授予 Root 权限",
                    Toast.LENGTH_LONG).show()
            } else {
                ThumbTriggerSettings.setEnabled(context, enabled)
                thumb = ThumbTriggerSettings.read(context)
                if (enabled && !portalMode && !serviceEnabled) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        },
    )
    ArrowPreference(
        title = "校准触发面积",
        summary = if (thumb.calibrated) "已校准，可重新采集指尖和拇指样本" else "各按压三次，确定此设备的触发门槛",
        onClick = openCalibration,
    )
    ArrowPreference(
        title = "拇指触发应用黑名单",
        summary = "在指定应用中暂停触发，桌面和输入法自动排除",
        onClick = onOpenBlacklist,
    )
}

package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.Intent
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
internal fun ThumbTriggerPreference(context: Context, onOpenBlacklist: () -> Unit) {
    var thumb by remember { mutableStateOf(ThumbTriggerSettings.read(context)) }
    var serviceEnabled by remember { mutableStateOf(AccessibilityRuntimeStatus.isServiceEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        thumb = ThumbTriggerSettings.read(context)
        serviceEnabled = AccessibilityRuntimeStatus.isServiceEnabled(context)
    }
    val openCalibration = {
        context.startActivity(Intent(context, ThumbCalibrationActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    SwitchPreference(
        title = "拇指面积触发",
        summary = when {
            thumb.requiresRecalibration -> "采集方式已更新，请重新校准指尖与拇指"
            !thumb.calibrated -> "先校准指尖与拇指的接触大小"
            !serviceEnabled -> "需要 Root 与无障碍服务；按压确认后松手打开分词或识字"
            else -> "按压确认后松手打开分词或识字；启用后取代无障碍长按触发"
        },
        checked = thumb.enabled,
        onCheckedChange = { enabled ->
            if (enabled && !thumb.calibrated) {
                Toast.makeText(context, "请先完成拇指面积校准", Toast.LENGTH_SHORT).show()
                openCalibration()
            } else {
                ThumbTriggerSettings.setEnabled(context, enabled)
                thumb = ThumbTriggerSettings.read(context)
                if (enabled && !serviceEnabled) {
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

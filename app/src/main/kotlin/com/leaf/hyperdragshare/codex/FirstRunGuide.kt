package com.leaf.hyperdragshare.codex

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val GUIDE_PREFERENCES = "first_run_guide"
private const val KEY_CONFIRMED = "confirmed"

@Composable
internal fun FirstRunGuide(
    context: Context,
    showOnFirstRun: Boolean = true,
    requested: Boolean = false,
    onDismiss: () -> Unit = {},
) {
    val preferences = remember(context) {
        context.getSharedPreferences(GUIDE_PREFERENCES, Context.MODE_PRIVATE)
    }
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(preferences, showOnFirstRun, requested) {
        show = requested || (showOnFirstRun && !preferences.getBoolean(KEY_CONFIRMED, false))
    }

    OverlayDialog(
        show = show,
        title = "开始使用 HyperDragShare",
        summary = "只有设置需要进入应用，其他操作都在当前页面完成。",
        onDismissRequest = {
            show = false
            onDismiss()
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "1. 在 LSPosed 启用模块并勾选推荐作用域，授予 Root 和悬浮窗权限。首次启用后重启手机。",
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = "2. 在其他应用静止按住文字，弹出后点选词块；用图标复制、搜索或拆词，按住选中内容可拖出。",
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = "3. 将图文加入中转站，点灰色侧条展开，按住内容拖到目标应用。微信接收后可能直接发送。",
                color = MiuixTheme.colorScheme.onSurface,
            )
            TextButton(
                text = "知道了",
                onClick = {
                    preferences.edit().putBoolean(KEY_CONFIRMED, true).apply()
                    show = false
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }
}

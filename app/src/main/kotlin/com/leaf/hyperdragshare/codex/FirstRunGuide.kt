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
        title = "开始使用 MMGG",
        summary = "MMGG（Make Mi Great Genesis）是 Root + LSPosed 模块，功能在当前页面使用。",
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
                text = "1. 启用推荐作用域并授权。首次重启；更新后可在设置中重载。",
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = "2. 按住文字取词，选中后复制、搜索或拆词；按住选区直接拖出。",
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = "3. 灰色侧条展开中转站，拖动标题移动，按住内容拖出。微信接收后可能直接发送。",
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

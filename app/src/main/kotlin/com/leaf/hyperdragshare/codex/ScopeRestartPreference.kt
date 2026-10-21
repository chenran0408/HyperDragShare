package com.leaf.hyperdragshare.codex

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.TextButton
import android.widget.Toast
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference

private enum class ReloadMode(val label: String, val detail: String) {
    Apps("热重载", "重启传送门和AICR进程，不重启手机。系统框架更新需要软重启；AICR在下次识别时加载。"),
    Android("软重启", "重新加载全部作用域。当前应用会关闭并回到锁屏；请先保存正在编辑的内容。"),
    Device("整机重启", "手机将重新启动，全部作用域重新加载；请先保存正在编辑的内容。"),
}

@Composable
internal fun ScopeRestartPreference(context: Context) {
    var showConfirmation by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(ReloadMode.Apps) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ArrowPreference(
        title = stringResource(R.string.scope_restart_title),
        summary = stringResource(R.string.scope_restart_summary),
        onClick = {
            failure = null
            showConfirmation = true
        },
    )
    OverlayDialog(
        show = showConfirmation,
        title = stringResource(R.string.scope_restart_confirmation_title),
        summary = when {
            running -> stringResource(R.string.scope_restart_running)
            failure != null -> context.getString(R.string.scope_restart_failed, failure)
            else -> mode.detail
        },
        onDismissRequest = { if (!running) showConfirmation = false },
    ) {
        if (running) {
            CircularProgressIndicator()
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ReloadMode.entries.forEach { choice ->
                    TextButton(if (mode == choice) "✓ ${choice.label}" else choice.label,
                        onClick = { mode = choice; failure = null }, modifier = Modifier.fillMaxWidth(),
                        colors = if (mode == choice) ButtonDefaults.textButtonColorsPrimary()
                            else ButtonDefaults.textButtonColors())
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    modifier = Modifier.weight(1f),
                    onClick = { showConfirmation = false },
                )
                TextButton(
                    text = mode.label,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        running = true
                        failure = null
                        scope.launch {
                            val error = withContext(Dispatchers.IO) {
                                try {
                                    when (mode) {
                                        ReloadMode.Apps -> ScopeRestart.reloadAppScopes(context)
                                        ReloadMode.Android -> ScopeRestart.restartAndroidRuntime()
                                        ReloadMode.Device -> ScopeRestart.restartDevice()
                                    }
                                    null
                                } catch (error: IOException) {
                                    DragShareLog.w("DragShare/ScopeRestart", "scope reload failed", error)
                                    error.message ?: error.javaClass.simpleName
                                }
                            }
                            running = false
                            failure = error
                            if (error == null) {
                                showConfirmation = false
                                if (mode == ReloadMode.Apps) Toast.makeText(context,
                                    "传送门已重载，AICR将在下次识别时加载新代码", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                )
            }
        }
    }
}

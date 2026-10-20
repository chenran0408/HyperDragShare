package com.leaf.hyperdragshare.codex

import android.content.Context
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
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable
internal fun ScopeRestartPreference(context: Context) {
    var showConfirmation by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
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
            else -> stringResource(R.string.scope_restart_confirmation_summary)
        },
        onDismissRequest = { if (!running) showConfirmation = false },
    ) {
        if (running) {
            CircularProgressIndicator()
        } else {
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
                    text = stringResource(R.string.scope_restart_confirm),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        running = true
                        failure = null
                        scope.launch {
                            val error = withContext(Dispatchers.IO) {
                                try {
                                    ScopeRestart.restartAndroidRuntime()
                                    null
                                } catch (error: IOException) {
                                    DragShareLog.w("DragShare/ScopeRestart", "soft restart failed", error)
                                    error.message ?: error.javaClass.simpleName
                                }
                            }
                            running = false
                            failure = error
                            if (error == null) showConfirmation = false
                        }
                    },
                )
            }
        }
    }
}

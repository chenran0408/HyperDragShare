package com.leaf.hyperdragshare.codex

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Email
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "DragShare/CopyDirectSuggestions"

private data class CopyDirectUiState(
    val loading: Boolean = true,
    val entities: List<AicrNerClient.Entity> = emptyList(),
    val error: String? = null,
)

@Composable
internal fun CopyDirectSuggestions(
    context: Context,
    text: String,
    onDismiss: () -> Unit,
    onOpenExternal: (Intent) -> Unit,
    onSearch: (String) -> Unit,
) {
    val requestActive = remember(context, text) { AtomicBoolean(true) }
    var uiState by remember(context, text) { mutableStateOf(CopyDirectUiState()) }
    var actionError by remember(context, text) { mutableStateOf<String?>(null) }

    DisposableEffect(context, text, requestActive) {
        requestActive.set(true)
        val value = text.trim()
        val direct = if (validHttpUrl(value) != null) {
            AicrNerClient.Entity(value, AicrNerClient.Entity.TYPE_URL, 0, value.length)
        } else null
        val request = if (direct != null) {
            uiState = CopyDirectUiState(loading = false, entities = listOf(direct))
            null
        } else AicrNerClient.extract(context, text) { entities, error ->
            if (requestActive.get()) {
                uiState = when {
                    error != null -> CopyDirectUiState(
                        loading = false,
                        error = error.message?.takeIf(String::isNotBlank) ?: "识别失败，请稍后再试。",
                    )
                    else -> CopyDirectUiState(
                        loading = false,
                        entities = entities.orEmpty().filter { it.text.isNotBlank() },
                    )
                }
            }
        }
        onDispose {
            requestActive.set(false)
            request?.cancel()
        }
    }

    val dismiss = {
        requestActive.set(false)
        onDismiss()
    }
    BackHandler(enabled = true, onBack = dismiss)

    Card(modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 120.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 120.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp, max = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "直达",
                    modifier = Modifier.weight(1f),
                    color = MiuixTheme.colorScheme.onSurface,
                )
                IconButton(
                    onClick = dismiss,
                    modifier = Modifier.size(44.dp),
                    minWidth = 44.dp,
                    minHeight = 44.dp,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = "关闭实体建议",
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp, max = 64.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                when {
                    actionError != null -> Text(
                        text = actionError.orEmpty(),
                        color = MiuixTheme.colorScheme.error,
                        maxLines = 2,
                    )
                    uiState.loading -> Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Text("正在识别…", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    uiState.error != null -> Text(
                        text = "识别失败：${uiState.error}",
                        color = MiuixTheme.colorScheme.error,
                        maxLines = 2,
                    )
                    uiState.entities.isEmpty() -> Text(
                        text = "未识别到可操作实体",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    else -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        uiState.entities.forEach { entity ->
                            val destination = directEntityDestination(entity)
                            val actionLabel = destination?.label ?: "搜索"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 44.dp)
                                    .clickable(role = Role.Button) {
                                        actionError = null
                                        if (destination == null) {
                                            onSearch(entity.text)
                                        } else {
                                            try {
                                                onOpenExternal(destination.intent)
                                            } catch (error: ActivityNotFoundException) {
                                                DragShareLog.w(TAG, "No activity for direct entity action", error)
                                                actionError = "没有可处理“$actionLabel”的应用。"
                                            } catch (error: SecurityException) {
                                                DragShareLog.w(TAG, "Direct entity action was denied", error)
                                                actionError = "系统拒绝了“$actionLabel”操作。"
                                            }
                                        }
                                    }
                                    .semantics { contentDescription = "$actionLabel：${entity.text}" }
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = destination?.icon ?: MiuixIcons.Search,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = entity.text,
                                    modifier = Modifier.weight(1f),
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                Text(
                                    text = actionLabel,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class DirectEntityDestination(
    val intent: Intent,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private fun directEntityDestination(entity: AicrNerClient.Entity): DirectEntityDestination? {
    val value = entity.text.trim()
    return when (entity.type) {
        AicrNerClient.Entity.TYPE_TEL -> normalizedPhone(value)?.let { phone ->
            DirectEntityDestination(
                intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)),
                label = "拨号",
                icon = MiuixIcons.Phone,
            )
        }
        AicrNerClient.Entity.TYPE_MAIL -> validEmail(value)?.let { email ->
            DirectEntityDestination(
                intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")),
                label = "发邮件",
                icon = MiuixIcons.Email,
            )
        }
        AicrNerClient.Entity.TYPE_URL -> validHttpUrl(value)?.let { uri ->
            DirectEntityDestination(
                intent = Intent(Intent.ACTION_VIEW, uri),
                label = "查看链接",
                icon = MiuixIcons.Link,
            )
        }
        else -> null
    }
}

private fun normalizedPhone(value: String): String? {
    val normalized = value.replace(Regex("[\\s().-]"), "")
    val digits = normalized.count { it in '0'..'9' }
    return normalized.takeIf { it.matches(Regex("\\+?[0-9]+")) && digits in 7..15 }
}

private fun validEmail(value: String): String? = value.takeIf {
    it.matches(Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,63}$")) &&
        !it.contains("..") && !it.substringAfter('@').contains(".-") &&
        !it.substringAfter('@').contains("-.")
}

private fun validHttpUrl(value: String): Uri? {
    if (value.any(Char::isWhitespace)) return null
    val uri = Uri.parse(value)
    val scheme = uri.scheme?.lowercase()
    return uri.takeIf {
        (scheme == "http" || scheme == "https") &&
            !it.host.isNullOrBlank() && it.port <= 65535
    }
}

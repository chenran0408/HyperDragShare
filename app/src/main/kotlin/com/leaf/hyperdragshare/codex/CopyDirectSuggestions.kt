package com.leaf.hyperdragshare.codex

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
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
    onOpenExternal: (Intent) -> Unit,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit = {},
) {
    val requestActive = remember(context, text) { AtomicBoolean(true) }
    var uiState by remember(context, text) { mutableStateOf(CopyDirectUiState()) }
    var actionError by remember(context, text) { mutableStateOf<String?>(null) }

    DisposableEffect(context, text, requestActive) {
        requestActive.set(true)
        val value = text.trim()
        val normalizedUrl = CopyDirectText.findHttpUrl(value)?.takeIf { validHttpUrl(it) != null }
        val normalizedEmail = CopyDirectText.findEmail(value)
        val directValue = normalizedUrl ?: normalizedEmail ?: value
        val direct = when {
            validHttpUrl(directValue) != null ->
                AicrNerClient.Entity(directValue, AicrNerClient.Entity.TYPE_URL, 0, directValue.length)
            validEmail(directValue) != null ->
                AicrNerClient.Entity(directValue, AicrNerClient.Entity.TYPE_MAIL, 0, directValue.length)
            else -> null
        }
        val request = if (direct != null) {
            uiState = CopyDirectUiState(loading = false, entities = listOf(direct))
            null
        } else AicrNerClient.extract(context, directValue) { entities, error ->
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.55f).dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actionError?.let {
            Text(text = it, color = MiuixTheme.colorScheme.error, maxLines = 2)
        }
        when {
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
                maxLines = 3,
            )
            uiState.entities.isEmpty() -> Text(
                text = "未识别到可操作实体",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            else -> Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                uiState.entities.forEach { entity ->
                    val destination = directEntityDestination(entity)
                    val actionLabel = destination?.label ?: "搜索"
                    Card {
                        BasicComponent(
                            title = entity.text,
                            summary = actionLabel,
                            startAction = {
                                Icon(
                                    imageVector = destination?.icon ?: MiuixIcons.Search,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                            onClick = {
                                actionError = null
                                if (destination == null) {
                                    onDismiss()
                                    onSearch(entity.text)
                                } else {
                                    try {
                                        onDismiss()
                                        onOpenExternal(destination.intent)
                                    } catch (error: ActivityNotFoundException) {
                                        DragShareLog.w(TAG, "No activity for direct entity action", error)
                                        actionError = "没有可处理“$actionLabel”的应用。"
                                    } catch (error: SecurityException) {
                                        DragShareLog.w(TAG, "Direct entity action was denied", error)
                                        actionError = "系统拒绝了“$actionLabel”操作。"
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

internal enum class DirectTextKind {
    Url,
    Email,
    Phone,
}

internal data class DirectEntityDestination(
    val kind: DirectTextKind,
    val intent: Intent,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

internal fun directEntityDestination(entity: AicrNerClient.Entity): DirectEntityDestination? {
    val value = entity.text.trim()
    return when (entity.type) {
        AicrNerClient.Entity.TYPE_TEL -> normalizedPhone(value)?.let { phone ->
            DirectEntityDestination(
                kind = DirectTextKind.Phone,
                intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)),
                label = "拨号",
                icon = MiuixIcons.Phone,
            )
        }
        AicrNerClient.Entity.TYPE_MAIL -> validEmail(CopyDirectText.normalizeEmail(value))?.let { email ->
            DirectEntityDestination(
                kind = DirectTextKind.Email,
                intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")),
                label = "发邮件",
                icon = MiuixIcons.Email,
            )
        }
        AicrNerClient.Entity.TYPE_URL ->
            validHttpUrl(CopyDirectText.findHttpUrl(value) ?: value)?.let { uri ->
            DirectEntityDestination(
                kind = DirectTextKind.Url,
                intent = Intent(Intent.ACTION_VIEW, uri),
                label = "查看链接",
                icon = MiuixIcons.Link,
            )
        }
        else -> null
    }
}

internal fun directTextDestination(value: String): DirectEntityDestination? {
    val text = value.trim()
    if (text.isEmpty()) return null
    CopyDirectText.findHttpUrl(text)?.let { url ->
        validHttpUrl(url)?.let {
            return DirectEntityDestination(
                kind = DirectTextKind.Url,
                intent = Intent(Intent.ACTION_VIEW, it),
                label = "查看链接",
                icon = MiuixIcons.Link,
            )
        }
    }
    CopyDirectText.findEmail(text)?.let { email ->
        validEmail(email)?.let {
            return DirectEntityDestination(
                kind = DirectTextKind.Email,
                intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$it")),
                label = "发邮件",
                icon = MiuixIcons.Email,
            )
        }
    }
    val phoneValue = if (text.startsWith("tel:", ignoreCase = true)) {
        text.substringAfter(':')
    } else {
        text
    }
    normalizedPhone(phoneValue)?.let { phone ->
        return DirectEntityDestination(
            kind = DirectTextKind.Phone,
            intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)),
            label = "拨号",
            icon = MiuixIcons.Phone,
        )
    }
    return null
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

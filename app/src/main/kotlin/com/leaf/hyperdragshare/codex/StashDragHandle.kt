package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.content.ClipDescription
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Single and multi-item drag use the same URI grants and current selection. */
@Composable
internal fun StashDragHandle(entries: List<StashStore.Entry>, onStarted: () -> Unit = {}) {
    val color = MiuixTheme.colorScheme.primary.toArgb()
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        factory = { context ->
            TextView(context).apply { textSize = 12f; gravity = Gravity.CENTER }
        },
        update = { view ->
            view.text = if (entries.size > 1) "按住拖出 ${entries.size} 项" else "按住拖出"
            view.setTextColor(color)
            view.isEnabled = entries.isNotEmpty()
            view.alpha = if (entries.isEmpty()) .4f else 1f
            view.setOnLongClickListener {
                if (entries.isEmpty()) return@setOnLongClickListener false
                val hasImages = entries.any { it.kind == StashStore.KIND_IMAGE }
                val mimeTypes = buildList {
                    if (hasImages) add("image/*")
                    if (entries.any { it.kind == StashStore.KIND_TEXT }) add("text/plain")
                }.toTypedArray()
                fun item(entry: StashStore.Entry) =
                    if (entry.kind == StashStore.KIND_IMAGE) ClipData.Item(StashImageProvider.uriForEntry(entry.id))
                    else ClipData.Item(entry.text.orEmpty())
                val clip = ClipData(ClipDescription("中转站内容", mimeTypes), item(entries.first()))
                entries.drop(1).forEach { clip.addItem(item(it)) }
                try {
                    val started = view.startDragAndDrop(clip, View.DragShadowBuilder(view), null,
                        View.DRAG_FLAG_GLOBAL or if (hasImages) View.DRAG_FLAG_GLOBAL_URI_READ else 0)
                    if (started) onStarted()
                    else Toast.makeText(view.context, "系统未能开始拖拽，请重试", Toast.LENGTH_SHORT).show()
                    started
                } catch (error: SecurityException) {
                    DragShareLog.w("DragShare/Stash", "drag permission denied", error)
                    Toast.makeText(view.context, "系统拒绝拖出内容：${error.message}", Toast.LENGTH_LONG).show()
                    false
                }
            }
        },
    )
}

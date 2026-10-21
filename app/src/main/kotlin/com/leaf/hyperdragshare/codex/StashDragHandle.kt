package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.content.ClipDescription
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Click to inspect; hold the content itself to take it to another application. */
@Composable
internal fun StashDraggableContent(
    entries: List<StashStore.Entry>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    draggable: Boolean = true,
    startDrag: (View, ClipData, View.DragShadowBuilder, Int) -> Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val foreground = MiuixTheme.colorScheme.onSurface.toArgb()
    val background = MiuixTheme.colorScheme.surfaceContainer.toArgb()
    Box(modifier.combinedClickable(enabled = enabled, onClick = onClick,
        onLongClickLabel = if (entries.size > 1) "拖出所选 ${entries.size} 项" else "拖出内容", onLongClick = if (draggable) ({
            startStashDrag(view, entries, foreground, background, startDrag)
        }) else null)) { content() }
}

private fun startStashDrag(view: View, entries: List<StashStore.Entry>, foreground: Int,
    background: Int, startDrag: (View, ClipData, View.DragShadowBuilder, Int) -> Boolean) {
    if (entries.isEmpty()) return
    val hasImages = entries.any { it.kind == StashStore.KIND_IMAGE }
    if (hasImages && entries.any { it.kind == StashStore.KIND_TEXT }) {
        Toast.makeText(view.context, "文字和图片请分别选取后拖出，避免目标应用直接发送文字", Toast.LENGTH_LONG).show()
        return
    }
    val clip = clipForStash(entries)
    val density = view.resources.displayMetrics.density
    val preview = TextView(view.context).apply {
        text = if (entries.size > 1) "${entries.size} 项内容" else if (hasImages) "图片" else entries.first().text
        textSize = 14f
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(foreground)
        this.background = GradientDrawable().apply { setColor(background); cornerRadius = 16 * density }
        val padding = (12 * density).toInt()
        setPadding(padding, padding, padding, padding)
        measure(View.MeasureSpec.makeMeasureSpec((200 * density).toInt(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec((120 * density).toInt(), View.MeasureSpec.AT_MOST))
        layout(0, 0, measuredWidth, measuredHeight)
    }
    val shadow = object : View.DragShadowBuilder() {
        override fun onProvideShadowMetrics(size: Point, touch: Point) {
            size.set(preview.width, preview.height)
            touch.set(preview.width / 2, preview.height / 2)
        }
        override fun onDrawShadow(canvas: Canvas) = preview.draw(canvas)
    }
    if (!hasImages) {
        StashTextDropWindow.start(view, entries.joinToString("\n") { it.text.orEmpty() }, shadow, startDrag)
        return
    }
    try {
        val started = startDrag(view, clip, shadow,
            View.DRAG_FLAG_GLOBAL or if (hasImages) View.DRAG_FLAG_GLOBAL_URI_READ else 0)
        if (!started) Toast.makeText(view.context, "系统未能开始拖拽，请重试", Toast.LENGTH_SHORT).show()
    } catch (error: SecurityException) {
        DragShareLog.w("DragShare/Stash", "drag permission denied", error)
        Toast.makeText(view.context, "系统拒绝拖出内容：${error.message}", Toast.LENGTH_LONG).show()
    }
}

internal fun clipForStash(entries: List<StashStore.Entry>): ClipData {
    val mimeTypes = entries.map {
        if (it.kind == StashStore.KIND_IMAGE) it.mime else "text/plain"
    }.distinct().toTypedArray()
    fun item(entry: StashStore.Entry) =
        if (entry.kind == StashStore.KIND_IMAGE) ClipData.Item(StashImageProvider.uriForEntry(entry.id))
        else ClipData.Item(entry.text.orEmpty())
    return ClipData(ClipDescription("中转站内容", mimeTypes), item(entries.first())).apply {
        entries.drop(1).forEach { addItem(item(it)) }
    }
}

package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.IOException
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val held = rememberUpdatedState(pressed)
    val currentEntries = rememberUpdatedState(entries)
    val scope = rememberCoroutineScope()
    Box(modifier.combinedClickable(interactionSource = interaction, enabled = enabled, onClick = onClick,
        onLongClickLabel = if (entries.size > 1) "拖出所选 ${entries.size} 项" else "拖出内容", onLongClick = if (draggable) ({
            scope.launch { startStashDrag(view, currentEntries.value, foreground, background, startDrag) { held.value } }
        }) else null)) { content() }
}

private suspend fun startStashDrag(view: View, entries: List<StashStore.Entry>, foreground: Int,
    background: Int, startDrag: (View, ClipData, View.DragShadowBuilder, Int) -> Boolean,
    isHeld: () -> Boolean) {
    if (entries.isEmpty()) return
    val hasImages = entries.any { it.kind == StashStore.KIND_IMAGE }
    if (hasImages && entries.any { it.kind == StashStore.KIND_TEXT }) {
        Toast.makeText(view.context, "文字和图片请分别选取后拖出", Toast.LENGTH_LONG).show()
        return
    }
    val operation = OperationTrace.fresh("stash")
    OperationTrace.event(operation, "drag_prepare", "started")
    val density = view.resources.displayMetrics.density
    fun textPreview(label: String): View = TextView(view.context).apply {
        text = label
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
    try {
        var thumbnail: Bitmap? = null
        val clip = if (!hasImages) ClipData.newPlainText("中转站文字", entries.joinToString("\n") { it.text.orEmpty() })
        else {
            val prepared = withContext(Dispatchers.IO) {
                StashStore(view.context.applicationContext).use { store ->
                    val files = entries.map { store.openImage(it.id) }
                    val uris = entries.zip(files).map { (entry, file) ->
                        StashDragImageCache.uri(view.context.applicationContext, entry, file)
                    }
                    val thumbnail = try {
                        decodeDragThumbnail(files.first())
                    } catch (error: IOException) {
                        DragShareLog.w("DragShare/Stash", "Unable to decode optional drag thumbnail error=" + error.javaClass.simpleName)
                        null
                    } catch (error: SecurityException) {
                        DragShareLog.w("DragShare/Stash", "Permission denied decoding optional drag thumbnail error=" + error.javaClass.simpleName)
                        null
                    }
                    uris to thumbnail
                }
            }
            thumbnail = prepared.second
            val clip = clipForStash(entries, prepared.first)
            prepared.first.forEach { uri -> OperationTrace.event(operation, "asset_ready", "available", uri) }
            clip
        }
        OperationTrace.put(clip, operation)
        if (!isHeld() || !view.isAttachedToWindow) {
            OperationTrace.event(operation, "drag_prepare", "abandoned")
            return
        }
        val preview = when {
            hasImages -> thumbnail?.let {
                imagePreview(view.context, it, entries.size, density, foreground, background)
            } ?: textPreview(if (entries.size > 1) "${entries.size} 张（预览不可用）" else "图片（预览不可用）")
            entries.size > 1 -> textPreview("${entries.size} 项内容")
            else -> textPreview(entries.first().text.orEmpty())
        }
        val shadow = object : View.DragShadowBuilder() {
            override fun onProvideShadowMetrics(size: Point, touch: Point) {
                size.set(preview.width, preview.height)
                touch.set(preview.width / 2, preview.height / 2)
            }
            override fun onDrawShadow(canvas: Canvas) = preview.draw(canvas)
        }
        val started = startDrag(view, clip, shadow,
            View.DRAG_FLAG_GLOBAL or if (hasImages) View.DRAG_FLAG_GLOBAL_URI_READ else 0)
        OperationTrace.event(operation, "drag_dispatch",
            if (started) "accepted_dispatch" else "rejected_dispatch")
        if (!started) Toast.makeText(view.context, "系统未能开始拖拽，请重试", Toast.LENGTH_SHORT).show()
    } catch (error: IOException) {
        OperationTrace.event(operation, "drag_prepare", "failed")
        DragShareLog.w("DragShare/Stash", "Unable to prepare drag image error=" + error.javaClass.simpleName)
        Toast.makeText(view.context, "无法准备拖放图片：${error.message}", Toast.LENGTH_LONG).show()
    } catch (error: SecurityException) {
        OperationTrace.event(operation, "drag_prepare", "failed")
        DragShareLog.w("DragShare/Stash", "drag permission denied error=" + error.javaClass.simpleName)
        Toast.makeText(view.context, "系统拒绝拖出内容：${error.message}", Toast.LENGTH_LONG).show()
    }
}

private fun decodeDragThumbnail(file: File): Bitmap {
    val edgeLimit = 256
    return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
        val longest = maxOf(info.size.width, info.size.height)
        if (longest > edgeLimit) {
            val scale = edgeLimit / longest.toFloat()
            decoder.setTargetSize(
                (info.size.width * scale).toInt().coerceAtLeast(1),
                (info.size.height * scale).toInt().coerceAtLeast(1),
            )
        }
    }
}

private fun imagePreview(context: Context, bitmap: Bitmap, count: Int, density: Float,
    foreground: Int, background: Int): View {
    val width = (200 * density).toInt()
    val height = (120 * density).toInt()
    val frame = FrameLayout(context).apply {
        this.background = GradientDrawable().apply { setColor(background); cornerRadius = 16 * density }
        clipToOutline = true
        addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bitmap)
            contentDescription = "中转站图片拖动预览"
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        if (count > 1) {
            val badge = TextView(context).apply {
                text = "${count} 张"
                textSize = 12f
                setTextColor(foreground)
                this.background = GradientDrawable().apply {
                    setColor(background)
                    cornerRadius = 12 * density
                }
                val horizontal = (9 * density).toInt()
                val vertical = (5 * density).toInt()
                setPadding(horizontal, vertical, horizontal, vertical)
            }
            addView(badge, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
                val margin = (8 * density).toInt()
                setMargins(margin, margin, margin, margin)
            })
        }
        measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        layout(0, 0, measuredWidth, measuredHeight)
    }
    return frame
}

internal fun clipForStash(entries: List<StashStore.Entry>, imageUris: List<Uri> = emptyList()): ClipData {
    val images = imageUris.iterator()
    val mimeTypes = entries.map {
        if (it.kind == StashStore.KIND_IMAGE) it.mime else "text/plain"
    }.distinct().toTypedArray()
    fun item(entry: StashStore.Entry) =
        if (entry.kind == StashStore.KIND_IMAGE) ClipData.Item(images.next())
        else ClipData.Item(entry.text.orEmpty())
    return ClipData(ClipDescription("中转站内容", mimeTypes), item(entries.first())).apply {
        entries.drop(1).forEach { addItem(item(it)) }
    }
}

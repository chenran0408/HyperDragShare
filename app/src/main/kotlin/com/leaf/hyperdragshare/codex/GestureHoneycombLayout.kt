package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leaf.hyperdragshare.gestures.config.AppTarget
import com.leaf.hyperdragshare.gestures.config.ConfigContract
import com.leaf.hyperdragshare.gestures.config.ConfigStore
import com.leaf.hyperdragshare.gestures.config.HoneycombPositions
import com.leaf.hyperdragshare.gestures.config.ShortcutIconLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class HoneycombLayoutCell(
    val target: AppTarget?,
    val label: String,
    val icon: Bitmap?,
)

private data class HoneycombLayoutState(
    val positions: HoneycombPositions,
    val cells: List<HoneycombLayoutCell>,
    val iconSizeDp: Int,
    val spacingDp: Int,
)

private data class HoneycombFrame(
    val centers: List<Offset>,
    val radius: Float,
)

/** Edits persistent honeycomb slots without changing the target-list order. */
@Composable
fun GestureHoneycombLayout(
    configStore: ConfigStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var layout by remember(configStore) { mutableStateOf<HoneycombLayoutState?>(null) }
    var editing by remember(configStore) { mutableStateOf(false) }
    var selectedSlot by remember(configStore) { mutableStateOf<Int?>(null) }
    var dragFrom by remember(configStore) { mutableStateOf<Int?>(null) }
    var dragTarget by remember(configStore) { mutableStateOf<Int?>(null) }
    var dragPoint by remember(configStore) { mutableStateOf(Offset.Zero) }
    var showCompactConfirmation by remember(configStore) { mutableStateOf(false) }
    var writing by remember(configStore) { mutableStateOf(false) }

    LaunchedEffect(configStore, context) {
        layout = withContext(Dispatchers.IO) { readHoneycombLayout(context, configStore) }
    }

    fun refreshPositions(update: () -> Unit) {
        if (writing) return
        writing = true
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                update()
                readHoneycombLayout(context, configStore)
            }
            layout = next
            selectedSlot = null
            dragFrom = null
            dragTarget = null
            writing = false
        }
    }

    fun swapSlots(first: Int, second: Int) {
        if (first == second || first < 0 || second < 0) return
        refreshPositions { configStore.swapHoneycombSlots(first, second) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "蜂窝位置",
                largeTitle = "蜂窝位置",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        top.yukonga.miuix.kmp.basic.Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回",
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
                actions = {
                    TextButton(
                        text = if (editing) "完成" else "编辑",
                        enabled = layout != null && !writing,
                        onClick = {
                            editing = !editing
                            selectedSlot = null
                            dragFrom = null
                            dragTarget = null
                        },
                    )
                },
            )
        },
    ) { contentPadding ->
        val current = layout
        if (current == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(contentPadding).padding(
                    horizontal = 16.dp, vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = when {
                        writing -> "正在保存位置…"
                        !editing -> "当前位置已保留。点击“编辑”调整图标位置。"
                        selectedSlot != null -> "已选一个图标；再点一个位置交换，也可长按拖动。"
                        else -> "点选两个位置交换图标，或长按图标拖到目标位置。空位可用于新应用。"
                    },
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                )
                HoneycombLayoutPreview(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    layout = current,
                    editing = editing && !writing,
                    selectedSlot = selectedSlot,
                    dragFrom = dragFrom,
                    dragTarget = dragTarget,
                    dragPoint = dragPoint,
                    onTapSlot = { index ->
                        if (!editing || writing) return@HoneycombLayoutPreview
                        val selected = selectedSlot
                        if (selected == null) {
                            if (current.cells.getOrNull(index)?.target != null) {
                                selectedSlot = index
                            }
                        } else if (selected == index) {
                            selectedSlot = null
                        } else {
                            swapSlots(selected, index)
                        }
                    },
                    onDragStart = { point ->
                        if (!editing || writing) return@HoneycombLayoutPreview
                        val frame = honeycombFrame(
                            current, point.width, point.height,
                            context.resources.displayMetrics.density,
                        )
                        val index = nearestHoneycombSlot(frame, point.x, point.y,
                            frame.radius * 1.7f)
                        if (index >= 0 && current.cells.getOrNull(index)?.target != null) {
                            dragFrom = index
                            dragTarget = index
                            dragPoint = Offset(point.x, point.y)
                            selectedSlot = null
                        }
                    },
                    onDrag = { point ->
                        val source = dragFrom ?: return@HoneycombLayoutPreview
                        val frame = honeycombFrame(
                            current, point.width, point.height,
                            context.resources.displayMetrics.density,
                        )
                        dragPoint = Offset(point.x, point.y)
                        dragTarget = nearestHoneycombSlot(frame, point.x, point.y,
                            frame.radius * 1.8f)
                        if (source >= current.cells.size) dragTarget = null
                    },
                    onDragEnd = {
                        val from = dragFrom
                        val to = dragTarget
                        dragFrom = null
                        dragTarget = null
                        if (from != null && to != null) swapSlots(from, to)
                    },
                )
                TextButton(
                    text = "重新紧凑排列",
                    enabled = !writing && current.cells.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showCompactConfirmation = true },
                )
            }
        }
    }

    OverlayDialog(
        show = showCompactConfirmation,
        title = "重新紧凑排列？",
        summary = "将移除空位并重新排列所有蜂窝应用，当前手动位置会被替换。",
        onDismissRequest = { showCompactConfirmation = false },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                text = "取消",
                modifier = Modifier.weight(1f),
                onClick = { showCompactConfirmation = false },
            )
            TextButton(
                text = "重新排列",
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                enabled = !writing,
                onClick = {
                    showCompactConfirmation = false
                    editing = false
                    selectedSlot = null
                    refreshPositions { configStore.compactHoneycombPositions() }
                },
            )
        }
    }
}

@Composable
private fun HoneycombLayoutPreview(
    modifier: Modifier,
    layout: HoneycombLayoutState,
    editing: Boolean,
    selectedSlot: Int?,
    dragFrom: Int?,
    dragTarget: Int?,
    dragPoint: Offset,
    onTapSlot: (Int) -> Unit,
    onDragStart: (PointerSize) -> Unit,
    onDrag: (PointerSize) -> Unit,
    onDragEnd: () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val primary = MiuixTheme.colorScheme.primary
    val onSurface = MiuixTheme.colorScheme.onSurface
    val surface = MiuixTheme.colorScheme.surfaceContainer
    Canvas(
        modifier = modifier
            .then(
                Modifier.pointerInput(layout, editing) {
                    detectTapGestures { point ->
                        val frame = honeycombFrame(layout, size.width.toFloat(), size.height.toFloat(), density)
                        val index = nearestHoneycombSlot(frame, point.x, point.y,
                            frame.radius * 1.7f)
                        if (index >= 0) onTapSlot(index)
                    }
                },
            )
            .pointerInput(layout, editing) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { point -> onDragStart(PointerSize(
                        point.x, point.y, size.width.toFloat(), size.height.toFloat(),
                    )) },
                    onDrag = { change, _ ->
                        onDrag(PointerSize(
                            change.position.x, change.position.y,
                            size.width.toFloat(), size.height.toFloat(),
                        ))
                        change.consume()
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            },
    ) {
        val frame = honeycombFrame(layout, size.width.toFloat(), size.height.toFloat(), density)
        drawRoundRect(
            color = Color(0xFF202127),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
        )
        frame.centers.forEachIndexed { index, center ->
            val cell = layout.cells[index]
            val isDragged = index == dragFrom
            drawCircle(
                color = if (cell.target == null) Color.White.copy(alpha = 0.12f) else surface,
                radius = frame.radius,
                center = if (isDragged) center else center,
            )
            if (cell.icon != null && (!isDragged || dragTarget == null)) {
                val image = cell.icon.asImageBitmap()
                val inset = frame.radius * 0.16f
                val imageSize = (frame.radius - inset) * 2f
                drawImage(
                    image = image,
                    dstOffset = androidx.compose.ui.unit.IntOffset(
                        (center.x - imageSize / 2f).toInt(),
                        (center.y - imageSize / 2f).toInt(),
                    ),
                    dstSize = androidx.compose.ui.unit.IntSize(imageSize.toInt(), imageSize.toInt()),
                    alpha = if (isDragged) 0.38f else 1f,
                )
            }
            if (editing && selectedSlot == index) {
                drawCircle(color = primary, radius = frame.radius + 7.dp.toPx(),
                    center = center, style = Stroke(width = 3.dp.toPx()))
            }
            if (editing && dragTarget == index) {
                drawCircle(color = primary.copy(alpha = 0.85f),
                    radius = frame.radius + 6.dp.toPx(), center = center,
                    style = Stroke(width = 3.dp.toPx()))
            }
        }
        if (dragFrom != null && dragFrom in layout.cells.indices) {
            val cell = layout.cells[dragFrom]
            if (cell.icon != null) {
                val image = cell.icon.asImageBitmap()
                val radius = frame.radius * 1.08f
                val imageSize = radius * 1.68f
                drawImage(
                    image = image,
                    dstOffset = androidx.compose.ui.unit.IntOffset(
                        (dragPoint.x - imageSize / 2f).toInt(),
                        (dragPoint.y - imageSize / 2f).toInt(),
                    ),
                    dstSize = androidx.compose.ui.unit.IntSize(imageSize.toInt(), imageSize.toInt()),
                )
            }
        }
        drawIntoCanvas { canvas ->
            val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = onSurface.copy(alpha = 0.86f).toArgb()
                textAlign = AndroidPaint.Align.CENTER
                textSize = 11.sp.toPx()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val native = canvas.nativeCanvas
            layout.cells.forEachIndexed { index, cell ->
                if (cell.target != null && index != dragFrom) {
                    val center = frame.centers[index]
                    val maxLabel = cell.label.take(10)
                    native.drawText(maxLabel, center.x, center.y + frame.radius + 14.dp.toPx(), textPaint)
                }
            }
        }
    }
}

private data class PointerSize(val x: Float, val y: Float, val width: Float, val height: Float)

private fun honeycombFrame(
    layout: HoneycombLayoutState,
    width: Float,
    height: Float,
    density: Float,
): HoneycombFrame {
    val pitch = maxOf(layout.iconSizeDp + 4f, layout.spacingDp.toFloat()) * density
    val maxX = layout.positions.slots.maxOfOrNull { kotlin.math.abs(it.x) } ?: 0f
    val maxY = layout.positions.slots.maxOfOrNull { kotlin.math.abs(it.y) } ?: 0f
    val rawRadius = layout.iconSizeDp * density * 0.5f
    val fitX = (width - 28f * density) / maxOf(1f, maxX * 2f * pitch + rawRadius * 2f)
    val fitY = (height - 28f * density) / maxOf(1f, maxY * 2f * pitch + rawRadius * 2f)
    val scale = minOf(1f, maxOf(0.12f, minOf(fitX, fitY)))
    val centers = layout.positions.slots.map { slot ->
        Offset(width * 0.5f + slot.x * pitch * scale, height * 0.5f + slot.y * pitch * scale)
    }
    return HoneycombFrame(centers, rawRadius * scale)
}

private fun nearestHoneycombSlot(frame: HoneycombFrame, x: Float, y: Float, radius: Float): Int {
    var bestIndex = -1
    var bestDistance = radius * radius
    frame.centers.forEachIndexed { index, point ->
        val dx = x - point.x
        val dy = y - point.y
        val distance = dx * dx + dy * dy
        if (distance <= bestDistance) {
            bestDistance = distance
            bestIndex = index
        }
    }
    return bestIndex
}

private suspend fun readHoneycombLayout(
    context: Context,
    store: ConfigStore,
): HoneycombLayoutState {
    val targets = store.honeycombTargets
    val positions = store.honeycombPositions
    val iconSize = store.preferences().getInt(
        ConfigContract.KEY_HONEYCOMB_ICON_SIZE_DP,
        ConfigContract.DEFAULT_HONEYCOMB_ICON_SIZE_DP,
    )
    val spacing = store.preferences().getInt(
        ConfigContract.KEY_HONEYCOMB_SPACING_DP,
        ConfigContract.DEFAULT_HONEYCOMB_SPACING_DP,
    )
    val rows = targets.associateBy { it.positionIdentity() }
    val cells = positions.slots.map { slot ->
        val target = rows[slot.owner]
        if (target == null) {
            HoneycombLayoutCell(null, "空位", null)
        } else {
            val (label, icon) = resolveHoneycombCell(context, target)
            HoneycombLayoutCell(target, label, icon)
        }
    }
    return HoneycombLayoutState(positions, cells, iconSize, spacing)
}

private fun resolveHoneycombCell(context: Context, target: AppTarget): Pair<String, Bitmap?> {
    val manager = context.packageManager
    return if (target.isShortcut) {
        val label = target.shortcutLabel.ifBlank { target.packageName() }
        val icon = try {
            drawableToHoneycombBitmap(ShortcutIconLoader.load(
                context, target.packageName(), target.shortcutId, target.userId,
            ))
        } catch (error: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Shortcut icon package not found: ${target.packageName()}", error)
            null
        } catch (error: SecurityException) {
            Log.w(TAG, "Shortcut icon access denied: ${target.packageName()}", error)
            null
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Invalid shortcut icon request: ${target.packageName()}", error)
            null
        }
        label to icon
    } else {
        val component = target.componentName()
        if (component == null) return target.packageName() to null
        try {
            val info = manager.getActivityInfo(component, 0)
            val label = info.loadLabel(manager)?.toString()?.ifBlank { target.packageName() }
                ?: target.packageName()
            label to drawableToHoneycombBitmap(info.loadIcon(manager))
        } catch (error: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Activity icon target not found: ${target.packageName()}", error)
            target.packageName() to null
        } catch (error: SecurityException) {
            Log.w(TAG, "Activity icon access denied: ${target.packageName()}", error)
            target.packageName() to null
        }
    }
}

private fun drawableToHoneycombBitmap(drawable: Drawable): Bitmap {
    val width = drawable.intrinsicWidth.coerceIn(1, 192)
    val height = drawable.intrinsicHeight.coerceIn(1, 192)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(canvas)
    return bitmap
}

private const val TAG = "HoneycombLayout"

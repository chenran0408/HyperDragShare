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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.TooltipAnchorPosition
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.TooltipDefaults
import top.yukonga.miuix.kmp.basic.PlainTooltip
import top.yukonga.miuix.kmp.basic.rememberTooltipState
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
    HoneycombEditorScreen(configStore, onBack)
}

/** Inline editor for pages that manage honeycomb targets. */
@Composable
fun HoneycombInlineEditor(
    configStore: ConfigStore,
    revision: Any?,
    modifier: Modifier = Modifier,
) {
    HoneycombEditorContent(configStore, revision, inline = true, modifier = modifier)
}

@Composable
private fun HoneycombEditorScreen(configStore: ConfigStore, onBack: () -> Unit) {
    var editing by remember(configStore) { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = "蜂巢位置",
                largeTitle = "蜂巢位置",
                navigationIcon = { HoneycombIconAction("返回", MiuixIcons.Back, onBack) },
                actions = {
                    HoneycombIconAction(
                        description = if (editing) "完成编辑" else "编辑位置",
                        imageVector = if (editing) MiuixIcons.Ok else MiuixIcons.Edit,
                        onClick = { editing = !editing },
                    )
                },
            )
        },
    ) { contentPadding ->
        HoneycombEditorContent(
            configStore = configStore,
            revision = null,
            inline = false,
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            fullPageEditing = editing,
        )
    }
}

@Composable
private fun HoneycombEditorContent(
    configStore: ConfigStore,
    revision: Any?,
    inline: Boolean,
    modifier: Modifier,
    fullPageEditing: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var layout by remember(configStore) { mutableStateOf<HoneycombLayoutState?>(null) }
    var selectedSlot by remember(configStore) { mutableStateOf<Int?>(null) }
    var dragFrom by remember(configStore) { mutableStateOf<Int?>(null) }
    var dragTarget by remember(configStore) { mutableStateOf<Int?>(null) }
    var dragPoint by remember(configStore) { mutableStateOf(Offset.Zero) }
    var showCompactConfirmation by remember(configStore) { mutableStateOf(false) }
    var writing by remember(configStore) { mutableStateOf(false) }
    var operationFeedback by remember(configStore) { mutableStateOf<String?>(null) }

    LaunchedEffect(configStore, context, revision) {
        layout = withContext(Dispatchers.IO) { readHoneycombLayout(context, configStore) }
        selectedSlot = null
        dragFrom = null
        dragTarget = null
        dragPoint = Offset.Zero
    }
    LaunchedEffect(fullPageEditing) {
        selectedSlot = null
        dragFrom = null
        dragTarget = null
        dragPoint = Offset.Zero
        operationFeedback = null
    }

    fun refreshPositions(feedback: String, update: () -> Unit) {
        if (writing) return
        writing = true
        operationFeedback = null
        scope.launch {
            val next = withContext(Dispatchers.IO) {
                update()
                readHoneycombLayout(context, configStore)
            }
            layout = next
            selectedSlot = null
            dragFrom = null
            dragTarget = null
            dragPoint = Offset.Zero
            operationFeedback = feedback
            writing = false
        }
    }

    fun swapSlots(first: Int, second: Int) {
        if (first == second || first < 0 || second < 0) return
        refreshPositions("已保存；系统界面（SystemUI）会在后续手势读取新布局，无需重启。") {
            configStore.swapHoneycombSlots(first, second)
        }
    }

    val current = layout
    if (current == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column(
            modifier = if (inline) modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                else modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
                val activeDragFrom = dragFrom
                val activeDragTarget = dragTarget
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(
                            text = when {
                                writing -> "正在保存位置…"
                                activeDragFrom != null -> {
                                    val source = current.cells.getOrNull(activeDragFrom)
                                    val target = activeDragTarget?.let { current.cells.getOrNull(it) }
                                    when {
                                        source == null -> "拖动位置无效，松手取消"
                                        activeDragTarget == null -> "没有有效落点，松手取消"
                                        activeDragTarget == activeDragFrom -> "正在拖动「${source.label}」，松手保持原位"
                                        target == null -> "没有有效落点，松手取消"
                                        target.target == null -> "将「${source.label}」移动到空位"
                                        else -> "将「${source.label}」与「${target.label}」交换"
                                    }
                                }
                                selectedSlot != null -> "已选一个图标；再点一个位置交换，或长按图标直接拖动。"
                                inline -> operationFeedback ?: "长按图标拖动排序，松手保存；可拖到空位。"
                                !fullPageEditing -> operationFeedback ?: "长按图标即可拖到目标位置；点击编辑图标可用点选交换。"
                                else -> operationFeedback ?: "长按图标拖到目标位置，也可点选两个位置交换。空位可用于新应用。"
                            },
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 14.sp,
                        )
                    }
                }
                HoneycombLayoutPreview(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    layout = current,
                    showLabels = !inline,
                    editing = !inline && fullPageEditing && !writing,
                    selectedSlot = selectedSlot,
                    dragFrom = dragFrom,
                    dragTarget = dragTarget,
                    dragPoint = dragPoint,
                    onTapSlot = { index ->
                        if (inline || !fullPageEditing || writing) return@HoneycombLayoutPreview
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
                        if (writing) return@HoneycombLayoutPreview
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
                            frame.radius * 1.8f).takeIf { it >= 0 }
                        if (source >= current.cells.size) dragTarget = null
                    },
                    onDragEnd = {
                        val from = dragFrom
                        val to = dragTarget
                        dragFrom = null
                        dragTarget = null
                        dragPoint = Offset.Zero
                        if (from != null && to != null) swapSlots(from, to)
                    },
                    onDragCancel = {
                        dragFrom = null
                        dragTarget = null
                        dragPoint = Offset.Zero
                    },
                )
            if (!inline) {
            TextButton(
                text = "重新紧凑排列",
                enabled = !writing && current.cells.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                onClick = { operationFeedback = null; showCompactConfirmation = true },
            )
        }
        }
        if (!inline) OverlayDialog(
            show = showCompactConfirmation,
            title = "重新紧凑排列？",
            summary = "将移除空位并重新排列所有蜂巢应用，当前手动位置会被替换。",
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
                        selectedSlot = null
                        refreshPositions("已保存；系统界面（SystemUI）会在后续手势读取新布局，无需重启。") {
                            configStore.compactHoneycombPositions()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun HoneycombIconAction(
    description: String,
    imageVector: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(48.dp).semantics { contentDescription = description },
            minWidth = 48.dp,
            minHeight = 48.dp,
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = description,
                tint = if (enabled) MiuixTheme.colorScheme.onBackground
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun HoneycombLayoutPreview(
    modifier: Modifier,
    layout: HoneycombLayoutState,
    showLabels: Boolean,
    editing: Boolean,
    selectedSlot: Int?,
    dragFrom: Int?,
    dragTarget: Int?,
    dragPoint: Offset,
    onTapSlot: (Int) -> Unit,
    onDragStart: (PointerSize) -> Unit,
    onDrag: (PointerSize) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val currentOnTapSlot = rememberUpdatedState(onTapSlot)
    val currentOnDragStart = rememberUpdatedState(onDragStart)
    val currentOnDrag = rememberUpdatedState(onDrag)
    val currentOnDragEnd = rememberUpdatedState(onDragEnd)
    val currentOnDragCancel = rememberUpdatedState(onDragCancel)
    val primary = MiuixTheme.colorScheme.primary
    val background = MiuixTheme.colorScheme.background
    val onSurface = MiuixTheme.colorScheme.onSurface
    val emptySlot = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.12f)
    val surface = MiuixTheme.colorScheme.surfaceContainer
    Canvas(
        modifier = modifier
            .then(
                Modifier.pointerInput(layout, editing) {
                    detectTapGestures { point ->
                        val frame = honeycombFrame(layout, size.width.toFloat(), size.height.toFloat(), density)
                        val index = nearestHoneycombSlot(frame, point.x, point.y,
                            frame.radius * 1.7f)
                        if (index >= 0) currentOnTapSlot.value(index)
                    }
                },
            )
            .pointerInput(layout, editing) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { point -> currentOnDragStart.value(PointerSize(
                        point.x, point.y, size.width.toFloat(), size.height.toFloat(),
                    )) },
                    onDrag = { change, _ ->
                        currentOnDrag.value(PointerSize(
                            change.position.x, change.position.y,
                            size.width.toFloat(), size.height.toFloat(),
                        ))
                        change.consume()
                    },
                    onDragEnd = { currentOnDragEnd.value() },
                    onDragCancel = { currentOnDragCancel.value() },
                )
            },
    ) {
        val frame = honeycombFrame(layout, size.width.toFloat(), size.height.toFloat(), density)
        drawRoundRect(
            color = background,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
        )
        frame.centers.forEachIndexed { index, center ->
            val cell = layout.cells[index]
            val isDragged = index == dragFrom
            drawCircle(
                color = when {
                    cell.target == null -> emptySlot
                    isDragged -> surface.copy(alpha = 0.55f)
                    else -> surface
                },
                radius = frame.radius,
                center = center,
            )
            if (cell.icon != null) {
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
                    alpha = if (isDragged) 0.24f else 1f,
                )
            }
            if (editing && selectedSlot == index) {
                drawCircle(color = primary, radius = frame.radius + 7.dp.toPx(),
                    center = center, style = Stroke(width = 3.dp.toPx()))
            }
            if (dragFrom != null && dragTarget == index) {
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
                drawCircle(
                    color = primary.copy(alpha = 0.18f),
                    radius = radius + 8.dp.toPx(),
                    center = dragPoint,
                )
                drawCircle(
                    color = primary,
                    radius = radius + 5.dp.toPx(),
                    center = dragPoint,
                    style = Stroke(width = 2.dp.toPx()),
                )
                drawCircle(
                    color = surface,
                    radius = radius,
                    center = dragPoint,
                )
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
        if (showLabels) drawIntoCanvas { canvas ->
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

package com.leaf.hyperdragshare.codex

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowInsets
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.state.ToggleableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import java.io.IOException
import kotlin.math.abs

/** Module-process side shelf; storage and image grants stay under the module UID. */
object StashOverlayPanel {
    private val main = Handler(Looper.getMainLooper())
    private var active: Shelf? = null

    @JvmStatic fun show(context: Context) {
        val app = context.applicationContext
        main.post {
            if (app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return@post
            active?.takeIf { it.embedded != null }?.let { it.expand(); return@post }
            if (!Settings.canDrawOverlays(app)) {
                Toast.makeText(app, "请先在“模块设置→悬浮窗权限”中授权，再打开侧边中转站", Toast.LENGTH_LONG).show()
                return@post
            }
            val shelf = active ?: Shelf(app).also { active = it }
            shelf.expand()
        }
    }

    /** Saving should leave the source application usable, without opening a tray over it. */
    @JvmStatic fun contentAdded(context: Context) {
        val app = context.applicationContext
        main.post {
            if (app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return@post
            // The content is already durable in StashStore. Refresh only an open shelf.
            active?.refresh()
        }
    }

    @JvmStatic fun dismiss() {
        main.post {
            active?.let { shelf ->
                if (shelf.embedded != null) shelf.embedded.requestClose() else shelf.overlay.dismiss()
            }
        }
    }

    /** Mount the same module-owned shelf content in the native sidebar's passive surface. */
    @JvmStatic internal fun attachSurface(app: Context, host: StashSurfaceHost) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Sidebar surfaces attach on the main thread" }
        check(!app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            "Unlock before opening stash"
        }
        if (active?.embedded === host) {
            active?.expand()
            return
        }
        check(active?.overlay?.isDragHandoffActive != true) { "Finish the active content drag first" }
        active?.let { previous ->
            if (previous.embedded != null) previous.embedded.requestClose()
            previous.overlay.dismiss()
        }
        check(active == null) { "Previous stash surface is still active" }
        val shelf = Shelf(app.applicationContext, host).also { active = it }
        host.onBack = shelf::handleHostBack
        try {
            shelf.expand()
        } catch (error: RuntimeException) {
            shelf.overlay.dismiss()
            throw error
        }
    }

    private class Shelf @JvmOverloads constructor(private val app: Context, val embedded: StashSurfaceHost? = null) {
        private val position = app.getSharedPreferences("stash_window_position", Context.MODE_PRIVATE)
        private var onLeft by mutableStateOf(position.getBoolean("left", false))
        private var centerY = position.getFloat("center_y", .5f)
        private var dragLeft: Float? = null
        private var dragStartRawX = 0f
        private var dragStartRawY = 0f
        private var dragStartLeft = 0
        private var dragStartCenterY = 0f
        private var dragStartAnchorY = 0f
        private var moving = false
        private var imeBottomInset = 0
        private val displayManager = app.getSystemService(DisplayManager::class.java)
        private val display = embedded?.context?.display
            ?: requireNotNull(displayManager.getDisplay(Display.DEFAULT_DISPLAY))
        private val context = embedded?.context ?: app.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var entries by mutableStateOf<List<StashStore.Entry>>(emptyList())
        private val listState = LazyListState()
        private var loading by mutableStateOf(false)
        private var reloadRequested = false
        private var message by mutableStateOf<String?>(null)
        private var draggingContent by mutableStateOf(false)
        private var embeddedDragRequestPending = false
        private var dragContentWidthDp = 0f
        private var dragContentHeightDp = 0f
        private var selectionMode by mutableStateOf(false)
        private var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private var confirmDelete by mutableStateOf(false)
        private var updating by mutableStateOf(false)
        private var preview by mutableStateOf<StashStore.Entry?>(null)
        private var openedBatchId by mutableStateOf<String?>(null)
        private val screenOffReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (embedded != null) embedded.requestClose() else overlay.dismiss()
            }
        }
        private val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == display.displayId && active === this@Shelf) resize()
            }
        }
        val overlay: InteractiveOverlayWindow = InteractiveOverlayWindow(context, OverlayWindowPolicy.application(),
            onDismissed = {
                app.unregisterReceiver(screenOffReceiver)
                displayManager.unregisterDisplayListener(displayListener)
                reloadRequested = false
                scope.cancel()
                if (active === this) active = null
            },
            onFailure = { report("中转站窗口无法显示", it) },
            embeddedHost = embedded,
        ) { Content() }

        init {
            if (embedded == null) overlay.window.decorView.setOnApplyWindowInsetsListener { _, insets ->
                // Decor insets are relative to this small window. Placement uses display coordinates.
                val displayInsets = context.getSystemService(WindowManager::class.java)
                    .maximumWindowMetrics.windowInsets
                val imeType = WindowInsets.Type.ime()
                val nextImeBottom = if (displayInsets.isVisible(imeType)) {
                    displayInsets.getInsets(imeType).bottom
                } else {
                    0
                }
                if (imeBottomInset != nextImeBottom) {
                    imeBottomInset = nextImeBottom
                    resize()
                }
                insets
            }
            app.registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
                Context.RECEIVER_NOT_EXPORTED)
            displayManager.registerDisplayListener(displayListener, main)
        }

        fun refresh() {
            if (overlay.show()) reload()
        }

        fun expand() {
            if (overlay.isDragHandoffActive) return
            resize()
            if (overlay.show()) reload()
        }

        fun collapse() {
            if (overlay.isDragHandoffActive) return
            if (embedded != null) {
                embedded.requestClose()
                return
            }
            overlay.dismiss()
        }

        private fun startContentDrag(source: View, clip: ClipData,
            shadow: View.DragShadowBuilder, flags: Int): Boolean {
            val operation = OperationTrace.read(clip)
            if (embedded != null) {
                if (embeddedDragRequestPending) return false
                embeddedDragRequestPending = true
                overlay.observeDragHandoff({ active === this@Shelf },
                    onDropped = ::collapse,
                    onFinished = { draggingContent = false })
                embedded.startDrag(clip, shadow, flags,
                    onStarted = { started ->
                        OperationTrace.event(operation, "module_drag_start_callback",
                            if (started) "reported_true" else "reported_false")
                        if (started) {
                            val density = context.resources.displayMetrics.density
                            dragContentWidthDp = overlay.composeView.width / density
                            dragContentHeightDp = overlay.composeView.height / density
                            draggingContent = true
                        } else {
                            embeddedDragRequestPending = false
                            overlay.cancelTextDragObservation()
                            Toast.makeText(context, "系统未能开始拖拽，请重试", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onEnded = { result ->
                        OperationTrace.event(operation, "module_drag_end_callback",
                            if (result) "drop_accepted" else "not_accepted_or_aborted")
                        finishEmbeddedDrag(result)
                    })
                return true
            }
            var started = false
            overlay.observeDragHandoff({ active === this@Shelf },
                onDropped = ::collapse,
                onStarted = {
                    OperationTrace.event(operation, "source_drag_start", "observed")
                    val density = context.resources.displayMetrics.density
                    val width = overlay.window.attributes.width
                    val height = overlay.window.attributes.height
                    dragContentWidthDp = width / density
                    dragContentHeightDp = height / density
                    draggingContent = true
                    resize(allowDuringDrag = true)
                },
                onFinished = {
                    OperationTrace.event(operation, "source_drag_finish_callback",
                        if (it) "drop_accepted" else "not_accepted_or_observation_cancelled")
                    draggingContent = false
                })
            try {
                started = source.startDragAndDrop(clip, shadow, null, flags)
                OperationTrace.event(operation, "source_drag_dispatch",
                    if (started) "native_start_returned_true" else "native_start_returned_false")
                return started
            } finally {
                // Failed startup has no ENDED event; leave the original content and selection visible.
                if (!started) overlay.cancelTextDragObservation()
            }
        }

        private fun finishEmbeddedDrag(accepted: Boolean) {
            embeddedDragRequestPending = false
            overlay.cancelTextDragObservation()
            if (accepted) collapse()
            else Toast.makeText(context,
                "目标应用未确认接收，内容仍保留在中转站", Toast.LENGTH_SHORT).show()
        }

        private fun back() {
            when {
                confirmDelete -> confirmDelete = false
                preview != null -> preview = null
                openedBatchId != null -> { openedBatchId = null; listState.requestScrollToItem(0) }
                else -> collapse()
            }
        }

        fun handleHostBack() = back()

        private fun resize(allowDuringDrag: Boolean = false) {
            if (embedded != null) return
            if (overlay.isDragHandoffActive && !allowDuringDrag) return
            val windowMetrics = context.getSystemService(WindowManager::class.java).maximumWindowMetrics
            val metrics = windowMetrics.bounds
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val navigationGestures = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.mandatorySystemGestures())
            val bottomInset = maxOf(insets.bottom, navigationGestures.bottom, imeBottomInset)
            val density = context.resources.displayMetrics.density
            val showPanel = !draggingContent
            overlay.window.attributes = overlay.window.attributes.apply {
                // Both dimensions are at most 40% of the display: area <= 16%, below 1/6.
                width = if (showPanel) minOf((200 * density).toInt(), (metrics.width() * .4f).toInt())
                    else (24 * density).toInt()
                height = if (showPanel) minOf((320 * density).toInt(), (metrics.height() * .4f).toInt())
                    else (96 * density).toInt()
                gravity = Gravity.LEFT or Gravity.TOP
                val rightEdge = (metrics.width() - insets.right - width).coerceAtLeast(insets.left)
                x = (dragLeft?.toInt() ?: if (onLeft) insets.left else rightEdge)
                    .coerceIn(insets.left, rightEdge)
                y = (centerY * metrics.height() - height / 2f).toInt()
                    .coerceIn(insets.top, (metrics.height() - bottomInset - height).coerceAtLeast(insets.top))
                flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    if (draggingContent) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0
            }
        }

        private fun finishMove() {
            val width = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds.width()
            onLeft = overlay.window.attributes.x + overlay.window.attributes.width / 2 < width / 2
            dragLeft = null
            position.edit().putBoolean("left", onLeft).putFloat("center_y", centerY).apply()
            resize()
        }

        private fun handleMove(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartRawX = event.rawX
                    dragStartRawY = event.rawY
                    dragStartLeft = overlay.window.attributes.x
                    dragStartCenterY = overlay.window.attributes.y + overlay.window.attributes.height / 2f
                    dragStartAnchorY = centerY
                    moving = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - dragStartRawX
                    val dy = event.rawY - dragStartRawY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moving = true
                    if (moving) {
                        val height = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds.height()
                        dragLeft = dragStartLeft + dx
                        centerY = ((dragStartCenterY + dy) / height).coerceIn(0f, 1f)
                        resize()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (moving) finishMove()
                    moving = false
                }
                MotionEvent.ACTION_CANCEL -> {
                    dragLeft = null
                    centerY = dragStartAnchorY
                    resize()
                    moving = false
                }
            }
            return true
        }

        @OptIn(ExperimentalComposeUiApi::class)
        @Composable private fun MoveHandle() {
            Column(
                Modifier.fillMaxWidth().height(48.dp)
                    .then(if (embedded == null) Modifier.systemGestureExclusion()
                    .pointerInteropFilter { handleMove(it) } else Modifier)
                    .semantics { contentDescription = if (embedded == null) "拖动中转站，松手自动贴边" else "中转站" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.width(34.dp).height(4.dp).background(
                    MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = .55f), RoundedCornerShape(2.dp)))
                Text(when {
                    confirmDelete -> "确认删除"
                    preview != null -> "图片预览"
                    selectionMode -> "已选 ${selectedIds.size} 项"
                    openedBatchId != null -> "分组"
                    else -> "中转站"
                },
                    color = MiuixTheme.colorScheme.onSurface, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        private fun reload() {
            if (loading) {
                reloadRequested = true
                return
            }
            scope.launch {
                loading = true
                message = null
                try {
                    val updated = withContext(Dispatchers.IO) { StashStore(app).use { it.listEntries() } }
                    entries = updated
                    if (openedBatchId != null && entries.none { it.batchId == openedBatchId }) openedBatchId = null
                    preview = preview?.let { image -> entries.firstOrNull { it.id == image.id } }
                    selectedIds = selectedIds.intersect(entries.map { it.id }.toSet())
                } catch (error: IOException) {
                    report("读取中转站失败", error)
                } catch (error: SecurityException) {
                    report("系统拒绝读取中转站", error)
                } finally {
                    loading = false
                    if (reloadRequested) {
                        reloadRequested = false
                        reload()
                    }
                }
            }
        }

        private fun deleteSelected() {
            val targets = selectedEntries()
            confirmDelete = false
            updating = true
            scope.launch {
                try {
                    for (entry in targets) {
                        withContext(Dispatchers.IO) { StashStore(app).use { it.delete(entry.id) } }
                        entries = entries.filterNot { it.id == entry.id }
                        selectedIds = selectedIds - entry.id
                    }
                    cancelSelection()
                    if (openedBatchId != null && entries.none { it.batchId == openedBatchId }) openedBatchId = null
                } catch (error: IOException) {
                    report("删除未完成，剩余内容已保留", error)
                } catch (error: SecurityException) {
                    report("系统拒绝删除，剩余内容已保留", error)
                } finally {
                    updating = false
                }
            }
        }

        private fun regroup(selected: List<StashStore.Entry>, split: Boolean) {
            if (updating) return
            updating = true
            message = null
            scope.launch {
                try {
                    val (batchId, refreshed) = withContext(Dispatchers.IO) {
                        StashStore(app).use { store ->
                            val ids = selected.map { it.id }
                            val batch = if (split) { store.ungroupEntries(ids); null }
                                else store.groupEntries(ids)
                            batch to store.listEntries()
                        }
                    }
                    entries = refreshed
                    openedBatchId = batchId
                    cancelSelection()
                    listState.requestScrollToItem(0)
                } catch (error: IOException) {
                    report(if (split) "无法拆开分组" else "无法合并分组", error)
                } catch (error: SecurityException) {
                    report("系统拒绝修改分组", error)
                } finally {
                    updating = false
                }
            }
        }

        private fun cancelSelection() {
            selectedIds = emptySet()
            selectionMode = false
        }

        private fun selectedEntries() = stashBatches(entries).flatten().filter { it.id in selectedIds }

        private fun openText(entry: StashStore.Entry) {
            if (BigBangOverlayLauncher.showText(app, entry.text.orEmpty(), -1, -1)) collapse()
        }

        fun report(label: String, error: Exception) {
            DragShareLog.w("DragShare/StashWindow", label, error)
            message = "$label：${error.message.orEmpty()}"
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }

        @Composable private fun ShelfItem(entry: StashStore.Entry, dragSelection: List<StashStore.Entry>) {
            val selected = entry.id in selectedIds
            val open = {
                if (selectionMode) {
                    selectedIds = if (selected) selectedIds - entry.id else selectedIds + entry.id
                } else if (entry.kind == StashStore.KIND_TEXT) openText(entry)
                else preview = entry
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp).then(
                    if (selected) Modifier.border(
                        1.dp, MiuixTheme.colorScheme.primary.copy(alpha = .72f), RoundedCornerShape(16.dp))
                    else Modifier
                ),
                colors = CardDefaults.defaultColors(color = if (selected)
                    MiuixTheme.colorScheme.primary.copy(alpha = .12f)
                    else MiuixTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(8.dp)) {
                    if (entry.kind == StashStore.KIND_IMAGE) {
                        Box(Modifier.fillMaxWidth()) {
                            StashDraggableContent(if (selectionMode) dragSelection else listOf(entry),
                                Modifier.fillMaxWidth(), enabled = !updating, draggable = !selectionMode || selected,
                                startDrag = ::startContentDrag, onClick = open) {
                                StashImage(entry, 480, Modifier.fillMaxWidth().height(96.dp), ContentScale.Fit)
                            }
                            if (selectionMode) {
                                Checkbox(
                                    state = if (selected) ToggleableState.On else ToggleableState.Off,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(44.dp)
                                        .background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                                        .semantics {
                                            contentDescription = if (selected) "取消选择这条内容" else "选择这条内容"
                                        },
                                    enabled = !updating,
                                    onClick = open,
                                )
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            StashDraggableContent(if (selectionMode) dragSelection else listOf(entry),
                                Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !updating,
                                draggable = !selectionMode || selected, startDrag = ::startContentDrag, onClick = open) {
                                Text(entry.text.orEmpty(), Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(
                                    start = 8.dp, top = 8.dp, bottom = 8.dp, end = if (selectionMode) 52.dp else 8.dp),
                                    fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3,
                                    overflow = TextOverflow.Ellipsis, color = MiuixTheme.colorScheme.onSurface)
                            }
                            if (selectionMode) {
                                Checkbox(
                                    state = if (selected) ToggleableState.On else ToggleableState.Off,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(44.dp)
                                        .background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                                        .semantics {
                                            contentDescription = if (selected) "取消选择这条内容" else "选择这条内容"
                                        },
                                    enabled = !updating,
                                    onClick = open,
                                )
                            }
                        }
                    }
                }
            }
        }

        @Composable private fun ShelfBatch(batch: List<StashStore.Entry>, dragSelection: List<StashStore.Entry>) {
            if (batch.size == 1) {
                ShelfItem(batch.first(), dragSelection)
                return
            }
            val ids = batch.map { it.id }.toSet()
            val selectedCount = ids.count { it in selectedIds }
            StashBatchCard(batch, Modifier.padding(horizontal = 4.dp), imageHeight = 56.dp,
                enabled = !updating, selectionMode = selectionMode, selectedCount = selectedCount,
                dragEntries = if (selectionMode) dragSelection else batch,
                startDrag = ::startContentDrag, onClick = {
                    if (selectionMode) {
                        selectedIds = if (selectedCount == batch.size) selectedIds - ids else selectedIds + ids
                    } else {
                        openedBatchId = batch.first().batchId
                        listState.requestScrollToItem(0)
                    }
                })
        }

        @Composable private fun Content() {
            val dark = DragShareSettings.readLocal(app).isDarkThemeInComposition()
            val visibleEntries = entries.filter {
                openedBatchId == null || it.batchId == openedBatchId
            }.let { if (openedBatchId != null) it.sortedBy { entry -> entry.batchOrder } else it }
            val visibleBatches = if (openedBatchId != null) visibleEntries.map { listOf(it) }
                else stashBatches(visibleEntries)
            val selected = selectedEntries()
            LaunchedEffect(openedBatchId) { listState.requestScrollToItem(0) }
            MiuixTheme(controller = remember(dark) {
                ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
            }) {
                Box(Modifier.fillMaxSize()) {
                    Column(
                        // Keep the drag source composed at its original size while the window shrinks.
                        modifier = (if (draggingContent)
                            Modifier.requiredSize(dragContentWidthDp.dp, dragContentHeightDp.dp)
                            else Modifier.fillMaxSize())
                            .graphicsLayer { alpha = if (draggingContent) 0f else 1f }
                            .clip(RoundedCornerShape(24.dp))
                            .background(MiuixTheme.colorScheme.surface)
                            .border(1.dp, MiuixTheme.colorScheme.onSurface.copy(
                                alpha = if (dark) .28f else .16f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                        ) {
                            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (preview != null || openedBatchId != null || confirmDelete) {
                                    OverlayIconAction(MiuixIcons.Back, MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        onClick = ::back, contentDescription = "返回内容列表")
                                } else {
                                    OverlayIconAction(if (selectionMode) MiuixIcons.Close else MiuixIcons.SelectAll,
                                        if (selectionMode) MiuixTheme.colorScheme.primary
                                        else MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        enabled = !updating && (selectionMode || visibleEntries.isNotEmpty()),
                                        onClick = { if (selectionMode) cancelSelection() else selectionMode = true },
                                        contentDescription = if (selectionMode) "退出多选" else "多选拖出")
                                }
                                Box(Modifier.weight(1f)) { MoveHandle() }
                                OverlayIconAction(StashCollapseIcon, MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    onClick = ::collapse, contentDescription = "收起中转站")
                            }
                        }
                        message?.let { error ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                            ) {
                                Text(error, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), fontSize = 11.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                        val image = preview
                        when {
                            confirmDelete -> Card(
                                Modifier.weight(1f).fillMaxWidth(),
                                insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                            ) {
                                Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.Center) {
                                    Text("删除已选 ${selectedIds.size} 项？", fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurface)
                                    Text("删除后无法恢复", fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    Row(Modifier.fillMaxWidth()) {
                                        TextButton("取消", { confirmDelete = false }, modifier = Modifier.weight(1f).height(44.dp))
                                        TextButton("删除", ::deleteSelected, modifier = Modifier.weight(1f).height(44.dp))
                                    }
                                }
                            }
                            image != null -> Card(
                                Modifier.weight(1f).fillMaxWidth(),
                                insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                            ) {
                                Column(Modifier.fillMaxSize().padding(4.dp)) {
                                    StashDraggableContent(if (selectionMode && image.id in selectedIds) selected else listOf(image),
                                        Modifier.fillMaxWidth().weight(1f), enabled = !updating,
                                        startDrag = ::startContentDrag, onClick = {}) {
                                        StashImage(image, 1200, Modifier.fillMaxSize(), ContentScale.Fit)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        OverlayIconAction(MiuixIcons.Scan, MiuixTheme.colorScheme.onSurfaceVariantActions,
                                            onClick = {
                                                val uri = StashImageProvider.uriForEntry(image.id)
                                                if (ImageOcrOverlay.startWithResult(app, uri) { text ->
                                                    if (active === this@Shelf &&
                                                        !app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                                                        if (text == null) {
                                                            if (entries.any { it.id == image.id }) {
                                                                preview = image
                                                                expand()
                                                            }
                                                        } else {
                                                            BigBangOverlayLauncher.showText(app, text, -1, -1)
                                                            collapse()
                                                        }
                                                    }
                                                }) { collapse() }
                                            }, contentDescription = "识别图片文字")
                                    }
                                }
                            }
                            visibleEntries.isEmpty() -> Card(
                                Modifier.weight(1f).fillMaxWidth(),
                                insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                            ) {
                                Column(Modifier.fillMaxSize().padding(12.dp),
                                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (loading) CircularProgressIndicator()
                                    Text(if (loading) "正在读取中转站" else "中转站还是空的",
                                        fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface)
                                    if (!loading) Text("拖拽或分享内容存入这里", fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                            }
                            else -> LazyColumn(Modifier.weight(1f), state = listState,
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(visibleBatches, key = {
                                    if (openedBatchId == null) it.first().batchId else it.first().id
                                }) { ShelfBatch(it, selected) }
                            }
                        }
                        if (updating) {
                            Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator()
                                Text("正在处理", Modifier.padding(start = 8.dp), fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        } else if (selectionMode && !confirmDelete && preview == null) {
                            StashGroupingActions(selected, true,
                                canUngroup = selected.any { it.id in stashGroupedEntryIds(entries) },
                                onGroup = { regroup(selected, false) }, onUngroup = { regroup(selected, true) })
                        }
                        if (selectionMode && !confirmDelete && preview == null) {
                            Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                val allSelected = visibleEntries.isNotEmpty() && visibleEntries.all { it.id in selectedIds }
                                OverlayIconAction(MiuixIcons.SelectAll,
                                    if (allSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    enabled = !updating && visibleEntries.isNotEmpty(),
                                    onClick = {
                                        val visibleIds = visibleEntries.map { it.id }.toSet()
                                        selectedIds = if (allSelected) selectedIds - visibleIds else selectedIds + visibleIds
                                    }, contentDescription = if (allSelected) "取消当前列表全选" else "全选当前列表")
                                OverlayIconAction(MiuixIcons.Delete, MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    enabled = !updating && selectedIds.isNotEmpty(),
                                    onClick = { confirmDelete = true }, contentDescription = "移除所选内容")
                            }
                        }
                    }
                }
            }
        }
    }
}

private val StashCollapseIcon = ImageVector.Builder(
    name = "CollapseShelf", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).apply {
    path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(6f, 9f)
        lineTo(12f, 15f)
        lineTo(18f, 9f)
    }
}.build()

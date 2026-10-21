package com.leaf.hyperdragshare.codex

import android.app.KeyguardManager
import android.content.BroadcastReceiver
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
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowInsets
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
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
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
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
            if (!Settings.canDrawOverlays(app)) {
                Toast.makeText(app, "请先在“模块设置→悬浮窗权限”中授权，再打开侧边中转站", Toast.LENGTH_LONG).show()
                return@post
            }
            val shelf = active ?: Shelf(app).also { active = it }
            shelf.expand()
        }
    }

    @JvmStatic fun showCollapsed(context: Context) {
        val app = context.applicationContext
        main.post {
            if (app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return@post
            if (!Settings.canDrawOverlays(app)) {
                Toast.makeText(app, "悬浮窗权限已关闭，无法恢复中转站侧条", Toast.LENGTH_LONG).show()
                return@post
            }
            val shelf = active ?: Shelf(app).also { active = it }
            shelf.collapse()
            shelf.refresh()
        }
    }

    /** Saving should leave the source application usable, without opening a tray over it. */
    @JvmStatic fun contentAdded(context: Context) {
        val app = context.applicationContext
        main.post {
            if (app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return@post
            if (!Settings.canDrawOverlays(app)) {
                Toast.makeText(app, "内容已保存；开启模块悬浮窗权限后可使用中转站侧条", Toast.LENGTH_LONG).show()
                return@post
            }
            val shelf = active ?: Shelf(app).also { active = it; it.collapse() }
            shelf.refresh()
        }
    }

    @JvmStatic fun dismiss() {
        main.post { active?.overlay?.dismiss() }
    }

    private class Shelf(private val app: Context) {
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
        private val displayManager = app.getSystemService(DisplayManager::class.java)
        private val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        private val context = app.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var entries by mutableStateOf<List<StashStore.Entry>>(emptyList())
        private val listState = LazyListState()
        private var loading by mutableStateOf(false)
        private var reloadRequested = false
        private var message by mutableStateOf<String?>(null)
        private var expanded by mutableStateOf(true)
        private var selectionMode by mutableStateOf(false)
        private var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private var confirmDelete by mutableStateOf(false)
        private var updating by mutableStateOf(false)
        private var preview by mutableStateOf<StashStore.Entry?>(null)
        private var openedBatchId by mutableStateOf<String?>(null)
        private val screenOffReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                overlay.dismiss()
            }
        }
        private val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY && active === this@Shelf) resize()
            }
        }
        val overlay: InteractiveOverlayWindow = InteractiveOverlayWindow(context, OverlayWindowPolicy.application(),
            onDismissed = {
                app.unregisterReceiver(screenOffReceiver)
                displayManager.unregisterDisplayListener(displayListener)
                scope.cancel()
                if (active === this) active = null
            },
            onFailure = { report("中转站窗口无法显示", it) },
        ) { Content() }

        init {
            app.registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
                Context.RECEIVER_NOT_EXPORTED)
            displayManager.registerDisplayListener(displayListener, main)
        }

        fun refresh() {
            if (overlay.show()) reload()
        }

        fun expand() {
            expanded = true
            resize()
            if (overlay.show()) reload()
        }

        fun collapse() {
            confirmDelete = false
            expanded = false
            resize()
        }

        private fun back() {
            when {
                confirmDelete -> confirmDelete = false
                preview != null -> preview = null
                openedBatchId != null -> { openedBatchId = null; listState.requestScrollToItem(0) }
                else -> collapse()
            }
        }

        private fun resize() {
            val windowMetrics = context.getSystemService(WindowManager::class.java).maximumWindowMetrics
            val metrics = windowMetrics.bounds
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val density = context.resources.displayMetrics.density
            overlay.window.attributes = overlay.window.attributes.apply {
                // Both dimensions are at most 40% of the display: area <= 16%, below 1/6.
                width = if (expanded) minOf((200 * density).toInt(), (metrics.width() * .4f).toInt())
                    else (24 * density).toInt()
                height = if (expanded) minOf((320 * density).toInt(), (metrics.height() * .4f).toInt())
                    else (96 * density).toInt()
                gravity = Gravity.LEFT or Gravity.TOP
                val rightEdge = (metrics.width() - insets.right - width).coerceAtLeast(insets.left)
                x = (dragLeft?.toInt() ?: if (onLeft) insets.left else rightEdge)
                    .coerceIn(insets.left, rightEdge)
                y = (centerY * metrics.height() - height / 2f).toInt()
                    .coerceIn(insets.top, (metrics.height() - insets.bottom - height).coerceAtLeast(insets.top))
                flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    if (expanded) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
        }

        private fun finishMove() {
            val width = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds.width()
            onLeft = overlay.window.attributes.x + overlay.window.attributes.width / 2 < width / 2
            dragLeft = null
            position.edit().putBoolean("left", onLeft).putFloat("center_y", centerY).apply()
            resize()
        }

        private fun handleMove(event: MotionEvent, expandOnTap: Boolean): Boolean {
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
                    if (moving) finishMove() else if (expandOnTap) expand()
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
                Modifier.fillMaxWidth().height(44.dp).systemGestureExclusion()
                    .semantics { contentDescription = "拖动中转站，松手自动贴边" }
                    .pointerInteropFilter { handleMove(it, false) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.width(28.dp).height(4.dp).background(
                    MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = .45f), RoundedCornerShape(2.dp)))
                Text(if (selectionMode) "已选 ${selectedIds.size} · 按住拖出" else "中转站 · ${entries.size}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        @OptIn(ExperimentalComposeUiApi::class)
        @Composable private fun CollapsedHandle() {
            Box(
                Modifier.fillMaxSize()
                    .systemGestureExclusion()
                    .semantics {
                        contentDescription = "展开中转站，${entries.size} 项；拖动后自动贴边"
                        onClick("展开中转站") { expand(); true }
                    }
                    .pointerInteropFilter { handleMove(it, true) },
                contentAlignment = if (onLeft) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Box(
                    Modifier.width(5.dp).height(64.dp)
                        .background(Color(0xFF8E8E93),
                            RoundedCornerShape(3.dp))
                )
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
                    val newestChanged = updated.firstOrNull()?.id != entries.firstOrNull()?.id
                    entries = updated
                    if (openedBatchId != null && entries.none { it.batchId == openedBatchId }) openedBatchId = null
                    preview = preview?.let { image -> entries.firstOrNull { it.id == image.id } }
                    if (newestChanged && !expanded && !selectionMode && preview == null && openedBatchId == null) {
                        // Apply with the new item provider, rather than keeping the old first key.
                        listState.requestScrollToItem(0)
                    }
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
            if (BigBangOverlayLauncher.showText(app, entry.text.orEmpty(), -1, -1, null)) collapse()
        }

        private fun report(label: String, error: Exception) {
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
            Card(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Column(Modifier.padding(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StashDraggableContent(if (selectionMode) dragSelection else listOf(entry), Modifier.weight(1f),
                            enabled = !updating, draggable = !selectionMode || selected,
                            onStarted = ::collapse, onClick = open) {
                            if (entry.kind == StashStore.KIND_IMAGE) {
                                StashImage(entry, 480, Modifier.fillMaxWidth().height(64.dp), ContentScale.Fit)
                            } else {
                                Text(entry.text.orEmpty(), fontSize = 13.sp, maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MiuixTheme.colorScheme.onSurface)
                            }
                        }
                        if (selectionMode) OverlayIconAction(
                            imageVector = if (selected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                            tint = MiuixTheme.colorScheme.primary,
                            enabled = !updating,
                            onClick = open,
                            contentDescription = if (selected) "取消选择这条内容" else "选择这条内容",
                        )
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
                onDragStarted = ::collapse, onClick = {
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
                BackHandler(enabled = expanded, onBack = ::back)
                if (!expanded) {
                    CollapsedHandle()
                } else {
                    Scaffold {
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) { MoveHandle() }
                                OverlayIconAction(Icons.Outlined.ExpandMore,
                                    MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    onClick = ::collapse, contentDescription = "收起中转站")
                            }
                            if (openedBatchId != null && preview == null && !confirmDelete) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    OverlayIconAction(Icons.Outlined.ArrowBack,
                                        MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        onClick = ::back, contentDescription = "返回全部内容")
                                    Text(stashBatchLabel(visibleEntries), fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                            }
                            message?.let {
                                Text(it, Modifier.padding(horizontal = 6.dp), fontSize = 11.sp,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                            val image = preview
                            when {
                                confirmDelete -> Column(Modifier.weight(1f).padding(8.dp),
                                    verticalArrangement = Arrangement.Center) {
                                    Text("删除已选 ${selectedIds.size} 项？", fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurface)
                                    Text("删除后无法恢复", fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    Row {
                                        TextButton("取消", { confirmDelete = false }, modifier = Modifier.weight(1f))
                                        TextButton("删除", ::deleteSelected, modifier = Modifier.weight(1f))
                                    }
                                }
                                image != null -> Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                                    StashDraggableContent(if (selectionMode && image.id in selectedIds) selected else listOf(image),
                                        Modifier.fillMaxWidth().weight(1f),
                                        enabled = !updating, onStarted = ::collapse, onClick = {}) {
                                        StashImage(image, 1200, Modifier.fillMaxSize(), ContentScale.Fit)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        OverlayIconAction(Icons.Outlined.ArrowBack,
                                            MiuixTheme.colorScheme.onSurfaceVariantActions,
                                            onClick = ::back, contentDescription = "返回内容列表")
                                        OverlayIconAction(Icons.Outlined.DocumentScanner,
                                            MiuixTheme.colorScheme.onSurfaceVariantActions,
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
                                                            BigBangOverlayLauncher.showText(app, text, -1, -1, uri)
                                                            collapse()
                                                        }
                                                    }
                                                }) {
                                                    collapse()
                                                }
                                            }, contentDescription = "识别图片文字")
                                    }
                                }
                                visibleEntries.isEmpty() -> Box(Modifier.weight(1f).padding(8.dp),
                                    contentAlignment = Alignment.Center) {
                                    Text(if (loading) "正在读取…" else "拖拽或分享内容存入这里",
                                        fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                                else -> LazyColumn(Modifier.weight(1f), state = listState,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(visibleBatches, key = {
                                        if (openedBatchId == null) it.first().batchId else it.first().id
                                    }) { ShelfBatch(it, selected) }
                                }
                            }
                            if (updating) {
                                Text("正在处理…", Modifier.padding(6.dp), fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            } else if (selectionMode && !confirmDelete && preview == null) {
                                StashGroupingActions(selected, true,
                                    canUngroup = selected.any { it.id in stashGroupedEntryIds(entries) },
                                    onGroup = { regroup(selected, false) },
                                    onUngroup = { regroup(selected, true) })
                            }
                            if (!confirmDelete && preview == null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                OverlayIconAction(if (selectionMode) Icons.Outlined.Deselect else Icons.Outlined.CheckBoxOutlineBlank,
                                    if (selectionMode) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    enabled = !updating && (selectionMode || visibleEntries.isNotEmpty()),
                                    onClick = {
                                        if (selectionMode) cancelSelection() else selectionMode = true
                                    }, contentDescription = if (selectionMode) "清空选择" else "多选拖出")
                                if (selectionMode) {
                                    val allSelected = visibleEntries.isNotEmpty() &&
                                        visibleEntries.all { it.id in selectedIds }
                                    OverlayIconAction(Icons.Outlined.SelectAll,
                                        MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        enabled = !updating && visibleEntries.isNotEmpty(),
                                        onClick = {
                                            val visibleIds = visibleEntries.map { it.id }.toSet()
                                            selectedIds = if (allSelected) selectedIds - visibleIds
                                                else selectedIds + visibleIds
                                        }, contentDescription = if (allSelected) "取消当前列表全选" else "全选当前列表")
                                    OverlayIconAction(Icons.Outlined.Delete,
                                        MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        enabled = !updating && selectedIds.isNotEmpty(),
                                        onClick = { confirmDelete = true }, contentDescription = "移除所选内容")
                                }
                                OverlayIconAction(Icons.Outlined.Close,
                                    MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    onClick = overlay::dismiss, contentDescription = "关闭中转站入口")
                            }
                        }
                    }
                }
            }
        }
    }
}

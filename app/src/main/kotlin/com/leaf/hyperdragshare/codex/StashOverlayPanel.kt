package com.leaf.hyperdragshare.codex

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
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
            shelf.overlay.show()
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
        private val display = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        private val context = app.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var entries by mutableStateOf<List<StashStore.Entry>>(emptyList())
        private val listState = LazyListState()
        private var loading by mutableStateOf(false)
        private var message by mutableStateOf<String?>(null)
        private var expanded by mutableStateOf(true)
        private var selectionMode by mutableStateOf(false)
        private var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private var searching by mutableStateOf(false)
        private var query by mutableStateOf("")
        private var confirmDelete by mutableStateOf(false)
        private var deleting by mutableStateOf(false)
        private var preview by mutableStateOf<StashStore.Entry?>(null)
        private val screenOffReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                overlay.dismiss()
            }
        }
        val overlay: InteractiveOverlayWindow = InteractiveOverlayWindow(context, OverlayWindowPolicy.application(),
            onDismissed = {
                app.unregisterReceiver(screenOffReceiver)
                scope.cancel()
                if (active === this) active = null
            },
            onFailure = { report("中转站窗口无法显示", it) },
        ) { Content() }

        init {
            app.registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF),
                Context.RECEIVER_NOT_EXPORTED)
        }

        fun expand() {
            expanded = true
            resize()
            if (overlay.show()) reload()
        }

        private var openingSystemWindow by mutableStateOf(false)

        private fun openSystemWindow() {
            if (openingSystemWindow) return
            openingSystemWindow = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val command = "am start --user current --windowingMode 5 -n " +
                            BuildConfig.APPLICATION_ID + "/.StashActivity --ez system_stash_window true"
                        val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
                        try {
                            if (!process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)) {
                                throw IOException("Root 启动小窗超时")
                            }
                            val output = process.inputStream.bufferedReader().use { it.readText() }
                            if (process.exitValue() != 0 || output.contains("Error:")) {
                                throw IOException(output.trim().ifEmpty { "系统拒绝启动小窗" })
                            }
                        } finally {
                            process.destroy()
                        }
                    }
                    collapse()
                } catch (error: IOException) {
                    report("无法打开系统小窗，请检查 Root 授权", error)
                } catch (error: SecurityException) {
                    report("系统拒绝中转站小窗请求", error)
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    report("系统小窗启动被中断", error)
                } finally {
                    openingSystemWindow = false
                }
            }
        }

        fun collapse() {
            preview = null
            confirmDelete = false
            expanded = false
            resize()
        }

        private fun resize() {
            val metrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            val density = context.resources.displayMetrics.density
            overlay.window.attributes = overlay.window.attributes.apply {
                // Both dimensions are at most 40% of the display: area <= 16%, below 1/6.
                width = if (expanded) minOf((200 * density).toInt(), (metrics.width() * .4f).toInt())
                    else (24 * density).toInt()
                height = if (expanded) minOf((320 * density).toInt(), (metrics.height() * .4f).toInt())
                    else (96 * density).toInt()
                gravity = Gravity.LEFT or Gravity.TOP
                x = (dragLeft?.toInt() ?: if (onLeft) 0 else metrics.width() - width)
                    .coerceIn(0, (metrics.width() - width).coerceAtLeast(0))
                y = (centerY * metrics.height() - height / 2f).toInt()
                    .coerceIn(0, (metrics.height() - height).coerceAtLeast(0))
                flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    if (expanded) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
        }

        private fun finishMove() {
            val width = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.width()
            onLeft = overlay.window.attributes.x + overlay.window.attributes.width / 2 < width / 2
            dragLeft = null
            position.edit().putBoolean("left", onLeft).putFloat("center_y", centerY).apply()
            resize()
        }

        private fun switchSide() {
            onLeft = !onLeft
            dragLeft = null
            position.edit().putBoolean("left", onLeft).apply()
            resize()
        }

        @Composable private fun MoveHandle() {
            Text(
                if (selectionMode) "已选 ${selectedIds.size}" else "中转站 · ${entries.size}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().height(28.dp).systemGestureExclusion().pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragLeft = overlay.window.attributes.x.toFloat() },
                        onDragEnd = ::finishMove,
                        onDragCancel = ::finishMove,
                    ) { change, delta ->
                        change.consume()
                        val height = context.getSystemService(WindowManager::class.java)
                            .currentWindowMetrics.bounds.height()
                        dragLeft = (dragLeft ?: overlay.window.attributes.x.toFloat()) + delta.x
                        centerY = (centerY + delta.y / height).coerceIn(0f, 1f)
                        resize()
                    }
                },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        @Composable private fun CollapsedHandle() {
            Box(
                Modifier.fillMaxSize()
                    .systemGestureExclusion()
                    .semantics { contentDescription = "展开中转站，${entries.size} 项；可拖动调整位置" }
                    .clickable(onClick = ::expand)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragLeft = overlay.window.attributes.x.toFloat() },
                            onDragEnd = ::finishMove,
                            onDragCancel = ::finishMove,
                        ) { change, delta ->
                            change.consume()
                            val height = context.getSystemService(WindowManager::class.java)
                                .currentWindowMetrics.bounds.height()
                            dragLeft = (dragLeft ?: overlay.window.attributes.x.toFloat()) + delta.x
                            centerY = (centerY + delta.y / height).coerceIn(0f, 1f)
                            resize()
                        }
                    },
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
            if (loading) return
            scope.launch {
                loading = true
                message = null
                try {
                    val updated = withContext(Dispatchers.IO) { StashStore(app).use { it.listEntries() } }
                    val newestChanged = updated.firstOrNull()?.id != entries.firstOrNull()?.id
                    entries = updated
                    if (newestChanged) {
                        searching = false
                        query = ""
                        preview = null
                        confirmDelete = false
                        cancelSelection()
                        // Apply with the new item provider, rather than keeping the old first key.
                        listState.requestScrollToItem(0)
                    }
                    selectedIds = selectedIds.intersect(entries.map { it.id }.toSet())
                } catch (error: IOException) {
                    report("读取中转站失败", error)
                } catch (error: SecurityException) {
                    report("系统拒绝读取中转站", error)
                } finally { loading = false }
            }
        }

        private fun copy(entry: StashStore.Entry) {
            try {
                val clip = if (entry.kind == StashStore.KIND_TEXT) {
                    ClipData.newPlainText("中转站文字", entry.text.orEmpty())
                } else ClipData.newUri(app.contentResolver, "中转站图片", StashImageProvider.uriForEntry(entry.id))
                app.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                Toast.makeText(app, "已复制", Toast.LENGTH_SHORT).show()
                collapse()
            } catch (error: SecurityException) {
                report("复制失败", error)
            }
        }

        private fun deleteSelected() {
            val targets = entries.filter { it.id in selectedIds }
            confirmDelete = false
            deleting = true
            scope.launch {
                try {
                    for (entry in targets) {
                        withContext(Dispatchers.IO) { StashStore(app).use { it.delete(entry.id) } }
                        entries = entries.filterNot { it.id == entry.id }
                        selectedIds = selectedIds - entry.id
                    }
                    cancelSelection()
                } catch (error: IOException) {
                    report("删除未完成，剩余内容已保留", error)
                } catch (error: SecurityException) {
                    report("系统拒绝删除，剩余内容已保留", error)
                } finally {
                    deleting = false
                }
            }
        }

        private fun cancelSelection() {
            selectedIds = emptySet()
            selectionMode = false
        }

        private fun openText(entry: StashStore.Entry) {
            if (BigBangOverlayLauncher.showText(app, entry.text.orEmpty(), -1, -1, null)) collapse()
        }

        private fun report(label: String, error: Exception) {
            DragShareLog.w("DragShare/StashWindow", label, error)
            message = "$label：${error.message.orEmpty()}"
            Toast.makeText(app, message, Toast.LENGTH_LONG).show()
        }

        @Composable private fun ShelfItem(entry: StashStore.Entry) {
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
                        StashDraggableContent(listOf(entry), Modifier.weight(1f),
                            enabled = !deleting, draggable = !selectionMode,
                            onStarted = ::collapse, onClick = open) {
                            if (entry.kind == StashStore.KIND_IMAGE) {
                                StashImage(entry, 480, Modifier.fillMaxWidth().height(64.dp))
                            } else {
                                Text(entry.text.orEmpty(), fontSize = 13.sp, maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MiuixTheme.colorScheme.onSurface)
                            }
                        }
                        OverlayIconAction(
                            imageVector = if (selectionMode) {
                                if (selected) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank
                            } else Icons.Outlined.ContentCopy,
                            tint = MiuixTheme.colorScheme.primary,
                            enabled = !deleting,
                            onClick = { if (selectionMode) open() else copy(entry) },
                            contentDescription = if (selectionMode) {
                                if (selected) "取消选择这条内容" else "选择这条内容"
                            } else "复制这条内容",
                        )
                    }
                }
            }
        }

        @Composable private fun Content() {
            val dark = DragShareSettings.readLocal(app).isDarkThemeInComposition()
            val focusManager = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            val closeSearch = {
                focusManager.clearFocus()
                keyboard?.hide()
                searching = false
                query = ""
                confirmDelete = false
                cancelSelection()
            }
            val visibleEntries = entries.filter {
                query.isBlank() || (it.kind == StashStore.KIND_TEXT &&
                    it.text.orEmpty().contains(query, ignoreCase = true))
            }
            LaunchedEffect(expanded) {
                if (!expanded) {
                    focusManager.clearFocus()
                    keyboard?.hide()
                }
            }
            MiuixTheme(controller = remember(dark) {
                ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
            }) {
                BackHandler(enabled = expanded) {
                    when {
                        confirmDelete -> confirmDelete = false
                        preview != null -> preview = null
                        selectionMode -> cancelSelection()
                        searching -> closeSearch()
                        else -> collapse()
                    }
                }
                if (!expanded) {
                    CollapsedHandle()
                } else {
                    Scaffold {
                        Column(Modifier.fillMaxSize()) {
                            if (searching) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    OverlayIconAction(Icons.Outlined.ArrowBack,
                                        MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        onClick = closeSearch, contentDescription = "退出搜索")
                                    BasicTextField(
                                        value = query,
                                        onValueChange = {
                                            query = it
                                            cancelSelection()
                                            preview = null
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = !deleting && !confirmDelete,
                                        singleLine = true,
                                        textStyle = TextStyle(fontSize = 13.sp,
                                            color = MiuixTheme.colorScheme.onSurface),
                                        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = {
                                            focusManager.clearFocus()
                                            keyboard?.hide()
                                        }),
                                        decorationBox = { field ->
                                            Box {
                                                if (query.isEmpty()) Text("搜索文字", fontSize = 13.sp,
                                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                                field()
                                            }
                                        },
                                    )
                                    OverlayIconAction(Icons.Outlined.Close,
                                        MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        enabled = query.isNotEmpty() && !deleting && !confirmDelete,
                                        onClick = { query = ""; cancelSelection() },
                                        contentDescription = "清空搜索文字")
                                }
                            } else {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) { MoveHandle() }
                                    OverlayIconAction(Icons.Outlined.OpenInFull,
                                        MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        enabled = !openingSystemWindow,
                                        onClick = ::openSystemWindow, contentDescription = "放大为系统小窗")
                                    OverlayIconAction(Icons.Outlined.ExpandMore,
                                        MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        onClick = ::collapse, contentDescription = "收起中转站")
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
                                    StashImage(image, 1200, Modifier.fillMaxWidth().weight(1f), ContentScale.Fit)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        OverlayIconAction(Icons.Outlined.ArrowBack,
                                            MiuixTheme.colorScheme.onSurfaceVariantActions,
                                            onClick = { preview = null }, contentDescription = "返回内容列表")
                                        OverlayIconAction(Icons.Outlined.DocumentScanner,
                                            MiuixTheme.colorScheme.onSurfaceVariantActions,
                                            onClick = {
                                                if (ImageOcrOverlay.start(app, StashImageProvider.uriForEntry(image.id))) {
                                                    collapse()
                                                }
                                            }, contentDescription = "识别图片文字")
                                    }
                                }
                                visibleEntries.isEmpty() -> Box(Modifier.weight(1f).padding(8.dp),
                                    contentAlignment = Alignment.Center) {
                                    Text(if (loading) "正在读取…" else if (query.isNotBlank()) "没有匹配文字"
                                        else "拖拽或分享内容存入这里",
                                        fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                }
                                else -> LazyColumn(Modifier.weight(1f), state = listState,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(visibleEntries, key = { it.id }) { ShelfItem(it) }
                                }
                            }
                            if (deleting) {
                                Text("正在删除…", Modifier.padding(6.dp), fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            } else if (selectionMode && !confirmDelete) {
                                StashDragHandle(entries.filter { it.id in selectedIds }, ::collapse)
                            }
                            if (!confirmDelete) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                OverlayIconAction(Icons.Outlined.SelectAll,
                                    if (selectionMode) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    enabled = !deleting && (selectionMode || visibleEntries.isNotEmpty()),
                                    onClick = {
                                        preview = null
                                        if (selectionMode) cancelSelection() else selectionMode = true
                                    }, contentDescription = if (selectionMode) "取消多选" else "多选拖出")
                                OverlayIconAction(if (selectionMode) Icons.Outlined.Delete else Icons.Outlined.SwapHoriz,
                                    MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    enabled = !deleting && (!selectionMode || selectedIds.isNotEmpty()),
                                    onClick = {
                                        if (selectionMode) {
                                            focusManager.clearFocus()
                                            keyboard?.hide()
                                            confirmDelete = true
                                        } else switchSide()
                                    },
                                    contentDescription = if (selectionMode) "删除所选内容"
                                        else if (onLeft) "移到右侧" else "移到左侧")
                                OverlayIconAction(Icons.Outlined.Search,
                                    MiuixTheme.colorScheme.onSurfaceVariantActions,
                                    enabled = !deleting,
                                    onClick = {
                                        preview = null
                                        if (searching) closeSearch() else searching = true
                                    },
                                    contentDescription = if (searching) "退出搜索" else "搜索已存文字")
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

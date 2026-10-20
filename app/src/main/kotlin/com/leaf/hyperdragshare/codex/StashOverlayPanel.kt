package com.leaf.hyperdragshare.codex

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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

    @JvmStatic fun dismiss() {
        main.post { active?.overlay?.dismiss() }
    }

    private class Shelf(private val app: Context) {
        private val position = app.getSharedPreferences("stash_window_position", Context.MODE_PRIVATE)
        private var onLeft = position.getBoolean("left", false)
        private var centerY = position.getFloat("center_y", .5f)
        private var dragLeft: Float? = null
        private val display = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        private val context = app.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var entries by mutableStateOf<List<StashStore.Entry>>(emptyList())
        private var loading by mutableStateOf(false)
        private var message by mutableStateOf<String?>(null)
        private var expanded by mutableStateOf(true)
        private var selectionMode by mutableStateOf(false)
        private var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private var pendingDelete by mutableStateOf<StashStore.Entry?>(null)
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

        private fun collapse() {
            preview = null
            pendingDelete = null
            expanded = false
            resize()
        }

        private fun resize() {
            val metrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            val density = context.resources.displayMetrics.density
            overlay.window.attributes = overlay.window.attributes.apply {
                width = if (expanded) minOf((340 * density).toInt(), metrics.width() - (16 * density).toInt())
                    else (60 * density).toInt()
                height = if (expanded) minOf((640 * density).toInt(), (metrics.height() * .78f).toInt())
                    else (88 * density).toInt()
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

        @Composable private fun MoveHandle() {
            Text(
                "— 拖动 —",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().height(28.dp).pointerInput(Unit) {
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

        private fun reload() {
            if (loading) return
            scope.launch {
                loading = true
                message = null
                try {
                    entries = withContext(Dispatchers.IO) { StashStore(app).use { it.listEntries() } }
                    selectedIds = selectedIds.intersect(entries.map { it.id }.toSet())
                } catch (error: IOException) {
                    report("读取中转站失败", error)
                } catch (error: SecurityException) {
                    report("系统拒绝读取中转站", error)
                } finally { loading = false }
            }
        }

        private fun delete(entry: StashStore.Entry) {
            pendingDelete = null
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { StashStore(app).use { it.delete(entry.id) } }
                    entries = entries.filterNot { it.id == entry.id }
                } catch (error: IOException) {
                    report("删除失败", error)
                } catch (error: SecurityException) {
                    report("系统拒绝删除内容", error)
                }
            }
        }

        private fun copy(entry: StashStore.Entry) {
            try {
                val clip = if (entry.kind == StashStore.KIND_TEXT) {
                    ClipData.newPlainText("中转站文字", entry.text.orEmpty())
                } else ClipData.newUri(app.contentResolver, "中转站图片", StashImageProvider.uriForEntry(entry.id))
                app.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
                Toast.makeText(app, "已复制", Toast.LENGTH_SHORT).show()
            } catch (error: SecurityException) {
                report("复制失败", error)
            }
        }

        private fun share(entry: StashStore.Entry) {
            val send = Intent(Intent.ACTION_SEND)
            if (entry.kind == StashStore.KIND_TEXT) {
                send.type = "text/plain"
                send.putExtra(Intent.EXTRA_TEXT, entry.text.orEmpty())
            } else {
                val uri = StashImageProvider.uriForEntry(entry.id)
                send.setDataAndType(uri, entry.mime ?: "image/*")
                send.putExtra(Intent.EXTRA_STREAM, uri)
                send.clipData = ClipData.newRawUri("中转站图片", uri)
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            launch(Intent.createChooser(send, "分享中转站内容").apply {
                if (entry.kind == StashStore.KIND_IMAGE) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }

        private fun launch(intent: Intent) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                collapse()
            } catch (error: ActivityNotFoundException) {
                report("没有可打开此内容的应用", error)
            } catch (error: SecurityException) {
                report("系统拒绝打开内容", error)
            }
        }

        private fun shareSelected() {
            val selected = entries.filter { it.id in selectedIds }
            if (selected.isEmpty()) return
            val text = selected.filter { it.kind == StashStore.KIND_TEXT }
                .mapNotNull { it.text }.joinToString("\n")
            val images = selected.filter { it.kind == StashStore.KIND_IMAGE }
            val send = if (images.isEmpty()) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
            } else {
                val uris = ArrayList(images.map { StashImageProvider.uriForEntry(it.id) })
                val description = ClipDescription("中转站内容",
                    if (text.isEmpty()) arrayOf("image/*", "text/uri-list")
                    else arrayOf("text/plain", "image/*", "text/uri-list"))
                val clip = ClipData(description, ClipData.Item(uris.first()))
                uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                if (text.isNotEmpty()) clip.addItem(ClipData.Item(text))
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "image/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    if (text.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, text)
                    clipData = clip
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            launch(Intent.createChooser(send, "分享已选内容").apply {
                if (images.isNotEmpty()) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
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

        @Composable private fun Content() {
            val dark = remember { DragShareSettings.readLocal(app).colorMode == DragShareSettings.COLOR_DARK }
            MiuixTheme(controller = remember {
                ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
            }) {
                BackHandler(enabled = expanded) {
                    when {
                        preview != null -> preview = null
                        pendingDelete != null -> pendingDelete = null
                        selectionMode -> cancelSelection()
                        else -> collapse()
                    }
                }
                Scaffold {
                    if (!expanded) {
                        Card(modifier = Modifier.fillMaxSize()) {
                            Column {
                                MoveHandle()
                                TextButton(text = "中转 ${entries.size}", onClick = ::expand, modifier = Modifier.fillMaxSize())
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            MoveHandle()
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("中转站 · ${entries.size}", modifier = Modifier.weight(1f),
                                    fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurface)
                                TextButton("收起", ::collapse)
                                TextButton("关闭", overlay::dismiss)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                TextButton("刷新", ::reload, enabled = !loading)
                                TextButton(if (selectionMode) "取消多选" else "多选", {
                                    if (selectionMode) cancelSelection() else selectionMode = true
                                }, enabled = selectionMode || entries.isNotEmpty())
                                TextButton("管理", { launch(Intent(context, StashActivity::class.java)) })
                            }
                            if (selectionMode) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text("已选 ${selectedIds.size}", modifier = Modifier.weight(1f),
                                        fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    TextButton(if (selectedIds.size == entries.size) "全不选" else "全选", {
                                        selectedIds = if (selectedIds.size == entries.size) emptySet()
                                            else entries.map { it.id }.toSet()
                                    })
                                    TextButton("分享", ::shareSelected, enabled = selectedIds.isNotEmpty())
                                }
                                StashDragHandle(entries.filter { it.id in selectedIds }, ::collapse)
                            }
                            message?.let {
                                Text(it, Modifier.padding(12.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                            val toDelete = pendingDelete
                            val image = preview
                            when {
                                toDelete != null -> Card(Modifier.padding(12.dp)) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text("删除这条内容？", color = MiuixTheme.colorScheme.onSurface)
                                        Row {
                                            TextButton("取消", { pendingDelete = null })
                                            TextButton("删除", { delete(toDelete) })
                                        }
                                    }
                                }
                                image != null -> Column(Modifier.padding(12.dp)) {
                                    StashImage(image, 1200, Modifier.fillMaxWidth().height(320.dp), ContentScale.Fit)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        TextButton("返回列表", { preview = null })
                                        TextButton("识别文字", {
                                            if (ImageOcrOverlay.start(app, StashImageProvider.uriForEntry(image.id))) {
                                                collapse()
                                            }
                                        })
                                    }
                                }
                                entries.isEmpty() -> Text(
                                    if (loading) "正在读取…" else "还没有内容。拖拽或分享文字、图片存入这里。",
                                    Modifier.padding(20.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(entries, key = { it.id }) { entry ->
                                        StashEntryCard(entry, selectionMode, entry.id in selectedIds, {
                                            selectedIds = if (entry.id in selectedIds) selectedIds - entry.id
                                                else selectedIds + entry.id
                                        },
                                            ::openText, { preview = it }, ::copy, ::share,
                                            { pendingDelete = it }, ::collapse)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

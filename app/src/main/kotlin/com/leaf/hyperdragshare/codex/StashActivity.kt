package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.compose.ui.state.ToggleableState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.io.IOException
import java.text.DateFormat
import java.util.Date

/** Persistent in-app collection for text and images received from the share or drag flow. */
class StashActivity : ComponentActivity() {
    private var entries by mutableStateOf<List<StashStore.Entry>>(emptyList())
    private var loading by mutableStateOf(true)
    private var message by mutableStateOf<String?>(null)
    private var pendingDelete by mutableStateOf<StashStore.Entry?>(null)
    private var confirmBatchDelete by mutableStateOf(false)
    private var previewEntry by mutableStateOf<StashStore.Entry?>(null)
    private var selectionMode by mutableStateOf(false)
    private var selectedIds by mutableStateOf<Set<String>>(emptySet())
    private var searchQuery by mutableStateOf("")
    private var kindFilter by mutableStateOf("all")
    private var pendingImport: Intent? = null
    private var shareImported = false
    private val importLock = Mutex()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        shareImported = savedInstanceState?.getBoolean("share_imported") == true
        searchQuery = savedInstanceState?.getString("search_query").orEmpty()
        kindFilter = savedInstanceState?.getString("kind_filter") ?: "all"
        pendingImport = intent.takeIf { !shareImported && isSupportedShare(it) }
        val dark = DragShareSettings.readLocal(this).colorMode == DragShareSettings.COLOR_DARK

        setContent {
            MiuixTheme(
                controller = remember { ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light) },
            ) {
                val scope = rememberCoroutineScope()
                BackHandler(enabled = selectionMode) {
                    selectionMode = false
                    selectedIds = emptySet()
                }
                val visibleEntries = entries.filter {
                    (kindFilter == "all" || it.kind == kindFilter) &&
                        (searchQuery.isBlank() || it.text.orEmpty().contains(searchQuery.trim(), ignoreCase = true))
                }
                StashScreen(
                    entries = visibleEntries,
                    searchQuery = searchQuery,
                    kindFilter = kindFilter,
                    onQueryChange = { searchQuery = it },
                    onKindChange = { kindFilter = it },
                    loading = loading,
                    message = message,
                    pendingDelete = pendingDelete,
                    previewEntry = previewEntry,
                    selectionMode = selectionMode,
                    selectedIds = selectedIds,
                    confirmBatchDelete = confirmBatchDelete,
                    onBack = ::finish,
                    onToggleSelectionMode = {
                        selectionMode = !selectionMode
                        selectedIds = emptySet()
                    },
                    onToggleEntrySelection = { id ->
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                    },
                    onToggleSelectAll = {
                        selectedIds = if (selectedIds.size == visibleEntries.size) emptySet()
                            else visibleEntries.map { it.id }.toSet()
                    },
                    onBatchShare = ::shareEntries,
                    onBatchDeleteRequest = { confirmBatchDelete = true },
                    onBatchDeleteDismiss = { confirmBatchDelete = false },
                    onBatchDeleteConfirm = { scope.launch { deleteSelectedEntries() } },
                    onRetry = {
                        scope.launch {
                            importPendingShare()
                            reloadEntries()
                        }
                    },
                    onOpenText = { entry ->
                        BigBangOverlayLauncher.showText(this, entry.text.orEmpty(), -1, -1, null)
                    },
                    onPreviewImage = { previewEntry = it },
                    onRecognizeImage = { entry ->
                        if (ImageOcrOverlay.start(this, StashImageProvider.uriForEntry(entry.id))) {
                            previewEntry = null
                        }
                    },
                    onCopy = ::copyEntry,
                    onShare = ::shareEntry,
                    onDeleteRequest = { pendingDelete = it },
                    onDeleteDismiss = { pendingDelete = null },
                    onDeleteConfirm = { entry ->
                        scope.launch { deleteEntry(entry) }
                    },
                    onPreviewDismiss = { previewEntry = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shareImported = false
        pendingImport = intent.takeIf(::isSupportedShare)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            importPendingShare()
            reloadEntries()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("share_imported", shareImported)
        outState.putString("search_query", searchQuery)
        outState.putString("kind_filter", kindFilter)
        super.onSaveInstanceState(outState)
    }

    private suspend fun importPendingShare() = importLock.withLock {
        val incoming = pendingImport ?: return@withLock
        message = null
        try {
            val savedCount = withContext(Dispatchers.IO) {
                StashStore(this@StashActivity).use { store ->
                    when {
                        incoming.type == "text/plain" -> {
                            val text = incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                                ?: throw IOException("分享内容没有文字")
                            if (text.isBlank()) throw IOException("分享的文字为空")
                            store.addText(text)
                            1
                        }
                        incoming.type?.startsWith("image/") == true -> {
                            store.addImages(contentResolver, incoming.imageUris())
                        }
                        else -> throw IOException("不支持这种分享内容")
                    }
                }
            }
            if (pendingImport === incoming) {
                pendingImport = null
                shareImported = true
            }
            Toast.makeText(this, "已保存 $savedCount 项到中转站", Toast.LENGTH_SHORT).show()
        } catch (error: IOException) {
            DragShareLog.w("DragShare/Stash", "unable to import shared content", error)
            message = "保存失败：${error.message ?: "无法读取分享内容"}"
        } catch (error: SecurityException) {
            DragShareLog.w("DragShare/Stash", "permission denied while importing shared content", error)
            message = "没有读取分享内容的权限，请重新分享后重试。"
        }
    }

    private suspend fun reloadEntries() {
        loading = true
        try {
            entries = withContext(Dispatchers.IO) {
                StashStore(this@StashActivity).use { it.listEntries() }
            }
            if (pendingImport == null) message = null
        } catch (error: IOException) {
            DragShareLog.w("DragShare/Stash", "unable to load stash entries", error)
            message = "无法读取中转站：${error.message ?: "存储读取失败"}"
        } catch (error: SecurityException) {
            DragShareLog.w("DragShare/Stash", "permission denied while reading stash entries", error)
            message = "没有读取中转站的权限。"
        } finally {
            loading = false
        }
    }

    private suspend fun deleteEntry(entry: StashStore.Entry) {
        pendingDelete = null
        try {
            withContext(Dispatchers.IO) {
                StashStore(this@StashActivity).use { it.delete(entry.id) }
            }
            entries = entries.filterNot { it.id == entry.id }
            if (previewEntry?.id == entry.id) previewEntry = null
        } catch (error: IOException) {
            DragShareLog.w("DragShare/Stash", "unable to delete stash entry", error)
            message = "删除失败：${error.message ?: "中转站条目未能删除"}"
        } catch (error: SecurityException) {
            DragShareLog.w("DragShare/Stash", "permission denied while deleting stash entry", error)
            message = "没有删除中转站条目的权限。"
        }
    }

    private fun copyEntry(entry: StashStore.Entry) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = if (entry.kind == StashStore.KIND_TEXT) {
                ClipData.newPlainText("中转站文字", entry.text.orEmpty())
            } else {
                ClipData.newUri(contentResolver, "中转站图片", StashImageProvider.uriForEntry(entry.id))
            }
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, if (entry.kind == StashStore.KIND_TEXT) "已复制文字" else "已复制图片", Toast.LENGTH_SHORT).show()
        } catch (error: SecurityException) {
            DragShareLog.w("DragShare/Stash", "permission denied while copying stash content", error)
            Toast.makeText(this, "复制失败：系统拒绝访问剪贴板或图片。", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareEntry(entry: StashStore.Entry) {
        val send = if (entry.kind == StashStore.KIND_TEXT) {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, entry.text.orEmpty())
            }
        } else {
            val uri = StashImageProvider.uriForEntry(entry.id)
            Intent(Intent.ACTION_SEND).apply {
                setDataAndType(uri, entry.mime ?: "image/*")
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("中转站图片", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        try {
            startActivity(Intent.createChooser(send, "分享中转站内容").apply {
                if (entry.kind == StashStore.KIND_IMAGE) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (error: ActivityNotFoundException) {
            DragShareLog.w("DragShare/Stash", "no activity can open the share chooser", error)
            Toast.makeText(this, "系统没有可用的分享界面。", Toast.LENGTH_LONG).show()
        } catch (error: SecurityException) {
            DragShareLog.w("DragShare/Stash", "permission denied while starting the share chooser", error)
            Toast.makeText(this, "分享失败：系统拒绝访问这条内容。", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareEntries(entriesToShare: List<StashStore.Entry>) {
        if (entriesToShare.isEmpty()) return
        val text = entriesToShare
            .filter { it.kind == StashStore.KIND_TEXT }
            .mapNotNull { it.text }
            .joinToString("\n")
        val images = entriesToShare.filter { it.kind == StashStore.KIND_IMAGE }
        val send = if (images.isEmpty()) {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        } else {
            val imageUris = ArrayList(images.map { StashImageProvider.uriForEntry(it.id) })
            val description = ClipDescription(
                "中转站内容",
                if (text.isEmpty()) {
                    arrayOf("image/*", "text/uri-list")
                } else {
                    arrayOf("text/plain", "image/*", "text/uri-list")
                },
            )
            val clip = ClipData(description, ClipData.Item(imageUris.first()))
            imageUris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            if (text.isNotEmpty()) clip.addItem(ClipData.Item(text))
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris)
                if (text.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, text)
                clipData = clip
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        try {
            startActivity(Intent.createChooser(send, "分享已选内容").apply {
                if (images.isNotEmpty()) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (error: ActivityNotFoundException) {
            DragShareLog.w("DragShare/Stash", "no activity can open the batch share chooser", error)
            Toast.makeText(this, "系统没有可用的分享界面。", Toast.LENGTH_LONG).show()
        } catch (error: SecurityException) {
            DragShareLog.w("DragShare/Stash", "permission denied while starting the batch share chooser", error)
            Toast.makeText(this, "分享失败：系统拒绝访问已选内容。", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun deleteSelectedEntries() {
        confirmBatchDelete = false
        val ids = selectedIds.toList()
        if (ids.isEmpty()) return
        try {
            val deletedIds = withContext(Dispatchers.IO) {
                StashStore(this@StashActivity).use { store -> ids.filter { store.delete(it) } }
            }
            entries = entries.filterNot { it.id in deletedIds }
            if (previewEntry?.id?.let(deletedIds::contains) == true) previewEntry = null
            selectedIds = emptySet()
            selectionMode = false
            Toast.makeText(this, "已删除 ${deletedIds.size} 项", Toast.LENGTH_SHORT).show()
        } catch (error: IOException) {
            DragShareLog.w("DragShare/Stash", "batch delete stopped before completion", error)
            reloadEntries()
            selectedIds = selectedIds.intersect(entries.map { it.id }.toSet())
            message = "批量删除未能全部完成：${error.message ?: "请检查仍保留的条目"}"
        } catch (error: SecurityException) {
            DragShareLog.w("DragShare/Stash", "permission denied during batch delete", error)
            reloadEntries()
            selectedIds = selectedIds.intersect(entries.map { it.id }.toSet())
            message = "没有删除已选条目的权限。"
        }
    }

    private fun isSupportedShare(intent: Intent): Boolean =
        (intent.action == Intent.ACTION_SEND &&
            (intent.type == "text/plain" || intent.type?.startsWith("image/") == true)) ||
            (intent.action == Intent.ACTION_SEND_MULTIPLE && intent.type?.startsWith("image/") == true)

    private fun Intent.imageUris(): List<Uri> {
        val streams = if (action == Intent.ACTION_SEND_MULTIPLE) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            listOfNotNull(getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        }
        if (streams.isNotEmpty()) return streams.distinct()
        val clip = clipData ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }.distinct()
    }
}

@Composable
private fun StashScreen(
    entries: List<StashStore.Entry>,
    searchQuery: String,
    kindFilter: String,
    onQueryChange: (String) -> Unit,
    onKindChange: (String) -> Unit,
    loading: Boolean,
    message: String?,
    pendingDelete: StashStore.Entry?,
    previewEntry: StashStore.Entry?,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    confirmBatchDelete: Boolean,
    onBack: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleEntrySelection: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onBatchShare: (List<StashStore.Entry>) -> Unit,
    onBatchDeleteRequest: () -> Unit,
    onBatchDeleteDismiss: () -> Unit,
    onBatchDeleteConfirm: () -> Unit,
    onRetry: () -> Unit,
    onOpenText: (StashStore.Entry) -> Unit,
    onPreviewImage: (StashStore.Entry) -> Unit,
    onRecognizeImage: (StashStore.Entry) -> Unit,
    onCopy: (StashStore.Entry) -> Unit,
    onShare: (StashStore.Entry) -> Unit,
    onDeleteRequest: (StashStore.Entry) -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: (StashStore.Entry) -> Unit,
    onPreviewDismiss: () -> Unit,
) {
    var searchExpanded by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(
            title = if (selectionMode) "已选 ${selectedIds.size} 项" else "中转站",
            largeTitle = if (selectionMode) "已选 ${selectedIds.size} 项" else "中转站",
            navigationIcon = {
                BackNavigationIcon(onClick = if (selectionMode) onToggleSelectionMode else onBack)
            },
            actions = {
                TextButton(
                    text = if (selectionMode) "完成" else "多选",
                    onClick = onToggleSelectionMode,
                    enabled = selectionMode || entries.isNotEmpty(),
                )
            },
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!selectionMode) {
                item(key = "filters") {
                    Column {
                        SearchBar(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            inputField = {
                                InputField(
                                    query = searchQuery,
                                    onQueryChange = onQueryChange,
                                    onSearch = { searchExpanded = false },
                                    expanded = searchExpanded,
                                    onExpandedChange = { searchExpanded = it },
                                    label = "搜索已存文字",
                                )
                            },
                            expanded = searchExpanded,
                            onExpandedChange = { searchExpanded = it },
                        ) {}
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("all" to "全部", StashStore.KIND_TEXT to "文字",
                                StashStore.KIND_IMAGE to "图片").forEach { (kind, label) ->
                                TextButton(
                                    text = if (kind == kindFilter) "$label · ${entries.size}" else label,
                                    onClick = { onKindChange(kind) },
                                )
                            }
                        }
                    }
                }
            }
            if (message != null) {
                item(key = "error") {
                    StashMessageCard(message = message, loading = loading, onRetry = onRetry)
                }
            }
            if (loading && entries.isEmpty()) {
                item(key = "loading") { StashMessageCard(message = "正在读取中转站…", loading = true) }
            } else if (entries.isEmpty() && message == null) {
                item(key = "empty") {
                    StashMessageCard(message = if (searchQuery.isNotBlank() || kindFilter != "all")
                        "没有匹配的内容。可清空搜索或切换到全部。"
                        else "中转站还是空的。可从分享菜单发送文字或图片到这里。")
                }
            } else {
                if (selectionMode) {
                    item(key = "selection-actions") {
                        StashSelectionActions(
                            selectedCount = selectedIds.size,
                            allSelected = selectedIds.size == entries.size,
                            onToggleSelectAll = onToggleSelectAll,
                            onShare = { onBatchShare(entries.filter { it.id in selectedIds }) },
                            onDelete = onBatchDeleteRequest,
                        )
                    }
                }
                items(entries, key = { it.id }) { entry ->
                    StashEntryCard(
                        entry = entry,
                        selectionMode = selectionMode,
                        selected = entry.id in selectedIds,
                        onToggleSelection = { onToggleEntrySelection(entry.id) },
                        onOpenText = onOpenText,
                        onPreviewImage = onPreviewImage,
                        onCopy = onCopy,
                        onShare = onShare,
                        onDelete = onDeleteRequest,
                    )
                }
            }
        }

    OverlayDialog(
        show = confirmBatchDelete,
        title = "删除已选的 ${selectedIds.size} 项？",
        summary = "删除后无法恢复。",
        onDismissRequest = onBatchDeleteDismiss,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(text = "取消", onClick = onBatchDeleteDismiss, modifier = Modifier.weight(1f))
            TextButton(
                text = "删除 ${selectedIds.size} 项",
                onClick = onBatchDeleteConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }

    OverlayDialog(
        show = pendingDelete != null,
        title = "删除这条内容？",
        summary = "删除后无法恢复。",
        onDismissRequest = onDeleteDismiss,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(text = "取消", onClick = onDeleteDismiss, modifier = Modifier.weight(1f))
            TextButton(
                text = "删除",
                onClick = { pendingDelete?.let(onDeleteConfirm) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
            )
        }
    }

    OverlayDialog(
        show = previewEntry != null,
        title = "图片预览",
        summary = previewEntry?.mime.orEmpty(),
        onDismissRequest = onPreviewDismiss,
    ) {
        previewEntry?.let { entry ->
            StashImage(
                entry = entry,
                edgeLimit = 1200,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(360.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(text = "关闭", onClick = onPreviewDismiss, modifier = Modifier.weight(1f))
                TextButton(text = "识别文字", onClick = { onRecognizeImage(entry) },
                    modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
            }
        }
    }
    }
}

@Composable
private fun StashSelectionActions(
    selectedCount: Int,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "已选择 $selectedCount 项",
                    modifier = Modifier.weight(1f),
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                )
                TextButton(
                    text = if (allSelected) "取消全选" else "全选",
                    onClick = onToggleSelectAll,
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "分享",
                    onClick = onShare,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
                TextButton(
                    text = "删除",
                    onClick = onDelete,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StashMessageCard(message: String, loading: Boolean = false, onRetry: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, color = MiuixTheme.colorScheme.onSurface, fontSize = 15.sp)
            if (onRetry != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    text = "重试",
                    onClick = onRetry,
                    enabled = !loading,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
internal fun StashEntryCard(
    entry: StashStore.Entry,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelection: () -> Unit,
    onOpenText: (StashStore.Entry) -> Unit,
    onPreviewImage: (StashStore.Entry) -> Unit,
    onCopy: (StashStore.Entry) -> Unit,
    onShare: (StashStore.Entry) -> Unit,
    onDelete: (StashStore.Entry) -> Unit,
    onDragStarted: () -> Unit = {},
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (entry.kind == StashStore.KIND_TEXT) "文字" else "图片",
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(Date(entry.createdAt)),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 11.sp,
                    )
                }
                if (selectionMode) {
                    Checkbox(
                        state = if (selected) ToggleableState.On else ToggleableState.Off,
                        onClick = onToggleSelection,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (entry.kind == StashStore.KIND_TEXT) {
                Text(
                    text = entry.text.orEmpty(),
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                StashImage(
                    entry = entry,
                    edgeLimit = 480,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
            }
            if (!selectionMode) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        text = if (entry.kind == StashStore.KIND_TEXT) "分词" else "预览",
                        onClick = { if (entry.kind == StashStore.KIND_TEXT) onOpenText(entry) else onPreviewImage(entry) },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(text = "复制", onClick = { onCopy(entry) }, modifier = Modifier.weight(1f))
                    TextButton(text = "分享", onClick = { onShare(entry) }, modifier = Modifier.weight(1f))
                    TextButton(text = "删除", onClick = { onDelete(entry) }, modifier = Modifier.weight(1f))
                }
                StashDragHandle(listOf(entry), onDragStarted)
            }
        }
    }
}

@Composable
internal fun StashImage(
    entry: StashStore.Entry,
    edgeLimit: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    var failed by remember(entry.id, edgeLimit) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(initialValue = null, entry.id, edgeLimit) {
        failed = false
        try {
            value = withContext(Dispatchers.IO) {
                val uri = StashImageProvider.uriForEntry(entry.id)
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
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
        } catch (error: IOException) {
            DragShareLog.w("DragShare/Stash", "unable to decode stash image", error)
            failed = true
        } catch (error: SecurityException) {
            DragShareLog.w("DragShare/Stash", "permission denied while decoding stash image", error)
            failed = true
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "中转站图片",
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (failed) "图片无法预览" else "正在加载图片…",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

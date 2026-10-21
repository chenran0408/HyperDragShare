package com.leaf.hyperdragshare.codex

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun stashBatches(entries: List<StashStore.Entry>): List<List<StashStore.Entry>> =
    entries.groupBy { it.batchId }.values.map { it.sortedBy { entry -> entry.batchOrder } }

internal fun stashGroupedEntryIds(entries: List<StashStore.Entry>): Set<String> =
    stashBatches(entries).filter { it.size > 1 }.flatten().map { it.id }.toSet()

internal fun stashBatchLabel(entries: List<StashStore.Entry>): String = when {
    entries.all { it.kind == StashStore.KIND_IMAGE } -> "${entries.size} 张图片"
    entries.all { it.kind == StashStore.KIND_TEXT } -> "${entries.size} 段文字"
    else -> "${entries.size} 项内容"
}

/** Secondary management actions appear only for a selection that can use them. */
@Composable
internal fun StashGroupingActions(
    entries: List<StashStore.Entry>,
    enabled: Boolean,
    canUngroup: Boolean,
    onGroup: () -> Unit,
    onUngroup: () -> Unit,
) {
    val canGroup = entries.size > 1 && entries.any { it.batchId != entries.first().batchId }
    if (!canGroup && !canUngroup) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (canGroup) TextButton("分组", onGroup, enabled = enabled, modifier = Modifier.weight(1f))
        if (canUngroup) TextButton("拆开", onUngroup, enabled = enabled, modifier = Modifier.weight(1f))
    }
}

/** Same batch and selection order in the shelf, native small window and management page. */
@Composable
internal fun StashBatchCard(
    batch: List<StashStore.Entry>,
    modifier: Modifier = Modifier,
    imageHeight: Dp,
    selectionMode: Boolean,
    selectedCount: Int,
    enabled: Boolean = true,
    onDragStarted: () -> Unit,
    onClick: () -> Unit,
) {
    Card(modifier.fillMaxWidth()) {
        StashDraggableContent(batch, Modifier.fillMaxWidth().padding(6.dp), enabled = enabled,
            draggable = !selectionMode, onStarted = onDragStarted, onClick = onClick) {
            Column {
                batch.firstOrNull { it.kind == StashStore.KIND_IMAGE }?.let {
                    StashImage(it, 480, Modifier.fillMaxWidth().height(imageHeight), ContentScale.Fit)
                }
                batch.firstOrNull { it.kind == StashStore.KIND_TEXT }?.let {
                    Text(it.text.orEmpty(), fontSize = 13.sp, maxLines = if (batch.any {
                        item -> item.kind == StashStore.KIND_IMAGE }) 1 else 3,
                        overflow = TextOverflow.Ellipsis, color = MiuixTheme.colorScheme.onSurface)
                }
                Text(if (selectionMode) "已选 $selectedCount/${batch.size} 项" else "${stashBatchLabel(batch)} · 点按展开",
                    fontSize = 12.sp, maxLines = 1, color = if (selectedCount > 0)
                        MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}

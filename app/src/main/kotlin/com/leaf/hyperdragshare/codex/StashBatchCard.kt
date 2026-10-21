package com.leaf.hyperdragshare.codex

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun stashBatches(entries: List<StashStore.Entry>): List<List<StashStore.Entry>> =
    entries.groupBy { it.batchId }.values.map { it.sortedBy { entry -> entry.batchOrder } }

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
                StashImage(batch.first(), 480, Modifier.fillMaxWidth().height(imageHeight), ContentScale.Fit)
                Text(if (selectionMode) "已选 $selectedCount/${batch.size} 张" else "${batch.size} 张图片 · 点按展开",
                    fontSize = 12.sp, maxLines = 1, color = if (selectedCount > 0)
                        MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}

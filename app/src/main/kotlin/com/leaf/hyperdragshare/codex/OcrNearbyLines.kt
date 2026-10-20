package com.leaf.hyperdragshare.codex

import android.graphics.Rect

/** Select the nearest OCR line above and below the source in the same column. */
internal fun nearbyOcrLines(bounds: List<Rect?>, source: Rect): Set<Int> {
    val sameColumn = bounds.indices.filter { index ->
        bounds[index]?.let { it.left < source.right && it.right > source.left && !it.isEmpty } == true
    }
    val before = sameColumn.filter { bounds[it]!!.bottom <= source.top }
        .minByOrNull { source.top - bounds[it]!!.bottom }
    val after = sameColumn.filter { bounds[it]!!.top >= source.bottom }
        .minByOrNull { bounds[it]!!.top - source.bottom }
    return listOfNotNull(before, after).toSet()
}

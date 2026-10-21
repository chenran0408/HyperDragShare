package com.leaf.hyperdragshare.codex

import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.floor

/** AICR 4.12.26 returns four image-space (x,y) box vertices, verified with known-position text. */
internal fun aicrLineBounds(line: AicrOcrResult.Line): Rect? {
    val points = line.box ?: return null
    require(points.size == 8 && points.all { it.isFinite() }) { "AICR 行坐标格式不受支持" }
    val xs = points.indices.step(2).map { points[it] }
    val ys = points.indices.step(2).map { points[it + 1] }
    return Rect(floor(xs.min()).toInt(), floor(ys.min()).toInt(),
        ceil(xs.max()).toInt(), ceil(ys.max()).toInt()).takeUnless { it.isEmpty }
}

/** Select nearby rows in the same column; a distant status bar is not a neighboring paragraph. */
internal fun nearbyOcrLines(bounds: List<Rect?>, source: Rect, referenceHeight: Int = source.height()): Set<Int> {
    val sameColumn = bounds.indices.filter { index ->
        bounds[index]?.let { it.left < source.right && it.right > source.left && !it.isEmpty } == true
    }
    fun closeEnough(index: Int, gap: Int) = gap <= 2 * maxOf(referenceHeight, bounds[index]!!.height())
    val before = sameColumn.filter { bounds[it]!!.bottom <= source.top &&
        closeEnough(it, source.top - bounds[it]!!.bottom) }
        .minByOrNull { source.top - bounds[it]!!.bottom }
    val after = sameColumn.filter { bounds[it]!!.top >= source.bottom &&
        closeEnough(it, bounds[it]!!.top - source.bottom) }
        .minByOrNull { bounds[it]!!.top - source.bottom }
    return listOfNotNull(before, after).toSet()
}

/** Advance only through selected, located rows in this screenshot; keep the supplied bounds intact. */
internal fun expandedNearbyOcrBounds(source: Rect, bounds: List<Rect?>,
    selected: Set<Int>, width: Int, height: Int): Rect? {
    var expanded: Rect? = null
    for (index in selected) {
        val row = bounds[index] ?: continue
        if (row.isEmpty) continue
        val clipped = Rect(row)
        if (!clipped.intersect(0, 0, width, height)) continue
        if (expanded == null) expanded = Rect(source)
        expanded.union(clipped)
    }
    return expanded?.apply { intersect(0, 0, width, height) }
}

/** Preserve OCR reading order; only rows clearly above the source belong before the original text. */
internal fun splitNearbyOcrSelection(texts: List<String>, bounds: List<Rect?>,
    selected: Set<Int>, source: Rect): Pair<String, String> {
    val (before, after) = texts.indices.filter { it in selected }.partition { index ->
        bounds[index]?.let { it.bottom <= source.top } == true
    }
    return before.joinToString("\n") { texts[it] } to after.joinToString("\n") { texts[it] }
}

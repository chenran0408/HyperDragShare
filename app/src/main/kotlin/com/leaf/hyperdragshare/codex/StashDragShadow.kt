package com.leaf.hyperdragshare.codex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.view.View
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Small in-memory preview sent over the embedded-host Binder request. */
internal data class StashDragShadowPayload(
    val png: ByteArray,
    val bitmapWidthPx: Int,
    val bitmapHeightPx: Int,
    val shadowWidthPx: Int,
    val shadowHeightPx: Int,
    val scaledHotspotX: Int,
    val scaledHotspotY: Int,
)

internal object StashDragShadowSnapshot {
    const val MAX_EDGE_PX = 256
    const val MAX_PNG_BYTES = 64 * 1024
    private const val MAX_SOURCE_EDGE_PX = 8192

    fun capture(shadow: View.DragShadowBuilder): StashDragShadowPayload? {
        val sourceSize = Point()
        val sourceHotspot = Point()
        shadow.onProvideShadowMetrics(sourceSize, sourceHotspot)
        val sourceWidth = sourceSize.x
        val sourceHeight = sourceSize.y
        if (sourceWidth !in 1..MAX_SOURCE_EDGE_PX || sourceHeight !in 1..MAX_SOURCE_EDGE_PX) {
            return null
        }

        val initialScale = min(1f, MAX_EDGE_PX.toFloat() / max(sourceWidth, sourceHeight))
        var width = (sourceWidth * initialScale).roundToInt().coerceAtLeast(1)
        var height = (sourceHeight * initialScale).roundToInt().coerceAtLeast(1)
        val hotspotX = sourceHotspot.x.coerceIn(0, sourceWidth)
        val hotspotY = sourceHotspot.y.coerceIn(0, sourceHeight)
        var bitmap: Bitmap? = null
        try {
            var working = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap = working
            Canvas(working).apply {
                scale(width.toFloat() / sourceWidth, height.toFloat() / sourceHeight)
                shadow.onDrawShadow(this)
            }
            while (true) {
                val output = ByteArrayOutputStream()
                if (!working.compress(Bitmap.CompressFormat.PNG, 100, output)) return null
                val encoded = output.toByteArray()
                if (encoded.size <= MAX_PNG_BYTES) {
                    return StashDragShadowPayload(
                        encoded,
                        working.width,
                        working.height,
                        sourceWidth,
                        sourceHeight,
                        (hotspotX * working.width.toFloat() / sourceWidth).roundToInt()
                            .coerceIn(0, working.width),
                        (hotspotY * working.height.toFloat() / sourceHeight).roundToInt()
                            .coerceIn(0, working.height),
                    )
                }
                val nextWidth = max(1, working.width * 3 / 4)
                val nextHeight = max(1, working.height * 3 / 4)
                if (nextWidth == working.width && nextHeight == working.height) return null
                val smaller = Bitmap.createScaledBitmap(working, nextWidth, nextHeight, true)
                working.recycle()
                working = smaller
                bitmap = smaller
            }
            @Suppress("UNREACHABLE_CODE")
            return null
        } finally {
            bitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }
}

/** Host-side shadow: keeps source-view size/hotspot while drawing a bounded bitmap. */
internal class StashDragShadowBuilder private constructor(
    private val bitmap: Bitmap,
    private val shadowWidthPx: Int,
    private val shadowHeightPx: Int,
    private val scaledHotspotX: Int,
    private val scaledHotspotY: Int,
) : View.DragShadowBuilder() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun onProvideShadowMetrics(size: Point, touch: Point) {
        size.set(shadowWidthPx, shadowHeightPx)
        touch.set(
            (scaledHotspotX * shadowWidthPx.toFloat() / bitmap.width).roundToInt()
                .coerceIn(0, shadowWidthPx),
            (scaledHotspotY * shadowHeightPx.toFloat() / bitmap.height).roundToInt()
                .coerceIn(0, shadowHeightPx),
        )
    }

    override fun onDrawShadow(canvas: Canvas) {
        canvas.drawBitmap(bitmap, null, Rect(0, 0, shadowWidthPx, shadowHeightPx), paint)
    }

    val isRecycled: Boolean get() = bitmap.isRecycled

    fun recycle() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }

    companion object {
        @JvmStatic
        fun decode(
            png: ByteArray?,
            bitmapWidthPx: Int,
            bitmapHeightPx: Int,
            shadowWidthPx: Int,
            shadowHeightPx: Int,
            scaledHotspotX: Int,
            scaledHotspotY: Int,
        ): StashDragShadowBuilder? {
            if (png == null || png.isEmpty() || png.size > StashDragShadowSnapshot.MAX_PNG_BYTES
                || bitmapWidthPx !in 1..StashDragShadowSnapshot.MAX_EDGE_PX
                || bitmapHeightPx !in 1..StashDragShadowSnapshot.MAX_EDGE_PX
                || shadowWidthPx !in 1..8192 || shadowHeightPx !in 1..8192
                || scaledHotspotX !in 0..bitmapWidthPx || scaledHotspotY !in 0..bitmapHeightPx
            ) return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(png, 0, png.size, bounds)
            if (bounds.outWidth != bitmapWidthPx || bounds.outHeight != bitmapHeightPx) return null
            val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)
                ?: return null
            if (bitmap.width != bitmapWidthPx || bitmap.height != bitmapHeightPx) {
                bitmap.recycle()
                return null
            }
            return StashDragShadowBuilder(
                bitmap,
                shadowWidthPx,
                shadowHeightPx,
                scaledHotspotX,
                scaledHotspotY,
            )
        }
    }
}

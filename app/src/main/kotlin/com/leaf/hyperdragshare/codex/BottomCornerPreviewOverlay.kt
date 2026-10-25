package com.leaf.hyperdragshare.codex

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import com.leaf.hyperdragshare.gestures.config.OverlayTouchRegions
import com.leaf.hyperdragshare.gestures.xposed.GestureGeometry

/** A deliberately small, passive full-screen drawing for the bottom-corner settings preview. */
internal class BottomCornerPreviewOverlay(
    context: Context,
    private val windowOperations: PreviewWindowOperations = PlatformPreviewWindowOperations,
    private val overlayPermission: () -> Boolean = { Settings.canDrawOverlays(context.applicationContext) },
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private var view: PreviewView? = null
    private var touchRegionId: Int? = null

    fun show(data: PreviewData): Boolean {
        if (view != null) {
            update(data)
            return true
        }
        if (!overlayPermission()) return false
        val regionId = OverlayTouchRegions.newWindowId()
        val overlay = PreviewView(appContext, data)
        overlay.setTouchRegionId(regionId)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "MMGG gesture settings preview"
        }
        return try {
            OverlayTouchRegions.update(appContext, regionId,
                android.graphics.Rect(0, 0,
                    windowManager.currentWindowMetrics.bounds.width(),
                    windowManager.currentWindowMetrics.bounds.height()))
            windowOperations.add(windowManager, overlay, params)
            view = overlay
            touchRegionId = regionId
            ViewCompat.requestApplyInsets(overlay)
            true
        } catch (_: RuntimeException) {
            try {
                if (overlay.parent != null) windowOperations.removeImmediately(windowManager, overlay)
            } catch (_: RuntimeException) {
                // The exclusion is cleared below even if the surface was already removed.
            }
            view = null
            touchRegionId = null
            OverlayTouchRegions.update(appContext, regionId, null)
            false
        }
    }

    fun update(data: PreviewData) {
        view?.configure(data)
    }

    internal fun currentPreviewData(): PreviewData? = view?.previewData()

    fun stop() {
        val current = view
        view = null
        try {
            if (current != null) windowOperations.remove(windowManager, current)
        } catch (_: RuntimeException) {
            try {
                if (current != null) windowOperations.removeImmediately(windowManager, current)
            } catch (_: RuntimeException) {
                // The exclusion is still cleared if display teardown raced this call.
            }
        } finally {
            touchRegionId?.let { OverlayTouchRegions.update(appContext, it, null) }
            touchRegionId = null
        }
    }

    internal data class PreviewData(
        val hotWidthPercent: Int,
        val hotHeightPercent: Int,
        val bottomGapDp: Int,
        val fanDistancePercent: Int,
        val sideDistancePercent: Int,
        val sideTopSafeMarginPercent: Int,
        val sideDirectionHorizontal: Boolean,
        val sideDirectionUp: Boolean,
        val sideDirectionDown: Boolean,
        val leftCornerEnabled: Boolean,
        val rightCornerEnabled: Boolean,
        val sideEnabled: Boolean,
        val primaryColor: Int,
        val surfaceColor: Int,
        val textColor: Int,
        val mutedColor: Int,
    )

    internal interface PreviewWindowOperations {
        fun add(manager: WindowManager, view: View, params: WindowManager.LayoutParams)
        fun remove(manager: WindowManager, view: View)
        fun removeImmediately(manager: WindowManager, view: View)
    }

    private object PlatformPreviewWindowOperations : PreviewWindowOperations {
        override fun add(manager: WindowManager, view: View, params: WindowManager.LayoutParams) {
            manager.addView(view, params)
        }

        override fun remove(manager: WindowManager, view: View) {
            manager.removeView(view)
        }

        override fun removeImmediately(manager: WindowManager, view: View) {
            manager.removeViewImmediate(view)
        }
    }

    private class PreviewView(context: Context, private var data: PreviewData) : View(context) {
        private val density = resources.displayMetrics.density
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1)
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dp(13)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        }
        private var safeInsets = WindowInsetsCompat.CONSUMED
        private var touchRegionId = 0

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                safeInsets = insets
                invalidate()
                insets
            }
        }

        fun setTouchRegionId(id: Int) {
            touchRegionId = id
        }

        fun configure(next: PreviewData) {
            data = next
            invalidate()
        }

        fun previewData(): PreviewData = data

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val width = width
            val height = height
            if (width <= 0 || height <= 0) return
            val safe = safeInsets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val reportedSideGestures = safeInsets.getInsets(WindowInsetsCompat.Type.systemGestures())
            val sideLeftWidth = if (reportedSideGestures.left > 0) reportedSideGestures.left else dp(32).toInt()
            val sideRightWidth = if (reportedSideGestures.right > 0) reportedSideGestures.right else dp(32).toInt()
            val gapPx = (data.bottomGapDp * density).toInt()
            val leftZone = GestureGeometry.hotZoneBounds(
                width, height, 0, 0, safe.top,
                data.hotWidthPercent, data.hotHeightPercent, gapPx, false,
            )
            val rightZone = GestureGeometry.hotZoneBounds(
                width, height, 0, 0, safe.top,
                data.hotWidthPercent, data.hotHeightPercent, gapPx, true,
            )

            val captionX = safe.left + dp(8)
            drawCaption(canvas, "手势范围预览 · 不拦截触摸", captionX, safe.top + dp(30), data.textColor)
            val sideTop = maxOf(safe.top, (height * data.sideTopSafeMarginPercent / 100f).toInt())
            val hotTop = minOf(leftZone.top, rightZone.top)
            val sideBottom = maxOf(sideTop,
                minOf(height - safe.bottom, hotTop - dp(24).toInt()))
            drawSideZones(canvas, width, sideTop, sideBottom,
                sideLeftWidth, sideRightWidth)
            drawCornerZone(canvas, leftZone, data.leftCornerEnabled)
            drawCornerZone(canvas, rightZone, data.rightCornerEnabled)

            val menuCenterX = width * .5f
            val fanDistancePx = minOf(width, height) * data.fanDistancePercent / 100f
            val menuTopLimit = safe.top + dp(36)
            val menuBottomLimit = height - safe.bottom - dp(36)
            val menuCenterY = if (menuTopLimit <= menuBottomLimit) {
                (height - gapPx - fanDistancePx).coerceIn(menuTopLimit, menuBottomLimit)
            } else {
                (safe.top + height - safe.bottom) / 2f
            }
            drawArrow(canvas, centerX(leftZone), centerY(leftZone), menuCenterX - dp(20), menuCenterY,
                if (data.leftCornerEnabled) data.primaryColor else data.mutedColor)
            drawArrow(canvas, centerX(rightZone), centerY(rightZone), menuCenterX + dp(20), menuCenterY,
                if (data.rightCornerEnabled) data.primaryColor else data.mutedColor)
            if (data.sideEnabled) {
                val swipeDistance = minOf(width * data.sideDistancePercent / 100f,
                    (width / 2f - safe.left - safe.right).coerceAtLeast(0f))
                val swipeY = (sideTop + sideBottom) / 2f
                if (data.sideDirectionHorizontal) {
                    val leftStart = sideLeftWidth / 2f
                    val rightStart = width - sideRightWidth / 2f
                    drawArrow(canvas, leftStart, swipeY,
                        leftStart + swipeDistance, swipeY, data.primaryColor)
                    drawArrow(canvas, rightStart, swipeY,
                        rightStart - swipeDistance, swipeY, data.primaryColor)
                }
                if (data.sideDirectionUp) {
                    drawArrow(canvas, sideLeftWidth / 2f, swipeY,
                        sideLeftWidth / 2f, sideTop.toFloat(), data.primaryColor)
                    drawArrow(canvas, width - sideRightWidth / 2f, swipeY,
                        width - sideRightWidth / 2f, sideTop.toFloat(), data.primaryColor)
                }
                if (data.sideDirectionDown) {
                    drawArrow(canvas, sideLeftWidth / 2f, swipeY,
                        sideLeftWidth / 2f, sideBottom.toFloat(), data.primaryColor)
                    drawArrow(canvas, width - sideRightWidth / 2f, swipeY,
                        width - sideRightWidth / 2f, sideBottom.toFloat(), data.primaryColor)
                }
            }
            drawGapMarker(canvas, leftZone, height)
            drawGapMarker(canvas, rightZone, height)

            val menu = RectF(menuCenterX - dp(72), menuCenterY - dp(24), menuCenterX + dp(72), menuCenterY + dp(24))
            fillPaint.color = withAlpha(data.primaryColor, .16f)
            canvas.drawRoundRect(menu, dp(14), dp(14), fillPaint)
            strokePaint.color = withAlpha(data.primaryColor, .82f)
            canvas.drawRoundRect(menu, dp(14), dp(14), strokePaint)
            drawCaption(canvas, "菜单大致位置", menu.left + dp(17), menu.centerY() + dp(5), data.textColor)

            drawCaption(canvas, "高 ${data.hotHeightPercent}% · 底距 ${data.bottomGapDp} dp",
                captionX, height - safe.bottom - dp(39), data.textColor)
            drawCaption(canvas, "斜滑 ${data.fanDistancePercent}% · 侧滑 ${data.sideDistancePercent}%",
                captionX, height - safe.bottom - dp(23), data.textColor)
            val sideDirections = buildList {
                if (data.sideDirectionHorizontal) add("横")
                if (data.sideDirectionUp) add("上")
                if (data.sideDirectionDown) add("下")
            }.joinToString("/").ifEmpty { "无" }
            drawCaption(canvas, "侧滑方向 $sideDirections", captionX,
                height - safe.bottom - dp(7), data.mutedColor)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (touchRegionId != 0 && w > 0 && h > 0) {
                OverlayTouchRegions.update(context, touchRegionId, android.graphics.Rect(0, 0, w, h))
            }
        }

        override fun onDetachedFromWindow() {
            if (touchRegionId != 0) OverlayTouchRegions.update(context, touchRegionId, null)
            super.onDetachedFromWindow()
        }

        private fun drawSideZones(canvas: Canvas, width: Int, topInset: Int, bottom: Int,
                                  leftWidth: Int, rightWidth: Int) {
            val top = topInset.toFloat()
            val safeBottom = maxOf(topInset, bottom).toFloat()
            fillPaint.color = if (data.sideEnabled) withAlpha(data.primaryColor, .25f) else withAlpha(data.mutedColor, .12f)
            canvas.drawRect(0f, top, leftWidth.toFloat(), safeBottom, fillPaint)
            canvas.drawRect((width - rightWidth).toFloat(), top, width.toFloat(), safeBottom, fillPaint)
        }

        private fun drawCornerZone(canvas: Canvas, zone: GestureGeometry.HotZoneBounds, enabled: Boolean) {
            fillPaint.color = if (enabled) withAlpha(data.primaryColor, .34f) else withAlpha(data.mutedColor, .12f)
            canvas.drawRect(zone.left.toFloat(), zone.top.toFloat(), zone.right.toFloat(), zone.bottom.toFloat(), fillPaint)
            strokePaint.color = if (enabled) withAlpha(data.primaryColor, .92f) else withAlpha(data.mutedColor, .35f)
            canvas.drawRect(zone.left.toFloat(), zone.top.toFloat(), zone.right.toFloat(), zone.bottom.toFloat(), strokePaint)
        }

        private fun drawGapMarker(canvas: Canvas, zone: GestureGeometry.HotZoneBounds, safeBottom: Int) {
            if (zone.bottom >= safeBottom) return
            val x = ((zone.left + zone.right) / 2f)
            strokePaint.color = withAlpha(data.primaryColor, .72f)
            canvas.drawLine(x, zone.bottom.toFloat(), x, safeBottom.toFloat(), strokePaint)
        }

        private fun drawArrow(canvas: Canvas, fromX: Float, fromY: Float, toX: Float, toY: Float, color: Int) {
            val dx = toX - fromX
            val dy = toY - fromY
            val length = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
            val ux = dx / length
            val uy = dy / length
            val endX = toX - ux * dp(15)
            val endY = toY - uy * dp(15)
            strokePaint.color = withAlpha(color, .85f)
            strokePaint.strokeWidth = dp(2)
            canvas.drawLine(fromX, fromY, endX, endY, strokePaint)
            val head = dp(8)
            val path = Path().apply {
                moveTo(toX, toY)
                lineTo(toX - ux * head - uy * head * .55f, toY - uy * head + ux * head * .55f)
                lineTo(toX - ux * head + uy * head * .55f, toY - uy * head - ux * head * .55f)
                close()
            }
            fillPaint.color = withAlpha(color, .9f)
            canvas.drawPath(path, fillPaint)
            strokePaint.strokeWidth = dp(1)
        }

        private fun drawCaption(canvas: Canvas, text: String, x: Float, baseline: Float, color: Int) {
            textPaint.color = color
            val left = x.coerceIn(dp(4), (width - dp(40)).coerceAtLeast(dp(4)))
            val maxTextWidth = (width - left - dp(12)).coerceAtLeast(dp(28))
            val lines = mutableListOf<String>()
            for (paragraph in text.split('\n')) {
                var line = ""
                for (character in paragraph) {
                    val candidate = line + character
                    if (line.isNotEmpty() && textPaint.measureText(candidate) > maxTextWidth) {
                        lines += line
                        line = character.toString()
                    } else {
                        line = candidate
                    }
                }
                lines += line
            }
            val lineHeight = dp(16)
            val totalHeight = lineHeight * lines.size
            val lastBaselineLimit = (height - dp(4) - lineHeight * (lines.size - 1))
                .coerceAtLeast(lineHeight)
            val firstBaseline = (baseline - totalHeight + lineHeight * .8f)
                .coerceIn(lineHeight, lastBaselineLimit)
            val lastBottom = firstBaseline + lineHeight * (lines.size - 1) + dp(4)
            val background = RectF(left - dp(4), firstBaseline - lineHeight * .8f - dp(3),
                left + maxOf(dp(20), lines.maxOfOrNull { textPaint.measureText(it) } ?: 0f) + dp(4),
                lastBottom.coerceAtMost(height - dp(2)))
            fillPaint.color = withAlpha(data.surfaceColor, .94f)
            canvas.drawRoundRect(background, dp(6), dp(6), fillPaint)
            lines.forEachIndexed { index, line ->
                canvas.drawText(line, left, firstBaseline + lineHeight * index, textPaint)
            }
        }

        private fun centerX(zone: GestureGeometry.HotZoneBounds) = (zone.left + zone.right) / 2f
        private fun centerY(zone: GestureGeometry.HotZoneBounds) = (zone.top + zone.bottom) / 2f
        private fun dp(value: Int) = value * density
        private fun withAlpha(color: Int, alpha: Float) =
            (color and 0x00ffffff) or ((android.graphics.Color.alpha(color) * alpha).toInt() shl 24)
    }
}

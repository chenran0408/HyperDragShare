package com.leaf.hyperdragshare.gestures.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.WindowManager
import com.leaf.hyperdragshare.codex.BottomCornerPreviewOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class BottomCornerPreviewOverlayTest {
    @Test
    fun showUpdatesLiveAndRepeatedStopRemovesItsTouchExclusion() {
        val context = RuntimeEnvironment.getApplication()
        val windows = FakeWindowOperations()
        val baseline = registeredRectCount()
        val overlay = BottomCornerPreviewOverlay(context, windows) { true }
        val first = data(12)
        val updated = data(18)

        assertTrue(overlay.show(first))
        assertEquals(1, registeredRectCount() - baseline)
        assertTrue(windows.params!!.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(windows.params!!.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertEquals(first, overlay.currentPreviewData())

        overlay.update(updated)
        assertEquals(updated, overlay.currentPreviewData())
        overlay.stop()
        overlay.stop()
        assertEquals(baseline, registeredRectCount())
        assertNull(overlay.currentPreviewData())
    }

    @Test
    fun failedAddClearsRegisteredRegionAndDoesNotKeepAView() {
        val context = RuntimeEnvironment.getApplication()
        val windows = FakeWindowOperations().apply { failAdd = true }
        val baseline = registeredRectCount()
        val overlay = BottomCornerPreviewOverlay(context, windows) { true }

        assertFalse(overlay.show(data(12)))
        assertEquals(baseline, registeredRectCount())
        assertNull(overlay.currentPreviewData())
    }

    @Test
    fun failedRemoveStillClearsTouchExclusionAndAllowsAnotherStop() {
        val context = RuntimeEnvironment.getApplication()
        val windows = FakeWindowOperations().apply {
            failRemove = true
            failRemoveImmediately = true
        }
        val baseline = registeredRectCount()
        val overlay = BottomCornerPreviewOverlay(context, windows) { true }

        assertTrue(overlay.show(data(12)))
        assertEquals(baseline + 1, registeredRectCount())
        overlay.stop()
        assertEquals(baseline, registeredRectCount())
        overlay.stop()
        assertEquals(baseline, registeredRectCount())
        assertTrue(windows.removeAttempted)
        assertTrue(windows.removeImmediatelyAttempted)
    }

    @Test
    fun previewDrawsPortraitLandscapeAndSmallFramesAndDraftUpdatesChangePixels() {
        val context = RuntimeEnvironment.getApplication()
        val windows = FakeWindowOperations()
        val overlay = BottomCornerPreviewOverlay(context, windows) { true }
        assertTrue(overlay.show(data(12)))
        val view = windows.addedView!!

        val portrait = render(view, 320, 640)
        overlay.update(data(18))
        val portraitUpdated = render(view, 320, 640)
        assertTrue(differentPixelCount(portrait, portraitUpdated) > 0)

        val landscape = render(view, 640, 320)
        assertTrue(nonTransparentPixelCount(landscape) > 0)
        val small = render(view, 160, 90)
        assertTrue(nonTransparentPixelCount(small) > 0)

        portrait.recycle()
        portraitUpdated.recycle()
        landscape.recycle()
        small.recycle()
        overlay.stop()
    }

    private fun render(view: View, width: Int, height: Int): Bitmap {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            view.draw(Canvas(bitmap))
        }
    }

    private fun differentPixelCount(before: Bitmap, after: Bitmap): Int {
        var count = 0
        for (y in 0 until before.height) for (x in 0 until before.width) {
            if (before.getPixel(x, y) != after.getPixel(x, y)) count++
        }
        return count
    }

    private fun nonTransparentPixelCount(bitmap: Bitmap): Int {
        var count = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 0) count++
        }
        return count
    }

    private fun registeredRectCount(): Int = OverlayTouchRegions.read()
        .getIntArray(ConfigContract.KEY_OVERLAY_TOUCH_RECTS)?.size?.div(4) ?: 0

    private fun data(height: Int) = BottomCornerPreviewOverlay.PreviewData(
        hotWidthPercent = 12,
        hotHeightPercent = height,
        bottomGapDp = 24,
        fanDistancePercent = 12,
        sideDistancePercent = 36,
        sideTopSafeMarginPercent = 20,
        sideDirectionHorizontal = true,
        sideDirectionUp = true,
        sideDirectionDown = true,
        leftCornerEnabled = true,
        rightCornerEnabled = false,
        sideEnabled = true,
        primaryColor = 0xff336699.toInt(),
        surfaceColor = 0xffeeeeee.toInt(),
        textColor = 0xff111111.toInt(),
        mutedColor = 0xff777777.toInt(),
    )

    private class FakeWindowOperations : BottomCornerPreviewOverlay.PreviewWindowOperations {
        var failAdd = false
        var failRemove = false
        var failRemoveImmediately = false
        var removeAttempted = false
        var removeImmediatelyAttempted = false
        var addedView: View? = null
        var params: WindowManager.LayoutParams? = null

        override fun add(manager: WindowManager, view: View, params: WindowManager.LayoutParams) {
            this.addedView = view
            this.params = params
            if (failAdd) throw IllegalStateException("add failed")
        }

        override fun remove(manager: WindowManager, view: View) {
            removeAttempted = true
            if (failRemove) throw IllegalStateException("remove failed")
            addedView = null
        }

        override fun removeImmediately(manager: WindowManager, view: View) {
            removeImmediatelyAttempted = true
            if (failRemoveImmediately) throw IllegalStateException("immediate remove failed")
            addedView = null
        }
    }
}

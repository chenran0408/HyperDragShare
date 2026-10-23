package com.leaf.hyperdragshare.codex

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Random

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StashDragShadowTest {
    @Test fun captureScalesPreviewAndHotspotAndHostRestoresOriginalMetrics() {
        val payload = StashDragShadowSnapshot.capture(ColorShadow())

        assertNotNull(payload)
        payload!!
        assertEquals(256, payload.bitmapWidthPx)
        assertEquals(128, payload.bitmapHeightPx)
        assertEquals(640, payload.shadowWidthPx)
        assertEquals(320, payload.shadowHeightPx)
        assertEquals(192, payload.scaledHotspotX)
        assertEquals(64, payload.scaledHotspotY)
        assertTrue(payload.png.size <= StashDragShadowSnapshot.MAX_PNG_BYTES)

        val hostShadow = StashDragShadowBuilder.decode(
            payload.png, payload.bitmapWidthPx, payload.bitmapHeightPx,
            payload.shadowWidthPx, payload.shadowHeightPx,
            payload.scaledHotspotX, payload.scaledHotspotY,
        )
        assertNotNull(hostShadow)
        hostShadow!!
        val size = Point()
        val hotspot = Point()
        hostShadow.onProvideShadowMetrics(size, hotspot)
        assertEquals(Point(640, 320), size)
        assertEquals(Point(480, 160), hotspot)

        val rendered = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
        hostShadow.onDrawShadow(Canvas(rendered))
        assertEquals(Color.MAGENTA, rendered.getPixel(32, 16))
        hostShadow.recycle()
        hostShadow.recycle()
        assertTrue(hostShadow.isRecycled)
        rendered.recycle()
    }

    @Test fun rejectsOversizedAndMismatchedPayloadBeforeDecoding() {
        val payload = StashDragShadowSnapshot.capture(ColorShadow())!!
        assertNull(StashDragShadowBuilder.decode(
            ByteArray(StashDragShadowSnapshot.MAX_PNG_BYTES + 1),
            payload.bitmapWidthPx, payload.bitmapHeightPx, payload.shadowWidthPx,
            payload.shadowHeightPx, payload.scaledHotspotX, payload.scaledHotspotY,
        ))
        assertNull(StashDragShadowBuilder.decode(
            payload.png, payload.bitmapWidthPx + 1, payload.bitmapHeightPx,
            payload.shadowWidthPx, payload.shadowHeightPx, payload.scaledHotspotX, payload.scaledHotspotY,
        ))
    }

    @Test fun highEntropyPreviewIsReducedUntilBinderPayloadFitsLimit() {
        val payload = StashDragShadowSnapshot.capture(NoiseShadow())

        assertNotNull(payload)
        assertTrue(payload!!.bitmapWidthPx < StashDragShadowSnapshot.MAX_EDGE_PX)
        assertTrue(payload.bitmapHeightPx < StashDragShadowSnapshot.MAX_EDGE_PX)
        assertTrue(payload.png.size <= StashDragShadowSnapshot.MAX_PNG_BYTES)
    }

    private class ColorShadow : View.DragShadowBuilder() {
        override fun onProvideShadowMetrics(size: Point, touch: Point) {
            size.set(640, 320)
            touch.set(480, 160)
        }

        override fun onDrawShadow(canvas: Canvas) {
            canvas.drawColor(Color.MAGENTA)
        }
    }

    private class NoiseShadow : View.DragShadowBuilder() {
        private val noise = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
            val random = Random(7)
            val pixels = IntArray(width * height) {
                Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            }
            setPixels(pixels, 0, width, 0, 0, width, height)
        }

        override fun onProvideShadowMetrics(size: Point, touch: Point) {
            size.set(256, 256)
            touch.set(128, 128)
        }

        override fun onDrawShadow(canvas: Canvas) {
            canvas.drawBitmap(noise, 0f, 0f, null)
            noise.recycle()
        }
    }
}

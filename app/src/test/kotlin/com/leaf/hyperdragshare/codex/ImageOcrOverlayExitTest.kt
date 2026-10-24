package com.leaf.hyperdragshare.codex

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.view.View
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises the actual private OCR Job and its one-shot InteractiveOverlayWindow dismissal. */
@RunWith(RobolectricTestRunner::class)
@Config(
    manifest = Config.NONE,
    sdk = [35],
    instrumentedPackages = ["com.leaf.hyperdragshare.codex"],
    shadows = [
        ImageOcrOverlayExitTest.ImageDecoderShadow::class,
        ImageOcrOverlayExitTest.AicrOcrClientShadow::class,
        ImageOcrOverlayExitTest.AicrBarcodeClientShadow::class,
    ],
)
@LooperMode(LooperMode.Mode.PAUSED)
class ImageOcrOverlayExitTest {
    private lateinit var decode: DecodePlan
    private var currentWindow: Any? = null

    @Before
    fun resetShadows() {
        ImageDecoderShadow.reset()
        AicrOcrClientShadow.callCount = 0
        AicrBarcodeClientShadow.callCount = 0
        decode = DecodePlan(Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888))
        ImageDecoderShadow.plan = decode
    }

    @After
    fun releaseDecoder() {
        currentWindow?.let(::dismissTwice)
        if (::decode.isInitialized) {
            decode.release.countDown()
            decode.worker?.join(TimeUnit.SECONDS.toMillis(2))
        }
        ShadowLooper.shadowMainLooper().idle()
        ImageDecoderShadow.reset()
    }

    @Test
    fun dismissWhileOcrDecodeIsPendingRecyclesLateBitmapAndDoesNotStartRecognition() {
        val resultCallbacks = mutableListOf<String?>()
        val receiverContext = ReceiverCountingContext(RuntimeEnvironment.getApplication())
        val (job, window) = createJob(receiverContext, qr = false) { resultCallbacks.add(it) }

        startDecode(job)
        await(decode.entered, "image decoder did not enter its controlled boundary")
        dismissTwice(window)
        assertEquals(1, receiverContext.unregisterCount)
        assertEquals(listOf(null), resultCallbacks)

        releaseAndWaitForDecode()
        ShadowLooper.shadowMainLooper().idle()

        assertTrue("late decoded bitmap must be released by the cancelled Job", decode.bitmap.isRecycled)
        assertEquals(0, AicrOcrClientShadow.callCount)
        assertEquals(0, AicrBarcodeClientShadow.callCount)
        assertEquals("dismissal callback must be delivered once", listOf(null), resultCallbacks)
        assertWindowDisposed(window)
    }

    @Test
    fun dismissWhileQrDecodeIsPendingRecyclesLateBitmapAndDoesNotStartBarcodeScan() {
        val resultCallbacks = mutableListOf<String?>()
        val receiverContext = ReceiverCountingContext(RuntimeEnvironment.getApplication())
        val (job, window) = createJob(receiverContext, qr = true) { resultCallbacks.add(it) }

        startDecode(job)
        await(decode.entered, "image decoder did not enter its controlled boundary")
        dismissTwice(window)
        assertEquals(1, receiverContext.unregisterCount)
        assertEquals(listOf(null), resultCallbacks)

        releaseAndWaitForDecode()
        ShadowLooper.shadowMainLooper().idle()

        assertTrue("late decoded bitmap must be released by the cancelled Job", decode.bitmap.isRecycled)
        assertEquals(0, AicrOcrClientShadow.callCount)
        assertEquals(0, AicrBarcodeClientShadow.callCount)
        assertEquals("dismissal callback must be delivered once", listOf(null), resultCallbacks)
        assertWindowDisposed(window)
    }

    private fun createJob(
        context: Context,
        qr: Boolean,
        onResult: (String?) -> Unit,
    ): Pair<Any, Any> {
        val jobClass = Class.forName("${ImageOcrOverlay::class.java.name}\$Job")
        val constructor = jobClass.declaredConstructors.single { it.parameterCount == 8 }
        constructor.isAccessible = true
        val uri = Uri.fromFile(File(context.cacheDir, "pending-image.png"))
        val job = constructor.newInstance(context, uri, qr, null as View?, onResult,
            null as Activity?, null, "-")
        val window = jobClass.getDeclaredMethod("getWindow").apply { isAccessible = true }.invoke(job)
        currentWindow = requireNotNull(window)
        return job to requireNotNull(window)
    }

    private fun startDecode(job: Any) {
        job.javaClass.getDeclaredMethod("decode").apply { isAccessible = true }.invoke(job)
    }

    private fun dismissTwice(window: Any) {
        val dismiss = window.javaClass.getDeclaredMethod("dismiss").apply { isAccessible = true }
        dismiss.invoke(window)
        dismiss.invoke(window)
    }

    private fun assertWindowDisposed(window: Any) {
        val disposed = window.javaClass.getDeclaredField("disposed").apply { isAccessible = true }
        assertTrue(disposed.getBoolean(window))
    }

    private fun releaseAndWaitForDecode() {
        decode.release.countDown()
        await(decode.returned, "image decoder did not return")
        decode.worker?.join(TimeUnit.SECONDS.toMillis(4))
        assertFalse("decode worker must finish after release", decode.worker?.isAlive ?: true)
    }

    private fun await(latch: CountDownLatch, message: String) {
        assertTrue(message, latch.await(4, TimeUnit.SECONDS))
    }

    class DecodePlan(val bitmap: Bitmap) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        @Volatile var worker: Thread? = null
    }

    private class ReceiverCountingContext(base: Context) : ContextWrapper(base) {
        @Volatile var unregisterCount = 0
            private set

        override fun unregisterReceiver(receiver: android.content.BroadcastReceiver) {
            unregisterCount++
            super.unregisterReceiver(receiver)
        }
    }

    // ImageDecoder is an Android SDK class. Mark it explicitly so Robolectric applies
    // this test-only replacement to its framework static methods on SDK 35.
    @Implements(value = ImageDecoder::class, isInAndroidSdk = true)
    class ImageDecoderShadow {
        companion object {
            @Volatile var plan: DecodePlan? = null

            fun reset() {
                plan?.release?.countDown()
                plan = null
            }

            @Implementation
            @JvmStatic
            fun decodeBitmap(
                source: ImageDecoder.Source?,
                listener: ImageDecoder.OnHeaderDecodedListener,
            ): Bitmap {
                val current = requireNotNull(plan)
                current.worker = Thread.currentThread()
                current.entered.countDown()
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
                var wasInterrupted = false
                try {
                    while (true) {
                        val remaining = deadline - System.nanoTime()
                        if (remaining <= 0L) throw IllegalStateException("controlled decode timed out")
                        try {
                            if (!current.release.await(remaining, TimeUnit.NANOSECONDS)) {
                                throw IllegalStateException("controlled decode timed out")
                            }
                            return current.bitmap
                        } catch (_: InterruptedException) {
                            // Decode is deliberately allowed to finish after the owning Job dismisses.
                            wasInterrupted = true
                        }
                    }
                } finally {
                    current.returned.countDown()
                    if (wasInterrupted) Thread.currentThread().interrupt()
                }
            }
        }
    }

    @Implements(value = AicrOcrClient::class, isInAndroidSdk = false)
    class AicrOcrClientShadow {
        companion object {
            @Volatile var callCount = 0

            @Implementation
            @JvmStatic
            fun recognize(
                context: Context,
                bitmap: Bitmap,
                callback: AicrOcrClient.Callback,
            ): AicrOcrClient.Request? {
                callCount++
                return null
            }
        }
    }

    @Implements(value = AicrBarcodeClient::class, isInAndroidSdk = false)
    class AicrBarcodeClientShadow {
        companion object {
            @Volatile var callCount = 0

            @Implementation
            @JvmStatic
            fun scan(
                context: Context,
                bitmap: Bitmap,
                callback: AicrBarcodeClient.Callback,
            ): AicrBarcodeClient.Request? {
                callCount++
                return null
            }
        }
    }
}

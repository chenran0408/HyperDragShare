package com.leaf.hyperdragshare.codex

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises ImageOcrActivity's real intent, request-generation, and main-thread callback paths. */
@RunWith(RobolectricTestRunner::class)
@Config(
    manifest = Config.NONE,
    sdk = [35],
    instrumentedPackages = ["com.leaf.hyperdragshare.codex"],
    shadows = [
        ImageOcrActivityExitTest.IncomingImageCacheShadow::class,
        ImageOcrActivityExitTest.ImageOcrOverlayShadow::class,
        ImageOcrActivityExitTest.BigBangOverlayLauncherShadow::class,
    ],
)
@LooperMode(LooperMode.Mode.PAUSED)
class ImageOcrActivityExitTest {
    private lateinit var plan: CopyPlan
    private lateinit var controller: ActivityController<ImageOcrActivity>
    private lateinit var activity: ImageOcrActivity

    @Before
    fun startNormalImageCopy() {
        IncomingImageCacheShadow.reset()
        ImageOcrOverlayShadow.reset()
        BigBangOverlayLauncherShadow.reset()
        plan = CopyPlan()
        IncomingImageCacheShadow.plan = plan
        controller = Robolectric.buildActivity(ImageOcrActivity::class.java, normalImageIntent())
            .create().start().resume()
        activity = controller.get()
        await(plan.entered, "copy worker did not enter its controlled boundary")
    }

    @After
    fun cleanUp() {
        if (::plan.isInitialized) {
            plan.release.countDown()
            try {
                plan.returned.await(2, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        if (::activity.isInitialized && !activity.isDestroyed) {
            controller.pause().stop().destroy()
        }
        idleMain()
        if (::plan.isInitialized) plan.createdFile?.delete()
        if (::plan.isInitialized) IncomingImageCacheShadow.reset()
    }

    @Test
    fun lateSuccessfulCopyCannotReplaceNewHostedImageAndDeletesItsOwnedFile() {
        val newUri = Uri.parse("content://new.sender/current-image")
        controller.newIntent(Intent().putExtra(ImageOcrActivity.EXTRA_HOSTED_URI, newUri))
        assertEquals(listOf(newUri), ImageOcrOverlayShadow.startedUris())

        plan.release.countDown()
        await(plan.returned, "late copy did not return")
        awaitCopyWorkerExit()
        idleMain()

        assertEquals("only the newer hosted URI may open", listOf(newUri),
            ImageOcrOverlayShadow.startedUris())
        assertNotNull(plan.createdFile)
        assertFalse("stale copy output must be deleted by the activity", plan.createdFile!!.exists())
        assertFalse(activity.isFinishing)
    }

    @Test
    fun successfulNormalCopyStartsHostedOcrAndKeepsItsCacheFileForTheOverlay() {
        plan.release.countDown()
        await(plan.returned, "normal copy did not return")
        awaitCopyWorkerExit()
        idleMain()

        val file = requireNotNull(plan.createdFile)
        assertTrue("the active OCR overlay still owns the copied input", file.exists())
        assertEquals(listOf(Uri.fromFile(file)), ImageOcrOverlayShadow.startedUris())
        assertFalse(activity.isFinishing)
        assertEquals(0, BigBangOverlayLauncherShadow.requestCount)
    }

    @Test
    fun lateSuccessfulCopyCannotReplaceBigBangIntentAndDeletesItsOwnedFile() {
        val args = Bundle().apply { putString("text", "new text request") }
        assertFalse("legacy Bundle has no trace marker", args.containsKey(OperationTrace.EXTRA))
        controller.newIntent(Intent().putExtra(ImageOcrActivity.EXTRA_BIGBANG_ARGS, args))
        assertEquals(1, BigBangOverlayLauncherShadow.requestCount)

        plan.release.countDown()
        await(plan.returned, "late copy did not return")
        awaitCopyWorkerExit()
        idleMain()

        assertEquals("old image must not open over the newer text request", emptyList<Uri>(),
            ImageOcrOverlayShadow.startedUris())
        assertNotNull(plan.createdFile)
        assertFalse("stale copy output must be deleted by the activity", plan.createdFile!!.exists())
        assertFalse(activity.isFinishing)
    }

    @Test
    fun lateCopyFailureCannotFinishActivityForNewHostedImage() {
        controller.newIntent(Intent().putExtra(
            ImageOcrActivity.EXTRA_HOSTED_URI,
            Uri.parse("content://new.sender/current-image"),
        ))
        plan.failure = IOException("old sender read failed")
        plan.release.countDown()
        await(plan.returned, "late failing copy did not return")
        awaitCopyWorkerExit()
        idleMain()

        assertFalse("an obsolete failure must not finish the current hosted request", activity.isFinishing)
        assertEquals(1, ImageOcrOverlayShadow.startedUris().size)
    }

    @Test
    fun lateCopyFailureCannotFinishActivityForNewBigBangRequest() {
        controller.newIntent(Intent().putExtra(
            ImageOcrActivity.EXTRA_BIGBANG_ARGS,
            Bundle().apply { putString("text", "new text request") },
        ))
        plan.failure = IOException("old sender read failed")
        plan.release.countDown()
        await(plan.returned, "late failing copy did not return")
        awaitCopyWorkerExit()
        idleMain()

        assertFalse("an obsolete failure must not finish the current text request", activity.isFinishing)
        assertEquals(1, BigBangOverlayLauncherShadow.requestCount)
    }

    @Test
    fun successfulCopyReturningAfterDestroyDeletesItsFileWithoutStartingUi() {
        controller.pause().stop().destroy()
        assertTrue(activity.isDestroyed)

        plan.release.countDown()
        await(plan.returned, "copy did not return after Activity destroy")
        awaitCopyWorkerExit()
        idleMain()

        assertNotNull(plan.createdFile)
        assertFalse("destroyed Activity must delete the owned cache output", plan.createdFile!!.exists())
        assertTrue(ImageOcrOverlayShadow.startedUris().isEmpty())
        assertEquals(0, BigBangOverlayLauncherShadow.requestCount)
    }

    private fun normalImageIntent() = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://old.sender/image"))

    private fun await(latch: CountDownLatch, message: String) {
        assertTrue(message, latch.await(4, TimeUnit.SECONDS))
    }

    private fun idleMain() {
        ShadowLooper.shadowMainLooper().idle()
    }

    private fun awaitCopyWorkerExit() {
        val field = ImageOcrActivity::class.java.getDeclaredField("worker")
        field.isAccessible = true
        val worker = field.get(activity) as? Thread ?: return
        worker.join(TimeUnit.SECONDS.toMillis(4))
        assertFalse("copy worker must finish after the controlled result", worker.isAlive)
    }

    class CopyPlan {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        @Volatile var failure: IOException? = null
        @Volatile var createdFile: File? = null
    }

    @Implements(value = IncomingImageCache::class, isInAndroidSdk = false)
    class IncomingImageCacheShadow {
        companion object {
            @Volatile var plan: CopyPlan? = null

            fun reset() {
                plan?.release?.countDown()
                plan = null
            }
        }

        // IncomingImageCache is a Kotlin object without @JvmStatic on copy(), so production
        // dispatches through INSTANCE.copy and Robolectric needs an instance shadow method.
        @Implementation
        @Throws(IOException::class)
        fun copy(context: Context, source: Uri): File {
            val current = requireNotNull(plan)
            current.entered.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4)
            var wasInterrupted = false
            try {
                while (true) {
                    val remaining = deadline - System.nanoTime()
                    if (remaining <= 0L) throw IOException("controlled image copy timed out")
                    try {
                        if (!current.release.await(remaining, TimeUnit.NANOSECONDS)) {
                            throw IOException("controlled image copy timed out")
                        }
                        break
                    } catch (_: InterruptedException) {
                        // Model a content provider read that does not stop when its thread is interrupted.
                        wasInterrupted = true
                    }
                }
                current.failure?.let { throw it }
                val file = File(context.cacheDir, "ocr-input-test/${UUID.randomUUID()}.image")
                file.parentFile?.mkdirs()
                file.writeBytes(byteArrayOf(1, 2, 3))
                current.createdFile = file
                return file
            } finally {
                current.returned.countDown()
                if (wasInterrupted) Thread.currentThread().interrupt()
            }
        }
    }

    @Implements(value = ImageOcrOverlay::class, isInAndroidSdk = false)
    class ImageOcrOverlayShadow {
        companion object {
            private val uris = mutableListOf<Uri>()

            fun reset() = uris.clear()
            fun startedUris(): List<Uri> = synchronized(uris) { uris.toList() }

            @Implementation
            @JvmStatic
            fun startHosted(activity: Activity, uri: Uri, qr: Boolean): Boolean {
                synchronized(uris) { uris.add(uri) }
                return true
            }

            @Implementation
            @JvmStatic
            fun startHosted(activity: Activity, uri: Uri, qr: Boolean, operation: String): Boolean {
                synchronized(uris) { uris.add(uri) }
                return true
            }
        }
    }

    @Implements(value = BigBangOverlayLauncher::class, isInAndroidSdk = false)
    class BigBangOverlayLauncherShadow {
        companion object {
            @Volatile var requestCount = 0

            fun reset() { requestCount = 0 }

            @Implementation
            @JvmStatic
            fun requestHosted(activity: Activity, input: Bundle?): Boolean {
                requestCount++
                return true
            }
        }
    }
}

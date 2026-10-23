package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.os.Handler
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class EmbeddedStashDragBridgeTest {
    private lateinit var host: FakeHost
    private lateinit var bridge: EmbeddedStashDragBridge
    private var grants = 0
    private var revokes = 0
    private val shadowPayload = StashDragShadowPayload(byteArrayOf(1), 1, 1, 1, 1, 0, 0)

    @Before fun setUp() {
        host = FakeHost()
        bridge = EmbeddedStashDragBridge(
            Handler(Looper.getMainLooper()),
            startNativeDrag = host::start,
            grant = { _, _ -> grants++ },
            revoke = { revokes++ },
        )
    }

    @Test fun hostStartRejectionReleasesGrantAndReportsFailure() {
        val starts = mutableListOf<Boolean>()
        bridge.start(clip(), 1, shadowPayload, starts::add) {}

        assertTrue(bridge.isBusy)
        host.callback!!.onStarted(false)
        idleMain()

        assertEquals(listOf(false), starts)
        assertFalse(bridge.isBusy)
        assertEquals(1, grants)
        assertEquals(1, revokes)
    }

    @Test fun endArrivingBeforeQueuedStartCompletesBothCallbacksInOrder() {
        val callbacks = mutableListOf<String>()
        bridge.start(clip(), 1, shadowPayload,
            onStarted = { callbacks += "started:$it" },
            onEnded = { callbacks += "ended:$it" })

        host.callback!!.onEnded(false)
        idleMain()

        assertEquals(listOf("started:true", "ended:false"), callbacks)
        assertFalse(bridge.isBusy)
        assertEquals(1, revokes)
    }

    @Test fun revokeFailureDoesNotLeaveBridgeBusyOrSuppressStartFailure() {
        val failingBridge = EmbeddedStashDragBridge(
            Handler(Looper.getMainLooper()),
            startNativeDrag = { _, _, _, _ -> },
            grant = { _, _ -> throw IllegalStateException("partial grant") },
            revoke = { throw IllegalStateException("revoke unavailable") },
        )
        val starts = mutableListOf<Boolean>()

        failingBridge.start(clip(), 1, shadowPayload, starts::add) {}

        assertEquals(listOf(false), starts)
        assertFalse(failingBridge.isBusy)
    }

    @Test fun acceptedAndCancelledHostDragKeepsThenReleasesGrant() {
        val starts = mutableListOf<Boolean>()
        val ends = mutableListOf<Boolean>()
        bridge.start(clip(), 1, shadowPayload, starts::add, ends::add)
        assertSame(shadowPayload, host.shadow)
        host.callback!!.onStarted(true)
        idleMain()

        assertEquals(listOf(true), starts)
        assertTrue(bridge.isBusy)
        assertEquals(0, revokes)
        host.callback!!.onEnded(false)
        idleMain()

        assertEquals(listOf(false), ends)
        assertFalse(bridge.isBusy)
        assertEquals(1, revokes)
    }

    @Test fun hostDeathWhileStartIsPendingIgnoresLateResult() {
        val starts = mutableListOf<Boolean>()
        val ends = mutableListOf<Boolean>()
        bridge.start(clip(), 1, shadowPayload, starts::add, ends::add)

        bridge.abort()
        host.callback!!.onStarted(true)
        idleMain()

        assertEquals(listOf(false), starts)
        assertTrue(ends.isEmpty())
        assertFalse(bridge.isBusy)
        assertEquals(1, revokes)
    }

    @Test fun hostDeathDuringDragCancelsOnceAndIgnoresLateEnd() {
        val ends = mutableListOf<Boolean>()
        bridge.start(clip(), 1, shadowPayload, {}, ends::add)
        host.callback!!.onStarted(true)
        idleMain()

        bridge.abort()
        host.callback!!.onEnded(true)
        idleMain()

        assertEquals(listOf(false), ends)
        assertFalse(bridge.isBusy)
        assertEquals(1, revokes)
    }

    @Test fun closeWaitsForCancelledDragAndRunsOnce() {
        var closes = 0
        val ends = mutableListOf<Boolean>()
        bridge.start(clip(), 1, shadowPayload, {}, ends::add)
        host.callback!!.onStarted(true)
        idleMain()
        assertTrue(bridge.deferClose { closes++ })

        host.callback!!.onEnded(false)
        idleMain()

        assertEquals(listOf(false), ends)
        assertEquals(1, closes)
        assertFalse(bridge.deferClose { closes++ })
    }

    private fun clip() = ClipData.newPlainText("stash", "sample")
    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private class FakeHost {
        var callback: EmbeddedStashDragBridge.Callback? = null
        var shadow: StashDragShadowPayload? = null
        fun start(clip: ClipData, flags: Int, shadow: StashDragShadowPayload?,
                  callback: EmbeddedStashDragBridge.Callback) {
            require(clip.itemCount == 1 && flags == 1)
            require(shadow != null && shadow.png.isNotEmpty())
            this.shadow = shadow
            this.callback = callback
        }
    }
}

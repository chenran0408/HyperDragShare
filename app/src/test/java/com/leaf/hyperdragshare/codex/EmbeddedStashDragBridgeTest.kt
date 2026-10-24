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
import org.robolectric.shadows.ShadowLog

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
        val operation = OperationTrace.fresh("stash")
        val starts = mutableListOf<Boolean>()
        bridge.start(tracedClip(operation), 1, shadowPayload, starts::add) {}

        assertTrue(bridge.isBusy)
        host.callback!!.onStarted(false)
        idleMain()

        assertEquals(listOf(false), starts)
        assertFalse(bridge.isBusy)
        assertEquals(1, grants)
        assertEquals(1, revokes)
        assertEvent(operation, "stage=host_drag_start outcome=rejected")
    }

    @Test fun traceExtraCrossesTheHostCallbackAndDuplicateCallbacksAreLoggedOnce() {
        val operation = OperationTrace.fresh("stash")
        val data = clip().apply { OperationTrace.put(this, operation) }
        val starts = mutableListOf<Boolean>()
        val ends = mutableListOf<Boolean>()
        bridge.start(data, 1, shadowPayload, starts::add, ends::add)
        assertEquals(operation, host.operation)

        host.callback!!.onStarted(true)
        idleMain()
        host.callback!!.onStarted(false)
        host.callback!!.onEnded(false)
        idleMain()
        host.callback!!.onEnded(true)
        idleMain()

        assertEquals(listOf(true), starts)
        assertEquals(listOf(false), ends)
        assertFalse(bridge.isBusy)
        val events = ShadowLog.getLogs().filter { it.tag == "DragShare/Operation" }
            .map { it.msg }.filter { it.contains("op=$operation ") }
        assertTrue(events.any { it.contains("stage=host_drag_start outcome=started") })
        assertTrue(events.any { it.contains("stage=host_drag_start_callback outcome=duplicate_ignored") })
        assertTrue(events.any { it.contains("stage=bridge_drop_result outcome=not_accepted_or_aborted") })
        assertTrue(events.any { it.contains("stage=host_drag_end_callback outcome=duplicate_ignored") })
        assertTrue(events.none { it.contains("sample") })
    }

    @Test fun missingOptionalTraceExtraDoesNotChangeDragBehavior() {
        val starts = mutableListOf<Boolean>()
        val ends = mutableListOf<Boolean>()
        bridge.start(clip(), 1, shadowPayload, starts::add, ends::add)

        host.callback!!.onStarted(true)
        idleMain()
        host.callback!!.onEnded(true)
        idleMain()

        assertEquals(listOf(true), starts)
        assertEquals(listOf(true), ends)
        assertFalse(bridge.isBusy)
        assertEquals(1, grants)
        assertEquals(1, revokes)
    }

    @Test fun endArrivingBeforeQueuedStartCompletesBothCallbacksInOrder() {
        val operation = OperationTrace.fresh("stash")
        val callbacks = mutableListOf<String>()
        bridge.start(tracedClip(operation), 1, shadowPayload,
            onStarted = { callbacks += "started:$it" },
            onEnded = { callbacks += "ended:$it" })

        host.callback!!.onEnded(false)
        idleMain()

        assertEquals(listOf("started:true", "ended:false"), callbacks)
        assertFalse(bridge.isBusy)
        assertEquals(1, revokes)
        val events = operationEvents(operation)
        assertTrue(events.indexOfFirst { it.contains("stage=host_drag_start_callback outcome=resolved_from_end_unknown") } >= 0)
        assertTrue(events.indexOfFirst { it.contains("stage=bridge_drop_result outcome=not_accepted_or_aborted") } >
                events.indexOfFirst { it.contains("stage=host_drag_start_callback outcome=resolved_from_end_unknown") })
    }

    @Test fun revokeFailureDoesNotLeaveBridgeBusyOrSuppressStartFailure() {
        val operation = OperationTrace.fresh("stash")
        val failingBridge = EmbeddedStashDragBridge(
            Handler(Looper.getMainLooper()),
            startNativeDrag = { _, _, _, _ -> },
            grant = { _, _ -> throw IllegalStateException("partial grant") },
            revoke = { throw IllegalStateException("revoke unavailable") },
        )
        val starts = mutableListOf<Boolean>()

        failingBridge.start(tracedClip(operation), 1, shadowPayload, starts::add) {}

        assertEquals(listOf(false), starts)
        assertFalse(failingBridge.isBusy)
        assertEvent(operation, "stage=uri_grant outcome=failed")
    }

    @Test fun failedNativeHandoffReleasesGrantWithoutClaimingDropResult() {
        val operation = OperationTrace.fresh("stash")
        val revocationCount = intArrayOf(0)
        val failedBridge = EmbeddedStashDragBridge(
            Handler(Looper.getMainLooper()),
            startNativeDrag = { _, _, _, _ -> throw IllegalStateException("handoff failed") },
            grant = { _, _ -> },
            revoke = { revocationCount[0]++ },
        )
        val starts = mutableListOf<Boolean>()
        val ends = mutableListOf<Boolean>()

        failedBridge.start(tracedClip(operation), 1, shadowPayload, starts::add, ends::add)

        assertEquals(listOf(false), starts)
        assertTrue(ends.isEmpty())
        assertFalse(failedBridge.isBusy)
        assertEquals(1, revocationCount[0])
        assertEvent(operation, "stage=host_drag_start outcome=rejected")
        assertTrue(operationEvents(operation).none { it.contains("stage=bridge_drop_result") })
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
        val operation = OperationTrace.fresh("stash")
        val starts = mutableListOf<Boolean>()
        val ends = mutableListOf<Boolean>()
        bridge.start(tracedClip(operation), 1, shadowPayload, starts::add, ends::add)

        bridge.abort()
        host.callback!!.onStarted(true)
        idleMain()

        assertEquals(listOf(false), starts)
        assertTrue(ends.isEmpty())
        assertFalse(bridge.isBusy)
        assertEquals(1, revokes)
        assertEvent(operation, "stage=bridge_abort outcome=aborted")
        assertEvent(operation, "stage=host_drag_start_callback outcome=stale_ignored")
    }

    @Test fun hostDeathDuringDragCancelsOnceAndIgnoresLateEnd() {
        val operation = OperationTrace.fresh("stash")
        val ends = mutableListOf<Boolean>()
        bridge.start(tracedClip(operation), 1, shadowPayload, {}, ends::add)
        host.callback!!.onStarted(true)
        idleMain()

        bridge.abort()
        host.callback!!.onEnded(true)
        idleMain()

        assertEquals(listOf(false), ends)
        assertFalse(bridge.isBusy)
        assertEquals(1, revokes)
        assertEvent(operation, "stage=bridge_abort outcome=aborted")
        assertEvent(operation, "stage=host_drag_end_callback outcome=stale_ignored")
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
    private fun tracedClip(operation: String) = clip().apply { OperationTrace.put(this, operation) }
    private fun operationEvents(operation: String) = ShadowLog.getLogs()
        .filter { it.tag == "DragShare/Operation" }
        .map { it.msg }
        .filter { it.contains("op=$operation ") }
    private fun assertEvent(operation: String, event: String) =
        assertTrue("Missing trace event: $event", operationEvents(operation).any { it.contains(event) })
    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private class FakeHost {
        var callback: EmbeddedStashDragBridge.Callback? = null
        var shadow: StashDragShadowPayload? = null
        var operation: String? = null
        fun start(clip: ClipData, flags: Int, shadow: StashDragShadowPayload?,
                  callback: EmbeddedStashDragBridge.Callback) {
            require(clip.itemCount == 1 && flags == 1)
            require(shadow != null && shadow.png.isNotEmpty())
            operation = OperationTrace.read(clip)
            this.shadow = shadow
            this.callback = callback
        }
    }
}

package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.os.Handler

/** Keeps the module-side drag state alive while the native host window owns system drag startup. */
internal class EmbeddedStashDragBridge(
    private val main: Handler,
    private val startNativeDrag: (ClipData, Int, Callback) -> Unit,
    private val grant: (ClipData, Int) -> Unit,
    private val revoke: () -> Unit,
) {
    internal interface Callback {
        fun onStarted(started: Boolean)
        fun onEnded(accepted: Boolean)
    }

    private var generation = 0L
    private var startPending = false
    private var dragActive = false
    private var closeAfterDrag: (() -> Unit)? = null
    private var startedCallback: ((Boolean) -> Unit)? = null
    private var endedCallback: ((Boolean) -> Unit)? = null

    val isBusy: Boolean get() = startPending || dragActive

    fun start(clip: ClipData, flags: Int, onStarted: (Boolean) -> Unit, onEnded: (Boolean) -> Unit) {
        if (isBusy) {
            onStarted(false)
            return
        }
        val session = ++generation
        try {
            grant(clip, flags)
        } catch (error: RuntimeException) {
            revokeTemporaryGrants()
            onStarted(false)
            return
        }
        startPending = true
        startedCallback = onStarted
        endedCallback = onEnded
        val callback = object : Callback {
            override fun onStarted(started: Boolean) { main.post { finishStart(session, started) } }
            override fun onEnded(accepted: Boolean) { main.post { finishDrag(session, accepted) } }
        }
        try {
            startNativeDrag(clip, flags, callback)
        } catch (error: Exception) {
            finishStart(session, false)
        }
    }

    /** Returns true when close was deferred until the source drag ends. */
    fun deferClose(action: () -> Unit): Boolean {
        if (!isBusy) return false
        closeAfterDrag = action
        return true
    }

    /** Host/lease death ends any pending request and relinquishes its temporary URI grants. */
    fun abort() {
        if (!isBusy) return
        generation++
        val wasPending = startPending
        val wasActive = dragActive
        startPending = false
        dragActive = false
        closeAfterDrag = null
        revokeTemporaryGrants()
        val onStarted = startedCallback
        val onEnded = endedCallback
        startedCallback = null
        endedCallback = null
        if (wasPending) onStarted?.invoke(false)
        if (wasActive) onEnded?.invoke(false)
    }

    private fun finishStart(session: Long, started: Boolean) {
        if (session != generation || !startPending) return
        startPending = false
        dragActive = started
        val callback = startedCallback
        startedCallback = null
        if (!started) {
            endedCallback = null
            revokeTemporaryGrants()
            callback?.invoke(false)
            finishDeferredClose()
        } else callback?.invoke(true)
    }

    private fun finishDrag(session: Long, accepted: Boolean) {
        if (session != generation || (!startPending && !dragActive)) return
        // An end callback can race the queued start callback. The host only emits END
        // after startDragAndDrop returned true, so complete the start side first.
        val startWasPending = startPending
        val pendingStartCallback = startedCallback
        startPending = false
        dragActive = false
        startedCallback = null
        revokeTemporaryGrants()
        val callback = endedCallback
        endedCallback = null
        if (startWasPending) pendingStartCallback?.invoke(true)
        callback?.invoke(accepted)
        finishDeferredClose()
    }

    private fun revokeTemporaryGrants() {
        try {
            revoke()
        } catch (error: RuntimeException) {
            DragShareLog.w("DragShare/Sidebar", "Unable to revoke embedded drag grants", error)
        }
    }

    private fun finishDeferredClose() {
        closeAfterDrag?.also {
            closeAfterDrag = null
            it()
        }
    }
}

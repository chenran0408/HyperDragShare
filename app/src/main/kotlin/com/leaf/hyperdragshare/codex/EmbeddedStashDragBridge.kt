package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.os.Handler

/** Keeps the module-side drag state alive while the native host window owns system drag startup. */
internal class EmbeddedStashDragBridge(
    private val main: Handler,
    private val startNativeDrag: (ClipData, Int, StashDragShadowPayload?, Callback) -> Unit,
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
    private var operation = "-"

    val isBusy: Boolean get() = startPending || dragActive

    fun start(clip: ClipData, flags: Int, shadow: StashDragShadowPayload?,
              onStarted: (Boolean) -> Unit, onEnded: (Boolean) -> Unit) {
        val traceOperation = OperationTrace.read(clip)
        if (isBusy) {
            OperationTrace.event(traceOperation, "bridge_drag_request", "rejected_busy")
            onStarted(false)
            return
        }
        val session = ++generation
        OperationTrace.event(traceOperation, "bridge_drag_request", "received")
        try {
            grant(clip, flags)
        } catch (error: RuntimeException) {
            OperationTrace.event(traceOperation, "uri_grant", "failed")
            DragShareLog.w("DragShare/Sidebar", "Embedded stash URI grant failed error=" + error.javaClass.simpleName)
            revokeTemporaryGrants()
            onStarted(false)
            return
        }
        operation = traceOperation
        val hasUris = runCatching { (0 until clip.itemCount).any { clip.getItemAt(it).uri != null } }
            .getOrDefault(false)
        OperationTrace.event(traceOperation, "uri_grant", if (hasUris) "granted" else "not_required")
        startPending = true
        startedCallback = onStarted
        endedCallback = onEnded
        val callback = object : Callback {
            override fun onStarted(started: Boolean) { main.post { finishStart(session, traceOperation, started) } }
            override fun onEnded(accepted: Boolean) { main.post { finishDrag(session, traceOperation, accepted) } }
        }
        try {
            startNativeDrag(clip, flags, shadow, callback)
        } catch (error: Exception) {
            OperationTrace.event(traceOperation, "host_drag_request", "failed_${error.javaClass.simpleName}")
            DragShareLog.w("DragShare/Sidebar", "Embedded stash host request failed error=" + error.javaClass.simpleName)
            finishStart(session, traceOperation, false)
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
        OperationTrace.event(operation, "bridge_abort", "aborted")
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
        operation = "-"
        if (wasPending) onStarted?.invoke(false)
        if (wasActive) onEnded?.invoke(false)
    }

    private fun finishStart(session: Long, traceOperation: String, started: Boolean) {
        if (session != generation) {
            OperationTrace.event(traceOperation, "host_drag_start_callback", "stale_ignored")
            return
        }
        if (!startPending) {
            OperationTrace.event(traceOperation, "host_drag_start_callback", "duplicate_ignored")
            return
        }
        startPending = false
        dragActive = started
        OperationTrace.event(traceOperation, "host_drag_start", if (started) "started" else "rejected")
        val callback = startedCallback
        startedCallback = null
        if (!started) {
            endedCallback = null
            revokeTemporaryGrants()
            callback?.invoke(false)
            operation = "-"
            finishDeferredClose()
        } else callback?.invoke(true)
    }

    private fun finishDrag(session: Long, traceOperation: String, accepted: Boolean) {
        if (session != generation) {
            OperationTrace.event(traceOperation, "host_drag_end_callback", "stale_ignored")
            return
        }
        if (!startPending && !dragActive) {
            OperationTrace.event(traceOperation, "host_drag_end_callback", "duplicate_ignored")
            return
        }
        // Keep the existing callback order when END races START. Without the native
        // event, this callback alone cannot distinguish observed END from host abort.
        val startWasPending = startPending
        val pendingStartCallback = startedCallback
        if (startWasPending) OperationTrace.event(traceOperation, "host_drag_start_callback", "resolved_from_end_unknown")
        startPending = false
        dragActive = false
        startedCallback = null
        revokeTemporaryGrants()
        val callback = endedCallback
        endedCallback = null
        OperationTrace.event(traceOperation, "bridge_drop_result", if (accepted) "accepted" else "not_accepted_or_aborted")
        operation = "-"
        if (startWasPending) pendingStartCallback?.invoke(true)
        callback?.invoke(accepted)
        finishDeferredClose()
    }

    private fun revokeTemporaryGrants() {
        try {
            revoke()
        } catch (error: RuntimeException) {
            DragShareLog.w("DragShare/Sidebar", "Unable to revoke embedded drag grants error=" + error.javaClass.simpleName)
        }
    }

    private fun finishDeferredClose() {
        closeAfterDrag?.also {
            closeAfterDrag = null
            it()
        }
    }
}

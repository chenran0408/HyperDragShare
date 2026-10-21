package com.leaf.hyperdragshare.codex

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.DragEvent
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner

/**
 * One-shot interactive window for a host that already has [policy]'s window capability.
 * Content supplies its own Miuix theme and BackHandler; this does not change passive drag windows.
 * All calls belong on the main thread. A dismissed instance must not be reused.
 */
internal class InteractiveOverlayWindow(
    context: Context,
    policy: OverlayWindowPolicy,
    private val onDismissed: () -> Unit,
    private val onFailure: (RuntimeException) -> Unit,
    content: @Composable () -> Unit,
) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    private val dialog = ComponentDialog(context, android.R.style.Theme_Translucent_NoTitleBar)
    internal val composeView = ComposeView(dialog.context)
    internal val window: Window = requireNotNull(dialog.window)
    private var disposed = false
    private var dragWindowAttributes: WindowManager.LayoutParams? = null
    private var onDragFinished: ((Boolean) -> Unit)? = null

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(false)
        composeView.setViewTreeViewModelStoreOwner(this)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent(content)
        dialog.setContentView(composeView)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setWindowAnimations(0)
        window.attributes = window.attributes.apply {
            type = policy.windowType
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
            gravity = Gravity.TOP or Gravity.START
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        dialog.setOnDismissListener { dispose() }
    }

    fun show(pressConfirmedAt: Long = 0L): Boolean {
        check(!disposed) { "Create a new interactive window after dismissal" }
        return try {
            dialog.show()
            if (pressConfirmedAt > 0L) {
                val view = window.decorView
                val observer = view.viewTreeObserver
                observer.addOnDrawListener(object : android.view.ViewTreeObserver.OnDrawListener {
                    private var delivered = false
                    override fun onDraw() {
                        if (delivered) return
                        delivered = true
                        // Queue after drawing; never announce success before a window exists.
                        view.post {
                            if (observer.isAlive) observer.removeOnDrawListener(this)
                            if (!disposed && view.isShown) {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                                DragShareLog.i("DragShare/Press", "window first draw confirmedToFrameMs=" +
                                    (android.os.SystemClock.uptimeMillis() - pressConfirmedAt))
                            }
                        }
                    }
                })
            }
            true
        } catch (error: WindowManager.BadTokenException) {
            failedToShow(error)
        } catch (error: SecurityException) {
            failedToShow(error)
        }
    }

    fun dismiss() {
        dialog.dismiss()
        dispose()
    }

    val isDragHandoffActive: Boolean get() = dragWindowAttributes != null

    fun observeTextDrag(isCurrent: () -> Boolean) = observeDragHandoff(isCurrent, ::dismiss)

    /** Keep the source attached until ENDED; the target's result only reports its drag acceptance. */
    fun observeDragHandoff(
        isCurrent: () -> Boolean,
        onDropped: () -> Unit,
        onStarted: (() -> Unit)? = null,
        onFinished: ((Boolean) -> Unit)? = null,
    ) {
        check(!disposed && dragWindowAttributes == null)
        val original = WindowManager.LayoutParams().also { it.copyFrom(window.attributes) }
        dragWindowAttributes = original
        onDragFinished = onFinished
        // This class owns the decor's listener; Compose children retain their own listeners.
        window.decorView.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    window.attributes = WindowManager.LayoutParams().also {
                        it.copyFrom(original)
                        if (onStarted == null) it.alpha = 0f
                        it.flags = it.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    }
                    onStarted?.invoke()
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    DragShareLog.i("DragShare/InteractiveWindow", "content drag ended accepted=${event.result}")
                    window.decorView.setOnDragListener(null)
                    dragWindowAttributes = null
                    val finished = onDragFinished
                    onDragFinished = null
                    if (!disposed) {
                        // A successful drop hands focus to the target; restoring the source first closes its editor.
                        if (!event.result) window.attributes = original
                        finished?.invoke(event.result)
                        if (!isCurrent()) dismiss()
                        else if (event.result) onDropped()
                    }
                    true
                }
                // Observing the source must never count as accepting its own dropped content.
                DragEvent.ACTION_DROP -> false
                else -> true
            }
        }
    }

    fun cancelTextDragObservation() {
        val original = dragWindowAttributes ?: return
        window.decorView.setOnDragListener(null)
        dragWindowAttributes = null
        val finished = onDragFinished
        onDragFinished = null
        if (!disposed) {
            window.attributes = original
            finished?.invoke(false)
        }
    }

    private fun failedToShow(error: RuntimeException): Boolean {
        DragShareLog.w("DragShare/InteractiveWindow", "Unable to show interactive window", error)
        dispose()
        onFailure(error)
        return false
    }

    private fun dispose() {
        if (disposed) return
        disposed = true
        window.decorView.setOnDragListener(null)
        dragWindowAttributes = null
        onDragFinished = null
        composeView.disposeComposition()
        viewModelStore.clear()
        onDismissed()
    }
}

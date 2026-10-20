package com.leaf.hyperdragshare.codex

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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

    fun show(): Boolean {
        check(!disposed) { "Create a new interactive window after dismissal" }
        return try {
            dialog.show()
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

    private fun failedToShow(error: RuntimeException): Boolean {
        DragShareLog.w("DragShare/InteractiveWindow", "Unable to show interactive window", error)
        dispose()
        onFailure(error)
        return false
    }

    private fun dispose() {
        if (disposed) return
        disposed = true
        composeView.disposeComposition()
        viewModelStore.clear()
        onDismissed()
    }
}

package com.leaf.hyperdragshare.codex

import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class InteractiveOverlayWindowTest {
    @Test
    fun interactiveWindowHasOwnersAndAllowsTouchFocusAndIme() {
        val overlay = window()
        assertTrue(overlay.show())
        val view = overlay.composeView
        assertNotNull(view.findViewTreeLifecycleOwner())
        assertNotNull(view.findViewTreeSavedStateRegistryOwner())
        assertNotNull(view.findViewTreeOnBackPressedDispatcherOwner())
        assertSame(overlay, view.findViewTreeViewModelStoreOwner())
        val attributes = overlay.window.attributes
        assertEquals(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG, attributes.type)
        assertEquals(0, attributes.flags and (
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM))
        assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE, attributes.softInputMode)
        overlay.dismiss()
    }

    @Test
    fun backUsesContentCallbackBeforeDismissAndDisposesOnlyOnce() {
        var dismissed = 0
        var backCount = 0
        val overlay = window { dismissed++ }
        assertTrue(overlay.show())
        val owner = requireNotNull(overlay.composeView.findViewTreeOnBackPressedDispatcherOwner())
        val lifecycle = requireNotNull(overlay.composeView.findViewTreeLifecycleOwner()).lifecycle
        val model = ClearableModel()
        overlay.viewModelStore.put("selection", model)
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { backCount++ }
        }
        owner.onBackPressedDispatcher.addCallback(owner, callback)
        owner.onBackPressedDispatcher.onBackPressed()
        assertEquals(1, backCount)
        assertEquals(0, dismissed)
        callback.isEnabled = false
        owner.onBackPressedDispatcher.onBackPressed()
        ShadowLooper.shadowMainLooper().idle()
        assertEquals(1, dismissed)
        assertTrue(model.cleared)
        assertEquals(Lifecycle.State.DESTROYED, lifecycle.currentState)
        overlay.dismiss()
        assertEquals(1, dismissed)
    }

    private fun window(onDismissed: () -> Unit = {}) = InteractiveOverlayWindow(
        RuntimeEnvironment.getApplication(),
        OverlayWindowPolicy.portal(),
        onDismissed,
        { throw AssertionError("Unexpected window failure", it) },
        {},
    )

    private class ClearableModel : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }
}

package com.leaf.hyperdragshare.codex

import android.app.KeyguardManager
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.view.View
import android.widget.Toast

/** Search sits above its source panel; dismissing it reveals the unchanged selection. */
internal object SearchOverlayWindow {
    private var active: InteractiveOverlayWindow? = null

    fun show(context: Context, owner: View, text: String, type: Int,
        openExternal: (Intent) -> Unit): Boolean {
        val app = context.applicationContext
        if (app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            Toast.makeText(app, "请解锁后打开搜索窗口", Toast.LENGTH_SHORT).show()
            return false
        }
        val host = (context as? Activity)?.takeIf { !it.isFinishing && !it.isDestroyed }
        if (host == null && !Settings.canDrawOverlays(app)) {
            Toast.makeText(app, "请先在模块设置中允许悬浮窗权限", Toast.LENGTH_LONG).show()
            return false
        }
        active?.dismiss()
        lateinit var overlay: InteractiveOverlayWindow
        val session = SearchOverlaySession(context, text, type,
            onClose = { overlay.dismiss() }, openExternal = openExternal)
        if (!session.prepareWebView(context)) {
            session.close()
            return false
        }
        val screenOff = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = overlay.dismiss()
        }
        val ownerListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) = overlay.dismiss()
        }
        overlay = InteractiveOverlayWindow(context,
            if (host != null) OverlayWindowPolicy.hosted() else OverlayWindowPolicy.application(),
            onDismissed = {
                owner.removeOnAttachStateChangeListener(ownerListener)
                app.unregisterReceiver(screenOff)
                session.close()
                if (active === overlay) active = null
            },
            onFailure = {
                DragShareLog.w("DragShare/Search", "Search overlay could not open", it)
                Toast.makeText(app, R.string.search_open_failed, Toast.LENGTH_LONG).show()
            },
        ) { session.Content() }
        app.registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        owner.addOnAttachStateChangeListener(ownerListener)
        active = overlay
        return overlay.show().also { if (it) session.resume() }
    }
}

package com.leaf.hyperdragshare.codex

import android.app.KeyguardManager
import android.content.Context
import android.provider.Settings
import android.widget.Toast

/** Entry for a user-requested Root capture. No visible OCR window exists until capture completes. */
object ScreenOcrOverlay {
    private var active: ScreenOcrSession? = null

    fun start(context: Context, selectRegion: Boolean = false, beforeCapture: () -> Unit = {},
        host: android.app.Activity? = null): Boolean {
        val app = context.applicationContext
        if (host == null && !Settings.canDrawOverlays(app)) {
            Toast.makeText(app, "请先在模块设置中允许悬浮窗权限", Toast.LENGTH_LONG).show()
            return false
        }
        if (app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            Toast.makeText(app, R.string.screen_ocr_locked, Toast.LENGTH_SHORT).show()
            return false
        }
        val previous = active
        previous?.close(finishHost = previous.hostActivity !== host)
        lateinit var session: ScreenOcrSession
        session = ScreenOcrSession(app, selectRegion, host,
            onClosed = { finishHost ->
                if (active === session) active = null
                if (finishHost) host?.finish()
            })
        active = session
        session.start(beforeCapture)
        return true
    }

    fun dismissHosted(host: android.app.Activity) {
        active?.takeIf { it.hostActivity === host }?.close()
    }
}

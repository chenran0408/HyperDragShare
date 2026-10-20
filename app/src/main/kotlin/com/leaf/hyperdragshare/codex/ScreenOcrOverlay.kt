package com.leaf.hyperdragshare.codex

import android.app.KeyguardManager
import android.content.Context
import android.os.Bundle
import android.os.ResultReceiver
import android.provider.Settings
import android.widget.Toast

/** Entry for a user-requested Root capture. No visible OCR window exists until capture completes. */
object ScreenOcrOverlay {
    private var active: ScreenOcrSession? = null

    fun start(context: Context, selectRegion: Boolean = false,
        resultReceiver: ResultReceiver? = null, sourceArea: Bundle? = null,
        beforeCapture: () -> Unit = {}): Boolean {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) {
            Toast.makeText(app, "请先在模块设置中允许悬浮窗权限", Toast.LENGTH_LONG).show()
            return false
        }
        if (app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            Toast.makeText(app, R.string.screen_ocr_locked, Toast.LENGTH_SHORT).show()
            return false
        }
        active?.close(restorePanel = false)
        lateinit var session: ScreenOcrSession
        session = ScreenOcrSession(app, selectRegion, resultReceiver, sourceArea,
            onClosed = { if (active === session) active = null })
        active = session
        session.start(beforeCapture)
        return true
    }
}

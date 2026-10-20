package com.leaf.hyperdragshare.codex

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Compatibility entry for the legacy segmentation Activity. Internal overlay search never starts it. */
class SearchOverlayActivity : ComponentActivity() {
    private lateinit var session: SearchOverlaySession
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED)
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            Toast.makeText(this, "请解锁后打开搜索窗口", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val dark = DragShareSettings.readLocal(this).isDarkTheme(this)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        session = SearchOverlaySession(this,
            intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(),
            intent.getIntExtra(EXTRA_SEARCH_TYPE, BoomActionHandler.SEARCH_WEB),
            onClose = ::finish,
            openExternal = { startActivity(it) },
            savedState = savedInstanceState)
        if (session.prepareWebView(this)) {
            setContent { session.Content() }
        } else {
            finish()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        session.open(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(),
            intent.getIntExtra(EXTRA_SEARCH_TYPE, BoomActionHandler.SEARCH_WEB))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::session.isInitialized) session.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized) session.resume()
    }

    override fun onPause() {
        if (::session.isInitialized) session.pause()
        super.onPause()
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        if (::session.isInitialized) session.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SEARCH_TYPE = "search_type"
    }
}

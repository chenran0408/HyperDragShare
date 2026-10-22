package com.leaf.hyperdragshare.codex

import android.os.Bundle
import androidx.activity.ComponentActivity

/** Transparent host for Quick Settings and back-tap OCR over the original page. */
class ScreenOcrActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)
        beginCapture()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        beginCapture()
    }

    private fun beginCapture() {
        val accepted = ScreenOcrOverlay.start(applicationContext,
            selectRegion = intent.getBooleanExtra(EXTRA_SELECT_REGION, false), host = this)
        if (!accepted) {
            finish()
        }
    }

    override fun onDestroy() {
        ScreenOcrOverlay.dismissHosted(this)
        BigBangOverlayLauncher.dismissHosted(this)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SELECT_REGION = "select_region"
    }
}

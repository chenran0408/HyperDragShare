package com.leaf.hyperdragshare.codex

import android.os.Bundle
import androidx.activity.ComponentActivity

/** Transparent bridge for Quick Settings and back-tap entry; all OCR UI lives in overlay windows. */
class ScreenOcrActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)
        val accepted = ScreenOcrOverlay.start(applicationContext,
            selectRegion = intent.getBooleanExtra(EXTRA_SELECT_REGION, false),
            beforeCapture = { finish() })
        if (!accepted) {
            finish()
        }
    }

    companion object {
        const val EXTRA_SELECT_REGION = "select_region"
    }
}

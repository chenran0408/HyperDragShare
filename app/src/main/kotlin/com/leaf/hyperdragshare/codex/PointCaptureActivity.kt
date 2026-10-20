package com.leaf.hyperdragshare.codex

import android.os.Bundle
import androidx.activity.ComponentActivity

/** Transparent Quick Settings bridge; selecting a point never opens a module page. */
class PointCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)
        PointCaptureOverlay.start(applicationContext)
        finish()
    }
}

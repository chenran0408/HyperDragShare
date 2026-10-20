package com.leaf.hyperdragshare.codex

import android.os.Bundle
import androidx.activity.ComponentActivity

/** Brief transparent entry so Quick Settings can collapse before the side window is used. */
class StashWindowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StashOverlayPanel.show(applicationContext)
        finish()
    }
}

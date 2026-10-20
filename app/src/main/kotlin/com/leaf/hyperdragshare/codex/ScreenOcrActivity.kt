package com.leaf.hyperdragshare.codex

import android.app.Activity
import android.os.Bundle
import android.os.ResultReceiver
import androidx.activity.ComponentActivity

/** Transparent bridge for Quick Settings and back-tap entry; all OCR UI lives in overlay windows. */
class ScreenOcrActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)
        val receiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER, ResultReceiver::class.java)
        val accepted = ScreenOcrOverlay.start(applicationContext,
            selectRegion = intent.getBooleanExtra(EXTRA_SELECT_REGION, false),
            resultReceiver = receiver,
            sourceArea = intent.getBundleExtra("source_area"),
            beforeCapture = { finish() })
        if (!accepted) {
            receiver?.send(Activity.RESULT_CANCELED, Bundle.EMPTY)
            finish()
        }
    }

    companion object {
        const val EXTRA_SELECT_REGION = "select_region"
        const val EXTRA_RESULT_RECEIVER = "result_receiver"
    }
}

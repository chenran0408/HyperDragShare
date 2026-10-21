package com.leaf.hyperdragshare.codex

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity

/** Transparent text receiver; all interactive work belongs to the independent overlay. */
class TextSegmentationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)
        receiveText()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveText()
    }

    private fun receiveText() {
        val text = resolveText(intent)
        if (text == null || !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, if (text == null) "没有收到可分词的文字"
                else "请先在模块设置中允许悬浮窗权限", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        openOverlay(text)
    }

    private fun openOverlay(text: String) {
        BigBangOverlayLauncher.showText(applicationContext, text,
            intent.getIntExtra(EXTRA_TOUCH_X, -1), intent.getIntExtra(EXTRA_TOUCH_Y, -1))
        finish()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        @JvmStatic
        fun resolveText(input: Intent): String? {
            if (input.type != "text/plain") return null
            val value = when (input.action) {
                Intent.ACTION_PROCESS_TEXT -> input.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                Intent.ACTION_SEND -> input.getCharSequenceExtra(Intent.EXTRA_TEXT)
                else -> null
            }
            return value?.toString()?.takeIf { it.isNotBlank() }
        }

        @JvmField
        val DBG = true

        private const val EXTRA_TOUCH_X = "dragshare_boom_touch_x"
        private const val EXTRA_TOUCH_Y = "dragshare_boom_touch_y"

        @JvmStatic
        fun createIntent(text: String): Intent = createIntent(text, -1, -1)

        @JvmStatic
        fun createIntent(text: String, touchX: Int, touchY: Int): Intent {
            return Intent(Intent.ACTION_SEND).apply {
                component = ComponentName(
                    BuildConfig.APPLICATION_ID,
                    TextSegmentationActivity::class.java.name,
                )
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(EXTRA_TOUCH_X, touchX)
                putExtra(EXTRA_TOUCH_Y, touchY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
        }
    }
}

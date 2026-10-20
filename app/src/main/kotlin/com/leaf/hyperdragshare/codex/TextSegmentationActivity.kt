package com.leaf.hyperdragshare.codex

import android.content.ComponentName
import android.content.ClipData
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Activity entry for system shares; the panel itself is shared with overlay hosts. */
class TextSegmentationActivity : ComponentActivity() {
    private lateinit var panel: BigBangPanelSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        panel = BigBangPanelSession(this, object : BigBangPanelSession.Actions {
            override fun dismiss() = finish()
            override fun search(text: String, type: Int) {
                startActivity(Intent(this@TextSegmentationActivity, SearchOverlayActivity::class.java)
                    .putExtra(Intent.EXTRA_TEXT, text)
                    .putExtra(SearchOverlayActivity.EXTRA_SEARCH_TYPE, type))
            }
            override fun share(text: String) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(send, null).apply {
                    flags = Intent.FLAG_ACTIVITY_NO_HISTORY
                })
            }
            override fun saveToStash(text: String, selectedOnly: Boolean) =
                this@TextSegmentationActivity.saveToStash(text, selectedOnly)
            override fun reopenOcr(uri: Uri) {
                startActivity(ImageOcrActivity.createRecheckIntent(this@TextSegmentationActivity, uri))
            }
        })
        setContent { panel.Content() }
        openFromIntent(savedInstanceState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openFromIntent(null)
    }

    private fun openFromIntent(savedState: Bundle?) {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (text.isNullOrBlank()) {
            finish()
            return
        }
        panel.open(text, intent.getIntExtra(EXTRA_TOUCH_X, -1),
            intent.getIntExtra(EXTRA_TOUCH_Y, -1),
            intent.getParcelableExtra(EXTRA_SOURCE_IMAGE_URI, Uri::class.java), savedState)
    }

    override fun finish() {
        if (::panel.isInitialized) panel.close()
        super.finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        if (::panel.isInitialized) panel.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        panel.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun saveToStash(text: String, savedSelection: Boolean) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    StashStore(this@TextSegmentationActivity).use { store ->
                        store.addText(text)
                    }
                }
                Toast.makeText(
                    this@TextSegmentationActivity,
                    if (savedSelection) "所选文字已存入中转站" else "全部文字已存入中转站",
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (error: IOException) {
                DragShareLog.w(TAG, "unable to save text to stash", error)
                Toast.makeText(
                    this@TextSegmentationActivity,
                    "存入中转站失败：${error.message ?: "本地存储不可用"}",
                    Toast.LENGTH_LONG,
                ).show()
            } catch (error: SecurityException) {
                DragShareLog.w(TAG, "permission denied while saving text to stash", error)
                Toast.makeText(
                    this@TextSegmentationActivity,
                    "存入中转站失败：系统拒绝访问本地存储",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    companion object {
        @JvmField
        val DBG = true

        private const val TAG = "DragShare/BigBang"
        private const val EXTRA_TOUCH_X = "dragshare_boom_touch_x"
        private const val EXTRA_TOUCH_Y = "dragshare_boom_touch_y"
        private const val EXTRA_SOURCE_IMAGE_URI = "dragshare_source_image_uri"

        @JvmStatic
        fun createIntent(text: String): Intent = createIntent(text, -1, -1)

        @JvmStatic
        fun createIntent(text: String, touchX: Int, touchY: Int): Intent {
            return createIntent(text, touchX, touchY, null)
        }

        @JvmStatic
        fun createIntent(text: String, touchX: Int, touchY: Int, sourceImage: Uri?): Intent {
            return Intent(Intent.ACTION_SEND).apply {
                component = ComponentName(
                    BuildConfig.APPLICATION_ID,
                    TextSegmentationActivity::class.java.name,
                )
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(EXTRA_TOUCH_X, touchX)
                putExtra(EXTRA_TOUCH_Y, touchY)
                if (sourceImage != null) {
                    putExtra(EXTRA_SOURCE_IMAGE_URI, sourceImage)
                    clipData = ClipData.newRawUri("source image", sourceImage)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
        }
    }
}

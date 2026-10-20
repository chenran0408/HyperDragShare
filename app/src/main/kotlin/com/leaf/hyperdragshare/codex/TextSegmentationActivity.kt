package com.leaf.hyperdragshare.codex

import android.content.ComponentName
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.io.IOException

/** Transparent text receiver; all interactive work belongs to the independent overlay. */
class TextSegmentationActivity : ComponentActivity() {
    private val requests = LatestRequest()
    private var worker: Thread? = null

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
        worker?.interrupt()
        val request = requests.begin()
        val text = resolveText(intent)
        if (text == null || !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, if (text == null) "没有收到可分词的文字"
                else "请先在模块设置中允许悬浮窗权限", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val source = intent.getParcelableExtra(EXTRA_SOURCE_IMAGE_URI, Uri::class.java)
        if (source == null) {
            openOverlay(text, null)
            return
        }
        if (source.scheme != "content") {
            Toast.makeText(this, "来源图片需要可读取的 content URI", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        worker = Thread({
            try {
                val owned = IncomingImageCache.copy(this, source)
                runOnUiThread {
                    if (requests.accepts(request) && !isFinishing && !isDestroyed) {
                        openOverlay(text, Uri.fromFile(owned))
                    } else if (!owned.delete()) DragShareLog.w(TAG, "unable to remove cancelled text source image")
                }
            } catch (error: IOException) {
                sourceFailed(request, error)
            } catch (error: SecurityException) {
                sourceFailed(request, error)
            }
        }, "receive-text-image").also { it.start() }
    }

    private fun openOverlay(text: String, source: Uri?) {
        BigBangOverlayLauncher.showText(applicationContext, text,
            intent.getIntExtra(EXTRA_TOUCH_X, -1), intent.getIntExtra(EXTRA_TOUCH_Y, -1), source)
        finish()
    }

    private fun sourceFailed(request: Long, error: Exception) {
        DragShareLog.w(TAG, "unable to retain text source image", error)
        runOnUiThread {
            if (requests.accepts(request) && !isFinishing && !isDestroyed) {
                Toast.makeText(this, "保留来源图片失败：${error.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        requests.invalidate()
        worker?.interrupt()
        super.onDestroy()
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

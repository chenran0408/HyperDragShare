package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.io.IOException

/** Transparent share receiver: retain the temporary URI before leaving the sending app. */
class ImageOcrActivity : ComponentActivity() {
    private var worker: Thread? = null
    private val requests = LatestRequest()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)
        receiveImage()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        receiveImage()
    }

    private fun receiveImage() {
        worker?.interrupt()
        val request = requests.begin()
        val source = resolveImageUri(intent)
        if (source == null || !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, if (source == null) "没有收到可读取的图片"
                else "请先在模块设置中允许悬浮窗权限", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        worker = Thread({
            try {
                val owned = IncomingImageCache.copy(this, source)
                runOnUiThread {
                    if (requests.accepts(request) && !isFinishing && !isDestroyed) {
                        ImageOcrOverlay.start(applicationContext, Uri.fromFile(owned))
                        finish()
                    } else if (!owned.delete()) DragShareLog.w(TAG, "unable to remove cancelled OCR input")
                }
            } catch (error: IOException) {
                fail(request, error)
            } catch (error: SecurityException) {
                fail(request, error)
            }
        }, "receive-ocr-image").also { it.start() }
    }

    private fun fail(request: Long, error: Exception) {
        DragShareLog.w(TAG, "unable to receive shared image", error)
        runOnUiThread {
            if (requests.accepts(request) && !isFinishing && !isDestroyed) {
                Toast.makeText(this, "接收图片失败：${error.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        requests.invalidate()
        worker?.interrupt()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DragShare/OCR"

        @JvmStatic
        fun resolveImageUri(input: Intent): Uri? {
            if (input.action != Intent.ACTION_SEND || input.type?.startsWith("image/") != true) {
                return null
            }
            val uri = input.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: input.data
            return uri?.takeIf { it.scheme == "content" }
        }

        @JvmStatic
        fun createRecheckIntent(context: Context, uri: Uri): Intent =
            Intent(context, ImageOcrActivity::class.java).apply {
                action = Intent.ACTION_SEND
                setDataAndType(uri, "image/*")
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("source image", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
    }
}

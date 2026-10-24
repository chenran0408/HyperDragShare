package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
        val bigBang = intent.getBundleExtra(EXTRA_BIGBANG_ARGS)
        val nestedOperation = bigBang?.let { OperationTrace.read(it) } ?: "-"
        val outerOperation = OperationTrace.read(intent.extras).let {
            if (it == "-") OperationTrace.read(intent.clipData) else it
        }
        val operation = when {
            nestedOperation != "-" -> nestedOperation
            outerOperation != "-" -> outerOperation
            else -> OperationTrace.fresh("image_receive")
        }
        if (bigBang != null) OperationTrace.put(bigBang, operation)
        OperationTrace.event(operation, "image_activity", "received")
        if (bigBang != null) {
            if (!BigBangOverlayLauncher.requestHosted(this, bigBang)) finish()
            return
        }
        val hosted = intent.getParcelableExtra(EXTRA_HOSTED_URI, Uri::class.java)
        if (hosted != null) {
            if (!ImageOcrOverlay.startHosted(this, hosted, intent.getBooleanExtra(EXTRA_QR, false), operation)) finish()
            return
        }
        val source = resolveImageUri(intent)
        if (source == null) {
            OperationTrace.event(operation, "image_receive", "rejected_missing_uri")
            Toast.makeText(this, "没有收到可读取的图片", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        worker = Thread({
            try {
                val owned = IncomingImageCache.copy(this, source)
                OperationTrace.event(operation, "image_receive", "copy_completed", source)
                runOnUiThread {
                    if (requests.accepts(request) && !isFinishing && !isDestroyed) {
                        if (!ImageOcrOverlay.startHosted(this, Uri.fromFile(owned), false, operation)) finish()
                    } else {
                        OperationTrace.event(operation, "image_receive", "late_ignored", source)
                        if (!owned.delete()) DragShareLog.w(TAG, "unable to remove cancelled OCR input")
                    }
                }
            } catch (error: IOException) {
                OperationTrace.event(operation, "image_receive", "failed_IOException", source)
                fail(request, error)
            } catch (error: SecurityException) {
                OperationTrace.event(operation, "image_receive", "failed_SecurityException", source)
                fail(request, error)
            }
        }, "receive-ocr-image").also { it.start() }
    }

    private fun fail(request: Long, error: Exception) {
        DragShareLog.w(TAG, "unable to receive shared image type=${error.javaClass.simpleName}")
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
        ImageOcrOverlay.dismissHosted(this)
        BigBangOverlayLauncher.dismissHosted(this)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DragShare/OCR"
        const val EXTRA_HOSTED_URI = "hosted_uri"
        const val EXTRA_QR = "qr"
        const val EXTRA_BIGBANG_ARGS = "bigbang_args"

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

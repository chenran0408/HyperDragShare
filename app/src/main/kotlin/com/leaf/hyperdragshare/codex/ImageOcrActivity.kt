package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.io.File
import java.io.IOException
import java.util.UUID

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
            var copy: File? = null
            try {
                val directory = File(cacheDir, "ocr-input")
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建图片暂存目录")
                directory.listFiles()?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > 86_400_000L }
                    ?.forEach { if (!it.delete()) DragShareLog.w(TAG, "unable to remove expired OCR input") }
                copy = File(directory, UUID.randomUUID().toString() + ".image")
                contentResolver.openInputStream(source).use { input ->
                    if (input == null) throw IOException("无法读取分享图片")
                    copy.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("接收已取消")
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > 32L * 1024 * 1024) throw IOException("图片超过32 MiB，请缩小后重试")
                            output.write(buffer, 0, count)
                        }
                    }
                }
                val owned = copy
                runOnUiThread {
                    if (requests.accepts(request) && !isFinishing && !isDestroyed) {
                        ImageOcrOverlay.start(applicationContext, Uri.fromFile(owned))
                        finish()
                    } else if (!owned.delete()) DragShareLog.w(TAG, "unable to remove cancelled OCR input")
                }
            } catch (error: IOException) {
                copy?.delete()
                fail(request, error)
            } catch (error: SecurityException) {
                copy?.delete()
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

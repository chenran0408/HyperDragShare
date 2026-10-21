package com.leaf.hyperdragshare.codex

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/** Transparent share receiver; all content is used through the floating tray. */
class StashActivity : ComponentActivity() {
    private var pendingImport: Intent? = null
    private var shareImported = false
    private var receiveOnly = false
    private var message: String? = null
    private val importLock = Mutex()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)
        receiveOnly = isSupportedShare(intent)
        shareImported = savedInstanceState?.getBoolean("share_imported") == true
        pendingImport = intent.takeIf { !shareImported && receiveOnly }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shareImported = false
        receiveOnly = isSupportedShare(intent)
        pendingImport = intent.takeIf(::isSupportedShare)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            importPendingShare()
            if (shareImported) StashOverlayPanel.contentAdded(this@StashActivity)
            else if (!receiveOnly) StashOverlayPanel.show(this@StashActivity)
            else Toast.makeText(this@StashActivity,
                message ?: "保存失败，请重新分享后重试", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("share_imported", shareImported)
        super.onSaveInstanceState(outState)
    }

    private suspend fun importPendingShare() = importLock.withLock {
        val incoming = pendingImport ?: return@withLock
        message = null
        try {
            val savedCount = withContext(Dispatchers.IO) {
                StashStore(this@StashActivity).use { store ->
                    when {
                        incoming.type == "text/plain" -> {
                            val text = incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                                ?: throw IOException("分享内容没有文字")
                            if (text.isBlank()) throw IOException("分享的文字为空")
                            store.addText(text)
                            1
                        }
                        incoming.type?.startsWith("image/") == true -> {
                            store.addImages(contentResolver, incoming.imageUris())
                        }
                        else -> throw IOException("不支持这种分享内容")
                    }
                }
            }
            if (pendingImport === incoming) {
                pendingImport = null
                shareImported = true
            }
            Toast.makeText(this, "已保存 $savedCount 项到中转站", Toast.LENGTH_SHORT).show()
        } catch (error: IOException) {
            DragShareLog.w("DragShare/Stash", "unable to import shared content", error)
            message = "保存失败：${error.message ?: "无法读取分享内容"}"
        } catch (error: SecurityException) {
            DragShareLog.w("DragShare/Stash", "permission denied while importing shared content", error)
            message = "没有读取分享内容的权限，请重新分享后重试。"
        }
    }

    private fun isSupportedShare(intent: Intent): Boolean =
        (intent.action == Intent.ACTION_SEND &&
            (intent.type == "text/plain" || intent.type?.startsWith("image/") == true)) ||
            (intent.action == Intent.ACTION_SEND_MULTIPLE && intent.type?.startsWith("image/") == true)

    private fun Intent.imageUris(): List<Uri> {
        val streams = if (action == Intent.ACTION_SEND_MULTIPLE) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            listOfNotNull(getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        }
        if (streams.isNotEmpty()) return streams.distinct()
        val clip = clipData ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }.distinct()
    }
}

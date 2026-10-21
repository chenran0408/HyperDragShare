package com.leaf.hyperdragshare.codex

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.util.UUID

/** Copy while the receiving Activity still owns the sender's temporary URI permission. */
internal object IncomingImageCache {
    fun copy(context: Context, source: Uri): File {
        val directory = File(context.cacheDir, "ocr-input")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建图片暂存目录")
        SharedImageCleanupJob.cleanupBeforeImport(context)
        val copy = File(directory, UUID.randomUUID().toString() + ".image")
        try {
            context.contentResolver.openInputStream(source).use { input ->
                if (input == null) throw IOException("无法读取分享图片")
                copy.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("接收已取消")
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 32L * 1024 * 1024) throw IOException("图片超过32 MiB，请缩小后重试")
                        output.write(buffer, 0, count)
                    }
                }
            }
            return copy
        } catch (error: IOException) {
            removePartial(copy)
            throw error
        } catch (error: SecurityException) {
            removePartial(copy)
            throw error
        }
    }

    private fun removePartial(copy: File) {
        if (copy.exists() && !copy.delete()) DragShareLog.w(TAG, "unable to remove partial image input")
    }

    private const val TAG = "DragShare/SharedInput"
}

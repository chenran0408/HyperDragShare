package com.leaf.hyperdragshare.codex

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.IOException

@Composable
internal fun StashImage(
    entry: StashStore.Entry,
    edgeLimit: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    var failed by remember(entry.id, edgeLimit) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(initialValue = null, entry.id, edgeLimit) {
        failed = false
        try {
            value = withContext(Dispatchers.IO) {
                val uri = StashImageProvider.uriForEntry(entry.id)
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > edgeLimit) {
                        val scale = edgeLimit / longest.toFloat()
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1),
                        )
                    }
                }
            }
        } catch (error: IOException) {
            DragShareLog.w("DragShare/Stash", "unable to decode stash image", error)
            failed = true
        } catch (error: SecurityException) {
            DragShareLog.w("DragShare/Stash", "permission denied while decoding stash image", error)
            failed = true
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "中转站图片",
            contentScale = contentScale,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (failed) "图片无法预览" else "正在加载图片…",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** Recognizes a granted image URI in the module process, never in the hooked host. */
class ImageOcrActivity : ComponentActivity() {
    private val requests = LatestRequest()
    private val decoder: ExecutorService = Executors.newSingleThreadExecutor()
    private val recognitionSlot = Semaphore(1)
    private var statusMessage by mutableIntStateOf(R.string.ocr_recognizing)
    private var isRecognizing by mutableStateOf(true)
    private var pendingText: String? = null
    private var sourceImageUri: Uri? = null
    private var resumed = false
    @Volatile private var aicrRequest: AicrOcrClient.Request? = null
    private var failureDetail by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val dark = DragShareSettings.readLocal(this).colorMode == DragShareSettings.COLOR_DARK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        sourceImageUri = resolveImageUri(intent)
        setContent {
            MiuixTheme(
                controller = ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light),
            ) {
                OcrStatusScreen(
                    statusMessage = statusMessage,
                    failureDetail = failureDetail,
                    isRecognizing = isRecognizing,
                    onRetry = ::recognizeIntent,
                    onCancel = ::finish,
                )
            }
        }
        recognizeIntent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sourceImageUri = resolveImageUri(intent)
        recognizeIntent()
    }

    private fun recognizeIntent() {
        aicrRequest?.cancel()
        failureDetail = null
        val request = requests.begin()
        pendingText = null
        isRecognizing = true
        statusMessage = R.string.ocr_recognizing
        val uri = sourceImageUri
        if (uri == null) {
            showFailure(request, R.string.ocr_invalid_image)
            return
        }
        decoder.execute { decodeAndRecognize(uri, request) }
    }

    private fun decodeAndRecognize(uri: Uri, request: Long) {
        try {
            recognitionSlot.acquire()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return
        }
        if (!requests.accepts(request)) {
            recognitionSlot.release()
            return
        }

        val bitmap: Bitmap
        try {
            bitmap = ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(contentResolver, uri),
            ) { imageDecoder, info, _ ->
                imageDecoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_IMAGE_EDGE) {
                    val scale = MAX_IMAGE_EDGE / longest.toFloat()
                    imageDecoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        } catch (error: IOException) {
            recognitionSlot.release()
            reportFailure(request, R.string.ocr_read_failed, error)
            return
        } catch (error: RuntimeException) {
            recognitionSlot.release()
            reportFailure(request, R.string.ocr_read_failed, error)
            return
        }
        if (!requests.accepts(request)) {
            bitmap.recycle()
            recognitionSlot.release()
            return
        }

        if (IntegrationSettings.isAicrOcrEnabled(this)) {
            val operation = AicrOcrClient.recognize(this, bitmap) { text, error ->
                try {
                    if (canAccept(request)) {
                        if (error == null) acceptText(request, text.orEmpty())
                        else if (error !is java.util.concurrent.CancellationException) {
                            failureDetail = error.message
                            showFailure(request, R.string.ocr_failed)
                        }
                    }
                } finally {
                    bitmap.recycle()
                    recognitionSlot.release()
                }
            }
            aicrRequest = operation
            if (!requests.accepts(request)) operation.cancel()
            return
        }

        val recognizer: TextRecognizer
        try {
            recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        } catch (error: RuntimeException) {
            bitmap.recycle()
            recognitionSlot.release()
            reportFailure(request, R.string.ocr_failed, error)
            return
        }
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnCompleteListener(Runnable::run) { task ->
                    try {
                        if (task.isSuccessful) {
                            val text = task.result?.text.orEmpty()
                            runOnUiThread { acceptText(request, text) }
                        } else {
                            reportFailure(request, R.string.ocr_failed, task.exception)
                        }
                    } finally {
                        recognizer.close()
                        bitmap.recycle()
                        recognitionSlot.release()
                    }
                }
        } catch (error: RuntimeException) {
            recognizer.close()
            bitmap.recycle()
            recognitionSlot.release()
            reportFailure(request, R.string.ocr_failed, error)
        }
    }

    private fun acceptText(request: Long, text: String) {
        if (!canAccept(request)) return
        if (text.isBlank()) {
            showFailure(request, R.string.ocr_no_text)
            return
        }
        pendingText = text
        openTextWhenResumed()
    }

    private fun openTextWhenResumed() {
        if (resumed && pendingText != null && !isFinishing) {
            val text = pendingText ?: return
            pendingText = null
            startActivity(
                TextSegmentationActivity.createIntent(
                    text = text,
                    touchX = -1,
                    touchY = -1,
                    sourceImage = sourceImageUri,
                ),
            )
            finish()
        }
    }

    private fun reportFailure(request: Long, message: Int, error: Exception?) {
        // Log error type, never input URIs, recognized text, or image data.
        DragShareLog.w(
            TAG,
            "image recognition failed: ${error?.javaClass?.simpleName ?: "cancelled"}",
        )
        runOnUiThread { showFailure(request, message) }
    }

    private fun showFailure(request: Long, message: Int) {
        if (canAccept(request)) {
            isRecognizing = false
            statusMessage = message
        }
    }

    private fun canAccept(request: Long): Boolean =
        requests.accepts(request) && !isFinishing && !isDestroyed

    override fun onResume() {
        super.onResume()
        resumed = true
        openTextWhenResumed()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onDestroy() {
        requests.invalidate()
        aicrRequest?.cancel()
        decoder.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DragShare/OCR"
        private const val MAX_IMAGE_EDGE = 4096

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

@Composable
private fun OcrStatusScreen(
    statusMessage: Int,
    failureDetail: String?,
    isRecognizing: Boolean,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().widthIn(max = 360.dp),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isRecognizing) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(20.dp))
                }
                Text(
                    text = stringResource(R.string.ocr_action),
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = failureDetail ?: stringResource(statusMessage),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(20.dp))
                if (!isRecognizing) {
                    TextButton(
                        text = stringResource(R.string.ocr_retry),
                        onClick = onRetry,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
                TextButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = onCancel,
                )
            }
        }
    }
}

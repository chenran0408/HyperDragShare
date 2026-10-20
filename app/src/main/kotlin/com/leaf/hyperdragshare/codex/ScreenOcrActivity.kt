package com.leaf.hyperdragshare.codex

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Screen text is captured only after a direct Quick Settings or back-tap action. */
class ScreenOcrActivity : ComponentActivity() {
    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var resultText by mutableStateOf<List<String>>(emptyList())
    private var statusMessage by mutableIntStateOf(R.string.screen_ocr_capturing)
    private var isRecognizing by mutableStateOf(true)
    private var darkTheme = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        darkTheme = DragShareSettings.readLocal(this).colorMode == DragShareSettings.COLOR_DARK
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.setDimAmount(0f)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
        showTransparentCaptureWindow()
        scheduleCapture()
    }

    private fun showTransparentCaptureWindow() {
        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Transparent),
            )
        }
    }

    private fun scheduleCapture() {
        window.decorView.postDelayed({ captureAndRecognize() }, CAPTURE_FRAME_DELAY_MILLIS)
    }

    private fun captureAndRecognize() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) {
            showFailure(R.string.screen_ocr_locked)
            return
        }
        isRecognizing = true
        captureExecutor.execute {
            val screenshot = try {
                RootScreenshotter().capture()
            } catch (error: IOException) {
                reportFailure(R.string.screen_ocr_capture_failed, error)
                return@execute
            } catch (error: RuntimeException) {
                reportFailure(R.string.screen_ocr_capture_failed, error)
                return@execute
            }
            recognizeScreenshot(screenshot)
        }
    }

    private fun recognizeScreenshot(bitmap: Bitmap) {
        val recognizer = try {
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        } catch (error: RuntimeException) {
            bitmap.recycle()
            reportFailure(R.string.screen_ocr_failed, error)
            return
        }
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnCompleteListener(Runnable::run) { task ->
                    try {
                        if (task.isSuccessful) {
                            val lines = task.result?.textBlocks.orEmpty()
                                .flatMap { block -> block.lines }
                                .map { line -> line.text.trim() }
                                .filter { line -> line.isNotEmpty() }
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    if (lines.isEmpty()) {
                                        showFailure(R.string.screen_ocr_no_text)
                                    } else {
                                        resultText = lines
                                        isRecognizing = false
                                        renderResults()
                                    }
                                }
                            }
                        } else {
                            reportFailure(
                                R.string.screen_ocr_failed,
                                task.exception ?: IllegalStateException("ML Kit recognition failed"),
                            )
                        }
                    } finally {
                        recognizer.close()
                        bitmap.recycle()
                    }
                }
        } catch (error: RuntimeException) {
            recognizer.close()
            bitmap.recycle()
            reportFailure(R.string.screen_ocr_failed, error)
        }
    }

    private fun reportFailure(message: Int, error: Throwable) {
        DragShareLog.w(
            TAG,
            "screen recognition failed: ${error.javaClass.simpleName}",
        )
        runOnUiThread { showFailure(message) }
    }

    private fun showFailure(message: Int) {
        if (isFinishing || isDestroyed) return
        statusMessage = message
        isRecognizing = false
        renderResults()
    }

    private fun renderResults() {
        setContent {
            MiuixTheme(
                controller = ThemeController(
                    if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
                ),
            ) {
                ScreenOcrPage(
                    lines = resultText,
                    statusMessage = statusMessage,
                    isRecognizing = isRecognizing,
                    onClose = ::finish,
                    onRetry = {
                        resultText = emptyList()
                        statusMessage = R.string.screen_ocr_capturing
                        isRecognizing = true
                        showTransparentCaptureWindow()
                        scheduleCapture()
                    },
                    onSegment = ::openSegmentation,
                    onCopy = ::copyText,
                    onShare = ::shareText,
                )
            }
        }
    }

    private fun openSegmentation(text: String) {
        if (text.isBlank()) return
        startActivity(TextSegmentationActivity.createIntent(text, -1, -1))
        finish()
    }

    private fun copyText(text: String) {
        if (text.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("screen text", text))
    }

    private fun shareText(text: String) {
        if (text.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, null))
    }

    override fun onDestroy() {
        captureExecutor.shutdownNow()
        super.onDestroy()
    }

    @Composable
    private fun ScreenOcrPage(
        lines: List<String>,
        statusMessage: Int,
        isRecognizing: Boolean,
        onClose: () -> Unit,
        onRetry: () -> Unit,
        onSegment: (String) -> Unit,
        onCopy: (String) -> Unit,
        onShare: (String) -> Unit,
    ) {
        val allText = lines.joinToString("\n")
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "screen-ocr-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.screen_ocr_title),
                            color = MiuixTheme.colorScheme.onSurface,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.screen_ocr_privacy),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 13.sp,
                        )
                    }
                    TextButton(text = stringResource(R.string.screen_ocr_close), onClick = onClose)
                }
            }
            if (isRecognizing) {
                item(key = "screen-ocr-progress") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(statusMessage),
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            } else if (lines.isEmpty()) {
                item(key = "screen-ocr-empty") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = stringResource(statusMessage),
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                text = stringResource(R.string.screen_ocr_retry),
                                onClick = onRetry,
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
                    }
                }
            } else {
                item(key = "screen-ocr-actions") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.screen_ocr_result_count, lines.size),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    text = stringResource(R.string.screen_ocr_segment_all),
                                    onClick = { onSegment(allText) },
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                )
                                TextButton(
                                    text = stringResource(R.string.screen_ocr_copy_all),
                                    onClick = { onCopy(allText) },
                                )
                                TextButton(
                                    text = stringResource(R.string.screen_ocr_share_all),
                                    onClick = { onShare(allText) },
                                )
                            }
                        }
                    }
                }
                itemsIndexed(lines, key = { index, _ -> index }) { _, line ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = line,
                                color = MiuixTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    text = stringResource(R.string.screen_ocr_segment_line),
                                    onClick = { onSegment(line) },
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                )
                                TextButton(
                                    text = stringResource(R.string.screen_ocr_copy_line),
                                    onClick = { onCopy(line) },
                                )
                                TextButton(
                                    text = stringResource(R.string.screen_ocr_share_all),
                                    onClick = { onShare(line) },
                                )
                            }
                        }
                    }
                }
                item(key = "screen-ocr-footer") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        TextButton(
                            text = stringResource(R.string.screen_ocr_retry),
                            onClick = onRetry,
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "DragShare/ScreenOCR"
        private const val CAPTURE_FRAME_DELAY_MILLIS = 120L
    }
}

package com.leaf.hyperdragshare.codex

import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.ResultReceiver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
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
    @Volatile private var screenshotForRegion: Bitmap? = null
    private var regionSelectionMessage by mutableIntStateOf(0)
    private var darkTheme = false
    @Volatile private var aicrRequest: AicrOcrClient.Request? = null
    @Volatile private var aicrBitmap: Bitmap? = null
    private var failureDetail by mutableStateOf<String?>(null)
    private val pendingCapture = Runnable { captureAndRecognize() }
    private var resultReceiver: ResultReceiver? = null
    private var resultSent = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultReceiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER, ResultReceiver::class.java)
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
        failureDetail = null
        window.decorView.postDelayed(pendingCapture, CAPTURE_FRAME_DELAY_MILLIS)
    }

    private fun captureAndRecognize() {
        if (isFinishing || isDestroyed) return
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
            runOnUiThread {
                if (isFinishing || isDestroyed) screenshot.recycle()
                else if (intent.getBooleanExtra(EXTRA_SELECT_REGION, false)) {
                    screenshotForRegion = screenshot
                    isRecognizing = false
                    showRegionSelector()
                } else recognizeScreenshot(screenshot, retainScreenshotForRegion = true)
            }
        }
    }

    private fun recognizeScreenshot(bitmap: Bitmap, retainScreenshotForRegion: Boolean) {
        failureDetail = null
        if (IntegrationSettings.isAicrOcrEnabled(this)) {
            aicrBitmap = bitmap
            val operation = AicrOcrClient.recognize(this, bitmap) { text, error ->
                aicrBitmap = null
                if (isFinishing || isDestroyed || error is java.util.concurrent.CancellationException) {
                    if (bitmap !== screenshotForRegion) bitmap.recycle()
                } else if (error != null) {
                    if (bitmap !== screenshotForRegion) bitmap.recycle()
                    failureDetail = error.message
                    showFailure(R.string.screen_ocr_failed)
                } else {
                    if (retainScreenshotForRegion) {
                        if (screenshotForRegion !== bitmap) screenshotForRegion?.recycle()
                        screenshotForRegion = bitmap
                    } else if (bitmap !== screenshotForRegion) bitmap.recycle()
                    val lines = text.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
                    if (lines.isEmpty()) showFailure(R.string.screen_ocr_no_text)
                    else {
                        resultText = lines
                        isRecognizing = false
                        presentRecognitionResult()
                    }
                }
            }
            aicrRequest = operation
            if (isFinishing || isDestroyed) operation.cancel()
            return
        }
        val recognizer = try {
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        } catch (error: RuntimeException) {
            if (bitmap !== screenshotForRegion) bitmap.recycle()
            reportFailure(R.string.screen_ocr_failed, error)
            return
        }
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnCompleteListener(Runnable::run) { task ->
                    var retainBitmapForRegion = false
                    try {
                        if (task.isSuccessful) {
                            val lines = task.result?.textBlocks.orEmpty()
                                .flatMap { block -> block.lines }
                                .map { line -> line.text.trim() }
                                .filter { line -> line.isNotEmpty() }
                            retainBitmapForRegion = retainScreenshotForRegion
                            runOnUiThread {
                                if (!isFinishing && !isDestroyed) {
                                    if (retainBitmapForRegion) {
                                        screenshotForRegion?.recycle()
                                        screenshotForRegion = bitmap
                                    }
                                    if (lines.isEmpty()) {
                                        showFailure(R.string.screen_ocr_no_text)
                                    } else {
                                        resultText = lines
                                        isRecognizing = false
                                        presentRecognitionResult()
                                    }
                                } else {
                                    if (bitmap !== screenshotForRegion) bitmap.recycle()
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
                        if (!retainBitmapForRegion && bitmap !== screenshotForRegion) {
                            bitmap.recycle()
                        }
                    }
                }
        } catch (error: RuntimeException) {
            recognizer.close()
            if (bitmap !== screenshotForRegion) bitmap.recycle()
            reportFailure(R.string.screen_ocr_failed, error)
        }
    }

    private fun reportFailure(message: Int, error: Throwable) {
        DragShareLog.w(
            TAG,
            "screen recognition failed",
            error,
        )
        runOnUiThread {
            failureDetail = error.message
            showFailure(message)
        }
    }

    private fun presentRecognitionResult() {
        if (resultReceiver == null && intent.getBooleanExtra(EXTRA_SELECT_REGION, false) &&
            BigBangOverlayLauncher.showText(this, resultText.joinToString("\n"), -1, -1, null)) {
            finish()
        } else renderResults()
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
                    canSelectRegion = screenshotForRegion != null,
                    onClose = ::finish,
                    onRetry = {
                        resultText = emptyList()
                        statusMessage = R.string.screen_ocr_capturing
                        isRecognizing = true
                        screenshotForRegion?.recycle()
                        screenshotForRegion = null
                        showTransparentCaptureWindow()
                        scheduleCapture()
                    },
                    onSelectRegion = ::showRegionSelector,
                    onSegment = ::openSegmentation,
                    onCopy = ::copyText,
                    onShare = ::shareText,
                )
            }
        }
    }

    private fun showRegionSelector() {
        val screenshot = screenshotForRegion
        if (screenshot == null) {
            showFailure(R.string.screen_ocr_region_unavailable)
            return
        }
        regionSelectionMessage = 0
        renderRegionSelector(screenshot)
    }

    private fun renderRegionSelector(screenshot: Bitmap) {
        setContent {
            MiuixTheme(
                controller = ThemeController(
                    if (darkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light,
                ),
            ) {
                RegionSelectionPage(
                    screenshot = screenshot,
                    message = regionSelectionMessage,
                    onCancel = {
                        if (intent.getBooleanExtra(EXTRA_SELECT_REGION, false) && resultText.isEmpty()) finish()
                        else renderResults()
                    },
                    onRecognize = ::recognizeRegion,
                )
            }
        }
    }

    private fun recognizeRegion(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        viewWidth: Int,
        viewHeight: Int,
    ) {
        val screenshot = screenshotForRegion
        if (screenshot == null) {
            showFailure(R.string.screen_ocr_region_unavailable)
            return
        }
        val cropRect = OcrCropMapper.map(
            startX,
            startY,
            endX,
            endY,
            viewWidth,
            viewHeight,
            screenshot.width,
            screenshot.height,
        )
        if (cropRect == null) {
            regionSelectionMessage = R.string.screen_ocr_region_invalid
            renderRegionSelector(screenshot)
            return
        }
        val crop = Bitmap.createBitmap(
            screenshot,
            cropRect.left,
            cropRect.top,
            cropRect.width(),
            cropRect.height(),
        )
        regionSelectionMessage = 0
        resultText = emptyList()
        statusMessage = R.string.screen_ocr_region_recognizing
        isRecognizing = true
        renderResults()
        recognizeScreenshot(crop, retainScreenshotForRegion = false)
    }

    private fun openSegmentation(text: String) {
        if (text.isBlank()) return
        resultReceiver?.let { receiver ->
            if (!resultSent) {
                resultSent = true
                receiver.send(RESULT_OK, Bundle().apply { putString("text", text) })
                finish()
            }
            return
        }
        if (BigBangOverlayLauncher.showText(this, text, -1, -1, null)) finish()
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
        if (isFinishing && !resultSent) {
            resultSent = true
            resultReceiver?.send(RESULT_CANCELED, Bundle.EMPTY)
        }
        window.decorView.removeCallbacks(pendingCapture)
        captureExecutor.shutdownNow()
        aicrRequest?.cancel()
        if (screenshotForRegion !== aicrBitmap) screenshotForRegion?.recycle()
        screenshotForRegion = null
        super.onDestroy()
    }

    @Composable
    private fun ScreenOcrPage(
        lines: List<String>,
        statusMessage: Int,
        isRecognizing: Boolean,
        canSelectRegion: Boolean,
        onClose: () -> Unit,
        onRetry: () -> Unit,
        onSelectRegion: () -> Unit,
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
                                text = failureDetail ?: stringResource(statusMessage),
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
                                text = failureDetail ?: stringResource(statusMessage),
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                text = stringResource(R.string.screen_ocr_retry),
                                onClick = onRetry,
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                            if (canSelectRegion) {
                                TextButton(
                                    text = stringResource(R.string.screen_ocr_select_region),
                                    onClick = onSelectRegion,
                                )
                            }
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
                                    text = if (resultReceiver != null) "追加到分词"
                                        else stringResource(R.string.screen_ocr_segment_all),
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
                            if (canSelectRegion) {
                                TextButton(
                                    text = stringResource(R.string.screen_ocr_select_region),
                                    onClick = onSelectRegion,
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
                                    text = if (resultReceiver != null) "追加此行"
                                        else stringResource(R.string.screen_ocr_segment_line),
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

    @Composable
    private fun RegionSelectionPage(
        screenshot: Bitmap,
        message: Int,
        onCancel: () -> Unit,
        onRecognize: (Float, Float, Float, Float, Int, Int) -> Unit,
    ) {
        val preview = remember(screenshot) { screenshot.asImageBitmap() }
        val accentColor = MiuixTheme.colorScheme.primary
        var start by remember(screenshot) { mutableStateOf<Offset?>(null) }
        var end by remember(screenshot) { mutableStateOf<Offset?>(null) }
        var viewSize by remember(screenshot) { mutableStateOf(IntSize.Zero) }
        val selection = if (start != null && end != null) {
            Rect(
                left = minOf(start!!.x, end!!.x),
                top = minOf(start!!.y, end!!.y),
                right = maxOf(start!!.x, end!!.x),
                bottom = maxOf(start!!.y, end!!.y),
            )
        } else {
            null
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                bitmap = preview,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewSize = it }
                    .pointerInput(screenshot) {
                        detectDragGestures(
                            onDragStart = { position ->
                                start = position
                                end = position
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                end = change.position
                            },
                        )
                    },
            ) {
                val dim = Color.Black.copy(alpha = 0.52f)
                val rect = selection
                if (rect == null || rect.width <= 0f || rect.height <= 0f) {
                    drawRect(dim)
                } else {
                    drawRect(dim, topLeft = Offset.Zero, size = Size(size.width, rect.top))
                    drawRect(
                        dim,
                        topLeft = Offset(0f, rect.top),
                        size = Size(rect.left, rect.height),
                    )
                    drawRect(
                        dim,
                        topLeft = Offset(rect.right, rect.top),
                        size = Size(size.width - rect.right, rect.height),
                    )
                    drawRect(
                        dim,
                        topLeft = Offset(0f, rect.bottom),
                        size = Size(size.width, size.height - rect.bottom),
                    )
                    drawRect(
                        Color.White.copy(alpha = 0.12f),
                        topLeft = Offset(rect.left, rect.top),
                        size = Size(rect.width, rect.height),
                    )
                    drawRect(
                        accentColor,
                        topLeft = Offset(rect.left, rect.top),
                        size = Size(rect.width, rect.height),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.screen_ocr_region_title),
                                color = MiuixTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.screen_ocr_region_hint),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 13.sp,
                            )
                        }
                        TextButton(
                            text = stringResource(R.string.screen_ocr_close),
                            onClick = onCancel,
                        )
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        if (message != 0) {
                            Text(
                                text = stringResource(message),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                text = stringResource(R.string.screen_ocr_close),
                                onClick = onCancel,
                            )
                            TextButton(
                                text = stringResource(R.string.screen_ocr_region_recognize),
                                onClick = {
                                    val from = start
                                    val to = end
                                    if (from != null && to != null) {
                                        onRecognize(
                                            from.x,
                                            from.y,
                                            to.x,
                                            to.y,
                                            viewSize.width,
                                            viewSize.height,
                                        )
                                    }
                                },
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_SELECT_REGION = "select_region"
        const val EXTRA_RESULT_RECEIVER = "result_receiver"
        private const val TAG = "DragShare/ScreenOCR"
        private const val CAPTURE_FRAME_DELAY_MILLIS = 120L
    }
}

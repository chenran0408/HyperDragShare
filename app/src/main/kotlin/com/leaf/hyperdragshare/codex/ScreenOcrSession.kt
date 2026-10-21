package com.leaf.hyperdragshare.codex

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.ResultReceiver
import android.os.Handler
import android.os.Looper
import android.app.Activity
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** One explicit capture, region/result UI and engine lifetime, independent of an Activity. */
internal class ScreenOcrSession(
    private val context: Context,
    private val selectRegion: Boolean,
    private val resultReceiver: ResultReceiver?,
    private val sourceArea: Bundle?,
    private val onClosed: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var window: InteractiveOverlayWindow? = null
    private var closed = false
    private var resultSent = false
    private var regionVisible by mutableStateOf(false)
    private var resultText by mutableStateOf<List<String>>(emptyList())
    private var selectedLines by mutableStateOf<Set<Int>>(emptySet())
    private var nearbySelection by mutableStateOf(false)
    private var statusMessage by mutableIntStateOf(R.string.screen_ocr_capturing)
    private var isRecognizing by mutableStateOf(true)
    private var screenshotForRegion: Bitmap? = null
    // Neither engine's input may be recycled before its completion callback, even after cancel.
    private var readingBitmap: Bitmap? = null
    private var regionSelectionMessage by mutableIntStateOf(0)
    private var aicrRequest: AicrOcrClient.Request? = null
    private var failureDetail by mutableStateOf<String?>(null)
    private var copying by mutableStateOf(false)
    private var copyMessage by mutableStateOf<String?>(null)
    private val pendingCapture = Runnable { captureAndRecognize() }
    private val screenOff = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = close(restorePanel = false)
    }

    fun start(beforeCapture: () -> Unit) {
        context.registerReceiver(screenOff, android.content.IntentFilter(Intent.ACTION_SCREEN_OFF),
            Context.RECEIVER_NOT_EXPORTED)
        beforeCapture()
        scheduleCapture()
    }

    private fun scheduleCapture() {
        failureDetail = null
        copyMessage = null
        main.postDelayed(pendingCapture, CAPTURE_FRAME_DELAY_MILLIS)
    }

    private fun captureAndRecognize() {
        if (closed) return
        if (context.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            Toast.makeText(context, R.string.screen_ocr_locked, Toast.LENGTH_SHORT).show()
            close(restorePanel = false)
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
            main.post {
                if (closed) {
                    screenshot.recycle()
                } else {
                    screenshotForRegion = screenshot
                    if (selectRegion) {
                        isRecognizing = false
                        showRegionSelector()
                    } else {
                        if (renderResults()) recognizeScreenshot(screenshot)
                    }
                }
            }
        }
    }

    private fun recognizeScreenshot(bitmap: Bitmap, nearbySource: android.graphics.Rect? = null) {
        readingBitmap = bitmap
        nearbySelection = nearbySource != null
        failureDetail = null
        if (IntegrationSettings.isAicrOcrEnabled(context)) {
            aicrRequest = AicrOcrClient.recognizeWithLines(context, bitmap) { result, error ->
                aicrRequest = null
                finishReading(bitmap)
                if (!closed && error !is java.util.concurrent.CancellationException) {
                    if (error != null) {
                        reportFailure(R.string.screen_ocr_failed, error)
                    } else {
                        if (nearbySource == null) {
                            recognitionCompleted(result.text.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() })
                        } else {
                            val lines = result.lines.filter { !it.text.isNullOrBlank() }
                            try {
                                val bounds = lines.map { aicrLineBounds(it) }
                                if (bounds.none { it != null }) {
                                    reportFailure(R.string.screen_ocr_region_unavailable,
                                        IllegalStateException("AICR 未返回可定位的文字行，请返回后手动框选"))
                                } else {
                                    recognitionCompleted(lines.map { it.text.trim() }, nearbyOcrLines(bounds, nearbySource))
                                }
                            } catch (error: IllegalArgumentException) {
                                reportFailure(R.string.screen_ocr_region_unavailable, error)
                            }
                        }
                    }
                }
            }
            return
        }
        val recognizer = try {
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        } catch (error: RuntimeException) {
            finishReading(bitmap)
            reportFailure(R.string.screen_ocr_failed, error)
            return
        }
        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener { task ->
                recognizer.close()
                finishReading(bitmap)
                if (!closed) {
                    if (task.isSuccessful) {
                        val recognizedLines = task.result.textBlocks.flatMap { it.lines }
                            .filter { it.text.isNotBlank() }
                        val suggested = nearbySource?.let { source ->
                            nearbyOcrLines(recognizedLines.map { it.boundingBox }, source)
                        }
                        recognitionCompleted(recognizedLines.map { it.text.trim() }, suggested)
                    } else {
                        reportFailure(R.string.screen_ocr_failed,
                            task.exception ?: IllegalStateException("ML Kit recognition failed"))
                    }
                }
            }
        } catch (error: RuntimeException) {
            recognizer.close()
            finishReading(bitmap)
            reportFailure(R.string.screen_ocr_failed, error)
        }
    }

    private fun finishReading(bitmap: Bitmap) {
        readingBitmap = null
        if (bitmap !== screenshotForRegion) bitmap.recycle()
    }

    private fun reportFailure(message: Int, error: Exception) {
        DragShareLog.w(TAG, "screen recognition failed", error)
        main.post {
            if (!closed) {
                failureDetail = context.getString(message) + "\n" + error.message.orEmpty()
                showFailure(message)
            }
        }
    }

    private fun recognitionCompleted(lines: List<String>, suggested: Set<Int>? = null) {
        if (lines.isEmpty()) {
            showFailure(R.string.screen_ocr_no_text)
            return
        }
        resultText = lines
        selectedLines = suggested ?: lines.indices.toSet()
        isRecognizing = false
        if (resultReceiver == null && selectRegion &&
            BigBangOverlayLauncher.showText(context, lines.joinToString("\n"), -1, -1, null)) {
            close()
        } else renderResults()
    }

    private fun showFailure(message: Int) {
        statusMessage = message
        isRecognizing = false
        renderResults()
    }

    private fun renderResults(): Boolean {
        regionVisible = false
        return showWindow()
    }

    private fun showRegionSelector() {
        if (screenshotForRegion == null) {
            showFailure(R.string.screen_ocr_region_unavailable)
            return
        }
        regionSelectionMessage = 0
        regionVisible = true
        showWindow()
    }

    private fun cancelRegion() {
        if (selectRegion && resultText.isEmpty()) close()
        else renderResults()
    }

    private fun showWindow(): Boolean {
        if (closed) return false
        if (window != null) return true
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val windowContext = context.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        lateinit var host: InteractiveOverlayWindow
        host = InteractiveOverlayWindow(windowContext, OverlayWindowPolicy.application(),
            onDismissed = {
                if (window === host) {
                    window = null
                    close()
                }
            },
            onFailure = {
                DragShareLog.w(TAG, "screen OCR window could not open", it)
                Toast.makeText(context, "无法显示屏幕识字窗口，请检查悬浮窗权限", Toast.LENGTH_LONG).show()
            },
        ) { Content() }
        window = host
        return host.show()
    }

    @Composable
    private fun Content() {
        val dark = DragShareSettings.readLocal(context).isDarkThemeInComposition()
        MiuixTheme(controller = remember(dark) {
            ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
        }) {
            BackHandler { if (regionVisible) cancelRegion() else close() }
            val screenshot = screenshotForRegion
            if (regionVisible && screenshot != null) {
                RegionSelectionPage(screenshot, regionSelectionMessage, ::cancelRegion, ::recognizeRegion)
            } else {
                val metrics = rememberOverlayPanelMetrics(panelHeightFraction = 0.68f)
                OverlayScene(Color.Black.copy(alpha = 0.38f), onDismiss = { close() }) {
                    FloatingPanel(width = metrics.width, height = metrics.height,
                        fillMax = metrics.fullScreen,
                        modifier = overlayPanelPlacement(metrics),
                        backgroundColor = MiuixTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize()) {
                            copyMessage?.let { message ->
                                Text(message, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    fontSize = 13.sp)
                            }
                            ScreenOcrPage(resultText, statusMessage, isRecognizing,
                                screenshotForRegion != null, { close() }, ::retryCapture, ::showRegionSelector,
                                ::openSegmentation, ::copyText, ::shareText)
                        }
                    }
                }
            }
        }
    }

    private fun retryCapture() {
        // Remove our window before the next capture; dismissing this host is not a session cancel.
        val host = window
        window = null
        host?.dismiss()
        screenshotForRegion?.recycle()
        screenshotForRegion = null
        resultText = emptyList()
        selectedLines = emptySet()
        statusMessage = R.string.screen_ocr_capturing
        isRecognizing = true
        scheduleCapture()
    }

    private fun recognizeRegion(startX: Float, startY: Float, endX: Float, endY: Float,
        viewWidth: Int, viewHeight: Int) {
        val screenshot = screenshotForRegion
        if (screenshot == null) {
            showFailure(R.string.screen_ocr_region_unavailable)
            return
        }
        val cropRect = OcrCropMapper.map(startX, startY, endX, endY, viewWidth, viewHeight,
            screenshot.width, screenshot.height)
        if (cropRect == null) {
            regionSelectionMessage = R.string.screen_ocr_region_invalid
            return
        }
        val crop = Bitmap.createBitmap(screenshot, cropRect.left, cropRect.top,
            cropRect.width(), cropRect.height())
        regionSelectionMessage = 0
        resultText = emptyList()
        statusMessage = R.string.screen_ocr_region_recognizing
        isRecognizing = true
        if (renderResults()) recognizeScreenshot(crop)
        else if (crop !== screenshot) crop.recycle()
    }

    private fun openSegmentation(text: String) {
        if (text.isBlank()) return
        if (resultReceiver != null) {
            resultSent = true
            resultReceiver.send(Activity.RESULT_OK, Bundle().apply { putString("text", text) })
            close()
        } else if (BigBangOverlayLauncher.showText(context, text, -1, -1, null)) close()
    }

    private fun copyText(text: String) {
        if (closed || copying || text.isBlank()) return
        copying = true
        copyMessage = "正在复制…"
        ClipboardWriter.copy(context, text) { error ->
            if (!closed) {
                copying = false
                copyMessage = if (error == null) "已复制文字"
                    else "复制失败：${error.message ?: "无法写入剪贴板"}"
            }
        }
    }

    private fun shareText(text: String) {
        if (text.isBlank()) return
        try {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            close()
        } catch (error: android.content.ActivityNotFoundException) {
            DragShareLog.w(TAG, "screen text sharing unavailable", error)
            Toast.makeText(context, "无法打开分享菜单", Toast.LENGTH_SHORT).show()
        } catch (error: SecurityException) {
            DragShareLog.w(TAG, "screen text sharing denied", error)
            Toast.makeText(context, "无法打开分享菜单", Toast.LENGTH_SHORT).show()
        }
    }

    fun close(restorePanel: Boolean = true) {
        if (closed) return
        closed = true
        if (!resultSent) {
            resultSent = true
            resultReceiver?.send(Activity.RESULT_CANCELED, Bundle().apply {
                putBoolean("restore_panel", restorePanel)
            })
        }
        main.removeCallbacks(pendingCapture)
        captureExecutor.shutdownNow()
        aicrRequest?.cancel()
        val host = window
        window = null
        host?.dismiss()
        if (screenshotForRegion !== readingBitmap) screenshotForRegion?.recycle()
        screenshotForRegion = null
        context.unregisterReceiver(screenOff)
        onClosed()
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
        val selectedText = lines.filterIndexed { index, _ -> index in selectedLines }.joinToString("\n")
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
                            if (nearbySelection) {
                                Text(
                                    text = "已按原文位置预选上下最近行；页面可能已变化，请核对后追加。没有合适内容可取消。",
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 13.sp,
                                )
                            }
                            Text(
                                text = if (resultReceiver != null) "已选 ${selectedLines.size} / ${lines.size} 行"
                                    else stringResource(R.string.screen_ocr_result_count, lines.size),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            if (resultReceiver == null) {
                                Text(
                                    text = stringResource(R.string.screen_ocr_tap_to_segment),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    fontSize = 13.sp,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    text = if (resultReceiver != null) "追加已选"
                                        else stringResource(R.string.screen_ocr_segment_all),
                                    onClick = { onSegment(if (resultReceiver != null) selectedText else allText) },
                                    enabled = resultReceiver == null || selectedLines.isNotEmpty(),
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                )
                                if (resultReceiver != null) {
                                    TextButton(
                                        text = if (selectedLines.size == lines.size) "取消全选" else "全选",
                                        onClick = {
                                            selectedLines = if (selectedLines.size == lines.size) emptySet()
                                                else lines.indices.toSet()
                                        },
                                    )
                                } else {
                                TextButton(
                                    text = stringResource(R.string.screen_ocr_copy_all),
                                    onClick = { onCopy(allText) },
                                    enabled = !copying,
                                )
                                TextButton(
                                    text = stringResource(R.string.screen_ocr_share_all),
                                    onClick = { onShare(allText) },
                                )
                                }
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
                itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                    val toggleLine = {
                        selectedLines = if (index in selectedLines) selectedLines - index else selectedLines + index
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp,
                            vertical = if (resultReceiver == null) 4.dp else 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().then(
                                    if (resultReceiver != null) Modifier.clickable(onClick = toggleLine) else Modifier),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (resultReceiver != null) {
                                    Checkbox(
                                        state = if (index in selectedLines) androidx.compose.ui.state.ToggleableState.On
                                            else androidx.compose.ui.state.ToggleableState.Off,
                                        onClick = toggleLine,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(
                                    text = line,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    modifier = Modifier.weight(1f).then(
                                        if (resultReceiver == null) {
                                            Modifier.clickable(role = Role.Button) { onSegment(line) }
                                                .padding(vertical = 12.dp)
                                        } else Modifier,
                                    ),
                                )
                                if (resultReceiver == null) {
                                    IconButton(onClick = { onCopy(line) }, enabled = !copying) {
                                        MiuixIcon(
                                            imageVector = Icons.Outlined.ContentCopy,
                                            contentDescription = stringResource(R.string.screen_ocr_copy_line),
                                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        )
                                    }
                                    IconButton(onClick = { onShare(line) }) {
                                        MiuixIcon(
                                            imageVector = Icons.Outlined.Share,
                                            contentDescription = stringResource(R.string.screen_ocr_share_line),
                                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                        )
                                    }
                                }
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
        val sourceBounds = remember(screenshot) {
            sourceArea?.let { area ->
                if (area.getInt("width") == screenshot.width && area.getInt("height") == screenshot.height) {
                    area.getParcelable("bounds", android.graphics.Rect::class.java)?.let { original ->
                        android.graphics.Rect(original).takeIf {
                            it.intersect(0, 0, screenshot.width, screenshot.height) && !it.isEmpty
                        }
                    }
                } else null
            }
        }
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
                sourceBounds?.let { bounds ->
                    drawRect(
                        color = Color(0xFF5AA9FF),
                        topLeft = Offset(bounds.left * size.width / screenshot.width,
                            bounds.top * size.height / screenshot.height),
                        size = Size(bounds.width() * size.width / screenshot.width,
                            bounds.height() * size.height / screenshot.height),
                        style = Stroke(width = 2.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(10.dp.toPx(), 6.dp.toPx()))),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
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
                                text = if (sourceBounds != null) "蓝色虚线是原文参考位置，页面可能已变化；请框选要追加的文字"
                                    else stringResource(R.string.screen_ocr_region_hint),
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
                        if (sourceBounds != null && resultReceiver != null) {
                            TextButton(
                                text = "查找原文附近文字",
                                onClick = {
                                    resultText = emptyList()
                                    statusMessage = R.string.screen_ocr_region_recognizing
                                    isRecognizing = true
                                    if (renderResults()) recognizeScreenshot(screenshot, sourceBounds)
                                },
                            )
                        }
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
                                enabled = selection != null && selection.width > 0f && selection.height > 0f,
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
        private const val TAG = "DragShare/ScreenOCR"
        private const val CAPTURE_FRAME_DELAY_MILLIS = 120L
    }
}

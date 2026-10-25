package com.leaf.hyperdragshare.codex

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
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
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.ThemeController
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** One explicit capture, region/result UI and AICR request lifetime, independent of an Activity. */
internal class ScreenOcrSession(
    private val context: Context,
    private val selectRegion: Boolean,
    val hostActivity: android.app.Activity?,
    private val onClosed: (Boolean) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var window: InteractiveOverlayWindow? = null
    private var closed = false
    private var regionVisible by mutableStateOf(false)
    private var resultText by mutableStateOf<List<String>>(emptyList())
    private var statusMessage by mutableIntStateOf(R.string.screen_ocr_capturing)
    private var isRecognizing by mutableStateOf(true)
    private var screenshotForRegion: Bitmap? = null
    // AICR reads its input synchronously; keep the Bitmap alive through its completion callback.
    private var readingBitmap: Bitmap? = null
    private var regionSelectionMessage by mutableIntStateOf(0)
    private var aicrRequest: AicrOcrClient.Request? = null
    private var failureDetail by mutableStateOf<String?>(null)
    private var copying by mutableStateOf(false)
    private var copyMessage by mutableStateOf<String?>(null)
    private val pendingCapture = Runnable { captureAndRecognize() }
    private val screenOff = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = close()
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
            close()
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

    private fun recognizeScreenshot(bitmap: Bitmap) {
        readingBitmap = bitmap
        resultText = emptyList()
        failureDetail = null
        aicrRequest = AicrOcrClient.recognize(context, bitmap) { result, error ->
            aicrRequest = null
            finishReading(bitmap)
            if (!closed && error !is java.util.concurrent.CancellationException) {
                if (error != null) {
                    reportFailure(R.string.screen_ocr_failed, error)
                } else {
                    recognitionCompleted(result.orEmpty().lines().filter { it.isNotBlank() })
                }
            }
        }
    }

    private fun finishReading(bitmap: Bitmap) {
        readingBitmap = null
        if (bitmap !== screenshotForRegion) bitmap.recycle()
    }

    private fun reportFailure(message: Int, error: Exception) {
        DragShareLog.w(TAG, "screen recognition failed category=aicr")
        main.post {
            if (!closed) {
                failureDetail = context.getString(message) + "\n" + error.message.orEmpty()
                showFailure(message)
            }
        }
    }

    private fun recognitionCompleted(lines: List<String>) {
        if (lines.isEmpty()) {
            showFailure(R.string.screen_ocr_no_text)
            return
        }
        resultText = lines
        isRecognizing = false
        if (selectRegion && openTextPanel(lines.joinToString("\n"))) {
            close(finishHost = false)
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
        val windowContext = hostActivity ?: context.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        lateinit var host: InteractiveOverlayWindow
        host = InteractiveOverlayWindow(windowContext,
            if (hostActivity != null) OverlayWindowPolicy.hosted() else OverlayWindowPolicy.application(),
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
                OverlayScene(Color.Black.copy(alpha = if (dark) .62f else .48f), onDismiss = { close() }) {
                    FloatingPanel(width = metrics.width, height = metrics.height,
                        fillMax = metrics.fullScreen,
                        modifier = overlayPanelPlacement(metrics),
                        shape = RoundedCornerShape(metrics.cornerRadius),
                        backgroundColor = MiuixTheme.colorScheme.background) {
                        OverlayPanelScaffold(
                            topBar = {
                                OverlayHeaderBar(
                                    backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
                                    topInset = metrics.topSystemInset, leftInset = metrics.leftSystemInset,
                                    rightInset = metrics.rightSystemInset,
                                    leading = {
                                        Text(stringResource(R.string.screen_ocr_title), fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onSurface)
                                    }, center = {}, trailing = {
                                        OverlayIconAction(MiuixIcons.Close, MiuixTheme.colorScheme.onSurface,
                                            onClick = { close() }, contentDescription = "关闭屏幕识字")
                                    },
                                )
                            }, bottomBar = {},
                        ) { bodyModifier ->
                            Column(bodyModifier) {
                                copyMessage?.let { message ->
                                    Text(message, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        fontSize = 13.sp)
                                }
                                ScreenOcrPage(resultText, statusMessage, isRecognizing,
                                    screenshotForRegion != null, ::retryCapture, ::showRegionSelector,
                                    ::openSegmentation, ::copyText, ::shareText)
                            }
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
        if (openTextPanel(text)) close(finishHost = false)
    }

    private fun openTextPanel(text: String): Boolean {
        val host = hostActivity
        return if (host == null) BigBangOverlayLauncher.showText(context, text, -1, -1)
        else BigBangOverlayLauncher.requestHosted(host, android.os.Bundle().apply { putString("text", text) })
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

    fun close(finishHost: Boolean = true) {
        if (closed) return
        closed = true
        main.removeCallbacks(pendingCapture)
        captureExecutor.shutdownNow()
        aicrRequest?.cancel()
        val host = window
        window = null
        host?.dismiss()
        if (screenshotForRegion !== readingBitmap) screenshotForRegion?.recycle()
        screenshotForRegion = null
        context.unregisterReceiver(screenOff)
        onClosed(finishHost)
    }

    @Composable
    private fun ScreenOcrPage(
        lines: List<String>,
        statusMessage: Int,
        isRecognizing: Boolean,
        canSelectRegion: Boolean,
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
            item(key = "screen-ocr-description") {
                Text(stringResource(R.string.screen_ocr_privacy), fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
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
                            Text(
                                text = stringResource(R.string.screen_ocr_tap_to_segment),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 13.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    text = stringResource(R.string.screen_ocr_segment_all),
                                    modifier = Modifier.height(44.dp), minHeight = 44.dp,
                                    onClick = { onSegment(allText) },
                                    colors = ButtonDefaults.textButtonColorsPrimary(),
                                )
                                OverlayIconAction(MiuixIcons.Copy, MiuixTheme.colorScheme.onSurface,
                                    enabled = !copying, onClick = { onCopy(allText) },
                                    contentDescription = stringResource(R.string.screen_ocr_copy_all))
                                OverlayIconAction(MiuixIcons.Share, MiuixTheme.colorScheme.onSurface,
                                    onClick = { onShare(allText) },
                                    contentDescription = stringResource(R.string.screen_ocr_share_all))
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
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = line,
                                color = MiuixTheme.colorScheme.onSurface,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                                    .clickable(role = Role.Button) { onSegment(line) }
                                    .padding(vertical = 12.dp),
                            )
                            OverlayIconAction(MiuixIcons.Copy, MiuixTheme.colorScheme.onSurface,
                                enabled = !copying, onClick = { onCopy(line) },
                                contentDescription = stringResource(R.string.screen_ocr_copy_line))
                            OverlayIconAction(MiuixIcons.Share, MiuixTheme.colorScheme.onSurface,
                                onClick = { onShare(line) },
                                contentDescription = stringResource(R.string.screen_ocr_share_line))
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
                                fontSize = 18.sp,
                                color = MiuixTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.screen_ocr_region_hint),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 13.sp,
                            )
                        }
                        OverlayIconAction(MiuixIcons.Close, MiuixTheme.colorScheme.onSurface,
                            onClick = onCancel, contentDescription = "退出区域选择")
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
                                text = stringResource(R.string.screen_ocr_region_recognize),
                                modifier = Modifier.height(44.dp), minHeight = 44.dp,
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

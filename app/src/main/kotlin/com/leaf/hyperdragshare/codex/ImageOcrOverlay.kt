package com.leaf.hyperdragshare.codex

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

/** Image decoding and recognition stay in the module; no Activity is started. */
object ImageOcrOverlay {
    const val METHOD_RECOGNIZE = "recognize_image_window"
    const val METHOD_QR_SCAN = "qr_scan_image_window"
    private val main = Handler(Looper.getMainLooper())
    private var active: Job? = null

    @JvmStatic fun start(context: Context, uri: Uri): Boolean = open(context, uri, false, null)

    @JvmStatic fun startFromPress(context: Context, uri: Uri, confirmedAt: Long): Boolean =
        open(context, uri, false, null, pressConfirmedAt = confirmedAt)

    @JvmStatic fun startWithResult(context: Context, uri: Uri, onResult: (String?) -> Unit): Boolean =
        open(context, uri, false, null, onResult)

    @JvmStatic fun startQr(context: Context, uri: Uri, owner: android.view.View? = null): Boolean =
        open(context, uri, true, owner)

    private fun open(context: Context, uri: Uri, qr: Boolean, owner: android.view.View?,
        onResult: ((String?) -> Unit)? = null, pressConfirmedAt: Long = 0L): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            main.post {
                android.widget.Toast.makeText(context.applicationContext, "请先在模块设置中允许悬浮窗权限",
                    android.widget.Toast.LENGTH_LONG).show()
            }
            return false
        }
        val app = context.applicationContext
        main.post {
            if (owner != null && !owner.isAttachedToWindow) {
                onResult?.invoke(null)
                return@post
            }
            active?.dismissForReplacement()
            if (app.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) {
                onResult?.invoke(null)
                return@post
            }
            val job = Job(app, uri, qr, owner, onResult)
            active = job
            if (job.window.show(pressConfirmedAt)) job.decode()
        }
        return true
    }

    private class Job(private val app: Context, private val uri: Uri, private val qr: Boolean,
        private val owner: android.view.View?, private val onResult: ((String?) -> Unit)?) {
        private val display = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        private val context = app.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        private var errorText by mutableStateOf<String?>(null)
        private var codes by mutableStateOf<List<String>?>(null)
        private var copiedCode by mutableStateOf<String?>(null)
        private var copying by mutableStateOf(false)
        private var actionError by mutableStateOf<String?>(null)
        private var cancelled = false
        private var resultDelivered = false
        private var aicr: AicrOcrClient.Request? = null
        private val screenOff: android.content.BroadcastReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: android.content.Intent) = window.dismiss()
        }
        private val ownerListener = object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: android.view.View) = Unit
            override fun onViewDetachedFromWindow(view: android.view.View) = window.dismiss()
        }
        val window: InteractiveOverlayWindow = InteractiveOverlayWindow(context, OverlayWindowPolicy.application(),
            onDismissed = {
                cancelled = true
                aicr?.cancel()
                app.unregisterReceiver(screenOff)
                owner?.removeOnAttachStateChangeListener(ownerListener)
                if (active === this) active = null
                if (!resultDelivered) {
                    resultDelivered = true
                    onResult?.invoke(null)
                }
            },
            onFailure = {
                DragShareLog.w("DragShare/ImageOcrWindow", "window failed", it)
                android.widget.Toast.makeText(app, "无法显示识别窗口：${it.message}", android.widget.Toast.LENGTH_LONG).show()
            },
        ) {
            val dark = DragShareSettings.readLocal(app).isDarkThemeInComposition()
            MiuixTheme(controller = androidx.compose.runtime.remember(dark) {
                ThemeController(if (dark)
                    ColorSchemeMode.Dark else ColorSchemeMode.Light)
            }) {
                BackHandler { window.dismiss() }
                val metrics = rememberOverlayPanelMetrics(panelHeightFraction = if (codes == null) .32f else .5f)
                OverlayScene(Color.Black.copy(alpha = if (dark) .62f else .48f), onDismiss = window::dismiss) {
                    FloatingPanel(metrics.width, metrics.height, fillMax = metrics.fullScreen,
                        modifier = overlayPanelPlacement(metrics), shape = RoundedCornerShape(metrics.cornerRadius),
                        backgroundColor = MiuixTheme.colorScheme.background) {
                        OverlayPanelScaffold(
                            topBar = {
                                OverlayHeaderBar(
                                    backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
                                    topInset = metrics.topSystemInset,
                                    leftInset = metrics.leftSystemInset,
                                    rightInset = metrics.rightSystemInset,
                                    leading = {
                                        Text(if (qr) "二维码" else "图片识字", fontSize = 18.sp,
                                            fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onSurface)
                                    },
                                    center = {},
                                    trailing = {
                                        OverlayIconAction(MiuixIcons.Close, MiuixTheme.colorScheme.onSurface,
                                            onClick = window::dismiss, contentDescription = "关闭识别窗口")
                                    },
                                )
                            },
                            bottomBar = {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp,
                                    vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (errorText != null) OverlayIconAction(MiuixIcons.Refresh,
                                        MiuixTheme.colorScheme.onSurface, onClick = { restart(qr) },
                                        contentDescription = "重试识别")
                                    if (!qr) OverlayIconAction(MiuixIcons.Scan,
                                        MiuixTheme.colorScheme.onSurface, onClick = { restart(true) },
                                        contentDescription = "扫描此图二维码")
                                }
                            },
                        ) { bodyModifier ->
                            Column(bodyModifier.verticalScroll(rememberScrollState()).padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                errorText?.let { Text(it, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface) }
                                actionError?.let { Text(it, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface) }
                                if (errorText == null && codes == null) {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        CircularProgressIndicator()
                                        Text(if (qr) "正在识别二维码…" else "正在识别图片…", fontSize = 14.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    }
                                }
                                codes?.forEach { value ->
                                    SelectionContainer {
                                        Text(value, fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurface)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        OverlayIconAction(MiuixIcons.Copy, MiuixTheme.colorScheme.onSurface,
                                            enabled = !copying, onClick = { copyCode(value) },
                                            contentDescription = "复制二维码内容")
                                        val direct = directTextDestination(value)
                                        OverlayIconAction(direct?.icon ?: MiuixIcons.Search,
                                            MiuixTheme.colorScheme.onSurface,
                                            onClick = {
                                                if (direct != null && direct.kind != DirectTextKind.Url) {
                                                    openExternal(direct.intent)
                                                } else {
                                                    SearchOverlayWindow.show(
                                                        context,
                                                        window.window.decorView,
                                                        value,
                                                        BoomActionHandler.SEARCH_WEB,
                                                        openExternal = ::openExternal,
                                                    )
                                                }
                                            }, contentDescription = direct?.label ?: "搜索内容")
                                        if (copiedCode == value) Text("已复制", fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        init {
            app.registerReceiver(screenOff, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF),
                Context.RECEIVER_NOT_EXPORTED)
            owner?.addOnAttachStateChangeListener(ownerListener)
        }

        fun decode() {
            Thread({
                try {
                    val source = if (uri.scheme == "file") ImageDecoder.createSource(java.io.File(requireNotNull(uri.path)))
                        else ImageDecoder.createSource(app.contentResolver, uri)
                    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val longest = maxOf(info.size.width, info.size.height)
                        if (longest > 4096) {
                            val scale = 4096f / longest
                            decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
                                (info.size.height * scale).toInt().coerceAtLeast(1))
                        }
                    }
                    if (qr) {
                        val found = try { QrImageDecoder.decode(bitmap) } finally { bitmap.recycle() }
                        main.post {
                            if (!cancelled) {
                                if (found.isEmpty()) errorText = "这张图片没有发现二维码" else codes = found
                            }
                        }
                    } else main.post {
                        if (cancelled) bitmap.recycle() else recognize(bitmap)
                    }
                } catch (error: java.io.IOException) {
                    main.post { failed(error) }
                } catch (error: RuntimeException) {
                    main.post { failed(error) }
                }
            }, "image-window-decode").start()
        }

        fun dismissForReplacement() {
            resultDelivered = true
            window.dismiss()
        }

        private fun restart(qr: Boolean) {
            // The next recognition owns the same result; dismissing this attempt is not cancellation.
            dismissForReplacement()
            if (!open(app, uri, qr, owner, onResult)) onResult?.invoke(null)
        }

        private fun copyCode(value: String) {
            copying = true
            actionError = null
            ClipboardWriter.copy(app, value) { error ->
                if (!cancelled) {
                    copying = false
                    if (error == null) copiedCode = value else actionError = "无法复制：${error.message}"
                }
            }
        }

        private fun openExternal(intent: android.content.Intent) {
            try {
                context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                window.dismiss()
            } catch (error: android.content.ActivityNotFoundException) {
                failed(error)
            } catch (error: SecurityException) {
                failed(error)
            }
        }

        private fun recognize(bitmap: Bitmap) {
            aicr = AicrOcrClient.recognize(app, bitmap) { text, error ->
                bitmap.recycle()
                if (!cancelled) {
                    if (error == null) completed(text.orEmpty()) else failed(error)
                }
            }
        }

        private fun completed(text: String) {
            if (text.isBlank()) {
                errorText = "这张图片没有识别到文字"
                return
            }
            resultDelivered = true
            window.dismiss()
            if (onResult != null) onResult.invoke(text)
            else BigBangOverlayLauncher.showText(app, text, -1, -1)
        }

        private fun failed(error: Exception) {
            if (cancelled) return
            DragShareLog.w("DragShare/ImageOcrWindow", "image recognition failed", error)
            if (codes != null) actionError = "无法打开：${error.message}"
            else errorText = "识别失败：${error.message}"
        }
    }
}

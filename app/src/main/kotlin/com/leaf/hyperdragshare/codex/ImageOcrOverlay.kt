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
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

/** Image decoding and recognition stay in the module; no Activity is started. */
object ImageOcrOverlay {
    const val METHOD_RECOGNIZE = "recognize_image_window"
    private val main = Handler(Looper.getMainLooper())
    private var active: Job? = null

    @JvmStatic fun start(context: Context, uri: Uri): Boolean = open(context, uri, false, null)

    @JvmStatic fun startQr(context: Context, uri: Uri, owner: android.view.View? = null): Boolean =
        open(context, uri, true, owner)

    private fun open(context: Context, uri: Uri, qr: Boolean, owner: android.view.View?): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            main.post {
                android.widget.Toast.makeText(context.applicationContext, "请先在模块设置中允许悬浮窗权限",
                    android.widget.Toast.LENGTH_LONG).show()
            }
            return false
        }
        val app = context.applicationContext
        main.post {
            if (owner != null && !owner.isAttachedToWindow) return@post
            active?.window?.dismiss()
            if (app.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) return@post
            val job = Job(app, uri, qr, owner)
            active = job
            if (job.window.show()) job.decode()
        }
        return true
    }

    private class Job(private val app: Context, private val uri: Uri, private val qr: Boolean,
        private val owner: android.view.View?) {
        private val display = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        private val context = app.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        private var errorText by mutableStateOf<String?>(null)
        private var codes by mutableStateOf<List<String>?>(null)
        private var copiedCode by mutableStateOf<String?>(null)
        private var copying by mutableStateOf(false)
        private var actionError by mutableStateOf<String?>(null)
        private var cancelled = false
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
                Card {
                    Column(Modifier.heightIn(max = (context.resources.configuration.screenHeightDp * 0.65f).dp)
                        .verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (codes != null) "二维码内容" else errorText
                            ?: if (qr) "正在识别二维码…" else "正在识别图片…",
                            color = MiuixTheme.colorScheme.onSurface)
                        actionError?.let { Text(it, color = MiuixTheme.colorScheme.onSurface) }
                        codes?.forEach { value ->
                            SelectionContainer { Text(value, color = MiuixTheme.colorScheme.onSurface) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(if (copying) "复制中…" else if (copiedCode == value) "已复制" else "复制",
                                    { copyCode(value) }, enabled = !copying)
                                val engine = SearchEngineCatalog.defaultForType(BoomActionHandler.SEARCH_WEB)
                                if (SearchEngineCatalog.destination(engine, value) != null) {
                                    TextButton(if (value.startsWith("https://", ignoreCase = true)) "打开链接" else "搜索", {
                                        SearchOverlayWindow.show(context, window.window.decorView, value,
                                            BoomActionHandler.SEARCH_WEB, openExternal = ::openExternal)
                                    })
                                }
                            }
                        }
                        if (errorText == null && codes == null) CircularProgressIndicator()
                        if (errorText != null) TextButton("重试", { restart(qr) })
                        if (!qr) TextButton("识别二维码", { restart(true) })
                        TextButton(if (codes != null) "关闭" else "取消", window::dismiss)
                    }
                }
            }
        }

        init {
            app.registerReceiver(screenOff, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF),
                Context.RECEIVER_NOT_EXPORTED)
            owner?.addOnAttachStateChangeListener(ownerListener)
            window.window.attributes = window.window.attributes.apply {
                width = (300 * context.resources.displayMetrics.density).toInt()
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.CENTER
                flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            }
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

        private fun restart(qr: Boolean) {
            window.dismiss()
            open(app, uri, qr, owner)
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
            if (IntegrationSettings.isAicrOcrEnabled(app)) {
                aicr = AicrOcrClient.recognize(app, bitmap) { text, error ->
                    bitmap.recycle()
                    if (!cancelled) {
                        if (error == null) completed(text.orEmpty()) else failed(error)
                    }
                }
                return
            }
            val recognizer = try {
                TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            } catch (error: RuntimeException) {
                bitmap.recycle()
                failed(error)
                return
            }
            try {
                recognizer.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener { task ->
                    recognizer.close()
                    bitmap.recycle()
                    if (!cancelled) {
                        if (task.isSuccessful) completed(task.result.text)
                        else failed(task.exception ?: IllegalStateException("文字识别失败"))
                    }
                }
            } catch (error: RuntimeException) {
                recognizer.close()
                bitmap.recycle()
                failed(error)
            }
        }

        private fun completed(text: String) {
            if (text.isBlank()) {
                errorText = "这张图片没有识别到文字"
                return
            }
            window.dismiss()
            BigBangOverlayLauncher.showText(app, text, -1, -1, uri)
        }

        private fun failed(error: Exception) {
            if (cancelled) return
            DragShareLog.w("DragShare/ImageOcrWindow", "image recognition failed", error)
            if (codes != null) actionError = "无法打开：${error.message}"
            else errorText = "识别失败：${error.message}"
        }
    }
}

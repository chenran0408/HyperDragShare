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
import androidx.compose.foundation.layout.padding
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

    @JvmStatic fun start(context: Context, uri: Uri) {
        if (!Settings.canDrawOverlays(context)) {
            main.post {
                android.widget.Toast.makeText(context.applicationContext, "请先在模块设置中允许悬浮窗权限",
                    android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }
        val app = context.applicationContext
        main.post {
            active?.window?.dismiss()
            if (app.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) return@post
            val job = Job(app, uri)
            active = job
            if (job.window.show()) job.decode()
        }
    }

    private class Job(private val app: Context, private val uri: Uri) {
        private val display = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        private val context = app.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        private var errorText by mutableStateOf<String?>(null)
        private var cancelled = false
        private var aicr: AicrOcrClient.Request? = null
        private val screenOff: android.content.BroadcastReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: android.content.Intent) = window.dismiss()
        }
        val window: InteractiveOverlayWindow = InteractiveOverlayWindow(context, OverlayWindowPolicy.application(),
            onDismissed = {
                cancelled = true
                aicr?.cancel()
                app.unregisterReceiver(screenOff)
                if (active === this) active = null
            },
            onFailure = {
                DragShareLog.w("DragShare/ImageOcrWindow", "window failed", it)
                android.widget.Toast.makeText(app, "无法显示识别窗口：${it.message}", android.widget.Toast.LENGTH_LONG).show()
            },
        ) {
            MiuixTheme(controller = androidx.compose.runtime.remember {
                ThemeController(if (DragShareSettings.readLocal(app).colorMode == DragShareSettings.COLOR_DARK)
                    ColorSchemeMode.Dark else ColorSchemeMode.Light)
            }) {
                Card {
                    Column(Modifier.padding(20.dp)) {
                        Text(errorText ?: "正在识别图片…", color = MiuixTheme.colorScheme.onSurface)
                        if (errorText == null) CircularProgressIndicator()
                        else TextButton("重试", { window.dismiss(); start(app, uri) })
                        TextButton("取消", window::dismiss)
                    }
                }
            }
        }

        init {
            app.registerReceiver(screenOff, android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF),
                Context.RECEIVER_NOT_EXPORTED)
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
                    main.post {
                        if (cancelled) bitmap.recycle() else recognize(bitmap)
                    }
                } catch (error: java.io.IOException) {
                    main.post { failed(error) }
                } catch (error: RuntimeException) {
                    main.post { failed(error) }
                }
            }, "image-window-decode").start()
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
            errorText = "识别失败：${error.message}"
        }
    }
}

package com.leaf.hyperdragshare.codex

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Choreographer
import android.view.Display
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.io.IOException
import java.util.concurrent.Executors

/** An explicit point belongs to this window, never to the underlying app's long-press gesture. */
object PointCaptureOverlay {
    private var active: Session? = null

    fun start(context: Context): Boolean {
        val app = context.applicationContext
        if (app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            Toast.makeText(app, "请解锁后点取内容", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!Settings.canDrawOverlays(app)) {
            Toast.makeText(app, "请先在模块设置中允许悬浮窗权限", Toast.LENGTH_LONG).show()
            return false
        }
        active?.close()
        return Session(app).also { active = it }.start()
    }

    private class Session(private val app: Context) {
        private val main = Handler(Looper.getMainLooper())
        private val worker = Executors.newSingleThreadExecutor()
        private val displayManager = app.getSystemService(DisplayManager::class.java)
        private val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        private val rotation = display.rotation
        private val context = app.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        private val sourceDisplay = context.getSystemService(WindowManager::class.java)
            .maximumWindowMetrics.bounds
        private val settings = DragShareSettings.readLocal(app)
        private var window: InteractiveOverlayWindow? = null
        private var nodeSession: RootNodeCaptureClient.Session? = null
        @Volatile private var closed = false
        private var prepared by mutableStateOf(false)
        private var failure by mutableStateOf<String?>(null)
        private var retryAllowed = true
        private val screenOff = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = close()
        }
        private val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) close()
            }
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY && !sameDisplay()) close()
            }
        }

        fun start(): Boolean {
            app.registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
            displayManager.registerDisplayListener(displayListener, main)
            if (!settings.isSharingEnabled(false) && !settings.isSharingEnabled(true)) {
                failure = "文字和图片内容均已关闭，请先在模块设置中开启需要的内容。"
                retryAllowed = false
            } else if (!settings.isAccessibilityRecognitionEnabledForOrientation(app.resources.configuration.orientation)) {
                failure = "当前方向的内容识别已关闭，请先在模块设置中开启横屏识别。"
                retryAllowed = false
            }
            if (!showWindow()) return false
            if (failure == null) prepareRoot()
            return true
        }

        private fun prepareRoot() {
            worker.execute {
                try {
                    val ready = RootNodeCaptureClient.open(app)
                    main.post {
                        if (closed) ready.close()
                        else {
                            nodeSession = ready
                            prepared = true
                        }
                    }
                } catch (error: IOException) {
                    fail("点取服务未能准备完成", error)
                } catch (error: SecurityException) {
                    fail("系统拒绝连接点取服务", error)
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    fail("点取准备已中断", error)
                }
            }
        }

        private fun pick(x: Float, y: Float) {
            if (closed || !prepared) return
            val root = requireNotNull(nodeSession)
            prepared = false
            removeWindow()
            // The original window must regain focus before UiAutomation asks for its semantic tree.
            Choreographer.getInstance().postFrameCallback {
                if (closed) return@postFrameCallback
                if (!available()) { close(); return@postFrameCallback }
                worker.execute {
                    try {
                        val excluded = settings.accessibilityBlacklistedPackages.toMutableSet()
                        excluded.addAll(AccessibilityBlacklist.builtInPackages(app))
                        val content = root.capture(x, y, excluded,
                            settings.isSharingEnabled(false), settings.isSharingEnabled(true)) { _ -> !closed }
                        main.post {
                            nodeSession = null
                            if (closed || !available()) {
                                if (content != null && content.isImage()) content.bitmap.recycle()
                                close()
                            } else if (content == null) {
                                fail("这里没有可提取的文字或图片，或该应用不允许识别。请重新点取。")
                            } else if (content.isImage()) {
                                openImage(content)
                            } else openText(content, x, y)
                        }
                    } catch (error: IOException) {
                        fail("点取失败，请重新选择", error)
                    } catch (error: SecurityException) {
                        fail("系统拒绝读取当前页面", error)
                    } catch (error: InterruptedException) {
                        Thread.currentThread().interrupt()
                        fail("点取已中断", error)
                    } finally {
                        root.close()
                    }
                }
            }
        }

        private fun openText(content: CapturedContent, x: Float, y: Float) {
            try {
                BigBangOverlayLauncher.request(app, Bundle().apply {
                    putString("text", content.text)
                    putString("before_text", content.beforeText)
                    putString("after_text", content.afterText)
                    putInt("x", x.toInt())
                    putInt("y", y.toInt())
                    putBundle("source_area", Bundle().apply {
                        putParcelable("bounds", content.sourceBounds)
                        putInt("width", sourceDisplay.width())
                        putInt("height", sourceDisplay.height())
                    })
                })
                close()
            } catch (error: IllegalStateException) {
                fail("无法显示分词窗口，请检查悬浮窗权限", error)
            }
        }

        private fun openImage(content: CapturedContent) {
            ImageStagingClient.stage(app, content.bitmap, object : ImageStagingClient.Callback {
                override fun onStaged(uri: Uri) {
                    content.bitmap.recycle()
                    main.post {
                        if (closed || !available()) { close(); return@post }
                        if (ImageOcrOverlay.start(app, uri)) close()
                        else fail("无法显示图片识字窗口，请检查悬浮窗权限。")
                    }
                }
                override fun onFailure(error: Throwable) {
                    content.bitmap.recycle()
                    fail("暂存所选图片失败", error)
                }
            })
        }

        private fun fail(message: String, error: Throwable? = null) {
            if (error != null) DragShareLog.w("DragShare/PointCapture", message, error)
            main.post {
                if (!closed) {
                    nodeSession?.close()
                    nodeSession = null
                    prepared = false
                    failure = message + if (error?.message.isNullOrBlank()) "" else "\n${error.message}"
                    showWindow()
                }
            }
        }

        private fun sameDisplay(): Boolean = displayManager.getDisplay(Display.DEFAULT_DISPLAY).rotation == rotation &&
            context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds == sourceDisplay

        private fun available(): Boolean = sameDisplay() &&
            !app.getSystemService(KeyguardManager::class.java).isKeyguardLocked

        private fun showWindow(): Boolean {
            if (window != null) return true
            lateinit var host: InteractiveOverlayWindow
            host = InteractiveOverlayWindow(context, OverlayWindowPolicy.application(),
                onDismissed = { if (window === host) { window = null; close() } },
                onFailure = {
                    DragShareLog.w("DragShare/PointCapture", "point picker window unavailable", it)
                    Toast.makeText(app, "无法显示点取窗口，请检查悬浮窗权限", Toast.LENGTH_LONG).show()
                }) { Content() }
            window = host
            return host.show()
        }

        private fun removeWindow() {
            val host = window
            window = null
            host?.dismiss()
        }

        fun close() {
            if (closed) return
            closed = true
            removeWindow()
            nodeSession?.close()
            nodeSession = null
            worker.shutdownNow()
            app.unregisterReceiver(screenOff)
            displayManager.unregisterDisplayListener(displayListener)
            if (active === this) active = null
        }

        @Composable private fun Content() {
            val dark = settings.isDarkThemeInComposition()
            MiuixTheme(controller = remember(dark) {
                ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
            }) {
                BackHandler { close() }
                if (prepared) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .06f))) {
                        AndroidView(modifier = Modifier.fillMaxSize(), factory = { viewContext ->
                            object : View(viewContext) {
                                override fun performClick(): Boolean {
                                    super.performClick()
                                    return true
                                }
                            }.apply {
                                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                                val gestures = GestureDetector(viewContext, object : GestureDetector.SimpleOnGestureListener() {
                                    override fun onDown(event: MotionEvent) = true
                                    override fun onSingleTapUp(event: MotionEvent): Boolean {
                                        performClick()
                                        pick(event.rawX, event.rawY)
                                        return true
                                    }
                                })
                                setOnTouchListener { _, event -> gestures.onTouchEvent(event); true }
                            }
                        })
                        // Consume taps on the instructions without exposing a no-op clickable control.
                        Card(Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(12.dp)
                            .pointerInput(Unit) { detectTapGestures { } }) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("点选文字或图片", Modifier.weight(1f), fontSize = 16.sp)
                                TextButton("取消", { close() })
                            }
                        }
                    }
                } else {
                    OverlayScene(Color.Black.copy(alpha = .26f), onDismiss = { close() }) {
                        Card(Modifier.align(Alignment.Center).padding(24.dp).widthIn(max = 360.dp)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("点取文字或图片")
                                if (failure == null) {
                                    CircularProgressIndicator()
                                    Text("正在准备 Root 点取服务…")
                                } else Text(requireNotNull(failure))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    if (failure != null && retryAllowed) {
                                        TextButton("重选", { close(); PointCaptureOverlay.start(app) })
                                    }
                                    TextButton("关闭", { close() })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

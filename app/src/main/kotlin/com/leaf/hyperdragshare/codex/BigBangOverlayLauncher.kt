package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast

/** Requests enter through the provider's module/portal UID check; UI stays in the module process. */
object BigBangOverlayLauncher {
    const val METHOD_OPEN = "open_bigbang_window"
    private val main = Handler(Looper.getMainLooper())
    private var active: InteractiveOverlayWindow? = null
    private var generation = 0L

    @JvmStatic fun request(context: Context, input: Bundle?): Bundle {
        val text = input?.getString("text")
        require(!text.isNullOrBlank()) { "没有可分词的文字" }
        check(Settings.canDrawOverlays(context)) { "请先在模块设置中允许悬浮窗权限" }
        val x = input.getInt("x", -1)
        val y = input.getInt("y", -1)
        val sourceImage = input.getParcelable("source_image", Uri::class.java)
        val before = input.getString("before_text")
        val after = input.getString("after_text")
        val pressConfirmedAt = input.getLong("press_confirmed_at")
        main.post { open(context.applicationContext, text, x, y, sourceImage, before = before, after = after,
            pressConfirmedAt = pressConfirmedAt) }
        return Bundle().apply { putBoolean("accepted", true) }
    }

    @JvmStatic fun showText(context: Context, text: String, x: Int, y: Int, sourceImage: Uri?): Boolean {
        return showTextWithAdjacent(context, text, x, y, sourceImage, null, null)
    }

    @JvmStatic fun showTextWithAdjacent(context: Context, text: String, x: Int, y: Int,
        sourceImage: Uri?, before: String?, after: String?): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "请先在模块设置中允许悬浮窗权限", Toast.LENGTH_LONG).show()
            return false
        }
        if (text.isBlank()) return false
        main.post { open(context.applicationContext, text, x, y, sourceImage, before = before, after = after) }
        return true
    }

    private fun open(app: Context, text: String, x: Int, y: Int, sourceImage: Uri?, savedState: Bundle? = null,
        before: String? = null, after: String? = null, pressConfirmedAt: Long = 0L) {
        if (app.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) {
            Toast.makeText(app, "请解锁后打开分词窗口", Toast.LENGTH_SHORT).show()
            return
        }
        active?.dismiss()
        val windowGeneration = ++generation
        val display = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val context = app.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        lateinit var overlay: InteractiveOverlayWindow
        val screenOff = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = overlay.dismiss()
        }
        lateinit var session: BigBangPanelSession
        val originalText = text
        session = BigBangPanelSession(context, object : BigBangPanelSession.Actions {
            override fun dismiss() = overlay.dismiss()
            override fun collapse() = overlay.dismiss()
            override fun search(text: String, type: Int) {
                SearchOverlayWindow.show(context, overlay.window.decorView, text, type,
                    openExternal = { launch(it) })
            }
            override fun share(text: String) {
                launch(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    this.type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }, null))
            }
            override fun dragText(source: View, text: String): Boolean {
                val density = context.resources.displayMetrics.density
                val colors = OverlayThemeColors.colors(DragShareSettings.readLocal(context).isDarkTheme(context))
                val preview = TextView(context).apply {
                    this.text = text
                    textSize = 14f
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(colors[1])
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(colors[0])
                        cornerRadius = 12 * density
                    }
                    val padding = (12 * density).toInt()
                    setPadding(padding, padding, padding, padding)
                    measure(View.MeasureSpec.makeMeasureSpec((200 * density).toInt(), View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec((120 * density).toInt(), View.MeasureSpec.AT_MOST))
                    layout(0, 0, measuredWidth, measuredHeight)
                }
                overlay.observeTextDrag { generation == windowGeneration && active === overlay }
                val started = try {
                    source.startDragAndDrop(ClipData.newPlainText("选中文字", text),
                        View.DragShadowBuilder(preview), null, View.DRAG_FLAG_GLOBAL)
                } catch (error: SecurityException) {
                    overlay.cancelTextDragObservation()
                    report(app, error)
                    return false
                }
                if (started) {
                    DragShareLog.i("DragShare/BigBangWindow", "selected text drag started length=${text.length}")
                    // The attached window waits for ENDED; cancellation restores this same selection.
                } else {
                    overlay.cancelTextDragObservation()
                    Toast.makeText(app, "系统未能开始拖拽，已保留选词", Toast.LENGTH_SHORT).show()
                }
                return started
            }
            override fun reopenOcr(uri: Uri) {
                val state = Bundle().also(session::saveState)
                val accepted = ImageOcrOverlay.startWithResult(app, uri) { result ->
                    if (generation == windowGeneration &&
                        !app.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) {
                        if (result == null) open(app, originalText, x, y, sourceImage, state)
                        else open(app, result, x, y, uri, Bundle().apply {
                            putBundle(BigBangPanelSession.UNDO_EDIT, state)
                        })
                    }
                }
                if (accepted) overlay.dismiss()
            }
            override fun readQr(uri: Uri) {
                ImageOcrOverlay.startQr(app, uri, overlay.window.decorView)
            }
            private fun launch(intent: Intent) {
                try {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    overlay.dismiss()
                } catch (error: android.content.ActivityNotFoundException) {
                    report(app, error)
                } catch (error: SecurityException) {
                    report(app, error)
                }
            }
        })
        overlay = InteractiveOverlayWindow(context, OverlayWindowPolicy.application(),
            onDismissed = {
                session.close()
                app.unregisterReceiver(screenOff)
                if (active === overlay) active = null
            },
            onFailure = { report(app, it) },
        ) { session.Content() }
        app.registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        active = overlay
        if (overlay.show(pressConfirmedAt)) session.open(text, x, y, sourceImage, savedState, before, after)
    }

    private fun report(context: Context, error: Exception) {
        DragShareLog.w("DragShare/BigBangWindow", "window action failed", error)
        Toast.makeText(context, "分词窗口操作失败：${error.message}", Toast.LENGTH_LONG).show()
    }
}

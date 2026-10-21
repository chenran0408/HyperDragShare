package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.hardware.display.DisplayManager
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
    const val METHOD_COMPLETE_OCR = "complete_wechat_ocr"
    const val METHOD_ABORT_OCR = "abort_wechat_ocr"
    private val main = Handler(Looper.getMainLooper())
    private var active: InteractiveOverlayWindow? = null
    private var activeSession: BigBangPanelSession? = null
    private var activeHost: android.app.Activity? = null
    private var activePressId = -1L
    private var activeTouchX = -1
    private var activeTouchY = -1
    private var generation = 0L

    @JvmStatic fun request(context: Context, input: Bundle?): Bundle {
        val args = requireNotNull(input) { "缺少分词窗口请求" }
        val text = args.getString("text").orEmpty()
        val ocrPending = args.getBoolean("ocr_pending")
        require(ocrPending || text.isNotBlank()) { "没有可分词的文字" }
        check(Settings.canDrawOverlays(context)) { "请先在模块设置中允许悬浮窗权限" }
        val x = args.getInt("x", -1)
        val y = args.getInt("y", -1)
        val before = args.getString("before_text")
        val after = args.getString("after_text")
        val pressConfirmedAt = args.getLong("press_confirmed_at")
        val copyDirect = args.getBoolean("copy_direct_source")
        val pressId = args.getLong("press_id", -1L)
        main.post { open(context.applicationContext, text, x, y, before = before, after = after,
            pressConfirmedAt = pressConfirmedAt, copyDirect = copyDirect, ocrPending = ocrPending,
            pressId = pressId) }
        return Bundle().apply { putBoolean("accepted", true) }
    }

    /** Text captured over system Settings uses this Activity's window token. */
    @JvmStatic fun requestHosted(activity: android.app.Activity, input: Bundle?): Boolean {
        val args = input ?: return false
        val text = args.getString("text").orEmpty()
        if (text.isBlank() || activity.isFinishing || activity.isDestroyed) return false
        main.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            open(activity, text, args.getInt("x", -1), args.getInt("y", -1),
                before = args.getString("before_text"), after = args.getString("after_text"),
                pressConfirmedAt = args.getLong("press_confirmed_at"),
                copyDirect = args.getBoolean("copy_direct_source"), host = activity)
        }
        return true
    }

    @JvmStatic fun dismissHosted(activity: android.app.Activity) {
        main.post { if (activeHost === activity) active?.dismiss() }
    }

    @JvmStatic fun completeOcr(input: Bundle?): Bundle {
        val pressId = input?.getLong("press_id", -1L) ?: -1L
        val text = input?.getString("text").orEmpty()
        require(pressId >= 0L && text.isNotBlank()) { "微信识别结果无效" }
        main.post {
            val session = activeSession
            if (activePressId != pressId || session == null) return@post
            activePressId = -1L
            DragShareLog.i("DragShare/Press", "WeChat OCR result delivered to existing panel characters=${text.length}")
            session.open(text, activeTouchX, activeTouchY)
        }
        return Bundle().apply { putBoolean("accepted", true) }
    }

    @JvmStatic fun abortOcr(input: Bundle?): Bundle {
        val pressId = input?.getLong("press_id", -1L) ?: -1L
        main.post {
            if (pressId >= 0L && activePressId == pressId) {
                activePressId = -1L
                active?.dismiss()
            }
        }
        return Bundle().apply { putBoolean("accepted", true) }
    }

    @JvmStatic fun showText(context: Context, text: String, x: Int, y: Int): Boolean {
        return showTextWithAdjacent(context, text, x, y, null, null)
    }

    @JvmStatic fun showTextWithAdjacent(context: Context, text: String, x: Int, y: Int,
        before: String?, after: String?): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "请先在模块设置中允许悬浮窗权限", Toast.LENGTH_LONG).show()
            return false
        }
        if (text.isBlank()) return false
        main.post { open(context.applicationContext, text, x, y, before = before, after = after) }
        return true
    }

    private fun open(app: Context, text: String, x: Int, y: Int, savedState: Bundle? = null,
        before: String? = null, after: String? = null, pressConfirmedAt: Long = 0L, copyDirect: Boolean = false,
        ocrPending: Boolean = false, pressId: Long = -1L, host: android.app.Activity? = null) {
        if (app.getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) {
            Toast.makeText(app, "请解锁后打开分词窗口", Toast.LENGTH_SHORT).show()
            return
        }
        if (activeHost === host) activeHost = null
        active?.dismiss()
        val windowGeneration = ++generation
        val display = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val context: Context = host ?: app.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        lateinit var overlay: InteractiveOverlayWindow
        val screenOff = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = overlay.dismiss()
        }
        lateinit var session: BigBangPanelSession
        session = BigBangPanelSession(context, object : BigBangPanelSession.Actions {
            override fun dismiss() = overlay.dismiss()
            override fun collapse() = overlay.dismiss()
            override fun searchDirect(text: String) {
                SearchOverlayWindow.show(context, overlay.window.decorView, text, BoomActionHandler.SEARCH_WEB,
                    openExternal = { launch(CopyDirectBrowser.route(app, it)) })
            }
            override fun search(text: String, type: Int) {
                SearchOverlayWindow.show(context, overlay.window.decorView, text, type,
                    openExternal = { launch(it) })
            }
            override fun openExternal(intent: Intent) = launch(CopyDirectBrowser.route(app, intent))
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
        overlay = InteractiveOverlayWindow(context,
            if (host != null) OverlayWindowPolicy.hosted() else OverlayWindowPolicy.application(),
            onDismissed = {
                session.close()
                app.unregisterReceiver(screenOff)
                if (active === overlay) {
                    active = null
                    activeSession = null
                    activePressId = -1L
                }
                if (host != null && activeHost === host) {
                    activeHost = null
                    host.finish()
                }
            },
            onFailure = { report(app, it) },
        ) { session.Content() }
        app.registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        active = overlay
        activeSession = session
        activeHost = host
        activePressId = if (ocrPending) pressId else -1L
        activeTouchX = x
        activeTouchY = y
        if (overlay.show(pressConfirmedAt)) {
            if (ocrPending) session.showOcrProgress(x, y)
            else {
                session.open(text, x, y, savedState, before, after)
                if (copyDirect) session.showDirectActions(text)
            }
        }
    }

    private fun report(context: Context, error: Exception) {
        DragShareLog.w("DragShare/BigBangWindow", "window action failed", error)
        Toast.makeText(context, "分词窗口操作失败：${error.message}", Toast.LENGTH_LONG).show()
    }
}

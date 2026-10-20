package com.leaf.hyperdragshare.codex

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import android.widget.Toast

/** Requests enter through the provider's module/portal UID check; UI stays in the module process. */
object BigBangOverlayLauncher {
    const val METHOD_OPEN = "open_bigbang_window"
    private val main = Handler(Looper.getMainLooper())
    private var active: InteractiveOverlayWindow? = null

    @JvmStatic fun request(context: Context, input: Bundle?): Bundle {
        val text = input?.getString("text")
        require(!text.isNullOrBlank()) { "没有可分词的文字" }
        check(Settings.canDrawOverlays(context)) { "请先在模块设置中允许“原页分词悬浮窗”权限" }
        val x = input.getInt("x", -1)
        val y = input.getInt("y", -1)
        main.post { open(context.applicationContext, text, x, y) }
        return Bundle().apply { putBoolean("accepted", true) }
    }

    private fun open(app: Context, text: String, x: Int, y: Int) {
        active?.dismiss()
        val display = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val context = app.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        lateinit var overlay: InteractiveOverlayWindow
        val session = BigBangPanelSession(context, object : BigBangPanelSession.Actions {
            override fun dismiss() = overlay.dismiss()
            override fun search(text: String, type: Int) {
                launch(Intent(context, SearchOverlayActivity::class.java)
                    .putExtra(Intent.EXTRA_TEXT, text)
                    .putExtra(SearchOverlayActivity.EXTRA_SEARCH_TYPE, type))
            }
            override fun share(text: String) {
                launch(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    this.type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }, null))
            }
            override fun reopenOcr(uri: Uri) {
                launch(Intent(context, ImageOcrActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
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
            override fun saveToStash(text: String, selectedOnly: Boolean) {
                Thread({
                    try {
                        StashStore(app).use { it.addText(text) }
                        main.post { Toast.makeText(app, "文字已存入中转站", Toast.LENGTH_SHORT).show() }
                    } catch (error: java.io.IOException) {
                        main.post { report(app, error) }
                    } catch (error: android.database.sqlite.SQLiteException) {
                        main.post { report(app, error) }
                    } catch (error: SecurityException) {
                        main.post { report(app, error) }
                    }
                }, "bigbang-stash").start()
            }
        })
        overlay = InteractiveOverlayWindow(context, OverlayWindowPolicy.application(),
            onDismissed = {
                session.close()
                if (active === overlay) active = null
            },
            onFailure = { report(app, it) },
        ) { session.Content() }
        active = overlay
        if (overlay.show()) session.open(text, x, y, null)
    }

    private fun report(context: Context, error: Exception) {
        DragShareLog.w("DragShare/BigBangWindow", "window action failed", error)
        Toast.makeText(context, "分词窗口操作失败：${error.message}", Toast.LENGTH_LONG).show()
    }
}

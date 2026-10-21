package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import java.io.IOException
import java.util.concurrent.Executors

/** Own text drops so a chat application's native receiver cannot silently send the text. */
internal object StashTextDropWindow {
    private const val MIME = "application/vnd.hyperdragshare.editor-text"
    private val worker = Executors.newSingleThreadExecutor()

    fun handles(clip: ClipData) = clip.description.hasMimeType(MIME)

    fun start(source: View, text: String, shadow: View.DragShadowBuilder,
        startDrag: (View, ClipData, View.DragShadowBuilder, Int) -> Boolean) {
        Session(source, text, shadow, startDrag).start()
    }

    private class Session(private val source: View, private val text: String,
        private val shadow: View.DragShadowBuilder,
        private val startDrag: (View, ClipData, View.DragShadowBuilder, Int) -> Boolean) {
        private val app = source.context.applicationContext
        private val context = app.createDisplayContext(source.display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        private val manager = context.getSystemService(WindowManager::class.java)
        private val target = View(context)
        private var attached = false
        private var drop: Pair<Float, Float>? = null

        fun start() {
            target.setOnDragListener { _, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> event.clipDescription?.hasMimeType(MIME) == true
                    DragEvent.ACTION_DROP -> {
                        val location = IntArray(2)
                        target.getLocationOnScreen(location)
                        drop = Pair(event.x + location[0], event.y + location[1])
                        // Native acceptance ends the shadow; insertion is confirmed separately.
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        close()
                        drop?.let { insert(it.first, it.second) }
                        true
                    }
                    else -> true
                }
            }
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                setFitInsetsTypes(0)
                title = "HyperDragShare text drop"
            }
            try {
                manager.addView(target, params)
                attached = true
                // Wait for this window's input surface, without replacing the content being held.
                target.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        target.viewTreeObserver.removeOnPreDrawListener(this)
                        target.post {
                            if (!attached) return@post
                            // The actual text never enters global ClipData, even if a receiver ignores MIME.
                            val clip = ClipData("填入文字", arrayOf(MIME),
                                ClipData.Item(Intent("${BuildConfig.APPLICATION_ID}.EDITOR_TEXT")))
                            try {
                                if (!source.isAttachedToWindow || !startDrag(source, clip, shadow, View.DRAG_FLAG_GLOBAL)) {
                                    close()
                                    failed(IOException("系统未能开始拖拽"))
                                }
                            } catch (error: SecurityException) {
                                close()
                                failed(error)
                            }
                        }
                        return true
                    }
                })
            } catch (error: WindowManager.BadTokenException) {
                failed(error)
            } catch (error: SecurityException) {
                failed(error)
            }
        }

        private fun insert(x: Float, y: Float) {
            worker.execute {
                try {
                    SharedRootNodeCapture.open(app, true).use { it.insertTextAt(x, y, text) }
                    source.post { StashOverlayPanel.textDropFinished(source, null) }
                } catch (error: IOException) {
                    source.post { failed(error) }
                } catch (error: SecurityException) {
                    source.post { failed(error) }
                }
            }
        }

        private fun failed(error: Exception) = StashOverlayPanel.textDropFinished(source, error)

        private fun close() {
            if (!attached) return
            attached = false
            target.setOnDragListener(null)
            // Finish detaching our input surface before querying the destination window.
            manager.removeViewImmediate(target)
        }
    }
}

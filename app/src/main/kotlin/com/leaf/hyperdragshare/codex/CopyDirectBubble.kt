package com.leaf.hyperdragshare.codex

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Cut
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** A short-lived copy action, confined to its own bounds and never taking keyboard focus. */
object CopyDirectBubble {
    private const val TAG = "DragShare/CopyBubble"
    private val main = Handler(Looper.getMainLooper())
    private var active: InteractiveOverlayWindow? = null

    @JvmStatic fun request(context: Context, text: String): Bundle {
        require(text.isNotBlank()) { "缺少复制的文字" }
        check(Settings.canDrawOverlays(context)) { "请先允许模块显示悬浮窗" }
        main.post { show(context.applicationContext, text) }
        return Bundle().apply { putBoolean("accepted", true) }
    }

    internal fun dismiss() {
        active?.dismiss()
    }

    private fun show(app: Context, text: String) {
        if (app.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        dismiss()
        val display = app.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val context = app.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        val destination = directTextDestination(text)
        val density = context.resources.displayMetrics.density
        val manager = context.getSystemService(WindowManager::class.java)
        val statusInset = manager.currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        lateinit var overlay: InteractiveOverlayWindow
        val timeout = Runnable { overlay.dismiss() }
        val screenOff = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = overlay.dismiss()
        }
        fun openText() {
            overlay.dismiss()
            BigBangOverlayLauncher.request(app, Bundle().apply {
                putString("text", text)
                putBoolean("copy_direct_source", true)
            })
        }
        fun openAction() {
            if (destination == null) {
                openText()
                return
            }
            try {
                app.startActivity(CopyDirectBrowser.route(app, destination.intent)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                overlay.dismiss()
                DragShareLog.i(TAG, "action opened kind=${destination.kind}")
            } catch (error: ActivityNotFoundException) {
                failed(app, error)
            } catch (error: SecurityException) {
                failed(app, error)
            }
        }
        overlay = InteractiveOverlayWindow(context, OverlayWindowPolicy.application(),
            onDismissed = {
                main.removeCallbacks(timeout)
                app.unregisterReceiver(screenOff)
                if (active === overlay) active = null
            },
            onFailure = { failed(app, it) },
        ) {
            val dark = DragShareSettings.readLocal(context).isDarkThemeInComposition()
            MiuixTheme(controller = remember(dark) {
                ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
            }) {
                CopyDirectActionCard(destination,
                    fallbackSummary = "轻点分词与直达",
                    modifier = Modifier.pointerInput(Unit) {
                        var dragDistance = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragDistance = 0f },
                            onDragEnd = { if (kotlin.math.abs(dragDistance) > 48 * density) overlay.dismiss() },
                        ) { change, amount ->
                            change.consume()
                            dragDistance += amount
                        }
                    },
                    onClick = ::openAction,
                    onSegment = if (destination != null) ::openText else null)
            }
        }
        overlay.window.attributes = overlay.window.attributes.apply {
            width = minOf((352 * density).toInt(), manager.currentWindowMetrics.bounds.width() - (32 * density).toInt())
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = statusInset + (12 * density).toInt()
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        }
        overlay.window.decorView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) overlay.dismiss()
            false
        }
        app.registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        active = overlay
        if (overlay.show()) {
            val duration = app.getSystemService(AccessibilityManager::class.java).getRecommendedTimeoutMillis(
                6_000, AccessibilityManager.FLAG_CONTENT_TEXT or AccessibilityManager.FLAG_CONTENT_CONTROLS)
            main.postDelayed(timeout, duration.toLong())
            DragShareLog.i(TAG, "shown kind=${destination?.kind ?: "text"}")
        }
    }

    private fun failed(context: Context, error: RuntimeException) {
        DragShareLog.w(TAG, "copy action failed", error)
        Toast.makeText(context, "复制直达失败：${error.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
internal fun CopyDirectActionCard(
    destination: DirectEntityDestination?,
    modifier: Modifier = Modifier,
    fallbackSummary: String = "",
    onClick: () -> Unit,
    onSegment: (() -> Unit)? = null,
) {
    val title = when (destination?.kind) {
        DirectTextKind.Url -> "已识别链接 · 点击打开"
        DirectTextKind.Email -> "已识别邮箱 · 点击发邮件"
        DirectTextKind.Phone -> "已识别号码 · 点击拨号"
        null -> "复制直达"
    }
    val summary = destination?.intent?.data?.let {
        if (destination.kind == DirectTextKind.Url) it.toString() else it.schemeSpecificPart
    } ?: fallbackSummary
    Card(modifier = modifier.fillMaxWidth().border(1.dp,
        MiuixTheme.colorScheme.primary.copy(alpha = 0.32f), RoundedCornerShape(16.dp))) {
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f).clickable(onClick = onClick).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(destination?.icon ?: MiuixIcons.Search, contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(summary, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            onSegment?.let {
                IconButton(onClick = it, minWidth = 44.dp, minHeight = 44.dp,
                    modifier = Modifier.padding(end = 6.dp)) {
                    Icon(MiuixIcons.Cut, contentDescription = "分词与更多直达动作",
                        tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

package com.leaf.hyperdragshare.codex

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Short-lived system notification for text copied through AICR. */
object CopyDirectNotification {
    private const val TAG = "DragShare/CopyDirectNotification"
    private const val CHANNEL_ID = "copy_direct"
    private const val NOTIFICATION_ID = 21401
    private const val OPEN_REQUEST_CODE = 21401
    private const val SEGMENT_REQUEST_CODE = 21402
    private const val TIMEOUT_MS = 6_000L

    /** Returns accepted=false when Android cannot show the notification. */
    @JvmStatic fun request(context: Context, text: String): Bundle {
        require(text.isNotBlank()) { "缺少复制的文字" }
        val app = context.applicationContext
        if (SearchOverlayWindow.isOpen()) {
            DragShareLog.i(TAG, "suppressed while search overlay is active")
            return Bundle().apply {
                putBoolean("accepted", true)
                putBoolean("suppressed", true)
            }
        }

        if (ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return failure("模块通知权限未授予")

        val manager = app.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return failure("模块通知已关闭")

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "复制直达", NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        description = "复制的链接、邮箱和号码的快捷操作"
                        setSound(null, null)
                        enableVibration(false)
                        setShowBadge(false)
                    },
            )
        }
        val channel = manager.getNotificationChannel(CHANNEL_ID)
            ?: return failure("复制直达通知频道不可用")
        if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
            return failure("复制直达通知频道已关闭")
        }
        if (channel.importance < NotificationManager.IMPORTANCE_HIGH) {
            return failure("复制直达通知频道优先级过低，无法显示悬浮提醒")
        }

        val destination = directTextDestination(text)
        val title = when (destination?.kind) {
            DirectTextKind.Url -> "打开链接"
            DirectTextKind.Email -> "撰写邮件"
            DirectTextKind.Phone -> "拨打号码"
            null -> "文字分词"
        }
        val target = destination?.intent?.data?.let(::targetLabel) ?: text.trim()
        val mainIntent = try {
            if (destination == null) {
                TextSegmentationActivity.createIntent(text)
            } else {
                CopyDirectBrowser.route(app, destination.intent)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (error: ActivityNotFoundException) {
            DragShareLog.w(TAG, "unable to route copied text", error)
            return failure("复制直达目标不可用：${error.message ?: "无法打开"}")
        }
        val contentIntent = PendingIntent.getActivity(
            app,
            OPEN_REQUEST_CODE,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_copy)
            .setContentTitle(title)
            .setContentText(target.take(MAX_SUMMARY_LENGTH))
            .setStyle(NotificationCompat.BigTextStyle().bigText(target.take(MAX_SUMMARY_LENGTH)))
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setAllowSystemGeneratedContextualActions(false)
            .setTimeoutAfter(TIMEOUT_MS)

        if (destination != null) {
            val segmentIntent = PendingIntent.getActivity(
                app,
                SEGMENT_REQUEST_CODE,
                TextSegmentationActivity.createIntent(text),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_text_segment, "分词", segmentIntent).build(),
            )
        }

        return try {
            manager.notify(NOTIFICATION_ID, builder.build())
            DragShareLog.i(TAG, "posted kind=${destination?.kind ?: "text"}")
            Bundle().apply {
                putBoolean("accepted", true)
                putInt("notification_id", NOTIFICATION_ID)
            }
        } catch (error: SecurityException) {
            DragShareLog.w(TAG, "notification permission rejected", error)
            failure("模块通知权限未授予：${error.message ?: "系统拒绝"}")
        } catch (error: RuntimeException) {
            DragShareLog.w(TAG, "notification could not be posted", error)
            failure("复制直达通知发送失败：${error.message ?: "系统错误"}")
        }
    }

    @JvmStatic fun dismiss(context: Context) {
        context.applicationContext.getSystemService(NotificationManager::class.java)
            ?.cancel(NOTIFICATION_ID)
    }

    private fun targetLabel(uri: Uri): String = when (uri.scheme?.lowercase()) {
        "http", "https" -> uri.toString()
        "mailto" -> uri.schemeSpecificPart
        "tel" -> uri.schemeSpecificPart
        else -> uri.toString()
    }

    private fun failure(message: String) = Bundle().apply {
        putBoolean("accepted", false)
        putString("error", message)
    }

    private const val MAX_SUMMARY_LENGTH = 160
}

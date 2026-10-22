package com.leaf.hyperdragshare.codex

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle

/** Creates actions for AICR's native copy-direct card; it never posts a notification. */
object CopyDirectActions {
    const val EXTRA_OPEN_PENDING_INTENT = "native_open_pending_intent"
    const val EXTRA_SEGMENT_PENDING_INTENT = "native_segment_pending_intent"
    const val EXTRA_OPEN_INTENT_URI = "native_open_intent_uri"
    const val EXTRA_SEGMENT_INTENT_URI = "native_segment_intent_uri"
    const val EXTRA_ICON = "native_copy_icon"
    const val EXTRA_TITLE = "native_copy_title"
    const val EXTRA_CONTENT = "native_copy_content"
    const val EXTRA_KIND = "native_copy_kind"
    const val EXTRA_FAILURE = "native_copy_failure"

    private const val OPEN_REQUEST_CODE = 21401
    private const val SEGMENT_REQUEST_CODE = 21402
    private const val MAX_SUMMARY_LENGTH = 160

    /** Returns PendingIntents and MIUI focus-action URIs to splice into AICR's original card. */
    @JvmStatic fun prepare(context: Context, text: String): Bundle {
        require(text.isNotBlank()) { "缺少复制的文字" }
        val app = context.applicationContext
        if (SearchOverlayWindow.isOpen()) {
            return Bundle().apply {
                putBoolean("accepted", true)
                putBoolean("suppressed", true)
            }
        }

        val destination = directTextDestination(text)
        val segmentIntent = TextSegmentationActivity.createIntent(text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        var failureMessage: String? = null
        val mainIntent = if (destination == null) segmentIntent else try {
            CopyDirectBrowser.route(app, destination.intent)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } catch (error: ActivityNotFoundException) {
            DragShareLog.w("DragShare/CopyDirectActions",
                "selected browser unavailable; native card will show an error", error)
            failureMessage = "所选浏览器不可用"
            null
        }
        val title = when {
            failureMessage != null -> failureMessage
            destination == null -> "文字分词"
            destination.kind == DirectTextKind.Url -> "打开链接"
            destination.kind == DirectTextKind.Email -> "撰写邮件"
            else -> "拨打号码"
        }
        val target = destination?.intent?.data?.let(::targetLabel) ?: text.trim()
        val openPendingIntent = mainIntent?.let {
            PendingIntent.getActivity(
                app,
                OPEN_REQUEST_CODE,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val segmentPendingIntent = PendingIntent.getActivity(
            app,
            SEGMENT_REQUEST_CODE,
            segmentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = Icon.createWithResource(app, R.drawable.ic_copy)
        return Bundle().apply {
            putBoolean("accepted", true)
            if (openPendingIntent != null) putParcelable(EXTRA_OPEN_PENDING_INTENT, openPendingIntent)
            putParcelable(EXTRA_SEGMENT_PENDING_INTENT, segmentPendingIntent)
            if (mainIntent != null) putString(EXTRA_OPEN_INTENT_URI,
                mainIntent.toUri(Intent.URI_INTENT_SCHEME))
            putString(EXTRA_SEGMENT_INTENT_URI, segmentIntent.toUri(Intent.URI_INTENT_SCHEME))
            putParcelable(EXTRA_ICON, icon)
            putString(EXTRA_TITLE, title)
            putString(EXTRA_CONTENT, target.take(MAX_SUMMARY_LENGTH))
            putString(EXTRA_KIND, destination?.kind?.name ?: "Text")
            if (failureMessage != null) putString(EXTRA_FAILURE, failureMessage)
        }
    }

    private fun targetLabel(uri: Uri): String = when (uri.scheme?.lowercase()) {
        "http", "https" -> uri.toString()
        "mailto" -> uri.schemeSpecificPart
        "tel" -> uri.schemeSpecificPart
        else -> uri.toString()
    }
}

package com.leaf.hyperdragshare.codex

import android.content.ClipData
import android.os.Bundle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SidebarDragProtocolTest {
    @Test fun absentOpenCapabilityDefaultsToLegacyAndExplicitCapabilityEnablesShadowV1() {
        assertEquals(false, sidebarHostSupportsShadowV1(Bundle()))
        assertEquals(true, sidebarHostSupportsShadowV1(Bundle().apply {
            putBoolean(StashSurfaceHost.SIDEBAR_SHADOW_V1_CAPABILITY, true)
        }))
    }

    @Test fun absentCapabilityUsesBeta12StartDragTransaction() {
        val host = RecordingHost()
        startSidebarDrag(host, clip(), 1, null, callback())

        assertEquals(1, host.legacyCalls)
        assertEquals(0, host.shadowCalls)
    }

    @Test fun advertisedCapabilityUsesAppendedShadowTransaction() {
        val host = RecordingHost()
        val payload = StashDragShadowPayload(byteArrayOf(9, 8), 2, 1, 20, 10, 1, 0)
        startSidebarDrag(host, clip(), 1, payload, callback())

        assertEquals(0, host.legacyCalls)
        assertEquals(1, host.shadowCalls)
        assertArrayEquals(payload.png, host.receivedPng)
    }

    private fun clip() = ClipData.newPlainText("stash", "sample")

    private fun callback() = object : ISidebarDragCallback.Stub() {
        override fun onStarted(started: Boolean) = Unit
        override fun onEnded(accepted: Boolean) = Unit
    }

    private class RecordingHost : ISidebarHost.Stub() {
        var legacyCalls = 0
        var shadowCalls = 0
        var receivedPng: ByteArray? = null

        override fun requestClose() = Unit

        override fun startDrag(clip: ClipData?, flags: Int, callback: ISidebarDragCallback?) {
            legacyCalls++
        }

        override fun startDragWithShadow(
            clip: ClipData?,
            flags: Int,
            shadowPng: ByteArray?,
            bitmapWidthPx: Int,
            bitmapHeightPx: Int,
            shadowWidthPx: Int,
            shadowHeightPx: Int,
            scaledHotspotX: Int,
            scaledHotspotY: Int,
            callback: ISidebarDragCallback?,
        ) {
            shadowCalls++
            receivedPng = shadowPng
        }
    }
}

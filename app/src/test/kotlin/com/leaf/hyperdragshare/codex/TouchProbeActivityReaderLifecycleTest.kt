package com.leaf.hyperdragshare.codex

import android.os.Looper
import com.leaf.hyperdragshare.codex.touchprobe.TouchReportReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TouchProbeActivityReaderLifecycleTest {
    @Test
    fun failureFromStoppedReaderCannotReplaceRestartedReaderState() {
        val activity = TouchProbeActivity()
        val readers = mutableListOf<FakeReader>()
        activity.readerFactory = { listener -> FakeReader(listener).also(readers::add) }

        activity.startProbe()
        val oldReader = readers.single()
        activity.startProbe()
        val currentReader = readers.last()

        assertTrue(oldReader.closed)
        assertTrue(activity.currentUiState.running)
        oldReader.listener.onFailure("old failure", null)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(activity.currentUiState.running)
        assertEquals("正在查找触控服务与算法库…", activity.currentUiState.status)

        currentReader.listener.onFailure("current failure", null)
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(activity.currentUiState.running)
        assertTrue(activity.currentUiState.status.contains("current failure"))
    }

    @Test
    fun queuedFailureCannotRestoreRunningStateAfterStop() {
        val activity = TouchProbeActivity()
        val readers = mutableListOf<FakeReader>()
        activity.readerFactory = { listener -> FakeReader(listener).also(readers::add) }
        activity.startProbe()

        readers.single().listener.onFailure("late failure", null)
        activity.stopProbe("已停止采样")
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(activity.currentUiState.running)
        assertEquals("已停止采样", activity.currentUiState.status)
        assertTrue(readers.single().closed)
    }

    private class FakeReader(
        val listener: TouchReportReader.Listener,
    ) : TouchProbeReaderHandle {
        var closed = false
        override fun start() = Unit
        override fun close() { closed = true }
    }
}

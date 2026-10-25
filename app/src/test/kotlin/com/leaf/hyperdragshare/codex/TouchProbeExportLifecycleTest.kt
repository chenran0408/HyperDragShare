package com.leaf.hyperdragshare.codex

import android.os.Looper
import com.leaf.hyperdragshare.codex.touchprobe.TouchProbeExporter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import org.robolectric.android.controller.ActivityController

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TouchProbeExportLifecycleTest {
    @Test
    fun failureAndEmptyStatesRemainEligibleForBasicReport() {
        val statuses = listOf(
            "尚未开始",
            "正在查找触控服务与算法库…",
            "连接失败：触控服务不可用",
            "已连接：未知算法库，仅收集诊断数据",
            "算法库哈希不匹配，无法安全采样",
            "触控数据流已中断，采样已停止：读取失败",
        )

        statuses.forEach { status ->
            assertTrue(
                "report export should be eligible for status: $status",
                canExportTouchProbeReport(TouchProbeUiState(status = status)),
            )
        }
    }

    @Test
    fun captureAndExportInProgressDisableExportWithoutChangingVisibilityPolicy() {
        assertFalse(canExportTouchProbeReport(TouchProbeUiState(captureActive = true)))
        assertFalse(canExportTouchProbeReport(TouchProbeUiState(exportInFlight = true)))
        assertTrue(canExportTouchProbeReport(TouchProbeUiState()))
    }

    @Test
    fun repeatedExportRequestSchedulesOnlyOneWorkerAndCompletes() {
        val controller = createActivity()
        val activity = controller.get()
        val pending = mutableListOf<() -> Unit>()
        var exportCalls = 0
        activity.exportTaskLauncher = { task -> pending += task }
        activity.reportRunner = TouchProbeReportRunner { _, _, _, _, _ ->
            exportCalls++
            throw IllegalStateException("synthetic export failure")
        }

        activity.exportReport(TouchProbeExporter.Options.smartDefault())
        activity.exportReport(TouchProbeExporter.Options.smartDefault())

        assertEquals(1, pending.size)
        assertTrue(activity.currentUiState.exportInFlight)
        pending.single().invoke()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, exportCalls)
        assertFalse(activity.currentUiState.exportInFlight)
        controller.destroy()
    }

    @Test
    fun completionQueuedAfterDestroyDoesNotReportIntoClosedActivity() {
        val controller = createActivity()
        val activity = controller.get()
        val pending = mutableListOf<() -> Unit>()
        activity.exportTaskLauncher = { task -> pending += task }
        activity.reportRunner = TouchProbeReportRunner { _, _, _, _, _ ->
            throw IllegalStateException("late synthetic failure")
        }
        activity.exportReport(TouchProbeExporter.Options.smartDefault())
        val toastBeforeDestroy = ShadowToast.getTextOfLatestToast()

        controller.destroy()
        pending.single().invoke()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(toastBeforeDestroy, ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun pickerCancellationClearsBusyStateWithoutSharing() {
        val controller = createActivity()
        val activity = controller.get()
        val pending = mutableListOf<() -> Unit>()
        val pickerRequests = mutableListOf<TouchProbeExporter.Result>()
        activity.exportTaskLauncher = { task -> pending += task }
        activity.reportRunner = TouchProbeReportRunner { _, _, _, _, _ -> textReportResult() }
        activity.touchReportSavePicker = { pickerRequests += it }

        activity.exportReport(TouchProbeExporter.Options.smartDefault())
        pending.single().invoke()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, pickerRequests.size)
        assertTrue(activity.currentUiState.exportInFlight)
        activity.onTouchReportSaveResult(null)
        activity.onTouchReportSaveResult(null)

        assertFalse(activity.currentUiState.exportInFlight)
        assertEquals("已取消保存，未分享触控报告", activity.currentUiState.status)
        controller.destroy()
    }

    @Test
    fun latePickerCallbackAfterExitIsIgnored() {
        val controller = createActivity()
        val activity = controller.get()
        val pending = mutableListOf<() -> Unit>()
        activity.exportTaskLauncher = { task -> pending += task }
        activity.reportRunner = TouchProbeReportRunner { _, _, _, _, _ -> textReportResult() }
        activity.touchReportSavePicker = { }
        activity.exportReport(TouchProbeExporter.Options.smartDefault())
        pending.single().invoke()
        shadowOf(Looper.getMainLooper()).idle()
        val statusBeforeExit = activity.currentUiState.status

        controller.destroy()
        activity.onTouchReportSaveResult(null)

        assertEquals(statusBeforeExit, activity.currentUiState.status)
        assertFalse(activity.currentUiState.exportInFlight)
    }

    private fun createActivity(): ActivityController<TouchProbeActivity> {
        val controller = Robolectric.buildActivity(TouchProbeActivity::class.java)
        controller.get().updateChannelForGate = "beta"
        return controller.create()
    }

    private fun textReportResult(): TouchProbeExporter.Result {
        val constructor = TouchProbeExporter.Result::class.java.getDeclaredConstructor(
            File::class.java,
            TouchProbeExporter.PackageKind::class.java,
            Boolean::class.javaPrimitiveType!!,
            Boolean::class.javaPrimitiveType!!,
            TouchProbeExporter.TouchLibraryCopy::class.java,
            String::class.java,
        )
        constructor.isAccessible = true
        return constructor.newInstance(
            File("touch-report.txt"),
            TouchProbeExporter.PackageKind.TEXT_REPORT,
            false,
            false,
            null,
            "",
        )
    }
}

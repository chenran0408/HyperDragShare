package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticNavigationTest {
    @Test
    fun legacyEntryIdsResolveToUnifiedDiagnosticsDestination() {
        listOf(
            "log.export",
            "gesture_compatibility_report",
            "gesture_feedback",
            "touch.probe",
        ).forEach { assertTrue(isUnifiedDiagnosticsEntryId(it)) }

        assertTrue(isUnifiedDiagnosticsEntryId("problem.diagnostics"))
        assertFalse(isUnifiedDiagnosticsEntryId("log.level"))
    }

    @Test
    fun canonicalEntryIsFoundByAllPreviousSearchTerms() {
        var opened = false
        val entry = unifiedDiagnosticsEntry { opened = true }

        listOf("日志", "报告", "反馈", "触控").forEach { query ->
            assertTrue("expected search match for $query", entry.matches(query))
        }
        assertTrue(isUnifiedDiagnosticsEntryId(entry.id))
        assertEquals("通用设置 › 工具与诊断 › 问题反馈与诊断", entry.path)
        entry.open?.invoke()
        assertTrue(opened)
    }
}

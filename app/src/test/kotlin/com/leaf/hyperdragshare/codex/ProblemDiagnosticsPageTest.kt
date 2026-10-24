package com.leaf.hyperdragshare.codex

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ProblemDiagnosticsPageTest {
    @Test
    fun exportSessionRejectsRepeatGenerationUntilCurrentOperationFinishes() {
        val session = DiagnosticExportSession()

        val first = session.begin()
        assertNotNull(first)
        assertNull(session.begin())
        assertTrue(session.isActive(first!!))

        assertTrue(session.finish(first))
        val second = session.begin()
        assertNotNull(second)
        assertTrue(second!! > first)
    }

    @Test
    fun saveAndShareHandlerReportsSaveAndChooserFailures() = runBlocking {
        val session = DiagnosticExportSession()
        val saveFailureToken = session.begin()!!
        val saveFailure = session.saveAndShare(
            saveFailureToken,
            save = { throw IOException("provider details") },
            share = { throw AssertionError("share must not run after save failure") },
        )
        assertEquals(DiagnosticExportOutcome.SAVE_FAILED, saveFailure)

        val shareFailureToken = session.begin()!!
        val shareFailure = session.saveAndShare(
            shareFailureToken,
            save = { },
            share = { throw IllegalStateException("chooser details") },
        )
        assertEquals(DiagnosticExportOutcome.SHARE_FAILED, shareFailure)
        assertNull(session.activeToken)
    }

    @Test
    fun savePickerResultIsConsumedOnceAndOldTokenCannotConsumeNewRequest() {
        val session = DiagnosticExportSession()
        val first = session.begin()!!
        assertTrue(session.consumeSavePickerResult(first))
        assertFalse(session.consumeSavePickerResult(first))
        session.finish(first)

        val second = session.begin()!!
        assertFalse(session.consumeSavePickerResult(first))
        assertTrue(session.consumeSavePickerResult(second))
    }

    @Test
    fun successfulSaveAndShareConsumesCurrentOperationOnce() = runBlocking {
        val session = DiagnosticExportSession()
        val token = session.begin()!!
        var saves = 0
        var shares = 0

        val outcome = session.saveAndShare(
            token,
            save = { saves++ },
            share = { shares++ },
        )

        assertEquals(DiagnosticExportOutcome.SAVED_CHOOSER_OPENED, outcome)
        assertEquals(1, saves)
        assertEquals(1, shares)
        assertFalse(session.isActive(token))
    }

    @Test
    fun cancelledOrExitedOperationIgnoresLatePickerCallback() {
        val session = DiagnosticExportSession()
        val cancelled = session.begin()!!
        session.cancelBeforeSavePicker(cancelled)
        assertFalse(session.isActive(cancelled))
        assertFalse(session.finish(cancelled))

        val exited = session.begin()!!
        session.cancelActive()
        assertFalse(session.isActive(exited))
        assertFalse(session.finish(exited))
    }

    @Test
    fun lateSaveCallbackAfterCancelOrExitDoesNotSaveOrShare() = runBlocking {
        val session = DiagnosticExportSession()
        val cancelled = session.begin()!!
        session.cancelBeforeSavePicker(cancelled)
        var staleSaveRan = false
        var staleShareRan = false
        assertEquals(
            DiagnosticExportOutcome.STALE,
            session.saveAndShare(cancelled, { staleSaveRan = true }, { staleShareRan = true }),
        )

        val exited = session.begin()!!
        session.cancelActive()
        assertEquals(
            DiagnosticExportOutcome.STALE,
            session.saveAndShare(exited, { staleSaveRan = true }, { staleShareRan = true }),
        )
        assertFalse(staleSaveRan)
        assertFalse(staleShareRan)
    }

    @Test
    fun exportedStateSummaryContainsOnlySafeEvidenceEnums() {
        val snapshot = ActivationSnapshot(
            root = ActivationEvidence.READY,
            injection = ActivationEvidence.UNKNOWN,
            portalService = ActivationEvidence.READY,
            portalRoot = ActivationEvidence.UNAVAILABLE,
            rootInput = ActivationEvidence.CHECKING,
            pressReader = ActivationEvidence.NOT_APPLICABLE,
            overlayPermission = ActivationEvidence.READY,
            thumbTriggerEnabled = false,
            copyDirectEnabled = false,
            rootInputError = "private raw failure",
            pressReaderError = "private component details",
        )

        val summary = diagnosticStateSummary(snapshot)
        assertTrue(summary.contains("root=READY"))
        assertTrue(summary.contains("portal_root=UNAVAILABLE"))
        assertFalse(summary.contains("private raw failure"))
        assertFalse(summary.contains("private component details"))
        assertTrue(summary.contains("overall=${snapshot.overallState}"))
    }
}

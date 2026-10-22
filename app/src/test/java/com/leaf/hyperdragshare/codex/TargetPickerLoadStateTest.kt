package com.leaf.hyperdragshare.codex

import com.leaf.hyperdragshare.gestures.config.AppTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetPickerLoadStateTest {
    private val existingRows = listOf(
        GestureTargetRow(AppTarget("example.app/.Main"), "Example", "example.app", null),
    )

    @Test
    fun refreshKeepsPreviouslyLoadedRowsVisible() {
        val refreshed = TargetPickerLoadState(existingRows, refreshing = false)
            .beginRefresh()

        assertTrue(refreshed.refreshing)
        assertEquals(existingRows, refreshed.rows)
        assertNull(refreshed.error)
    }

    @Test
    fun failureKeepsLastRowsAndAllowsLaterRetry() {
        val failed = TargetPickerLoadState(existingRows, refreshing = true).failed()

        assertFalse(failed.refreshing)
        assertEquals(existingRows, failed.rows)
        assertEquals(TargetPickerLoadError.UNAVAILABLE, failed.error)

        val retry = failed.beginRefresh()
        assertTrue(retry.refreshing)
        assertEquals(existingRows, retry.rows)
        assertNull(retry.error)
    }

    @Test
    fun successfulRefreshReplacesSnapshotAndClearsError() {
        val newRows = listOf(
            GestureTargetRow(AppTarget("new.app/.Main"), "New", "new.app", null),
        )
        val loaded = TargetPickerLoadState(existingRows).failed().beginRefresh().loaded(newRows)

        assertFalse(loaded.refreshing)
        assertSame(newRows, loaded.rows)
        assertNull(loaded.error)
    }

    @Test
    fun initialFailureStopsSpinnerAndExposesRetryState() {
        val failed = TargetPickerLoadState().failed()

        assertFalse(failed.refreshing)
        assertTrue(failed.rows.isEmpty())
        assertEquals(TargetPickerLoadError.UNAVAILABLE, failed.error)
    }
}

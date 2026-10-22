package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateVersionTest {
    @Test
    fun stableReleaseSortsAfterItsBeta() {
        assertTrue(compareVersionNames("1.7.219-beta.1", "1.7.219")!! < 0)
        assertTrue(compareVersionNames("1.7.219", "1.7.219-beta.1")!! > 0)
    }

    @Test
    fun betaCounterAndStableVersionsSortNumerically() {
        assertTrue(compareVersionNames("1.7.219-beta.2", "1.7.219-beta.10")!! < 0)
        assertTrue(compareVersionNames("1.7.219", "1.7.218")!! > 0)
        assertEquals(0, compareVersionNames("1.7.219+build.2", "1.7.219"))
    }

    @Test
    fun unrelatedLabelsAreRejected() {
        assertNull(compareVersionNames("beta-1", "1.7.219"))
        assertNull(compareVersionNames("1.7.219", "release"))
    }
}

package com.leaf.hyperdragshare.codex

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class OcrNearbyLinesTest {
    @Test fun selectsNearestRowsAndExcludesSourceAndOtherColumn() {
        val source = Rect(100, 200, 300, 280)
        val lines = listOf(
            Rect(100, 40, 300, 70),
            Rect(100, 150, 280, 180),
            Rect(110, 210, 290, 250),
            Rect(330, 281, 500, 310),
            Rect(100, 320, 280, 350),
            Rect(100, 400, 280, 430),
        )
        assertEquals(setOf(1, 4), nearbyOcrLines(lines, source))
    }

    @Test fun missingOrOverlappingBoundsDoNotSelectArbitraryText() {
        val source = Rect(100, 200, 300, 280)
        assertEquals(emptySet<Int>(), nearbyOcrLines(
            listOf(null, Rect(), Rect(100, 180, 300, 220), Rect(310, 300, 400, 330)), source))
    }
}

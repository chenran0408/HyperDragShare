package com.leaf.hyperdragshare.codex

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class OcrNearbyLinesTest {
    @Test fun aWideTextViewDoesNotPullInDistantStatusBarText() {
        assertEquals(setOf(1), nearbyOcrLines(listOf(
            Rect(80, 20, 220, 80), Rect(480, 960, 960, 1010)), Rect(100, 820, 1340, 920)))
    }

    @Test fun targetRomBoxVerticesKeepImageCoordinatesAndExcludeDistantOrOtherColumnRows() {
        val left = AicrOcrResult.Line("LEFT 123", null,
            floatArrayOf(63f, 142f, 63f, 71f, 323f, 71f, 323f, 142f))
        val right = AicrOcrResult.Line("RIGHT 456", null,
            floatArrayOf(611.2418f, 394.03796f, 610.8323f, 321.1358f,
                924.85034f, 319.37164f, 925.2599f, 392.2738f))
        val below = AicrOcrResult.Line("原文附近文字", null,
            floatArrayOf(76f, 632f, 76f, 560f, 443f, 560f, 443f, 632f))
        assertEquals(Rect(610, 319, 926, 395), aicrLineBounds(right))
        assertEquals(emptySet<Int>(), nearbyOcrLines(listOf(left, right, below).map(::aicrLineBounds),
            Rect(76, 84, 318, 131)))
    }

    @Test fun absentGeometryIsUnavailableAndFourValuesAreNotGuessedAsLTRB() {
        assertNull(aicrLineBounds(AicrOcrResult.Line("text", null, null)))
        assertThrows(IllegalArgumentException::class.java) {
            aicrLineBounds(AicrOcrResult.Line("text", null, floatArrayOf(1f, 2f, 3f, 4f)))
        }
    }

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

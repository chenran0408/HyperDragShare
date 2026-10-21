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
    @Test fun consecutiveNearbySelectionsAdvanceBeyondAlreadyInsertedRows() {
        val source = Rect(100, 200, 300, 240)
        val lines = listOf(Rect(100, 80, 300, 110), Rect(100, 140, 300, 170),
            Rect(100, 200, 300, 240), Rect(100, 270, 300, 300), Rect(100, 330, 300, 360))
        val first = nearbyOcrLines(lines, source)
        assertEquals(setOf(1, 3), first)
        val expanded = expandedNearbyOcrBounds(source, lines, first, 400, 500)!!
        assertEquals(Rect(100, 140, 300, 300), expanded)
        val next = nearbyOcrLines(lines, expanded, source.height())
        assertEquals(setOf(0, 4), next)
        assertEquals(Rect(100, 80, 300, 360), expandedNearbyOcrBounds(expanded, lines, next, 400, 500))
    }

    @Test fun expandingTheRangeDoesNotIncreaseTheOriginalDistanceLimit() {
        val expanded = Rect(100, 400, 1340, 900)
        assertEquals(setOf(1), nearbyOcrLines(listOf(
            Rect(80, 20, 220, 80), Rect(480, 930, 960, 970)), expanded, 40))
    }

    @Test fun onlySelectedLocatedImageRowsAdvanceWithoutChangingInputBounds() {
        val source = Rect(100, 100, 300, 140)
        val partlyOutside = Rect(-20, 20, 200, 80)
        val lines = listOf(partlyOutside, null, Rect(5, 5, 5, 10),
            Rect(500, 500, 600, 530), Rect(100, 300, 300, 340))
        assertEquals(Rect(0, 20, 300, 140), expandedNearbyOcrBounds(source, lines, setOf(0, 1, 2, 3), 400, 400))
        assertEquals(Rect(100, 100, 300, 140), source)
        assertEquals(Rect(-20, 20, 200, 80), partlyOutside)
        assertNull(expandedNearbyOcrBounds(source, lines, setOf(1, 2, 3), 400, 400))
    }

    @Test fun selectedRowsArePartitionedInOcrOrderRatherThanSelectionOrder() {
        val texts = listOf("上方第一行", "上方第二行", "未选择", "下方第一行", "下方第二行")
        val bounds = listOf(Rect(100, 80, 300, 110), Rect(100, 150, 300, 180),
            Rect(100, 210, 300, 250), Rect(100, 300, 300, 330), Rect(100, 350, 300, 380))
        assertEquals("上方第一行\n上方第二行" to "下方第一行\n下方第二行",
            splitNearbyOcrSelection(texts, bounds, linkedSetOf(4, 1, 3, 0), Rect(100, 200, 300, 280)))
    }

    @Test fun overlappingAndUnlocatedRowsAreNotGuessedToPrecedeTheSource() {
        assertEquals("恰好位于上方" to "重叠行\n无坐标行\n下方行", splitNearbyOcrSelection(
            listOf("重叠行", "无坐标行", "下方行", "恰好位于上方"),
            listOf(Rect(100, 180, 300, 220), null, Rect(100, 300, 300, 340), Rect(100, 170, 300, 200)),
            linkedSetOf(3, 2, 1, 0), Rect(100, 200, 300, 280)))
    }

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

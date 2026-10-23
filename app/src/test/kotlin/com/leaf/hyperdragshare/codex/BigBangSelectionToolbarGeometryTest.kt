package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertEquals
import org.junit.Test

class BigBangSelectionToolbarGeometryTest {
    @Test
    fun labeledSelectionActionsRemainClearOfTheFirstVisibleWordRow() {
        val toolbarHeight = SELECTION_ACTION_TOOLBAR_HEIGHT_DP
        val toolbarGap = 8
        val reservedTop = toolbarHeight + toolbarGap
        val toolbarTop = selectionToolbarTopY(
            anchorYpx = 0,
            pageTopInsetPx = reservedTop,
            toolbarHeightPx = toolbarHeight,
            gapPx = toolbarGap,
            viewportHeightPx = 240,
        )

        assertEquals(0, toolbarTop)
        assertEquals(toolbarGap, reservedTop - (toolbarTop + toolbarHeight))
    }

    @Test
    fun firstVisibleRowHasAReservedToolbarAreaAboveIt() {
        assertEquals(
            0,
            selectionToolbarTopY(
                anchorYpx = 0,
                pageTopInsetPx = 56,
                toolbarHeightPx = 48,
                gapPx = 8,
                viewportHeightPx = 220,
            ),
        )
    }

    @Test
    fun laterSelectionUsesItsPageRelativeAnchorAfterTheInset() {
        val top = selectionToolbarTopY(
            anchorYpx = 40,
            pageTopInsetPx = 56,
            toolbarHeightPx = 48,
            gapPx = 8,
            viewportHeightPx = 220,
        )

        assertEquals(40, top)
        assertEquals(8, 40 + 56 - (top + 48))
    }

    @Test
    fun selectionScrolledPastTheTopClampsIntoTheReservedArea() {
        val top = selectionToolbarTopY(
            anchorYpx = -12,
            pageTopInsetPx = 56,
            toolbarHeightPx = 48,
            gapPx = 8,
            viewportHeightPx = 120,
        )

        assertEquals(0, top)
        assertEquals(8, 56 - (top + 48))
    }

    @Test
    fun constrainedViewportNeverProducesANegativeToolbarTop() {
        assertEquals(
            0,
            selectionToolbarTopY(-100, 20, 48, 8, 32),
        )
    }
}

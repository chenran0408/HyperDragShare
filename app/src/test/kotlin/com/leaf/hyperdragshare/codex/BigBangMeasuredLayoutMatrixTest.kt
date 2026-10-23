package com.leaf.hyperdragshare.codex

import android.app.Application
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import java.util.TreeSet
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BigBangMeasuredLayoutMatrixTest {
    private data class Fixture(
        val activity: org.robolectric.android.controller.ActivityController<android.app.Activity>,
        val root: View,
        val page: BoomChipPage,
        val viewportHeightPx: Int,
        val reserveTopPx: Int,
    ) {
        fun close() {
            activity.pause().stop().destroy()
        }
    }

    @Test
    @Config(sdk = [35], application = Application::class,
        fontScale = 1.7f, qualifiers = "w360dp-h760dp-port")
    fun realChipsWrapChineseLongUrlAndEmojiOnNarrowPortraitWithLargeSystemFont() {
        val context = RuntimeEnvironment.getApplication()
        val original = Configuration(context.resources.configuration)
        val originalMetrics = DisplayMetrics().apply { setTo(context.resources.displayMetrics) }
        try {
            val fixture = openFixture(widthDp = 360, viewportHeightDp = 760)
            try {
                val chinese = (0 until 42).joinToString(" ") { "中文长段布局检查第${it}行" }
                val url = "https://example.com/" + "路径segment123".repeat(36)
                val emoji = "👩‍👩‍👧‍👦 🇨🇳 👍🏽"
                val text = "$chinese $url $emoji"
                val segments = wordSegments(text)

                assertTrue(fixture.page.initWords(segments, text, -1, -1, -1))
                measure(fixture.root, 360, fixture)

                val words = fixture.root.findViewById<SwipeSelectView>(R.id.boom_content)
                val chips = allChipViews(words)
                val urlChip = chips.single { it.text.toString() == url }
                val familyChip = chips.single { it.text.toString() == "👩‍👩‍👧‍👦" }

                val metrics = fixture.activity.get().resources.displayMetrics
                val expectedTextSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    BigBangSettings.DEFAULT_TEXT_SIZE_SP,
                    metrics,
                )
                val baselineMetrics = DisplayMetrics().apply {
                    density = metrics.density
                    scaledDensity = density // SP baseline at system fontScale 1.0 only.
                }
                val fontScaleOneBaseline = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    BigBangSettings.DEFAULT_TEXT_SIZE_SP,
                    baselineMetrics,
                )
                assertEquals("chip uses the system's SP conversion at the configured font scale",
                    expectedTextSize, urlChip.textSize, 0.01f)
                assertTrue("fontScale=${context.resources.configuration.fontScale}, " +
                    "expectedTextSize=$expectedTextSize, fontScaleOneBaseline=$fontScaleOneBaseline",
                    expectedTextSize > fontScaleOneBaseline)
                val urlRowWidth = (urlChip.parent as ViewGroup).width
                assertTrue(
                    "long URL should wrap in the measured TextView: " +
                        "chipWidth=${urlChip.width}, " +
                        "layoutWidth=${urlChip.layout?.width}, " +
                        "lineCount=${urlChip.lineCount}, " +
                        "paintMeasure=${urlChip.paint.measureText(urlChip.text.toString())}, " +
                        "rowWidth=$urlRowWidth",
                    urlChip.lineCount > 1,
                )
                assertEquals(url, urlChip.text.toString())
                assertEquals("👩‍👩‍👧‍👦", familyChip.text.toString())
                assertFalse("normal chips should not ellipsize", urlChip.ellipsize != null)
                assertTrue(fixture.page.mLayout.rowCount > 1)
                assertRowsFit(words)

                fixture.page.mScroller.scrollTo(0, 0)
                fixture.page.restoreSelectionNow(TreeSet(setOf(0)))
                assertToolbarAboveSelection(fixture, 0)
                fixture.page.mScroller.scrollTo(0, words.height)
                measure(fixture.root, 360, fixture)
                val lastWord = fixture.page.mLayout.wordCount - 1
                fixture.page.restoreSelectionNow(TreeSet(setOf(lastWord)))
                assertToolbarAboveSelection(fixture, lastWord)
            } finally {
                fixture.close()
            }
        } finally {
            context.resources.updateConfiguration(original, originalMetrics)
        }
    }

    @Test
    @Config(sdk = [35], application = Application::class,
        fontScale = 1.0f, qualifiers = "w800dp-h360dp-land")
    fun realScrollableRowsKeepSelectionToolbarClearForFirstAndLastWordsInLandscape() {
        val context = RuntimeEnvironment.getApplication()
        val original = Configuration(context.resources.configuration)
        val originalMetrics = DisplayMetrics().apply { setTo(context.resources.displayMetrics) }
        try {
            val fixture = openFixture(widthDp = 800, viewportHeightDp = 360)
            try {
                val text = (0 until 220).joinToString(" ") { "词块${it}长中文" }
                assertTrue(fixture.page.initWords(wordSegments(text), text, -1, -1, -1))
                measure(fixture.root, 800, fixture)

                val words = fixture.root.findViewById<SwipeSelectView>(R.id.boom_content)
                assertRowsFit(words)
                assertTrue("short landscape viewport should contain scrollable real rows",
                    fixture.page.mScroller.canScrollVertically(1))

                fixture.page.mScroller.scrollTo(0, 0)
                fixture.page.restoreSelectionNow(TreeSet(setOf(0)))
                assertToolbarAboveSelection(fixture, 0)

                fixture.page.mScroller.scrollTo(0, words.height)
                measure(fixture.root, 800, fixture)
                ShadowLooper.shadowMainLooper().idle()
                fixture.page.restoreSelectionNow(TreeSet(setOf(fixture.page.mLayout.wordCount - 1)))
                assertToolbarAboveSelection(fixture, fixture.page.mLayout.wordCount - 1)
                assertTrue(fixture.page.mScroller.canScrollVertically(-1))
            } finally {
                fixture.close()
            }
        } finally {
            context.resources.updateConfiguration(original, originalMetrics)
        }
    }

    @Test
    @Config(sdk = [35], application = Application::class,
        fontScale = 1.0f, qualifiers = "w360dp-h760dp-port")
    fun selectingWordsHighlightsSelectionAndMovesRowsAroundIt() {
        val fixture = openFixture(widthDp = 360, viewportHeightDp = 760)
        try {
            val text = (0 until 48).joinToString(" ") { "词块${it}长中文内容" }
            assertTrue(fixture.page.initWords(wordSegments(text), text, -1, -1, -1))
            measure(fixture.root, 360, fixture)

            val words = fixture.root.findViewById<SwipeSelectView>(R.id.boom_content)
            val selected = TreeSet((9..11).toList())
            val firstSelectedRow = fixture.page.mLayout.getRowForIndex(selected.first())
            val lastSelectedRow = fixture.page.mLayout.getRowForIndex(selected.last())
            assertTrue("fixture selection must be inside the chip list", firstSelectedRow > 0)
            assertTrue("fixture must leave unselected rows below selection",
                lastSelectedRow < words.childCount - 1)

            fixture.page.restoreSelectionNow(selected)
            ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(240))

            val focus = fixture.root.findViewById<View>(R.id.boom_selection_region)
            assertEquals(View.VISIBLE, focus.visibility)
            assertTrue("focus band must cover the selected row span", focus.height > 0)
            val firstSelected = words.getChildAt(firstSelectedRow)
            val lastSelected = words.getChildAt(lastSelectedRow)
            assertTrue("focus band starts above the first selected row",
                focus.y < words.y + firstSelected.top)
            assertTrue("focus band ends below the last selected row",
                focus.y + focus.height > words.y + lastSelected.bottom)
            assertEquals("rows above the selected span move up as in the reference",
                fixture.root.resources.getDimension(R.dimen.chip_row_move_up_offset),
                words.getChildAt(firstSelectedRow - 1).translationY, 0.5f)
            assertEquals("selected rows stay in place", 0f,
                words.getChildAt(firstSelectedRow).translationY, 0.5f)
            assertEquals("rows below the selected span move down as in the reference",
                fixture.root.resources.getDimension(R.dimen.chip_row_move_down_offset),
                words.getChildAt(lastSelectedRow + 1).translationY, 0.5f)
            assertTrue("the moved final rows have matching scroll padding",
                words.paddingBottom >= fixture.root.resources.getDimensionPixelSize(
                    R.dimen.chip_row_move_down_offset))

            fixture.page.handleClick()
            ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(240))
            assertEquals(View.GONE, focus.visibility)
            for (index in 0 until words.childCount) {
                assertEquals("row $index returns after clearing selection", 0f,
                    words.getChildAt(index).translationY, 0.5f)
            }
            assertEquals(0, words.paddingBottom)
        } finally {
            fixture.close()
        }
    }

    @Test
    @Config(sdk = [35], application = Application::class,
        fontScale = 1.0f, qualifiers = "w360dp-h760dp-port")
    fun clearingSelectionBeforeRowAnimationAdvancesCancelsPendingMoves() {
        val fixture = openFixture(widthDp = 360, viewportHeightDp = 760)
        try {
            val text = (0 until 48).joinToString(" ") { "词块${it}长中文内容" }
            assertTrue(fixture.page.initWords(wordSegments(text), text, -1, -1, -1))
            measure(fixture.root, 360, fixture)

            fixture.page.restoreSelectionNow(TreeSet((9..11).toList()))
            val focus = fixture.root.findViewById<View>(R.id.boom_selection_region)
            assertEquals(View.VISIBLE, focus.visibility)

            // Clear immediately, before the row property animators advance a frame.
            assertTrue(fixture.page.handleClick())
            ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(240))

            val words = fixture.root.findViewById<SwipeSelectView>(R.id.boom_content)
            assertEquals(View.GONE, focus.visibility)
            for (index in 0 until words.childCount) {
                assertEquals("row $index stays in place after the pending move is cancelled", 0f,
                    words.getChildAt(index).translationY, 0.5f)
            }
            assertEquals(0, words.paddingBottom)
        } finally {
            fixture.close()
        }
    }

    private fun openFixture(widthDp: Int, viewportHeightDp: Int): Fixture {
        val context = RuntimeEnvironment.getApplication()
        val configuration = Configuration(context.resources.configuration).apply {
            screenWidthDp = widthDp
            screenHeightDp = viewportHeightDp
            smallestScreenWidthDp = minOf(widthDp, viewportHeightDp)
        }
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
        val density = context.resources.displayMetrics.density
        val widthPx = (widthDp * density).roundToInt()
        val viewportHeightPx = (viewportHeightDp * density).roundToInt()
        val reserveTopPx = (56f * density).roundToInt()
        val bottomInsetPx = (4f * density).roundToInt()
        val activity = Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val root = LayoutInflater.from(activity.get()).inflate(R.layout.boom_activity_layout, null, false)
        val page = BoomChipPage(activity.get(), root, false, object : BoomChipPage.Actions {
            override fun dismiss() = Unit
            override fun copy(text: String) = Unit
            override fun search(text: String, type: Int) = Unit
            override fun dragText(source: View, text: String) = false
        })
        activity.get().setContentView(root)
        val fixture = Fixture(activity, root, page,
            viewportHeightPx, reserveTopPx)
        measurePx(root, widthPx, viewportHeightPx - reserveTopPx - bottomInsetPx)
        return fixture
    }

    private fun measure(root: View, widthDp: Int, fixture: Fixture) {
        val density = root.resources.displayMetrics.density
        measurePx(root, (widthDp * density).roundToInt(),
            fixture.viewportHeightPx - fixture.reserveTopPx - (4f * density).roundToInt())
        ShadowLooper.shadowMainLooper().idle()
        // BoomChipPage reflows after receiving the actual content width from layout.
        measurePx(root, (widthDp * density).roundToInt(), root.height)
        ShadowLooper.shadowMainLooper().idle()
        measurePx(root, root.width, root.height)
    }

    private fun measurePx(root: View, widthPx: Int, heightPx: Int) {
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, widthPx, heightPx)
    }

    private fun wordSegments(text: String): IntArray {
        val segments = ArrayList<Int>()
        var start = -1
        text.forEachIndexed { index, character ->
            if (character.isWhitespace()) {
                if (start >= 0) {
                    segments.add(start)
                    segments.add(index - 1)
                    start = -1
                }
            } else if (start < 0) {
                start = index
            }
        }
        if (start >= 0) {
            segments.add(start)
            segments.add(text.lastIndex)
        }
        segments.add(-1)
        return segments.toIntArray()
    }

    private fun allChipViews(words: SwipeSelectView): List<TextView> {
        val chips = ArrayList<TextView>()
        for (rowIndex in 0 until words.childCount) {
            val row = words.getChildAt(rowIndex) as? LinearLayout ?: continue
            for (childIndex in 0 until row.childCount) {
                (row.getChildAt(childIndex) as? TextView)?.let(chips::add)
            }
        }
        return chips
    }

    private fun assertRowsFit(words: SwipeSelectView) {
        assertTrue(words.width > 0)
        for (rowIndex in 0 until words.childCount) {
            val row = words.getChildAt(rowIndex) as? LinearLayout ?: continue
            assertTrue("row $rowIndex wider than the measured content", row.width <= words.width)
            for (childIndex in 0 until row.childCount) {
                val child = row.getChildAt(childIndex)
                assertTrue("chip $rowIndex/$childIndex starts outside its row", child.left >= 0)
                assertTrue("chip $rowIndex/$childIndex overflows its row", child.right <= row.width)
                assertNotNull("chip $rowIndex/$childIndex text layout missing", (child as TextView).layout)
            }
        }
    }

    private fun assertToolbarAboveSelection(fixture: Fixture, wordIndex: Int) {
        val anchorY = fixture.page.selectionActionAnchorY()
        assertTrue("selected row should have a measured anchor", anchorY >= 0)
        val density = fixture.root.resources.displayMetrics.density
        val toolbarHeight = (48f * density).roundToInt()
        val gap = (8f * density).roundToInt()
        val toolbarTop = selectionToolbarTopY(
            anchorYpx = anchorY,
            pageTopInsetPx = fixture.reserveTopPx,
            toolbarHeightPx = toolbarHeight,
            gapPx = gap,
            viewportHeightPx = fixture.viewportHeightPx,
        )
        val rowIndex = fixture.page.mLayout.getRowForIndex(wordIndex)
        val row = fixture.root.findViewById<SwipeSelectView>(R.id.boom_content).getChildAt(rowIndex)
        val actualRowTop = fixture.reserveTopPx + anchorY

        assertTrue("toolbar must remain within the measured panel", toolbarTop >= 0)
        assertTrue(toolbarTop + toolbarHeight <= fixture.viewportHeightPx)
        assertTrue("toolbar must leave the configured gap above the selected row",
            toolbarTop + toolbarHeight + gap <= actualRowTop)
        assertTrue("selected row should have an actual measured height", row.height > 0)
    }
}

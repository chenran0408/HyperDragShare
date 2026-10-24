package com.leaf.hyperdragshare.codex

import androidx.activity.ComponentActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.platform.ComposeView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SettingsCatalogNavigationTest {
    @Test
    fun actualGestureCatalogEntriesMapExactlyOnceAndKeepSearchFocusDestination() {
        val entries = productionGestureEntries()
        val assignedGroups = GESTURE_SETTING_CATEGORIES.flatMap { it.groups }
        assertEquals(SettingModule.GESTURES.groups.toSet(), assignedGroups.toSet())
        assertEquals(assignedGroups.size, assignedGroups.distinct().size)
        assertTrue("production gesture catalog should not be empty", entries.isNotEmpty())
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        entries.forEach { entry ->
            assertNotNull("${entry.id} has a category", gestureCategoryFor(entry))
            assertEquals(1, GESTURE_SETTING_CATEGORIES.count { category ->
                entriesForGestureCategory(entries, category).any { it.id == entry.id }
            })
        }
        GESTURE_SETTING_CATEGORIES.forEach { category ->
            val categorized = entriesForGestureCategory(entries, category)
            assertEquals(categorized.size, categorized.map { it.id }.distinct().size)
        }

        val sideEntry = entries.first { it.title == "启用侧滑列表" }
        assertEquals("side", gestureCategoryFor(sideEntry)?.id)
        assertTrue(sideEntry.matches("侧滑"))
        assertTrue(sideEntry.matches("侧边滑动"))
        assertTrue(entries.any { it.matches("底角斜滑") })
        val visibleCategoryRows = entriesForGestureCategory(entries, GESTURE_SETTING_CATEGORIES.first { it.id == "side" })
            .filter { it.enabled || it.prerequisite == null || it.id == sideEntry.id }
        assertNotNull(settingEntryListIndex(visibleCategoryRows, sideEntry.id))
    }

    @Test
    fun lastListPositionSurvivesStateSnapshotAndSaverRestore() {
        val store = SettingsScrollPositionStore()
        val moduleKey = "module:GESTURES:categories"
        val sideKey = "module:GESTURES:category:side"
        val finalRootState = LazyListState(5, 18)
        val finalSideState = LazyListState(7, 36)
        store.save(moduleKey, finalRootState)
        store.save(sideKey, finalSideState)

        val saverScope = object : SaverScope {
            override fun canBeSaved(value: Any): Boolean = true
        }
        val saved = with(SettingsScrollPositionStore.Saver) { saverScope.save(store) }
        val restored = SettingsScrollPositionStore.Saver.restore(requireNotNull(saved))
            ?: error("scroll position saver did not restore")

        val restoredRoot = restored.createListState(moduleKey)
        val restoredSide = restored.createListState(sideKey)
        assertEquals(5, restoredRoot.firstVisibleItemIndex)
        assertEquals(18, restoredRoot.firstVisibleItemScrollOffset)
        assertEquals(7, restoredSide.firstVisibleItemIndex)
        assertEquals(36, restoredSide.firstVisibleItemScrollOffset)
    }

    private fun productionGestureEntries(): List<SettingEntry> {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val composeView = ComposeView(activity)
        val captured = AtomicReference<List<SettingEntry>?>()
        try {
            activity.setContentView(composeView)
            composeView.setContent {
                val entries = gestureSettingEntries(activity, onManageTargets = {})
                SideEffect { captured.set(entries.filter { it.module == SettingModule.GESTURES }) }
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (System.nanoTime() < deadline && captured.get() == null) {
                ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(16))
            }
            return captured.get() ?: error("Compose did not produce gesture setting entries")
        } finally {
            composeView.disposeComposition()
            controller.pause().stop().destroy()
            ShadowLooper.shadowMainLooper().idle()
        }
    }
}

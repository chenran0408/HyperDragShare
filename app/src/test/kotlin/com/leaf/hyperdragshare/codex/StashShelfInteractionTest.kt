package com.leaf.hyperdragshare.codex

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import androidx.compose.runtime.MutableState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class StashShelfInteractionTest {
    @Test fun backLeavesChildLayersBeforeCollapsingAndKeepsSelection() = withShelf { shelf ->
        val image = entry("image", "group", 0, image = true)
        val ids = setOf(image.id, "other")
        state(shelf, "selectedIds", ids)
        state(shelf, "selectionMode", true)
        state(shelf, "preview", image)
        state(shelf, "openedBatchId", "group")
        state(shelf, "confirmDelete", true)

        call(shelf, "back")
        assertEquals(false, state(shelf, "confirmDelete"))
        assertSame(image, state(shelf, "preview"))
        call(shelf, "back")
        assertNull(state(shelf, "preview"))
        assertEquals(true, state(shelf, "expanded"))
        call(shelf, "back")
        assertNull(state(shelf, "openedBatchId"))
        assertEquals(true, state(shelf, "expanded"))
        call(shelf, "back")
        assertEquals(false, state(shelf, "expanded"))
        assertEquals(ids, state(shelf, "selectedIds"))
        assertEquals(true, state(shelf, "selectionMode"))

        // Content drag and the collapse button both use collapse(), not Back's preview exit.
        state(shelf, "preview", image)
        call(shelf, "collapse")
        assertSame(image, state(shelf, "preview"))
        assertEquals(ids, state(shelf, "selectedIds"))
    }

    @Test fun groupBrowsingDoesNotTruncateTheDragSelectionOrReverseItsOrder() = withShelf { shelf ->
        val first = entry("first", "group", 0)
        val second = entry("second", "group", 1)
        val other = entry("other", "other", 0)
        state(shelf, "entries", listOf(second, other, first))
        state(shelf, "selectedIds", setOf(first.id, second.id, other.id))
        state(shelf, "selectionMode", true)
        state(shelf, "openedBatchId", other.batchId)

        @Suppress("UNCHECKED_CAST")
        val selected = call(shelf, "selectedEntries") as List<StashStore.Entry>
        val clip = clipForStash(selected)
        assertEquals(3, clip.itemCount)
        assertEquals(listOf("first", "second", "other"),
            (0 until clip.itemCount).map { clip.getItemAt(it).text.toString() })
    }

    @Test fun cancelledHandleMoveRestoresPositionWithoutSavingANewEdge() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("stash_window_position", Context.MODE_PRIVATE)
        preferences.edit().putBoolean("left", true).putFloat("center_y", .4f).commit()
        withShelf { shelf ->
            call(shelf, "collapse")
            val overlay = field(shelf, "overlay") as InteractiveOverlayWindow
            val startX = overlay.window.attributes.x
            val startY = overlay.window.attributes.y
            fun move(action: Int, x: Float, y: Float) {
                val event = MotionEvent.obtain(0L, 0L, action, x, y, 0)
                try { call(shelf, "handleMove", event, false) } finally { event.recycle() }
            }
            move(MotionEvent.ACTION_DOWN, startX + 5f, startY + 5f)
            move(MotionEvent.ACTION_MOVE, startX + 605f, startY + 105f)
            assertNotEquals(startX, overlay.window.attributes.x)
            move(MotionEvent.ACTION_CANCEL, startX + 605f, startY + 105f)

            assertEquals(startX, overlay.window.attributes.x)
            assertEquals(startY, overlay.window.attributes.y)
            assertEquals(.4f, field(shelf, "centerY") as Float, 0f)
            assertTrue(preferences.getBoolean("left", false))
            assertEquals(.4f, preferences.getFloat("center_y", 0f), 0f)
            assertNull(field(shelf, "dragLeft"))
            assertFalse(field(shelf, "moving") as Boolean)
        }
    }

    private fun withShelf(test: (Any) -> Unit) {
        val constructor = Class.forName("com.leaf.hyperdragshare.codex.StashOverlayPanel\$Shelf")
            .getDeclaredConstructor(Context::class.java).apply { isAccessible = true }
        val shelf = constructor.newInstance(RuntimeEnvironment.getApplication())
        // An unattached WindowContext has empty max bounds in Robolectric.
        val context = field(shelf, "context") as Context
        val configuration = ReflectionHelpers.getField<Any>(context.resources.configuration, "windowConfiguration")
        ReflectionHelpers.callInstanceMethod<Any>(configuration, "setMaxBounds",
            ClassParameter.from(Rect::class.java, Rect(0, 0, 1000, 2000)))
        try { test(shelf) } finally { (field(shelf, "overlay") as InteractiveOverlayWindow).dismiss() }
    }

    private fun entry(id: String, batch: String, order: Int, image: Boolean = false): StashStore.Entry {
        val constructor = StashStore.Entry::class.java.declaredConstructors.single().apply { isAccessible = true }
        return constructor.newInstance(id, if (image) StashStore.KIND_IMAGE else StashStore.KIND_TEXT,
            if (image) null else id, if (image) id else null, if (image) "image/png" else null,
            1L, batch, order) as StashStore.Entry
    }

    private fun call(shelf: Any, name: String, vararg arguments: Any?): Any? =
        shelf.javaClass.declaredMethods.single { it.name == name && it.parameterCount == arguments.size }
            .apply { isAccessible = true }.invoke(shelf, *arguments)

    private fun field(shelf: Any, name: String): Any? =
        shelf.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(shelf)

    private fun state(shelf: Any, name: String): Any? =
        (field(shelf, "$name\$delegate") as MutableState<*>).value

    @Suppress("UNCHECKED_CAST")
    private fun state(shelf: Any, name: String, value: Any?) {
        (field(shelf, "$name\$delegate") as MutableState<Any?>).value = value
    }
}

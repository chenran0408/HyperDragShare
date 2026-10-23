package com.leaf.hyperdragshare.codex

import android.content.Context
import android.os.Bundle
import com.leaf.hyperdragshare.gestures.config.ConfigContract
import com.leaf.hyperdragshare.gestures.config.ConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowToast
import java.time.Duration
import java.util.LinkedHashSet

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class SettingsEffectNoticeTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun themeChangeReportsImmediateAppAndNextOverlayEffect() {
        val previous = DragShareSettings.defaults()
        val next = changedSettings {
            putInt("color_mode", DragShareSettings.COLOR_DARK)
        }

        saveDragShareSettingsWithNotice(context, previous, next)

        assertToastContains("应用主题立即更新")
        assertToastContains("新建的浮窗")
        assertToastContains("无需重启")
    }

    @Test
    fun loggingChangeReportsImmediateEffectInsteadOfPortalSessionEffect() {
        val previous = DragShareSettings.defaults()
        val next = changedSettings {
            putInt("log_level", DragShareSettings.LOG_LEVEL_DEBUG)
            putInt("log_destination", DragShareSettings.LOG_DESTINATION_FILE)
        }

        saveDragShareSettingsWithNotice(context, previous, next)

        assertToastContains("日志等级和保存位置已即时应用")
        assertToastContains("无需重启")
        assertToastDoesNotContain("下次取词或选图")
    }

    @Test
    fun portalSettingReportsNextSessionWithoutRestart() {
        val previous = DragShareSettings.defaults()
        val next = changedSettings {
            putInt("simple_menu_position", DragShareSettings.SIMPLE_MENU_POSITION_TOP)
        }

        saveDragShareSettingsWithNotice(context, previous, next)

        assertToastContains("传送门在下一次取词或选图时读取")
        assertToastContains("当前操作不变")
        assertToastContains("无需重启")
    }

    @Test
    fun blacklistChangeNamesPortalAndSystemUiConsumers() {
        val previous = DragShareSettings.defaults()
        val next = changedSettings {
            putStringArrayList("accessibility_blacklisted_packages", arrayListOf("example.game"))
        }

        saveDragShareSettingsWithNotice(context, previous, next)

        assertToastContains("传送门下次读取避让名单")
        assertToastContains("系统界面（SystemUI）")
        assertToastContains("后续手势读取")
        assertToastContains("无需重启")
    }

    @Test
    fun configStoreWriteReportsSystemUiHostWithoutClaimingRuntimeConfirmation() {
        val store = ConfigStore(context)

        saveGestureBooleanSetting(context, store, ConfigContract.KEY_ENABLED, true)

        // ConfigStore coalesces writes for 120 ms before persisting and notifying hosts.
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(120))
        assertTrue(store.preferences().getBoolean(ConfigContract.KEY_ENABLED, false))
        assertToastContains("系统界面（SystemUI）")
        assertToastContains("后续手势读取新设置")
        assertToastContains("无需重启")
        assertToastDoesNotContain("已生效")
        assertToastDoesNotContain("已收到")
    }

    @Test
    fun consecutiveUiMutationsPreserveScalarCollectionsAndNotices() {
        val original = DragShareSettings.defaults().toBuilder()
            .setHiddenTargetKeys(linkedSetOf("target.initial-hidden"))
            .setTargetOrder(listOf("target.first", "target.second"))
            .setAccessibilityBlacklistedPackages(linkedSetOf("app.initial-blocked"))
            .setManualGamePackages(linkedSetOf("app.initial-game"))
            .build()
        val previous = DragShareSettings.readLocal(context)
        try {
            original.saveLocal(context)
            var current = DragShareSettings.readLocal(context)
            val persist: (DragShareSettingsMutation) -> DragShareSettings = { mutation ->
                updateDragShareSettingsWithNotice(context, current, mutation).also {
                    current = it
                }
            }

            // Separate calls model callbacks from the settings pages.
            persist { setColorMode(DragShareSettings.COLOR_DARK) }
            assertToastContains("应用主题立即更新")
            persist { setEdgeTriggerDp(123) }

            persist { settings ->
                val hidden = LinkedHashSet(settings.hiddenTargetKeys)
                hidden.add("target.added-hidden")
                setHiddenTargetKeys(hidden)
            }
            persist { settings ->
                val hidden = LinkedHashSet(settings.hiddenTargetKeys)
                hidden.remove("target.initial-hidden")
                setHiddenTargetKeys(hidden)
            }
            persist { setTargetOrder(listOf("target.second", "target.first")) }

            persist { settings ->
                val packages = LinkedHashSet(settings.accessibilityBlacklistedPackages)
                packages.add("app.added-blocked")
                setAccessibilityBlacklistedPackages(packages)
            }
            assertToastContains("系统界面（SystemUI）")
            persist { settings ->
                val packages = LinkedHashSet(settings.accessibilityBlacklistedPackages)
                packages.remove("app.initial-blocked")
                setAccessibilityBlacklistedPackages(packages)
            }
            persist { settings ->
                val packages = LinkedHashSet(settings.manualGamePackages)
                packages.add("app.added-game")
                setManualGamePackages(packages)
            }
            assertToastContains("系统界面（SystemUI）")

            val saved = DragShareSettings.readLocal(context)
            assertEquals(DragShareSettings.COLOR_DARK, current.colorMode)
            assertEquals(DragShareSettings.COLOR_DARK, saved.colorMode)
            assertEquals(123, saved.edgeTriggerDp)
            assertEquals(setOf("target.added-hidden"), saved.hiddenTargetKeys)
            assertEquals(listOf("target.second", "target.first"), saved.targetOrder)
            assertEquals(setOf("app.added-blocked"), saved.accessibilityBlacklistedPackages)
            assertEquals(setOf("app.initial-game", "app.added-game"), saved.manualGamePackages)
            assertToastContains("后续手势读取更新后的名单")
        } finally {
            previous.saveLocal(context)
        }
    }

    private fun changedSettings(mutate: Bundle.() -> Unit): DragShareSettings {
        val bundle = DragShareSettings.defaults().toBundle()
        mutate(bundle)
        return DragShareSettings.fromBundle(bundle)
    }

    private fun assertToastContains(text: String) {
        ShadowLooper.shadowMainLooper().idle()
        assertTrue(ShadowToast.getTextOfLatestToast().contains(text))
    }

    private fun assertToastDoesNotContain(text: String) {
        ShadowLooper.shadowMainLooper().idle()
        assertTrue(!ShadowToast.getTextOfLatestToast().contains(text))
    }
}

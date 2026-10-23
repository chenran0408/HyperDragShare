package com.leaf.hyperdragshare.codex

import android.content.Context
import android.widget.Toast
import com.leaf.hyperdragshare.gestures.config.ConfigStore

/** Saves portal settings and tells the user when the real consumers will read them. */
internal fun saveDragShareSettingsWithNotice(
    context: Context,
    previous: DragShareSettings,
    next: DragShareSettings,
) {
    next.saveLocal(context)

    val themeChanged = previous.colorMode != next.colorMode
    val loggingChanged = previous.logLevel != next.logLevel
            || previous.logDestination != next.logDestination
    val gestureExclusionsChanged = previous.pauseAutomaticInGames != next.pauseAutomaticInGames
            || previous.manualGamePackages != next.manualGamePackages
            || previous.accessibilityBlacklistedPackages != next.accessibilityBlacklistedPackages
    val portalSessionChanged = previous.contentCaptureMode != next.contentCaptureMode
            || previous.uiStyle != next.uiStyle
            || previous.edgeTriggerDp != next.edgeTriggerDp
            || previous.scrollSpeedDpPerSecond != next.scrollSpeedDpPerSecond
            || previous.blockBackgroundScroll != next.blockBackgroundScroll
            || previous.textSharingEnabled != next.textSharingEnabled
            || previous.imageSharingEnabled != next.imageSharingEnabled
            || previous.simpleMenuPosition != next.simpleMenuPosition
            || previous.simpleMenuOpacityPercent != next.simpleMenuOpacityPercent
            || previous.simpleMenuCornerRadiusDp != next.simpleMenuCornerRadiusDp
            || previous.simpleMenuEdgeDistanceDp != next.simpleMenuEdgeDistanceDp
            || previous.iconOpacityPercent != next.iconOpacityPercent
            || previous.modernBlurRadiusDp != next.modernBlurRadiusDp
            || previous.modernGlassOpacityPercent != next.modernGlassOpacityPercent
            || previous.closeMenuWhenPointerLeaves != next.closeMenuWhenPointerLeaves
            || previous.hiddenTargetKeys != next.hiddenTargetKeys
            || previous.targetOrder != next.targetOrder
            || previous.accessibilityLandscapeRecognitionEnabled != next.accessibilityLandscapeRecognitionEnabled
            || previous.accessibilityLongPressTimeoutMillis != next.accessibilityLongPressTimeoutMillis
            || previous.accessibilityRecognitionSensitivityPercent != next.accessibilityRecognitionSensitivityPercent

    val messages = buildList {
        if (themeChanged) add("已保存：应用主题立即更新，新建的浮窗也会使用该主题。无需重启。")
        if (loggingChanged) add("已保存：日志等级和保存位置已即时应用。无需重启。")
        if (gestureExclusionsChanged) {
            add("已保存：传送门下次读取避让名单；系统界面（SystemUI）在后续手势读取更新后的名单。无需重启。")
        }
        if (portalSessionChanged) {
            add("已保存：传送门在下一次取词或选图时读取；当前操作不变。无需重启作用域。")
        }
        if (isEmpty()) add("设置已保存。")
    }
    Toast.makeText(
        context,
        messages.joinToString(" "),
        if (messages.size > 1 || messages.first().length > 45) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
    ).show()
}

/** Writes one live gesture preference through ConfigStore and reports its actual host scope. */
internal fun saveGestureBooleanSetting(
    context: Context,
    store: ConfigStore,
    key: String,
    value: Boolean,
) {
    store.putBoolean(key, value)
    showGestureConfigSaved(context)
}

internal fun saveGestureIntegerSetting(
    context: Context,
    store: ConfigStore,
    key: String,
    value: Int,
) {
    store.putInt(key, value)
    showGestureConfigSaved(context)
}

internal fun showGestureConfigSaved(context: Context) {
    Toast.makeText(
        context,
        "已保存；系统界面（SystemUI）会在后续手势读取新设置，无需重启。",
        Toast.LENGTH_SHORT,
    ).show()
}

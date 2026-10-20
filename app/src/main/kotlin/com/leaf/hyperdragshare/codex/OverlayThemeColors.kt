package com.leaf.hyperdragshare.codex

import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** Observe configuration changes even in a long-lived overlay composition. */
@Composable
internal fun DragShareSettings.isDarkThemeInComposition(): Boolean =
    isDarkTheme(LocalConfiguration.current.uiMode)

/** Miuix colors shared with the native View and Canvas overlays. */
internal object OverlayThemeColors {
    @JvmStatic fun colors(dark: Boolean): IntArray {
        val colors = if (dark) darkColorScheme() else lightColorScheme()
        return intArrayOf(
            colors.surfaceContainer.toArgb(),
            colors.onSurface.toArgb(),
            colors.onSurfaceVariantSummary.toArgb(),
            colors.primary.toArgb(),
            colors.primary.copy(alpha = 0.14f).toArgb(),
            colors.primary.copy(alpha = 0.4f).toArgb(),
        )
    }
}

package com.leaf.hyperdragshare.codex

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ModernOverlaySurfaceTest {
    @Test
    fun failedNativeGlassKeepsTheExistingPublicBlurAppearance() {
        val surface = Color(0xFF123456)

        val result = modernOverlayCardColor(
            surfaceColor = surface,
            opacityPercent = 40,
            nativeBackdropBlur = true,
            nativeGlassMaterial = false,
        )

        assertEquals(0.4f, result.alpha, 0.0001f)
        assertEquals(surface.red, result.red, 0.0001f)
        assertEquals(surface.green, result.green, 0.0001f)
        assertEquals(surface.blue, result.blue, 0.0001f)
    }

    @Test
    fun nativeGlassMakesComposeSurfaceTransparentWithoutLosingOpaqueFallback() {
        val surface = Color(0xFF123456)

        assertEquals(
            Color.Transparent,
            modernOverlayCardColor(surface, 40, nativeBackdropBlur = true, nativeGlassMaterial = true),
        )
        assertEquals(
            surface,
            modernOverlayCardColor(surface, 40, nativeBackdropBlur = false, nativeGlassMaterial = false),
        )
    }
}

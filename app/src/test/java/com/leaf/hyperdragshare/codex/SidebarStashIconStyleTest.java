package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SidebarStashIconStyleTest {
    @Test public void whiteGlyphKeepsContrastAcrossLightGlassAndDarkSurfaces() {
        int[] backgrounds = {
                Color.WHITE,
                Color.rgb(242, 242, 242),
                Color.rgb(220, 238, 255),
                Color.rgb(255, 232, 194),
                Color.rgb(32, 34, 38)
        };

        assertEquals(Color.WHITE, SidebarStashIconStyle.ICON_COLOR);
        assertEquals(0x44, Color.alpha(SidebarStashIconStyle.RIPPLE_COLOR));
        for (int background : backgrounds) {
            assertTrue("idle button contrast on " + Integer.toHexString(background),
                    contrast(Color.WHITE, composite(SidebarStashIconStyle.IDLE_SURFACE, background)) >= 4.5);
            assertTrue("active button contrast on " + Integer.toHexString(background),
                    contrast(Color.WHITE, composite(SidebarStashIconStyle.ACTIVE_SURFACE, background)) >= 4.5);
        }
    }

    @Test public void activeSurfaceRemainsDistinctFromIdleSurface() {
        assertNotEquals(SidebarStashIconStyle.IDLE_SURFACE, SidebarStashIconStyle.ACTIVE_SURFACE);
        assertTrue(Color.alpha(SidebarStashIconStyle.ACTIVE_SURFACE)
                > Color.alpha(SidebarStashIconStyle.IDLE_SURFACE));
    }

    private static int composite(int foreground, int background) {
        int alpha = Color.alpha(foreground);
        int inverse = 255 - alpha;
        return Color.rgb(
                (Color.red(foreground) * alpha + Color.red(background) * inverse + 127) / 255,
                (Color.green(foreground) * alpha + Color.green(background) * inverse + 127) / 255,
                (Color.blue(foreground) * alpha + Color.blue(background) * inverse + 127) / 255);
    }

    private static double contrast(int first, int second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * linear(Color.red(color))
                + 0.7152 * linear(Color.green(color))
                + 0.0722 * linear(Color.blue(color));
    }

    private static double linear(int component) {
        double value = component / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}

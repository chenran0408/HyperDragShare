package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class PressWindowSnapshotTest {
    @Test
    public void clipsRegionAroundOccludingWindowAndRejectsTouchInsideIt() {
        PressWindowSnapshot snapshot = PressWindowSnapshot.parse(dump(
                window("overlay", "com.example/com.example.Overlay", "APPLICATION_OVERLAY",
                        "[400,700][600,900]", true, ""),
                window("focus", "com.tencent.mm/com.tencent.mm.ui.LauncherUI", "BASE_APPLICATION",
                        "[0,0][1000,2000]", true, "")));

        assertEquals("com.tencent.mm/com.tencent.mm.ui.LauncherUI", snapshot.windowName);
        Rect region = snapshot.regionAt(250, 800, 1000, 2000, 1f);
        assertEquals(new Rect(0, 480, 400, 1120), region);
        assertTrue(snapshot.permits(region));
        assertNull(snapshot.regionAt(500, 800, 1000, 2000, 1f));
        assertFalse(snapshot.permits(new Rect(350, 750, 450, 850)));
    }

    @Test
    public void readsTargetRomRectFrame() {
        PressWindowSnapshot snapshot = PressWindowSnapshot.parse(dump(window("focus",
                "com.tencent.mm/com.tencent.mm.ui.LauncherUI", "BASE_APPLICATION",
                "Rect(0, 0 - 1440, 3200)", true, "")));
        assertEquals(new Rect(0, 0, 1440, 3200), snapshot.bounds);
    }

    @Test
    public void rejectsSecureFocusAndMissingFocusToken() {
        String secure = dump(window("focus", "com.tencent.mm/com.tencent.mm.ui.LauncherUI",
                "BASE_APPLICATION", "[0,0][1000,2000]", true, "fl=0x2000"));
        assertNull(PressWindowSnapshot.parse(secure));

        String wrongToken = secure.replace("mCurrentFocus=Window{focus", "mCurrentFocus=Window{other");
        assertNull(PressWindowSnapshot.parse(wrongToken));
    }

    @Test
    public void ignoresExplicitlyTransparentAndOtherDisplayOverlays() {
        String transparent = window("overlay", "com.example/com.example.Overlay", "APPLICATION_OVERLAY",
                "[0,0][1000,2000]", true, "mShownAlpha=0.0");
        String otherDisplay = window("secondary", "com.example/com.example.Overlay", "APPLICATION_OVERLAY",
                "[0,0][1000,2000]", true, "").replace("mDisplayId=0", "mDisplayId=2");
        PressWindowSnapshot snapshot = PressWindowSnapshot.parse(dump(transparent, otherDisplay,
                window("focus", "com.tencent.mm/com.tencent.mm.ui.LauncherUI", "BASE_APPLICATION",
                        "[-50,0][1050,2000]", true, "")));
        Rect region = snapshot.regionAt(900, 1000, 1000, 2000, 1f);
        assertTrue(snapshot.permits(region));
        assertTrue(region.left >= 0);
        assertTrue(region.right <= 1000);
    }

    @Test
    public void sameWindowRequiresTokenNameAndBoundsToMatch() {
        String template = window("focus", "com.tencent.mm/com.tencent.mm.ui.LauncherUI",
                "BASE_APPLICATION", "[0,0][1000,2000]", true, "");
        PressWindowSnapshot first = PressWindowSnapshot.parse(dump(template));
        PressWindowSnapshot same = PressWindowSnapshot.parse(dump(template));
        PressWindowSnapshot newToken = PressWindowSnapshot.parse(dump(template).replace("focus", "next"));
        assertTrue(first.sameWindow(same));
        assertFalse(first.sameWindow(newToken));
    }

    private static String dump(String... windows) {
        StringBuilder dump = new StringBuilder("mCurrentFocus=Window{focus u0 com.tencent.mm/com.tencent.mm.ui.LauncherUI}\n");
        for (int i = 0; i < windows.length; i++) dump.append("Window #").append(i).append(' ').append(windows[i]);
        return dump.toString();
    }

    private static String window(String token, String name, String type, String frame,
            boolean visible, String flags) {
        return "Window{" + token + " u0 " + name + "}:\n"
                + "  mDisplayId=0\n"
                + "  mAttrs={ty=" + type + (flags.isEmpty() ? "" : " " + flags) + "}\n"
                + "  mHasSurface=true\n"
                + "  isOnScreen=" + visible + "\n"
                + "  isVisible=" + visible + "\n"
                + "  Frames: frame=" + frame + "\n";
    }
}

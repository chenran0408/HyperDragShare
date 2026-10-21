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
    public void joinsCurrentRomWindowMetadataToVisibleHandlesByToken() {
        PressWindowSnapshot.ParseResult parsed = PressWindowSnapshot.parseDetailed(currentRomChatDump());

        assertEquals("accepted", parsed.reason);
        assertTrue(parsed.snapshot != null);
        assertEquals("com.tencent.mm/com.tencent.mm.ui.chatting.variants.ChattingMainUI",
                parsed.snapshot.windowName);
        assertEquals(new Rect(0, 0, 1440, 3200), parsed.snapshot.bounds);
        assertTrue(PressWindowSnapshot.isSupportedWeChatWindow(
                parsed.snapshot.packageName, parsed.snapshot.windowName));
        assertTrue(PressWindowSnapshot.isSupportedWeChatWindow("com.tencent.mm",
                "com.tencent.mm/com.tencent.mm.plugin.webview.ui.tools.WebViewUI"));
        assertTrue(PressWindowSnapshot.isSupportedWeChatWindow("com.tencent.mm",
                "com.tencent.mm/com.tencent.mm.plugin.sns.ui.SnsTimeLineUI"));
        assertFalse(PressWindowSnapshot.isSupportedWeChatWindow("com.example",
                "com.tencent.mm/com.tencent.mm.ui.LauncherUI"));
        assertFalse(PressWindowSnapshot.isSupportedWeChatWindow("com.tencent.mm",
                "com.example/.Overlay"));
        assertEquals(new Rect(424, 488, 1288, 1512),
                parsed.snapshot.regionAt(856, 1000, 1440, 3200, 3f));
        assertEquals(new Rect(0, 488, 864, 1512),
                parsed.snapshot.regionAt(50, 1000, 1440, 3200, 3f));
        assertEquals(new Rect(288, 2176, 1152, 3125),
                parsed.snapshot.regionAt(720, 3100, 1440, 3200, 3f));
    }

    @Test
    public void rejectsVisibleSurfaceWithoutMatchingHandleButIgnoresHiddenWindowWithoutFrame() {
        String hidden = "Window #0 Window{hidden u0 com.example/.Hidden}:\n"
                + "  mDisplayId=0\n  mAttrs={ty=APPLICATION_OVERLAY}\n"
                + "  mHasSurface=false\n  isOnScreen=false\n  isVisible=false\n";
        String visibleUnknown = "Window #1 Window{unknown u0 com.example/.Overlay}:\n"
                + "  mDisplayId=0\n  mAttrs={ty=APPLICATION_OVERLAY}\n"
                + "  mHasSurface=true\n  isOnScreen=true\n  isVisible=true\n";
        String focus = window("focus", "com.tencent.mm/com.tencent.mm.ui.LauncherUI",
                "BASE_APPLICATION", "[0,0][1000,2000]", true, "");
        PressWindowSnapshot.ParseResult parsed = PressWindowSnapshot.parseDetailed(
                dump(hidden, visibleUnknown, focus));

        assertNull(parsed.snapshot);
        assertEquals("visible_occlusion_bounds_missing", parsed.reason);
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

    private static String currentRomChatDump() {
        return "mCurrentFocus=Window{c0ffee u0 com.tencent.mm/com.tencent.mm.ui.chatting.variants.ChattingMainUI}\n"
                + "WINDOW MANAGER WINDOWS (dumpsys window windows)\n"
                + romWindow(0, "dead001", "MiuiShellDropTarget", "DRAG", false, false)
                + romWindow(1, "dead002", "DockAssistantView", "DISPLAY_OVERLAY", false, false)
                + romWindow(2, "face001", "GestureStubRight", "MAGNIFICATION_OVERLAY", true, true)
                + romWindow(3, "face002", "GestureStubLeft", "MAGNIFICATION_OVERLAY", true, true)
                + romNavigationBarWindow()
                + romWindow(5, "face003", "MitsuhaCameraRingWindow", "SYSTEM_OVERLAY", true, true)
                + romWindow(6, "face004", "StatusBar", "STATUS_BAR", true, true)
                + romWindow(7, "c0ffee", "com.tencent.mm/com.tencent.mm.ui.chatting.variants.ChattingMainUI",
                        "BASE_APPLICATION", true, true)
                + "  6 visible windows: [face001 GestureStubRight, frame=[Rect(1362, 0 - 1440, 3200)], "
                + "touchableRegion=SkRegion((1362,338,1440,3092)), windowToken=android.os.BinderProxy@1, displayId=0, "
                + "face002 GestureStubLeft, frame=[Rect(0, 0 - 315, 3200)], "
                + "touchableRegion=SkRegion((0,338,78,3092)), windowToken=android.os.BinderProxy@2, displayId=0, "
                + "face003 MitsuhaCameraRingWindow, frame=[Rect(0, 0 - 1440, 401)], "
                + "windowToken=android.os.BinderProxy@3, displayId=0, face004 StatusBar, "
                + "frame=[Rect(0, 0 - 1440, 169)], windowToken=android.os.BinderProxy@4, displayId=0, "
                + "c0ffee com.tencent.mm/com.tencent.mm.ui.chatting.variants.ChattingMainUI, "
                + "frame=[Rect(0, 0 - 1440, 3200)], windowToken=android.os.BinderProxy@5, displayId=0]\n"
                + "    1 display info(s):\n"
                + "displayId=0, mLogicalSize=1440x3200, mTransform=Matrix{}\n";
    }

    private static String romWindow(int index, String token, String name, String type,
            boolean surface, boolean visible) {
        String ownerPackage = name.startsWith("GestureStub") ? "com.miui.home"
                : name.startsWith("com.tencent.mm/") ? "com.tencent.mm" : "com.android.systemui";
        String flags = name.startsWith("GestureStub")
                ? "NOT_FOCUSABLE NOT_TOUCH_MODAL LAYOUT_IN_SCREEN" : "NOT_FOCUSABLE";
        return "  Window #" + index + " Window{" + token + " u0 " + name + "}:\n"
                + "    mDisplayId=0 taskId=1\n"
                + "    mOwnerUid=10137 showForAllUsers=true package=" + ownerPackage + " appop=NONE\n"
                + "    mAttrs={(0,0)(fillxfill) ty=" + type + " fl=" + flags + "}\n"
                + "    mHasSurface=" + surface + " isReadyForDisplay=" + surface + "\n"
                + "    Surface: shown=" + visible + " isOnScreen=" + visible + "\n"
                + "    isVisible=" + visible + "\n";
    }

    private static String romNavigationBarWindow() {
        return "  Window #4 Window{nav u0 NavigationBar0}:\n"
                + "    mDisplayId=0\n"
                + "    mOwnerUid=1000 showForAllUsers=true package=com.android.systemui appop=NONE\n"
                + "    mAttrs={(0,0)(fillx75) ty=NAVIGATION_BAR fl=NOT_FOCUSABLE NOT_TOUCH_MODAL\n"
                + "      providedInsets:\n"
                + "        InsetsFrameProvider: {id=#a4a60001, index=0, type=navigationBars, source=FRAME, flags=[SUPPRESS_SCRIM], insetsSize=Insets{left=0, top=0, right=0, bottom=75}}\n"
                + "    mHasSurface=true isReadyForDisplay()=true\n"
                + "    isOnScreen=true\n    isVisible=true\n";
    }
}

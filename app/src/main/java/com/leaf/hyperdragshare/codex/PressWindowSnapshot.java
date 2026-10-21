package com.leaf.hyperdragshare.codex;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A small, fail-closed view of the focused application window and windows above it. */
final class PressWindowSnapshot {
    private static final Pattern WINDOW = Pattern.compile("Window #\\d+ Window\\{([^ ]+) ([^ ]+) ([^}]+)\\}:");
    private static final Pattern FOCUS = Pattern.compile("mCurrentFocus=Window\\{([^ ]+) ([^ ]+) ([^}]+)\\}");
    private static final Pattern DISPLAY = Pattern.compile("\\bmDisplayId=(\\d+)\\b");
    private static final Pattern FRAME_PAIR = Pattern.compile(
            "\\bframe=\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*]\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*]");
    private static final Pattern FRAME_RECT = Pattern.compile(
            "\\bframe=Rect\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*-\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)");
    private static final Pattern HANDLE_FRAME = Pattern.compile(
            "(?:\\[|,\\s*)([0-9a-fA-F]+) ([^,]+), frame=\\[Rect\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*-\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)\\].*?displayId=(\\d+)");
    private static final Pattern TYPE = Pattern.compile("\\bty=([A-Z_]+)\\b");
    private static final Pattern HEX_FLAGS = Pattern.compile("\\bfl=0x([0-9a-fA-F]+)");
    private static final Pattern SECURE_FLAG = Pattern.compile("\\bfl=[^\\r\\n]*\\bSECURE\\b");
    private static final Pattern ZERO_ALPHA = Pattern.compile("(?:mShownAlpha|mAlpha|alpha)=0(?:\\.0+)?(?:[\\s}]|$)");
    private static final Pattern OWNER_PACKAGE = Pattern.compile("\\bmOwnerUid=\\d+[^\\r\\n]*?\\bpackage=([^\\s]+)");
    private static final Pattern NAVIGATION_INSETS = Pattern.compile(
            "InsetsFrameProvider: \\{[^\\r\\n]*?type=navigationBars, source=FRAME,[^\\r\\n]*?insetsSize=Insets\\{left=(\\d+), top=(\\d+), right=(\\d+), bottom=(\\d+)\\}\\}");
    private static final Pattern DISPLAY_SIZE = Pattern.compile("displayId=0, mLogicalSize=(\\d+)x(\\d+)");
    private static final int FLAG_SECURE = 0x2000;

    final String token;
    final String packageName;
    final String windowName;
    final Rect bounds;
    private final List<Rect> occlusions;

    private PressWindowSnapshot(String token, String packageName, String windowName,
            Rect bounds, List<Rect> occlusions) {
        this.token = token;
        this.packageName = packageName;
        this.windowName = windowName;
        this.bounds = new Rect(bounds);
        this.occlusions = new ArrayList<>(occlusions);
    }

    static PressWindowSnapshot parse(String dump) {
        return parseDetailed(dump).snapshot;
    }

    static ParseResult parseDetailed(String dump) {
        if (dump == null || dump.isEmpty()) return ParseResult.rejected("window_dump_empty");
        Matcher focusMatcher = FOCUS.matcher(dump);
        if (!focusMatcher.find()) return ParseResult.rejected("focus_missing");
        String focusToken = focusMatcher.group(1);
        String windowName = focusMatcher.group(3).trim();
        int slash = windowName.indexOf('/');
        if (focusToken.isEmpty() || slash <= 0 || slash == windowName.length() - 1)
            return ParseResult.rejected("focus_identity_invalid");
        String packageName = windowName.substring(0, slash);
        Matcher displaySizeMatcher = DISPLAY_SIZE.matcher(dump);
        Rect displayBounds = displaySizeMatcher.find()
                ? new Rect(0, 0, Integer.parseInt(displaySizeMatcher.group(1)),
                Integer.parseInt(displaySizeMatcher.group(2))) : null;

        java.util.Map<String, WindowHandle> handles = readVisibleHandles(dump);

        List<String> blocks = new ArrayList<>();
        Matcher matcher = WINDOW.matcher(dump);
        int start = -1;
        while (matcher.find()) {
            if (start >= 0) blocks.add(dump.substring(start, matcher.start()));
            start = matcher.start();
        }
        if (start >= 0) blocks.add(dump.substring(start));

        int focusIndex = -1;
        for (int i = 0; i < blocks.size(); i++) {
            Matcher header = WINDOW.matcher(blocks.get(i));
            if (header.find() && focusToken.equals(header.group(1))) {
                focusIndex = i;
                break;
            }
        }
        if (focusIndex < 0) return ParseResult.rejected("focus_window_missing");

        WindowData focused = readWindow(blocks.get(focusIndex), handles);
        if (focused == null) return ParseResult.rejected("focus_metadata_invalid");
        if (focused.display != 0) return ParseResult.rejected("focus_not_default_display");
        if (!focused.visible) return ParseResult.rejected("focus_not_visible");
        if (!focused.surface) return ParseResult.rejected("focus_surface_missing");
        if (focused.secure) return ParseResult.rejected("focus_secure");
        if (!("BASE_APPLICATION".equals(focused.type) || "APPLICATION".equals(focused.type)))
            return ParseResult.rejected("focus_type_unsupported");
        if (focused.frame == null || focused.frame.isEmpty()) return ParseResult.rejected("focus_bounds_missing");

        List<Rect> occlusions = new ArrayList<>();
        for (int i = 0; i < focusIndex; i++) {
            WindowData above = readWindow(blocks.get(i), handles);
            if (above == null) return ParseResult.rejected("occlusion_metadata_invalid");
            if (above.display == 0 && above.visible && above.surface) {
                if (isVerifiedTransparentMiuiGestureStub(above)) continue;
                Rect obstacle = above.frame;
                if (obstacle == null && "NAVIGATION_BAR".equals(above.type))
                    obstacle = navigationBarFrame(above, displayBounds);
                if (obstacle == null || obstacle.isEmpty())
                    return ParseResult.rejected("visible_occlusion_bounds_missing");
                occlusions.add(new Rect(obstacle));
            }
        }
        return ParseResult.accepted(new PressWindowSnapshot(focusToken, packageName, windowName,
                focused.frame, occlusions));
    }

    static boolean isSupportedWeChatWindow(String packageName, String windowName) {
        if (!"com.tencent.mm".equals(packageName) || windowName == null
                || !windowName.startsWith("com.tencent.mm/")) return false;
        String component = windowName.substring("com.tencent.mm/".length());
        return component.startsWith(".") || component.startsWith("com.tencent.mm.");
    }

    private static java.util.Map<String, WindowHandle> readVisibleHandles(String dump) {
        java.util.Map<String, WindowHandle> handles = new java.util.HashMap<>();
        int start = dump.indexOf(" visible windows: [");
        if (start < 0) return handles;
        int end = dump.indexOf(" display info(s):", start);
        if (end < 0) end = dump.indexOf(" window handles on display(s):", start);
        if (end < 0) end = dump.length();
        Matcher matcher = HANDLE_FRAME.matcher(dump.substring(start, end));
        while (matcher.find()) {
            if (Integer.parseInt(matcher.group(7)) != 0) continue;
            Rect bounds = new Rect(Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4)),
                    Integer.parseInt(matcher.group(5)), Integer.parseInt(matcher.group(6)));
            handles.put(matcher.group(1), new WindowHandle(bounds));
        }
        return handles;
    }

    static final class ParseResult {
        final PressWindowSnapshot snapshot;
        final String reason;

        private ParseResult(PressWindowSnapshot snapshot, String reason) {
            this.snapshot = snapshot;
            this.reason = reason;
        }

        private static ParseResult accepted(PressWindowSnapshot snapshot) {
            return new ParseResult(snapshot, "accepted");
        }

        private static ParseResult rejected(String reason) {
            return new ParseResult(null, reason);
        }
    }

    Rect regionAt(float x, float y, int width, int height, float density) {
        Rect usable = new Rect(bounds);
        if (!usable.intersect(0, 0, width, height)
                || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(density)
                || density <= 0 || width <= 0 || height <= 0
                || x < usable.left || x >= usable.right || y < usable.top || y >= usable.bottom) {
            return null;
        }
        // Wider than the original 60% crop so a pressed sentence keeps both ends. This mirrors
        // the useful context ratio observed in the reference implementation without copying
        // its blind full-screen OCR fallbacks.
        int regionWidth = Math.min(usable.width(), Math.max(1, Math.round(width * 0.78f)));
        int regionHeight = Math.min(usable.height(), Math.max(1, Math.round(height * 0.34f)));
        int left = Math.max(usable.left, Math.min(Math.round(x - regionWidth / 2f), usable.right - regionWidth));
        int top = Math.max(usable.top, Math.min(Math.round(y - regionHeight / 2f), usable.bottom - regionHeight));
        Rect region = new Rect(left, top, left + regionWidth, top + regionHeight);
        for (Rect obstacle : occlusions) {
            if (!Rect.intersects(region, obstacle)) continue;
            if (obstacle.contains(Math.round(x), Math.round(y))) {
                Rect nearby = nearestVisibleRegion(usable, obstacle, x, y, regionWidth, regionHeight);
                if (nearby != null) return nearby;
                return null;
            }
            if (x < obstacle.left) region.right = Math.min(region.right, obstacle.left);
            else if (x >= obstacle.right) region.left = Math.max(region.left, obstacle.right);
            else if (y < obstacle.top) region.bottom = Math.min(region.bottom, obstacle.top);
            else if (y >= obstacle.bottom) region.top = Math.max(region.top, obstacle.bottom);
            if (region.isEmpty()) return null;
        }
        return permits(region) ? region : null;
    }

    private Rect nearestVisibleRegion(Rect usable, Rect obstacle, float x, float y,
            int width, int height) {
        List<Rect> candidates = new ArrayList<>(4);
        int top = Math.max(usable.top, Math.min(Math.round(y - height / 2f), usable.bottom - height));
        int left = Math.max(usable.left, Math.min(Math.round(x - width / 2f), usable.right - width));
        if (obstacle.left > usable.left) {
            candidates.add(new Rect(Math.max(usable.left, obstacle.left - width), top,
                    obstacle.left, Math.min(usable.bottom, top + height)));
        }
        if (obstacle.right < usable.right) {
            candidates.add(new Rect(obstacle.right, top,
                    Math.min(usable.right, obstacle.right + width), Math.min(usable.bottom, top + height)));
        }
        if (obstacle.top > usable.top) {
            candidates.add(new Rect(left, Math.max(usable.top, obstacle.top - height),
                    Math.min(usable.right, left + width), obstacle.top));
        }
        if (obstacle.bottom < usable.bottom) {
            candidates.add(new Rect(left, obstacle.bottom,
                    Math.min(usable.right, left + width), Math.min(usable.bottom, obstacle.bottom + height)));
        }
        Rect nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        int pointX = Math.round(x), pointY = Math.round(y);
        for (Rect candidate : candidates) {
            if (!permits(candidate)) continue;
            long dx = Math.max(Math.max(candidate.left - pointX, 0), pointX - candidate.right);
            long dy = Math.max(Math.max(candidate.top - pointY, 0), pointY - candidate.bottom);
            long distance = dx * dx + dy * dy;
            if (distance < nearestDistance || (distance == nearestDistance
                    && nearest != null && candidate.width() * candidate.height() > nearest.width() * nearest.height())) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    boolean permits(Rect region) {
        if (region == null || region.isEmpty() || !bounds.contains(region)) return false;
        for (Rect obstacle : occlusions) if (Rect.intersects(region, obstacle)) return false;
        return true;
    }

    boolean sameWindow(PressWindowSnapshot other) {
        return other != null && token.equals(other.token) && windowName.equals(other.windowName)
                && bounds.equals(other.bounds);
    }

    private static WindowData readWindow(String block, java.util.Map<String, WindowHandle> handles) {
        Matcher header = WINDOW.matcher(block);
        if (!header.find()) return null;
        Matcher displayMatcher = DISPLAY.matcher(block);
        Matcher frameMatcher = FRAME_RECT.matcher(block);
        boolean hasFrame = frameMatcher.find();
        if (!hasFrame) {
            frameMatcher = FRAME_PAIR.matcher(block);
            hasFrame = frameMatcher.find();
        }
        Matcher typeMatcher = TYPE.matcher(block);
        Matcher packageMatcher = OWNER_PACKAGE.matcher(block);
        if (!displayMatcher.find() || !typeMatcher.find()
                || !block.contains("mHasSurface=") || !block.contains("isOnScreen=")
                || !block.contains("isVisible=")) return null;
        Rect frame = hasFrame ? new Rect(Integer.parseInt(frameMatcher.group(1)), Integer.parseInt(frameMatcher.group(2)),
                Integer.parseInt(frameMatcher.group(3)), Integer.parseInt(frameMatcher.group(4))) : null;
        if (frame == null) {
            WindowHandle handle = handles.get(header.group(1));
            if (handle != null) frame = new Rect(handle.bounds);
        }
        boolean surface = block.contains("mHasSurface=true");
        boolean visible = block.contains("isOnScreen=true") && block.contains("isVisible=true")
                && !ZERO_ALPHA.matcher(block).find();
        Matcher flagsMatcher = HEX_FLAGS.matcher(block);
        boolean secure = SECURE_FLAG.matcher(block).find();
        if (flagsMatcher.find()) secure |= (Long.parseLong(flagsMatcher.group(1), 16) & FLAG_SECURE) != 0;
        String ownerPackage = packageMatcher.find() ? packageMatcher.group(1) : "";
        return new WindowData(header.group(3).trim(), ownerPackage,
                Integer.parseInt(displayMatcher.group(1)), frame, typeMatcher.group(1),
                block, surface, visible, secure);
    }

    /** This exact pair is a verified transparent edge-gesture input surface on the target ROM. */
    private static boolean isVerifiedTransparentMiuiGestureStub(WindowData window) {
        if (!"com.miui.home".equals(window.ownerPackage)
                || !"MAGNIFICATION_OVERLAY".equals(window.type)
                || !("GestureStubLeft".equals(window.name) || "GestureStubRight".equals(window.name))) {
            return false;
        }
        String flags = window.block;
        return flags.matches("(?s).*\\bfl=(?=[^\\r\\n]*\\bNOT_FOCUSABLE\\b)"
                + "(?=[^\\r\\n]*\\bNOT_TOUCH_MODAL\\b)"
                + "(?=[^\\r\\n]*\\bLAYOUT_IN_SCREEN\\b)[^\\r\\n]*.*");
    }

    private static Rect navigationBarFrame(WindowData window, Rect displayBounds) {
        if (!"com.android.systemui".equals(window.ownerPackage) || displayBounds == null
                || displayBounds.isEmpty()) return null;
        Matcher insets = NAVIGATION_INSETS.matcher(window.block);
        if (!insets.find()) return null;
        int left = Integer.parseInt(insets.group(1));
        int top = Integer.parseInt(insets.group(2));
        int right = Integer.parseInt(insets.group(3));
        int bottom = Integer.parseInt(insets.group(4));
        if (left != 0 || top != 0 || right != 0 || bottom <= 0 || bottom >= displayBounds.height())
            return null;
        return new Rect(displayBounds.left, displayBounds.bottom - bottom,
                displayBounds.right, displayBounds.bottom);
    }

    private static final class WindowData {
        final String name;
        final String ownerPackage;
        final int display;
        final Rect frame;
        final String type;
        final String block;
        final boolean surface;
        final boolean visible;
        final boolean secure;

        WindowData(String name, String ownerPackage, int display, Rect frame, String type,
                String block, boolean surface, boolean visible, boolean secure) {
            this.name = name;
            this.ownerPackage = ownerPackage;
            this.display = display;
            this.frame = frame;
            this.type = type;
            this.block = block;
            this.surface = surface;
            this.visible = visible;
            this.secure = secure;
        }
    }

    private static final class WindowHandle {
        final Rect bounds;

        WindowHandle(Rect bounds) {
            this.bounds = bounds;
        }
    }
}

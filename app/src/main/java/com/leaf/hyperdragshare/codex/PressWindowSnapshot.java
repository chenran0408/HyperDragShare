package com.leaf.hyperdragshare.codex;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A small, fail-closed view of the focused application window and windows above it. */
final class PressWindowSnapshot {
    private static final Pattern WINDOW = Pattern.compile("Window #\\d+ Window\\{([^ ]+) ([^ ]+) ([^}]+)}:");
    private static final Pattern FOCUS = Pattern.compile("mCurrentFocus=Window\\{([^ ]+) ([^ ]+) ([^}]+)}");
    private static final Pattern DISPLAY = Pattern.compile("\\bmDisplayId=(\\d+)\\b");
    private static final Pattern FRAME_PAIR = Pattern.compile(
            "\\bframe=\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*]\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*]");
    private static final Pattern FRAME_RECT = Pattern.compile(
            "\\bframe=Rect\\(\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*-\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)");
    private static final Pattern TYPE = Pattern.compile("\\bty=([A-Z_]+)\\b");
    private static final Pattern HEX_FLAGS = Pattern.compile("\\bfl=0x([0-9a-fA-F]+)");
    private static final Pattern SECURE_FLAG = Pattern.compile("\\bfl=[^\\r\\n]*\\bSECURE\\b");
    private static final Pattern ZERO_ALPHA = Pattern.compile("(?:mShownAlpha|mAlpha|alpha)=0(?:\\.0+)?(?:[\\s}]|$)");
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
        if (dump == null || dump.isEmpty()) return null;
        Matcher focusMatcher = FOCUS.matcher(dump);
        if (!focusMatcher.find()) return null;
        String focusToken = focusMatcher.group(1);
        String windowName = focusMatcher.group(3).trim();
        int slash = windowName.indexOf('/');
        if (focusToken.isEmpty() || slash <= 0 || slash == windowName.length() - 1) return null;
        String packageName = windowName.substring(0, slash);

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
        if (focusIndex < 0) return null;

        WindowData focused = readWindow(blocks.get(focusIndex));
        if (focused == null || focused.display != 0 || !focused.visible || !focused.surface
                || focused.secure || !("BASE_APPLICATION".equals(focused.type)
                || "APPLICATION".equals(focused.type)) || focused.frame.isEmpty()) return null;

        List<Rect> occlusions = new ArrayList<>();
        for (int i = 0; i < focusIndex; i++) {
            WindowData above = readWindow(blocks.get(i));
            if (above == null) return null;
            if (above.display == 0 && above.visible && above.surface && !above.frame.isEmpty()) {
                occlusions.add(new Rect(above.frame));
            }
        }
        return new PressWindowSnapshot(focusToken, packageName, windowName,
                focused.frame, occlusions);
    }

    Rect regionAt(float x, float y, int width, int height, float density) {
        Rect usable = new Rect(bounds);
        if (!usable.intersect(0, 0, width, height)
                || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(density)
                || density <= 0 || width <= 0 || height <= 0
                || x < usable.left || x >= usable.right || y < usable.top || y >= usable.bottom) {
            return null;
        }
        int regionWidth = Math.min(usable.width(), Math.max(1, Math.round(width * 0.60f)));
        int regionHeight = Math.min(usable.height(), Math.max(1, Math.round(height * 0.32f)));
        int left = Math.max(usable.left, Math.min(Math.round(x - regionWidth / 2f), usable.right - regionWidth));
        int top = Math.max(usable.top, Math.min(Math.round(y - regionHeight / 2f), usable.bottom - regionHeight));
        Rect region = new Rect(left, top, left + regionWidth, top + regionHeight);
        for (Rect obstacle : occlusions) {
            if (!Rect.intersects(region, obstacle)) continue;
            if (obstacle.contains(Math.round(x), Math.round(y))) return null;
            if (x < obstacle.left) region.right = Math.min(region.right, obstacle.left);
            else if (x >= obstacle.right) region.left = Math.max(region.left, obstacle.right);
            else if (y < obstacle.top) region.bottom = Math.min(region.bottom, obstacle.top);
            else if (y >= obstacle.bottom) region.top = Math.max(region.top, obstacle.bottom);
            if (region.isEmpty()) return null;
        }
        return permits(region) ? region : null;
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

    private static WindowData readWindow(String block) {
        Matcher displayMatcher = DISPLAY.matcher(block);
        Matcher frameMatcher = FRAME_RECT.matcher(block);
        boolean hasFrame = frameMatcher.find();
        if (!hasFrame) {
            frameMatcher = FRAME_PAIR.matcher(block);
            hasFrame = frameMatcher.find();
        }
        Matcher typeMatcher = TYPE.matcher(block);
        if (!displayMatcher.find() || !hasFrame || !typeMatcher.find()
                || !block.contains("mHasSurface=") || !block.contains("isOnScreen=")
                || !block.contains("isVisible=")) return null;
        Rect frame = new Rect(Integer.parseInt(frameMatcher.group(1)), Integer.parseInt(frameMatcher.group(2)),
                Integer.parseInt(frameMatcher.group(3)), Integer.parseInt(frameMatcher.group(4)));
        boolean surface = block.contains("mHasSurface=true");
        boolean visible = block.contains("isOnScreen=true") && block.contains("isVisible=true")
                && !ZERO_ALPHA.matcher(block).find();
        Matcher flagsMatcher = HEX_FLAGS.matcher(block);
        boolean secure = SECURE_FLAG.matcher(block).find();
        if (flagsMatcher.find()) secure |= (Long.parseLong(flagsMatcher.group(1), 16) & FLAG_SECURE) != 0;
        return new WindowData(Integer.parseInt(displayMatcher.group(1)), frame,
                typeMatcher.group(1), surface, visible, secure);
    }

    private static final class WindowData {
        final int display;
        final Rect frame;
        final String type;
        final boolean surface;
        final boolean visible;
        final boolean secure;

        WindowData(int display, Rect frame, String type, boolean surface, boolean visible, boolean secure) {
            this.display = display;
            this.frame = frame;
            this.type = type;
            this.surface = surface;
            this.visible = visible;
            this.secure = secure;
        }
    }
}

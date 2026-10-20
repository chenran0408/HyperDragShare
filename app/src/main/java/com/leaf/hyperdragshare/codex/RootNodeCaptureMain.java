package com.leaf.hyperdragshare.codex;

import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Explicit node lookups in app_process; serving mode never traverses nodes while idle. */
@android.annotation.TargetApi(34)
public final class RootNodeCaptureMain {
    private RootNodeCaptureMain() {}

    private static java.lang.reflect.Constructor<?> connectionConstructor;
    private static java.lang.reflect.Constructor<?> automationConstructor;
    private static java.lang.reflect.Method connect;
    private static java.lang.reflect.Method disconnect;

    // Runs as Root app_process, not as the targetSdk-restricted application process.
    // These exact ROM signatures were exercised by diagnostics/root-node.
    @android.annotation.SuppressLint({"BlockedPrivateApi", "SoonBlockedPrivateApi"})
    public static void main(String[] args) throws Exception {
        boolean serve = args.length == 1 && "--serve".equals(args[0]);
        if (args.length != 0 && !serve) throw new IllegalArgumentException("Expected --serve or no arguments");
        Looper.prepareMainLooper();
        Class<?> connectionType = Class.forName("android.app.IUiAutomationConnection");
        connectionConstructor = Class.forName("android.app.UiAutomationConnection").getDeclaredConstructor();
        automationConstructor = UiAutomation.class.getDeclaredConstructor(Looper.class, connectionType);
        connect = UiAutomation.class.getDeclaredMethod("connectWithTimeout", int.class, long.class);
        disconnect = UiAutomation.class.getDeclaredMethod("disconnect");
        Class.forName(AccessibilityNodeClassifier.class.getName());
        Class.forName(AccessibilityCandidateSelector.class.getName());
        Class.forName(AccessibilityNodeSnapshot.class.getName());
        HandlerThread thread = new HandlerThread("DragShareNodeCapture");
        thread.start();
        Handler watchdog = new Handler(thread.getLooper());
        UiAutomation automation = (UiAutomation) automationConstructor.newInstance(
                thread.getLooper(), connectionConstructor.newInstance());
        Object eventLock = new Object();
        int[] eventVersion = {0};
        boolean connected = false;
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            connect.invoke(automation, UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES, 2000L);
            connected = true;
            automation.setOnAccessibilityEventListener(event -> {
                synchronized (eventLock) {
                    eventVersion[0]++;
                    eventLock.notifyAll();
                }
            });
            if (serve) {
                System.out.println(new JSONObject().put("ready", true).put("pid", android.os.Process.myPid()));
                System.out.flush();
            }
            String line;
            while ((line = input.readLine()) != null) {
                long started = SystemClock.uptimeMillis();
                JSONObject result = captureRequest(new JSONObject(line), automation,
                        eventLock, eventVersion, watchdog);
                result.put("elapsed_ms", SystemClock.uptimeMillis() - started);
                System.out.println(result);
                System.out.flush();
                if (!serve) break;
            }
        } finally {
            // A serving process releases its Binder client by exiting below. Calling disconnect
            // would restore rotation saved at startup, overwriting later user rotation changes.
            // One-shot diagnostics retain their original disconnect behavior.
            if (!serve && connected) disconnect.invoke(automation);
            thread.quitSafely();
        }
        System.exit(0);
    }

    private static JSONObject captureRequest(JSONObject request, UiAutomation automation,
                                            Object eventLock, int[] eventVersion, Handler watchdog)
            throws Exception {
        Runnable timeout = () -> {
            System.err.println("Root node capture exceeded deadline");
            System.exit(2);
        };
        watchdog.postDelayed(timeout, 4000L);
        try {
            long started = SystemClock.uptimeMillis();
            AccessibilityNodeInfo root = awaitRoot(automation, eventLock, eventVersion);
            long rootAt = SystemClock.uptimeMillis();
            try {
                JSONObject result = capture(root, request);
                long capturedAt = SystemClock.uptimeMillis();
                AccessibilityNodeInfo current = automation.getRootInActiveWindow();
                try {
                    if (current == null || current.getWindowId() != root.getWindowId()
                            || !java.util.Objects.equals(string(current.getPackageName()),
                            string(root.getPackageName()))) {
                        result = new JSONObject().put("found", false).put("reason", "window_changed");
                    }
                } finally {
                    if (current != null) current.recycle();
                }
                return result.put("root_ms", rootAt - started)
                        .put("capture_ms", capturedAt - rootAt)
                        .put("verify_ms", SystemClock.uptimeMillis() - capturedAt);
            } finally {
                root.recycle();
            }
        } finally {
            watchdog.removeCallbacks(timeout);
        }
    }
    private static AccessibilityNodeInfo awaitRoot(UiAutomation automation, Object lock,
                                                   int[] version) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 1200L;
        while (true) {
            if (SystemClock.uptimeMillis() >= deadline) {
                throw new IOException("Active window nodes not ready");
            }
            int observed;
            synchronized (lock) { observed = version[0]; }
            automation.clearCache();
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            if (root != null) {
                // Compose can publish an empty host root while its semantics bridge is starting.
                // Wait for its content event instead of treating that host as a finished lookup.
                if (root.getChildCount() > 0) {
                    AccessibilityNodeInfo first = root.getChild(0);
                    if (first != null) {
                        first.recycle();
                        return root;
                    }
                } else if (!android.text.TextUtils.isEmpty(root.getText())
                        || !android.text.TextUtils.isEmpty(root.getContentDescription())) return root;
                root.recycle();
            }
            synchronized (lock) {
                while (observed == version[0]) {
                    long remaining = deadline - SystemClock.uptimeMillis();
                    if (remaining <= 0) throw new IOException("Active window nodes not ready");
                    lock.wait(remaining);
                }
            }
        }
    }

    private static JSONObject capture(AccessibilityNodeInfo root, JSONObject request) throws Exception {
        JSONObject absent = new JSONObject().put("found", false).put("reason", "no_text");
        String source = string(root.getPackageName());
        absent.put("window_package", source);
        if (source == null || source.equals("com.android.systemui")
                || source.equals(BuildConfig.APPLICATION_ID)
                || source.equals("com.miui.contentextension")) return absent.put("reason", "excluded_app");
        JSONArray blocked = request.getJSONArray("blocked");
        for (int i = 0; i < blocked.length(); i++) {
            if (source.equals(blocked.getString(i))) return absent.put("reason", "excluded_app");
        }
        AccessibilityWindowInfo window = root.getWindow();
        if (window != null) {
            try {
                if (window.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return absent.put("reason", "excluded_window");
            } finally {
                window.recycle();
            }
        }
        float x = (float) request.getDouble("x"), y = (float) request.getDouble("y");
        AccessibilityNodeClassifier classifier = new AccessibilityNodeClassifier(
                (float) request.getDouble("density"), request.getInt("width"), request.getInt("height"));
        if (request.optBoolean("early_candidate", false)) {
            long started = SystemClock.uptimeMillis();
            List<AccessibilityNodeSnapshot> pointNodes = new ArrayList<>();
            List<Rect> pointPasswords = new ArrayList<>();
            traverse(root, 0, false, pointNodes, pointPasswords, started, true, x, y);
            AccessibilityCandidateSelector.Selection point = selectText(
                    classifier, pointNodes, pointPasswords, x, y, source);
            // Rich TextViews can have clickable-span children. Their whole subtree is checked
            // below, while unrelated branches stay deferred until the complete lookup.
            if (point != null && (point.candidate.leaf || point.candidate.editable
                    || pointNodes.stream().anyMatch(node -> node.bounds.equals(point.candidate.bounds)
                    && "android.widget.TextView".equals(node.className)
                    && java.util.Objects.equals(node.text, point.candidate.text)))) {
                System.out.println(new JSONObject().put("stage", "candidate")
                        .put("candidate_ms", SystemClock.uptimeMillis() - started));
                System.out.flush();
            }
        }
        List<AccessibilityNodeSnapshot> snapshots = new ArrayList<>();
        List<Rect> passwords = new ArrayList<>();
        traverse(root, 0, false, snapshots, passwords, SystemClock.uptimeMillis(), false, x, y);
        AccessibilityCandidateSelector.Selection selection = selectText(
                classifier, snapshots, passwords, x, y, source);
        absent.put("node_count", snapshots.size());
        if (selection == null) return absent;
        AccessibilityCandidate candidate = selection.candidate;
        return new JSONObject().put("found", true).put("package", source)
                .put("node_count", snapshots.size()).put("text", candidate.text).put("before", selection.beforeText)
                .put("after", selection.afterText)
                .put("left", candidate.bounds.left).put("top", candidate.bounds.top)
                .put("right", candidate.bounds.right).put("bottom", candidate.bounds.bottom);
    }

    private static AccessibilityCandidateSelector.Selection selectText(AccessibilityNodeClassifier classifier,
            List<AccessibilityNodeSnapshot> snapshots, List<Rect> passwords, float x, float y, String source) {
        for (Rect password : passwords) {
            if (password.contains((int) x, (int) y)) return null;
            // An ancestor can expose combined text, so it must not be a candidate either.
            snapshots.removeIf(node -> Rect.intersects(node.bounds, password));
        }
        AccessibilityCandidateSelector.Selection selection = AccessibilityCandidateSelector.select(
                classifier.classify(snapshots), x, y);
        return selection != null && !selection.isImage()
                && source.equals(selection.candidate.sourcePackage) ? selection : null;
    }

    private static void traverse(AccessibilityNodeInfo node, int depth, boolean web,
                                 List<AccessibilityNodeSnapshot> nodes, List<Rect> passwords,
                                 long started, boolean pointOnly, float x, float y) throws IOException {
        if (depth > 64 || nodes.size() >= 4000 || SystemClock.uptimeMillis() - started > 1000L) {
            throw new IOException("Active window node traversal exceeded limit");
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (pointOnly && !bounds.contains((int) x, (int) y)) return;
        if (node.isPassword()) {
            passwords.add(bounds);
            return;
        }
        String className = string(node.getClassName());
        boolean childPointOnly = pointOnly && !"android.widget.TextView".equals(className)
                && !node.isEditable();
        boolean insideWeb = web || "android.webkit.WebView".equals(className);
        int count = node.getChildCount();
        nodes.add(new AccessibilityNodeSnapshot.Builder().bounds(bounds)
                .packageName(string(node.getPackageName())).className(className)
                .viewId(node.getViewIdResourceName()).text(string(node.getText()))
                .contentDescription(string(node.getContentDescription()))
                .visible(node.isVisibleToUser()).editable(node.isEditable())
                .clickable(node.isClickable()).longClickable(node.isLongClickable())
                .important(node.isImportantForAccessibility()).leaf(count == 0)
                .insideWebView(insideWeb).depth(depth).traversalOrder(nodes.size()).build());
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                traverse(child, depth + 1, insideWeb, nodes, passwords, started, childPointOnly, x, y);
            } finally {
                child.recycle();
            }
        }
    }

    private static String string(CharSequence value) {
        return value == null ? null : value.toString();
    }
}

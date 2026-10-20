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

/** One explicitly requested node lookup, launched from the installed module APK by app_process. */
@android.annotation.TargetApi(34)
public final class RootNodeCaptureMain {
    private RootNodeCaptureMain() {}

    // Runs as Root app_process, not as the targetSdk-restricted application process.
    // These exact ROM signatures were exercised by diagnostics/root-node.
    @android.annotation.SuppressLint({"BlockedPrivateApi", "SoonBlockedPrivateApi"})
    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        JSONObject request = new JSONObject(new BufferedReader(new InputStreamReader(
                System.in, StandardCharsets.UTF_8)).readLine());
        HandlerThread thread = new HandlerThread("DragShareNodeCapture");
        thread.start();
        // A Binder call can outlive a client-side su timeout. End this process itself so that
        // AccessibilityManager receives binder death instead of retaining an orphan connection.
        new Handler(thread.getLooper()).postDelayed(() -> {
            System.err.println("Root node capture exceeded deadline");
            System.exit(2);
        }, 4000L);
        Class<?> connectionType = Class.forName("android.app.IUiAutomationConnection");
        Object connection = Class.forName("android.app.UiAutomationConnection")
                .getDeclaredConstructor().newInstance();
        UiAutomation automation = (UiAutomation) UiAutomation.class
                .getDeclaredConstructor(Looper.class, connectionType)
                .newInstance(thread.getLooper(), connection);
        Object eventLock = new Object();
        int[] eventVersion = {0};
        boolean connected = false;
        JSONObject result;
        try {
            UiAutomation.class.getDeclaredMethod("connectWithTimeout", int.class, long.class)
                    .invoke(automation, UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES, 2000L);
            connected = true;
        automation.setOnAccessibilityEventListener(event -> {
            synchronized (eventLock) {
                eventVersion[0]++;
                eventLock.notifyAll();
            }
        });

            AccessibilityNodeInfo root = awaitRoot(automation, eventLock, eventVersion);
            try {
                result = capture(root, request);
                automation.clearCache();
                AccessibilityNodeInfo current = automation.getRootInActiveWindow();
                try {
                    if (current == null || current.getWindowId() != root.getWindowId()
                            || !java.util.Objects.equals(string(current.getPackageName()),
                            string(root.getPackageName()))) {
                        result = new JSONObject().put("found", false);
                    }
                } finally {
                    if (current != null) current.recycle();
                }
            } finally {
                root.recycle();
            }
        } finally {
            // This is intentionally a short connection. Android restores the rotation state saved
            // at connect on disconnect; persistent connections could overwrite much later changes.
            if (connected) UiAutomation.class.getDeclaredMethod("disconnect").invoke(automation);
            thread.quitSafely();
        }
        System.out.println(result);
        System.exit(0);
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
            if (root != null) return root;
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
        JSONObject absent = new JSONObject().put("found", false);
        String source = string(root.getPackageName());
        if (source == null || source.equals("com.android.systemui")
                || source.equals(BuildConfig.APPLICATION_ID)
                || source.equals("com.miui.contentextension")) return absent;
        JSONArray blocked = request.getJSONArray("blocked");
        for (int i = 0; i < blocked.length(); i++) {
            if (source.equals(blocked.getString(i))) return absent;
        }
        AccessibilityWindowInfo window = root.getWindow();
        if (window != null) {
            try {
                if (window.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return absent;
            } finally {
                window.recycle();
            }
        }
        float x = (float) request.getDouble("x"), y = (float) request.getDouble("y");
        List<AccessibilityNodeSnapshot> snapshots = new ArrayList<>();
        List<Rect> passwords = new ArrayList<>();
        traverse(root, 0, false, snapshots, passwords, SystemClock.uptimeMillis());
        for (Rect password : passwords) {
            if (password.contains((int) x, (int) y)) return absent;
            // An ancestor can expose combined text, so it must not be a candidate either.
            snapshots.removeIf(node -> Rect.intersects(node.bounds, password));
        }
        AccessibilityNodeClassifier classifier = new AccessibilityNodeClassifier(
                (float) request.getDouble("density"), request.getInt("width"), request.getInt("height"));
        AccessibilityCandidateSelector.Selection selection = AccessibilityCandidateSelector.select(
                classifier.classify(snapshots), x, y);
        if (selection == null || selection.isImage()) return absent;
        AccessibilityCandidate candidate = selection.candidate;
        if (!source.equals(candidate.sourcePackage)) return absent;
        return new JSONObject().put("found", true).put("package", source)
                .put("text", candidate.text).put("before", selection.beforeText)
                .put("after", selection.afterText)
                .put("left", candidate.bounds.left).put("top", candidate.bounds.top)
                .put("right", candidate.bounds.right).put("bottom", candidate.bounds.bottom);
    }

    private static void traverse(AccessibilityNodeInfo node, int depth, boolean web,
                                 List<AccessibilityNodeSnapshot> nodes, List<Rect> passwords,
                                 long started) throws IOException {
        if (depth > 64 || nodes.size() >= 4000 || SystemClock.uptimeMillis() - started > 1000L) {
            throw new IOException("Active window node traversal exceeded limit");
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (node.isPassword()) {
            passwords.add(bounds);
            return;
        }
        String className = string(node.getClassName());
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
                traverse(child, depth + 1, insideWeb, nodes, passwords, started);
            } finally {
                child.recycle();
            }
        }
    }

    private static String string(CharSequence value) {
        return value == null ? null : value.toString();
    }
}


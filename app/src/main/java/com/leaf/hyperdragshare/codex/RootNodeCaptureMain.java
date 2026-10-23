package com.leaf.hyperdragshare.codex;

import android.app.UiAutomation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Explicit node lookups in app_process; serving mode never traverses nodes while idle. */
@android.annotation.TargetApi(34)
public final class RootNodeCaptureMain {
    private RootNodeCaptureMain() {}

    private static java.lang.reflect.Constructor<?> connectionConstructor;
    private static java.lang.reflect.Constructor<?> automationConstructor;
    private static java.lang.reflect.Method connect;
    private static java.lang.reflect.Method disconnect;

    // WMS identity is used only for a node-less WeChat conversation capture.
    private static final int WINDOW_MANAGER_CAPTURE = -2;
    private static final Set<String> HOLD_PROMPTS = Set.of(
            "按住说话", "按住讲话", "长按说话", "长按讲话", "按住语音输入",
            "长按语音输入", "按住语音说话", "按住开始说话", "按住开始录音",
            "按住录音说话", "长按录音", "按住发语音", "长按发语音", "按住发送语音",
            "按住录音", "按住发送", "松开发送", "松开结束录音", "松开取消",
            "上滑取消", "手指上滑取消发送", "松开转文字", "holdtotalk", "presstotalk",
            "holdtospeak", "pressandholdtospeak", "holdtorecord", "releasetosend",
            "releasetocancel", "releasetostoprecording",
            "slideuptocancel");
    private static final Set<String> VIDEO_SPEED_HOLD_PROMPTS = Set.of(
            "长按倍速播放", "按住倍速", "按住倍速播放", "长按快进", "按住快进",
            "长按加速播放", "按住加速播放");
    private static PressWindowSnapshot pressWindow;
    private static Rect pressRegion;
    private static String pressVerificationError = "";
    private static float pressX, pressY, pressDensity;
    private static int pressWidth, pressHeight;

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
            try {
                connect.invoke(automation, UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES, 2000L);
                AccessibilityServiceInfo info = automation.getServiceInfo();
                info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
                automation.setServiceInfo(info);
                connected = true;
            } catch (ReflectiveOperationException | SecurityException error) {
                if (!serve) throw error;
                Throwable cause = error instanceof java.lang.reflect.InvocationTargetException
                        && error.getCause() != null ? error.getCause() : error;
                String reason;
                if (cause instanceof IllegalStateException && cause.getMessage() != null
                        && cause.getMessage().contains("already registered")) {
                    reason = "界面取词服务正被其他自动化会话占用，请结束该会话后重试";
                } else if (cause instanceof SecurityException) {
                    reason = "系统拒绝界面取词连接，请检查 Root 授权";
                } else {
                    reason = "无法连接界面取词服务：" + cause.getClass().getSimpleName()
                            + (cause.getMessage() == null ? "" : "（" + cause.getMessage() + "）");
                }
                System.err.println("Root node connection failed");
                error.printStackTrace(System.err);
                System.out.println(new JSONObject().put("ready", false).put("error", reason));
                System.out.flush();
            }
            if (connected) {
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

    static JSONObject captureRequest(JSONObject request, UiAutomation automation,
                                            Object eventLock, int[] eventVersion, Handler watchdog)
            throws Exception {
        Runnable timeout = () -> {
            System.err.println("Root node capture exceeded deadline");
            System.exit(2);
        };
        watchdog.postDelayed(timeout, 4000L);
        try {
            if ("capture_ocr_region".equals(request.optString("operation"))) {
                return capturePressOcrRegion(request, automation);
            }
            if ("capture_image".equals(request.optString("operation"))) {
                return captureImageRegion(request, automation);
            }
            if ("verify_window".equals(request.optString("operation"))) {
                if (request.getInt("window_id") == WINDOW_MANAGER_CAPTURE) {
                    Rect region = verifyPressRegion(request.getString("package"));
                    return windowResult(region);
                }
                automation.clearCache();
                AccessibilityNodeInfo current = automation.getRootInActiveWindow();
                try {
                    boolean same = current != null
                            && current.getWindowId() == request.getInt("window_id")
                            && request.getString("package").equals(string(current.getPackageName()));
                    return same ? new JSONObject().put("same_window", true)
                            .put("left", request.getInt("left")).put("top", request.getInt("top"))
                            .put("right", request.getInt("right")).put("bottom", request.getInt("bottom"))
                            : new JSONObject().put("same_window", false);
                } finally {
                    if (current != null) current.recycle();
                }
            }
            if ("verify_source".equals(request.optString("operation"))) {
                if (pressWindow != null) {
                    return new JSONObject().put("same_source",
                            verifyPressRegion(request.getString("package")) != null);
                }
                automation.clearCache();
                AccessibilityNodeInfo current = automation.getRootInActiveWindow();
                AccessibilityWindowInfo window = current == null ? null : current.getWindow();
                try {
                    return new JSONObject().put("same_source", current != null && current.isVisibleToUser()
                            && window != null && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION
                            && request.getString("package").equals(string(current.getPackageName())));
                } finally {
                    if (window != null) window.recycle();
                    if (current != null) current.recycle();
                }
            }
            if ("insert_text".equals(request.optString("operation"))) {
                try { return insertTextAt(automation, request); }
                catch (ReflectiveOperationException | SecurityException | IllegalStateException error) {
                    System.err.println("Root text insertion failed: " + error.getClass().getSimpleName());
                    return insertFailure("系统拒绝输入文字，请检查 Root 连接与当前窗口");
                }
            }
            long started = SystemClock.uptimeMillis();
            boolean inputMethodAtPress = pressHitsInputMethod(automation, request);
            if (inputMethodAtPress) {
                return new JSONObject().put("found", false).put("reason", "excluded_input_method");
            }
            pressWindow = null;
            pressRegion = null;
            boolean press = request.optBoolean("press_lookup", false);
            boolean wechatWindowRejected = false;
            automation.clearCache();
            AccessibilityNodeInfo root = press ? automation.getRootInActiveWindow()
                    : awaitRoot(automation, eventLock, eventVersion);
            if (root == null && press && request.optBoolean("allow_text")) {
                JSONObject conversation = captureUnexposedConversation(request);
                if (conversation.optBoolean("found")
                        || !"no_supported_visible_wechat_window".equals(conversation.optString("reason")))
                    return conversation;
                wechatWindowRejected = true;
                // Keep the existing semantics-startup wait for other applications.
                root = awaitRoot(automation, eventLock, eventVersion);
            }
            if (root == null) return new JSONObject().put("found", false).put("reason", "window_not_ready");
            long rootAt = SystemClock.uptimeMillis();
            try {
                JSONObject result = capture(root, request);
                long capturedAt = SystemClock.uptimeMillis();
                String source = string(root.getPackageName());
                boolean wechatPress = press && "com.tencent.mm".equals(source);
                if (!wechatPress) {
                    AccessibilityNodeInfo current = automation.getRootInActiveWindow();
                    try {
                        if (current == null || current.getWindowId() != root.getWindowId()
                                || !java.util.Objects.equals(string(current.getPackageName()), source)) {
                            result = new JSONObject().put("found", false).put("reason", "window_changed");
                        }
                    } finally {
                        if (current != null) current.recycle();
                    }
                }
                String rejection = result.optString("reason");
                boolean canUseWeChatWindowCapture = press && request.optBoolean("allow_text", true)
                        && "com.tencent.mm".equals(source)
                        && !wechatWindowRejected
                        && !"excluded_input_method".equals(rejection)
                        && !"excluded_app".equals(rejection)
                        && !"excluded_hold_control".equals(rejection)
                        && !"content_type_disabled".equals(rejection)
                        && !"window_changed".equals(rejection);
                if (!result.optBoolean("found") && canUseWeChatWindowCapture) {
                    JSONObject windowCapture = captureUnexposedConversation(request);
                    if (windowCapture.optBoolean("found")
                            || !"no_supported_visible_wechat_window".equals(windowCapture.optString("reason"))) {
                        return windowCapture.put("root_ms", rootAt - started)
                                .put("fallback_reason", rejection.isEmpty() ? "no_content_candidate" : rejection)
                                .put("capture_ms", capturedAt - rootAt)
                                .put("verify_ms", SystemClock.uptimeMillis() - capturedAt);
                    }
                }
                return result.put("window_id", root.getWindowId()).put("root_ms", rootAt - started)
                        .put("capture_ms", capturedAt - rootAt)
                        .put("verify_ms", SystemClock.uptimeMillis() - capturedAt);
            } finally {
                root.recycle();
            }
        } finally {
            watchdog.removeCallbacks(timeout);
        }
    }

    private static PressWindowSnapshot.ParseResult readPressWindow() throws IOException, InterruptedException {
        Process dump = new ProcessBuilder("/system/bin/dumpsys", "-t", "1", "window")
                .redirectErrorStream(true).start();
        try {
            String output = new String(dump.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (dump.waitFor() != 0) throw new IOException("系统窗口查询失败");
            return PressWindowSnapshot.parseDetailed(output);
        } finally {
            dump.destroy();
        }
    }

    private static JSONObject captureUnexposedConversation(JSONObject request) throws Exception {
        JSONObject absent = new JSONObject().put("found", false);
        if (!request.optBoolean("allow_text") || keyguardLocked())
            return absent.put("reason", "text_disabled_or_locked");
        final PressWindowSnapshot.ParseResult parsed;
        try { parsed = readPressWindow(); }
        catch (IOException error) {
            System.err.println("Press window inspection failed: " + error.getMessage());
            return absent.put("reason", "window_inspection_failed");
        }
        if (parsed.snapshot == null) {
            System.err.println("Press window capture rejected reason=" + parsed.reason);
            return absent.put("reason", "window_" + parsed.reason);
        }
        PressWindowSnapshot window = parsed.snapshot;
        if (!PressWindowSnapshot.isSupportedWeChatWindow(window.packageName, window.windowName)) {
            System.err.println("Press window capture rejected reason=unsupported_wechat_component");
            return absent.put("reason", "no_supported_visible_wechat_window");
        }
        JSONArray blocked = request.getJSONArray("blocked");
        for (int i = 0; i < blocked.length(); i++) {
            if (window.packageName.equals(blocked.getString(i))) {
                System.err.println("Press window capture rejected reason=excluded_app");
                return absent.put("reason", "excluded_app");
            }
        }
        Rect region = window.regionAt((float) request.getDouble("x"), (float) request.getDouble("y"),
                request.getInt("width"), request.getInt("height"), (float) request.getDouble("density"));
        if (region == null) {
            System.err.println("Press window capture rejected reason=press_region_obscured");
            return absent.put("reason", "press_region_obscured");
        }
        pressWindow = window;
        pressRegion = region;
        pressX = (float) request.getDouble("x");
        pressY = (float) request.getDouble("y");
        pressDensity = (float) request.getDouble("density");
        pressWidth = request.getInt("width");
        pressHeight = request.getInt("height");
        return new JSONObject().put("found", true).put("package", window.packageName)
                .put("kind", "ocr_region").put("window_id", WINDOW_MANAGER_CAPTURE)
                .put("left", region.left).put("top", region.top)
                .put("right", region.right).put("bottom", region.bottom);
    }

    private static JSONObject windowResult(Rect region) throws Exception {
        if (region == null) return new JSONObject().put("same_window", false);
        return new JSONObject().put("same_window", true).put("left", region.left).put("top", region.top)
                .put("right", region.right).put("bottom", region.bottom);
    }

    private static JSONObject capturePressOcrRegion(JSONObject request, UiAutomation automation)
            throws Exception {
        String source = request.getString("package");
        long screenshotStarted = SystemClock.uptimeMillis();
        Bitmap screenshot;
        try {
            screenshot = automation.takeScreenshot();
        } catch (RuntimeException error) {
            System.err.println("Press screenshot failed: " + error.getClass().getSimpleName());
            return new JSONObject().put("same_window", false).put("reason", "系统无法截取当前微信页面");
        }
        long screenshotMs = SystemClock.uptimeMillis() - screenshotStarted;
        if (screenshot == null) {
            return new JSONObject().put("same_window", false).put("reason", "系统未返回屏幕图像");
        }
        Bitmap cropped = null;
        try {
            long verifyStarted = SystemClock.uptimeMillis();
            Rect region = verifyPressRegion(source);
            long verifyMs = SystemClock.uptimeMillis() - verifyStarted;
            if (region == null) {
                return new JSONObject().put("same_window", false)
                        .put("reason", "按压窗口已变化：" + pressVerificationError);
            }
            Rect crop = new Rect(region);
            if (!crop.intersect(0, 0, screenshot.getWidth(), screenshot.getHeight())) {
                return new JSONObject().put("same_window", false)
                        .put("reason", "安全识字区域已移出屏幕");
            }
            cropped = Bitmap.createBitmap(screenshot, crop.left, crop.top, crop.width(), crop.height());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!cropped.compress(Bitmap.CompressFormat.JPEG, 95, bytes)) {
                return new JSONObject().put("same_window", false).put("reason", "系统无法编码屏幕图像");
            }
            return new JSONObject().put("same_window", true)
                    .put("left", crop.left).put("top", crop.top)
                    .put("right", crop.right).put("bottom", crop.bottom)
                    .put("image_cropped", true)
                    .put("screenshot_ms", screenshotMs).put("verify_ms", verifyMs)
                    .put("image_base64", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
        } finally {
            if (cropped != null && cropped != screenshot) cropped.recycle();
            screenshot.recycle();
        }
    }

    /** Captures and crops an image node inside the helper to avoid a slow full-screen su screencap. */
    private static JSONObject captureImageRegion(JSONObject request, UiAutomation automation)
            throws Exception {
        long screenshotStarted = SystemClock.uptimeMillis();
        Bitmap screenshot;
        try {
            screenshot = automation.takeScreenshot();
        } catch (RuntimeException error) {
            System.err.println("Image screenshot failed: " + error.getClass().getSimpleName());
            return new JSONObject().put("same_window", false).put("reason", "系统无法截取当前页面");
        }
        long screenshotMs = SystemClock.uptimeMillis() - screenshotStarted;
        if (screenshot == null) {
            return new JSONObject().put("same_window", false).put("reason", "系统未返回屏幕图像");
        }
        Bitmap cropped = null;
        try {
            long verifyStarted = SystemClock.uptimeMillis();
            String source = request.getString("package");
            int windowId = request.getInt("window_id");
            automation.clearCache();
            AccessibilityNodeInfo root = automation.getRootInActiveWindow();
            boolean same;
            try {
                same = root != null && root.getWindowId() == windowId
                        && source.equals(string(root.getPackageName()));
            } finally {
                if (root != null) root.recycle();
            }
            long verifyMs = SystemClock.uptimeMillis() - verifyStarted;
            if (!same) {
                return new JSONObject().put("same_window", false).put("reason", "按压窗口已变化")
                        .put("screenshot_ms", screenshotMs).put("verify_ms", verifyMs);
            }
            Rect crop = new Rect(request.getInt("left"), request.getInt("top"),
                    request.getInt("right"), request.getInt("bottom"));
            if (!crop.intersect(0, 0, screenshot.getWidth(), screenshot.getHeight())) {
                return new JSONObject().put("same_window", false).put("reason", "图片区域已移出屏幕");
            }
            cropped = Bitmap.createBitmap(screenshot, crop.left, crop.top, crop.width(), crop.height());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!cropped.compress(Bitmap.CompressFormat.JPEG, 95, bytes)) {
                return new JSONObject().put("same_window", false).put("reason", "系统无法编码屏幕图像");
            }
            return new JSONObject().put("same_window", true)
                    .put("left", crop.left).put("top", crop.top)
                    .put("right", crop.right).put("bottom", crop.bottom)
                    .put("screenshot_ms", screenshotMs).put("verify_ms", verifyMs)
                    .put("image_base64", Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP));
        } finally {
            if (cropped != null && cropped != screenshot) cropped.recycle();
            screenshot.recycle();
        }
    }

    private static Rect verifyPressRegion(String source) throws Exception {
        pressVerificationError = "";
        if (pressWindow == null || pressRegion == null || !source.equals(pressWindow.packageName)
                || !PressWindowSnapshot.isSupportedWeChatWindow(source, pressWindow.windowName)
                || keyguardLocked()) {
            pressVerificationError = "source_changed_or_locked";
            System.err.println("Press window verification rejected reason=" + pressVerificationError);
            return null;
        }
        try {
            PressWindowSnapshot.ParseResult parsed = readPressWindow();
            if (parsed.snapshot == null) {
                pressVerificationError = parsed.reason;
                System.err.println("Press window verification rejected reason=" + pressVerificationError);
                return null;
            }
            PressWindowSnapshot current = parsed.snapshot;
            if (!PressWindowSnapshot.isSupportedWeChatWindow(current.packageName, current.windowName)
                    || !pressWindow.sameWindow(current)) {
                pressVerificationError = "window_identity_changed";
                System.err.println("Press window verification rejected reason=" + pressVerificationError);
                return null;
            }
            Rect region = current.regionAt(pressX, pressY, pressWidth, pressHeight, pressDensity);
            if (region == null) {
                pressVerificationError = "no_visible_region_near_touch";
                System.err.println("Press window verification rejected reason=" + pressVerificationError);
                return null;
            }
            pressWindow = current;
            pressRegion = region;
            return region;
        } catch (IOException error) {
            pressVerificationError = "window_inspection_failed";
            System.err.println("Press window verification failed: " + error.getMessage());
            return null;
        }
    }

    private static boolean keyguardLocked() throws ReflectiveOperationException {
        Class<?> manager = Class.forName("android.view.IWindowManager");
        android.os.IBinder binder = (android.os.IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "window");
        Object service = Class.forName("android.view.IWindowManager$Stub")
                .getMethod("asInterface", android.os.IBinder.class).invoke(null, binder);
        if (service == null) throw new IllegalStateException("Window service unavailable");
        return (Boolean) manager.getMethod("isKeyguardLocked").invoke(service);
    }

    private static JSONObject insertFailure(String reason) throws org.json.JSONException {
        return new JSONObject().put("inserted", false).put("reason", reason);
    }

    private static JSONObject insertTextAt(UiAutomation automation, JSONObject request) throws Exception {
        float x = (float) request.getDouble("x"), y = (float) request.getDouble("y");
        String text = request.getString("text");
        if (!Float.isFinite(x) || !Float.isFinite(y) || text.isEmpty()) return insertFailure("拖入位置或文字无效");
        if (keyguardLocked()) return insertFailure("请先解锁手机再拖入文字");
        automation.clearCache();
        List<AccessibilityWindowInfo> windows = automation.getWindows();
        windows.sort((a, b) -> Integer.compare(b.getLayer(), a.getLayer()));
        try {
            Rect bounds = new Rect();
            for (AccessibilityWindowInfo window : windows) {
                window.getBoundsInScreen(bounds);
                if (!bounds.contains((int) x, (int) y)) continue;
                AccessibilityNodeInfo root = window.getRoot();
                try {
                    // The module's own drop shield is intentionally above the destination.
                    if (root != null && BuildConfig.APPLICATION_ID.equals(string(root.getPackageName()))) continue;
                    if (window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD)
                        return insertFailure("请把文字拖到输入框内，不要拖到键盘上");
                    if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION)
                        return insertFailure("拖入位置被系统窗口遮挡");
                    if (root == null) return insertFailure("无法读取目标应用的输入框");
                    AccessibilityNodeInfo target = editableAt(root, x, y);
                    if (target == null) return insertFailure("此位置不是可编辑输入框");
                    try {
                        if (!target.refresh() || !eligibleEditor(target, x, y))
                            return insertFailure("输入框已变化，请重新拖入");
                        if (keyguardLocked()) return insertFailure("手机已锁定，未填入文字");
                        CharSequence oldValue = target.getText();
                        String old = oldValue == null ? "" : oldValue.toString();
                        int start = target.getTextSelectionStart(), end = target.getTextSelectionEnd();
                        int cursor = insertionStart(old, start, end) + text.length();
                        Bundle arguments = new Bundle();
                        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                insertedText(old, start, end, text));
                        if (!target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments))
                            return insertFailure("目标输入框拒绝填入文字");
                        Bundle selection = new Bundle();
                        selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor);
                        selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor);
                        boolean cursorSet = false;
                        try { cursorSet = target.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection); }
                        catch (SecurityException | IllegalStateException error) {
                            // Text was already accepted; a cursor failure must not report insertion failure.
                            System.err.println("Inserted text; cursor update failed: " + error.getClass().getSimpleName());
                        }
                        return new JSONObject().put("inserted", true).put("cursor_set", cursorSet);
                    } finally { target.recycle(); }
                } finally { if (root != null) root.recycle(); }
            }
            return insertFailure("拖入位置没有应用输入框");
        } finally { for (AccessibilityWindowInfo window : windows) window.recycle(); }
    }

    static boolean eligibleEditor(AccessibilityNodeInfo node, float x, float y) {
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        return node.isVisibleToUser() && node.isEnabled() && node.isEditable() && !node.isPassword()
                && bounds.contains((int) x, (int) y);
    }

    private static AccessibilityNodeInfo editableAt(AccessibilityNodeInfo node, float x, float y) {
        if (node.isPassword()) return null;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                AccessibilityNodeInfo found = editableAt(child, x, y);
                if (found != null) return found;
            } finally { child.recycle(); }
        }
        return eligibleEditor(node, x, y) ? AccessibilityNodeInfo.obtain(node) : null;
    }

    private static int insertionStart(String old, int start, int end) {
        return start >= 0 && end >= 0 && start <= old.length() && end <= old.length()
                ? Math.min(start, end) : old.length();
    }

    static String insertedText(String old, int start, int end, String insertion) {
        int first = insertionStart(old, start, end);
        int last = start >= 0 && end >= 0 && start <= old.length() && end <= old.length()
                ? Math.max(start, end) : old.length();
        return old.substring(0, first) + insertion + old.substring(last);
    }

    private static boolean pressHitsInputMethod(UiAutomation automation, JSONObject request)
            throws org.json.JSONException {
        if (!request.optBoolean("press_lookup", false)) return false;
        automation.clearCache();
        List<AccessibilityWindowInfo> windows = automation.getWindows();
        try {
            return inputMethodAtPoint(windows, (float) request.getDouble("x"),
                    (float) request.getDouble("y"));
        } finally {
            for (AccessibilityWindowInfo window : windows) window.recycle();
        }
    }

    static boolean inputMethodAtPoint(List<AccessibilityWindowInfo> windows, float x, float y) {
        Rect bounds = new Rect();
        for (AccessibilityWindowInfo window : windows) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue;
            window.getBoundsInScreen(bounds);
            if (x >= bounds.left && x < bounds.right && y >= bounds.top && y < bounds.bottom) {
                return true;
            }
        }
        return false;
    }

    private static AccessibilityNodeInfo awaitRoot(UiAutomation automation, Object lock,
                                                   int[] version) throws InterruptedException {
        long deadline = SystemClock.uptimeMillis() + 1200L;
        while (true) {
            if (SystemClock.uptimeMillis() >= deadline) {
                return null;
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
                    if (remaining <= 0) return null;
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
        boolean pressLookup = request.optBoolean("press_lookup", false);
        boolean allowText = request.optBoolean("allow_text", true);
        boolean allowImages = request.optBoolean("allow_images", false);
        float x = (float) request.getDouble("x"), y = (float) request.getDouble("y");
        Rect windowBounds = new Rect();
        AccessibilityWindowInfo window = root.getWindow();
        if (pressLookup && window == null) return absent.put("reason", "root_window_missing");
        if (pressLookup && !root.isVisibleToUser()) {
            window.recycle();
            return absent.put("reason", "root_not_visible");
        }
        if (window != null) {
            try {
                if (window.getType() == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
                        || (pressLookup && window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION)) {
                    return absent.put("reason", "excluded_window_type");
                }
                window.getBoundsInScreen(windowBounds);
                if (pressLookup && !windowBounds.contains((int) x, (int) y)) {
                    return absent.put("reason", "outside_window");
                }
            } finally {
                window.recycle();
            }
        }
        float density = (float) request.getDouble("density");
        AccessibilityNodeClassifier classifier = new AccessibilityNodeClassifier(
                density, request.getInt("width"), request.getInt("height"));
        if (pressLookup || (request.optBoolean("early_candidate", false) && allowText)) {
            long started = SystemClock.uptimeMillis();
            List<AccessibilityNodeSnapshot> pointNodes = new ArrayList<>();
            List<Rect> pointPasswords = new ArrayList<>();
            traverse(root, 0, false, pointNodes, pointPasswords, started, true, x, y);
            // Exclude before either text selection or the empty-region OCR path. A label
            // alone may describe ordinary text; the node or its parent must be a control.
            if (pressLookup && holdControlAtPoint(pointNodes, x, y, source)) {
                return absent.put("reason", "excluded_hold_control");
            }
            AccessibilityCandidateSelector.Selection point = selectContent(
                    classifier, pointNodes, pointPasswords, x, y, source, true, false);
            // Rich TextViews can have clickable-span children. Their whole subtree is checked
            // below, while unrelated branches stay deferred until the complete lookup.
            if (point != null && (point.candidate.leaf || point.candidate.editable
                    || pointNodes.stream().anyMatch(node -> node.bounds.equals(point.candidate.bounds)
                    && "android.widget.TextView".equals(node.className)
                    && java.util.Objects.equals(node.text, point.candidate.text)))) {
                if (pressLookup) return contentResult(point, source, pointNodes.size());
                System.out.println(new JSONObject().put("stage", "candidate")
                        .put("package", source)
                        .put("candidate_ms", SystemClock.uptimeMillis() - started));
                System.out.flush();
            }
        }
        List<AccessibilityNodeSnapshot> snapshots = new ArrayList<>();
        List<Rect> passwords = new ArrayList<>();
        traverse(root, 0, false, snapshots, passwords, SystemClock.uptimeMillis(), false, x, y);
        AccessibilityCandidateSelector.Selection selection = selectContent(
                classifier, snapshots, passwords, x, y, source,
                pressLookup || allowText, pressLookup || allowImages);
        absent.put("node_count", snapshots.size());
        if (selection == null) {
            if (!pressLookup || !allowImages) return absent;
            Rect region = safeEmptyOcrRegion(x, y, windowBounds, request.getInt("width"),
                    request.getInt("height"), density, request.optBoolean("press_early", false),
                    source, snapshots, passwords);
            if (region == null) return absent.put("reason", "unsafe_ocr_region");
            return new JSONObject().put("found", true).put("package", source).put("kind", "ocr_region")
                    .put("node_count", snapshots.size()).put("left", region.left).put("top", region.top)
                    .put("right", region.right).put("bottom", region.bottom);
        }
        if (selection.isImage() ? !allowImages : !allowText) return absent.put("reason", "content_type_disabled");
        return contentResult(selection, source, snapshots.size());
    }

    static boolean holdControlAtPoint(List<AccessibilityNodeSnapshot> nodes, float x, float y, String source) {
        for (AccessibilityNodeSnapshot label : nodes) {
            if (!label.visible || !source.equals(label.packageName)
                    || !label.bounds.contains((int) x, (int) y)) continue;
            String text = label.text;
            String description = label.contentDescription;
            boolean exactVoicePrompt = holdLabel(text) || holdLabel(description);
            boolean embeddedVoicePrompt = !exactVoicePrompt
                    && (containsHoldLabel(text) || containsHoldLabel(description));
            boolean videoSpeedPrompt = videoSpeedHoldLabel(text) || videoSpeedHoldLabel(description);
            if (!exactVoicePrompt && !embeddedVoicePrompt && !videoSpeedPrompt) continue;
            for (AccessibilityNodeSnapshot control : nodes) {
                if (!control.visible || !source.equals(control.packageName)
                        || !isScopedControl(control, label)) continue;
                boolean interactive = control.editable || control.clickable || control.longClickable;
                if (videoSpeedPrompt && !control.editable && interactive) return true;
                boolean voiceControl = !embeddedVoicePrompt || control.editable
                        || ((control.clickable || control.longClickable)
                        && isVoiceButtonClass(control.className));
                if (!videoSpeedPrompt && interactive && voiceControl) return true;
            }
        }
        return false;
    }

    private static boolean isScopedControl(AccessibilityNodeSnapshot control,
                                           AccessibilityNodeSnapshot label) {
        if (control == label) return true;
        if (control.depth + 1 != label.depth || !control.bounds.contains(label.bounds)) return false;
        long controlArea = (long) control.bounds.width() * control.bounds.height();
        long labelArea = (long) label.bounds.width() * label.bounds.height();
        return labelArea > 0 && controlArea <= labelArea * 16L;
    }

    private static boolean holdLabel(String text) {
        if (text == null) return false;
        return HOLD_PROMPTS.contains(normalizeHoldText(text));
    }

    private static boolean containsHoldLabel(String text) {
        if (text == null) return false;
        String normalized = normalizeHoldText(text);
        for (String phrase : HOLD_PROMPTS) {
            if (normalized.contains(phrase)) return true;
        }
        return false;
    }

    private static String normalizeHoldText(String text) {
        return text.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isVoiceButtonClass(String className) {
        return className != null && className.endsWith("Button");
    }

    private static boolean videoSpeedHoldLabel(String text) {
        if (text == null) return false;
        return VIDEO_SPEED_HOLD_PROMPTS.contains(normalizeHoldText(text));
    }

    private static JSONObject contentResult(AccessibilityCandidateSelector.Selection selection,
                                            String source, int nodeCount) throws org.json.JSONException {
        AccessibilityCandidate candidate = selection.candidate;
        return new JSONObject().put("found", true).put("package", source)
                .put("kind", selection.isImage() ? "image" : "text")
                .put("node_count", nodeCount).put("text", candidate.text).put("before", selection.beforeText)
                .put("after", selection.afterText)
                .put("left", candidate.bounds.left).put("top", candidate.bounds.top)
                .put("right", candidate.bounds.right).put("bottom", candidate.bounds.bottom);
    }

    // Called only after a complete traversal of a visible application window containing the press.
    static Rect safeEmptyOcrRegion(float x, float y, Rect window, int width, int height, float density,
                                  boolean earlyCapture, String source,
                                  List<AccessibilityNodeSnapshot> snapshots, List<Rect> passwords) {
        if (window == null || !Float.isFinite(x) || !Float.isFinite(y) || width <= 0 || height <= 0
                || x < window.left || x >= window.right || y < window.top || y >= window.bottom) return null;
        Rect available = new Rect(window);
        if (!available.intersect(0, 0, width, height)) return null;
        int cropWidth = Math.min(available.width(), Math.max(1, Math.round(width * (earlyCapture ? .92f : .78f))));
        int cropHeight = Math.min(available.height(), Math.max(1, Math.round(height * (earlyCapture ? .92f : .34f))));
        int left = Math.max(available.left, Math.min(Math.round(x) - cropWidth / 2, available.right - cropWidth));
        int top = Math.max(available.top, Math.min(Math.round(y) - cropHeight / 2, available.bottom - cropHeight));
        Rect region = new Rect(left, top, left + cropWidth, top + cropHeight);
        Rect protectedRegion = new Rect(region);
        int margin = Math.max(1, Math.round(density));
        protectedRegion.inset(-margin, -margin);
        for (Rect password : passwords) {
            if (Rect.intersects(protectedRegion, password)) return null;
        }
        for (AccessibilityNodeSnapshot node : snapshots) {
            if (node.visible && Rect.intersects(region, node.bounds) && !source.equals(node.packageName)) return null;
        }
        return region;
    }

    static AccessibilityCandidateSelector.Selection selectContent(AccessibilityNodeClassifier classifier,
            List<AccessibilityNodeSnapshot> snapshots, List<Rect> passwords, float x, float y, String source,
            boolean allowText, boolean allowImages) {
        for (Rect password : passwords) {
            if (password.contains((int) x, (int) y)) return null;
            // An ancestor can expose combined text, so it must not be a candidate either.
            snapshots.removeIf(node -> Rect.intersects(node.bounds, password));
        }
        AccessibilityCandidateSelector.Selection selection = AccessibilityCandidateSelector.select(
                classifier.classify(snapshots), x, y);
        if (selection == null || !source.equals(selection.candidate.sourcePackage)) return null;
        // Images wait for the complete password scan. A generic blank View is not an image request.
        if (selection.isImage()) return allowImages && selection.candidate.strongImage ? selection : null;
        return allowText ? selection : null;
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

package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.leaf.hyperdragshare.gestures.config.OverlayTouchRegions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RootTouchSource {
    interface Listener {
        void onPointerEvent(int action, float x, float y, long eventTime,
                            int rawTouchMajor, int rawTouchMinor, int rawWidthMajor);
    }

    interface StateListener {
        void onState(boolean ready, String reason);
    }

    interface ContactListener {
        boolean onContactEvent(int action, float x, float y, float secondX, float secondY,
                               int pointerCount, long eventTime);
    }

    private static final String TAG = "DragShare/RootInput";
    private static final String READY_LINE = "HDS_INPUT_READY";
    private static final String INVENTORY_MARKER = "HDS_INPUT_INVENTORY";
    private static final String RANGES_MARKER = "HDS_INPUT_RANGES";
    private static final Pattern EVENT_HANDLER = Pattern.compile("\\b(event\\d+)\\b");
    private static final Pattern BLUETOOTH_BUS = Pattern.compile("(?m)^I:.*\\bBus=0005\\b");
    private static final Pattern MAX_X = Pattern.compile("ABS_MT_POSITION_X\\s*:.*?max\\s+(\\d+)");
    private static final Pattern MAX_Y = Pattern.compile("ABS_MT_POSITION_Y\\s*:.*?max\\s+(\\d+)");
    private final Context context;
    private final Listener listener;
    private final StateListener stateListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Run currentRun;
    private volatile boolean ready;
    private volatile String failureReason = "";
    private volatile ContactListener contactListener;
    private volatile boolean twoFingerPressEnabled;
    private boolean statePublished;

    /** Resources are transferred under this lock; no I/O or listener runs with it held. */
    static final class Run {
        final GestureDiagnostics.Stream diagnostics = new GestureDiagnostics.Stream("root_evdev");
        volatile boolean cancelled;
        private Process process;
        volatile EvdevTouchParser parser;
        int rawMaxX;
        int rawMaxY;
        // Main-queue state of the gesture actually delivered to the host.
        boolean pointerDown;
        boolean ignoredGesture;
        boolean rejectedTwoFingerCandidate;
        boolean rejectedContactStreamEnded;
        volatile long frameEventTime;
        float lastX;
        float lastY;

        synchronized boolean attach(Process value) {
            if (cancelled) return false;
            process = value;
            return true;
        }

        synchronized boolean release(Process value) {
            if (process != value) return false;
            process = null;
            return true;
        }

        synchronized Process cancel() {
            cancelled = true;
            Process owned = process;
            process = null;
            return owned;
        }
    }

    RootTouchSource(Context context, Listener listener, StateListener stateListener) {
        this.context = context;
        this.listener = listener;
        this.stateListener = stateListener;
    }

    // Lifecycle and callbacks share the main queue. A listener can reenter stop/start safely.
    void start() {
        onMain(() -> {
            if (currentRun != null) return;
            Run run = new Run();
            currentRun = run;
            publishState(false, "");
            launch("drag-share-root-input", () -> runLoop(run));
        });
    }

    void stop() {
        onMain(() -> {
            Run run = currentRun;
            currentRun = null;
            if (run != null) {
                Process owned = run.cancel();
                if (owned != null) launch("drag-share-input-stop", () -> endProcess(owned));
                if (run.pointerDown) {
                    boolean deliverCancel = !run.ignoredGesture;
                    run.pointerDown = false;
                    run.ignoredGesture = false;
                    ContactListener contacts = contactListener;
                    if (contacts != null) {
                        contacts.onContactEvent(MotionEvent.ACTION_CANCEL, run.lastX, run.lastY,
                                Float.NaN, Float.NaN, 0, SystemClock.uptimeMillis());
                    }
                    if (deliverCancel) {
                        run.diagnostics.mark("ACTION_CANCEL", null, "INPUT_CANCELLED", null,
                                null, null, "root_source_stop_synthetic_cancel");
                        listener.onPointerEvent(MotionEvent.ACTION_CANCEL, run.lastX, run.lastY,
                                SystemClock.uptimeMillis(), 0, 0, 0);
                    }
                }
            }
            if (currentRun == null) publishState(false, "");
        });
    }

    boolean isReady() {
        return ready;
    }

    void setContactListener(ContactListener listener) {
        contactListener = listener;
    }

    void setTwoFingerPressEnabled(boolean enabled) {
        twoFingerPressEnabled = enabled;
        Run run = currentRun;
        if (run != null && run.parser != null) run.parser.setTwoFingerPressEnabled(enabled);
    }

    void clearContactListener(ContactListener listener) {
        if (contactListener == listener) contactListener = null;
    }

    static EvdevTouchParser createParser(EvdevTouchParser.Listener listener,
                                         EvdevTouchParser.ContactListener contactListener,
                                         boolean twoFingerPressEnabled) {
        EvdevTouchParser parser = new EvdevTouchParser(listener, contactListener);
        parser.setTwoFingerPressEnabled(twoFingerPressEnabled);
        return parser;
    }

    String failureReason() {
        return failureReason;
    }

    private void onMain(Runnable action) {
        if (Looper.myLooper() == mainHandler.getLooper()) action.run();
        else mainHandler.post(action);
    }

    private void publishState(boolean value, String reason) {
        if (statePublished && ready == value && Objects.equals(failureReason, reason)) return;
        statePublished = true;
        ready = value;
        failureReason = reason;
        stateListener.onState(value, reason);
    }

    private void runLoop(Run run) {
        String failure = "";
        InputStream stream = null;
        long connectionStarted = SystemClock.elapsedRealtime();
        try {
            run.parser = createParser((action, x, y, major, minor, width) ->
                    onRawFrame(run, action, x, y, major, minor, width),
                    (action, x, y, secondX, secondY, count) -> onRawContactFrame(
                            run, action, x, y, secondX, secondY, count),
                    twoFingerPressEnabled);
            long probeStarted = SystemClock.elapsedRealtime();
            String probe = rootText(run, probeCommand());
            logConnectionStage("device_probe", probeStarted);
            checkRunning(run);
            String devices = sectionBetween(probe, INVENTORY_MARKER, RANGES_MARKER);
            String allRanges = substringAfter(probe, RANGES_MARKER);
            WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics display = new DisplayMetrics();
            manager.getDefaultDisplay().getRealMetrics(display);
            int rotation = manager.getDefaultDisplay().getRotation();
            String devicePath = findTouchDevicePath(devices, allRanges,
                    display.widthPixels, display.heightPixels, rotation);
            if (devicePath == null) throw new IOException("无法确认当前屏幕的触摸设备，请查看诊断日志。");
            String ranges = deviceRanges(allRanges, devicePath);
            if (ranges == null) throw new IOException("无法读取触摸设备坐标范围。");
            run.rawMaxX = parseMaximum(MAX_X, ranges);
            run.rawMaxY = parseMaximum(MAX_Y, ranges);
            if (run.rawMaxX <= 0 || run.rawMaxY <= 0) {
                throw new IOException("触摸设备没有可用的坐标范围。");
            }
            checkRunning(run);
            Process process = startProcess(run, readerCommand(devicePath));
            StringBuilder errors = drainErrors(process);
            stream = process.getInputStream();
            InputStream input = stream;
            String header = readWithDeadline(() -> readLine(input));
            if (!READY_LINE.equals(header)) {
                throw new IOException("Root 输入连接未就绪（" + processStatus(process) + "）：" + errorText(errors));
            }
            checkRunning(run);
            mainHandler.post(() -> {
                if (currentRun == run && !run.cancelled) publishState(true, "");
            });
            logConnectionStage("time_to_ready", connectionStarted);
            DragShareLog.i(TAG, "ready device=" + devicePath + " raw="
                    + run.rawMaxX + "x" + run.rawMaxY + " display="
                    + display.widthPixels + "x" + display.heightPixels + " rotation=" + rotation);
            readEvents(run, stream);
            if (!run.cancelled) {
                throw new IOException("Root 触摸事件流已结束（" + processStatus(process) + "）：" + errorText(errors));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (!run.cancelled) {
                failure = "触摸输入连接被中断。";
                DragShareLog.w(TAG, failure, interrupted);
            }
        } catch (IOException | SecurityException error) {
            if (!run.cancelled) {
                failure = "触摸输入不可用：" + error.getMessage();
                logConnectionStage("time_to_failure", connectionStarted);
                DragShareLog.w(TAG, failure, error);
            }
        } finally {
            Process owned = run.cancel();
            String reason = failure;
            mainHandler.post(() -> {
                if (currentRun != run) return;
                if (run.pointerDown) {
                    boolean deliverCancel = !run.ignoredGesture;
                    run.pointerDown = false;
                    run.ignoredGesture = false;
                    ContactListener contacts = contactListener;
                    if (contacts != null) {
                        contacts.onContactEvent(MotionEvent.ACTION_CANCEL, run.lastX, run.lastY,
                                Float.NaN, Float.NaN, 0, SystemClock.uptimeMillis());
                    }
                    if (deliverCancel) {
                        run.diagnostics.mark("ACTION_CANCEL", null, "INPUT_CANCELLED", null,
                                null, null, "root_reader_closed_synthetic_cancel");
                        listener.onPointerEvent(MotionEvent.ACTION_CANCEL, run.lastX, run.lastY,
                                SystemClock.uptimeMillis(), 0, 0, 0);
                    }
                    if (currentRun != run) return;
                }
                currentRun = null;
                publishState(false, reason);
            });
            if (owned != null) endProcess(owned);
            // Cleanup stays on the old worker and never delays its state notification.
            if (stream != null) closeReadStream(stream);
        }
    }

    private static void checkRunning(Run run) throws InterruptedException {
        if (run.cancelled) throw new InterruptedException("input run cancelled");
    }

    private static Process startProcess(Run run, String command)
            throws IOException, InterruptedException {
        checkRunning(run);
        Process process = new ProcessBuilder("su", "-c", command).start();
        if (!run.attach(process)) {
            endProcess(process);
            throw new InterruptedException("input run cancelled during root startup");
        }
        return process;
    }

    /** A stdin watcher kills only this shell's child when its Java owner disappears. */
    static String supervise(String command) {
        return "exec 3<&0\n" + command + " &\nchild=$!\nguard=\n"
                + "cleanup() { trap - EXIT HUP INT TERM; kill \"$child\" 2>/dev/null; "
                + "if [ -n \"$guard\" ]; then kill \"$guard\" 2>/dev/null; fi; "
                + "wait \"$child\" 2>/dev/null; "
                + "if [ -n \"$guard\" ]; then wait \"$guard\" 2>/dev/null; fi; }\n"
                + "trap cleanup EXIT\ntrap 'exit 143' HUP INT TERM\n"
                + "(trap - EXIT HUP INT TERM; IFS= read -r control <&3; "
                + "kill \"$child\" 2>/dev/null) &\nguard=$!\n"
                + "wait \"$child\"\nresult=$?\nkill \"$guard\" 2>/dev/null\n"
                + "wait \"$guard\" 2>/dev/null\ntrap - EXIT HUP INT TERM\nexit \"$result\"";
    }

    static String readerCommand(String devicePath) {
        if (!devicePath.matches("/dev/input/event[0-9]+")) {
            throw new IllegalArgumentException("Invalid input device path");
        }
        return "exec 4<" + devicePath + " || exit $?\n"
                + "printf '" + READY_LINE + "\\n'\n"
                + supervise("cat <&4 4<&-");
    }

    /** Reads the device inventory and all coordinate ranges under one root authorization. */
    static String probeCommand() {
        return "( printf '" + INVENTORY_MARKER + "\\n'; cat /proc/bus/input/devices || exit $?; "
                + "printf '\\n" + RANGES_MARKER + "\\n'; getevent -lp )";
    }

    static String sectionBetween(CharSequence output, String startMarker, String endMarker)
            throws IOException {
        String value = output == null ? "" : output.toString();
        int start = value.indexOf(startMarker);
        if (start < 0) throw new IOException("Root 输入设备信息格式无效。");
        start += startMarker.length();
        int end = value.indexOf(endMarker, start);
        if (end < 0) throw new IOException("Root 输入坐标信息格式无效。");
        return value.substring(start, end).trim();
    }

    private static String substringAfter(CharSequence output, String marker) throws IOException {
        String value = output == null ? "" : output.toString();
        int position = value.indexOf(marker);
        if (position < 0) throw new IOException("Root 输入坐标信息格式无效。");
        return value.substring(position + marker.length()).trim();
    }

    static String deviceRanges(CharSequence allRanges, String devicePath) {
        if (allRanges == null || devicePath == null) return null;
        String value = allRanges.toString();
        Pattern header = Pattern.compile("(?m)^add device \\d+: "
                + Pattern.quote(devicePath) + "\\s*$");
        Matcher matcher = header.matcher(value);
        if (!matcher.find()) return null;
        int end = value.indexOf("\nadd device ", matcher.end());
        return end < 0 ? value.substring(matcher.start()) : value.substring(matcher.start(), end);
    }

    private static void logConnectionStage(String stage, long startedAt) {
        DragShareLog.i(TAG, "connection stage=" + stage + " elapsed_ms="
                + (SystemClock.elapsedRealtime() - startedAt));
    }

    private String rootText(Run run, String command) throws IOException, InterruptedException {
        Process process = startProcess(run, supervise(command + " </dev/null"));
        StringBuilder errors = drainErrors(process);
        try {
            String text = readWithDeadline(() -> {
                try (InputStream input = process.getInputStream()) {
                    return new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            });
            checkRunning(run);
            if (run.release(process)) {
                int exit = endProcess(process);
                if (exit != 0) {
                    throw new IOException(command + " 退出码=" + exit + "：" + errorText(errors));
                }
            }
            checkRunning(run);
            return text;
        } finally {
            if (run.release(process)) endProcess(process);
        }
    }

    private static <T> T readWithDeadline(Callable<T> read)
            throws IOException, InterruptedException {
        FutureTask<T> task = new FutureTask<>(read);
        launch("drag-share-input-read", task);
        try {
            return task.get(4, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            throw new IOException("Root 输入命令在 4 秒内没有响应。", timeout);
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof IOException) throw (IOException) failed.getCause();
            throw new IllegalStateException("Unexpected input read failure", failed.getCause());
        } finally {
            task.cancel(true);
        }
    }

    /** Called only on workers. Never close stdout while another thread is blocked reading it. */
    static int endProcess(Process process) {
        try {
            process.getOutputStream().close();
        } catch (IOException error) {
            DragShareLog.w(TAG, "Unable to send input supervisor EOF", error);
        }
        try {
            if (process.waitFor(1500, TimeUnit.MILLISECONDS)) return process.exitValue();
            DragShareLog.w(TAG, "Root input supervisor did not exit after stdin EOF");
            process.destroyForcibly();
            if (process.waitFor(500, TimeUnit.MILLISECONDS)) return process.exitValue();
            DragShareLog.w(TAG, "Root input process did not exit after termination");
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            DragShareLog.w(TAG, "Interrupted while stopping root input", interrupted);
        }
        return -1;
    }

    private static StringBuilder drainErrors(Process process) {
        StringBuilder errors = new StringBuilder();
        launch("drag-share-input-errors", () -> {
            try (InputStream error = process.getErrorStream()) {
                byte[] buffer = new byte[512];
                for (int count; (count = error.read(buffer)) != -1;) {
                    synchronized (errors) {
                        if (errors.length() < 4096) {
                            errors.append(new String(buffer, 0, Math.min(count, 4096 - errors.length()),
                                    StandardCharsets.UTF_8));
                        }
                    }
                }
            } catch (IOException error) {
                synchronized (errors) {
                    errors.append(" stderr: ").append(error.getMessage());
                }
                DragShareLog.w(TAG, "Unable to read root input stderr", error);
            }
        });
        return errors;
    }

    private static String processStatus(Process process) {
        return process.isAlive() ? "Root 监督进程尚未退出" : "退出码=" + process.exitValue();
    }

    private static String errorText(StringBuilder errors) {
        synchronized (errors) {
            return errors.length() == 0 ? "Root 进程未返回错误详情" : errors.toString().trim();
        }
    }

    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int value; (value = input.read()) != -1;) {
            if (value == '\n') return bytes.toString(StandardCharsets.UTF_8.name());
            if (bytes.size() >= 128) throw new IOException("Invalid root input ready header");
            bytes.write(value);
        }
        return bytes.toString(StandardCharsets.UTF_8.name());
    }

    private void readEvents(Run run, InputStream stream) throws IOException {
        int size = android.os.Process.is64Bit() ? 24 : 16;
        byte[] event = new byte[size];
        while (!run.cancelled) {
            int offset = 0;
            while (!run.cancelled && offset < size) {
                int count = stream.read(event, offset, size - offset);
                if (count < 0) return;
                offset += count;
            }
            if (run.cancelled) return;
            ByteBuffer buffer = ByteBuffer.wrap(event).order(ByteOrder.LITTLE_ENDIAN);
            buffer.position(size == 24 ? 16 : 8);
            run.parser.consume(buffer.getShort() & 0xffff, buffer.getShort() & 0xffff, buffer.getInt());
        }
    }

    private void onRawFrame(Run run, int action, float rawX, float rawY,
                            int major, int minor, int width) {
        long eventTime = run.frameEventTime > 0L
                ? run.frameEventTime : SystemClock.uptimeMillis();
        int contacts = GestureDiagnostics.enabled() && run.parser != null
                ? run.parser.diagnosticPointerCount() : -1;
        mainHandler.post(() -> {
            if (currentRun != run || run.cancelled) return;
            if (run.rejectedTwoFingerCandidate) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    run.pointerDown = false;
                    if (run.rejectedContactStreamEnded) {
                        run.rejectedTwoFingerCandidate = false;
                        run.rejectedContactStreamEnded = false;
                    }
                }
                return;
            }
            WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            manager.getDefaultDisplay().getRealMetrics(metrics);
            float[] point = GestureMath.mapRawPoint(rawX, rawY, run.rawMaxX, run.rawMaxY,
                    metrics.widthPixels, metrics.heightPixels, manager.getDefaultDisplay().getRotation());
            if (action == MotionEvent.ACTION_DOWN) {
                run.pointerDown = true;
                run.ignoredGesture = OverlayTouchRegions.contains(point[0], point[1]);
                if (run.ignoredGesture) {
                    DragShareLog.i(TAG, "ignoring gesture started inside module overlay bounds");
                }
            }
            else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) run.pointerDown = false;
            run.diagnostics.root(action, point[0], point[1], rawX, rawY, -1, eventTime, contacts,
                    null, run.ignoredGesture ? "IGNORED" : "PHYSICAL_INPUT", null, null, null,
                    run.ignoredGesture ? "module_overlay_exclusion"
                            : action == MotionEvent.ACTION_CANCEL ? "evdev_multiple_contacts" : "evdev_frame");
            if (run.ignoredGesture) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    run.ignoredGesture = false;
                }
                return;
            }
            run.lastX = point[0];
            run.lastY = point[1];
            listener.onPointerEvent(action, point[0], point[1], eventTime, major, minor, width);
        });
    }

    private void onRawContactFrame(Run run, int action, float rawX, float rawY,
                                   float secondRawX, float secondRawY, int pointerCount) {
        long eventTime = SystemClock.uptimeMillis();
        run.frameEventTime = eventTime;
        mainHandler.post(() -> {
            if (currentRun != run || run.cancelled) return;
            if (run.rejectedTwoFingerCandidate) {
                if (pointerCount == 0) run.rejectedContactStreamEnded = true;
                else if (run.rejectedContactStreamEnded && action == MotionEvent.ACTION_DOWN) {
                    run.rejectedTwoFingerCandidate = false;
                    run.rejectedContactStreamEnded = false;
                }
            }
            float x = Float.NaN;
            float y = Float.NaN;
            float secondX = Float.NaN;
            float secondY = Float.NaN;
            if (pointerCount > 0 && Float.isFinite(rawX) && Float.isFinite(rawY)) {
                WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                DisplayMetrics metrics = new DisplayMetrics();
                manager.getDefaultDisplay().getRealMetrics(metrics);
                int rotation = manager.getDefaultDisplay().getRotation();
                float[] point = GestureMath.mapRawPoint(rawX, rawY, run.rawMaxX, run.rawMaxY,
                        metrics.widthPixels, metrics.heightPixels, rotation);
                x = point[0];
                y = point[1];
                if (Float.isFinite(secondRawX) && Float.isFinite(secondRawY)) {
                    float[] second = GestureMath.mapRawPoint(secondRawX, secondRawY,
                            run.rawMaxX, run.rawMaxY, metrics.widthPixels, metrics.heightPixels, rotation);
                    secondX = second[0];
                    secondY = second[1];
                }
            }
            if (action == MotionEvent.ACTION_DOWN && pointerCount > 0
                    && Float.isFinite(x) && Float.isFinite(y)
                    && OverlayTouchRegions.contains(x, y)) {
                run.ignoredGesture = true;
            }
            ContactListener target = contactListener;
            if (target == null || run.ignoredGesture) return;
            boolean accepted = target.onContactEvent(
                    action, x, y, secondX, secondY, pointerCount, eventTime);
            handleContactDecision(run, action, x, y, eventTime, pointerCount,
                    rawX, rawY, accepted);
        });
    }

    void handleContactDecision(Run run, int action, float x, float y, long eventTime,
                               int pointerCount, float rawX, float rawY, boolean accepted) {
        if (accepted) return;
        boolean rejectedSecondPointer = (action == MotionEvent.ACTION_POINTER_DOWN
                || action == MotionEvent.ACTION_DOWN) && pointerCount == 2;
        boolean unconfirmedPointerReduction = action == MotionEvent.ACTION_POINTER_UP
                && pointerCount == 1;
        if (rejectedSecondPointer || unconfirmedPointerReduction) {
            rejectTwoFingerCandidate(run, x, y, eventTime, pointerCount, rawX, rawY);
        }
    }

    void rejectTwoFingerCandidate(Run run, float x, float y, long eventTime,
                                  int pointerCount, float rawX, float rawY) {
        run.rejectedTwoFingerCandidate = true;
        if (!run.pointerDown) return;
        run.pointerDown = false;
        run.diagnostics.root(MotionEvent.ACTION_CANCEL, x, y, rawX, rawY, -1,
                eventTime, pointerCount, null, "CANCELLED", null, null, null,
                "two_finger_candidate_rejected");
        listener.onPointerEvent(MotionEvent.ACTION_CANCEL, x, y, eventTime, 0, 0, 0);
    }

    static String findTouchDevicePath(CharSequence devices) {
        if (devices == null) return null;
        for (String block : devices.toString().split("(?:\\r?\\n){2,}")) {
            boolean direct = block.contains("B: PROP=2") || block.contains("INPUT_PROP_DIRECT");
            boolean touchNamed = block.toLowerCase(Locale.ROOT).contains("touch");
            if (!block.contains("B: ABS=") || (!direct && !touchNamed)) continue;
            for (String line : block.split("\\n")) {
                if (!line.startsWith("H: Handlers=")) continue;
                Matcher matcher = EVENT_HANDLER.matcher(line);
                if (matcher.find()) return "/dev/input/" + matcher.group(1);
            }
        }
        return null;
    }

    static String findTouchDevicePath(CharSequence devices, CharSequence ranges,
                                     int width, int height, int rotation) {
        if (devices == null || ranges == null || width <= 0 || height <= 0) return null;
        boolean rotated = rotation == android.view.Surface.ROTATION_90
                || rotation == android.view.Surface.ROTATION_270;
        double displayRatio = rotated ? (double) height / width : (double) width / height;
        String best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        boolean ambiguous = false;
        for (String block : devices.toString().split("(?:\\r?\\n){2,}")) {
            // A Bluetooth watch can advertise DIRECT and MT axes too.
            if (BLUETOOTH_BUS.matcher(block).find()) continue;
            String path = findTouchDevicePath(block);
            if (path == null) continue;
            String deviceRange = deviceRanges(ranges, path);
            if (deviceRange == null) continue;
            int maxX = parseMaximum(MAX_X, deviceRange);
            int maxY = parseMaximum(MAX_Y, deviceRange);
            if (maxX <= 0 || maxY <= 0) continue;
            // Multiple touch panels are independently exposed. Compare natural display
            // coordinates, never their event-number order or a fixed device name.
            double score = Math.abs(Math.log(((maxX + 1d) / (maxY + 1d)) / displayRatio));
            if (score < bestScore - 0.000001d) {
                best = path;
                bestScore = score;
                ambiguous = false;
            } else if (Math.abs(score - bestScore) <= 0.000001d) {
                ambiguous = true;
            }
        }
        return ambiguous ? null : best;
    }

    private static int parseMaximum(Pattern pattern, CharSequence value) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private static void closeReadStream(InputStream stream) {
        try {
            stream.close();
        } catch (IOException error) {
            DragShareLog.w(TAG, "Unable to close root input stream", error);
        }
    }

    private static void launch(String name, Runnable action) {
        Thread thread = new Thread(action, name);
        thread.setDaemon(true);
        thread.start();
    }
}

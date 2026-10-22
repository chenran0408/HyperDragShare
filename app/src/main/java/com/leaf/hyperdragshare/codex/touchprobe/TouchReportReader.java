package com.leaf.hyperdragshare.codex.touchprobe;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Root-backed, read-only touch-report reader. No process memory is written. */
public final class TouchReportReader implements AutoCloseable {
    public interface Listener {
        void onReady(ConnectionInfo connection);
        void onFrame(TouchReportLayout.Frame frame);
        void onFailure(String message, Throwable error);
    }

    private static final long DISCOVERY_TIMEOUT_MS = 6_000L;
    private static final ExecutorService DISCOVERY_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "touch-probe-discovery");
                thread.setDaemon(true);
                return thread;
            });

    private final Listener listener;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Process process;
    private Thread worker;

    public TouchReportReader(Listener listener) {
        if (listener == null) throw new IllegalArgumentException("Missing listener");
        this.listener = listener;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) return;
        worker = new Thread(this::run, "touch-probe-reader");
        worker.setDaemon(true);
        worker.start();
    }

    private void run() {
        Process active = null;
        try {
            String discovery = runDiscovery();
            TouchDeviceMapParser.DeviceMap map = TouchDeviceMapParser.parse(discovery);
            if (!running.get()) return;
            TouchProfileRegistry.Selection selection = TouchProfileRegistry.resolve(map);
            active = new ProcessBuilder("su", "-c", readerScript(map, selection.profile))
                    .redirectErrorStream(false)
                    .start();
            process = active;
            drainErrors(active.getErrorStream());
            InputStream input = active.getInputStream();
            String header = readLine(input);
            if (!header.startsWith("READY ")) {
                throw new IOException("Root reader did not become ready: " + header);
            }
            String pointer = normalizeAddress(parseHeaderValue(header, "pointer"));
            String expected = normalizeAddress(parseHeaderValue(header, "expected"));
            ConnectionInfo info = new ConnectionInfo(map, selection, pointer, expected);
            listener.onReady(info);

            byte[] report = new byte[selection.profile.reportSize];
            while (running.get()) {
                readFully(input, report);
                if (!running.get()) break;
                listener.onFrame(TouchReportLayout.parseFrame(
                        report,
                        selection.profile.reportSize,
                        selection.profile.slotCount,
                        selection.profile.slotStride,
                        selection.profile.metaOffset));
            }
        } catch (Throwable error) {
            if (running.get()) listener.onFailure(error.getMessage(), error);
        } finally {
            running.set(false);
            Process owned = active;
            process = null;
            if (owned != null) {
                try {
                    owned.getOutputStream().close();
                } catch (IOException ignored) {}
                owned.destroy();
            }
        }
    }

    private static String runDiscovery() throws Exception {
        Process process = new ProcessBuilder("su", "-c", discoveryScript())
                .redirectErrorStream(true)
                .start();
        Future<byte[]> read = DISCOVERY_EXECUTOR.submit(() -> {
            try (InputStream input = process.getInputStream()) {
                return input.readAllBytes();
            }
        });
        try {
            if (!process.waitFor(DISCOVERY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("Root touch discovery timed out");
            }
            byte[] output = read.get(2, TimeUnit.SECONDS);
            return new String(output, StandardCharsets.UTF_8);
        } finally {
            read.cancel(true);
            process.destroy();
        }
    }

    static String discoveryScript() {
        return "echo '===ROOT==='; id; "
                + "echo '===PROPS==='; "
                + "for key in ro.product.manufacturer ro.product.brand ro.product.model "
                + "ro.product.device ro.mi.os.version.name "
                + "ro.mi.os.version.incremental ro.build.fingerprint ro.build.version.release "
                + "ro.build.version.sdk; do "
                + "value=$(getprop \"$key\" 2>/dev/null || true); "
                + "printf '%s=%s\\n' \"$key\" \"$value\"; done; "
                + "echo '===COMMANDS==='; "
                + "for tool in awk cat getprop pidof dd od tr sha256sum sleep stat; do "
                + "if command -v \"$tool\" >/dev/null 2>&1; then "
                + "printf '%s=available\\n' \"$tool\"; else printf '%s=missing\\n' \"$tool\"; fi; done; "
                + "echo '===PID==='; pidof " + TouchReportLayout.SERVICE_NAME + "; "
                + "pid=$(pidof " + TouchReportLayout.SERVICE_NAME
                + " | awk '{print $1}'); [ -n \"$pid\" ] || exit 3; "
                + "echo '===MAPS==='; cat /proc/$pid/maps; "
                + "echo '===HASH==='; "
                + "library=$(awk '/" + TouchReportLayout.LIBRARY_NAME
                + "/{print $6; exit}' /proc/$pid/maps); "
                + "if [ -n \"$library\" ] && command -v sha256sum >/dev/null 2>&1; then "
                + "sha256sum \"$library\" 2>/dev/null || true; fi; "
                + "echo '===LIBRARY_SIZE==='; "
                + "if [ -n \"$library\" ]; then stat -c %s \"$library\" 2>/dev/null || true; fi; "
                + "echo '===CODE_SIGNATURE==='; "
                + "if [ -n \"$library\" ] && command -v dd >/dev/null 2>&1 "
                + "&& command -v sha256sum >/dev/null 2>&1; then "
                + "text_hash=$(dd if=\"$library\" iflag=skip_bytes,count_bytes "
                + "skip=" + TouchProfileRegistry.REFERENCE.textFileOffset
                + " count=" + TouchProfileRegistry.REFERENCE.textByteCount
                + " bs=4096 2>/dev/null | sha256sum | awk '{print $1}'); "
                + "[ -n \"$text_hash\" ] && printf 'text_sha256=%s\\n' \"$text_hash\" || true; fi";
    }

    static String readerScript(
            TouchDeviceMapParser.DeviceMap map,
            TouchProfileRegistry.Profile profile) {
        String global = TouchDeviceMapParser.addressHex(
                map.baseAddress.add(java.math.BigInteger.valueOf(profile.globalPointerRva)));
        String expected = TouchDeviceMapParser.addressHex(
                map.baseAddress.add(java.math.BigInteger.valueOf(profile.expectedReportRva)));
        return "set -u; pid=" + map.pid + "; global=" + global + "; expected=" + expected + "; "
                + "gp_hex=$(dd if=/proc/$pid/mem iflag=skip_bytes,count_bytes skip=$global "
                + "count=8 bs=8 2>/dev/null | od -An -tx8 -N8 | tr -d ' \\n') || exit 4; "
                + "case \"$gp_hex\" in ''|*[!0-9a-fA-F]*) exit 5;; esac; "
                + "pointer=0x$gp_hex; "
                + "printf 'READY pid=%s global=%s pointer=%s expected=%s\\n' "
                + "\"$pid\" \"$global\" \"$pointer\" \"$expected\"; "
                + "while :; do "
                + "dd if=/proc/$pid/mem iflag=skip_bytes,count_bytes skip=$pointer "
                + "count=" + profile.reportSize + " bs=" + profile.reportSize
                + " 2>/dev/null || exit 6; sleep 0.05; done";
    }

    /** Re-runs discovery so exports never reuse a PID, mapping range, or library hash. */
    public static TouchDeviceMapParser.DeviceMap discover() throws Exception {
        return TouchDeviceMapParser.parse(runDiscovery());
    }

    private static String parseHeaderValue(String header, String name) throws IOException {
        String marker = name + "=";
        int start = header.indexOf(marker);
        if (start < 0) throw new IOException("Missing " + name + " in reader header");
        start += marker.length();
        int end = header.indexOf(' ', start);
        return end < 0 ? header.substring(start) : header.substring(start, end);
    }

    static String normalizeAddress(String value) throws IOException {
        try {
            long address = Long.decode(value);
            if (address < 0) throw new NumberFormatException("Negative address");
            return "0x" + Long.toHexString(address);
        } catch (NumberFormatException error) {
            throw new IOException("Invalid pointer address: " + value, error);
        }
    }

    private void drainErrors(InputStream input) {
        Thread thread = new Thread(() -> {
            try (InputStream stream = input) {
                byte[] buffer = new byte[512];
                for (int count; (count = stream.read(buffer)) >= 0;) {
                    if (!running.get()) break;
                }
            } catch (IOException ignored) {}
        }, "touch-probe-reader-errors");
        thread.setDaemon(true);
        thread.start();
    }

    private static String readLine(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int value; (value = input.read()) >= 0;) {
            if (value == '\n') return output.toString(StandardCharsets.UTF_8.name()).trim();
            if (output.size() >= 4096) throw new IOException("Reader header is too long");
            output.write(value);
        }
        throw new IOException("Root touch reader ended before its header");
    }

    private static void readFully(InputStream input, byte[] target) throws IOException {
        int offset = 0;
        while (offset < target.length) {
            int count = input.read(target, offset, target.length - offset);
            if (count < 0) throw new IOException("Root touch report stream ended");
            offset += count;
        }
    }

    @Override
    public void close() {
        running.set(false);
        Process owned = process;
        process = null;
        if (owned != null) {
            try {
                owned.getOutputStream().close();
            } catch (IOException ignored) {}
            owned.destroy();
        }
        Thread active = worker;
        if (active != null && active != Thread.currentThread()) {
            active.interrupt();
        }
    }

    public static final class ConnectionInfo {
        public final TouchDeviceMapParser.DeviceMap map;
        public final TouchProfileRegistry.Selection selection;
        public final String pointerAddress;
        public final String expectedAddress;
        public final boolean pointerMatchesProfile;

        ConnectionInfo(
                TouchDeviceMapParser.DeviceMap map,
                String pointerAddress,
                String expectedAddress,
                boolean pointerMatchesProfile) {
            this(
                    map,
                    TouchProfileRegistry.resolve(map),
                    pointerAddress,
                    expectedAddress,
                    pointerMatchesProfile);
        }

        ConnectionInfo(
                TouchDeviceMapParser.DeviceMap map,
                TouchProfileRegistry.Selection selection,
                String pointerAddress,
                String expectedAddress) {
            this(
                    map,
                    selection,
                    pointerAddress,
                    expectedAddress,
                    pointerAddress != null && pointerAddress.equalsIgnoreCase(expectedAddress));
        }

        private ConnectionInfo(
                TouchDeviceMapParser.DeviceMap map,
                TouchProfileRegistry.Selection selection,
                String pointerAddress,
                String expectedAddress,
                boolean pointerMatchesProfile) {
            this.map = map;
            this.selection = selection;
            this.pointerAddress = pointerAddress;
            this.expectedAddress = expectedAddress;
            this.pointerMatchesProfile = pointerMatchesProfile;
        }
    }
}

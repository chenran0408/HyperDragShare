package com.leaf.hyperdragshare.codex;

import android.content.Context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Applies the ROM's existing one-key-clean app lock to this module and its Portal host. */
final class RootProcessKeepAlive {
    private static final String TAG = "DragShare/RootKeepAlive";
    private static final int MAIN_USER_ID = 0;
    private static final long TIMEOUT_SECONDS = 6L;

    private RootProcessKeepAlive() {}

    static final class Result {
        final boolean ready;
        final String detail;

        Result(boolean ready, String detail) {
            this.ready = ready;
            this.detail = detail;
        }
    }

    static Result ensureLocked(Context context) {
        String apk = context.getApplicationInfo().sourceDir;
        String command = "CLASSPATH=" + shellQuote(apk)
                + " /system/bin/app_process /system/bin "
                + RootProcessKeepAliveMain.class.getName()
                + " --enable " + MAIN_USER_ID;
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            String output;
            try (java.io.InputStream stream = process.getInputStream()) {
                if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return failed("ProcessManager 锁定请求超时");
                }
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            if (process.exitValue() != 0 || !output.contains("ROOT_LOCKS_READY")) {
                return failed(output.isEmpty()
                        ? "ProcessManager 锁定失败（退出码 " + process.exitValue() + "）"
                        : output);
            }
            DragShareLog.i(TAG, output);
            return new Result(true, output);
        } catch (IOException error) {
            return failed("无法启动 Root ProcessManager 检查：" + error.getMessage());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return failed("Root ProcessManager 检查被中断");
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static Result failed(String detail) {
        DragShareLog.e(TAG, detail, null);
        return new Result(false, detail);
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}

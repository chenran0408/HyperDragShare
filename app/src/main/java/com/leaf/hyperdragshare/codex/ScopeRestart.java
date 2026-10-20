package com.leaf.hyperdragshare.codex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Reloads system_server and app scopes by restarting the target ROM's zygote service. */
final class ScopeRestart {
    private static final String TAG = "DragShare/ScopeRestart";

    private ScopeRestart() {}

    static void restartAndroidRuntime() throws IOException {
        DragShareLog.i(TAG, "user confirmed Android runtime restart for android, Taplus and AICR scopes");
        Process process = new ProcessBuilder("su", "-c", "setprop ctl.restart zygote")
                .redirectErrorStream(true)
                .start();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                throw new IOException("Root 授权或软重启请求超时");
            }
            if (process.exitValue() != 0) {
                String reason = new String(
                        process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                throw new IOException(reason.isEmpty() ? "Root 未允许软重启" : reason);
            }
            DragShareLog.i(TAG, "Android runtime restart request accepted");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("软重启请求被中断", error);
        } finally {
            process.destroy();
        }
    }
}

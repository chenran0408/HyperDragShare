package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.os.SystemClock;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** User-requested app scope reload or a restart of the target Android runtime/device. */
final class ScopeRestart {
    private static final String TAG = "DragShare/ScopeRestart";
    private ScopeRestart() {}

    static void reloadAppScopes(Context context) throws IOException {
        int oldPid = ModuleActivation.injectedProcessId(context);
        ModuleActivation.clearPortalReport(context);
        runRoot("killall com.android.systemui && "
                + "{ sidebar_pid=$(pidof com.miui.securitycenter:ui); "
                + "if [ -n \"$sidebar_pid\" ]; then kill $sidebar_pid; fi; } && "
                + "am force-stop --user current com.miui.contentextension && "
                + "am force-stop --user current com.xiaomi.aicr && "
                + ModuleActivation.portalHandshakeCommand(), "应用作用域重载");
        long deadline = SystemClock.uptimeMillis() + 10000L;
        try {
            while (SystemClock.uptimeMillis() < deadline) {
                int pid = ModuleActivation.injectedProcessId(context);
                if (pid > 0 && pid != oldPid && ModuleActivation.isCurrentBuildInjected(context)
                        && ModuleActivation.isCurrentBuildPortalRootGranted(context)
                        && ModuleActivation.isRootInputReady(context)
                        && (!ThumbTriggerSettings.read(context).enabled || ModuleActivation.isPressReaderReady(context))) {
                    DragShareLog.i(TAG, "new portal injection and required input/reader confirmed pid=" + pid);
                    return;
                }
                if (pid > 0 && pid != oldPid && ModuleActivation.isCurrentBuildInjected(context)) {
                    String failure = ModuleActivation.rootInputError(context);
                    if (!failure.isEmpty()) throw new IOException("Root 输入未恢复：" + failure);
                    if (ThumbTriggerSettings.read(context).enabled) {
                        failure = ModuleActivation.pressReaderError(context);
                        if (!failure.isEmpty()) throw new IOException("按压取词未恢复：" + failure);
                    }
                }
                Thread.sleep(100L);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("作用域重载等待被中断", error);
        }
        throw new IOException("新作用域的输入或取词连接尚未就绪，请查看首页状态；系统框架更新需软重启生效");
    }

    static void restartAndroidRuntime() throws IOException {
        runRoot("setprop ctl.restart zygote", "软重启");
    }

    static void restartDevice() throws IOException {
        runRoot("reboot", "整机重启");
    }

    private static void runRoot(String command, String action) throws IOException {
        DragShareLog.i(TAG, "user requested " + action);
        Process process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
        try (InputStream output = process.getInputStream(); OutputStream input = process.getOutputStream()) {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException(action + "请求超时");
            }
            if (process.exitValue() != 0) {
                String reason = new String(output.readAllBytes(), StandardCharsets.UTF_8).trim();
                throw new IOException(reason.isEmpty() ? "Root未允许" + action : reason);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException(action + "请求被中断", error);
        } finally {
            process.destroy();
        }
    }
}

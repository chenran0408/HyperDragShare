package com.leaf.hyperdragshare.codex;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Root app_process entry point for MIUI's existing ProcessManager locked-app list. */
public final class RootProcessKeepAliveMain {
    private static final String PROCESS_MANAGER_CLASS = "miui.process.ProcessManager";
    private static final String[] TARGET_PACKAGES = {
            BuildConfig.APPLICATION_ID,
            MainHook.TAPLUS_PACKAGE
    };

    private RootProcessKeepAliveMain() {}

    public static void main(String[] args) {
        try {
            if (args == null || args.length != 2
                    || !("--enable".equals(args[0]) || "--disable".equals(args[0]))) {
                throw new IllegalArgumentException("usage: --enable|--disable <userId>");
            }
            boolean enable = "--enable".equals(args[0]);
            int userId = Integer.parseInt(args[1]);
            Class<?> manager = Class.forName(PROCESS_MANAGER_CLASS);
            Method isLocked = manager.getMethod("isLockedApplication", String.class, int.class);
            Method updateLocked = manager.getMethod(
                    "updateApplicationLockedState", String.class, int.class, boolean.class);
            int changed = 0;
            for (String packageName : TARGET_PACKAGES) {
                boolean before = (Boolean) isLocked.invoke(null, packageName, userId);
                if (before != enable) {
                    updateLocked.invoke(null, packageName, userId, enable);
                    changed++;
                }
                boolean after = (Boolean) isLocked.invoke(null, packageName, userId);
                if (after != enable) {
                    throw new IllegalStateException("ProcessManager 未确认 " + packageName
                            + " locked=" + enable);
                }
            }
            System.out.println("ROOT_LOCKS_READY action=" + args[0]
                    + " user=" + userId + " changed=" + changed);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            Throwable cause = error instanceof InvocationTargetException
                    && ((InvocationTargetException) error).getCause() != null
                    ? ((InvocationTargetException) error).getCause() : error;
            System.err.println("ROOT_LOCKS_ERROR " + cause.getClass().getSimpleName()
                    + ": " + String.valueOf(cause.getMessage()));
            System.exit(1);
        }
    }

}

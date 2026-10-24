package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.os.Build;
import android.view.Display;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * Best-effort cancellation of the foreground app's current touch stream.
 *
 * <p>The portal process normally observes the gesture without owning it. On
 * system builds that expose the hidden InputManager cancellation/monitor APIs
 * to the portal UID, the current pointer stream is cancelled or pilfered so
 * the original window cannot continue a scroll. The feature is optional
 * because these are hidden, permission-gated APIs.</p>
 */
final class BackgroundTouchBlocker {
    private static final String TAG = "DragShare/InputBlocker";
    private final GestureDiagnostics.Stream diagnostics = new GestureDiagnostics.Stream("background_blocker");
    private static final String MONITOR_NAME = "DragShare background lock";
    private static final long ROOT_COMMAND_TIMEOUT_MILLIS = 1_000L;
    private static final long ROOT_TRANSACTION_WAIT_MILLIS = 50L;
    private static final Pattern SUCCESSFUL_SERVICE_CALL = Pattern.compile(
            "Result:\\s*Parcel\\(\\s*(?:0x[0-9a-fA-F]+:\\s*)?00000000(?=\\s|\\)|$)");

    private final Context context;
    private final MethodInvoker methodInvoker;
    private final RootTouchCanceller rootTouchCanceller;
    private final ExecutorService background = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "drag-share-touch-blocker");
        thread.setDaemon(true);
        return thread;
    });
    private Object inputMonitor;
    private boolean started;
    private boolean nativeInputUnavailable;

    BackgroundTouchBlocker(Context context) {
        this(context, new ReflectiveMethodInvoker(), new RootServiceCallCanceller());
    }

    BackgroundTouchBlocker(Context context, MethodInvoker methodInvoker) {
        this(context, methodInvoker, new RootServiceCallCanceller());
    }

    BackgroundTouchBlocker(
            Context context,
            MethodInvoker methodInvoker,
            RootTouchCanceller rootTouchCanceller) {
        this.context = context == null ? null : context.getApplicationContext();
        this.methodInvoker = methodInvoker == null ? new ReflectiveMethodInvoker() : methodInvoker;
        this.rootTouchCanceller = rootTouchCanceller == null
                ? new RootServiceCallCanceller()
                : rootTouchCanceller;
        this.rootTouchCanceller.prepare();
    }

    /** Removes the hidden-input command from the overlay first-frame path. */
    void startAsync() {
        startAsync(() -> true);
    }

    /** Drops a queued cancellation when its press has already ended or been replaced. */
    void startAsync(BooleanSupplier stillCurrent) {
        background.execute(() -> {
            if (!stillCurrent.getAsBoolean()) return;
            long startedAt = android.os.SystemClock.elapsedRealtime();
            boolean active = start();
            DragShareLog.i(TAG, "async start elapsed_ms="
                    + (android.os.SystemClock.elapsedRealtime() - startedAt) + " active=" + active);
        });
    }

    void stopAsync() {
        if (!background.isShutdown()) background.execute(this::stop);
    }

    void close() {
        if (background.isShutdown()) return;
        background.execute(() -> {
            stop();
            background.shutdown();
        });
    }

    synchronized boolean start() {
        diagnostics.mark("BLOCK_REQUEST", null, "OBSERVATION", null, null, null, "existing_background_block_start");
        if (started) {
            return true;
        }
        if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            log("gesture monitor unavailable on this Android version");
            return false;
        }

        Object inputManager = context.getSystemService(Context.INPUT_SERVICE);
        if (inputManager == null) {
            log("InputManager service is null");
            return false;
        }
        if (!nativeInputUnavailable) {
            Object monitor = null;
            try {
                // Android 14 exposes a simpler hidden operation that cancels the
                // current targets without requiring an input channel to be kept
                // alive. Prefer it when the ROM makes it available.
                try {
                    methodInvoker.invoke(inputManager, "cancelCurrentTouch");
                    diagnostics.mark("BLOCK_RETURN", null, "OBSERVATION", null, null, null,
                            "hidden_cancel_call_returned");
                    started = true;
                    log("foreground touch stream cancelled");
                    return true;
                } catch (Exception unavailable) {
                    log("cancelCurrentTouch unavailable (" + unavailable.getClass().getSimpleName()
                            + "); trying gesture monitor");
                }

                int displayId = resolveDisplayId();

                monitor = methodInvoker.invoke(
                        inputManager,
                        "monitorGestureInput",
                        MONITOR_NAME,
                        displayId);
                if (monitor == null) {
                    log("monitorGestureInput returned null");
                } else {
                    // This sends ACTION_CANCEL to the previous touch target and makes
                    // the monitor the target for the remainder of the pointer stream.
                    methodInvoker.invoke(monitor, "pilferPointers");
                    diagnostics.mark("BLOCK_RETURN", null, "OBSERVATION", null, null, null,
                            "pilfer_call_returned");
                    inputMonitor = monitor;
                    started = true;
                    log("foreground touch stream pilfered");
                    return true;
                }
            } catch (Exception error) {
                if (monitor != null) {
                    try {
                        methodInvoker.invoke(monitor, "dispose");
                    } catch (Exception ignored) {
                        // Keep the original failure as the diagnostic signal.
                    }
                }
                nativeInputUnavailable = true;
                log("gesture monitor unavailable; using root cancellation for this process", error);
            }
            nativeInputUnavailable = true;
        }

        try {
            if (rootTouchCanceller.cancelCurrentTouch()) {
                diagnostics.mark("BLOCK_RETURN", null, "OBSERVATION", null, null, null,
                        "root_cancel_command_reported_success");
                started = true;
                log("foreground touch stream cancelled through root");
                return true;
            }
            log("root cancelCurrentTouch command failed");
        } catch (Exception error) {
            log("unable to cancel foreground touch stream through root", error);
        }
        return false;
    }

    static String rootServiceCallCommand(int transactionCode) {
        if (transactionCode <= 0) {
            throw new IllegalArgumentException("transactionCode must be positive");
        }
        return "uid=$(/system/bin/id -u); "
                + "[ \"$uid\" = 0 ] || exit 1; "
                + "/system/bin/service call input " + transactionCode;
    }

    static boolean isSuccessfulServiceCallResult(String result) {
        return result != null && SUCCESSFUL_SERVICE_CALL.matcher(result).find();
    }

    private int resolveDisplayId() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return Display.DEFAULT_DISPLAY;
        }
        try {
            Display display = context.getDisplay();
            if (display != null) {
                return display.getDisplayId();
            }
        } catch (Exception ignored) {
            // A Service application context on recent Android versions is not visual.
            log("context has no display; using the default display for gesture monitor");
        }
        return Display.DEFAULT_DISPLAY;
    }

    synchronized void stop() {
        Object monitor = inputMonitor;
        inputMonitor = null;
        started = false;
        if (monitor == null) {
            return;
        }
        try {
            methodInvoker.invoke(monitor, "dispose");
        } catch (Exception error) {
            log("unable to dispose gesture monitor", error);
        }
    }

    private static void log(String message) {
        DragShareLog.i(TAG, message);
    }

    private static void log(String message, Throwable error) {
        DragShareLog.w(TAG, message, error);
    }

    interface MethodInvoker {
        Object invoke(Object target, String methodName, Object... args) throws Exception;
    }

    interface RootTouchCanceller {
        boolean cancelCurrentTouch() throws Exception;

        default void prepare() {}
    }

    private static final class RootServiceCallCanceller implements RootTouchCanceller {
        private final FutureTask<Integer> transactionCodeTask = new FutureTask<>(() -> {
            int transactionCode =
                    FrameworkBinderTransactionResolver.resolveCancelCurrentTouchTransactionCode();
            if (transactionCode <= 0) {
                throw new IllegalStateException("Invalid dynamically resolved input transaction");
            }
            log("resolved root input transaction dynamically");
            return transactionCode;
        });
        private boolean transactionCodeTaskStarted;

        @Override
        public synchronized void prepare() {
            if (transactionCodeTaskStarted) {
                return;
            }
            transactionCodeTaskStarted = true;
            Thread thread = new Thread(transactionCodeTask, "drag-share-input-transaction");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public boolean cancelCurrentTouch() throws Exception {
            prepare();
            int transactionCode = awaitTransactionCode();
            if (transactionCode <= 0) {
                return false;
            }
            java.lang.Process process = null;
            try {
                process = new ProcessBuilder(
                        "su",
                        "-c",
                        rootServiceCallCommand(transactionCode))
                        .redirectErrorStream(true)
                        .start();
                if (!process.waitFor(ROOT_COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    return false;
                }
                String result;
                try (InputStream output = process.getInputStream()) {
                    result = new String(output.readAllBytes(), StandardCharsets.UTF_8);
                }
                return process.exitValue() == 0 && isSuccessfulServiceCallResult(result);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw error;
            } finally {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        }

        private int awaitTransactionCode() throws Exception {
            try {
                return transactionCodeTask.get(ROOT_TRANSACTION_WAIT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
                return -1;
            } catch (ExecutionException error) {
                if (error.getCause() instanceof Exception cause) throw cause;
                throw new IllegalStateException("Unable to resolve input transaction", error.getCause());
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw error;
            }
        }
    }

    private static final class ReflectiveMethodInvoker implements MethodInvoker {
        @Override
        public Object invoke(Object target, String methodName, Object... args) throws Exception {
            if (target == null) {
                throw new NullPointerException("target");
            }
            Method method = findMethod(target.getClass(), methodName, args);
            method.setAccessible(true);
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException error) {
                if (error.getCause() instanceof Exception cause) throw cause;
                throw error;
            }
        }

        private static Method findMethod(Class<?> type, String name, Object[] args)
                throws NoSuchMethodException {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) {
                    if (name.equals(method.getName()) && matches(method.getParameterTypes(), args)) {
                        return method;
                    }
                }
            }
            throw new NoSuchMethodException(type.getName() + "." + name);
        }

        private static boolean matches(Class<?>[] parameterTypes, Object[] args) {
            if (parameterTypes.length != args.length) {
                return false;
            }
            for (int index = 0; index < parameterTypes.length; index++) {
                Object value = args[index];
                Class<?> parameter = parameterTypes[index];
                if (value == null) {
                    if (parameter.isPrimitive()) {
                        return false;
                    }
                    continue;
                }
                Class<?> valueType = value.getClass();
                if (parameter.isPrimitive()) {
                    if (!primitiveWrapper(parameter).isAssignableFrom(valueType)) {
                        return false;
                    }
                } else if (!parameter.isAssignableFrom(valueType)) {
                    return false;
                }
            }
            return true;
        }

        private static Class<?> primitiveWrapper(Class<?> primitive) {
            if (primitive == boolean.class) return Boolean.class;
            if (primitive == byte.class) return Byte.class;
            if (primitive == char.class) return Character.class;
            if (primitive == short.class) return Short.class;
            if (primitive == int.class) return Integer.class;
            if (primitive == long.class) return Long.class;
            if (primitive == float.class) return Float.class;
            if (primitive == double.class) return Double.class;
            return Void.class;
        }
    }
}

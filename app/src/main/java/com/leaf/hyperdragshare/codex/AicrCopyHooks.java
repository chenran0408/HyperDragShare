package com.leaf.hyperdragshare.codex;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentProvider;
import android.content.Context;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Optional direct-copy bridge for Xiaomi AICR's text provider. */
final class AicrCopyHooks {
    private static final String TAG = "DragShare/Aicr";
    private static final String PROVIDER_CLASS = "com.xiaomi.aicr.copydirect.TextDirectProvider";
    private static final String METHOD_PROCESS_TEXT = "processText";
    private static final Uri PROVIDER_URI =
            Uri.parse("content://com.leaf.hyperdragshare.codex.share/settings");
    private static final long DUPLICATE_WINDOW_MILLIS = 500L;

    private static long lastLaunchAt;
    private static long lastClipTimestamp;
    private static String lastText;

    private AicrCopyHooks() {}

    static void install(ClassLoader classLoader) {
        Class<?> providerClass;
        try {
            providerClass = XposedHelpers.findClassIfExists(PROVIDER_CLASS, classLoader);
        } catch (RuntimeException | LinkageError error) {
            log("text-direct provider lookup failed; leaving the copy flow unchanged: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
            return;
        }
        if (providerClass == null) {
            log("text-direct provider is absent; leaving the copy flow unchanged");
            return;
        }
        try {
            XposedBridge.hookAllMethods(providerClass, "call", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length < 3 || !METHOD_PROCESS_TEXT.equals(param.args[0])) return;
                    ContentProvider provider = (ContentProvider) param.thisObject;
                    // Clipboard and module RPCs belong to AICR, not the incoming caller.
                    long identity = Binder.clearCallingIdentity();
                    try {
                        Bundle result = openClipboardText(provider.getContext());
                        if (result != null) param.setResult(result);
                    } catch (RuntimeException error) {
                        log("copy handling failed; keeping the original copy flow: "
                                + error.getClass().getSimpleName() + ": " + error.getMessage());
                        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(provider.getContext(),
                                "无法显示复制直达，已保留系统复制操作；请查看模块诊断日志",
                                Toast.LENGTH_LONG).show());
                    } finally {
                        Binder.restoreCallingIdentity(identity);
                    }
                }
            });
            log("text-direct copy hook installed build=" + BuildConfig.VERSION_NAME + "/" + BuildConfig.VERSION_CODE);
        } catch (RuntimeException | LinkageError error) {
            log("text-direct copy hook unavailable: " + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
        }
    }

    private static synchronized Bundle openClipboardText(Context context) {
        Bundle settings = context.getContentResolver().call(PROVIDER_URI,
                IntegrationSettings.METHOD_GET_AICR_SETTINGS, null, null);
        if (settings == null
                || !settings.getBoolean(IntegrationSettings.EXTRA_COPY_DIRECT_ENABLED, false)) {
            return null;
        }
        ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
        ClipData clip = clipboard.getPrimaryClip();
        String text = AicrClipboardText.read(clip);
        if (text == null) return null;

        long now = SystemClock.elapsedRealtime();
        long timestamp = clip.getDescription().getTimestamp();
        if (text.equals(lastText) && timestamp == lastClipTimestamp
                && now - lastLaunchAt < DUPLICATE_WINDOW_MILLIS) {
            Bundle result = new Bundle();
            result.putBoolean("success", true);
            return result;
        }
        Bundle input = new Bundle();
        input.putString(IntegrationSettings.EXTRA_TEXT, text);
        Bundle accepted = context.getContentResolver().call(PROVIDER_URI,
                IntegrationSettings.METHOD_OPEN_AICR_TEXT, null, input);
        if (accepted == null || !accepted.getBoolean("accepted", false)) return null;
        lastLaunchAt = now;
        lastClipTimestamp = timestamp;
        lastText = text;
        Bundle result = new Bundle();
        result.putBoolean("success", true);
        log("requested copy action bubble from AICR copy");
        return result;
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}

package com.leaf.hyperdragshare.codex;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.SystemClock;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Optional direct-copy bridge for Xiaomi AICR's text provider. */
final class AicrCopyHooks {
    private static final String TAG = "DragShare/Aicr";
    private static final String PROVIDER_CLASS = "com.xiaomi.aicr.copydirect.TextDirectProvider";
    private static final String METHOD_PROCESS_TEXT = "processText";
    private static final String MODULE_PACKAGE = "com.leaf.hyperdragshare.codex";
    private static final String SEGMENTATION_ACTIVITY =
            "com.leaf.hyperdragshare.codex.TextSegmentationActivity";
    private static final String PROVIDER_URI =
            "content://com.leaf.hyperdragshare.codex.share/settings";
    private static final long DUPLICATE_WINDOW_MILLIS = 500L;

    private static volatile long lastLaunchAt;

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
                    Object[] args = param.args;
                    if (args == null || args.length < 3
                            || !METHOD_PROCESS_TEXT.equals(args[0])) {
                        return;
                    }
                    try {
                        Bundle result = openClipboardText(param.thisObject);
                        if (result != null) {
                            param.setResult(result);
                        }
                    } catch (RuntimeException error) {
                        log("copy handling failed; leaving the provider result unchanged: "
                                + error.getClass().getSimpleName() + ": " + error.getMessage());
                    }
                }
            });
            log("text-direct copy hook installed");
        } catch (RuntimeException | LinkageError error) {
            log("text-direct copy hook unavailable: " + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
        }
    }

    private static Bundle openClipboardText(Object provider) {
        if (!(provider instanceof ContentProvider)) {
            return null;
        }
        Context context = ((ContentProvider) provider).getContext();
        if (context == null || !copyDirectEnabled(context)) {
            return null;
        }
        Context applicationContext = context.getApplicationContext();
        if (applicationContext != null) {
            context = applicationContext;
        }
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(
                Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return null;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0 || clip.getDescription() == null) {
            return null;
        }
        ClipDescription description = clip.getDescription();
        CharSequence label = description.getLabel();
        if (label != null && "drag-share-text".contentEquals(label)) {
            return null;
        }
        PersistableBundle clipExtras = description.getExtras();
        if (clipExtras != null
                && clipExtras.getBoolean("android.content.extra.IS_SENSITIVE", false)) {
            return null;
        }
        CharSequence value = clip.getItemAt(0).coerceToText(context);
        String text = value == null ? "" : value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastLaunchAt < DUPLICATE_WINDOW_MILLIS) {
            return null;
        }
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setComponent(new ComponentName(MODULE_PACKAGE, SEGMENTATION_ACTIVITY))
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
        lastLaunchAt = now;

        Bundle result = new Bundle();
        result.putBoolean("success", true);
        log("opened text segmentation from AICR copy");
        return result;
    }

    private static boolean copyDirectEnabled(Context context) {
        try {
            Bundle result = context.getContentResolver().call(
                    android.net.Uri.parse(PROVIDER_URI),
                    IntegrationSettings.METHOD_GET_AICR_SETTINGS,
                    null,
                    null);
            return result != null
                    && result.getBoolean(IntegrationSettings.EXTRA_COPY_DIRECT_ENABLED, false);
        } catch (RuntimeException error) {
            log("settings provider unavailable; AICR copy remains unchanged: "
                    + error.getClass().getSimpleName() + ": " + error.getMessage());
            return false;
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}

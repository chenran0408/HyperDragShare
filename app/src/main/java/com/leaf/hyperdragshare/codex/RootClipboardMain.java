package com.leaf.hyperdragshare.codex;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Looper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** One explicit stdin clipboard write, executed as UID 1000; never prints clipboard contents. */
public final class RootClipboardMain {
    private RootClipboardMain() {}

    @android.annotation.SuppressLint({"BlockedPrivateApi", "SoonBlockedPrivateApi"})
    public static void main(String[] args) {
        int status = 0;
        try {
            Looper.prepareMainLooper();
            Class<?> type = Class.forName("android.app.ActivityThread");
            Object thread = type.getDeclaredMethod("systemMain").invoke(null);
            Context context = (Context) type.getDeclaredMethod("getSystemContext").invoke(thread);
            String text = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            ClipboardManager manager = context.getSystemService(ClipboardManager.class);
            manager.setPrimaryClip(ClipData.newPlainText("HyperDragShare", text));
            ClipData actual = manager.getPrimaryClip();
            CharSequence copied = actual != null && actual.getItemCount() > 0
                    ? actual.getItemAt(0).getText() : null;
            if (copied == null || !text.contentEquals(copied)) {
                throw new IOException("System clipboard did not confirm the requested write");
            }
            System.out.println("copied");
            System.out.flush();
        } catch (IOException error) {
            System.err.println("java.io.IOException: " + error.getMessage());
            status = 1;
        } catch (ReflectiveOperationException | RuntimeException error) {
            System.err.println("java.io.IOException: System clipboard access failed ("
                    + error.getClass().getSimpleName() + ")");
            status = 1;
        }
        System.err.flush();
        System.exit(status);
    }
}

package com.leaf.hyperdragshare.codex;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.webkit.MimeTypeMap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** One stdin write: text as system UID, images as shell UID to retain URI grants. */
public final class RootClipboardMain {
    private RootClipboardMain() {}

    @android.annotation.SuppressLint({"BlockedPrivateApi", "SoonBlockedPrivateApi"})
    public static void main(String[] args) {
        int status = 0;
        try {
            boolean image = args.length == 1 && "--image".equals(args[0]);
            if (args.length != 0 && !image) throw new IOException("Expected no arguments or --image");
            Looper.prepareMainLooper();
            Class<?> type = Class.forName("android.app.ActivityThread");
            Object thread = type.getDeclaredMethod("systemMain").invoke(null);
            Context context = (Context) type.getDeclaredMethod("getSystemContext").invoke(thread);
            String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            ClipboardManager manager = context.getSystemService(ClipboardManager.class);
            Uri uri = null;
            ClipData requested;
            if (image) {
                uri = Uri.parse(input);
                if (!"content".equals(uri.getScheme()) || !ImageStagingClient.AUTHORITY.equals(uri.getAuthority())) {
                    throw new IOException("Clipboard image must come from the module share provider");
                }
                String suffix = ShareUriToken.suffix(uri.getLastPathSegment());
                String mime = suffix == null ? null : MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(suffix.substring(1));
                if (mime == null || !mime.startsWith("image/")) throw new IOException("图片格式不可用于剪贴板");
                // This app_process has no AMS application record. newUri() queries a provider and
                // is rejected at that boundary; the owner already staged and identified this image.
                requested = new ClipData("HyperDragShare image", new String[]{mime}, new ClipData.Item(uri));
            } else requested = ClipData.newPlainText("HyperDragShare", input);
            ClipData actual;
            if (image) {
                // Target ROM IClipboard signature, verified on-device. The system Context reports
                // package android, so use shell's actual attribution for this shell-UID process.
                Object binder = Class.forName("android.os.ServiceManager")
                        .getDeclaredMethod("getService", String.class).invoke(null, "clipboard");
                Object service = Class.forName("android.content.IClipboard$Stub")
                        .getDeclaredMethod("asInterface", android.os.IBinder.class).invoke(null, binder);
                Class<?> clipboard = Class.forName("android.content.IClipboard");
                // su 2000 is the primary user's shell, matching this device's module installation.
                int user = 0;
                clipboard.getDeclaredMethod("setPrimaryClip", ClipData.class, String.class, String.class,
                                int.class, int.class)
                        .invoke(service, requested, "com.android.shell", null, user, 0);
                actual = (ClipData) clipboard.getDeclaredMethod("getPrimaryClip", String.class, String.class,
                                int.class, int.class)
                        .invoke(service, "com.android.shell", null, user, 0);
            } else {
                manager.setPrimaryClip(requested);
                actual = manager.getPrimaryClip();
            }
            ClipData.Item copied = actual != null && actual.getItemCount() > 0 ? actual.getItemAt(0) : null;
            boolean matches = copied != null && (image ? uri.equals(copied.getUri())
                    : copied.getText() != null && input.contentEquals(copied.getText()));
            if (!matches) {
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

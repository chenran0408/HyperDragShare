package com.leaf.hyperdragshare.codex;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public final class ShareImageProvider extends ContentProvider {
    private static final String TAG = "DragShareProvider";
    private static final long MAX_FILE_BYTES = 32L * 1024L * 1024L;

    private File shareDirectory;

    @Override
    public boolean onCreate() {
        if (getContext() == null) {
            return false;
        }
        shareDirectory = new File(getContext().getCacheDir(), "shared-images");
        return shareDirectory.exists() || shareDirectory.mkdirs();
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (NativeSidebarState.REPORT.equals(method)) {
            enforcePackageCaller("com.miui.securitycenter", "native sidebar region report");
            try {
                return NativeSidebarState.report(contextOrThrow(), extras);
            } catch (RemoteException error) {
                DragShareLog.w(TAG, "native sidebar reporter binder already died", error);
                throw new IllegalStateException("Native sidebar reporter is no longer alive", error);
            }
        }
        if (NativeSidebarState.READ.equals(method)) {
            enforcePackageCaller("com.android.systemui", "native sidebar region read");
            return NativeSidebarState.read(contextOrThrow());
        }
        if (StashSurfaceHost.METHOD_OPEN.equals(method)) {
            int callerUid = enforcePackageCaller("com.miui.securitycenter", "native sidebar stash surface open");
            long identity = Binder.clearCallingIdentity();
            try {
                return StashSurfaceHost.open(contextOrThrow(), extras, callerUid);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
        if (SharedRootNodeCapture.METHOD_OPEN.equals(method)) {
            enforcePortalCaller();
            int uid = Binder.getCallingUid();
            long identity = Binder.clearCallingIdentity();
            try {
                return SharedRootNodeCapture.acquire(contextOrThrow(), extras.getBinder("owner"), uid,
                        extras.getBoolean("manual"));
            } finally { Binder.restoreCallingIdentity(identity); }
        }
        if (ImageOcrOverlay.METHOD_RECOGNIZE.equals(method)) {
            enforcePortalCaller();
            Uri uri = extras == null ? null : extras.getParcelable("image", Uri.class);
            if (uri == null) throw new IllegalArgumentException("缺少识别图片");
            resolveFile(uri);
            long identity = Binder.clearCallingIdentity();
            try {
                Bundle result = new Bundle();
                result.putBoolean("accepted", ImageOcrOverlay.startFromPress(contextOrThrow(), uri, extras.getLong("press_confirmed_at")));
                return result;
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
        if (BigBangOverlayLauncher.METHOD_OPEN.equals(method)) {
            enforcePortalCaller();
            long identity = Binder.clearCallingIdentity();
            try {
                return BigBangOverlayLauncher.request(contextOrThrow(), extras);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
        if (StashClient.METHOD_PUT.equals(method)) {
            enforcePortalCaller();
            return putInStash(extras);
        }
        if (ModuleActivation.METHOD_REPORT_INJECTED.equals(method)) {
            enforcePortalCaller();
            ModuleActivation.recordInjected(contextOrThrow(), extras);
            return Bundle.EMPTY;
        }
        if (DragShareSettings.METHOD_GET_SETTINGS.equals(method)) {
            enforcePortalCaller();
            Bundle result = DragShareSettings.readLocal(contextOrThrow()).toBundle();
            ThumbTriggerSettings.appendToBundle(contextOrThrow(), result);
            result.putBoolean(IntegrationSettings.EXTRA_COPY_DIRECT_ENABLED,
                    IntegrationSettings.isCopyDirectEnabled(contextOrThrow()));
            return result;
        }
        if (IntegrationSettings.METHOD_GET_AICR_SETTINGS.equals(method)) {
            enforceAicrCallerOrModule();
            Bundle result = new Bundle();
            result.putBoolean(
                    IntegrationSettings.EXTRA_COPY_DIRECT_ENABLED,
                    IntegrationSettings.isCopyDirectEnabled(contextOrThrow()));
            return result;
        }
        if (IntegrationSettings.METHOD_OPEN_AICR_TEXT.equals(method)) {
            enforceAicrCallerOrModule();
            String text = extras == null ? null : extras.getString(IntegrationSettings.EXTRA_TEXT);
            if (text == null || text.isBlank()) throw new IllegalArgumentException("缺少可分词的文字");
            if (!IntegrationSettings.isCopyDirectEnabled(contextOrThrow())) {
                Bundle result = new Bundle();
                result.putBoolean("accepted", false);
                return result;
            }
            Bundle input = new Bundle();
            input.putString(IntegrationSettings.EXTRA_TEXT, text);
            input.putBoolean("copy_direct_source", true);
            long identity = Binder.clearCallingIdentity();
            try {
                return BigBangOverlayLauncher.request(contextOrThrow(), input);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
        if (ImageStagingClient.METHOD_GRANT.equals(method)) {
            enforcePortalCaller();
            return grantImage(extras);
        }
        if (ImageStagingClient.METHOD_REVOKE.equals(method)) {
            enforcePortalCaller();
            return revokeImage(extras);
        }
        if (!ImageStagingClient.METHOD_STAGE.equals(method)) {
            return super.call(method, arg, extras);
        }
        enforcePortalCaller();
        return stageImage(extras);
    }

    private Bundle putInStash(Bundle extras) {
        if (extras == null) throw new IllegalArgumentException("Missing stash content");
        Uri image = extras.getParcelable(StashClient.EXTRA_IMAGE, Uri.class);
        try (StashStore store = new StashStore(contextOrThrow())) {
            StashStore.Entry entry;
            if (image != null) {
                // Only accept an image already staged by this provider, never an arbitrary file URI.
                try (InputStream input = new FileInputStream(resolveFile(image))) {
                    entry = store.addImage(input, getType(image));
                }
            } else {
                String text = extras.getString(StashClient.EXTRA_TEXT);
                if (text == null || text.isBlank()) throw new IllegalArgumentException("Missing stash text");
                entry = store.addText(text);
            }
            Bundle result = new Bundle();
            result.putString(StashClient.RESULT_ID, entry.id);
            DragShareLog.i(TAG, "saved content to stash kind=" + entry.kind);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                StashOverlayPanel.contentAdded(contextOrThrow());
            });
            return result;
        } catch (IOException error) {
            DragShareLog.w(TAG, "unable to save content to stash", error);
            throw new IllegalStateException("无法存入中转站：" + error.getMessage(), error);
        }
    }

    private Bundle stageImage(Bundle extras) {
        if (extras == null) {
            throw new IllegalArgumentException("Missing image data");
        }
        ParcelFileDescriptor stream = extras.getParcelable(
                ImageStagingClient.EXTRA_STREAM,
                ParcelFileDescriptor.class);
        byte[] bytes = extras.getByteArray(ImageStagingClient.EXTRA_BYTES);
        if (stream == null && (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_BYTES)) {
            throw new IllegalArgumentException("图片为空或超过32 MiB");
        }

        SharedImageCleanupJob.cleanupBeforeImport(contextOrThrow());
        String token = UUID.randomUUID().toString();
        File temporary = new File(shareDirectory, token + ".tmp");
        long written;
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            if (stream != null) {
                try (InputStream input =
                             new ParcelFileDescriptor.AutoCloseInputStream(stream)) {
                    written = copyImage(input, output);
                }
            } else {
                output.write(bytes);
                written = bytes.length;
            }
            output.getFD().sync();
        } catch (IOException error) {
            temporary.delete();
            throw new IllegalStateException("图片暂存失败：" + error.getMessage(), error);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // AutoCloseInputStream normally owns this descriptor.
                }
            }
        }
        String suffix;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (FileInputStream image = new FileInputStream(temporary)) {
                BitmapFactory.decodeStream(image, null, bounds);
            }
            String mime = bounds.outMimeType;
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || mime == null || !mime.startsWith("image/")) {
                throw new IOException("系统无法解码此图片格式");
            }
            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (extension == null) throw new IOException("系统未提供此图片格式的扩展名");
            suffix = "." + extension;
            File destination = fileForToken(token, suffix);
            if (!temporary.renameTo(destination)) throw new IOException("无法发布图片暂存文件");
        } catch (IOException error) {
            temporary.delete();
            throw new IllegalStateException("图片暂存失败：" + error.getMessage(), error);
        }

        Uri uri = uriForToken(token, suffix);
        grantReadAccessAsOwner(
                MainHook.TAPLUS_PACKAGE,
                uri);
        DragShareLog.i(TAG, "staged format=" + suffix.substring(1)
                + " bytes=" + written);
        Bundle result = new Bundle();
        result.putString(ImageStagingClient.RESULT_URI, uri.toString());
        return result;
    }

    private static long copyImage(InputStream input, FileOutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            total += read;
            if (total > MAX_FILE_BYTES) {
                throw new IOException("图片超过32 MiB");
            }
            output.write(buffer, 0, read);
        }
        if (total == 0L) {
            throw new IOException("图片内容为空");
        }
        return total;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Read-only provider");
        }
        try {
            File file = resolveFile(uri);
            if (!file.isFile()) {
                throw new FileNotFoundException("Staged image is missing");
            }
            DragShareLog.i(TAG, "open uid=" + Binder.getCallingUid()
                    + " bytes=" + file.length());
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (FileNotFoundException | RuntimeException error) {
            DragShareLog.w(TAG, "open failed uid=" + Binder.getCallingUid()
                    + " uri=" + describeUri(uri), error);
            throw error;
        }
    }

    @Override
    public String getType(Uri uri) {
        String suffix = resolveSuffix(uri);
        DragShareLog.d(TAG, "getType uid=" + Binder.getCallingUid()
                + " uri=" + describeUri(uri) + " result="
                + (suffix == null ? null : mimeTypeForSuffix(suffix)));
        return suffix == null ? null : mimeTypeForSuffix(suffix);
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        File file;
        try {
            file = resolveFile(uri);
        } catch (RuntimeException error) {
            DragShareLog.w(TAG, "query failed uid=" + Binder.getCallingUid()
                    + " uri=" + describeUri(uri), error);
            throw error;
        }
        DragShareLog.d(TAG, "query uid=" + Binder.getCallingUid()
                + " uri=" + describeUri(uri) + " bytes=" + file.length());
        String[] columns = projection == null
                ? new String[]{
                        OpenableColumns.DISPLAY_NAME,
                        OpenableColumns.SIZE,
                        MediaStore.MediaColumns.MIME_TYPE}
                : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if (OpenableColumns.DISPLAY_NAME.equals(column)) {
                String token = resolveToken(uri);
                String suffix = resolveSuffix(uri);
                row.add(token == null || suffix == null
                        ? "drag-share.png"
                        : ShareUriToken.fileName(token, suffix));
            } else if (OpenableColumns.SIZE.equals(column)) {
                row.add(file.length());
            } else if (MediaStore.MediaColumns.MIME_TYPE.equals(column)) {
                row.add(mimeTypeForSuffix(resolveSuffix(uri)));
            } else if (MediaStore.MediaColumns.DATE_MODIFIED.equals(column)) {
                row.add(file.lastModified() / 1000L);
            } else if (MediaStore.MediaColumns.TITLE.equals(column)) {
                row.add("DragShare image");
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Use call(stage_image)");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private Bundle grantImage(Bundle extras) {
        Uri uri = requireShareUri(extras);
        String packageName = requirePackageName(extras);
        File file = resolveFile(uri);
        if (!file.isFile()) {
            throw new IllegalArgumentException("Staged image no longer exists");
        }
        grantReadAccessAsOwner(
                packageName,
                uri);
        DragShareLog.i(TAG, "granted package=" + packageName);
        Bundle result = new Bundle();
        result.putBoolean(ImageStagingClient.RESULT_GRANTED, true);
        return result;
    }

    private Bundle revokeImage(Bundle extras) {
        Uri uri = requireShareUri(extras);
        String packageName = requirePackageName(extras);
        revokeReadAccessAsOwner(
                packageName,
                uri);
        return Bundle.EMPTY;
    }

    private Uri requireShareUri(Bundle extras) {
        String value = extras == null
                ? null
                : extras.getString(ImageStagingClient.RESULT_URI);
        Uri uri = value == null ? null : Uri.parse(value);
        if (uri == null || resolveToken(uri) == null) {
            throw new IllegalArgumentException("Invalid share URI");
        }
        return uri;
    }

    private String requirePackageName(Bundle extras) {
        String packageName = extras == null
                ? null
                : extras.getString(ImageStagingClient.EXTRA_PACKAGE);
        if (packageName == null || packageName.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing target package");
        }
        return packageName;
    }

    private void enforcePortalCaller() {
        int callingUid = Binder.getCallingUid();
        if (callingUid == Process.myUid()) {
            return;
        }
        PackageManager packageManager = contextOrThrow().getPackageManager();
        String[] packages = packageManager.getPackagesForUid(callingUid);
        if (packages != null) {
            for (String packageName : packages) {
                if (MainHook.TAPLUS_PACKAGE.equals(packageName)) {
                    return;
                }
            }
        }
        throw new SecurityException("Only Taplus can stage share images");
    }

    private void enforceAicrCallerOrModule() {
        int callingUid = Binder.getCallingUid();
        if (callingUid == Process.myUid()) {
            return;
        }
        String[] packages = contextOrThrow().getPackageManager().getPackagesForUid(callingUid);
        if (packages != null) {
            for (String packageName : packages) {
                if (MainHook.AICR_PACKAGE.equals(packageName)) {
                    return;
                }
            }
        }
        throw new SecurityException("Only Xiaomi AICR can read its integration setting");
    }

    private int enforcePackageCaller(String packageName, String operation) {
        int callingUid = Binder.getCallingUid();
        try {
            int packageUid = contextOrThrow().getPackageManager()
                    .getApplicationInfo(packageName, 0).uid;
            if (callingUid == packageUid) return callingUid;
            DragShareLog.w(TAG, operation + " denied callerUid=" + callingUid
                    + " expectedUid=" + packageUid);
            throw new SecurityException("Only " + packageName + " may perform " + operation);
        } catch (PackageManager.NameNotFoundException error) {
            DragShareLog.w(TAG, operation + " denied; caller package is unavailable: " + packageName, error);
            throw new SecurityException("Required caller package is unavailable: " + packageName, error);
        }
    }

    private File resolveFile(Uri uri) {
        String token = resolveToken(uri);
        String suffix = resolveSuffix(uri);
        if (token == null || suffix == null) {
            throw new IllegalArgumentException("Invalid share URI");
        }
        return fileForToken(token, suffix);
    }

    private String resolveToken(Uri uri) {
        String pathSegment = resolvePathSegment(uri);
        return pathSegment == null ? null : ShareUriToken.parse(pathSegment);
    }

    private String resolveSuffix(Uri uri) {
        String pathSegment = resolvePathSegment(uri);
        return pathSegment == null ? null : ShareUriToken.suffix(pathSegment);
    }

    private String resolvePathSegment(Uri uri) {
        if (uri == null) {
            return null;
        }
        if (!ImageStagingClient.AUTHORITY.equals(uri.getAuthority())) {
            return null;
        }
        if (uri.getPathSegments().size() != 2
                || !"shared".equals(uri.getPathSegments().get(0))) {
            return null;
        }
        return uri.getPathSegments().get(1);
    }

    private static String describeUri(Uri uri) {
        if (uri == null) {
            return "null";
        }
        return uri.getScheme() + "://" + uri.getAuthority()
                + "/" + (uri.getPathSegments().isEmpty()
                ? "" : uri.getPathSegments().get(0)) + "/<redacted>";
    }

    private File fileForToken(String token, String suffix) {
        return new File(shareDirectory, ShareUriToken.fileName(token, suffix));
    }

    private Uri uriForToken(String token, String suffix) {
        return ImageStagingClient.BASE_URI.buildUpon()
                .appendPath("shared")
                .appendPath(ShareUriToken.fileName(token, suffix))
                .build();
    }

    private static String mimeTypeForSuffix(String suffix) {
        return suffix == null ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(suffix.substring(1));
    }

    private void grantReadAccessAsOwner(String packageName, Uri uri) {
        long identity = Binder.clearCallingIdentity();
        try {
            contextOrThrow().grantUriPermission(
                    packageName,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private void revokeReadAccessAsOwner(String packageName, Uri uri) {
        long identity = Binder.clearCallingIdentity();
        try {
            if (packageName == null) {
                contextOrThrow().revokeUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                contextOrThrow().revokeUriPermission(
                        packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    private android.content.Context contextOrThrow() {
        android.content.Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("Provider is not attached");
        }
        return context;
    }
}

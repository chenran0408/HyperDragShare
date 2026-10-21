package com.leaf.hyperdragshare.codex;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.IOException;

/** Private persistent images; external readers require an explicit temporary URI grant. */
public final class StashImageProvider extends ContentProvider {
    private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".stash";
    private StashStore store;

    public static Uri uriForEntry(String id) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(id).build();
    }

    @Override
    public boolean onCreate() {
        store = new StashStore(getContext());
        return true;
    }

    @Override
    public void shutdown() {
        store.close();
        super.shutdown();
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        DragShareLog.i("DragShare/Stash", "image open callerUid=" + Binder.getCallingUid());
        if (!"r".equals(mode)) throw new FileNotFoundException("Stash images are read-only");
        try {
            return ParcelFileDescriptor.open(store.openImage(id(uri)), ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (IOException | IllegalArgumentException error) {
            DragShareLog.w("DragShare/Stash", "unable to open shared stash image", error);
            FileNotFoundException failure = new FileNotFoundException("中转站图片不可用");
            failure.initCause(error);
            throw failure;
        }
    }

    @Override
    public String getType(Uri uri) {
        DragShareLog.i("DragShare/Stash", "image type callerUid=" + Binder.getCallingUid());
        StashStore.Entry entry = entry(uri);
        return entry == null ? null : entry.mime;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        DragShareLog.i("DragShare/Stash", "image query callerUid=" + Binder.getCallingUid());
        // This receiver queries _data instead of opening the granted content URI.
        boolean legacyDrop = "com.tencent.mm".equals(getCallingPackage());
        String[] columns = projection == null
                ? legacyDrop ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE, MediaStore.MediaColumns.DATA}
                    : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        StashStore.Entry entry = entry(uri);
        if (entry == null) return cursor;
        long identity = Binder.clearCallingIdentity();
        try {
            MatrixCursor.RowBuilder row = cursor.newRow();
            for (String column : columns) {
                if (OpenableColumns.DISPLAY_NAME.equals(column)) row.add("HyperDragShare-" + entry.id);
                else if (OpenableColumns.SIZE.equals(column)) row.add(store.openImage(entry.id).length());
                else if (legacyDrop && MediaStore.MediaColumns.DATA.equals(column))
                    row.add(StashDragImageCache.path(getContext(), entry, store.openImage(entry.id)));
                else row.add(null);
            }
            return cursor;
        } catch (IOException | SecurityException | IllegalArgumentException error) {
            cursor.close();
            DragShareLog.w("DragShare/Stash", "image drop metadata failed", error);
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getContext(),
                    "图片未能交给目标应用，原图仍保留：" + error.getMessage(), Toast.LENGTH_LONG).show());
            throw new IllegalStateException("无法读取中转站图片信息", error);
        } finally { Binder.restoreCallingIdentity(identity); }
    }

    private StashStore.Entry entry(Uri uri) {
        try {
            StashStore.Entry entry = store.getEntry(id(uri));
            return entry != null && StashStore.KIND_IMAGE.equals(entry.kind) ? entry : null;
        } catch (IOException error) {
            throw new IllegalStateException("无法读取中转站条目", error);
        }
    }

    private static String id(Uri uri) {
        if (!AUTHORITY.equals(uri.getAuthority()) || uri.getPathSegments().size() != 1) {
            throw new IllegalArgumentException("Invalid stash image URI");
        }
        return uri.getLastPathSegment();
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}

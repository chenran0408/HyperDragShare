package com.leaf.hyperdragshare.codex;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/** A legacy drop receiver needs a public path; never give it the private stash path. */
final class StashDragImageCache {
    private static final String PREFS = "stash_drag_images";
    private static final long MAX_AGE = TimeUnit.HOURS.toMillis(24);

    private StashDragImageCache() {}

    @SuppressWarnings("deprecation")
    static synchronized String path(Context context, StashStore.Entry entry, File source) throws IOException {
        cleanup(context);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ContentResolver resolver = context.getContentResolver();
        long row = prefs.getLong(entry.id, -1);
        if (row != -1) {
            String existing = dataPath(resolver, ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, row));
            if (existing != null) return existing;
            prefs.edit().remove(entry.id).remove(entry.id + ".time").apply();
        }
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(entry.mime);
        if (extension == null) throw new IOException("无法为此图片格式创建拖放副本");
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "HyperDragShare-" + entry.id + "." + extension);
        values.put(MediaStore.MediaColumns.MIME_TYPE, entry.mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/HyperDragShare");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri copy = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (copy == null) throw new IOException("系统未能创建图片拖放副本");
        try {
            try (FileInputStream input = new FileInputStream(source); OutputStream output = resolver.openOutputStream(copy)) {
                if (output == null) throw new IOException("无法写入图片拖放副本");
                input.transferTo(output);
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(copy, ready, null, null) != 1) throw new IOException("图片拖放副本未能就绪");
            String path = dataPath(resolver, copy);
            if (path == null) throw new IOException("系统未提供图片拖放副本路径");
            prefs.edit().putLong(entry.id, ContentUris.parseId(copy))
                    .putLong(entry.id + ".time", System.currentTimeMillis()).apply();
            return path;
        } catch (IOException | SecurityException | IllegalArgumentException error) {
            try { resolver.delete(copy, null, null); }
            catch (SecurityException | IllegalArgumentException cleanupError) { error.addSuppressed(cleanupError); }
            throw error;
        }
    }

    @SuppressWarnings("deprecation")
    private static String dataPath(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{MediaStore.MediaColumns.DATA}, null, null, null)) {
            return cursor != null && cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    static synchronized int cleanup(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long cutoff = System.currentTimeMillis() - MAX_AGE;
        int removed = 0;
        for (String key : prefs.getAll().keySet()) {
            if (key.endsWith(".time") || prefs.getLong(key + ".time", 0) > cutoff) continue;
            long row = prefs.getLong(key, -1);
            if (row == -1) continue;
            context.getContentResolver().delete(ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, row),
                    null, null);
            prefs.edit().remove(key).remove(key + ".time").apply();
            removed++;
        }
        return removed;
    }
}

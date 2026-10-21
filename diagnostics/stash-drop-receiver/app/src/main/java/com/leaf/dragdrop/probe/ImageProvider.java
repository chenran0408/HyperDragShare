package com.leaf.dragdrop.probe;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

public final class ImageProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String file;
        switch (uri.getPath()) {
            case "/sample": file = "sample.png"; break;
            case "/batch-a": file = "batch-a.png"; break;
            case "/batch-b": file = "batch-b.png"; break;
            default: throw new FileNotFoundException("Unknown generated sample");
        }
        if (!"r".equals(mode)) throw new FileNotFoundException("sample is read-only");
        return ParcelFileDescriptor.open(new File(getContext().getFilesDir(), file), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}

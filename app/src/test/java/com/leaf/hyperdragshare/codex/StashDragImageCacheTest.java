package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowMimeTypeMap;
import org.robolectric.shadows.ShadowContentResolver;

import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public final class StashDragImageCacheTest {
    private static final String PREFS = "stash_drag_images";
    private static final byte[] SOURCE_BYTES = {7, 19, 38, 71, 115};

    private Context base;
    private Context context;
    private FakeDownloadsProvider provider;
    private android.content.SharedPreferences preferences;
    private StashStore store;
    private StashStore.Entry entry;
    private File source;

    @Before public void setUp() throws Exception {
        base = RuntimeEnvironment.getApplication();
        preferences = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences.edit().clear().commit();
        ShadowMimeTypeMap mimeMap = Shadow.extract(MimeTypeMap.getSingleton());
        mimeMap.addExtensionMimeTypeMapping("png", "image/png");

        provider = new FakeDownloadsProvider();
        String authority = MediaStore.Downloads.EXTERNAL_CONTENT_URI.getAuthority();
        ProviderInfo info = new ProviderInfo();
        info.authority = authority;
        provider.attachInfo(base, info);
        ShadowContentResolver.registerProviderInternal(authority, provider);
        context = base;

        store = new StashStore(base);
        entry = store.addImage(new java.io.ByteArrayInputStream(SOURCE_BYTES), "image/png");
        source = store.openImage(entry.id);
    }

    @After public void tearDown() {
        if (store != null) store.close();
        if (preferences != null) preferences.edit().clear().commit();
        if (provider != null) provider.clearRows();
    }

    @Test public void createsConcreteMimeDownloadsUriOnlyAfterCopyIsReady() throws Exception {
        Uri uri = StashDragImageCache.uri(context, entry, source);

        assertEquals(MediaStore.Downloads.EXTERNAL_CONTENT_URI.getAuthority(), uri.getAuthority());
        assertEquals(1, provider.insertCount);
        assertEquals("image/png", provider.inserted.getAsString(MediaStore.MediaColumns.MIME_TYPE));
        assertEquals("HyperDragShare-" + entry.id + ".png",
                provider.inserted.getAsString(MediaStore.MediaColumns.DISPLAY_NAME));
        assertEquals("Download/HyperDragShare",
                provider.inserted.getAsString(MediaStore.MediaColumns.RELATIVE_PATH));
        assertEquals(Integer.valueOf(1), provider.inserted.getAsInteger(MediaStore.MediaColumns.IS_PENDING));
        assertEquals(Integer.valueOf(0), provider.lastUpdate.getAsInteger(MediaStore.MediaColumns.IS_PENDING));
        assertArrayEquals(SOURCE_BYTES, read(provider.row(1).file));
        assertEquals(1L, preferences.getLong(entry.id, -1L));
        assertTrue(preferences.getLong(entry.id + ".time", 0L) > 0L);
    }

    @Test public void reusesAnExistingCachedDownloadsRowWhenItStillHasADataPath() throws Exception {
        FakeDownloadsProvider.Row existing = provider.seed(41, SOURCE_BYTES);
        preferences.edit().putLong(entry.id, 41)
                .putLong(entry.id + ".time", System.currentTimeMillis()).commit();

        Uri uri = StashDragImageCache.uri(context, entry, source);

        assertEquals(Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "41"), uri);
        assertEquals(0, provider.insertCount);
        assertArrayEquals(SOURCE_BYTES, read(existing.file));
    }

    @Test public void removesAStaleCachedRowAndCreatesANewDownloadsCopy() throws Exception {
        preferences.edit().putLong(entry.id, 41)
                .putLong(entry.id + ".time", System.currentTimeMillis()).commit();

        Uri uri = StashDragImageCache.uri(context, entry, source);

        assertEquals(1, provider.insertCount);
        assertEquals(1L, android.content.ContentUris.parseId(uri));
        assertFalse(preferences.contains(entry.id) && preferences.getLong(entry.id, -1) == 41);
        assertArrayEquals(SOURCE_BYTES, read(provider.row(1).file));
    }

    @Test public void failedReadyUpdateDeletesPartialMediaStoreCopyAndDoesNotCacheIt() throws Exception {
        provider.failReadyUpdate = true;

        try {
            StashDragImageCache.uri(context, entry, source);
            throw new AssertionError("Expected the failed MediaStore update to abort the copy");
        } catch (IOException expected) {
            assertEquals("图片拖放副本未能就绪", expected.getMessage());
        }

        assertEquals(1, provider.deleteCount);
        assertNull(provider.rowOrNull(1));
        assertFalse(preferences.contains(entry.id));
        assertFalse(preferences.contains(entry.id + ".time"));
    }

    private static byte[] read(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            return input.readAllBytes();
        }
    }

    private static final class FakeDownloadsProvider extends ContentProvider {
        private final Map<Long, Row> rows = new HashMap<>();
        private long nextId = 1;
        int insertCount;
        int deleteCount;
        boolean failReadyUpdate;
        ContentValues inserted;
        ContentValues lastUpdate;

        @Override public boolean onCreate() { return true; }

        @Override public String getType(Uri uri) { return "image/png"; }

        @Override public Cursor query(Uri uri, String[] projection, String selection,
                                      String[] selectionArgs, String sortOrder) {
            Row value = rowOrNull(android.content.ContentUris.parseId(uri));
            if (value == null) return null;
            String[] columns = projection == null ? new String[]{MediaStore.MediaColumns.DATA} : projection;
            MatrixCursor cursor = new MatrixCursor(columns, 1);
            MatrixCursor.RowBuilder row = cursor.newRow();
            for (String column : columns) {
                row.add(MediaStore.MediaColumns.DATA.equals(column) ? value.file.getAbsolutePath() : null);
            }
            return cursor;
        }

        @Override public Uri insert(Uri uri, ContentValues values) {
            insertCount++;
            inserted = new ContentValues(values);
            long id = nextId++;
            File file = new File(getContext().getCacheDir(), "fake-download-" + id + ".image");
            rows.put(id, new Row(id, file, new ContentValues(values)));
            return android.content.ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
        }

        @Override public int update(Uri uri, ContentValues values, String selection,
                                    String[] selectionArgs) {
            lastUpdate = new ContentValues(values);
            if (failReadyUpdate) return 0;
            Row row = rowOrNull(android.content.ContentUris.parseId(uri));
            if (row == null) return 0;
            row.values.put(MediaStore.MediaColumns.IS_PENDING,
                    values.getAsInteger(MediaStore.MediaColumns.IS_PENDING));
            return 1;
        }

        @Override public int delete(Uri uri, String selection, String[] selectionArgs) {
            deleteCount++;
            Row removed = rows.remove(android.content.ContentUris.parseId(uri));
            if (removed != null) removed.file.delete();
            return removed == null ? 0 : 1;
        }

        @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
                throws FileNotFoundException {
            Row row = rowOrNull(android.content.ContentUris.parseId(uri));
            if (row == null) throw new FileNotFoundException(uri.toString());
            return ParcelFileDescriptor.open(row.file,
                    ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE
                            | ParcelFileDescriptor.MODE_WRITE_ONLY);
        }

        Row seed(long id, byte[] bytes) throws IOException {
            File file = new File(getContext().getCacheDir(), "fake-download-" + id + ".image");
            try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); }
            Row row = new Row(id, file, new ContentValues());
            rows.put(id, row);
            nextId = Math.max(nextId, id + 1);
            return row;
        }

        Row row(long id) { return rows.get(id); }
        Row rowOrNull(long id) { return rows.get(id); }

        void clearRows() {
            for (Row row : rows.values()) row.file.delete();
            rows.clear();
        }

        private static final class Row {
            final long id;
            final File file;
            final ContentValues values;

            Row(long id, File file, ContentValues values) {
                this.id = id;
                this.file = file;
                this.values = values;
            }
        }
    }
}

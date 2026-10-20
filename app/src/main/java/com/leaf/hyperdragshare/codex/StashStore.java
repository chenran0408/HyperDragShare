package com.leaf.hyperdragshare.codex;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Persistent text and image entries for the in-app stash. */
public final class StashStore implements AutoCloseable {
    public static final String KIND_TEXT = "text";
    public static final String KIND_IMAGE = "image";

    private static final String DATABASE_NAME = "stash.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE = "stash_entries";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final Database database;
    private final File stashDirectory;

    public StashStore(Context context) {
        Context appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        database = new Database(Objects.requireNonNull(appContext, "application context"));
        stashDirectory = new File(appContext.getFilesDir(), "stash");
    }

    public Entry addText(String text) throws IOException {
        Objects.requireNonNull(text, "text");
        String id = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("kind", KIND_TEXT);
        values.put("text", text);
        values.putNull("image_file_name");
        values.putNull("mime");
        values.put("created_at", createdAt);
        insert(values);
        return new Entry(id, KIND_TEXT, text, null, null, createdAt);
    }

    /** Copies the image bytes without closing the caller-owned input stream. */
    public Entry addImage(InputStream source, String mime) throws IOException {
        Objects.requireNonNull(source, "source");
        String storedMime = normalizeImageMime(mime);
        String id = UUID.randomUUID().toString();
        File imageFile = imageFile(id);
        ensureStashDirectory();

        try (FileOutputStream output = new FileOutputStream(imageFile)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = source.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            output.getFD().sync();
        } catch (IOException error) {
            removeGeneratedFile(imageFile, error);
            throw error;
        }

        long createdAt = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("kind", KIND_IMAGE);
        values.putNull("text");
        values.put("image_file_name", id);
        values.put("mime", storedMime);
        values.put("created_at", createdAt);
        try {
            insert(values);
        } catch (IOException error) {
            removeGeneratedFile(imageFile, error);
            throw error;
        }
        return new Entry(id, KIND_IMAGE, null, id, storedMime, createdAt);
    }

    public List<Entry> listEntries() throws IOException {
        List<Entry> entries = new ArrayList<>();
        try (Cursor cursor = database.getReadableDatabase().query(
                TABLE,
                COLUMNS,
                null,
                null,
                null,
                null,
                "created_at DESC, rowid DESC")) {
            while (cursor.moveToNext()) {
                entries.add(readEntry(cursor));
            }
        } catch (SQLiteException error) {
            throw databaseFailure("list stash entries", error);
        }
        return entries;
    }

    public Entry getEntry(String id) throws IOException {
        requireUuid(id);
        try (Cursor cursor = database.getReadableDatabase().query(
                TABLE,
                COLUMNS,
                "id = ?",
                new String[]{id},
                null,
                null,
                null,
                "1")) {
            return cursor.moveToFirst() ? readEntry(cursor) : null;
        } catch (SQLiteException error) {
            throw databaseFailure("read stash entry", error);
        }
    }

    public File openImage(String id) throws IOException {
        Entry entry = getEntry(id);
        if (entry == null || !KIND_IMAGE.equals(entry.kind)) {
            throw new FileNotFoundException("Stashed image not found: " + id);
        }
        requireUuid(entry.imageFileName);
        File image = imageFile(entry.imageFileName);
        if (!image.isFile()) {
            throw new FileNotFoundException("Stashed image file is missing: " + id);
        }
        return image;
    }

    public boolean delete(String id) throws IOException {
        requireUuid(id);
        try {
            SQLiteDatabase db = database.getWritableDatabase();
            db.beginTransaction();
            try {
                Entry entry;
                try (Cursor cursor = db.query(
                        TABLE,
                        COLUMNS,
                        "id = ?",
                        new String[]{id},
                        null,
                        null,
                        null,
                        "1")) {
                    entry = cursor.moveToFirst() ? readEntry(cursor) : null;
                }
                if (entry == null) {
                    db.setTransactionSuccessful();
                    return false;
                }

                int deleted = db.delete(TABLE, "id = ?", new String[]{id});
                if (deleted == 0) {
                    db.setTransactionSuccessful();
                    return false;
                }

                if (KIND_IMAGE.equals(entry.kind)) {
                    File image = imageFile(entry.imageFileName);
                    try {
                        java.nio.file.Files.deleteIfExists(image.toPath());
                    } catch (IOException error) {
                        throw new IOException("Stash image file could not be deleted", error);
                    }
                }

                db.setTransactionSuccessful();
                return true;
            } finally {
                db.endTransaction();
            }
        } catch (SQLiteException error) {
            throw databaseFailure("delete stash entry", error);
        }
    }

    @Override
    public void close() {
        database.close();
    }

    private void insert(ContentValues values) throws IOException {
        try {
            database.getWritableDatabase().insertOrThrow(TABLE, null, values);
        } catch (SQLiteException error) {
            throw databaseFailure("save stash entry", error);
        }
    }

    private void ensureStashDirectory() throws IOException {
        if (!stashDirectory.exists() && !stashDirectory.mkdirs()) {
            throw new IOException("Unable to create the stash image directory");
        }
        if (!stashDirectory.isDirectory()) {
            throw new IOException("Stash image path is not a directory: " + stashDirectory);
        }
    }

    private File imageFile(String fileName) {
        requireUuid(fileName);
        return new File(stashDirectory, fileName);
    }

    private static String normalizeImageMime(String mime) {
        if (mime == null || mime.trim().isEmpty()) {
            return "image/*";
        }
        String normalized = mime.trim();
        if (!normalized.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("Image MIME type must start with image/");
        }
        return normalized;
    }

    private static void requireUuid(String id) {
        if (id == null || !UUID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Stash entry id must be a UUID");
        }
    }

    private static void removeGeneratedFile(File file, IOException cause) {
        if (file.exists() && !file.delete()) {
            cause.addSuppressed(new IOException("Unable to remove incomplete stash image: " + file));
        }
    }

    private static IOException databaseFailure(String operation, SQLiteException cause) {
        return new IOException("Unable to " + operation + " in the stash database", cause);
    }

    private static Entry readEntry(Cursor cursor) {
        return new Entry(
                cursor.getString(0),
                cursor.getString(1),
                cursor.isNull(2) ? null : cursor.getString(2),
                cursor.isNull(3) ? null : cursor.getString(3),
                cursor.isNull(4) ? null : cursor.getString(4),
                cursor.getLong(5));
    }

    private static final String[] COLUMNS = {
            "id", "kind", "text", "image_file_name", "mime", "created_at"
    };

    public static final class Entry {
        public final String id;
        public final String kind;
        public final String text;
        public final String imageFileName;
        public final String mime;
        public final long createdAt;

        private Entry(
                String id,
                String kind,
                String text,
                String imageFileName,
                String mime,
                long createdAt) {
            this.id = id;
            this.kind = kind;
            this.text = text;
            this.imageFileName = imageFileName;
            this.mime = mime;
            this.createdAt = createdAt;
        }
    }

    private static final class Database extends SQLiteOpenHelper {
        Database(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE + " ("
                    + "id TEXT PRIMARY KEY NOT NULL, "
                    + "kind TEXT NOT NULL CHECK(kind IN ('text', 'image')), "
                    + "text TEXT, "
                    + "image_file_name TEXT, "
                    + "mime TEXT, "
                    + "created_at INTEGER NOT NULL)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            throw new SQLException("Unsupported stash database upgrade: "
                    + oldVersion + " to " + newVersion);
        }
    }
}

package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33)
public final class StashStoreTest {
    private static final String DATABASE_NAME = "stash.db";

    private Context context;
    private StashStore store;

    @Before
    public void setUp() throws IOException {
        context = RuntimeEnvironment.getApplication();
        context.deleteDatabase(DATABASE_NAME);
        deleteTree(new File(context.getFilesDir(), "stash"));
        store = new StashStore(context);
    }

    @After
    public void tearDown() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    public void textAndImageSurviveStoreReopen() throws IOException {
        byte[] imageBytes = {1, 4, 9, 16, 25};
        TrackingInputStream input = new TrackingInputStream(imageBytes);
        StashStore.Entry text = store.addText("saved text");
        StashStore.Entry image = store.addImage(input, "image/png");
        assertFalse(input.closed);

        store.close();
        store = new StashStore(context);

        assertEquals("saved text", store.getEntry(text.id).text);
        StashStore.Entry reopenedImage = store.getEntry(image.id);
        assertEquals(StashStore.KIND_IMAGE, reopenedImage.kind);
        assertEquals("image/png", reopenedImage.mime);
        assertArrayEquals(imageBytes, readFile(store.openImage(image.id)));
    }

    @Test
    public void listEntriesReturnsNewestFirst() throws IOException {
        StashStore.Entry oldest = store.addText("oldest");
        StashStore.Entry middle = store.addText("middle");
        StashStore.Entry newest = store.addText("newest");

        List<StashStore.Entry> entries = store.listEntries();

        assertEquals(3, entries.size());
        assertEquals(newest.id, entries.get(0).id);
        assertEquals(middle.id, entries.get(1).id);
        assertEquals(oldest.id, entries.get(2).id);
    }

    @Test
    public void deletingImageRemovesOnlyItsFileAndEntry() throws IOException {
        StashStore.Entry first = store.addImage(new ByteArrayInputStream(new byte[]{1}), "image/*");
        StashStore.Entry second = store.addImage(new ByteArrayInputStream(new byte[]{2}), "image/jpeg");
        File firstFile = store.openImage(first.id);
        File secondFile = store.openImage(second.id);

        assertTrue(store.delete(first.id));

        assertFalse(firstFile.exists());
        assertTrue(secondFile.exists());
        assertEquals(null, store.getEntry(first.id));
        assertEquals(second.id, store.listEntries().get(0).id);
    }

    @Test
    public void failedImageFileDeletionRollsBackOnlyThatEntry() throws IOException {
        StashStore.Entry image = store.addImage(new ByteArrayInputStream(new byte[]{1}), "image/png");
        StashStore.Entry text = store.addText("keep this entry");
        File imagePath = new File(new File(context.getFilesDir(), "stash"), image.imageFileName);
        assertTrue(imagePath.delete());
        assertTrue(imagePath.mkdir());
        File child = new File(imagePath, "keep.txt");
        assertTrue(child.createNewFile());

        IOException failure = assertThrows(IOException.class, () -> store.delete(image.id));

        assertEquals("Stash image file could not be deleted", failure.getMessage());
        assertNotNull(failure.getCause());
        assertEquals(image.id, store.getEntry(image.id).id);
        assertEquals("keep this entry", store.getEntry(text.id).text);
        assertEquals(2, store.listEntries().size());
        assertTrue(child.exists());
    }

    @Test
    public void rejectsNonUuidIdsBeforeUsingThemAsPaths() throws IOException {
        File outside = new File(context.getFilesDir(), "stash-outside.txt");
        assertTrue(outside.createNewFile());
        StashStore.Entry saved = store.addText("keep me");

        assertThrows(IllegalArgumentException.class, () -> store.openImage("../stash-outside.txt"));
        assertThrows(IllegalArgumentException.class, () -> store.delete("../stash-outside.txt"));

        assertTrue(outside.exists());
        assertEquals("keep me", store.getEntry(saved.id).text);
    }

    @Test
    public void imageCopyFailureLeavesNoEntryOrPartialFile() throws IOException {
        InputStream input = new InputStream() {
            private boolean returnedByte;

            @Override
            public int read() throws IOException {
                if (!returnedByte) {
                    returnedByte = true;
                    return 42;
                }
                throw new IOException("injected image read failure");
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                if (!returnedByte) {
                    returnedByte = true;
                    buffer[offset] = 42;
                    return 1;
                }
                throw new IOException("injected image read failure");
            }
        };

        IOException failure = assertThrows(IOException.class,
                () -> store.addImage(input, "image/png"));

        assertEquals("injected image read failure", failure.getMessage());
        assertTrue(store.listEntries().isEmpty());
        File stash = new File(context.getFilesDir(), "stash");
        assertTrue(!stash.exists() || stash.listFiles().length == 0);
    }

    @Test
    public void oversizedImageImportLeavesNoEntryOrPartialFile() throws IOException {
        InputStream input = new InputStream() {
            private long remaining = 32L * 1024L * 1024L + 1;

            @Override
            public int read() {
                if (remaining == 0) {
                    return -1;
                }
                remaining--;
                return 0x5a;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                if (remaining == 0) {
                    return -1;
                }
                int count = (int) Math.min(length, remaining);
                for (int index = offset; index < offset + count; index++) {
                    buffer[index] = 0x5a;
                }
                remaining -= count;
                return count;
            }
        };

        IOException failure = assertThrows(IOException.class,
                () -> store.addImage(input, "image/png"));

        assertEquals("图片大小超过 32 MiB 限制", failure.getMessage());
        assertTrue(store.listEntries().isEmpty());
        File stash = new File(context.getFilesDir(), "stash");
        assertTrue(!stash.exists() || stash.listFiles().length == 0);
    }

    @Test
    public void databaseInsertFailureRemovesGeneratedImageAndKeepsCause() throws IOException {
        store.listEntries();
        store.close();
        SQLiteDatabase rawDatabase = context.openOrCreateDatabase(
                DATABASE_NAME, Context.MODE_PRIVATE, null);
        rawDatabase.execSQL("CREATE TRIGGER fail_stash_insert BEFORE INSERT ON stash_entries "
                + "BEGIN SELECT RAISE(FAIL, 'injected database failure'); END");
        rawDatabase.close();
        store = new StashStore(context);

        IOException failure = assertThrows(IOException.class,
                () -> store.addImage(new ByteArrayInputStream(new byte[]{7}), "image/png"));

        assertTrue(failure.getMessage().contains("save stash entry"));
        assertNotNull(failure.getCause());
        assertTrue(store.listEntries().isEmpty());
        File stash = new File(context.getFilesDir(), "stash");
        assertTrue(!stash.exists() || stash.listFiles().length == 0);
    }

    private static byte[] readFile(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[128];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static void deleteTree(File file) throws IOException {
        if (!file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        if (!file.delete()) {
            throw new IOException("Unable to delete test file: " + file);
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        boolean closed;

        TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}

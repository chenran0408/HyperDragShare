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
    public void dragClipKeepsConcreteImageTypesAndMixedItemOrder() throws IOException {
        StashStore.Entry text = store.addText("generated drag sample");
        StashStore.Entry png = store.addImage(new ByteArrayInputStream(new byte[]{1}), "image/png");
        StashStore.Entry jpeg = store.addImage(new ByteArrayInputStream(new byte[]{2}), "image/jpeg");
        android.content.ClipData clip = StashDragHandleKt.clipForStash(List.of(png, text, jpeg));
        assertTrue(clip.getDescription().hasMimeType("image/png"));
        assertTrue(clip.getDescription().hasMimeType("image/jpeg"));
        assertTrue(clip.getDescription().hasMimeType("image/*"));
        assertTrue(clip.getDescription().hasMimeType("text/plain"));
        assertEquals(3, clip.getItemCount());
        assertEquals(StashImageProvider.uriForEntry(png.id), clip.getItemAt(0).getUri());
        assertEquals(text.text, clip.getItemAt(1).getText());
        assertEquals(StashImageProvider.uriForEntry(jpeg.id), clip.getItemAt(2).getUri());
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

    @Test
    public void imageBatchSurvivesReopenWithItsOriginalOrderAndIndependentImports() throws IOException {
        File first = new File(context.getCacheDir(), "batch-first.png");
        File second = new File(context.getCacheDir(), "batch-second.png");
        java.nio.file.Files.write(first.toPath(), new byte[]{1, 2});
        java.nio.file.Files.write(second.toPath(), new byte[]{3, 4});
        try {
            assertEquals(2, store.addImages(context.getContentResolver(), java.util.Arrays.asList(
                    android.net.Uri.fromFile(first), android.net.Uri.fromFile(second))));
            String batchId = store.listEntries().get(0).batchId;
            StashStore.Entry separateImage = store.addImage(
                    new ByteArrayInputStream(new byte[]{5, 6}), "image/png");
            StashStore.Entry separateText = store.addText("independent text");
            assertEquals(1, store.addImages(context.getContentResolver(),
                    List.of(android.net.Uri.fromFile(first))));
            String secondImportBatch = store.listEntries().get(0).batchId;

            store.close();
            store = new StashStore(context);

            List<StashStore.Entry> batch = StashBatchCardKt.stashBatches(store.listEntries()).stream()
                    .filter(group -> batchId.equals(group.get(0).batchId))
                    .findFirst().orElseThrow();
            assertEquals(5, store.listEntries().size());
            assertEquals(2, batch.size());
            assertEquals(0, batch.get(0).batchOrder);
            assertEquals(1, batch.get(1).batchOrder);
            assertArrayEquals(new byte[]{1, 2}, readFile(store.openImage(batch.get(0).id)));
            assertArrayEquals(new byte[]{3, 4}, readFile(store.openImage(batch.get(1).id)));
            assertFalse(batchId.equals(secondImportBatch));
            assertEquals(separateImage.id, store.getEntry(separateImage.id).batchId);
            assertEquals(separateText.id, store.getEntry(separateText.id).batchId);
            assertEquals(0, store.getEntry(separateImage.id).batchOrder);
            assertEquals(0, store.getEntry(separateText.id).batchOrder);
        } finally {
            java.nio.file.Files.deleteIfExists(first.toPath());
            java.nio.file.Files.deleteIfExists(second.toPath());
        }
    }

    @Test
    public void versionOneUpgradePreservesOldEntriesAsIndependentBatches() throws IOException {
        store.close();
        String textId = "11111111-1111-4111-8111-111111111111";
        String imageId = "22222222-2222-4222-8222-222222222222";
        byte[] imageBytes = {9, 8, 7, 6};
        File directory = new File(context.getFilesDir(), "stash");
        assertTrue(directory.mkdirs());
        java.nio.file.Files.write(new File(directory, imageId).toPath(), imageBytes);
        SQLiteDatabase oldDatabase = context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null);
        try {
            oldDatabase.execSQL("CREATE TABLE stash_entries (id TEXT PRIMARY KEY NOT NULL, "
                    + "kind TEXT NOT NULL CHECK(kind IN ('text', 'image')), text TEXT, "
                    + "image_file_name TEXT, mime TEXT, created_at INTEGER NOT NULL)");
            oldDatabase.execSQL("INSERT INTO stash_entries VALUES (?, 'text', ?, NULL, NULL, ?)",
                    new Object[]{textId, "existing saved text", 100L});
            oldDatabase.execSQL("INSERT INTO stash_entries VALUES (?, 'image', NULL, ?, 'image/png', ?)",
                    new Object[]{imageId, imageId, 200L});
            oldDatabase.setVersion(1);
        } finally {
            oldDatabase.close();
        }
        store = new StashStore(context);

        assertEquals(2, store.listEntries().size());
        StashStore.Entry text = store.getEntry(textId);
        StashStore.Entry image = store.getEntry(imageId);
        assertEquals("existing saved text", text.text);
        assertEquals(textId, text.batchId);
        assertEquals(imageId, image.batchId);
        assertEquals(0, text.batchOrder);
        assertEquals(0, image.batchOrder);
        assertEquals(100L, text.createdAt);
        assertEquals(200L, image.createdAt);
        assertEquals("image/png", image.mime);
        assertArrayEquals(imageBytes, readFile(store.openImage(imageId)));
        store.close();
        store = new StashStore(context);
        assertEquals(textId, store.getEntry(textId).batchId);
        assertArrayEquals(imageBytes, readFile(store.openImage(imageId)));
    }

    @Test
    public void failedSecondImageRollsBackBatchButKeepsExistingContent() throws IOException {
        File first = new File(context.getCacheDir(), "batch-first.png");
        java.nio.file.Files.write(first.toPath(), new byte[]{1, 2});
        File missing = new File(context.getCacheDir(), "missing-" + java.util.UUID.randomUUID());
        try (StashStore store = new StashStore(context)) {
            String existing = store.addText("keep").id;
            assertThrows(IOException.class, () -> store.addImages(context.getContentResolver(),
                    java.util.Arrays.asList(android.net.Uri.fromFile(first),
                            android.net.Uri.fromFile(missing))));
            assertEquals(1, store.listEntries().size());
            assertNotNull(store.getEntry(existing));
            assertEquals(0, new File(context.getFilesDir(), "stash").list().length);
        } finally {
            java.nio.file.Files.deleteIfExists(first.toPath());
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

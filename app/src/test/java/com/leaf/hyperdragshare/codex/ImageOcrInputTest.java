package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33)
public final class ImageOcrInputTest {
    @Test
    public void acceptsImageStreamAndDataFallback() {
        Uri uri = Uri.parse("content://test.images/selected");
        Intent stream = new Intent(Intent.ACTION_SEND).setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri);
        Intent data = new Intent(Intent.ACTION_SEND).setDataAndType(uri, "image/jpeg");
        assertEquals(uri, ImageOcrActivity.resolveImageUri(stream));
        assertEquals(uri, ImageOcrActivity.resolveImageUri(data));
    }

    @Test
    public void rejectsFileAndNetworkUrisWithoutOpeningThem() {
        for (String value : new String[]{"file:///sdcard/image.png", "https://example.com/image.png"}) {
            assertNull(ImageOcrActivity.resolveImageUri(
                    new Intent(Intent.ACTION_SEND).setDataAndType(Uri.parse(value), "image/png")));
        }
    }

    @Test
    public void rejectsWrongActionMimeAndMissingImage() {
        Uri uri = Uri.parse("content://test.images/selected");
        assertNull(ImageOcrActivity.resolveImageUri(
                new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/png")));
        assertNull(ImageOcrActivity.resolveImageUri(
                new Intent(Intent.ACTION_SEND).setDataAndType(uri, "text/plain")));
        assertNull(ImageOcrActivity.resolveImageUri(
                new Intent(Intent.ACTION_SEND).setType("image/png")));
    }

    @Test
    public void retryIntentKeepsTheGrantedImageUriAndReadFlag() {
        Uri uri = Uri.parse("content://test.images/selected");

        Intent retry = ImageOcrActivity.createRecheckIntent(RuntimeEnvironment.getApplication(), uri);

        assertEquals(Intent.ACTION_SEND, retry.getAction());
        assertEquals("image/*", retry.getType());
        assertEquals(uri, retry.getData());
        assertEquals(uri, retry.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class));
        assertEquals(uri, retry.getClipData().getItemAt(0).getUri());
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,
                retry.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    @Test
    public void ocrResultIntentCarriesTheSourceImageForRerun() {
        Uri uri = Uri.parse("content://test.images/selected");

        Intent result = TextSegmentationActivity.createIntent("recognized", -1, -1, uri);

        assertEquals(uri, result.getParcelableExtra("dragshare_source_image_uri", Uri.class));
        assertEquals(uri, result.getClipData().getItemAt(0).getUri());
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,
                result.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
}

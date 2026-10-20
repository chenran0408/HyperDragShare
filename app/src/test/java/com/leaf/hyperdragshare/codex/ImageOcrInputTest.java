package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

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
}

package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.webkit.MimeTypeMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowMimeTypeMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public final class ShareImageProviderTest {
    @Before public void providePlatformMimeMapping() {
        // Robolectric's MIME shadow starts empty; Android supplies this platform mapping.
        ShadowMimeTypeMap mapping = Shadow.extract(MimeTypeMap.getSingleton());
        mapping.addExtensionMimeTypeMapping("jpg", "image/jpeg");
    }

    @Test public void jpegStreamKeepsItsFormatAndOriginalBytes() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ShareImageProvider provider = new ShareImageProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = ImageStagingClient.AUTHORITY;
        provider.attachInfo(context, info);
        Bitmap bitmap = Bitmap.createBitmap(24, 16, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.rgb(17, 83, 149));
        byte[] jpeg;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 91, output));
            jpeg = output.toByteArray();
        } finally {
            bitmap.recycle();
        }
        File source = File.createTempFile("clipboard-jpeg", ".jpg", context.getCacheDir());
        try {
            try (FileOutputStream output = new FileOutputStream(source)) {
                output.write(jpeg);
            }
            Bundle extras = new Bundle();
            Bundle result;
            try (ParcelFileDescriptor input = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)) {
                extras.putParcelable(ImageStagingClient.EXTRA_STREAM, input);
                result = provider.call(ImageStagingClient.METHOD_STAGE, null, extras);
            }
            assertNotNull(result);
            Uri staged = Uri.parse(result.getString(ImageStagingClient.RESULT_URI));
            assertEquals("image/jpeg", provider.getType(staged));
            assertFalse(staged.getLastPathSegment().endsWith(".png"));
            try (ParcelFileDescriptor.AutoCloseInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                    provider.openFile(staged, "r"))) {
                assertArrayEquals(jpeg, input.readAllBytes());
            }
        } finally {
            source.delete();
        }
    }
}

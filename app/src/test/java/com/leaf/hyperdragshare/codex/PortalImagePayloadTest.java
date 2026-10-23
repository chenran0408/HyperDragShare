package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public final class PortalImagePayloadTest {
    @Test public void missingAndOtherModesCannotReuseAnExistingStaticBitmap() {
        Bundle other = new Bundle();
        Bitmap oldBitmap = bitmap();

        assertFalse(PortalContentCaptureSource.hasCurrentImagePayload(null));
        assertFalse(PortalContentCaptureSource.hasCurrentImagePayload(other));
        assertFalse(PortalContentCaptureSource.matchesCurrentImageBitmap(other, oldBitmap));

        oldBitmap.recycle();
    }

    @Test public void viewBitmapMustBeTheSameLiveBitmapHeldByTheHost() {
        Bitmap current = bitmap();
        Bitmap stale = bitmap();
        Bundle mode = new Bundle();
        mode.putParcelable("viewBitmap", current);

        assertTrue(PortalContentCaptureSource.hasCurrentImagePayload(mode));
        assertTrue(PortalContentCaptureSource.matchesCurrentImageBitmap(mode, current));
        assertFalse(PortalContentCaptureSource.matchesCurrentImageBitmap(mode, stale));

        current.recycle();
        assertFalse(PortalContentCaptureSource.hasCurrentImagePayload(mode));
        stale.recycle();
    }

    @Test public void onlyLoadableHttpUrlsQualifyAsCurrentImagePayload() {
        Bitmap hostBitmap = bitmap();
        Bundle mode = new Bundle();
        mode.putString("imgUrl", "https://cdn.example.test/image.png");
        assertTrue(PortalContentCaptureSource.hasCurrentImagePayload(mode));
        assertTrue(PortalContentCaptureSource.matchesCurrentImageBitmap(mode, hostBitmap));

        mode.putString("imgUrl", "//cdn.example.test/image.png");
        assertTrue(PortalContentCaptureSource.hasCurrentImagePayload(mode));
        mode.putString("imgUrl", "ftp://cdn.example.test/image.png");
        assertFalse(PortalContentCaptureSource.hasCurrentImagePayload(mode));
        mode.putString("imgUrl", "https://");
        assertFalse(PortalContentCaptureSource.hasCurrentImagePayload(mode));
        mode.putString("imgUrl", "https://cdn.example.test/image with space.png");
        assertFalse(PortalContentCaptureSource.hasCurrentImagePayload(mode));
        mode.putString("imgUrl", "");
        assertFalse(PortalContentCaptureSource.hasCurrentImagePayload(mode));
        assertFalse(PortalContentCaptureSource.matchesCurrentImageBitmap(mode, hostBitmap));

        hostBitmap.recycle();
    }

    private static Bitmap bitmap() {
        return Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
    }
}

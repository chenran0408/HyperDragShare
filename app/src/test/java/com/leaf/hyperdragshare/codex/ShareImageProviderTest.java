package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ProviderInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
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
import org.robolectric.shadows.ShadowPackageManager;

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
        mapping.addExtensionMimeTypeMapping("png", "image/png");
    }

    @Test public void sourceClassificationReturnsOnlyKnownCategoryOrUnknown() {
        Context context = RuntimeEnvironment.getApplication();
        install(context, "test.game", ApplicationInfo.CATEGORY_GAME);
        install(context, "com.eg.android.AlipayGphone", ApplicationInfo.CATEGORY_UNDEFINED);
        ShareImageProvider provider = newProvider(context);

        Bundle request = new Bundle();
        request.putString(ShareImageProvider.EXTRA_SOURCE_PACKAGE, "test.game");
        Bundle known = provider.call(ShareImageProvider.METHOD_CLASSIFY_SOURCE_APP, null, request);
        assertTrue(known.getBoolean("known"));
        assertEquals(ApplicationInfo.CATEGORY_GAME, known.getInt("category"));
        assertFalse(known.containsKey("packages"));

        request.putString(ShareImageProvider.EXTRA_SOURCE_PACKAGE, "com.eg.android.AlipayGphone");
        Bundle alipay = provider.call(ShareImageProvider.METHOD_CLASSIFY_SOURCE_APP, null, request);
        assertTrue(alipay.getBoolean("known"));
        assertEquals(ApplicationInfo.CATEGORY_UNDEFINED, alipay.getInt("category"));

        request.putString(ShareImageProvider.EXTRA_SOURCE_PACKAGE, "test.missing");
        Bundle unknown = provider.call(ShareImageProvider.METHOD_CLASSIFY_SOURCE_APP, null, request);
        assertFalse(unknown.getBoolean("known"));
        assertEquals(ApplicationInfo.CATEGORY_UNDEFINED, unknown.getInt("category"));
        assertEquals(2, unknown.size());
    }

    @Test public void sourceClassificationRejectsUntrustedOrCrossUserCallers() {
        int moduleUid = Process.myUid();
        int userId = moduleUid / 100_000;
        int portalUid = userId * 100_000 + 12345;
        int otherUid = userId * 100_000 + 12346;
        int otherUserPortalUid = (userId + 1) * 100_000 + 12345;

        assertTrue(ShareImageProvider.isTrustedPortalCaller(moduleUid, moduleUid, null));
        assertTrue(ShareImageProvider.isTrustedPortalCaller(
                portalUid, moduleUid, new String[]{"com.miui.contentextension"}));
        assertFalse(ShareImageProvider.isTrustedPortalCaller(
                otherUid, moduleUid, new String[]{"com.example.untrusted"}));
        assertTrue(ShareImageProvider.isTrustedPortalCaller(
                otherUserPortalUid, moduleUid, new String[]{"com.miui.contentextension"}));
        assertTrue(ShareImageProvider.sameAndroidUser(portalUid, moduleUid));
        assertFalse(ShareImageProvider.sameAndroidUser(otherUserPortalUid, moduleUid));
        assertTrue(ShareImageProvider.isTrustedPortalCaller(
                portalUid, moduleUid, new String[]{"com.miui.contentextension"})
                && ShareImageProvider.sameAndroidUser(portalUid, moduleUid));
        assertFalse(ShareImageProvider.isTrustedPortalCaller(
                otherUserPortalUid, moduleUid, new String[]{"com.miui.contentextension"})
                && ShareImageProvider.sameAndroidUser(otherUserPortalUid, moduleUid));
    }

    @Test public void sourceClassificationRejectsMalformedPackageName() {
        ShareImageProvider provider = newProvider(RuntimeEnvironment.getApplication());
        Bundle request = new Bundle();
        request.putString(ShareImageProvider.EXTRA_SOURCE_PACKAGE, "com.example;get_installed_apps");
        try {
            provider.call(ShareImageProvider.METHOD_CLASSIFY_SOURCE_APP, null, request);
            throw new AssertionError("Malformed package name was accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("package"));
        }
    }

    @Test public void settingsProviderReturnsThePersistedPressConfirmationValue() {
        Context context = RuntimeEnvironment.getApplication();
        android.content.SharedPreferences preferences = context.getSharedPreferences(
                "drag_share_settings", Context.MODE_PRIVATE);
        boolean hadValue = preferences.contains("accessibility_long_press_timeout_millis");
        int previous = preferences.getInt("accessibility_long_press_timeout_millis", 600);
        try {
            preferences.edit().putInt("accessibility_long_press_timeout_millis", 1800).commit();
            Bundle result = newProvider(context).call(DragShareSettings.METHOD_GET_SETTINGS, null, null);
            assertEquals(1800, result.getInt("accessibility_long_press_timeout_millis"));
        } finally {
            if (hadValue) {
                preferences.edit().putInt("accessibility_long_press_timeout_millis", previous).commit();
            } else {
                preferences.edit().remove("accessibility_long_press_timeout_millis").commit();
            }
        }
    }

    private static ShareImageProvider newProvider(Context context) {
        ShareImageProvider provider = new ShareImageProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = ImageStagingClient.AUTHORITY;
        provider.attachInfo(context, info);
        return provider;
    }

    private static void install(Context context, String packageName, int category) {
        PackageInfo info = new PackageInfo();
        info.packageName = packageName;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = packageName;
        info.applicationInfo.category = category;
        ShadowPackageManager packages = Shadow.extract(context.getPackageManager());
        packages.installPackage(info);
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
            assertFalse("staging keeps diagnostics optional and does not return a capability as trace metadata",
                    result.containsKey(OperationTrace.EXTRA));
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

    @Test public void legacyByteArrayStageWithoutTraceStillReturnsReadableImage() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ShareImageProvider provider = newProvider(context);
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.rgb(29, 91, 173));
        byte[] png;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
            png = output.toByteArray();
        } finally {
            bitmap.recycle();
        }

        Bundle legacyRequest = new Bundle();
        legacyRequest.putByteArray(ImageStagingClient.EXTRA_BYTES, png);
        Bundle result = provider.call(ImageStagingClient.METHOD_STAGE, null, legacyRequest);

        assertNotNull(result);
        Uri staged = Uri.parse(result.getString(ImageStagingClient.RESULT_URI));
        try (ParcelFileDescriptor.AutoCloseInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                provider.openFile(staged, "r"))) {
            assertArrayEquals(png, input.readAllBytes());
        }
    }
}

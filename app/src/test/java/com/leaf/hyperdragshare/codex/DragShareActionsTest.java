package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.AppOpsManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ProviderInfo;
import android.graphics.Bitmap;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.webkit.MimeTypeMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowMimeTypeMap;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowLooper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(
        manifest = Config.NONE,
        sdk = 35,
        instrumentedPackages = "com.leaf.hyperdragshare.codex",
        shadows = {
                DragShareActionsTest.ClipboardAppOpsShadow.class,
                DragShareActionsTest.ClipboardManagerShadow.class,
                DragShareActionsTest.DragShareToastShadow.class,
                DragShareActionsTest.LocalImageSaverShadow.class,
                DragShareActionsTest.BigBangLauncherShadow.class
        })
@LooperMode(LooperMode.Mode.PAUSED)
public final class DragShareActionsTest {
    private static final String RECEIVER_PACKAGE = "sample.receiver";
    private static final ComponentName RECEIVER =
            new ComponentName(RECEIVER_PACKAGE, "sample.receiver.ReceiveActivity");

    private Context context;
    private DragShareActions actions;
    private SignalingHandler actionHandler;
    private RecordingGrantContext grantContext;
    private StartSnapshotContext startContext;

    @Before public void setUp() throws Exception {
        ShadowLog.clear();
        context = RuntimeEnvironment.getApplication();
        ShadowMimeTypeMap mapping = Shadow.extract(MimeTypeMap.getSingleton());
        mapping.addExtensionMimeTypeMapping("png", "image/png");
        ClipboardManagerShadow.lastClip = null;
        ClipboardManagerShadow.failure = null;
        ClipboardManagerShadow.writer = null;
        LocalImageSaverShadow.failure = null;
        LocalImageSaverShadow.savedBitmap = null;
        LocalImageSaverShadow.threadName = null;
        LocalImageSaverShadow.saved = new CountDownLatch(1);
        BigBangLauncherShadow.lastRequest = null;
        BigBangLauncherShadow.accepted = true;
        DragShareToastShadow.messages.clear();
        DragShareToastShadow.changed = new CountDownLatch(1);
        install(RECEIVER_PACKAGE);
        assertTrue("receiver package must have a distinct nonzero UID",
                packageUid(RECEIVER_PACKAGE) != 0 && packageUid(RECEIVER_PACKAGE) != Process.myUid());
        clearStash();
        registerProvider();
        startContext = new StartSnapshotContext(context);
        actionHandler = new SignalingHandler(Looper.getMainLooper());
        actions = new DragShareActions(
                startContext,
                actionHandler,
                new DragShareToast(context, OverlayWindowPolicy.portal()),
                false);
    }

    @Test public void textShareUsesExplicitSendIntentAndPreservesTextContract() {
        actions.execute(CapturedContent.text("hello receiver", "source.app", null),
                new ShareTarget(RECEIVER, "Receiver", null), null, 10f, 20f);

        Intent intent = nextStartedActivity();
        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals("text/plain", intent.getType());
        assertEquals(RECEIVER, intent.getComponent());
        assertTrue(intent.hasCategory(Intent.CATEGORY_DEFAULT));
        assertTrue((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertEquals("hello receiver", intent.getStringExtra(Intent.EXTRA_TEXT));
        assertNull(intent.getData());
        assertNull(intent.getClipData());
    }

    @Test public void imageShareUsesDataStreamClipAndReadGrant() throws Exception {
        Uri image = stagedImage("share-contract");

        actions.execute(CapturedContent.image(bitmap(), "source.app", null, true),
                new ShareTarget(RECEIVER, "Receiver", null), image, 10f, 20f,
                "image-share-operation");

        Intent intent = nextStartedActivity();
        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals("image/png", intent.getType());
        assertEquals(RECEIVER, intent.getComponent());
        assertEquals(image, intent.getData());
        assertEquals(image, intent.getParcelableExtra(Intent.EXTRA_STREAM));
        assertNotNull(intent.getClipData());
        assertEquals(image, intent.getClipData().getItemAt(0).getUri());
        assertEquals("image-share-operation", OperationTrace.read(intent.getExtras()));
        assertEquals("image-share-operation", OperationTrace.read(intent.getClipData()));
        assertTrue((intent.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertEquals(image, grantContext.grantedUri);
        assertEquals(RECEIVER_PACKAGE, grantContext.grantedPackage);
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, grantContext.grantedFlags);
        assertEquals("drag-share.png", intent.getStringExtra(Intent.EXTRA_TITLE));
        try (ParcelFileDescriptor.AutoCloseInputStream input =
                     new ParcelFileDescriptor.AutoCloseInputStream(
                             context.getContentResolver().openFileDescriptor(image, "r"))) {
            assertEquals(0x89, input.read());
            assertEquals('P', input.read());
            assertEquals('N', input.read());
            assertEquals('G', input.read());
        }
    }

    @Test public void failedImageShareRevokesTheExplicitReadGrant() throws Exception {
        Uri image = stagedImage("share-failure");
        // Robolectric does not model the system URI permission state for these calls. This test
        // verifies the real ShareLauncher-to-Provider Context grant/revoke calls and flags;
        // whether Android actually revokes the receiver's permission remains untested here.
        Context failingStart = new ContextWrapper(context) {
            @Override public void startActivity(Intent intent) {
                throw new IllegalStateException("start rejected");
            }
        };
        DragShareActions failingActions = new DragShareActions(failingStart,
                new Handler(Looper.getMainLooper()),
                new DragShareToast(context, OverlayWindowPolicy.portal()), false);

        failingActions.execute(CapturedContent.image(bitmap(), "source.app", null, true),
                new ShareTarget(RECEIVER, "Receiver", null), image, 0f, 0f);

        assertEquals(image, grantContext.grantedUri);
        assertEquals(RECEIVER_PACKAGE, grantContext.grantedPackage);
        assertEquals(image, grantContext.revokedUri);
        assertEquals(RECEIVER_PACKAGE, grantContext.revokedPackage);
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, grantContext.grantedFlags);
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, grantContext.revokedFlags);
        assertTrue(DragShareToastShadow.messages.contains("无法打开 Receiver"));
        String events = operationEvents();
        assertTrue(events.contains("stage=share outcome=failed_IllegalStateException"));
        assertFalse(events.contains("stage=share outcome=activity_launch_returned"));
        assertFalse(events.contains(image.toString()));
        assertFalse(events.contains("start rejected"));
    }

    @Test public void shareLaunchReturnedIsRecordedWithoutClaimingDelivery() {
        actions.execute(CapturedContent.text("trace text", "source.app", null),
                new ShareTarget(RECEIVER, "Receiver", null), null, 0f, 0f, "test-operation-1");

        Intent intent = nextStartedActivity();
        assertEquals("test-operation-1", OperationTrace.read(intent.getExtras()));
        String events = operationEvents();
        assertTrue(events.contains("op=test-operation-1 stage=action outcome=started"));
        assertTrue(events.contains("op=test-operation-1 stage=share outcome=activity_requested"));
        assertTrue(events.contains("op=test-operation-1 stage=share outcome=activity_launch_returned"));
        assertFalse(events.contains("delivered"));
        assertFalse(events.contains("opened"));
    }

    @Test public void missingOperationIdDoesNotBlockShareLaunch() {
        actions.execute(CapturedContent.text("untraced text", "source.app", null),
                new ShareTarget(RECEIVER, "Receiver", null), null, 0f, 0f, "-");

        Intent intent = nextStartedActivity();
        assertEquals(Intent.ACTION_SEND, intent.getAction());
        assertEquals("untraced text", intent.getStringExtra(Intent.EXTRA_TEXT));
        assertEquals("-", OperationTrace.read(intent.getExtras()));
    }

    @Test public void copyTextAndImageUseClipboardWriterAndDeliverSuccessCallbacks() throws Exception {
        ClipboardManagerShadow.changed = new CountDownLatch(1);
        resetToastSignal();
        actions.execute(CapturedContent.text("clipboard text", "source.app", null),
                ShareTarget.copyTextToClipboard(null), null, 0f, 0f);
        awaitMainEffect(ClipboardManagerShadow.changed, "text clipboard write");
        awaitClipboardWorkerAndDrain();
        awaitMainToast("文字已复制到剪贴板");
        assertEquals("clipboard text", ClipboardManagerShadow.lastClip.getItemAt(0).getText());

        Uri image = stagedImage("clipboard-image");
        ClipboardManagerShadow.changed = new CountDownLatch(1);
        resetToastSignal();
        actions.execute(CapturedContent.image(bitmap(), "source.app", null, true),
                ShareTarget.copyImageToClipboard(null), image, 0f, 0f);
        awaitMainEffect(ClipboardManagerShadow.changed, "image clipboard write");
        awaitClipboardWorkerAndDrain();
        awaitMainToast("图片已复制到剪贴板");
        assertEquals(image, ClipboardManagerShadow.lastClip.getItemAt(0).getUri());
        assertTrue(operationEvents().contains("stage=clipboard outcome=completed"));
    }

    @Test public void clipboardWriteFailureIsDeliveredThroughExistingFailureFeedback() throws Exception {
        ClipboardManagerShadow.failure = new IllegalStateException("clipboard denied");
        ClipboardManagerShadow.changed = new CountDownLatch(1);
        resetToastSignal();
        actions.execute(CapturedContent.text("clipboard text", "source.app", null),
                ShareTarget.copyTextToClipboard(null), null, 0f, 0f);

        awaitMainEffect(ClipboardManagerShadow.changed, "failed clipboard write");
        awaitClipboardWorkerAndDrain();
        awaitMainToastPrefix("复制文字失败：");
    }

    @Test public void imageClipboardFailureIsDeliveredThroughImageFailureFeedback() throws Exception {
        ClipboardManagerShadow.failure = new IllegalStateException("clipboard denied");
        ClipboardManagerShadow.changed = new CountDownLatch(1);
        resetToastSignal();
        actions.execute(CapturedContent.image(bitmap(), "source.app", null, true),
                ShareTarget.copyImageToClipboard(null), stagedImage("clipboard-failure"), 0f, 0f);

        awaitMainEffect(ClipboardManagerShadow.changed, "failed image clipboard write");
        awaitClipboardWorkerAndDrain();
        awaitMainToastPrefix("复制图片失败：");
    }

    @Test public void stashStoresTextAndImageThroughStashClientAndProvider() throws Exception {
        resetToastSignal();
        actions.execute(CapturedContent.text("stashed text", "source.app", null),
                ShareTarget.stash(null), null, 0f, 0f);
        awaitActionPostAndDrain();
        awaitMainToast("已存入中转站");
        try (StashStore store = new StashStore(context)) {
            assertEquals("stashed text", store.listEntries().get(0).text);
        }

        Uri image = stagedImage("stash-image");
        actionHandler.resetSignal();
        resetToastSignal();
        actions.execute(CapturedContent.image(bitmap(), "source.app", null, true),
                ShareTarget.stash(null), image, 0f, 0f);
        awaitActionPostAndDrain();
        awaitMainToast("已存入中转站");
        try (StashStore store = new StashStore(context)) {
            List<StashStore.Entry> entries = store.listEntries();
            assertEquals(2, entries.size());
            assertEquals(StashStore.KIND_IMAGE, entries.get(0).kind);
            assertEquals(StashStore.KIND_TEXT, entries.get(1).kind);
        }
        assertTrue(operationEvents().contains("stage=stash outcome=completed"));
    }

    @Test public void stashFailureReportsFailureWithoutCreatingAnEntry() throws Exception {
        try (StashStore store = new StashStore(context)) {
            assertTrue(store.listEntries().isEmpty());
        }
        SQLiteDatabase database = SQLiteDatabase.openDatabase(
                context.getDatabasePath("stash.db").getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READWRITE);
        database.execSQL("CREATE TRIGGER fail_action_stash BEFORE INSERT ON stash_entries "
                + "BEGIN SELECT RAISE(ABORT, 'injected stash failure'); END");
        database.close();
        resetToastSignal();
        actions.execute(CapturedContent.text("not stored", "source.app", null),
                ShareTarget.stash(null), null, 0f, 0f);

        awaitActionPostAndDrain();
        awaitMainToastPrefix("存入中转站失败：");
        try (StashStore store = new StashStore(context)) {
            assertTrue(store.listEntries().isEmpty());
        }
    }

    @Test public void localSaveKeepsBitmapAliveOnOriginalWorkerAndReportsFailure() throws Exception {
        Bitmap image = bitmap();
        resetToastSignal();
        actions.execute(CapturedContent.image(image, "source.app", null, true),
                ShareTarget.saveToLocal(null), null, 0f, 0f);

        assertTrue("save worker did not finish", LocalImageSaverShadow.saved.await(2, TimeUnit.SECONDS));
        awaitActionPostAndDrain();
        assertSame(image, LocalImageSaverShadow.savedBitmap);
        assertFalse(image.isRecycled());
        assertEquals("drag-share-local-save", LocalImageSaverShadow.threadName);
        awaitMainToast("已保存到本地");
        assertTrue(operationEvents().contains("stage=local_save outcome=completed"));

        LocalImageSaverShadow.failure = new IOException("disk full");
        LocalImageSaverShadow.saved = new CountDownLatch(1);
        actionHandler.resetSignal();
        resetToastSignal();
        actions.execute(CapturedContent.image(image, "source.app", null, true),
                ShareTarget.saveToLocal(null), null, 0f, 0f);
        assertTrue("failed save worker did not finish",
                LocalImageSaverShadow.saved.await(2, TimeUnit.SECONDS));
        awaitActionPostAndDrain();
        awaitMainToast("保存图片失败");
        assertFalse(image.isRecycled());
    }

    @Test public void segmentationPassesTextContextCoordinatesAndChecksAcceptance() {
        CapturedContent content = CapturedContent.text(
                "selected words", "source.app", null, "before words", "after words");
        actions.execute(content, ShareTarget.textSegmentation(null), null, 12.6f, 39.4f,
                "segmentation-operation");

        assertNotNull(BigBangLauncherShadow.lastRequest);
        assertEquals("selected words", BigBangLauncherShadow.lastRequest.getString("text"));
        assertEquals("before words", BigBangLauncherShadow.lastRequest.getString("before_text"));
        assertEquals("after words", BigBangLauncherShadow.lastRequest.getString("after_text"));
        assertEquals(13, BigBangLauncherShadow.lastRequest.getInt("x"));
        assertEquals(39, BigBangLauncherShadow.lastRequest.getInt("y"));
        assertEquals("segmentation-operation", OperationTrace.read(BigBangLauncherShadow.lastRequest));
        assertTrue(operationEvents().contains("stage=segmentation outcome=accepted"));

        BigBangLauncherShadow.accepted = false;
        BigBangLauncherShadow.lastRequest = null;
        actions.execute(content, ShareTarget.textSegmentation(null), null, 1f, 2f,
                "segmentation-rejected-operation");
        assertNotNull(BigBangLauncherShadow.lastRequest);
        assertTrue(DragShareToastShadow.messages.stream()
                .anyMatch(message -> message.startsWith("无法打开分词窗口：")));
        assertTrue(operationEvents().contains("stage=segmentation outcome=rejected"));
    }

    @Test public void ocrAndQrOpenDistinctImageIntentsWithTheSameUriGrantContract() throws Exception {
        Uri image = stagedImage("ocr-qr");
        actions.execute(CapturedContent.image(bitmap(), "source.app", null, true),
                ShareTarget.imageOcr(null), image, 0f, 0f, "ocr-operation");
        Intent ocr = nextStartedActivity();
        assertEquals(new ComponentName(BuildConfig.APPLICATION_ID, ImageOcrActivity.class.getName()),
                ocr.getComponent());
        assertEquals(image, ocr.getData());
        assertEquals("image/*", ocr.getType());
        assertEquals(image, ocr.getParcelableExtra(ImageOcrActivity.EXTRA_HOSTED_URI));
        assertFalse(ocr.getBooleanExtra(ImageOcrActivity.EXTRA_QR, true));
        assertEquals(image, ocr.getClipData().getItemAt(0).getUri());
        assertEquals("ocr-operation", OperationTrace.read(ocr.getExtras()));
        assertEquals("ocr-operation", OperationTrace.read(ocr.getClipData()));
        assertTrue((ocr.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);

        actions.execute(CapturedContent.image(bitmap(), "source.app", null, true),
                ShareTarget.qrScan(null), image, 0f, 0f, "qr-operation");
        Intent qr = nextStartedActivity();
        assertEquals(image, qr.getData());
        assertEquals(image, qr.getParcelableExtra(ImageOcrActivity.EXTRA_HOSTED_URI));
        assertTrue(qr.getBooleanExtra(ImageOcrActivity.EXTRA_QR, false));
        assertEquals(image, qr.getClipData().getItemAt(0).getUri());
        assertEquals("qr-operation", OperationTrace.read(qr.getExtras()));
        assertEquals("qr-operation", OperationTrace.read(qr.getClipData()));
        assertTrue((qr.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
    }

    private Uri stagedImage(String label) throws Exception {
        File directory = new File(context.getCacheDir(), "shared-images");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        String token = UUID.randomUUID().toString();
        File image = new File(directory, token + ".png");
        Bitmap source = bitmap();
        try (FileOutputStream output = new FileOutputStream(image)) {
            assertTrue("could not encode staged PNG " + label,
                    source.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            source.recycle();
        }
        return Uri.parse("content://" + ImageStagingClient.AUTHORITY + "/shared/" + token + ".png");
    }

    private void registerProvider() {
        ShareImageProvider result = new ShareImageProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = ImageStagingClient.AUTHORITY;
        grantContext = new RecordingGrantContext(context);
        result.attachInfo(grantContext, info);
        ShadowContentResolver.registerProviderInternal(ImageStagingClient.AUTHORITY, result);
    }

    private Intent nextStartedActivity() {
        ShadowApplication application = Shadow.extract(context);
        Intent robolectricIntent = application.getNextStartedActivity();
        assertNotNull("expected a launched activity", robolectricIntent);
        assertFalse("expected startActivity snapshot", startContext.snapshots.isEmpty());
        return startContext.snapshots.remove(0);
    }

    private static String operationEvents() {
        StringBuilder result = new StringBuilder();
        for (ShadowLog.LogItem item : ShadowLog.getLogsForTag("DragShare/Operation")) {
            result.append(item.msg).append('\n');
        }
        return result.toString();
    }

    private static Bitmap bitmap() {
        return Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888);
    }

    private static void install(String packageName) {
        PackageInfo info = new PackageInfo();
        info.packageName = packageName;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = packageName;
        info.applicationInfo.uid = Math.max(1, Process.myUid() + 10_000);
        org.robolectric.shadows.ShadowPackageManager packages =
                org.robolectric.shadow.api.Shadow.extract(RuntimeEnvironment.getApplication().getPackageManager());
        packages.installPackage(info);
    }

    private int packageUid(String packageName) throws Exception {
        return context.getPackageManager().getApplicationInfo(packageName, 0).uid;
    }

    private void clearStash() {
        context.getDatabasePath("stash.db").delete();
        deleteTree(new File(context.getFilesDir(), "stash"));
    }

    private static void deleteTree(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static void awaitMainEffect(CountDownLatch latch, String operation) throws Exception {
        assertTrue(operation + " did not finish", latch.await(2, TimeUnit.SECONDS));
    }

    private static void awaitClipboardWorkerAndDrain() throws Exception {
        Thread worker = ClipboardManagerShadow.writer;
        assertNotNull("clipboard worker was not observed", worker);
        worker.join(TimeUnit.SECONDS.toMillis(2));
        assertFalse("clipboard worker did not finish", worker.isAlive());
        ShadowLooper.shadowMainLooper().idle();
    }

    private void awaitActionPostAndDrain() throws Exception {
        assertTrue("action completion was not posted",
                actionHandler.posted.await(2, TimeUnit.SECONDS));
        ShadowLooper.shadowMainLooper().idle();
    }

    private static void awaitMainToast(String expected) throws Exception {
        awaitToastChanged(expected);
    }

    private static void awaitMainToastPrefix(String prefix) throws Exception {
        assertTrue("toast was not delivered", DragShareToastShadow.changed.await(2, TimeUnit.SECONDS));
        assertTrue("missing toast prefix " + prefix,
                DragShareToastShadow.messages.stream().anyMatch(m -> m.startsWith(prefix)));
    }

    private static void awaitToastChanged(String expected) throws Exception {
        assertTrue("toast was not delivered", DragShareToastShadow.changed.await(2, TimeUnit.SECONDS));
        assertTrue("missing toast " + expected, DragShareToastShadow.messages.contains(expected));
    }

    private static void resetToastSignal() {
        DragShareToastShadow.changed = new CountDownLatch(1);
    }

    @Implements(value = AppOpsManager.class)
    public static final class ClipboardAppOpsShadow {
        @Implementation public int unsafeCheckOpRawNoThrow(String op, int uid, String packageName) {
            return AppOpsManager.MODE_ALLOWED;
        }
    }

    @Implements(value = android.content.ClipboardManager.class)
    public static final class ClipboardManagerShadow {
        static volatile ClipData lastClip;
        static volatile RuntimeException failure;
        static volatile CountDownLatch changed = new CountDownLatch(1);
        static volatile Thread writer;

        @Implementation public void setPrimaryClip(ClipData clip) {
            writer = Thread.currentThread();
            lastClip = clip;
            changed.countDown();
            if (failure != null) throw failure;
        }
    }

    @Implements(value = DragShareToast.class, isInAndroidSdk = false)
    public static final class DragShareToastShadow {
        static final java.util.concurrent.CopyOnWriteArrayList<String> messages =
                new java.util.concurrent.CopyOnWriteArrayList<>();
        static volatile CountDownLatch changed = new CountDownLatch(1);

        @Implementation public void show(String message) {
            messages.add(message);
            changed.countDown();
        }
        @Implementation public void show(String message, int duration) {
            messages.add(message);
            changed.countDown();
        }
    }

    @Implements(value = LocalImageSaver.class, isInAndroidSdk = false)
    public static final class LocalImageSaverShadow {
        static volatile IOException failure;
        static volatile Bitmap savedBitmap;
        static volatile String threadName;
        static volatile CountDownLatch saved = new CountDownLatch(1);

        @Implementation public static Uri save(Context context, Bitmap bitmap) throws IOException {
            savedBitmap = bitmap;
            threadName = Thread.currentThread().getName();
            saved.countDown();
            if (failure != null) throw failure;
            return Uri.parse("content://saved/image.png");
        }
    }

    @Implements(value = BigBangOverlayLauncher.class, isInAndroidSdk = false)
    public static final class BigBangLauncherShadow {
        static volatile Bundle lastRequest;
        static volatile boolean accepted = true;

        @Implementation public static Bundle request(Context context, Bundle input) {
            lastRequest = new Bundle(input);
            Bundle result = new Bundle();
            result.putBoolean("accepted", accepted);
            return result;
        }
    }

    private static final class SignalingHandler extends Handler {
        volatile CountDownLatch posted = new CountDownLatch(1);

        SignalingHandler(Looper looper) { super(looper); }
        void resetSignal() { posted = new CountDownLatch(1); }

        @Override public boolean sendMessageAtTime(Message message, long uptimeMillis) {
            boolean result = super.sendMessageAtTime(message, uptimeMillis);
            posted.countDown();
            return result;
        }
    }

    private static final class RecordingGrantContext extends ContextWrapper {
        volatile String grantedPackage;
        volatile Uri grantedUri;
        volatile int grantedFlags;
        volatile String revokedPackage;
        volatile Uri revokedUri;
        volatile int revokedFlags;

        RecordingGrantContext(Context base) { super(base); }

        @Override public void grantUriPermission(String toPackage, Uri uri, int modeFlags) {
            grantedPackage = toPackage;
            grantedUri = uri;
            grantedFlags = modeFlags;
            super.grantUriPermission(toPackage, uri, modeFlags);
        }

        @Override public void revokeUriPermission(String targetPackage, Uri uri, int modeFlags) {
            revokedPackage = targetPackage;
            revokedUri = uri;
            revokedFlags = modeFlags;
            super.revokeUriPermission(targetPackage, uri, modeFlags);
        }
    }

    private static final class StartSnapshotContext extends ContextWrapper {
        final List<Intent> snapshots = new ArrayList<>();

        StartSnapshotContext(Context base) { super(base); }

        @Override public void startActivity(Intent intent) {
            snapshots.add(new Intent(intent));
            super.startActivity(intent);
        }
    }
}

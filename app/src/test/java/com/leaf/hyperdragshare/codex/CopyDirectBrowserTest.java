package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.PendingIntent;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.PatternMatcher;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.shadows.ShadowPendingIntent;
import org.robolectric.shadows.ShadowLog;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public final class CopyDirectBrowserTest {
    private static final String TARGET_PACKAGE = "test.wildcard.browser";
    private static final String OTHER_BROWSER_PACKAGE = "test.generic.browser";
    private static final String DEEP_LINK_PACKAGE = "test.site.handler";
    private static final String URL = "https://example.com/story?id=17";

    private Context context;
    private ShadowPackageManager packages;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        packages = Shadow.extract(context.getPackageManager());
        IntegrationSettings.setCopyDirectBrowserPackage(context, "");
        DragShareLog.configure(DragShareSettings.defaults());
        ShadowLog.clear();
    }

    @After public void restoreLogging() { DragShareLog.configure(DragShareSettings.defaults()); }

    @Test public void browserChoicesKeepWildcardWebHandlersButRejectSiteDeepLinks() {
        addWebHandler(TARGET_PACKAGE, "TestBrowser", "*", null);
        addWebHandler(OTHER_BROWSER_PACKAGE, "Generic", null, null);
        addWebHandler(DEEP_LINK_PACKAGE, "Site handler", "example.com", null);
        addWebHandler("test.path.handler", "Path handler", "*", "/copy-direct-probe");

        List<CopyDirectBrowser.Choice> choices = CopyDirectBrowser.choices(context);

        assertTrue(containsPackage(choices, TARGET_PACKAGE));
        assertTrue(containsPackage(choices, OTHER_BROWSER_PACKAGE));
        assertFalse(containsPackage(choices, DEEP_LINK_PACKAGE));
        assertFalse(containsPackage(choices, "test.path.handler"));
    }

    @Test public void nativeCardUriRetainsSelectedBrowserAndFullWebTarget() throws Exception {
        addWebHandler(TARGET_PACKAGE, "TestBrowser", "*", null);
        IntegrationSettings.setCopyDirectBrowserPackage(context, TARGET_PACKAGE);

        Bundle prepared = CopyDirectActions.prepare(context, URL);

        assertTrue(prepared.getBoolean("accepted"));
        PendingIntent open = prepared.getParcelable(CopyDirectActions.EXTRA_OPEN_PENDING_INTENT);
        assertNotNull(open);
        assertNotNull(prepared.getParcelable(CopyDirectActions.EXTRA_SEGMENT_PENDING_INTENT));
        Intent cardIntent = Intent.parseUri(
                prepared.getString(CopyDirectActions.EXTRA_OPEN_INTENT_URI),
                Intent.URI_INTENT_SCHEME);
        Intent pendingIntent = Shadow.<ShadowPendingIntent>extract(open).getSavedIntent();
        assertEquals(Intent.ACTION_VIEW, cardIntent.getAction());
        assertEquals(Uri.parse(URL), cardIntent.getData());
        assertEquals(TARGET_PACKAGE, cardIntent.getPackage());
        assertTrue(cardIntent.hasCategory(Intent.CATEGORY_BROWSABLE));
        assertTrue((cardIntent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
        assertEquals(cardIntent.getAction(), pendingIntent.getAction());
        assertEquals(cardIntent.getData(), pendingIntent.getData());
        assertEquals(cardIntent.getPackage(), pendingIntent.getPackage());
        assertEquals(cardIntent.getCategories(), pendingIntent.getCategories());
        assertEquals(cardIntent.getFlags(), pendingIntent.getFlags());
    }

    @Test public void unavailableSelectedBrowserProducesNativeCardFailureInsteadOfImplicitOpen()
            throws Exception {
        IntegrationSettings.setCopyDirectBrowserPackage(context, TARGET_PACKAGE);

        Bundle prepared = CopyDirectActions.prepare(context, URL);

        assertTrue(prepared.getBoolean("accepted"));
        assertNull(prepared.getParcelable(CopyDirectActions.EXTRA_OPEN_PENDING_INTENT));
        assertNotNull(prepared.getParcelable(CopyDirectActions.EXTRA_SEGMENT_PENDING_INTENT));
        assertEquals("所选浏览器不可用", prepared.getString(CopyDirectActions.EXTRA_FAILURE));
        assertNull(prepared.getString(CopyDirectActions.EXTRA_OPEN_INTENT_URI));
    }

    @Test public void systemDefaultAndNonWebActionsKeepTheirExistingRouting() {
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(URL));
        assertSame(web, CopyDirectBrowser.route(context, web));

        IntegrationSettings.setCopyDirectBrowserPackage(context, TARGET_PACKAGE);
        Intent email = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:test@example.com"));
        Intent phone = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:1234567"));
        assertSame(email, CopyDirectBrowser.route(context, email));
        assertSame(phone, CopyDirectBrowser.route(context, phone));
    }

    @Test public void preparationEvidenceOmitsCopiedAddressAndDoesNotClaimBrowserOpened() {
        addWebHandler(TARGET_PACKAGE, "TestBrowser", "*", null);
        IntegrationSettings.setCopyDirectBrowserPackage(context, TARGET_PACKAGE);
        CopyDirectActions.prepare(context, URL);
        StringBuilder evidence = new StringBuilder();
        for (ShadowLog.LogItem event : ShadowLog.getLogsForTag("DragShare/Operation")) {
            evidence.append(event.msg).append('\n');
        }
        String logs = evidence.toString();
        assertTrue(logs.contains("stage=copy_direct_prepare outcome=requested"));
        assertTrue(logs.contains("stage=copy_direct_prepare outcome=selected_browser_ready"));
        assertFalse(logs.contains(URL));
        assertFalse(logs.contains("example.com"));
        assertFalse(logs.contains(TARGET_PACKAGE));
        assertFalse(logs.contains("opened"));
        assertFalse(logs.contains("sent"));
    }

    private void addWebHandler(String packageName, String label, String host, String path) {
        ComponentName component = new ComponentName(packageName, packageName + ".BrowserActivity");
        ActivityInfo activity = packages.addActivityIfNotPresent(component);
        activity.enabled = true;
        activity.exported = true;
        activity.applicationInfo.enabled = true;
        activity.nonLocalizedLabel = label;
        IntentFilter filter = new IntentFilter(Intent.ACTION_VIEW);
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        filter.addCategory(Intent.CATEGORY_BROWSABLE);
        filter.addDataScheme("https");
        if (host != null) filter.addDataAuthority(host, null);
        if (path != null) filter.addDataPath(path, PatternMatcher.PATTERN_LITERAL);
        packages.addIntentFilterForActivity(component, filter);
    }

    private static boolean containsPackage(List<CopyDirectBrowser.Choice> choices,
            String packageName) {
        for (CopyDirectBrowser.Choice choice : choices) {
            if (packageName.equals(choice.packageName)) return true;
        }
        return false;
    }
}

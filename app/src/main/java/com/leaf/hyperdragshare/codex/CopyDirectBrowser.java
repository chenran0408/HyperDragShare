package com.leaf.hyperdragshare.codex;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Browser preference is applied only to explicit copy-direct web actions. */
public final class CopyDirectBrowser {
    private CopyDirectBrowser() {}

    public static final class Choice {
        public final String packageName;
        public final String label;
        public Choice(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    public static List<Choice> choices(Context context) {
        PackageManager packages = context.getPackageManager();
        Intent web = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://example.com/copy-direct-probe"))
                .addCategory(Intent.CATEGORY_BROWSABLE);
        Map<String, Choice> browsers = new LinkedHashMap<>();
        for (ResolveInfo result : packages.queryIntentActivities(web,
                PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_RESOLVED_FILTER)) {
            if (result.activityInfo.exported && result.filter != null
                    && result.filter.hasCategory(Intent.CATEGORY_BROWSABLE)
                    && supportsBroadWebAddresses(result.filter)
                    && (result.filter.hasDataScheme("http")
                        || result.filter.hasDataScheme("https"))) {
                String name = result.activityInfo.packageName;
                browsers.putIfAbsent(name, new Choice(name, result.loadLabel(packages).toString()));
            }
        }
        List<Choice> choices = new ArrayList<>(browsers.values());
        choices.sort(Comparator.comparing(choice -> choice.label));
        choices.add(0, new Choice("", "跟随系统默认浏览器"));
        return choices;
    }

    public static Intent route(Context context, Intent action) {
        String scheme = action.getScheme();
        if (!Intent.ACTION_VIEW.equals(action.getAction())
                || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) return action;
        String selected = IntegrationSettings.getCopyDirectBrowserPackage(context);
        if (selected.isEmpty()) return action;
        Intent explicit = new Intent(action).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(selected);
        if (context.getPackageManager().resolveActivity(explicit, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            throw new ActivityNotFoundException("所选复制直达浏览器不可用，请在设置中重新选择");
        }
        return explicit;
    }

    private static boolean supportsBroadWebAddresses(IntentFilter filter) {
        if (filter.countDataAuthorities() == 0) return true;
        if (filter.countDataPaths() != 0 || filter.countDataSchemeSpecificParts() != 0) {
            return false;
        }
        // A wildcard host with no port/path restriction accepts arbitrary web addresses.
        for (int index = 0; index < filter.countDataAuthorities(); index++) {
            IntentFilter.AuthorityEntry authority = filter.getDataAuthority(index);
            if ("*".equals(authority.getHost()) && authority.getPort() == -1) return true;
        }
        return false;
    }
}

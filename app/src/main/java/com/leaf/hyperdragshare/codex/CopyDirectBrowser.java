package com.leaf.hyperdragshare.codex;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
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
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE);
        Map<String, Choice> browsers = new LinkedHashMap<>();
        for (ResolveInfo result : packages.queryIntentActivities(web,
                PackageManager.MATCH_DEFAULT_ONLY | PackageManager.GET_RESOLVED_FILTER)) {
            if (result.activityInfo.exported && result.filter != null
                    && result.filter.hasCategory(Intent.CATEGORY_BROWSABLE)
                    && result.filter.countDataAuthorities() == 0) {
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
}

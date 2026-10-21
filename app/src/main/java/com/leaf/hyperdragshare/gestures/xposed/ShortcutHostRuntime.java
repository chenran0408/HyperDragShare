package com.leaf.hyperdragshare.gestures.xposed;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.ShortcutInfo;
import android.app.BroadcastOptions;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;

import com.leaf.hyperdragshare.gestures.config.ConfigContract;
import com.leaf.hyperdragshare.gestures.config.ShortcutIconLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ShortcutHostRuntime {
    static final String MIUI_HOME = "com.miui.home";
    static final String SYSTEM_UI = "com.android.systemui";
    static final String ACTION_START_SHORTCUT = "com.leaf.hyperdragshare.gestures.START_SHORTCUT";
    static final String ACTION_START_ACTIVITY = "com.leaf.hyperdragshare.gestures.START_ACTIVITY";
    static final String ACTION_START_LAUNCHER_SHORTCUT =
            "com.leaf.hyperdragshare.gestures.START_LAUNCHER_SHORTCUT";
    static final String EXTRA_PACKAGE = "package";
    static final String EXTRA_SHORTCUT_ID = "shortcut_id";
    static final String EXTRA_COMPONENT = "component";
    static final String EXTRA_USER_ID = "user_id";
    static final String EXTRA_OPTIONS = "options";
    static final String EXTRA_INTENT_URI = "intent_uri";
    private static volatile boolean installed;
    private static final Map<String, Long> publishedIconVersions = new HashMap<>();
    private static final Object CATALOG_LOCK = new Object();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final ExecutorService CATALOG_EXECUTOR = Executors.newSingleThreadExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "hypergesture-shortcut-catalog");
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            });
    private static final long CATALOG_DEBOUNCE_MS = 350L;
    private static Context catalogContext;
    private static boolean catalogScheduled;
    private static boolean catalogRunning;
    private static boolean catalogPending;
    private static boolean catalogObserverInstalled;
    private static boolean catalogSourceInstalled;
    private static final Runnable START_CATALOG_REFRESH =
            ShortcutHostRuntime::startCatalogRefresh;

    private ShortcutHostRuntime() { }

    /** Sends an internal SystemUI-to-MiuiHome request with sender identity sharing enabled. */
    static void sendSystemUiRequest(Context context, Intent request) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Bundle options = BroadcastOptions.makeBasic()
                    .setShareIdentityEnabled(true)
                    .toBundle();
            context.sendBroadcast(request, null, options);
        } else {
            context.sendBroadcast(request);
        }
    }

    static synchronized void install(Context context) {
        if (installed) return;
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            Log.i("Shortcut host waiting for MiuiHome application context");
            return;
        }
        Log.attachReporter(appContext);
        RecentsClearButtonRuntime.install(appContext);
        IntentFilter filter = new IntentFilter(ACTION_START_SHORTCUT);
        filter.addAction(ACTION_START_ACTIVITY);
        filter.addAction(ACTION_START_LAUNCHER_SHORTCUT);
        appContext.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context receiverContext, Intent intent) {
                if (!BroadcastSenderValidator.isFromPackage(
                        receiverContext, this, SYSTEM_UI)) {
                    Log.i("Ignored shortcut host request from unauthorized sender");
                    return;
                }
                if (ACTION_START_ACTIVITY.equals(intent.getAction())) {
                    startActivity(receiverContext, intent);
                } else if (ACTION_START_LAUNCHER_SHORTCUT.equals(intent.getAction())) {
                    startLauncherShortcut(receiverContext, intent);
                } else {
                    startShortcut(receiverContext, intent);
                }
            }
        }, filter, Context.RECEIVER_EXPORTED);
        installed = true;
        Log.i("Shortcut launch bridge active in MiuiHome");
    }

    /** Installs the catalog publisher in a host that can query LauncherApps, currently SystemUI. */
    static synchronized void installCatalogSource(Context context) {
        if (catalogSourceInstalled) return;
        Context appContext = context == null ? null : context.getApplicationContext();
        if (appContext == null) {
            Log.e("SystemUI catalog source unavailable: application context is null");
            return;
        }
        Log.attachReporter(appContext);
        installCatalogObserver(appContext);
        catalogSourceInstalled = true;
        requestCatalogRefresh(appContext, 0L);
        Log.i("Shortcut catalog source active in SystemUI");
    }

    private static synchronized void installCatalogObserver(Context context) {
        if (catalogObserverInstalled) return;
        try {
            context.getContentResolver().registerContentObserver(ConfigContract.CATALOG_URI,
                    false, new android.database.ContentObserver(MAIN_HANDLER) {
                        @Override public void onChange(boolean selfChange) {
                            requestCatalogRefresh(context, CATALOG_DEBOUNCE_MS);
                        }
                    });
            catalogObserverInstalled = true;
        } catch (RuntimeException error) {
            Log.e("Cannot observe shortcut catalog refresh requests", error);
        }
    }

    private static void requestCatalogRefresh(Context context, long delayMs) {
        Context appContext = context.getApplicationContext();
        if (appContext == null) appContext = context;
        synchronized (CATALOG_LOCK) {
            catalogContext = appContext;
            if (catalogRunning) {
                catalogPending = true;
                return;
            }
            if (catalogScheduled) MAIN_HANDLER.removeCallbacks(START_CATALOG_REFRESH);
            catalogScheduled = true;
            MAIN_HANDLER.postDelayed(START_CATALOG_REFRESH, Math.max(0L, delayMs));
        }
    }

    private static void startCatalogRefresh() {
        Context context;
        synchronized (CATALOG_LOCK) {
            catalogScheduled = false;
            if (catalogRunning) {
                catalogPending = true;
                return;
            }
            context = catalogContext;
            if (context == null) return;
            catalogRunning = true;
        }
        Context refreshContext = context;
        CATALOG_EXECUTOR.execute(() -> {
            long started = SystemClock.elapsedRealtime();
            try {
                publishCatalog(refreshContext);
                long elapsed = SystemClock.elapsedRealtime() - started;
                Log.i("Shortcut catalog refresh completed in " + elapsed + "ms");
            } catch (Throwable error) {
                Log.e("Shortcut catalog refresh failed at host boundary", error);
            } finally {
                MAIN_HANDLER.post(() -> finishCatalogRefresh(refreshContext));
            }
        });
    }

    private static void finishCatalogRefresh(Context context) {
        boolean refreshAgain;
        synchronized (CATALOG_LOCK) {
            catalogRunning = false;
            refreshAgain = catalogPending;
            catalogPending = false;
        }
        if (refreshAgain) requestCatalogRefresh(context, CATALOG_DEBOUNCE_MS);
    }

    private static void startLauncherShortcut(Context context, Intent request) {
        String expectedPackage = request.getStringExtra(EXTRA_PACKAGE);
        String intentUri = request.getStringExtra(EXTRA_INTENT_URI);
        Bundle options = request.getBundleExtra(EXTRA_OPTIONS);
        if (expectedPackage == null || expectedPackage.isEmpty()
                || intentUri == null || intentUri.isEmpty()) return;
        try {
            Intent launch = Intent.parseUri(intentUri, 0);
            String actualPackage = packageName(context, launch);
            if (!expectedPackage.equals(actualPackage)) {
                throw new SecurityException("Desktop shortcut package changed");
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch, options == null ? Bundle.EMPTY : options);
            Log.i("MiuiHome started desktop shortcut package=" + expectedPackage);
        } catch (Throwable error) {
            Log.e("Cannot start desktop shortcut in MiuiHome", error);
        }
    }

    private static void startShortcut(Context context, Intent intent) {
        if (!ACTION_START_SHORTCUT.equals(intent.getAction())) return;
        String packageName = intent.getStringExtra(EXTRA_PACKAGE);
        String shortcutId = intent.getStringExtra(EXTRA_SHORTCUT_ID);
        Bundle options = intent.getBundleExtra(EXTRA_OPTIONS);
        if (packageName == null || packageName.isEmpty() || shortcutId == null || shortcutId.isEmpty()) return;
        try {
            LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
            if (launcherApps == null) throw new IllegalStateException("LauncherApps unavailable");
            launcherApps.startShortcut(packageName, shortcutId, null,
                    options == null ? Bundle.EMPTY : options, Process.myUserHandle());
            Log.i("MiuiHome started shortcut " + packageName + "/" + shortcutId);
        } catch (Throwable error) {
            Log.e("Cannot start shortcut in MiuiHome", error);
        }
    }

    private static void startActivity(Context context, Intent intent) {
        if (!ACTION_START_ACTIVITY.equals(intent.getAction())) return;
        ComponentName component = ComponentName.unflattenFromString(
                intent.getStringExtra(EXTRA_COMPONENT));
        int userId = intent.getIntExtra(EXTRA_USER_ID, 0);
        Bundle options = intent.getBundleExtra(EXTRA_OPTIONS);
        if (component == null || userId == 0) return;
        try {
            LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
            if (launcherApps == null) throw new IllegalStateException("LauncherApps unavailable");
            launcherApps.startMainActivity(component, userHandle(userId), null,
                    options == null ? Bundle.EMPTY : options);
            Log.i("MiuiHome started activity " + component.flattenToShortString()
                    + " user=" + userId);
        } catch (Throwable error) {
            Log.e("Cannot start cross-user activity in MiuiHome", error);
        }
    }

    static boolean startShortcutDirect(Context context, RuntimeTarget target, Bundle options) {
        if (target == null) return false;
        try {
            if (target.isLauncherShortcut()) {
                // startActivity would silently use the SystemUI user. Let the
                // existing launcher bridge handle cross-user desktop shortcuts.
                if (!userHandle(target.userId).equals(Process.myUserHandle())) return false;
                Intent launch = Intent.parseUri(target.shortcutIntentUri, 0);
                if (!target.packageName.equals(packageName(context, launch))) {
                    throw new SecurityException("Desktop shortcut package changed");
                }
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launch, options == null ? Bundle.EMPTY : options);
            } else {
                LauncherApps apps = context.getSystemService(LauncherApps.class);
                if (apps == null) return false;
                apps.startShortcut(target.packageName, target.shortcutId, null,
                        options == null ? Bundle.EMPTY : options, userHandle(target.userId));
            }
            Log.i("SystemUI started shortcut directly " + target.packageName + "/" + target.shortcutId);
            return true;
        } catch (Throwable error) {
            Log.i("Direct shortcut start unavailable package=" + target.packageName
                    + " error=" + error.getClass().getSimpleName());
            return false;
        }
    }

    static boolean startActivityDirect(Context context, ComponentName component,
                                       int userId, Bundle options) {
        if (component == null || userId == 0) return false;
        try {
            LauncherApps apps = context.getSystemService(LauncherApps.class);
            if (apps == null) return false;
            apps.startMainActivity(component, userHandle(userId), null,
                    options == null ? Bundle.EMPTY : options);
            return true;
        } catch (Throwable error) {
            Log.i("Direct cross-user activity start unavailable component="
                    + component.flattenToShortString() + " error=" + error.getClass().getSimpleName());
            return false;
        }
    }

    private static void publishCatalog(Context context) {
        LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
        ArrayList<String> iconKeys = new ArrayList<>();
        ArrayList<Bitmap> icons = new ArrayList<>();
        CatalogReadResult dynamic = readDynamicShortcuts(context, launcherApps, iconKeys, icons);
        CatalogReadResult desktop = readDesktopShortcuts(context, iconKeys, icons);
        JSONArray previous = null;
        if (!dynamic.readable || !desktop.readable) {
            previous = readPreviousCatalog(context, ConfigContract.KEY_SHORTCUT_CATALOG);
            if (previous == null) {
                Log.e("Shortcut catalog update skipped: a source failed and the existing catalog "
                        + "could not be read; preserving the stored catalog");
                publishActivityCatalog(context, launcherApps);
                return;
            }
        }

        JSONArray out = new JSONArray();
        Set<String> seen = new HashSet<>();
        if (!dynamic.readable) appendPreviousKind(previous, out, seen, false);
        else appendAllUnique(dynamic.items, out, seen);
        if (!desktop.readable) appendPreviousKind(previous, out, seen, true);
        else appendAllUnique(desktop.items, out, seen);

        try {
            try {
                flushShortcutIcons(context, iconKeys, icons);
            } catch (RuntimeException iconError) {
                Log.e("SystemUI could not publish shortcut icons; continuing with catalog", iconError);
            }
            prunePublishedIconVersions(out);
            Bundle extras = new Bundle();
            extras.putString(ConfigContract.KEY_SHORTCUT_CATALOG, out.toString());
            context.getContentResolver().call(ConfigContract.URI, "report_shortcuts", null, extras);
            Log.i("SystemUI published shortcut catalog count=" + out.length()
                    + " dynamic=" + (dynamic.readable ? dynamic.items.length() : "preserved")
                    + " desktop=" + (desktop.readable ? desktop.items.length() : "preserved"));
        } catch (RuntimeException error) {
            Log.e("SystemUI could not publish shortcut catalog", error);
        }
        publishActivityCatalog(context, launcherApps);
    }

    private static CatalogReadResult readDynamicShortcuts(Context context,
                                                           LauncherApps launcherApps,
                                                           ArrayList<String> iconKeys,
                                                           ArrayList<Bitmap> icons) {
        if (launcherApps == null) {
            Log.e("SystemUI LauncherApps unavailable; keeping previous dynamic shortcuts");
            return CatalogReadResult.failed();
        }
        JSONArray out = new JSONArray();
        try {
            LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery().setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                            | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                            | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                            | LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED);
            List<ShortcutInfo> shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle());
            if (shortcuts == null) throw new IllegalStateException("LauncherApps returned null shortcuts");
            for (ShortcutInfo shortcut : shortcuts) {
                if (shortcut == null || !shortcut.isEnabled()) continue;
                CharSequence label = shortcut.getShortLabel();
                if (label == null) label = shortcut.getLongLabel();
                if (label == null) continue;
                JSONObject item = new JSONObject();
                item.put("package", shortcut.getPackage());
                item.put("id", shortcut.getId());
                item.put("label", displayShortcutLabel(context, shortcut.getPackage(),
                        label.toString()));
                item.put("kind", "standard");
                out.put(item);
                try {
                    Drawable icon = launcherApps.getShortcutIconDrawable(shortcut,
                            context.getResources().getDisplayMetrics().densityDpi);
                    queueShortcutIcon(context, iconKeys, icons,
                            ShortcutIconLoader.key(shortcut.getPackage(), shortcut.getId(), 0),
                            shortcut.getLastChangedTimestamp(), icon);
                } catch (RuntimeException iconError) {
                    Log.i("SystemUI could not read icon for shortcut " + shortcut.getPackage()
                            + "/" + shortcut.getId() + ": " + iconError.getClass().getSimpleName());
                }
            }
            return CatalogReadResult.success(out);
        } catch (Exception error) {
            Log.e("SystemUI could not read dynamic shortcuts; keeping prior entries", error);
            return CatalogReadResult.failed();
        }
    }

    private static CatalogReadResult readDesktopShortcuts(Context context,
                                                            ArrayList<String> iconKeys,
                                                            ArrayList<Bitmap> icons) {
        JSONArray out = new JSONArray();
        Set<String> seen = new HashSet<>();
        ArrayList<String> pendingKeys = new ArrayList<>();
        ArrayList<Bitmap> pendingIcons = new ArrayList<>();
        Uri favorites = Uri.parse(
                "content://com.miui.home.launcher.settings/favorites");
        int added = 0;
        try (Cursor cursor = context.getContentResolver().query(
                favorites, null, null, null, null)) {
            if (cursor == null) throw new IllegalStateException("Desktop favorites provider returned null cursor");
            int idColumn = cursor.getColumnIndex("_id");
            int titleColumn = cursor.getColumnIndex("title");
            int intentColumn = cursor.getColumnIndex("intent");
            int typeColumn = cursor.getColumnIndex("itemType");
            int shortcutColumn = cursor.getColumnIndex("isShortcut");
            int iconColumn = cursor.getColumnIndex("icon");
            if (intentColumn < 0) {
                throw new IllegalStateException("Desktop favorites table has no intent column");
            }
            if (typeColumn < 0 && shortcutColumn < 0) {
                throw new IllegalStateException("Desktop favorites table has no shortcut type columns");
            }
            while (cursor.moveToNext()) {
                if (out.length() >= 240) {
                    throw new IllegalStateException("Desktop favorites exceed the 240-item catalog limit");
                }
                int itemType = typeColumn >= 0 && !cursor.isNull(typeColumn)
                        ? cursor.getInt(typeColumn) : -1;
                int isShortcut = shortcutColumn >= 0 && !cursor.isNull(shortcutColumn)
                        ? cursor.getInt(shortcutColumn) : 0;
                if (itemType != 1 && itemType != 14 && isShortcut == 0) continue;
                if (cursor.isNull(intentColumn)) {
                    throw new IllegalStateException("Desktop shortcut row has no intent");
                }
                String rawIntent = cursor.getString(intentColumn);
                if (rawIntent == null || rawIntent.isEmpty()) {
                    throw new IllegalStateException("Desktop shortcut row has an empty intent");
                }
                Intent launch;
                try {
                    launch = Intent.parseUri(rawIntent, 0);
                } catch (Exception error) {
                    throw new IllegalStateException("Cannot parse desktop shortcut intent", error);
                }
                String packageName = packageName(context, launch);
                if (packageName == null || packageName.isEmpty()) {
                    throw new IllegalStateException("Cannot resolve package for desktop shortcut");
                }
                String label = titleColumn >= 0 && !cursor.isNull(titleColumn)
                        ? cursor.getString(titleColumn) : packageName;
                if (label == null || label.isEmpty()) label = packageName;
                String shortcutId = launch.getStringExtra("shortcut_id");
                if (shortcutId == null || shortcutId.isEmpty()) {
                    long rowId = idColumn >= 0 && !cursor.isNull(idColumn)
                            ? cursor.getLong(idColumn) : rawIntent.hashCode();
                    shortcutId = "launcher:" + rowId;
                }
                String key = packageName + "\n" + shortcutId;
                if (!seen.add(key)) continue;
                context.getPackageManager().getApplicationInfo(packageName, 0);
                JSONObject item = new JSONObject();
                item.put("package", packageName);
                item.put("id", shortcutId);
                item.put("label", displayShortcutLabel(context, packageName, label));
                item.put("kind", "launcher");
                item.put("intent", rawIntent);
                item.put("userId", 0);
                out.put(item);
                if (iconColumn >= 0 && !cursor.isNull(iconColumn)) {
                    byte[] encoded = cursor.getBlob(iconColumn);
                    Bitmap icon = encoded == null ? null : BitmapFactory.decodeByteArray(
                            encoded, 0, encoded.length);
                    queueShortcutIcon(context, pendingKeys, pendingIcons,
                            ShortcutIconLoader.key(packageName, shortcutId, 0),
                            encoded == null ? 0L : java.util.Arrays.hashCode(encoded), icon);
                }
                added++;
            }
            iconKeys.addAll(pendingKeys);
            icons.addAll(pendingIcons);
            Log.i("SystemUI read desktop shortcut catalog added=" + added);
            return CatalogReadResult.success(out);
        } catch (Exception error) {
            Log.e("SystemUI could not read desktop favorites; keeping prior launcher entries", error);
            return CatalogReadResult.failed();
        }
    }

    private static final class CatalogReadResult {
        final boolean readable;
        final JSONArray items;

        private CatalogReadResult(boolean readable, JSONArray items) {
            this.readable = readable;
            this.items = items;
        }

        static CatalogReadResult success(JSONArray items) {
            return new CatalogReadResult(true, items);
        }

        static CatalogReadResult failed() {
            return new CatalogReadResult(false, new JSONArray());
        }
    }

    private static JSONArray readPreviousCatalog(Context context, String key) {
        try {
            Bundle catalogs = context.getContentResolver().call(
                    ConfigContract.URI, "get_catalog", null, null);
            if (catalogs == null || !catalogs.containsKey(key)) {
                throw new IllegalStateException("Provider did not return " + key);
            }
            String raw = catalogs.getString(key);
            if (raw == null) throw new IllegalStateException("Provider returned null " + key);
            return new JSONArray(raw);
        } catch (Exception error) {
            Log.e("Cannot read previous catalog " + key + "; partial source result will not replace it",
                    error);
            return null;
        }
    }

    private static void appendAllUnique(JSONArray from, JSONArray to, Set<String> seen) {
        for (int index = 0; index < from.length(); index++) {
            JSONObject item = from.optJSONObject(index);
            appendUnique(item, to, seen);
        }
    }

    private static void appendPreviousKind(JSONArray previous, JSONArray target,
                                           Set<String> seen, boolean launcherShortcuts) {
        for (int index = 0; index < previous.length(); index++) {
            JSONObject item = previous.optJSONObject(index);
            if (item == null) continue;
            boolean isLauncher = "launcher".equals(item.optString("kind"))
                    || !item.optString("intent", "").isEmpty();
            if (isLauncher == launcherShortcuts) appendUnique(item, target, seen);
        }
    }

    private static void appendUnique(JSONObject item, JSONArray target, Set<String> seen) {
        if (item == null) return;
        String packageName = item.optString("package", "");
        String shortcutId = item.optString("id", "");
        String key = packageName + "\n" + shortcutId;
        if (packageName.isEmpty() || shortcutId.isEmpty() || !seen.add(key)) return;
        target.put(item);
    }

    private static synchronized void queueShortcutIcon(Context context, ArrayList<String> keys,
                                          ArrayList<Bitmap> icons, String key, long version,
                                          Drawable drawable) {
        if (drawable == null) return;
        Long previous = publishedIconVersions.get(key);
        if (previous != null && previous == version) return;
        int intrinsic = Math.max(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        int size = Math.max(96, Math.min(144, intrinsic));
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(canvas);
        queueShortcutIcon(context, keys, icons, key, version, bitmap);
    }

    private static void prunePublishedIconVersions(JSONArray catalog) {
        java.util.HashSet<String> current = new java.util.HashSet<>();
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject item = catalog.optJSONObject(i);
            if (item != null) {
                current.add(item.optString("package", "") + "\n"
                        + item.optString("id", ""));
            }
        }
        synchronized (publishedIconVersions) {
            publishedIconVersions.keySet().retainAll(current);
        }
    }

    private static synchronized void queueShortcutIcon(Context context, ArrayList<String> keys,
                                          ArrayList<Bitmap> icons, String key, long version,
                                          Bitmap bitmap) {
        if (bitmap == null || key == null || key.isEmpty()) return;
        Long previous = publishedIconVersions.get(key);
        if (previous != null && previous == version) return;
        publishedIconVersions.put(key, version);
        keys.add(key);
        icons.add(bitmap);
        if (keys.size() >= 6) flushShortcutIcons(context, keys, icons);
    }

    private static void flushShortcutIcons(Context context, ArrayList<String> keys,
                                           ArrayList<Bitmap> icons) {
        if (keys.isEmpty()) return;
        Bundle extras = new Bundle();
        extras.putStringArrayList(ShortcutIconLoader.EXTRA_KEYS,
                new ArrayList<>(keys));
        extras.putParcelableArrayList(ShortcutIconLoader.EXTRA_ICONS,
                new ArrayList<>(icons));
        context.getContentResolver().call(ConfigContract.URI,
                ShortcutIconLoader.METHOD_REPORT, null, extras);
        keys.clear();
        icons.clear();
    }

    private static String packageName(Context context, Intent intent) {
        if (intent.getPackage() != null && !intent.getPackage().isEmpty()) {
            return intent.getPackage();
        }
        if (intent.getComponent() != null) return intent.getComponent().getPackageName();
        try {
            android.content.pm.ResolveInfo resolved = context.getPackageManager()
                    .resolveActivity(intent, 0);
            return resolved == null || resolved.activityInfo == null
                    ? null : resolved.activityInfo.packageName;
        } catch (Exception error) {
            Log.i("Cannot resolve desktop shortcut package: "
                    + error.getClass().getSimpleName());
            return null;
        }
    }

    private static String displayShortcutLabel(Context context, String packageName,
                                               String shortcutLabel) {
        String shortcut = shortcutLabel == null ? "" : shortcutLabel.trim();
        String app = "";
        try {
            android.content.pm.ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(packageName, 0);
            CharSequence label = context.getPackageManager().getApplicationLabel(info);
            if (label != null) app = label.toString().trim();
        } catch (Exception error) {
            Log.i("Cannot read app label for shortcut package=" + packageName + ": "
                    + error.getClass().getSimpleName());
        }
        if (app.isEmpty()) app = packageName == null ? "" : packageName;
        if (shortcut.isEmpty()) return app;
        if (app.isEmpty() || shortcut.startsWith(app)) return shortcut;
        return app + " · " + shortcut;
    }

    private static void publishActivityCatalog(Context context, LauncherApps launcherApps) {
        if (launcherApps == null) {
            Log.e("SystemUI LauncherApps unavailable; keeping previous dual-app activities");
            return;
        }
        JSONArray out = new JSONArray();
        try {
            android.os.UserManager users = context.getSystemService(android.os.UserManager.class);
            Object dualUser = de.robv.android.xposed.XposedHelpers.callMethod(users, "getUserInfo", 999);
            List<LauncherActivityInfo> activities = dualUser == null
                    ? java.util.Collections.emptyList()
                    : launcherApps.getActivityList(null, userHandle(999));
            if (activities == null) {
                throw new IllegalStateException("LauncherApps returned null dual-app activity list");
            }
            for (LauncherActivityInfo activity : activities) {
                if (activity == null) {
                    throw new IllegalStateException("LauncherApps returned a null dual-app activity");
                }
                ComponentName component = activity.getComponentName();
                CharSequence label = activity.getLabel();
                if (component == null || label == null) {
                    throw new IllegalStateException("Dual-app activity has no component or label");
                }
                if (out.length() >= 160) {
                    throw new IllegalStateException("Dual-app activity list exceeds the 160-item limit");
                }
                JSONObject item = new JSONObject();
                item.put("component", component.flattenToString());
                item.put("label", label.toString());
                item.put("userId", 999);
                out.put(item);
            }
            Bundle extras = new Bundle();
            extras.putString(ConfigContract.KEY_ACTIVITY_CATALOG, out.toString());
            context.getContentResolver().call(ConfigContract.URI, "report_activities",
                    null, extras);
            Log.i("SystemUI published dual-app activity catalog count=" + out.length());
        } catch (Exception error) {
            Log.e("SystemUI could not read dual-app activity list; preserving previous catalog", error);
        }
    }

    private static UserHandle userHandle(int userId) {
        return UserHandle.getUserHandleForUid(userId * 100000);
    }
}

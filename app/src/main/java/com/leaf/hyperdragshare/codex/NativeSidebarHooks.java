package com.leaf.hyperdragshare.codex;

import android.content.ContentProviderClient;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedBridge;

/** Exact adapter for SecurityCenter 40001365. It never replaces the Home gesture/Binder handoff. */
final class NativeSidebarHooks {
    private static final String TAG = "DragShare/Sidebar";
    private static final String WRAPPER = "com.miui.dock.sidebar.p";
    private static final String TURBO = "com.miui.gamebooster.windowmanager.newbox.TurboLayout";
    private static final Map<Object, Panel> panels = new IdentityHashMap<>();
    private static final Map<Object, View[]> watched = new IdentityHashMap<>();
    private static final Map<View, android.view.ViewTreeObserver.OnGlobalLayoutListener> listeners = new IdentityHashMap<>();
    private static final Binder reporter = new Binder();
    private static int[] lastRegions = new int[0];
    private static final ExecutorService ipc = Executors.newSingleThreadExecutor(r -> new Thread(r, "sidebar-stash"));

    static void install(ClassLoader loader) {
        try {
            Class<?> wrapper = XposedHelpers.findClass(WRAPPER, loader);
            Class<?> turbo = XposedHelpers.findClass(TURBO, loader);
            // Validate the measured APK's accessors before installing any UI mutation.
            for (String method : new String[]{"B", "C", "H", "o"}) wrapper.getDeclaredMethod(method);
            for (String method : new String[]{"getDockLayout", "getAppsLayout", "getBoxView", "F", "D", "G", "e0"})
                turbo.getDeclaredMethod(method);
            turbo.getDeclaredField("j"); turbo.getDeclaredField("s");
            XposedBridge.hookAllConstructors(wrapper, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Object owner = param.thisObject;
                    View cover = (View) XposedHelpers.callMethod(owner, "w");
                    View body = (View) XposedHelpers.callMethod(owner, "B");
                    try {
                        if (cover.getContext().getPackageManager().getPackageInfo("com.miui.securitycenter", 0)
                                .getLongVersionCode() != 40001365L) return;
                        watched.put(owner, new View[]{cover, body});
                        for (View view : new View[]{cover, body}) {
                            android.view.ViewTreeObserver observer = view.getViewTreeObserver();
                            android.view.ViewTreeObserver.OnGlobalLayoutListener previous = listeners.remove(view);
                            if (previous != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(previous);
                            android.view.ViewTreeObserver.OnGlobalLayoutListener listener = () -> reportRegions(cover.getContext());
                            listeners.put(view, listener);
                            observer.addOnGlobalLayoutListener(listener);
                        }
                    } catch (PackageManager.NameNotFoundException | RuntimeException error) {
                        DragShareLog.w(TAG, "Cannot observe native sidebar handle", error);
                    }
                }
            });
            XposedHelpers.findAndHookMethod("com.miui.gamebooster.service.DockWindowManagerService", loader,
                    "onDestroy", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    for (Panel panel : panels.values()) panel.dispose();
                    panels.clear();
                    listeners.forEach((view, listener) -> {
                        if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
                    });
                    listeners.clear(); watched.clear();
                    reportRegions((Context) param.thisObject);
                }
            });
            XposedHelpers.findAndHookMethod(wrapper, "W", boolean.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!(boolean) param.args[0]) {
                        Panel panel = panels.remove(param.thisObject);
                        if (panel != null) panel.dispose();
                        View view = (View) XposedHelpers.callMethod(param.thisObject, "B");
                        view.post(() -> reportRegions(view.getContext()));
                        return;
                    }
                    Object owner = param.thisObject;
                    View view = (View) XposedHelpers.callMethod(owner, "C");
                    // Wait for the original Home->dock handoff animation, without holding its callback.
                    view.postDelayed(() -> attach(owner), 700);
                }
            });
            XposedHelpers.findAndHookMethod(turbo, "e0", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Object owner = XposedHelpers.callMethod(param.thisObject, "getSidebarWrapper");
                    Panel panel = panels.get(owner);
                    if (panel != null && !panel.switching) {
                        try { panel.nativeSearchChanged(); }
                        catch (RuntimeException error) { panel.fail(error); }
                    }
                }
            });
            DragShareLog.i(TAG, "exact sidebar hooks installed build=" + BuildConfig.VERSION_CODE);
        } catch (ReflectiveOperationException | XposedHelpers.ClassNotFoundError | RuntimeException error) {
            DragShareLog.w(TAG, "Sidebar interface unavailable; native behavior retained", error);
        }
    }

    private static void reportRegions(Context context) {
        java.util.ArrayList<Integer> coordinates = new java.util.ArrayList<>();
        java.util.Set<View> candidates = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        // sidebar.p.B is the full window container. Only w() is the real edge handle.
        for (View[] views : watched.values()) candidates.add(views[0]);
        for (Panel panel : panels.values()) {
            candidates.add(panel.rail);
            View active = panel.activeRegion();
            if (active != null) candidates.add(active);
        }
        for (View view : candidates) {
            Rect local = new Rect();
            if (view.isEnabled() && view.isShown() && view.isAttachedToWindow() && view.getLocalVisibleRect(local) && !local.isEmpty()) {
                // Hyper's raw input coordinates are display/screen coordinates, not root-view coordinates.
                int[] screen = new int[2]; view.getLocationOnScreen(screen);
                coordinates.add(screen[0] + local.left); coordinates.add(screen[1] + local.top);
                coordinates.add(screen[0] + local.right); coordinates.add(screen[1] + local.bottom);
            }
        }
        int[] regions = coordinates.stream().mapToInt(Integer::intValue).toArray();
        if (java.util.Arrays.equals(regions, lastRegions)) return;
        lastRegions = regions;
        ipc.execute(() -> {
            Bundle input = new Bundle(); input.putBinder("owner", reporter); input.putIntArray("regions", regions);
            try { context.getContentResolver().call(ImageStagingClient.BASE_URI, NativeSidebarState.REPORT, null, input); }
            catch (IllegalArgumentException | IllegalStateException | SecurityException error) {
                DragShareLog.w(TAG, "Cannot publish sidebar input ownership", error);
            }
        });
    }

    private static void attach(Object owner) {
        if (panels.containsKey(owner) || !(boolean) XposedHelpers.callMethod(owner, "H")) return;
        RelativeLayout turbo = (RelativeLayout) XposedHelpers.callMethod(owner, "C");
        try {
            long version = turbo.getContext().getPackageManager().getPackageInfo("com.miui.securitycenter", 0).getLongVersionCode();
            if (version != 40001365L || !(boolean) XposedHelpers.callMethod(turbo, "F")) {
                DragShareLog.w(TAG, "Sidebar version/layout unsupported; native behavior retained version=" + version);
                return;
            }
            Panel panel = new Panel(owner, turbo);
            panels.put(owner, panel);
            panel.attach();
        } catch (PackageManager.NameNotFoundException | RuntimeException error) {
            Panel panel = panels.remove(owner);
            if (panel != null) panel.dispose();
            DragShareLog.w(TAG, "Unable to attach sidebar rail; native behavior restored", error);
        }
    }

    private enum Section { APPS, SEARCH, STASH, TOOLS }

    private static final class Panel implements View.OnAttachStateChangeListener {
        final Object owner;
        final RelativeLayout turbo;
        final Context context;
        final View dock;
        final View apps;
        final View tools;
        final int scene;
        final boolean left;
        final int[] padding;
        final LinearLayout rail;
        final FrameLayout pane;
        final Map<View, Integer> hidden = new IdentityHashMap<>();
        final Map<Section, TextView> buttons = new java.util.EnumMap<>(Section.class);
        Section selected;
        boolean switching, disposed;
        int generation;
        ISidebarContent content;
        IBinder.DeathRecipient contentDeath;
        SurfaceView surface;
        final ISidebarHost host = new ISidebarHost.Stub() {
            @Override public void requestClose() {
                turbo.post(() -> {
                    if (!disposed) {
                        try { XposedHelpers.callMethod(XposedHelpers.callMethod(owner, "o"), "O0", false); }
                        catch (RuntimeException error) { fail(error); }
                    }
                });
            }
        };

        Panel(Object owner, RelativeLayout turbo) {
            this.owner = owner; this.turbo = turbo; context = turbo.getContext();
            dock = (View) XposedHelpers.callMethod(turbo, "getDockLayout");
            apps = (View) XposedHelpers.callMethod(turbo, "getAppsLayout");
            if (dock == null || dock.getParent() != turbo) throw new IllegalStateException("Native dock is not ready");
            tools = (View) XposedHelpers.callMethod(turbo, "getBoxView");
            scene = (int) XposedHelpers.callMethod(XposedHelpers.getObjectField(turbo, "j"), "c");
            if (scene != 1 && scene != 3 && scene != 4 && scene != 5)
                throw new IllegalStateException("Unverified sidebar scene " + scene);
            left = (boolean) XposedHelpers.callMethod(turbo, "G");
            padding = new int[]{turbo.getPaddingLeft(), turbo.getPaddingTop(), turbo.getPaddingRight(), turbo.getPaddingBottom()};
            rail = new LinearLayout(context); rail.setOrientation(LinearLayout.VERTICAL);
            rail.setGravity(Gravity.CENTER); rail.setElevation(dp(4));
            rail.setBackground(background(color(android.R.attr.colorBackground), 20));
            pane = new FrameLayout(context);
            pane.setBackground(background(color(android.R.attr.colorBackground), 20));
        }

        void attach() {
            turbo.setPadding(padding[0] + (left ? dp(52) : 0), padding[1],
                    padding[2] + (left ? 0 : dp(52)), padding[3]);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(dp(48), dp(224));
            params.addRule(left ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
            if (left) params.leftMargin = -dp(52); else params.rightMargin = -dp(52);
            turbo.addView(rail, params);
            addButton(Section.APPS, "应用"); addButton(Section.SEARCH, "搜索");
            addButton(Section.STASH, "中转站");
            addButton(Section.TOOLS, scene == 1 ? "游戏" : scene == 3 ? "视频" : scene == 5 ? "美颜" : "工具");
            turbo.addOnAttachStateChangeListener(this);
            selected = nativeSearchOpen() ? Section.SEARCH : scene == 4 ? Section.APPS : Section.TOOLS;
            apply();
            DragShareLog.i(TAG, "native rail attached scene=" + scene + " build=" + BuildConfig.VERSION_CODE);
        }

        void addButton(Section section, String label) {
            TextView button = new TextView(context);
            button.setText(label); button.setTextSize(11);
            RailIcon icon = new RailIcon(section);
            icon.setBounds(0, 0, dp(22), dp(22));
            button.setCompoundDrawables(null, icon, null, null);
            button.setCompoundDrawablePadding(dp(3)); button.setPadding(0, dp(6), 0, 0);
            button.setGravity(Gravity.CENTER); button.setContentDescription(label);
            button.setOnClickListener(v -> select(section));
            rail.addView(button, new LinearLayout.LayoutParams(dp(48), dp(56)));
            buttons.put(section, button);
        }

        void select(Section section) {
            if (disposed || switching || section == selected || XposedHelpers.getBooleanField(turbo, "s")) return;
            try {
                releaseContent(); restoreVisibility();
                selected = section;
                boolean shouldSearch = section == Section.SEARCH;
                if (nativeSearchOpen() != shouldSearch) {
                    switching = true;
                    XposedHelpers.callMethod(turbo, "e0");
                    awaitNativeTransition(SystemClock.uptimeMillis() + 2000);
                } else apply();
            } catch (RuntimeException error) { fail(error); }
        }

        void nativeSearchChanged() {
            releaseContent(); restoreVisibility();
            selected = nativeSearchOpen() ? Section.SEARCH : scene == 4 ? Section.APPS : Section.TOOLS;
            switching = true;
            awaitNativeTransition(SystemClock.uptimeMillis() + 2000);
        }

        void awaitNativeTransition(long deadline) {
            turbo.postDelayed(() -> {
                if (disposed) return;
                if (XposedHelpers.getBooleanField(turbo, "s")) {
                    if (SystemClock.uptimeMillis() < deadline) awaitNativeTransition(deadline);
                    else fail(new IllegalStateException("Native app search transition did not finish"));
                } else {
                    switching = false;
                    try { apply(); } catch (RuntimeException error) { fail(error); }
                }
            }, 50);
        }

        boolean nativeSearchOpen() { return (boolean) XposedHelpers.callMethod(turbo, "D"); }

        void apply() {
            if (disposed) return;
            restoreVisibility();
            if (selected != Section.TOOLS && tools != null) hide(tools);
            // Keep the original app strip and its launch/edit/drag handlers in the native hierarchy.
            if (selected == Section.STASH || selected == Section.TOOLS && tools == null) {
                android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                        Math.min(dp(200), Math.max(1, (int) (metrics.widthPixels * 0.4f))),
                        Math.min(dp(320), Math.max(1, (int) (metrics.heightPixels * 0.4f))));
                params.addRule(left ? RelativeLayout.RIGHT_OF : RelativeLayout.LEFT_OF, dock.getId());
                if (left) params.leftMargin = dp(8); else params.rightMargin = dp(8);
                if (pane.getParent() == null) turbo.addView(pane, params);
                if (selected == Section.STASH) openStash();
                else message("当前应用没有原生场景工具");
            } else if (pane.getParent() == turbo) turbo.removeView(pane);
            for (Map.Entry<Section, TextView> entry : buttons.entrySet()) {
                int tint = color(entry.getKey() == selected ? android.R.attr.colorAccent : android.R.attr.textColorPrimary);
                entry.getValue().setTextColor(tint);
                ((RailIcon) entry.getValue().getCompoundDrawables()[1]).tint(tint);
            }
            rail.bringToFront();
            reportRegions(context);
        }

        View activeRegion() {
            if (selected == Section.STASH || selected == Section.TOOLS && tools == null) return pane;
            return switch (selected) {
                case APPS -> dock;
                case SEARCH -> apps;
                case TOOLS -> tools;
                case STASH -> pane;
            };
        }

        void hide(View view) { hidden.putIfAbsent(view, view.getVisibility()); view.setVisibility(View.GONE); }
        void restoreVisibility() { hidden.forEach(View::setVisibility); hidden.clear(); }

        void openStash() {
            message("正在连接中转站…");
            int current = ++generation;
            surface = new SurfaceView(context);
            SurfaceView target = surface;
            // Above the native window's background, below other windows and drag shadows.
            surface.setZOrderOnTop(true);
            pane.addView(surface, new FrameLayout.LayoutParams(-1, -1));
            surface.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override public void surfaceCreated(SurfaceHolder holder) {
                    target.post(() -> connect(target, current));
                }
                @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    ISidebarContent lease = content;
                    if (current == generation && lease != null && width > 0 && height > 0) ipc.execute(() -> {
                        try { lease.resize(width, height); }
                        catch (RemoteException error) { turbo.post(() -> fail(error)); }
                    });
                }
                @Override public void surfaceDestroyed(SurfaceHolder holder) {
                    if (current == generation) releaseContent();
                }
            });
        }

        void connect(SurfaceView target, int current) {
            if (disposed || current != generation || !target.isAttachedToWindow()) return;
            Bundle input = new Bundle();
            input.putBinder("host_token", target.getHostToken()); input.putBinder("owner", host.asBinder());
            input.putInt("display_id", target.getDisplay().getDisplayId());
            input.putInt("width", target.getWidth()); input.putInt("height", target.getHeight());
            ipc.execute(() -> {
                try (ContentProviderClient provider = context.getContentResolver().acquireContentProviderClient(ImageStagingClient.BASE_URI)) {
                    if (provider == null) throw new IllegalStateException("Module provider is unavailable");
                    Bundle response = provider.call(StashSurfaceHost.METHOD_OPEN, null, input);
                    if (response == null) throw new IllegalStateException("Module returned no sidebar surface");
                    response.setClassLoader(SurfaceControlViewHost.SurfacePackage.class.getClassLoader());
                    SurfaceControlViewHost.SurfacePackage pack = response.getParcelable("surface", SurfaceControlViewHost.SurfacePackage.class);
                    ISidebarContent lease = ISidebarContent.Stub.asInterface(response.getBinder("content"));
                    if (pack == null || lease == null) throw new IllegalStateException("Module returned an incomplete sidebar surface");
                    turbo.post(() -> mount(target, current, pack, lease));
                } catch (RemoteException | RuntimeException error) { turbo.post(() -> { if (current == generation) fail(error); }); }
            });
        }

        void mount(SurfaceView target, int current, SurfaceControlViewHost.SurfacePackage pack, ISidebarContent lease) {
            boolean transferred = false;
            try {
                if (disposed || current != generation || !target.isAttachedToWindow()) { lease.close(); return; }
                content = lease;
                IBinder.DeathRecipient death = () -> turbo.post(() -> { if (!disposed && content == lease) fail(new IllegalStateException("Module stash process exited")); });
                lease.asBinder().linkToDeath(death, 0);
                contentDeath = death;
                target.setChildSurfacePackage(pack);
                transferred = true;
                DragShareLog.i(TAG, "stash surface mounted scene=" + scene);
            } catch (RemoteException | RuntimeException error) { fail(error); }
            finally { if (!transferred) pack.release(); }
        }

        void releaseContent() {
            ++generation;
            ISidebarContent lease = content; content = null;
            if (lease != null) {
                if (contentDeath != null) lease.asBinder().unlinkToDeath(contentDeath, 0);
                ipc.execute(() -> {
                    try { lease.close(); }
                    catch (RemoteException error) { DragShareLog.w(TAG, "Stash lease already disconnected", error); }
                });
            }
            contentDeath = null; surface = null;
            pane.removeAllViews();
            if (pane.getParent() == turbo) turbo.removeView(pane);
        }

        void message(String message) {
            pane.removeAllViews();
            TextView text = new TextView(context); text.setText(message); text.setGravity(Gravity.CENTER);
            text.setTextColor(color(android.R.attr.textColorPrimary));
            pane.addView(text, new FrameLayout.LayoutParams(-1, -1));
        }

        void fail(Exception error) {
            if (disposed) { DragShareLog.w(TAG, "Previous sidebar session failed after closing", error); return; }
            DragShareLog.w(TAG, "Sidebar integration failed; restoring native UI", error);
            panels.remove(owner); dispose();
            Toast.makeText(context, "侧边栏扩展暂不可用，已保留原生功能", Toast.LENGTH_LONG).show();
        }

        void dispose() {
            if (disposed) return;
            disposed = true; releaseContent(); restoreVisibility();
            turbo.removeOnAttachStateChangeListener(this);
            turbo.removeView(rail);
            turbo.setPadding(padding[0], padding[1], padding[2], padding[3]);
            reportRegions(context);
        }

        @Override public void onViewAttachedToWindow(View view) {}
        @Override public void onViewDetachedFromWindow(View view) { panels.remove(owner); dispose(); }
        int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
        int color(int attribute) {
            TypedValue value = new TypedValue(); context.getTheme().resolveAttribute(attribute, value, true);
            return value.resourceId != 0 ? context.getColor(value.resourceId) : value.data;
        }
        GradientDrawable background(int color, int radius) {
            GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
        }
    }

    /** Small stroke icons use the native theme; no module resource IDs are resolved in the host. */
    private static final class RailIcon extends android.graphics.drawable.Drawable {
        private final Section section;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        RailIcon(Section section) {
            this.section = section; paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(1.7f); paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        }
        void tint(int color) { paint.setColor(color); invalidateSelf(); }
        @Override public void draw(android.graphics.Canvas canvas) {
            canvas.save(); canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            switch (section) {
                case APPS -> {
                    for (int x : new int[]{4, 14}) for (int y : new int[]{4, 14})
                        canvas.drawRoundRect(x, y, x + 6, y + 6, 1.5f, 1.5f, paint);
                }
                case SEARCH -> { canvas.drawCircle(10, 10, 6, paint); canvas.drawLine(15, 15, 21, 21, paint); }
                case STASH -> {
                    canvas.drawRoundRect(3, 7, 21, 20, 2, 2, paint);
                    canvas.drawLine(7, 4, 17, 4, paint); canvas.drawLine(3, 12, 21, 12, paint);
                    canvas.drawLine(9, 15, 15, 15, paint);
                }
                case TOOLS -> {
                    for (int x : new int[]{5, 12, 19}) canvas.drawLine(x, 3, x, 21, paint);
                    canvas.drawCircle(5, 8, 2, paint); canvas.drawCircle(12, 16, 2, paint); canvas.drawCircle(19, 10, 2, paint);
                }
            }
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}

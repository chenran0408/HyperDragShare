package com.leaf.hyperdragshare.codex;

import com.leaf.hyperdragshare.gestures.xposed.FanFreeformHook;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class MainHook implements IXposedHookLoadPackage {
    static final String TAPLUS_PACKAGE = "com.miui.contentextension";
    static final String AICR_PACKAGE = "com.xiaomi.aicr";
    static final String TRUST_SERVICE_PACKAGE = "com.xiaomi.trustservice";
    static final String ANDROID_PACKAGE = "android";
    static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    static final String SECURITY_CENTER_PACKAGE = "com.miui.securitycenter";
    static final long SECURITY_CENTER_VERSION = 40001365L;
    private static volatile boolean sidebarHookInstalled;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (TAPLUS_PACKAGE.equals(lpparam.packageName)) {
            PortalHooks.install(lpparam.classLoader);
        } else if (AICR_PACKAGE.equals(lpparam.packageName)) {
            AicrCopyHooks.install(lpparam.classLoader);
            AicrCoreAccessHooks.install(lpparam.classLoader);
        } else if (TRUST_SERVICE_PACKAGE.equals(lpparam.packageName)) {
            TrustServiceRiskHooks.install(lpparam);
        } else if (SYSTEM_UI_PACKAGE.equals(lpparam.packageName)
                || "com.miui.home".equals(lpparam.packageName)) {
            new FanFreeformHook().handleLoadPackage(lpparam);
        } else if (SECURITY_CENTER_PACKAGE.equals(lpparam.packageName)
                && "com.miui.securitycenter:ui".equals(lpparam.processName)
                && android.os.Build.VERSION.SDK_INT == 37) {
            de.robv.android.xposed.XposedHelpers.findAndHookMethod(android.app.Application.class,
                    "attach", android.content.Context.class, new de.robv.android.xposed.XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (sidebarHookInstalled) return;
                    android.content.Context context = (android.content.Context) param.args[0];
                    if (!SECURITY_CENTER_PACKAGE.equals(context.getPackageName())) return;
                    try {
                        long version = context.getPackageManager()
                                .getPackageInfo(SECURITY_CENTER_PACKAGE, 0).getLongVersionCode();
                        if (version == SECURITY_CENTER_VERSION) {
                            sidebarHookInstalled = true;
                            NativeSidebarHooks.install(lpparam.classLoader);
                        } else {
                            DragShareLog.i("DragShare/Sidebar", "native sidebar hook skipped unsupported version=" + version);
                        }
                    } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException error) {
                        DragShareLog.w("DragShare/Sidebar", "native sidebar version check failed; native behavior retained", error);
                    }
                }
            });
        } else if (ANDROID_PACKAGE.equals(lpparam.packageName)) {
            RootProcessFrameworkHooks.install(lpparam.classLoader);
            BackTapFrameworkHooks.install(lpparam);
            OverlayNotificationHooks.install(lpparam.classLoader);
        }
    }
}

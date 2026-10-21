package com.leaf.hyperdragshare.codex;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class MainHook implements IXposedHookLoadPackage {
    static final String TAPLUS_PACKAGE = "com.miui.contentextension";
    static final String AICR_PACKAGE = "com.xiaomi.aicr";
    static final String ANDROID_PACKAGE = "android";
    static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (TAPLUS_PACKAGE.equals(lpparam.packageName)) {
            PortalHooks.install(lpparam.classLoader);
        } else if (AICR_PACKAGE.equals(lpparam.packageName)) {
            AicrCopyHooks.install(lpparam.classLoader);
        } else if (SYSTEM_UI_PACKAGE.equals(lpparam.packageName)) {
            NativePressSystemUiHooks.install(lpparam.classLoader);
        } else if (ANDROID_PACKAGE.equals(lpparam.packageName)) {
            RootProcessFrameworkHooks.install(lpparam.classLoader);
            BackTapFrameworkHooks.install(lpparam);
        }
    }
}

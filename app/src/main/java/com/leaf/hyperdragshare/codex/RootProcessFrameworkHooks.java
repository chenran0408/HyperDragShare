package com.leaf.hyperdragshare.codex;

import android.content.pm.ApplicationInfo;
import android.util.SparseArray;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Protect only our live hosts' Root children from Android's phantom count trimming. */
final class RootProcessFrameworkHooks {
    private static final String TAG = "DragShare/RootProcess: ";

    private RootProcessFrameworkHooks() {}

    static void install(ClassLoader loader) {
        try {
            Class<?> record = Class.forName("com.android.server.am.PhantomProcessRecord", false, loader);
            Class<?> service = Class.forName("com.android.server.am.ActivityManagerService", false, loader);
            Class<?> list = Class.forName("com.android.server.am.PhantomProcessList", false, loader);
            Class<?> owner = Class.forName("com.android.server.am.ProcessRecord", false, loader);
            Field uid = field(record, "mUid"), pid = field(record, "mPid"), ppid = field(record, "mPpid");
            Field am = field(record, "mService"), processes = field(service, "mPhantomProcessList");
            Field owners = field(list, "mPhantomToAppProcessMap"), info = field(owner, "info");
            Method getPid = owner.getMethod("getPid");
            Method isKilled = owner.getMethod("isKilled");
            Method isKilledByAm = owner.getMethod("isKilledByAm");
            getPid.setAccessible(true);
            isKilled.setAccessible(true);
            isKilledByAm.setAccessible(true);
            Method kill = record.getDeclaredMethod("killLocked", String.class, boolean.class);
            XposedBridge.hookMethod(kill, new XC_MethodHook() {
                private boolean enabled = true;

                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!enabled || !"Trimming phantom processes".equals(param.args[0])) return;
                    try {
                        Object child = param.thisObject;
                        if (uid.getInt(child) != 0) return;
                        int childPid = pid.getInt(child);
                        // The trim caller already holds the phantom list lock. Never add a PID-map lock here.
                        SparseArray<?> appOwners = (SparseArray<?>) owners.get(processes.get(am.get(child)));
                        Object app = appOwners.get(childPid);
                        if (app == null || (int) getPid.invoke(app) != ppid.getInt(child)
                                || (boolean) isKilled.invoke(app) || (boolean) isKilledByAm.invoke(app)) return;
                        ApplicationInfo application = (ApplicationInfo) info.get(app);
                        if (application == null || !(BuildConfig.APPLICATION_ID.equals(application.packageName)
                                || MainHook.TAPLUS_PACKAGE.equals(application.packageName))) return;
                        param.setResult(null);
                        XposedBridge.log(TAG + "kept Root child pid=" + childPid + " owner=" + application.packageName);
                    } catch (ReflectiveOperationException | ClassCastException error) {
                        enabled = false;
                        XposedBridge.log(TAG + "ownership check failed; original trimming retained: " + error);
                    }
                }
            });
            XposedBridge.log(TAG + "installed exact phantom count-trim hook build="
                    + BuildConfig.VERSION_NAME + "/" + BuildConfig.VERSION_CODE);
        } catch (ReflectiveOperationException | IllegalArgumentException | SecurityException | LinkageError error) {
            XposedBridge.log(TAG + "hook unavailable; original trimming retained: " + error);
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}

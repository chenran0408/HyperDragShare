package com.leaf.hyperdragshare.codex;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.lang.ref.WeakReference;

/** Tracks the module's currently resumed Activity for windows that need an Activity token. */
final class ForegroundActivity implements Application.ActivityLifecycleCallbacks {
    private static volatile WeakReference<Activity> resumed = new WeakReference<>(null);
    private static boolean installed;

    private ForegroundActivity() {}

    static void install(Application application) {
        if (installed) return;
        installed = true;
        application.registerActivityLifecycleCallbacks(new ForegroundActivity());
    }

    static Activity resumed() {
        Activity activity = resumed.get();
        return activity == null || activity.isFinishing() || activity.isDestroyed() ? null : activity;
    }

    @Override public void onActivityResumed(Activity activity) {
        resumed = new WeakReference<>(activity);
    }

    @Override public void onActivityPaused(Activity activity) {
        if (resumed.get() == activity) resumed = new WeakReference<>(null);
    }

    @Override public void onActivityDestroyed(Activity activity) {
        if (resumed.get() == activity) resumed = new WeakReference<>(null);
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
}

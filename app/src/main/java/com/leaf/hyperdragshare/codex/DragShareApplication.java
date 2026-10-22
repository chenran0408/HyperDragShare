package com.leaf.hyperdragshare.codex;

import android.app.Application;

/** Application-level initialization shared by module-app processes. */
public final class DragShareApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DragShareLog.configure(DragShareSettings.readLocal(this));
        DragShareDiagnostics.captureRuntimeOnce(this, "module application created", null);
        ForegroundActivity.install(this);
        SharedImageCleanupJob.schedule(this);
    }
}

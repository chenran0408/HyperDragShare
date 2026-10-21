package com.leaf.hyperdragshare.codex;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import java.io.IOException;

/** Debug-only metadata / explicit point check. Does not inject a thumb or a game gesture. */
public final class GameCaptureProbe extends Instrumentation {
    private Bundle arguments;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments;
        start();
    }

    @Override public void onStart() {
        Bundle report = new Bundle();
        boolean passed = false;
        try {
            Bundle enabled = DragShareSettings.defaults().toBundle();
            enabled.putBoolean("pause_automatic_in_games", true);
            DragShareSettings settings = DragShareSettings.fromBundle(enabled);
            int games = 0;
            for (ApplicationInfo info : getTargetContext().getPackageManager().getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(0))) {
                if (info.category != ApplicationInfo.CATEGORY_GAME) continue;
                if (!GameCapturePolicy.shouldPause(getTargetContext(), settings, info.packageName)
                        || GameCapturePolicy.shouldPause(getTargetContext(), DragShareSettings.defaults(), info.packageName)) {
                    throw new IllegalStateException("Declared game policy mismatch");
                }
                games++;
            }
            report.putInt("declaredGames", games);
            report.putBoolean("savedEnabled", DragShareSettings.readLocal(getTargetContext()).pauseAutomaticInGames);
            if (arguments != null && arguments.containsKey("x")) {
                float x = Float.parseFloat(arguments.getString("x"));
                float y = Float.parseFloat(arguments.getString("y"));
                String expected = arguments.getString("expected");
                String[] earlySource = {null};
                CapturedContent content;
                try (RootNodeCaptureClient.Session session = RootNodeCaptureClient.open(getTargetContext())) {
                    content = session.capture(x, y, AccessibilityBlacklist.builtInPackages(getTargetContext()),
                            true, false, source -> { earlySource[0] = source; return true; });
                }
                if (content == null || !expected.equals(earlySource[0]) || !expected.equals(content.sourcePackage)) {
                    throw new IllegalStateException("Root candidate source mismatch");
                }
                report.putBoolean("candidateSourceMatches", true);
                report.putBoolean("nonGameRemainsAvailable", !GameCapturePolicy.shouldPause(
                        getTargetContext(), settings, content.sourcePackage));
                report.putInt("textLength", content.text.length());
            }
            passed = true;
        } catch (IOException | InterruptedException | RuntimeException error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            report.putString("error", error.toString());
        }
        finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, report);
    }
}

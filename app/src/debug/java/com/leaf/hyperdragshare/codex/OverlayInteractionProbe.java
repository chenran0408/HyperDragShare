package com.leaf.hyperdragshare.codex;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** Debug-only synthetic text for inspecting the real overlay without reading user content. */
public final class OverlayInteractionProbe extends Instrumentation {
    private boolean longText;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        longText = arguments != null && "long".equals(arguments.getString("sample"));
        start();
    }

    @Override public void onStart() {
        String sample = "传送门文字识别\n小米15 Pro HyperOS 4\n复制 搜索 分享 12345";
        if (longText) {
            StringBuilder paragraphs = new StringBuilder();
            for (int index = 1; index <= 25; index++) {
                paragraphs.append("第").append(index).append("段：选择文字后可以复制、合并、搜索与分享。\n");
            }
            sample = paragraphs.toString();
        }
        Bundle report = new Bundle();
        boolean accepted = BigBangOverlayLauncher.showText(getTargetContext(), sample, -1, -1, null);
        report.putBoolean("accepted", accepted);
        report.putBoolean("longText", longText);
        report.putInt("syntheticLength", sample.length());
        if (accepted) {
            try {
                Thread.sleep(90000L); // Keep the target process alive for bounded, manual UI inspection.
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                report.putString("error", "inspection interrupted");
                accepted = false;
            }
        }
        finish(accepted ? Activity.RESULT_OK : Activity.RESULT_CANCELED, report);
    }
}

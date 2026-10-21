package com.leaf.hyperdragshare.codex;

import android.content.ClipData;
import android.content.ClipDescription;
import android.os.PersistableBundle;

/** Reads only explicit, non-sensitive text from an external single-item clip. */
final class AicrClipboardText {
    private AicrClipboardText() {}

    static String read(ClipData clip) {
        if (clip == null || clip.getItemCount() != 1) return null;
        ClipDescription description = clip.getDescription();
        if (!description.hasMimeType("text/*")) return null;
        CharSequence label = description.getLabel();
        if (label != null && ("HyperDragShare".contentEquals(label)
                || "HyperDragShare image".contentEquals(label))) return null;
        PersistableBundle extras = description.getExtras();
        if (extras != null && extras.getBoolean("android.content.extra.IS_SENSITIVE", false)) return null;
        CharSequence value = clip.getItemAt(0).getText();
        if (value == null) return null;
        String text = value.toString();
        return text.isBlank() ? null : text;
    }
}

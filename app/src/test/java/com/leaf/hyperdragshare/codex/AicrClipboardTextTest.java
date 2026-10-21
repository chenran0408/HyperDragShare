package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ClipData;
import android.net.Uri;
import android.os.PersistableBundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33)
public final class AicrClipboardTextTest {
    @Test
    public void readsExplicitTextWithoutTrimmingAndIgnoresBlankClips() {
        String text = " \n原文保留空白\t ";
        assertEquals(text, AicrClipboardText.read(ClipData.newPlainText(null, text)));
        assertNull(AicrClipboardText.read(ClipData.newPlainText("source", " \n\t ")));
        assertNull(AicrClipboardText.read(null));
    }

    @Test
    public void ignoresActualModuleLabelsAndSensitiveText() {
        assertNull(AicrClipboardText.read(ClipData.newPlainText("HyperDragShare", "own text")));
        assertNull(AicrClipboardText.read(ClipData.newPlainText("HyperDragShare image", "own image text")));
        ClipData sensitive = ClipData.newPlainText("external source", "generated sensitive sample");
        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
        sensitive.getDescription().setExtras(extras);
        assertNull(AicrClipboardText.read(sensitive));
        extras.putBoolean("android.content.extra.IS_SENSITIVE", false);
        sensitive.getDescription().setExtras(extras);
        assertEquals("generated sensitive sample", AicrClipboardText.read(sensitive));
    }

    @Test
    public void rejectsImageMimeAndDoesNotCoerceUriToText() {
        ClipData image = new ClipData("image", new String[]{"image/png"}, new ClipData.Item("image description"));
        assertNull(AicrClipboardText.read(image));
        ClipData uri = ClipData.newRawUri("source", Uri.parse("content://generated/images/135"));
        assertTrue(uri.getDescription().hasMimeType("text/*"));
        assertNull(AicrClipboardText.read(uri));
    }

    @Test
    public void rejectsMultipleItemsRatherThanOpeningOnlyTheFirst() {
        ClipData clip = ClipData.newPlainText("source", "first generated item");
        clip.addItem(new ClipData.Item("second generated item"));
        assertNull(AicrClipboardText.read(clip));
    }
}

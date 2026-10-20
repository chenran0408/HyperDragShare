package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Intent;
import android.text.SpannableString;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 33)
public final class TextSegmentationInputTest {
    @Test public void readsOnlyTheTextFieldBelongingToTheIncomingAction() {
        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, new SpannableString(" 原文\n保留空格 "))
                .putExtra(Intent.EXTRA_PROCESS_TEXT, "other field");
        assertEquals(" 原文\n保留空格 ", TextSegmentationActivity.resolveText(share));
        share.setAction(Intent.ACTION_PROCESS_TEXT);
        assertEquals("other field", TextSegmentationActivity.resolveText(share));
    }

    @Test public void rejectsUnrelatedActionsMimeAndEmptyText() {
        Intent input = new Intent(Intent.ACTION_VIEW).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "text");
        assertNull(TextSegmentationActivity.resolveText(input));
        input.setAction(Intent.ACTION_SEND).setType("image/png");
        assertNull(TextSegmentationActivity.resolveText(input));
        input.setType("text/plain").putExtra(Intent.EXTRA_TEXT, " \n\t");
        assertNull(TextSegmentationActivity.resolveText(input));
        input.setAction(Intent.ACTION_PROCESS_TEXT);
        assertNull(TextSegmentationActivity.resolveText(input));
    }
}

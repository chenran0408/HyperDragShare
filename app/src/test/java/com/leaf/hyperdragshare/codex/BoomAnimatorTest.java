package com.leaf.hyperdragshare.codex;

import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import java.time.Duration;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BoomAnimatorTest {
    @Test
    public void cancellingEntryLeavesTheChipVisibleAndAtItsLayoutPosition() {
        View chip = new View(RuntimeEnvironment.getApplication());
        chip.setTranslationX(80f);
        chip.setTranslationY(160f);
        BoomAnimator.makeBoomAnimation(chip);
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(50));

        chip.animate().cancel();

        assertEquals(1f, chip.getScaleX(), 0f);
        assertEquals(1f, chip.getScaleY(), 0f);
        assertEquals(1f, chip.getAlpha(), 0f);
        assertEquals(0f, chip.getTranslationX(), 0f);
        assertEquals(0f, chip.getTranslationY(), 0f);
    }
}

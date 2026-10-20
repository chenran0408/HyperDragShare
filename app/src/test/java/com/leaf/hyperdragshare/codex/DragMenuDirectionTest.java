package com.leaf.hyperdragshare.codex;

import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public final class DragMenuDirectionTest {
    @Test public void displayRefreshPreservesSelectedEdgeForEveryStyle() throws Exception {
        DragShareController controller = new DragShareController(RuntimeEnvironment.getApplication());
        Field direction = field("simpleMenuPosition");
        Field settings = field("settings");
        var refresh = DragShareController.class.getDeclaredMethod("refreshDisplayGeometry");
        refresh.setAccessible(true);
        for (int style = 0; style < 4; style++) {
            Bundle values = new Bundle();
            values.putInt("ui_style", style);
            settings.set(controller, DragShareSettings.fromBundle(values));
            for (int edge = 0; edge < 4; edge++) {
                direction.setInt(controller, edge);
                assertTrue((Boolean) refresh.invoke(controller));
                assertEquals("style=" + style + ", edge=" + edge,
                        edge, direction.getInt(controller));
            }
        }
    }

    private static Field field(String name) throws Exception {
        Field field = DragShareController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}

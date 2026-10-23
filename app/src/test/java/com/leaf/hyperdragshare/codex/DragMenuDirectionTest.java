package com.leaf.hyperdragshare.codex;

import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.lang.reflect.Field;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public final class DragMenuDirectionTest {
    @Test public void positionPolicyUsesFixedSidesNearHandAndFollowEdgeAcrossStyles() {
        for (int style = 0; style < 4; style++) {
            Bundle values = new Bundle();
            values.putInt("ui_style", style);
            for (int edge = DragShareSettings.SIMPLE_MENU_POSITION_TOP;
                    edge <= DragShareSettings.SIMPLE_MENU_POSITION_RIGHT; edge++) {
                values.putInt("simple_menu_position", edge);
                int configured = DragShareSettings.fromBundle(values).simpleMenuPosition;
                assertEquals(edge, GestureMath.initialMenuPosition(configured, 200f, 1000));
                assertFalse(GestureMath.followsDragEdge(configured));
            }
            values.putInt("simple_menu_position", DragShareSettings.SIMPLE_MENU_POSITION_NEAR_HAND);
            int nearHand = DragShareSettings.fromBundle(values).simpleMenuPosition;
            assertEquals(DragShareSettings.SIMPLE_MENU_POSITION_LEFT,
                    GestureMath.initialMenuPosition(nearHand, 100f, 1000));
            assertEquals(DragShareSettings.SIMPLE_MENU_POSITION_RIGHT,
                    GestureMath.initialMenuPosition(nearHand, 900f, 1000));
            values.putInt("simple_menu_position", DragShareSettings.SIMPLE_MENU_POSITION_DRAG_EDGE);
            int follow = DragShareSettings.fromBundle(values).simpleMenuPosition;
            assertEquals(DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM,
                    GestureMath.initialMenuPosition(follow, 500f, 1000));
            assertTrue(GestureMath.followsDragEdge(follow));
        }
    }

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
                refresh.invoke(controller);
                assertEquals("style=" + style + ", edge=" + edge,
                        edge, direction.getInt(controller));
            }
        }
    }

    @Test public void bottomInputDropZoneDoesNotMaskBottomMenuTriggerAcrossStyles() {
        int width = 1080;
        int height = 2400;
        int bottomInset = 96;
        int triggerY = height - bottomInset - 40;
        int zoneHeight = 412;
        for (int style = 0; style < 4; style++) {
            Bundle values = new Bundle();
            values.putInt("ui_style", style);
            for (int fixedEdge = DragShareSettings.SIMPLE_MENU_POSITION_TOP;
                    fixedEdge <= DragShareSettings.SIMPLE_MENU_POSITION_RIGHT; fixedEdge++) {
                values.putInt("simple_menu_position", fixedEdge);
                int configured = DragShareSettings.fromBundle(values).simpleMenuPosition;
                int effectiveEdge = GestureMath.initialMenuPosition(configured, width / 2f, width);
                boolean defer = GestureMath.shouldDeferToBottomInputDropZone(
                        width / 2f, triggerY, width, height, bottomInset, zoneHeight, 180, effectiveEdge);
                assertEquals("style=" + style + ", fixedEdge=" + fixedEdge,
                        effectiveEdge != DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM, defer);
            }

            values.putInt("simple_menu_position", DragShareSettings.SIMPLE_MENU_POSITION_DRAG_EDGE);
            int configuredFollow = DragShareSettings.fromBundle(values).simpleMenuPosition;
            int initialFollowEdge = GestureMath.initialMenuPosition(configuredFollow, width / 2f, width);
            int followedEdge = GestureMath.nearestMenuEdge(width / 2f, triggerY,
                    width, 80, height - bottomInset, 180, initialFollowEdge);
            assertEquals(DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM, followedEdge);
            assertFalse("style=" + style + " follow-to-bottom",
                    GestureMath.shouldDeferToBottomInputDropZone(
                            width / 2f, triggerY, width, height, bottomInset, zoneHeight, 180, followedEdge));
        }
    }

    @Test public void bottomInputDropZoneStillKeepsCentralDropAndSideAreas() {
        int width = 1080;
        int height = 2400;
        int bottomInset = 96;
        int zoneHeight = 412;
        int top = DragShareSettings.SIMPLE_MENU_POSITION_TOP;
        assertTrue(GestureMath.shouldDeferToBottomInputDropZone(
                width / 2f, height - bottomInset - 40, width, height, bottomInset, zoneHeight, 180, top));
        assertFalse(GestureMath.shouldDeferToBottomInputDropZone(
                width * 0.1f, height - bottomInset - 40, width, height, bottomInset, zoneHeight, 180, top));
        assertFalse(GestureMath.shouldDeferToBottomInputDropZone(
                width / 2f, height - bottomInset - zoneHeight - 1, width, height,
                bottomInset, zoneHeight, 180, top));
        assertTrue(GestureMath.shouldDeferToBottomInputDropZone(
                width / 2f, height - bottomInset - 220, width, height,
                bottomInset, zoneHeight, 180, DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM));
        assertFalse(GestureMath.shouldDeferToBottomInputDropZone(
                width / 2f, height - bottomInset - 40, width, height,
                bottomInset, zoneHeight, 180, DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM));
    }

    private static Field field(String name) throws Exception {
        Field field = DragShareController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}

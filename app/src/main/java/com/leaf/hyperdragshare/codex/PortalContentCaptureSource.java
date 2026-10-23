package com.leaf.hyperdragshare.codex;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;

import java.net.URI;
import java.util.Map;

import de.robv.android.xposed.XposedHelpers;

/** The only adapter allowed to read Taplus' private fields and methods. */
final class PortalContentCaptureSource {
    private PortalContentCaptureSource() {}

    static String clipboardCardText(Class<?> serviceClass) {
        return (String) XposedHelpers.callStaticMethod(serviceClass, "getClipboardContent");
    }

    static Object clipboardCardOwner(Object listener) {
        return XposedHelpers.getObjectField(listener, "this$0");
    }

    static void dismissClipboardCard(Object owner) {
        XposedHelpers.callMethod(owner, "hideSplashFloatView", true);
    }

    static CapturedContent capture(Object taplusService, DragShareSettings settings) {
        if (taplusService == null) {
            return null;
        }
        Class<?> serviceClass = taplusService.getClass();
        try {
            String sourcePackage = (String) XposedHelpers.callStaticMethod(serviceClass, "getInjectorPackage");
            if (BuildConfig.APPLICATION_ID.equals(sourcePackage)) return null;
            android.content.Context context = (android.content.Context) taplusService;
            if (AccessibilityBlacklist.isBlocked(context, settings, sourcePackage)
                    || GameCapturePolicy.shouldPause(context, settings, sourcePackage)) {
                DragShareLog.i("DragShare/PortalCapture",
                        "portal content ignored source=" + sourcePackage + " reason=excluded_source");
                return null;
            }
            Rect sourceBounds = (Rect) XposedHelpers.callMethod(taplusService, "getContentRect");
            Object textMode = XposedHelpers.getStaticObjectField(serviceClass, "sIsTextMode");
            if (Boolean.TRUE.equals(textMode)) {
                String value = (String) XposedHelpers.getStaticObjectField(serviceClass, "sContent");
                return CapturedContent.text(value, sourcePackage, sourceBounds);
            }
            Object result = XposedHelpers.getStaticObjectField(serviceClass, "mCatcherResult");
            Bundle imageMode = imagePickMode(result);
            if (!hasCurrentImagePayload(imageMode)) {
                DragShareLog.i("DragShare/PortalCapture",
                        "portal image ignored source=" + sourcePackage + " reason=missing_image_payload");
                return null;
            }
            Bitmap value = (Bitmap) XposedHelpers.callStaticMethod(serviceClass, "getBitmap");
            if (!matchesCurrentImageBitmap(imageMode, value)) {
                DragShareLog.i("DragShare/PortalCapture",
                        "portal image ignored source=" + sourcePackage + " reason=stale_bitmap");
                return null;
            }
            // HyperOS 4's cancelTask() recycles the service-owned sBitmap. The
            // module stages images asynchronously, so keep an independent snapshot.
            Bitmap snapshot = value.copy(Bitmap.Config.ARGB_8888, false);
            DragShareLog.i("DragShare/PortalCapture",
                    "portal image accepted source=" + sourcePackage
                            + " origin=" + imageOrigin(imageMode)
                            + " bounds=" + sourceBounds
                            + " bitmap=" + snapshot.getWidth() + "x" + snapshot.getHeight());
            return CapturedContent.image(snapshot, sourcePackage, sourceBounds, true);
        } catch (NoSuchFieldError | NoSuchMethodError | XposedHelpers.InvocationTargetError | RuntimeException error) {
            DragShareLog.w("DragShare/PortalCapture", "Unable to read portal 5.1.5 content; keeping host behavior", error);
            if (taplusService instanceof android.content.Context) {
                android.widget.Toast.makeText((android.content.Context) taplusService,
                        "无法读取传送门内容，请查看模块日志", android.widget.Toast.LENGTH_LONG).show();
            }
            return null;
        }
    }

    private static Bundle imagePickMode(Object result) {
        if (result == null) return null;
        try {
            Object params = XposedHelpers.callMethod(result, "getParams", "image_pick");
            if (!(params instanceof Map)) return null;
            Object mode = ((Map<?, ?>) params).get("image_pick_mode");
            return mode instanceof Bundle ? (Bundle) mode : null;
        } catch (RuntimeException | LinkageError error) {
            return null;
        }
    }

    static boolean hasCurrentImagePayload(Bundle imageMode) {
        if (imageMode == null) return false;
        if (imageMode.containsKey("imgUrl")) {
            Object url = imageMode.get("imgUrl");
            return url instanceof String && isLoadableImageUrl((String) url);
        }
        if (imageMode.containsKey("viewBitmap")) {
            Object candidate = imageMode.get("viewBitmap");
            return candidate instanceof Bitmap && !((Bitmap) candidate).isRecycled();
        }
        return false;
    }

    static boolean matchesCurrentImageBitmap(Bundle imageMode, Bitmap value) {
        if (!hasCurrentImagePayload(imageMode) || value == null || value.isRecycled()) return false;
        if (imageMode.containsKey("imgUrl")) return true;
        if (imageMode.containsKey("viewBitmap")) {
            return value == imageMode.get("viewBitmap");
        }
        return true;
    }

    private static boolean isLoadableImageUrl(String value) {
        if (value == null || value.isEmpty() || !value.equals(value.trim())) return false;
        String normalized = value.startsWith("//") ? "http:" + value : value;
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            return ("http".equals(scheme) || "https".equals(scheme))
                    && uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (java.net.URISyntaxException error) {
            return false;
        }
    }

    private static String imageOrigin(Bundle imageMode) {
        if (imageMode == null) return "unknown";
        if (imageMode.containsKey("imgUrl")) return "image_url";
        if (imageMode.containsKey("viewBitmap")) return "view_bitmap";
        return "other";
    }

    static PointF initialPoint(Object taplusService, float fallbackX, float fallbackY) {
        if (fallbackX >= 0f && fallbackY >= 0f) {
            return new PointF(fallbackX, fallbackY);
        }
        if (taplusService != null) {
            try {
                PointF point = (PointF) XposedHelpers.callMethod(
                        taplusService,
                        "getFirstTouchPoint");
                if (point != null) {
                    return new PointF(point.x, point.y);
                }
                Point injectorPoint = (Point) XposedHelpers.callStaticMethod(
                        taplusService.getClass(),
                        "getInjectorPoint");
                if (injectorPoint != null) {
                    return new PointF(injectorPoint.x, injectorPoint.y);
                }
            } catch (NoSuchMethodError | XposedHelpers.InvocationTargetError | RuntimeException error) {
                DragShareLog.w("DragShare/PortalCapture", "Unable to read portal initial point", error);
            }
        }
        return null;
    }
}

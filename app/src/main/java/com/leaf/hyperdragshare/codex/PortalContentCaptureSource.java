package com.leaf.hyperdragshare.codex;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;

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
            boolean sourceExcluded = AccessibilityBlacklist.isBlocked(context, settings, sourcePackage)
                    || GameCapturePolicy.shouldPause(context, settings, sourcePackage);
            String rejection = PortalCapturePolicy.rejectionReason(
                    sourcePackage, sourceExcluded, null,
                    context.getResources().getDisplayMetrics().density);
            if (rejection != null) {
                DragShareLog.i("DragShare/PortalCapture",
                        "portal content ignored source=" + sourcePackage + " reason=" + rejection);
                return null;
            }
            Rect sourceBounds = (Rect) XposedHelpers.callMethod(taplusService, "getContentRect");
            Object textMode = XposedHelpers.getStaticObjectField(serviceClass, "sIsTextMode");
            if (Boolean.TRUE.equals(textMode)) {
                String value = (String) XposedHelpers.getStaticObjectField(serviceClass, "sContent");
                return CapturedContent.text(value, sourcePackage, sourceBounds);
            }
            float density = context.getResources().getDisplayMetrics().density;
            rejection = PortalCapturePolicy.rejectionReason(
                    sourcePackage, false, sourceBounds, density);
            if (rejection != null) {
                DragShareLog.i("DragShare/PortalCapture",
                        "portal image ignored source=" + sourcePackage + " reason=" + rejection
                                + " bounds=" + sourceBounds);
                return null;
            }
            Bitmap value = (Bitmap) XposedHelpers.callStaticMethod(serviceClass, "getBitmap");
            if (value == null || value.isRecycled()) {
                return null;
            }
            // HyperOS 4's cancelTask() recycles the service-owned sBitmap. The
            // module stages images asynchronously, so keep an independent snapshot.
            Bitmap snapshot = value.copy(Bitmap.Config.ARGB_8888, false);
            DragShareLog.i("DragShare/PortalCapture",
                    "portal image accepted source=" + sourcePackage + " bounds=" + sourceBounds);
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

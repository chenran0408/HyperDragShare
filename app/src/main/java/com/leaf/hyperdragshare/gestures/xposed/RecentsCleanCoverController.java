package com.leaf.hyperdragshare.gestures.xposed;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/** OS4 APK fallback for the launcher clear button that is no longer a hooked View. */
final class RecentsCleanCoverController {
    private final Context context;
    private final WindowManager windowManager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private CoverView view;
    private Rect attachedArea;
    // The wallpaper cache retains weak callbacks; keep ours alive until delivery.
    private BlurredWallpaperCache.Callback wallpaperCallback;

    RecentsCleanCoverController(Context context, WindowManager windowManager) {
        this.context = context;
        this.windowManager = windowManager;
    }

    @SuppressLint("RtlHardcoded") // Physical screen coordinates match the OEM button.
    void setVisible(boolean visible) {
        if (Looper.myLooper() != main.getLooper()) {
            main.post(() -> setVisible(visible));
            return;
        }
        if (!visible || windowManager == null) {
            detach();
            return;
        }
        Rect display = windowManager.getCurrentWindowMetrics().getBounds();
        // The supplied APK's coordinates are a portrait approximation. Do not
        // block an unrelated landscape control before its layout is verified.
        if (display.width() >= display.height()) {
            detach();
            return;
        }
        Rect area = new Rect(display.left + Math.round(display.width() * .386f),
                display.top + Math.round(display.height() * .8716f),
                display.left + Math.round(display.width() * .614f),
                display.top + Math.round(display.height() * .977f));
        if (area.isEmpty()) { detach(); return; }
        if (view != null && area.equals(attachedArea)) return;
        detach();
        CoverView next = new CoverView(context);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                area.width(), area.height(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = area.left;
        params.y = area.top;
        params.setTitle("HyperGestureRecentsCleanCover");
        params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        try {
            windowManager.addView(next, params);
            view = next;
            attachedArea = new Rect(area);
            wallpaperCallback = bitmap -> main.post(() -> {
                if (view == next) next.setWallpaper(bitmap);
            });
            Bitmap cached = BlurredWallpaperCache.getOrRequest(context, 28, wallpaperCallback);
            if (cached != null) next.setWallpaper(cached);
            Log.i("Recents clear-button cover attached area=" + area);
        } catch (Throwable error) {
            Log.e("Cannot attach recents clear-button cover", error);
        }
    }

    private void detach() {
        CoverView current = view;
        view = null;
        attachedArea = null;
        wallpaperCallback = null;
        if (current == null || windowManager == null) return;
        try {
            windowManager.removeViewImmediate(current);
        } catch (Throwable error) {
            Log.e("Cannot detach recents clear-button cover", error);
        }
    }

    private static final class CoverView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private Bitmap wallpaper;

        CoverView(Context context) {
            super(context);
            setClickable(true);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }

        void setWallpaper(Bitmap bitmap) { wallpaper = bitmap; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            bounds.set(0, 0, getWidth(), getHeight());
            float radius = getWidth() * .3f;
            if (wallpaper != null && !wallpaper.isRecycled()) {
                canvas.drawBitmap(wallpaper, null, bounds, paint);
                paint.setColor(0x14000000);
            } else {
                paint.setColor(0xff20232b);
            }
            canvas.drawRoundRect(bounds, radius, radius, paint);
        }
    }
}

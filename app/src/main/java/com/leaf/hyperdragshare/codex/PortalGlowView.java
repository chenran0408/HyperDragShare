package com.leaf.hyperdragshare.codex;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Full-screen, non-interactive edge glow used by the portal-style overlay. */
final class PortalGlowView extends View {
    private static final int PARTICLE_COUNT = 18;
    private static final float MAX_GLOW_THICKNESS_DP = 118f;

    private final float density;
    private final boolean dark;
    private final Paint hazePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint radialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint horizonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ribbonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ribbonPath = new Path();
    private final Matrix edgeTransform = new Matrix();

    private LinearGradient hazeShader;
    private LinearGradient horizonShader;
    private RadialGradient radialShader;
    private ValueAnimator expansionAnimator;

    private boolean running;
    private long animationStartUptime;
    private int edgePosition = DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM;
    private int edgeInset;
    private float targetPullProgress;
    private float displayedPullProgress;
    private float expansionProgress;
    private float pointerFraction = 0.5f;

    PortalGlowView(Context context, boolean dark, int bottomInset) {
        super(context);
        this.dark = dark;
        this.edgeInset = Math.max(0, bottomInset);
        this.density = context.getResources().getDisplayMetrics().density;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setForceDarkAllowed(false);
        }
        ribbonPaint.setStyle(Paint.Style.STROKE);
        ribbonPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    void setEdge(int menuPosition, int edgeInset) {
        if (menuPosition < DragShareSettings.SIMPLE_MENU_POSITION_TOP
                || menuPosition > DragShareSettings.SIMPLE_MENU_POSITION_RIGHT) {
            throw new IllegalArgumentException("Unknown portal glow edge: " + menuPosition);
        }
        this.edgePosition = menuPosition;
        this.edgeInset = Math.max(0, edgeInset);
        rebuildEdgeTransform(getWidth(), getHeight());
        rebuildShaders(getWidth(), getHeight());
        invalidate();
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        animationStartUptime = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    void stop() {
        running = false;
        if (expansionAnimator != null) {
            expansionAnimator.cancel();
            expansionAnimator = null;
        }
    }

    void setPullProgress(float progress, float pointerXFraction) {
        targetPullProgress = GestureMath.clamp01(progress);
        pointerFraction = GestureMath.clamp01(pointerXFraction);
        if (!running) {
            invalidate();
        }
    }

    void expandMenu() {
        if (expansionProgress >= 1f) {
            return;
        }
        if (expansionAnimator != null) {
            expansionAnimator.cancel();
        }
        expansionAnimator = ValueAnimator.ofFloat(expansionProgress, 1f);
        expansionAnimator.setDuration(380L);
        expansionAnimator.setInterpolator(new DecelerateInterpolator(1.7f));
        expansionAnimator.addUpdateListener(animator -> {
            expansionProgress = (float) animator.getAnimatedValue();
            invalidate();
        });
        expansionAnimator.start();
    }

    void collapseMenu() {
        if (expansionProgress <= 0f) {
            return;
        }
        if (expansionAnimator != null) {
            expansionAnimator.cancel();
        }
        expansionAnimator = ValueAnimator.ofFloat(expansionProgress, 0f);
        expansionAnimator.setDuration(240L);
        expansionAnimator.setInterpolator(new DecelerateInterpolator(1.4f));
        expansionAnimator.addUpdateListener(animator -> {
            expansionProgress = (float) animator.getAnimatedValue();
            invalidate();
        });
        expansionAnimator.start();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        rebuildEdgeTransform(width, height);
        rebuildShaders(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        if (hazeShader == null || radialShader == null || horizonShader == null) {
            rebuildShaders(getWidth(), getHeight());
        }

        float delta = targetPullProgress - displayedPullProgress;
        if (Math.abs(delta) < 0.002f) {
            displayedPullProgress = targetPullProgress;
        } else {
            displayedPullProgress += delta * 0.18f;
        }

        float pull = easeOut(displayedPullProgress);
        float strength = Math.min(1f, 0.18f + pull * 0.68f + expansionProgress * 0.22f);
        float virtualWidth = virtualWidth(getWidth(), getHeight());
        float virtualHeight = virtualHeight(getWidth(), getHeight());
        float contentBottom = Math.max(1f, virtualHeight - edgeInset);
        float glowHeight = dp(36) + dp(68) * pull + dp(14) * expansionProgress;
        float horizontalShift = (pointerFraction - 0.5f) * virtualWidth * 0.18f;

        int edgeSave = canvas.save();
        canvas.concat(edgeTransform);
        int contentSave = canvas.save();
        canvas.clipRect(0f, Math.max(0f, contentBottom - glowHeight), virtualWidth, contentBottom);

        hazePaint.setShader(hazeShader);
        hazePaint.setAlpha(Math.round(255f * strength));
        canvas.drawRect(0f, contentBottom - glowHeight, virtualWidth, contentBottom, hazePaint);

        int radialSave = canvas.save();
        canvas.translate(horizontalShift, 0f);
        radialPaint.setShader(radialShader);
        radialPaint.setAlpha(Math.round(255f * Math.min(1f, strength * 1.12f)));
        canvas.drawCircle(virtualWidth / 2f, contentBottom + dp(24),
                Math.max(virtualWidth * 0.48f, dp(220)), radialPaint);
        canvas.restoreToCount(radialSave);

        drawRibbons(canvas, contentBottom, pull, strength, horizontalShift);
        drawParticles(canvas, contentBottom, glowHeight, strength, horizontalShift);
        canvas.restoreToCount(contentSave);

        horizonPaint.setShader(horizonShader);
        horizonPaint.setAlpha(Math.round(255f * Math.min(1f, strength * 1.18f)));
        canvas.drawRect(0f, contentBottom - dp(2.2f), virtualWidth, contentBottom, horizonPaint);
        canvas.restoreToCount(edgeSave);

        if (running) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    private void rebuildShaders(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        float virtualWidth = virtualWidth(width, height);
        float virtualHeight = virtualHeight(width, height);
        float contentBottom = Math.max(1f, virtualHeight - edgeInset);
        int blue = dark ? 0xA84F78FF : 0x985C8BFF;
        int cyan = dark ? 0xB044DDF1 : 0xA249E4EE;
        int rose = dark ? 0x8EC868FF : 0x7EDB72D7;
        hazeShader = new LinearGradient(
                0f,
                contentBottom - dp(MAX_GLOW_THICKNESS_DP),
                0f,
                contentBottom,
                new int[]{Color.TRANSPARENT, 0x143F6DFF, blue, cyan},
                new float[]{0f, 0.34f, 0.72f, 1f},
                Shader.TileMode.CLAMP);
        radialShader = new RadialGradient(
                virtualWidth / 2f,
                contentBottom + dp(24),
                Math.max(virtualWidth * 0.48f, dp(220)),
                new int[]{0xE4EAFDFF, cyan, blue, rose, Color.TRANSPARENT},
                new float[]{0f, 0.12f, 0.38f, 0.66f, 1f},
                Shader.TileMode.CLAMP);
        horizonShader = new LinearGradient(
                0f,
                0f,
                virtualWidth,
                0f,
                new int[]{Color.TRANSPARENT, rose, 0xFFE8FFFF, cyan, blue, Color.TRANSPARENT},
                new float[]{0f, 0.16f, 0.43f, 0.62f, 0.84f, 1f},
                Shader.TileMode.CLAMP);
    }

    private void rebuildEdgeTransform(int width, int height) {
        if (width <= 0 || height <= 0) {
            edgeTransform.reset();
            return;
        }
        float screenWidth = width;
        float screenHeight = height;
        float[] values;
        switch (edgePosition) {
            case DragShareSettings.SIMPLE_MENU_POSITION_TOP:
                values = new float[]{
                        1f, 0f, 0f,
                        0f, -1f, screenHeight,
                        0f, 0f, 1f};
                break;
            case DragShareSettings.SIMPLE_MENU_POSITION_LEFT:
                values = new float[]{
                        0f, -1f, screenWidth,
                        1f, 0f, 0f,
                        0f, 0f, 1f};
                break;
            case DragShareSettings.SIMPLE_MENU_POSITION_RIGHT:
                values = new float[]{
                        0f, 1f, 0f,
                        1f, 0f, 0f,
                        0f, 0f, 1f};
                break;
            case DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM:
                edgeTransform.reset();
                return;
            default:
                throw new IllegalArgumentException("Unknown portal glow edge: " + edgePosition);
        }
        edgeTransform.setValues(values);
    }

    private float virtualWidth(int width, int height) {
        return isVerticalEdge() ? height : width;
    }

    private float virtualHeight(int width, int height) {
        return isVerticalEdge() ? width : height;
    }

    private boolean isVerticalEdge() {
        return edgePosition == DragShareSettings.SIMPLE_MENU_POSITION_LEFT
                || edgePosition == DragShareSettings.SIMPLE_MENU_POSITION_RIGHT;
    }

    private void drawRibbons(
            Canvas canvas,
            float contentBottom,
            float pull,
            float strength,
            float horizontalShift) {
        long elapsed = Math.max(0L, SystemClock.uptimeMillis() - animationStartUptime);
        float phase = (elapsed % 2400L) / 2400f;
        int[] colors = {0xFF67E8F4, 0xFF78A4FF, 0xFFE178E8};
        for (int index = 0; index < colors.length; index++) {
            float lane = index - 1f;
            float sway = (float) Math.sin((phase + index * 0.23f) * Math.PI * 2f);
            float startX = virtualWidth(getWidth(), getHeight())
                    * (0.18f + index * 0.32f) + horizontalShift * 0.5f;
            float peakY = contentBottom - dp(20) - dp(56) * pull - dp(8) * sway;
            ribbonPath.reset();
            ribbonPath.moveTo(startX - dp(150), contentBottom + dp(4));
            ribbonPath.cubicTo(
                    startX - dp(82), contentBottom - dp(18),
                    startX + lane * dp(52), peakY,
                    startX + dp(150), contentBottom + dp(3));
            ribbonPaint.setColor(colors[index]);
            ribbonPaint.setStrokeWidth(dp(1.2f + pull * 1.1f));
            ribbonPaint.setAlpha(Math.round(150f * strength));
            canvas.drawPath(ribbonPath, ribbonPaint);
        }
    }

    private void drawParticles(
            Canvas canvas,
            float contentBottom,
            float glowHeight,
            float strength,
            float horizontalShift) {
        float seconds = Math.max(0L, SystemClock.uptimeMillis() - animationStartUptime) / 1000f;
        particlePaint.setColor(0xFFF1FFFF);
        for (int index = 0; index < PARTICLE_COUNT; index++) {
            float cycle = (seconds * (0.18f + (index % 4) * 0.025f) + index * 0.137f) % 1f;
            float seed = ((index * 47) % 101) / 100f;
            float x = seed * virtualWidth(getWidth(), getHeight())
                    + horizontalShift * (0.2f + seed * 0.35f);
            x += (float) Math.sin(seconds * 1.7f + index) * dp(6);
            float y = contentBottom - cycle * glowHeight * (0.38f + seed * 0.54f);
            float fade = (float) Math.sin(cycle * Math.PI);
            particlePaint.setAlpha(Math.round(180f * strength * Math.max(0f, fade)));
            canvas.drawCircle(x, y, dp(0.8f + (index % 3) * 0.35f), particlePaint);
        }
    }

    private float dp(float value) {
        return value * density;
    }

    private static float easeOut(float value) {
        float inverse = 1f - GestureMath.clamp01(value);
        return 1f - inverse * inverse;
    }
}

package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class DragShareController {
    private static final String TAG = "DragShare/UI";
    private static final int PREVIEW_TEXT_WIDTH_DP = 184;
    private static final int PREVIEW_TEXT_HEIGHT_DP = 112;
    private static final int PREVIEW_IMAGE_SIZE_DP = 148;
    private static final int PORTAL_PREVIEW_TEXT_SIZE_DP = 112;
    private static final int PORTAL_PREVIEW_IMAGE_SIZE_DP = 116;
    private static final int MENU_TRIGGER_DP = 72;
    private static final int EDGE_SCROLL_FULL_SPEED_INSET_DP = 8;
    private static final long MODERN_OVERLAY_EXIT_DURATION_MS = 220L;
    private static final long LINEAR_MENU_EXIT_DURATION_MS = 160L;
    private static final long PREVIEW_EXIT_DURATION_MS = 160L;
    private static final int CIRCLE_EDGE_TRIGGER_DP = 76;
    private static final int CIRCLE_EDGE_SOFT_DISTANCE_DP = 180;
    private static final long CIRCLE_EDGE_OPEN_DELAY_MS = 200L;
    private static final long DUPLICATE_EVENT_WINDOW_MS = 2;
    // Emergency recovery only: normal completion still follows Root UP/CANCEL. A long
    // stationary drag remains valid for 30 seconds without input before being cancelled.
    static final long STALE_INPUT_TIMEOUT_MS = 30_000L;
    private static final ThreadPoolExecutor TARGET_QUERY_EXECUTOR =
            new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "dragshare-target-query");
                thread.setDaemon(true);
                return thread;
            });
    private static boolean modernOverlayPrewarmStarted;


    private final Context context;
    private final WindowManager windowManager;
    private final BackgroundTouchBlocker backgroundTouchBlocker;
    private final OverlayWindowPolicy windowPolicy;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LatestRequest targetQueryRequests = new LatestRequest();
    private final LatestTaskSlot targetQueryTask = new LatestTaskSlot(TARGET_QUERY_EXECUTOR);
    private final DragShareToast dragShareToast;
    private Runnable modernOverlayPrewarmTask;
    private Runnable staleInputWatchdog;
    private ModernPreviewOverlayView modernOverlayPrewarmView;
    private ModernOverlayWindow modernOverlayPrewarmWindow;
    private ViewTreeObserver.OnDrawListener modernOverlayPrewarmDrawListener;
    private final Runnable edgeScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!active || !menuShown) {
                return;
            }
            if (isCircleStyle()) {
                if (circleMenuView == null || circleScrollDirection == 0) {
                    return;
                }
                circleMenuView.scrollWindow(circleScrollDirection);
                updateSelectedTarget(lastX, lastY);
                mainHandler.postDelayed(this, circleScrollIntervalMs());
                return;
            }
            if (edgeDirection == 0 || (menuScroll == null
                    && menuVerticalScroll == null
                    && modernMenuView == null)) {
                return;
            }
            long now = SystemClock.uptimeMillis();
            long elapsed = lastEdgeScrollUptime == 0
                    ? 16L
                    : Math.min(48L, Math.max(1L, now - lastEdgeScrollUptime));
            lastEdgeScrollUptime = now;
            float distanceWithRemainder = dpFloat(settings.scrollSpeedDpPerSecond)
                    * edgeScrollSpeedMultiplier() * elapsed / 1000f
                    + edgeScrollRemainderPx;
            int distance = (int) distanceWithRemainder;
            edgeScrollRemainderPx = distanceWithRemainder - distance;
            if (distance > 0) {
                if (modernMenuView != null) {
                    modernMenuView.scrollByPixels(edgeDirection * distance);
                } else if (menuScroll != null) {
                    menuScroll.scrollBy(edgeDirection * distance, 0);
                } else {
                    menuVerticalScroll.scrollBy(0, edgeDirection * distance);
                }
                updateSelectedTarget(lastX, lastY);
            }
            mainHandler.postDelayed(this, 16L);
        }
    };
    private final Runnable circleExpandRunnable = new Runnable() {
        @Override
        public void run() {
            if (!active || menuShown || circleMenuView == null
                    || circlePendingEdge == CircleMenuGeometry.EDGE_NONE) {
                return;
            }
            int edge = distanceFromMenuEdge(lastX, lastY) <= circleTriggerPx()
                    ? circleEdgeForPosition() : CircleMenuGeometry.EDGE_NONE;
            if (edge == circlePendingEdge) {
                circleEdge = edge;
                try {
                    showMenuOnMain();
                } catch (RuntimeException error) {
                    failOverlay("分享菜单显示失败", error);
                }
            }
        }
    };

    private volatile boolean active;
    private volatile boolean destroyed;
    private volatile long rootDownTime = -1L;
    private volatile long pressHandoffTime = -1L;
    private volatile long pressPipelineDownTime = -1L;
    // startPick*Task can create Taplus' full-screen float view before this controller reaches
    // the main thread. Keep its window suppressed during that small handoff.
    private volatile boolean pendingPortalHostFloatWindowSuppression;
    private volatile float lastObservedX = -1;
    private volatile float lastObservedY = -1;
    private volatile long lastObservedEventTime;
    private String gestureTraceSource;
    private long gestureTraceDownTime = -1L;
    private float gestureTraceStartX;
    private float gestureTraceStartY;
    private float gestureTraceLastX;
    private float gestureTraceLastY;
    private float gestureTraceMaxDistance;
    private int gestureTraceMoves;

    private float lastX;
    private float menuDragOriginX;
    private float lastY;
    private long lastHandledEventTime = Long.MIN_VALUE;
    private int lastHandledAction = -1;
    private int edgeDirection;
    private boolean inputSourceLogged;
    private boolean duplicateStartLogged;
    private boolean backgroundBlockAttempted;
    private boolean portalGlowStartedLogged;
    private boolean portalGlowExpandedLogged;
    private long lastEdgeScrollUptime;
    private float edgeScrollRemainderPx;
    private int circleEdge = CircleMenuGeometry.EDGE_NONE;
    private int circlePendingEdge = CircleMenuGeometry.EDGE_NONE;
    private int circleScrollDirection;

    private int screenWidth;
    private int screenHeight;
    private int topInset;
    private int bottomInset;
    private int menuTop;
    private int menuHeight;
    private int menuTriggerTop;
    private int menuLeft;
    private int menuWidth;
    private int simpleMenuPosition = DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM;
    private int configuredSimpleMenuPosition = DragShareSettings.DEFAULT_SIMPLE_MENU_POSITION;




    private View previewView;
    private ModernPreviewOverlayView modernPreviewView;
    private ModernOverlayWindow modernPreviewWindow;
    private WindowManager.LayoutParams previewParams;
    private int previewWidth;
    private int previewHeight;
    private boolean modernPreviewEnterStarted;
    // Keep an exiting preview addressable until its animation completes so a new drag can
    // tear it down immediately instead of briefly stacking two overlay windows.
    private int previewExitGeneration;
    private View exitingPreviewView;
    private ModernPreviewOverlayView exitingModernPreviewView;
    private ModernOverlayWindow exitingModernPreviewWindow;

    private PortalGlowView glowView;
    private WindowManager.LayoutParams glowParams;

    private View menuView;
    private ModernMenuOverlayView modernMenuView;
    private ModernOverlayWindow modernMenuWindow;
    private Runnable modernMenuDisposeRunnable;
    private int linearMenuFadeGeneration;
    private View fadingLinearMenuView;
    private WindowManager.LayoutParams menuParams;
    private HorizontalScrollView menuScroll;
    private ScrollView menuVerticalScroll;
    private LinearLayout menuRow;
    private final List<View> menuItems = new ArrayList<>();
    private CircleMenuOverlayView circleMenuView;
    private WindowManager.LayoutParams circleMenuParams;
    private List<ShareTarget> shareTargets = new ArrayList<>();
    private boolean targetsLoaded;
    private boolean targetQueryRequested;
    private View targetQueryPreviewView;
    private ViewTreeObserver.OnDrawListener targetQueryDrawListener;
    private Runnable targetQueryStartRunnable;
    private ShareTarget selectedTarget;
    private boolean menuShown;

    private volatile Session session;
    private DragShareSettings settings = DragShareSettings.defaults();
    private OverlayColors palette = OverlayColors.light();

    DragShareController(Context context) {
        this(context, OverlayWindowPolicy.portal());
    }

    DragShareController(Context context, OverlayWindowPolicy windowPolicy) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.backgroundTouchBlocker = new BackgroundTouchBlocker(context);
        this.windowPolicy = windowPolicy == null ? OverlayWindowPolicy.portal() : windowPolicy;
        this.dragShareToast = new DragShareToast(context, this.windowPolicy);
        prewarmModernOverlay();
    }

    boolean isActive() {
        return active;
    }

    boolean canContinuePress(long downTime) {
        long currentDownTime = rootDownTime;
        if (currentDownTime >= 0L && currentDownTime != downTime) return false;
        Session current = session;
        return !active || (downTime >= 0L && current != null
                && current.rootDownTime == downTime);
    }

    boolean isAwaitingRootRelease() {
        return active || (rootDownTime >= 0L && (pressHandoffTime == rootDownTime
                || pressPipelineDownTime == rootDownTime));
    }

    void notePressPipeline(long downTime) {
        if (downTime >= 0L && rootDownTime == downTime) pressPipelineDownTime = downTime;
    }

    void endPressPipeline(long downTime) {
        if (pressPipelineDownTime == downTime) pressPipelineDownTime = -1L;
    }

    /** Called on main only after the text/OCR provider has accepted the press. */
    void handoffToPress(long downTime) {
        if (downTime < 0L || rootDownTime != downTime) return;
        pressHandoffTime = downTime;
        endPressPipeline(downTime);
        Session current = session;
        if (active && current != null && current.rootDownTime == downTime) {
            current.cancelled = true;
            active = false;
            stopEdgeScroll();
            backgroundTouchBlocker.stop();
            disposeModernOverlayPrewarm();
            removeGestureViews();
        }
        pendingPortalHostFloatWindowSuppression = false;
        log("press accepted; native preview suppressed until root release");
    }

    void reservePortalHostFloatWindowSuppression() {
        pendingPortalHostFloatWindowSuppression = true;
    }

    boolean shouldSuppressPortalHostFloatWindow() {
        return isAwaitingRootRelease() || pendingPortalHostFloatWindowSuppression;
    }

    void show(
            CapturedContent content,
            float initialX,
            float initialY,
            DragShareSettings loadedSettings) {
        if (content == null) {
            return;
        }
        long downTime = rootDownTime;
        runOnMain(() -> showOnMain(content, initialX, initialY, loadedSettings, downTime));
    }

    /** Reclaim a still-active Root press after its OCR text handoff was shown. */
    void showContentForPressTakeover(
            CapturedContent content,
            float initialX,
            float initialY,
            DragShareSettings loadedSettings,
            long downTime) {
        if (content == null) return;
        runOnMain(() -> {
            if (destroyed || downTime < 0L || rootDownTime != downTime
                    || (pressHandoffTime >= 0L && pressHandoffTime != downTime)) {
                if (content.isImage() && content.bitmapOwnedByDragShare
                        && !content.bitmap.isRecycled()) content.bitmap.recycle();
                log("press content takeover rejected because Root press changed");
                return;
            }
            pressHandoffTime = -1L;
            pressPipelineDownTime = -1L;
            if (active && session != null && session.rootDownTime == downTime) {
                cancelGestureOnMain();
            }
            showOnMain(content, initialX, initialY, loadedSettings, downTime);
        });
    }

    void cancelActiveSession() {
        runOnMain(this::cancelGestureOnMain);
    }

    float latestPointerX() {
        return lastObservedX;
    }

    float latestPointerY() {
        return lastObservedY;
    }

    void acceptPointerEvent(
            int action,
            float x,
            float y,
            long eventTime,
            String source) {
        acceptPointerEvent(action, x, y, eventTime, source, null);
    }

    void acceptPointerEvent(
            int action,
            float x,
            float y,
            long eventTime,
            String source,
            Runnable beforeFinish) {
        if ("root".equals(source) && action == MotionEvent.ACTION_DOWN) {
            rootDownTime = eventTime;
            pressHandoffTime = -1L;
            pressPipelineDownTime = -1L;
        }
        long downTime = rootDownTime;
        if (action != MotionEvent.ACTION_MOVE) {
            DragShareLog.dSampled(TAG, "pointer:" + source + ":" + action, 250L,
                    () -> "pointer received source=" + source
                            + " action=" + MotionEvent.actionToString(action)
                            + " point=" + Math.round(x) + "," + Math.round(y)
                            + " active=" + active
                            + " time=" + eventTime);
        }
        lastObservedX = x;
        lastObservedY = y;
        lastObservedEventTime = eventTime;
        mainHandler.post(() -> {
            boolean traceWasActive = active;
            recordGestureTrace(action, x, y, eventTime, source);
            if (active && !inputSourceLogged) {
                inputSourceLogged = true;
                log("input source=" + source
                        + " action=" + MotionEvent.actionToString(action)
                        + " point=" + Math.round(x) + "," + Math.round(y));
            }
            try {
                handleMotionOnMain(action, x, y, eventTime, source, beforeFinish);
            } finally {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    logGestureTrace(source, eventTime, traceWasActive);
                }
                if ("root".equals(source)
                        && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
                    if (rootDownTime == downTime) rootDownTime = -1L;
                    if (pressHandoffTime == downTime) pressHandoffTime = -1L;
                    endPressPipeline(downTime);
                }
            }
        });
    }

    private void recordGestureTrace(int action, float x, float y, long eventTime, String source) {
        if (action == MotionEvent.ACTION_DOWN) {
            gestureTraceSource = source;
            gestureTraceDownTime = eventTime;
            gestureTraceStartX = x;
            gestureTraceStartY = y;
            gestureTraceLastX = x;
            gestureTraceLastY = y;
            gestureTraceMaxDistance = 0f;
            gestureTraceMoves = 0;
            return;
        }
        if (gestureTraceDownTime < 0L || !source.equals(gestureTraceSource)) return;
        gestureTraceLastX = x;
        gestureTraceLastY = y;
        if (action != MotionEvent.ACTION_MOVE) return;
        gestureTraceMoves++;
        gestureTraceMaxDistance = Math.max(
                gestureTraceMaxDistance,
                (float) Math.hypot(x - gestureTraceStartX, y - gestureTraceStartY));
    }

    private void logGestureTrace(String source, long eventTime, boolean wasActive) {
        if (gestureTraceDownTime < 0L || !source.equals(gestureTraceSource)) return;
        long durationMs = Math.max(0L, eventTime - gestureTraceDownTime);
        boolean worthKeeping = wasActive || menuShown
                || ("root".equals(source) && durationMs >= 650L);
        if (worthKeeping) {
            log("gesture summary source=" + source
                    + " duration_ms=" + durationMs
                    + " moves=" + gestureTraceMoves
                    + " max_distance_px=" + Math.round(gestureTraceMaxDistance)
                    + " start=" + Math.round(gestureTraceStartX) + "," + Math.round(gestureTraceStartY)
                    + " end=" + Math.round(gestureTraceLastX) + "," + Math.round(gestureTraceLastY)
                    + " overlay_active=" + wasActive
                    + " menu=" + menuShown);
        }
        gestureTraceSource = null;
        gestureTraceDownTime = -1L;
        gestureTraceMoves = 0;
        gestureTraceMaxDistance = 0f;
    }

    /** Called by a root-backed source after it has created an active drag session. */
    void onRootDragSessionStarted() {
        runOnMain(() -> {
            if (active) {
                startBackgroundBlockerIfEnabled();
            }
        });
    }

    void finishFromControlEvent() {
        mainHandler.post(() -> {
            if (!active) {
                return;
            }
            handlePointerOnMain(lastObservedX, lastObservedY);
            // The fallback path does not have a physical root ACTION_UP callback.
            finishGestureOnMain(true);
        });
    }

    void onHostTaskCancelled() {
        // Give the queued 257/control callback a chance to finish the drag first.
        mainHandler.postDelayed(() -> {
            if (active) {
                cancelGestureOnMain();
            } else {
                backgroundTouchBlocker.stop();
                if (!isPreviewExitPending()) {
                    removeGestureViews();
                }
            }
        }, 32L);
    }

    void destroy() {
        destroyed = true;
        rootDownTime = -1L;
        pressHandoffTime = -1L;
        pressPipelineDownTime = -1L;
        pendingPortalHostFloatWindowSuppression = false;
        runOnMain(() -> {
            active = false;
            if (session != null) {
                session.cancelled = true;
            }
            stopEdgeScroll();
            backgroundTouchBlocker.stop();
            removeGestureViews();
            dragShareToast.close();
        });
    }

    private void showOnMain(
            CapturedContent payload,
            float requestedInitialX,
            float requestedInitialY,
            DragShareSettings loadedSettings,
            long downTime) {
        if (downTime != rootDownTime || (downTime >= 0L && downTime == pressHandoffTime)) {
            pendingPortalHostFloatWindowSuppression = false;
            return;
        }
        if (destroyed) {
            pendingPortalHostFloatWindowSuppression = false;
            return;
        }
        if (active) {
            pendingPortalHostFloatWindowSuppression = false;
            if (!duplicateStartLogged) {
                duplicateStartLogged = true;
                log("duplicate Taplus start ignored during active drag");
            }
            return;
        }
        active = false;
        inputSourceLogged = false;
        duplicateStartLogged = false;
        backgroundBlockAttempted = false;
        portalGlowStartedLogged = false;
        portalGlowExpandedLogged = false;
        shareTargets = new ArrayList<>();
        targetsLoaded = false;
        targetQueryRequested = false;
        lastHandledEventTime = Long.MIN_VALUE;
        lastHandledAction = -1;
        settings = loadedSettings == null ? DragShareSettings.defaults() : loadedSettings;

        configuredSimpleMenuPosition = settings.simpleMenuPosition;

        palette = OverlayColors.from(settings.isDarkTheme(context));
        stopEdgeScroll();
        backgroundTouchBlocker.stop();
        removeGestureViews();

        if (!settings.isSharingEnabled(payload.isImage())) {
            pendingPortalHostFloatWindowSuppression = false;
            log("sharing disabled kind=" + payload.kind);
            return;
        }

        try {
            refreshDisplayGeometry();
            session = new Session(payload, downTime);
            createPreview(payload);
            createGlow();
            if (glowView != null && glowParams != null) {
                windowManager.addView(glowView, glowParams);
                glowView.start();
            }
            showPreviewOverlay();
            active = true;
            refreshStaleInputWatchdog();
            pendingPortalHostFloatWindowSuppression = false;

            traceAccessibility("overlay added kind=" + payload.kind);
            float initialX = Float.isFinite(requestedInitialX) && requestedInitialX >= 0f
                    ? requestedInitialX
                    : screenWidth / 2f;
            float initialY = Float.isFinite(requestedInitialY) && requestedInitialY >= 0f
                    ? requestedInitialY
                    : Math.max(topInset + dp(80), screenHeight * 0.32f);
            lastX = initialX;
            menuDragOriginX = initialX;
            lastY = initialY;
            simpleMenuPosition = GestureMath.initialMenuPosition(
                    configuredSimpleMenuPosition, initialX, screenWidth);
            updateMenuGeometry();
            updatePreviewPosition(initialX, initialY);
            updatePortalPullEffect(initialX, initialY);
            startPreviewEnterAnimation();

            loadTargetsAndCreateMenu(session, payload, initialX, initialY);
        } catch (RuntimeException error) {
            failOverlay("拖拽窗口创建失败", error);
            return;
        }

        if (payload.isImage()) {
            final Session stagedSession = session;
            ImageStagingClient.stage(context, payload.bitmap, new ImageStagingClient.Callback() {
                @Override
                public void onStaged(Uri uri) {
                    mainHandler.post(() -> {
                        if (destroyed || stagedSession.cancelled) {
                            return;
                        }
                        stagedSession.stagedUri = uri;
                        if (stagedSession.pendingTarget != null) {
                            launchPendingShare(stagedSession);
                        }
                    });
                }

                @Override
                public void onFailure(Throwable error) {
                    mainHandler.post(() -> {
                        if (!destroyed && !stagedSession.cancelled) {
                            log("image staging failed", error);
                            if (stagedSession.pendingTarget != null) {
                                showToast("图片准备失败");
                            }
                        }
                    });
                }
            });
        }
    }

    private List<ShareTarget> queryTargets(CapturedContent payload) {
        List<ShareTarget> queried = ShareTargetRepository.query(context, payload);
        return ShareTargetRepository.applySettings(context, queried, settings, payload.isImage());
    }

    private void prewarmModernOverlay() {
        if (destroyed || modernOverlayPrewarmStarted) {
            return;
        }
        modernOverlayPrewarmStarted = true;
        Runnable prewarm = () -> {
            modernOverlayPrewarmTask = null;
            if (destroyed) return;
            try {
                long started = SystemClock.elapsedRealtime();
                CapturedContent content = CapturedContent.text("预热", "warmup", null);
                ModernPreviewOverlayView warmupView = new ModernPreviewOverlayView(
                        context, content, DragShareSettings.defaults());
                modernOverlayPrewarmView = warmupView;
                WindowManager.LayoutParams warmupParams =
                        overlayParams(2, 2, "DragShare modern overlay warmup");
                warmupParams.x = -2;
                warmupParams.y = -2;
                ModernOverlayWindow warmupWindow = new ModernOverlayWindow(
                        context, windowManager, warmupView, warmupParams, 0);
                modernOverlayPrewarmWindow = warmupWindow;
                ViewTreeObserver.OnDrawListener drawListener =
                        new ViewTreeObserver.OnDrawListener() {
                            private boolean finishPosted;

                            @Override
                            public void onDraw() {
                                if (finishPosted) {
                                    return;
                                }
                                finishPosted = true;
                                warmupView.post(() -> {
                                    finishModernOverlayPrewarm(
                                            warmupView, warmupWindow, this, started);
                                });
                            }
                        };
                ViewTreeObserver observer = warmupView.getViewTreeObserver();
                observer.addOnDrawListener(drawListener);
                modernOverlayPrewarmDrawListener = drawListener;
                warmupWindow.showAnimated();
            } catch (RuntimeException error) {
                disposeModernOverlayPrewarm();
                log("modern overlay prewarm failed", error);
            }
        };
        modernOverlayPrewarmTask = prewarm;
        mainHandler.post(prewarm);
    }

    private void finishModernOverlayPrewarm(
            ModernPreviewOverlayView warmupView,
            ModernOverlayWindow warmupWindow,
            ViewTreeObserver.OnDrawListener drawListener,
            long started) {
        ViewTreeObserver observer = warmupView.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnDrawListener(drawListener);
        if (modernOverlayPrewarmView != warmupView
                || modernOverlayPrewarmWindow != warmupWindow) return;
        modernOverlayPrewarmDrawListener = null;
        modernOverlayPrewarmView = null;
        modernOverlayPrewarmWindow = null;
        try {
            warmupWindow.dispose();
            log("modern overlay prewarm completed elapsed_ms="
                    + (SystemClock.elapsedRealtime() - started));
        } catch (RuntimeException error) {
            log("modern overlay prewarm cleanup failed", error);
        }
    }

    private void disposeModernOverlayPrewarm() {
        if (modernOverlayPrewarmTask != null) {
            mainHandler.removeCallbacks(modernOverlayPrewarmTask);
            modernOverlayPrewarmTask = null;
        }
        ModernPreviewOverlayView view = modernOverlayPrewarmView;
        ModernOverlayWindow window = modernOverlayPrewarmWindow;
        ViewTreeObserver.OnDrawListener listener = modernOverlayPrewarmDrawListener;
        modernOverlayPrewarmView = null;
        modernOverlayPrewarmWindow = null;
        modernOverlayPrewarmDrawListener = null;
        if (view != null && listener != null) {
            ViewTreeObserver observer = view.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnDrawListener(listener);
        }
        if (window != null) {
            try {
                window.dispose();
            } catch (RuntimeException error) {
                log("modern overlay prewarm cleanup failed", error);
            }
        } else if (view != null) {
            try {
                view.disposeOverlay();
            } catch (RuntimeException error) {
                log("modern overlay prewarm view cleanup failed", error);
            }
        }
    }

    private void loadTargetsAndCreateMenu(
            Session capturedSession,
            CapturedContent payload,
            float initialX,
            float initialY) {
        View targetPreview = previewView;
        ViewTreeObserver.OnDrawListener drawListener = new ViewTreeObserver.OnDrawListener() {
                    @Override
                    public void onDraw() {
                        if (targetQueryRequested || destroyed || !active
                                || session != capturedSession) {
                            return;
                        }
                        targetQueryRequested = true;
                        Runnable startQuery = () -> {
                            ViewTreeObserver observer = targetPreview.getViewTreeObserver();
                            if (observer.isAlive()) {
                                observer.removeOnDrawListener(this);
                            }
                            if (targetQueryPreviewView == targetPreview) {
                                targetQueryPreviewView = null;
                                targetQueryDrawListener = null;
                                targetQueryStartRunnable = null;
                            }
                            if (destroyed || !active || session != capturedSession) return;
                            queryTargetsAfterPreview(
                                    capturedSession, payload, initialX, initialY);
                        };
                        targetQueryPreviewView = targetPreview;
                        targetQueryDrawListener = this;
                        targetQueryStartRunnable = startQuery;
                        targetPreview.post(startQuery);
                    }
                };
        targetQueryPreviewView = targetPreview;
        targetQueryDrawListener = drawListener;
        targetPreview.getViewTreeObserver().addOnDrawListener(drawListener);
    }

    private void queryTargetsAfterPreview(
            Session capturedSession,
            CapturedContent payload,
            float initialX,
            float initialY) {
        cancelTargetQuery();
        long request = targetQueryRequests.begin();
        targetQueryTask.submit(() -> {
            long started = SystemClock.elapsedRealtime();
            List<ShareTarget> queried;
            try {
                queried = queryTargets(payload);
            } catch (RuntimeException error) {
                mainHandler.post(() -> {
                    if (targetQueryRequests.accepts(request)
                            && !destroyed && active && session == capturedSession) {
                        failOverlay("分享目标读取失败", error);
                    }
                });
                return;
            }
            long elapsedMs = SystemClock.elapsedRealtime() - started;
            mainHandler.post(() -> {
                if (!targetQueryRequests.accepts(request)
                        || destroyed || !active || session != capturedSession) {
                    return;
                }
                shareTargets = queried;
                targetsLoaded = true;
                createMenu();
                if (isCircleStyle() && circleMenuView != null && circleMenuParams != null) {
                    circleMenuView.setVisibility(View.VISIBLE);
                    windowManager.addView(circleMenuView, circleMenuParams);
                    updateCirclePointer(lastX, lastY);
                }
                if (!isCircleStyle() && shouldShowSimpleMenu(lastX, lastY)) {
                    showMenuOnMain();
                }
                log("preview shown kind=" + payload.kind
                        + " source=" + payload.sourcePackage
                        + " targets=" + shareTargets.size()
                        + " targetQueryMs=" + elapsedMs
                        + " style=" + settings.uiStyle
                        + " blockBackground=" + settings.blockBackgroundScroll
                        + " at=" + Math.round(initialX) + "," + Math.round(initialY));
            });
        });
    }

    private void cancelTargetQuery() {
        targetQueryRequests.invalidate();
        targetQueryTask.cancel();
    }

    private void cancelPreviewTargetLoad() {
        View targetPreview = targetQueryPreviewView;
        Runnable startQuery = targetQueryStartRunnable;
        ViewTreeObserver.OnDrawListener drawListener = targetQueryDrawListener;
        targetQueryPreviewView = null;
        targetQueryStartRunnable = null;
        targetQueryDrawListener = null;
        if (targetPreview == null) return;
        if (startQuery != null) targetPreview.removeCallbacks(startQuery);
        if (drawListener != null) {
            ViewTreeObserver observer = targetPreview.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnDrawListener(drawListener);
        }
    }

    private void createPreview(CapturedContent payload) {
        if (isModernStyle()) {
            int side = ModernPreviewSizer.squareSidePx(
                    payload,
                    screenWidth,
                    context.getResources().getDisplayMetrics().density);
            previewWidth = side;
            previewHeight = side;
            modernPreviewView = new ModernPreviewOverlayView(context, payload, settings);
            previewView = modernPreviewView;
            previewParams = overlayParams(previewWidth, previewHeight, "DragShare modern preview");
            modernPreviewWindow = new ModernOverlayWindow(
                    context,
                    windowManager,
                    modernPreviewView,
                    previewParams,
                    dp(settings.modernBlurRadiusDp));
            return;
        }
        boolean portalStyle = isPortalStyle();
        boolean circleStyle = isCircleStyle();
        boolean compactStyle = portalStyle || circleStyle;
        FrameLayout standardPreviewView = new FrameLayout(context);
        previewView = standardPreviewView;
        previewView.setClipToOutline(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            previewView.setForceDarkAllowed(false);
        }
        previewView.setElevation(dp(compactStyle ? 6 : 8));
        previewView.setBackground(roundDrawable(
                palette.previewBackground,
                compactStyle ? 12 : 8));

        if (compactStyle) {
            int sizeDp = !payload.isImage()
                    ? PORTAL_PREVIEW_TEXT_SIZE_DP
                    : PORTAL_PREVIEW_IMAGE_SIZE_DP;
            previewWidth = dp(sizeDp);
            previewHeight = dp(sizeDp);
            if (!payload.isImage()) {
                TextView text = new TextView(context);
                CharSequence previewText = payload.text;
                if (previewText != null && previewText.length() > 40) {
                    previewText = previewText.subSequence(0, 40);
                }
                text.setText(previewText);
                text.setTextColor(palette.primaryText);
                text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                text.setGravity(Gravity.CENTER);
                text.setPadding(dp(7), dp(7), dp(7), dp(7));
                text.setMaxLines(4);
                text.setEllipsize(android.text.TextUtils.TruncateAt.END);
                standardPreviewView.addView(text, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            } else {
                ImageView image = new ImageView(context);
                image.setImageBitmap(payload.bitmap);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setPadding(dp(3), dp(3), dp(3), dp(3));
                standardPreviewView.addView(image, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            }
        } else if (!payload.isImage()) {
            previewWidth = dp(PREVIEW_TEXT_WIDTH_DP);
            previewHeight = dp(PREVIEW_TEXT_HEIGHT_DP);
            TextView text = new TextView(context);
            text.setText(payload.text);
            text.setTextColor(palette.primaryText);
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            text.setGravity(Gravity.CENTER_VERTICAL);
            text.setPadding(dp(12), dp(10), dp(12), dp(10));
            text.setMaxLines(4);
            text.setEllipsize(android.text.TextUtils.TruncateAt.END);
            standardPreviewView.addView(text, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            previewWidth = dp(PREVIEW_IMAGE_SIZE_DP);
            previewHeight = dp(PREVIEW_IMAGE_SIZE_DP);
            ImageView image = new ImageView(context);
            image.setImageBitmap(payload.bitmap);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setPadding(dp(6), dp(6), dp(6), dp(6));
            standardPreviewView.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        previewParams = overlayParams(previewWidth, previewHeight, "DragShare preview");
    }

    private void showPreviewOverlay() {
        if (isModernStyle()) {
            modernPreviewWindow.show();
        } else {
            windowManager.addView(previewView, previewParams);
        }
    }

    private void createGlow() {
        if (!isPortalStyle()) {
            glowView = null;
            glowParams = null;
            return;
        }
        glowView = new PortalGlowView(
                context,
                settings.isDarkTheme(context),
                bottomInset);
        glowParams = overlayParams(screenWidth, screenHeight, "DragShare portal glow");
    }

    private void startPreviewEnterAnimation() {
        if (isModernStyle()) {
            enableModernLocalBlurAndReveal();
            return;
        }
        if (previewView == null || !isPortalStyle()) {
            return;
        }
        previewView.animate().cancel();
        previewView.setAlpha(0f);
        previewView.setScaleX(0.88f);
        previewView.setScaleY(0.88f);
        previewView.animate()
                .alpha(0.94f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200L)
                .start();
    }

    private void enableModernLocalBlurAndReveal() {
        ModernPreviewOverlayView preview = modernPreviewView;
        if (preview == null) {
            return;
        }
        modernPreviewEnterStarted = false;
        int blurRadiusPx = dp(settings.modernBlurRadiusDp);
        boolean enabled = modernPreviewWindow != null
                && modernPreviewWindow.isNativeBackdropBlurEnabled();
        log("modern Android background blur " + (enabled ? "enabled" : "unavailable")
                + " radiusPx=" + blurRadiusPx);
        revealModernPreview(preview);
    }

    private void revealModernPreview(ModernPreviewOverlayView preview) {
        if (preview == null || modernPreviewEnterStarted) {
            return;
        }
        modernPreviewEnterStarted = true;
        if (modernPreviewWindow != null) {
            modernPreviewWindow.showAnimated();
        } else {
            preview.showAnimated();
        }
    }
    private void createMenu() {
        updateMenuGeometry();
        if (isModernStyle()) {
            Map<ShareTarget, Drawable> icons = new LinkedHashMap<>();
            for (ShareTarget target : shareTargets) {
                icons.put(target, iconForTarget(target));
            }
            modernMenuView = new ModernMenuOverlayView(
                    context,
                    new ArrayList<>(shareTargets),
                    icons,
                    settings,
                    isVerticalSimpleMenu());
            menuView = modernMenuView;
            menuParams = overlayParams(menuWidth, menuHeight, "DragShare modern targets");
            menuParams.x = menuLeft;
            menuParams.y = menuTop;
            modernMenuWindow = new ModernOverlayWindow(
                    context,
                    windowManager,
                    modernMenuView,
                    menuParams,
                    dp(settings.modernBlurRadiusDp));
            return;
        }
        if (isCircleStyle()) {
            circleMenuView = new CircleMenuOverlayView(
                    context,
                    screenWidth,
                    screenHeight,
                    topInset,
                    bottomInset,
                    settings.isDarkTheme(context),
                    palette.accent,
                    palette.primaryText,
                    palette.selectedItemBackground,
                    palette.selectedItemBorder,
                    settings.iconOpacityPercent);
            circleMenuView.setTargets(shareTargets);
            circleMenuParams = overlayParams(
                    screenWidth,
                    screenHeight,
                    "DragShare circle menu");
            return;
        }
        boolean portalStyle = isPortalStyle();
        boolean vertical = isVerticalSimpleMenu();
        FrameLayout linearMenuView = new FrameLayout(context);
        menuView = linearMenuView;
        linearMenuView.setClipChildren(false);
        linearMenuView.setClipToPadding(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            linearMenuView.setForceDarkAllowed(false);
        }
        int horizontalPadding = dp(4);
        int verticalPadding = dp(4);
        int bottomPadding = dp(portalStyle ? 2 : 4);
        addLinearMenuBackground();
        linearMenuView.setElevation(dp(portalStyle ? 0 : 10));

        FrameLayout menuContent = new FrameLayout(context);
        menuContent.setClipChildren(false);
        menuContent.setClipToPadding(false);
        menuContent.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                bottomPadding);
        linearMenuView.addView(menuContent, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        menuRow = new LinearLayout(context);
        menuRow.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        menuRow.setGravity(vertical ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER_VERTICAL);
        menuRow.setClipChildren(false);
        menuRow.setClipToPadding(false);
        if (vertical) {
            menuVerticalScroll = new ScrollView(context);
            menuVerticalScroll.setVerticalScrollBarEnabled(false);
            menuVerticalScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            menuVerticalScroll.setClipToPadding(false);
            menuVerticalScroll.setClipChildren(false);
            menuVerticalScroll.addView(menuRow, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            menuContent.addView(menuVerticalScroll, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            menuScroll = new HorizontalScrollView(context);
            menuScroll.setHorizontalScrollBarEnabled(false);
            menuScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            menuScroll.setClipToPadding(false);
            menuScroll.setClipChildren(false);
            menuScroll.setFillViewport(false);
            menuScroll.addView(menuRow, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            menuContent.addView(menuScroll, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        menuItems.clear();
        if (shareTargets.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("没有可用的分享应用");
            empty.setTextColor(palette.secondaryText);
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            empty.setGravity(Gravity.CENTER);
            if (vertical) {
                menuRow.addView(empty, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            } else if (portalStyle) {
                menuRow.addView(empty, new LinearLayout.LayoutParams(
                        screenWidth,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            } else {
                menuContent.addView(empty, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
            }
        } else {
            int itemWidth = dp(LinearMenuGeometry.ITEM_WIDTH_DP);
            int itemHeight = dp(LinearMenuGeometry.ITEM_HEIGHT_DP);
            int iconSize = dp(40);
            for (ShareTarget target : shareTargets) {
                LinearLayout item = new LinearLayout(context);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER_HORIZONTAL);
                item.setPadding(dp(3), dp(3), dp(3), dp(3));
                item.setTag(target);
                item.setBackground(itemBackground(target, false));

                ImageView icon = new ImageView(context);
                icon.setImageDrawable(iconForTarget(target));
                icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
                icon.setAlpha(settings.iconOpacityPercent / 100f);
                item.addView(icon, new LinearLayout.LayoutParams(iconSize, iconSize));

                TextView label = new TextView(context);
                label.setText(target.label);
                label.setTextColor(palette.primaryText);
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                label.setGravity(Gravity.CENTER);
                label.setMaxLines(1);
                label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                label.setAlpha(settings.iconOpacityPercent / 100f);
                if (portalStyle) {
                    label.setShadowLayer(dpFloat(2), 0f, dpFloat(1),
                            settings.isDarkTheme(context)
                                    ? 0xCC000000
                                    : 0x66000000);
                }
                item.addView(label, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));

                if (vertical) {
                    int width = Math.max(1, menuWidth - dp(8));
                    menuRow.addView(item, new LinearLayout.LayoutParams(width, itemHeight));
                } else {
                    menuRow.addView(item, new LinearLayout.LayoutParams(itemWidth, itemHeight));
                }
                menuItems.add(item);
            }
        }

        menuParams = overlayParams(menuWidth, menuHeight, "DragShare targets");
        menuParams.x = menuLeft;
        menuParams.y = menuTop;
    }

    private void addLinearMenuBackground() {
        if (!(menuView instanceof FrameLayout)) {
            return;
        }
        FrameLayout linearMenuView = (FrameLayout) menuView;
        int cornerRadiusDp = isPortalStyle() ? 16 : settings.simpleMenuCornerRadiusDp;
        View background = new View(context);
        background.setBackground(roundDrawable(
                applyAbsoluteOpacity(palette.menuBackground, 100),
                cornerRadiusDp));
        background.setAlpha(isPortalStyle() ? 0.94f : simpleMenuBackgroundOpacityFraction(
                settings.simpleMenuOpacityPercent));
        linearMenuView.addView(background, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        int cornerRadiusPx = dp(cornerRadiusDp);
        linearMenuView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
            }
        });
        linearMenuView.setClipToOutline(true);
    }

    private void handleMotionOnMain(
            int action,
            float x,
            float y,
            long eventTime,
            String source,
            Runnable beforeFinish) {
        if (!active) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                finishGestureOnMain(false, beforeFinish);
            }
            return;
        }
        // Only pilfer once the authoritative root stream has produced an
        // event. The MIUI fallback cannot safely consume its synthetic cancel.
        if ("root".equals(source)
                && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE)) {
            startBackgroundBlockerIfEnabled();
        }
        if (eventTime == lastHandledEventTime && action == lastHandledAction
                && eventTime != 0 && eventTime > lastObservedEventTime - DUPLICATE_EVENT_WINDOW_MS) {
            return;
        }
        lastHandledEventTime = eventTime;
        lastHandledAction = action;
        lastX = x;
        lastY = y;
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            refreshStaleInputWatchdog();
        }

        if (action == MotionEvent.ACTION_CANCEL) {
            log("gesture cancelled source=" + source);
            finishGestureOnMain(false, beforeFinish);
            return;
        }
        if (action == MotionEvent.ACTION_UP) {
            try {
                handlePointerOnMain(x, y);
            } finally {
                log("gesture finished source=" + source
                        + " point=" + Math.round(x) + "," + Math.round(y)
                        + " menu=" + menuShown);
                finishGestureOnMain(true, beforeFinish);
            }
        } else handlePointerOnMain(x, y);
    }

    private void handlePointerOnMain(float x, float y) {
        if (!active || !Float.isFinite(x) || !Float.isFinite(y)) {
            return;
        }
        try {
            lastX = x;
            lastY = y;
            updatePreviewPosition(x, y);
            updatePortalPullEffect(x, y);
            updateGestureMenuPosition(x, y);
            if (GestureMath.shouldDeferToBottomInputDropZone(
                    x, y, screenWidth, screenHeight, bottomInset, dp(110),
                    Math.max(dp(settings.edgeTriggerDp), dp(MENU_TRIGGER_DP)), simpleMenuPosition)) {
                if (menuShown) {
                    if (isCircleStyle()) hideCircleMenu();
                    else hideLinearMenu();
                }
                return;
            }
            if (isCircleStyle()) {
                updateCirclePointer(x, y);
                if (menuShown) {
                    updateSelectedTarget(x, y);
                }
                return;
            }
            boolean menuOpenedNow = false;
            if (!menuShown && shouldShowSimpleMenu(x, y)) {
                showMenuOnMain();
                menuOpenedNow = menuShown;
            }
            if (menuShown) {
                if (settings.closeMenuWhenPointerLeaves
                        && !menuOpenedNow
                        && !isPointerWithinSimpleMenuZone(x, y)) {
                    hideLinearMenu();
                    return;
                }
                updateSelectedTarget(x, y);
                boolean pointerInScrollableMenu = isPointerInsideSimpleMenu(x, y);
                int nextDirection = 0;
                if (pointerInScrollableMenu) {
                    int edgeAxisSize = edgeScrollAxisSize();
                    int edgeWidth = edgeScrollWidthPx();
                    nextDirection = GestureMath.edgeScrollDirection(
                            isVerticalSimpleMenu() ? y - menuTop : x - menuLeft,
                            edgeAxisSize,
                            edgeWidth);
                }
                if (nextDirection != edgeDirection) {
                    edgeDirection = nextDirection;
                    edgeScrollRemainderPx = 0f;
                    log("edge scroll direction=" + nextDirection);
                    if (nextDirection == 0) {
                        stopEdgeScroll();
                    } else {
                        lastEdgeScrollUptime = 0L;
                        mainHandler.removeCallbacks(edgeScrollRunnable);
                        mainHandler.post(edgeScrollRunnable);
                    }
                }
            }
        } catch (RuntimeException error) {
            failOverlay("分享菜单显示失败", error);
        }
    }

    private void updateCirclePointer(float x, float y) {
        if (circleMenuView == null) {
            return;
        }
        int trigger = circleTriggerPx();
        int wantedEdge = circleEdgeForPosition();
        float distance = distanceFromMenuEdge(x, y);
        int nextEdge = distance <= trigger ? wantedEdge : CircleMenuGeometry.EDGE_NONE;
        float progress = GestureMath.clamp01(1f - distance
                / Math.max(trigger, dp(CIRCLE_EDGE_SOFT_DISTANCE_DP)));
        if (menuShown) {
            circleMenuView.updatePointer(x, y);
            if (settings.closeMenuWhenPointerLeaves
                    && !circleMenuView.containsExpandedRegion(x, y)) {
                hideCircleMenu();
                circleMenuView.setEdgeProgress(nextEdge, progress, x, y);
                updateCirclePendingEdge(nextEdge);
                return;
            }
        } else {
            circleMenuView.setEdgeProgress(nextEdge, progress, x, y);
            updateCirclePendingEdge(nextEdge);
            return;
        }

        int nextScrollDirection = circleMenuView.scrollDirectionForPointer(x, y);
        if (nextScrollDirection != circleScrollDirection) {
            circleScrollDirection = nextScrollDirection;
            mainHandler.removeCallbacks(edgeScrollRunnable);
            if (nextScrollDirection != 0) {
                mainHandler.post(edgeScrollRunnable);
            }
        }
        // The reference side menu keeps the edge and vertical anchor captured
        // at expansion time. Pointer movement only changes hover/drop state.
    }

    private void updateCirclePendingEdge(int nextEdge) {
        if (nextEdge == circlePendingEdge) {
            return;
        }
        mainHandler.removeCallbacks(circleExpandRunnable);
        circlePendingEdge = nextEdge;
        if (nextEdge != CircleMenuGeometry.EDGE_NONE) {
            mainHandler.postDelayed(circleExpandRunnable, CIRCLE_EDGE_OPEN_DELAY_MS);
        }
    }

    private int circleTriggerPx() {
        int maxTrigger = Math.max(1, Math.min(screenWidth, screenHeight) / 2);
        return Math.min(
                maxTrigger,
                Math.max(dp(CIRCLE_EDGE_TRIGGER_DP), dp(settings.edgeTriggerDp)));
    }

    private int circleEdgeForPosition() {
        switch (simpleMenuPosition) {
            case DragShareSettings.SIMPLE_MENU_POSITION_TOP: return CircleMenuGeometry.EDGE_TOP;
            case DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM: return CircleMenuGeometry.EDGE_BOTTOM;
            case DragShareSettings.SIMPLE_MENU_POSITION_LEFT: return CircleMenuGeometry.EDGE_LEFT;
            default: return CircleMenuGeometry.EDGE_RIGHT;
        }
    }

    private float distanceFromMenuEdge(float x, float y) {
        switch (simpleMenuPosition) {
            case DragShareSettings.SIMPLE_MENU_POSITION_TOP: return Math.max(0f, y - topInset);
            case DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM: return Math.max(0f, screenHeight - bottomInset - y);
            case DragShareSettings.SIMPLE_MENU_POSITION_LEFT: return Math.max(0f, x);
            default: return Math.max(0f, screenWidth - x);
        }
    }

    private float glowPointerFraction(float x, float y) {
        if (menuShown) {
            return 0.5f;
        }
        return isVerticalSimpleMenu() ? y / Math.max(1f, screenHeight) : x / Math.max(1f, screenWidth);
    }



    private boolean isVerticalSimpleMenu() {
        return (simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_LEFT
                || simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_RIGHT);
    }

    private int edgeScrollAxisSize() {
        return isVerticalSimpleMenu() ? menuHeight : menuWidth;
    }

    private int edgeScrollWidthPx() {
        int edgeAxisSize = edgeScrollAxisSize();
        int maxEdgeWidth = Math.max(1, (edgeAxisSize - 1) / 2);
        return Math.min(dp(settings.edgeTriggerDp), maxEdgeWidth);
    }

    private float edgeScrollSpeedMultiplier() {
        boolean vertical = isVerticalSimpleMenu();
        return GestureMath.edgeScrollSpeedMultiplier(
                vertical ? lastY - menuTop : lastX - menuLeft,
                edgeScrollAxisSize(),
                edgeScrollWidthPx(),
                dp(EDGE_SCROLL_FULL_SPEED_INSET_DP));
    }

    private boolean shouldShowSimpleMenu(float x, float y) {
        int trigger = Math.max(dp(settings.edgeTriggerDp), dp(MENU_TRIGGER_DP));
        return distanceFromMenuEdge(x, y) <= trigger;
    }

    private boolean isPointerInsideSimpleMenu(float x, float y) {
        return menuShown
                && x >= menuLeft
                && x < menuLeft + menuWidth
                && y >= menuTop
                && y < menuTop + menuHeight;
    }

    private boolean isPointerWithinSimpleMenuZone(float x, float y) {
        return isPointerInsideSimpleMenu(x, y) || shouldShowSimpleMenu(x, y);
    }

    private void hideLinearMenu() {
        if (!menuShown || isCircleStyle()) {
            return;
        }
        stopEdgeScroll();
        menuShown = false;
        if (isModernStyle() && modernMenuView != null) {
            ModernMenuOverlayView menuToDispose = modernMenuView;
            scheduleModernMenuDispose(menuToDispose, modernMenuWindow);
        } else if (menuView != null) {
            fadeOutLinearMenu(menuView);
        }
        selectedTarget = null;
        if (modernMenuView != null) {
            modernMenuView.setSelectedTarget(null);
        }
        if (glowView != null && isPortalStyle()) {
            glowView.collapseMenu();
            updatePortalPullEffect(lastX, lastY);
        }
        updatePreviewPosition(lastX, lastY);
    }

    private void hideCircleMenu() {
        if (!menuShown || !isCircleStyle() || circleMenuView == null) {
            return;
        }
        stopEdgeScroll();
        mainHandler.removeCallbacks(circleExpandRunnable);
        circleMenuView.collapse();
        menuShown = false;
        selectedTarget = null;
        circleEdge = CircleMenuGeometry.EDGE_NONE;
        circlePendingEdge = CircleMenuGeometry.EDGE_NONE;
        updatePreviewPosition(lastX, lastY);
    }



    private void updateGestureMenuPosition(float x, float y) {
        final int edge;
        if (configuredSimpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_NEAR_HAND) {
            edge = GestureMath.nearHandMenuPosition(menuDragOriginX, x, dp(12), simpleMenuPosition);
        } else if (GestureMath.followsDragEdge(configuredSimpleMenuPosition)) {
            edge = GestureMath.nearestMenuEdge(x, y, screenWidth, topInset,
                    screenHeight - bottomInset, Math.max(dp(settings.edgeTriggerDp), dp(MENU_TRIGGER_DP)),
                    simpleMenuPosition);
        } else {
            return;
        }
        if (edge < 0 || edge == simpleMenuPosition) return;
        if (isCircleStyle()) hideCircleMenu();
        repositionMenu(edge);
    }

    private void repositionMenu(int nextPosition) {
        if (nextPosition == simpleMenuPosition) {
            return;
        }
        if (!targetsLoaded) {
            simpleMenuPosition = nextPosition;
            updateMenuGeometry();
            return;
        }
        if (isCircleStyle()) {
            simpleMenuPosition = nextPosition;
            updateCirclePointer(lastX, lastY);
            return;
        }
        boolean wasShown = menuShown;
        View previousMenuView = menuView;
        if (wasShown) {
            hideLinearMenu();
        }
        if (isModernStyle()) {
            // The old exit callback belongs to these references. Replacing them would
            // cancel that callback and strand its window and blur listener.
            cancelModernMenuDispose();
            removeOverlayView(previousMenuView, modernMenuView, modernMenuWindow);
        } else {
            // Leaving an edge may already have started its exit animation. Release
            // that window before replacing the reference, including while hidden.
            cancelLinearMenuFadeOut();
            removeLinearMenuImmediately(previousMenuView);
        }
        menuView = null;
        modernMenuView = null;
        modernMenuWindow = null;
        menuParams = null;
        menuScroll = null;
        menuVerticalScroll = null;
        menuRow = null;
        simpleMenuPosition = nextPosition;
        refreshDisplayGeometry();
        createMenu();
        if (wasShown) {
            showMenuOnMain();
        }
        updatePreviewPosition(lastX, lastY);
    }







    private long circleScrollIntervalMs() {
        float speed = Math.max(1f, settings.scrollSpeedDpPerSecond);
        return Math.max(120L, Math.min(420L, Math.round(300f * 560f / speed)));
    }

    private void showMenuOnMain() {
        if (menuShown) {
            return;
        }
        if (!targetsLoaded) {
            return;
        }
        if (isCircleStyle()) {
            if (circleMenuView == null || circlePendingEdge == CircleMenuGeometry.EDGE_NONE) {
                return;
            }
            menuShown = true;

            circleEdge = circlePendingEdge;
            circleScrollDirection = 0;
            circleMenuView.expand(
                    circleEdge,
                    screenWidth / 2f,
                    (topInset + screenHeight - bottomInset) / 2f);
            updatePreviewPosition(lastX, lastY);
            startCirclePreviewAnimation();
            log("circle menu shown edge=" + circleEdge
                    + " pointer=" + Math.round(lastX) + "," + Math.round(lastY));
            mainHandler.post(() -> updateSelectedTarget(lastX, lastY));
            return;
        }
        if (isModernStyle()) {
            cancelModernMenuDispose();
            // A menu that completed its exit animation is removed to guarantee that the
            // blurred overlay cannot remain on screen. Recreate it when the same drag re-enters
            // the trigger zone, preserving the public "leave and return" behavior.
            if (menuView == null || modernMenuView == null) {
                createMenu();
            }
        }
        if (menuView == null) {
            return;
        }

        if (!isModernStyle() && menuView.isAttachedToWindow()) {
            cancelLinearMenuFadeOut(menuView);
            menuView.animate().cancel();
            menuShown = true;
            menuView.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(LINEAR_MENU_EXIT_DURATION_MS)
                    .setInterpolator(new DecelerateInterpolator(1.5f))
                    .start();
            updatePreviewPosition(lastX, lastY);
            if (isPortalStyle() && glowView != null) {
                glowView.setPullProgress(1f, glowPointerFraction(lastX, lastY));
                glowView.expandMenu();
            }
            log("linear menu restored pointerY=" + Math.round(lastY)
                    + " menuTop=" + menuTop);
            menuView.post(() -> updateSelectedTarget(lastX, lastY));
            return;
        }
        if (isModernStyle() && modernMenuView != null
                && modernMenuWindow.isShowing()) {
            menuShown = true;
            modernMenuWindow.showAnimated();
            updatePreviewPosition(lastX, lastY);
            log("modern menu shown pointerY=" + Math.round(lastY) + " menuTop=" + menuTop);
            menuView.post(() -> updateSelectedTarget(lastX, lastY));
            return;
        }
        if (isModernStyle()) {
            modernMenuWindow.showAnimated();
        } else {
            windowManager.addView(menuView, menuParams);
        }
        menuShown = true;
        updatePreviewPosition(lastX, lastY);
        log("menu shown style=" + settings.uiStyle + " anchoredAtCenter=true"
                + " effectivePosition=" + simpleMenuPosition + " bounds=" + menuLeft + "," + menuTop
                + "," + menuWidth + "," + menuHeight + " pointer=" + Math.round(lastX) + "," + Math.round(lastY));
        if (isPortalStyle()) {
            if (glowView != null) {
                glowView.setPullProgress(1f, glowPointerFraction(lastX, lastY));
                glowView.expandMenu();
            }
            if (!portalGlowExpandedLogged) {
                portalGlowExpandedLogged = true;
                log("portal glow expanded with share tray");
            }
            startPortalMenuEnterAnimation();
        } else {
            menuView.setAlpha(0f);
            menuView.setTranslationX(isVerticalSimpleMenu()
                    ? (simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_LEFT
                    ? -dp(12) : dp(12))
                    : 0f);
            menuView.setTranslationY(!isVerticalSimpleMenu()
                    ? (simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_TOP
                    ? -dp(12) : dp(12))
                    : 0f);
            menuView.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(120L)
                    .start();
        }
        menuView.post(() -> updateSelectedTarget(lastX, lastY));

    }



    private void startPortalMenuEnterAnimation() {
        menuView.setAlpha(1f);
        menuView.setTranslationX(0f);
        menuView.setTranslationY(0f);
        OvershootInterpolator springLike = new OvershootInterpolator(0.78f);
        for (int index = 0; index < menuItems.size(); index++) {
            View item = menuItems.get(index);
            item.animate().cancel();
            item.setAlpha(0f);
            boolean vertical = isVerticalSimpleMenu();
            float offset = dp(48) * (simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_LEFT
                    || simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_TOP ? -1 : 1);
            item.setTranslationX(vertical ? offset : 0f);
            item.setTranslationY(vertical ? 0f : offset);
            item.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .translationY(0f)
                    .setStartDelay(Math.min(180L, index * 22L))
                    .setDuration(430L)
                    .setInterpolator(springLike)
                    .start();
        }
    }

    private void startCirclePreviewAnimation() {
        if (previewView == null) {
            return;
        }
        previewView.animate().cancel();
        previewView.animate()
                .alpha(0.92f)
                .scaleX(0.88f)
                .scaleY(0.88f)
                .setDuration(500L)
                .start();
    }

    private void updateSelectedTarget(float x, float y) {
        if (isCircleStyle()) {
            ShareTarget hit = circleMenuView == null ? null : circleMenuView.hitTest(x, y);
            selectedTarget = hit;
            if (circleMenuView != null) {
                circleMenuView.setSelectedTarget(hit);
            }
            return;
        }
        if (isModernStyle()) {
            ShareTarget hit = menuShown && isPointerInsideSimpleMenu(x, y)
                    && modernMenuView != null
                    ? modernMenuView.hitTest(x, y)
                    : null;
            selectedTarget = hit;
            if (modernMenuView != null) {
                modernMenuView.setSelectedTarget(hit);
            }
            return;
        }
        ShareTarget hit = null;
        if (menuShown && isPointerInsideSimpleMenu(x, y)) {
            for (int i = 0; i < menuItems.size(); i++) {
                View item = menuItems.get(i);
                int[] location = new int[2];
                item.getLocationOnScreen(location);
                boolean inside;
                if (isPortalStyle() && item.getWidth() > 0 && item.getHeight() > 0) {
                    float centerX = location[0] + item.getWidth() / 2f;
                    float centerY = location[1] + item.getHeight() / 2f;
                    float scale = GestureMath.portalItemScale(
                            x, centerX, item.getWidth());
                    inside = Math.abs(x - centerX) <= item.getWidth() * scale / 2f
                            && Math.abs(y - centerY) <= item.getHeight() * scale / 2f;
                } else {
                    inside = x >= location[0] && x < location[0] + item.getWidth()
                            && y >= location[1] && y < location[1] + item.getHeight();
                }
                if (inside) {
                    Object tag = item.getTag();
                    if (tag instanceof ShareTarget) {
                        hit = (ShareTarget) tag;
                    }
                    break;
                }
            }
        }
        boolean selectionChanged = hit != selectedTarget;
        selectedTarget = hit;
        if (isPortalStyle()) {
            boolean pointerInMenu = menuShown && isPointerInsideSimpleMenu(x, y);
            for (View item : menuItems) {
                int[] location = new int[2];
                item.getLocationOnScreen(location);
                float scale = pointerInMenu && item.getWidth() > 0
                        ? GestureMath.portalItemScale(
                                x,
                                location[0] + item.getWidth() / 2f,
                                item.getWidth())
                        : 1f;
                item.setScaleX(scale);
                item.setScaleY(scale);
                if (selectionChanged) {
                    item.setBackground(itemBackground(
                            (ShareTarget) item.getTag(),
                            item.getTag() == selectedTarget));
                }
            }
            return;
        }
        if (!selectionChanged) {
            return;
        }
        for (View item : menuItems) {
            boolean selected = item.getTag() == selectedTarget;
            item.setBackground(itemBackground((ShareTarget) item.getTag(), selected));
            item.animate().scaleX(selected ? 1.04f : 1f)
                    .scaleY(selected ? 1.04f : 1f).setDuration(80L).start();
        }
    }

    private void finishGestureOnMain(boolean allowShare) {
        finishGestureOnMain(allowShare, null);
    }

    private void finishGestureOnMain(boolean allowShare, Runnable afterDeactivate) {
        if (!active) {
            if (afterDeactivate != null) afterDeactivate.run();
            return;
        }
        Session finished = session;
        ShareTarget target = selectedTarget;
        if (allowShare && menuShown) {
            updateSelectedTarget(lastX, lastY);
            target = selectedTarget;
        }

        if (settings.blockBackgroundScroll && !backgroundBlockAttempted) {
            log("background scroll lock skipped because root input was not active");
        }
        active = false;
        cancelStaleInputWatchdog();
        stopEdgeScroll();
        backgroundTouchBlocker.stop();
        if (afterDeactivate != null) {
            afterDeactivate.run();
        }

        // HyperOS can silently reject an AccessibilityService activity start after its last
        // accessibility overlay has been removed. Keep the passive overlay attached through
        // the user-selected launch, then clean it up in the same main-thread turn.
        if (allowShare && target != null && finished != null) {
            if (!finished.payload.isImage()
                    || target.isSaveToLocal()
                    || finished.stagedUri != null) {
                launchShare(finished, target);
            } else {
                finished.pendingTarget = target;
                showToast("正在准备图片");
            }
        } else if (finished != null && finished.pendingTarget == null) {
            finished.cancelled = true;
        }
        removeGestureViews(true);
    }

    private void cancelGestureOnMain() {
        if (!active) {
            return;
        }
        active = false;
        cancelStaleInputWatchdog();
        stopEdgeScroll();
        backgroundTouchBlocker.stop();
        removeGestureViews(true);
        if (session != null && session.pendingTarget == null) {
            session.cancelled = true;
        }
    }

    private void failOverlay(String message, RuntimeException error) {
        log(message, error);
        traceAccessibility("overlay failed=" + error.getClass().getSimpleName() + ":" + error.getMessage());
        pendingPortalHostFloatWindowSuppression = false;
        if (session != null) session.cancelled = true;
        if (active) cancelGestureOnMain();
        else removeGestureViews();
        showToast(message + "，已取消本次拖拽；详情见诊断日志", Toast.LENGTH_LONG);
    }

    private void refreshStaleInputWatchdog() {
        cancelStaleInputWatchdog();
        Session observedSession = session;
        if (!active || observedSession == null || destroyed) return;
        Runnable watchdog = new Runnable() {
            @Override
            public void run() {
                if (staleInputWatchdog != this) return;
                staleInputWatchdog = null;
                if (!active || destroyed || session != observedSession) return;
                log("gesture cancelled after root input became stale");
                cancelGestureOnMain();
            }
        };
        staleInputWatchdog = watchdog;
        mainHandler.postDelayed(watchdog, STALE_INPUT_TIMEOUT_MS);
    }

    private void cancelStaleInputWatchdog() {
        Runnable watchdog = staleInputWatchdog;
        staleInputWatchdog = null;
        if (watchdog != null) mainHandler.removeCallbacks(watchdog);
    }

    private void launchPendingShare(Session pendingSession) {
        ShareTarget target = pendingSession.pendingTarget;
        if (target == null
                || (pendingSession.stagedUri == null && !target.isSaveToLocal())) {
            return;
        }
        pendingSession.pendingTarget = null;
        launchShare(pendingSession, target);
    }

    private void launchShare(Session shareSession, ShareTarget target) {
        if (target.isImageOcr()) {
            try {
                launchImageOcrHost(shareSession.stagedUri, false);
            } catch (RuntimeException error) {
                log("image OCR window failed", error);
                showToast("无法识别图片：" + error.getMessage());
            }
            shareSession.cancelled = true;
            return;
        }
        if (target.isQrScan()) {
            try {
                launchImageOcrHost(shareSession.stagedUri, true);
            } catch (RuntimeException error) {
                log("image QR window failed", error);
                showToast("无法扫描图片二维码：" + error.getMessage());
            }
            shareSession.cancelled = true;
            return;
        }
        if (target.isStash()) {
            stashContent(shareSession.payload, shareSession.stagedUri);
            shareSession.cancelled = true;
            return;
        }
        if (target.isSaveToLocal()) {
            saveImageLocally(shareSession.payload.bitmap);
            shareSession.cancelled = true;
            return;
        }
        if (target.isCopyToClipboard()) {
            copyToClipboard(shareSession.payload, shareSession.stagedUri);
            shareSession.cancelled = true;
            return;
        }
        if (target.isTextSegmentation()) {
            openTextSegmentation(shareSession.payload);
            shareSession.cancelled = true;
            return;
        }
        try {
            log("drop target=" + target.component.flattenToShortString());
            ShareLauncher.launch(
                    context,
                    shareSession.payload,
                    target,
                    shareSession.stagedUri,
                    dragShareToast);
        } catch (Throwable error) {
            log("share launch failed", error);
        }
        shareSession.cancelled = true;
    }

    private void launchImageOcrHost(Uri image, boolean qr) {
        android.content.Intent intent = new android.content.Intent();
        intent.setComponent(new android.content.ComponentName(BuildConfig.APPLICATION_ID,
                ImageOcrActivity.class.getName()));
        intent.putExtra(ImageOcrActivity.EXTRA_HOSTED_URI, image);
        intent.putExtra(ImageOcrActivity.EXTRA_QR, qr);
        intent.setDataAndType(image, "image/*");
        intent.setClipData(android.content.ClipData.newRawUri("image", image));
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                | android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
        log("image window handed to Activity qr=" + qr);
    }

    private void stashContent(CapturedContent payload, Uri stagedImage) {
        new Thread(() -> {
            try {
                StashClient.put(context, payload, stagedImage);
                log("stored content in stash kind=" + (payload.isImage() ? "image" : "text"));
                mainHandler.post(() -> showToast("已存入中转站"));
            } catch (IOException | SecurityException | IllegalStateException error) {
                log("stash save failed", error);
                mainHandler.post(() -> showToast("存入中转站失败：" + error.getMessage(), Toast.LENGTH_LONG));
            }
        }, "drag-share-stash-save").start();
    }

    private void copyToClipboard(CapturedContent payload, Uri stagedImage) {
        boolean image = payload != null && payload.isImage();
        if (!image) {
            String text = payload == null ? null : payload.text;
            if (text == null || text.trim().isEmpty()) {
                showToast("没有可复制的文字");
                return;
            }
            ClipboardWriter.copy(context, text, error -> {
                if (error == null) {
                    log("copied text to clipboard");
                    showToast("文字已复制到剪贴板");
                } else {
                    log("clipboard text copy failed", error);
                    showToast("复制文字失败：" + error.getMessage(), Toast.LENGTH_LONG);
                }
            });
            return;
        }
        if (stagedImage == null) {
            showToast("图片尚未准备好，无法复制");
            return;
        }
        ClipboardWriter.copyImage(context, stagedImage, error -> {
            if (error == null) {
                log("copied image to clipboard");
                showToast("图片已复制到剪贴板");
            } else {
                log("clipboard image copy failed", error);
                showToast("复制图片失败：" + error.getMessage(), Toast.LENGTH_LONG);
            }
        });
    }

    private void openTextSegmentation(CapturedContent payload) {
        String text = payload.text;
        if (text == null || text.trim().isEmpty()) {
            showToast("没有可分词的文字");
            return;
        }
        try {
            android.os.Bundle input = new android.os.Bundle();
            input.putString("text", text);
            input.putString("before_text", payload.beforeText);
            input.putString("after_text", payload.afterText);
            input.putInt("x", Math.round(lastX));
            input.putInt("y", Math.round(lastY));
            android.os.Bundle result = context.getContentResolver().call(
                    ImageStagingClient.BASE_URI, BigBangOverlayLauncher.METHOD_OPEN, null, input);
            if (result == null || !result.getBoolean("accepted")) {
                throw new IllegalStateException("分词窗口未接受请求");
            }
            log("requested text segmentation window");
        } catch (RuntimeException error) {
            log("open text segmentation failed", error);
            showToast("无法打开分词窗口：" + error.getMessage());
        }
    }

    private void saveImageLocally(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            showToast("保存图片失败");
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                LocalImageSaver.save(context, bitmap);
                log("saved image as PNG");
                traceAccessibility("local save succeeded");
                mainHandler.post(() -> showToast("已保存到本地", android.widget.Toast.LENGTH_LONG));
            } catch (Throwable error) {
                log("local image save failed", error);
                traceAccessibility("local save failed=" + error.getClass().getSimpleName());
                mainHandler.post(() -> showToast("保存图片失败", android.widget.Toast.LENGTH_LONG));
            }
        }, "drag-share-local-save");
        worker.start();
    }

    private void updatePreviewPosition(float x, float y) {
        if (previewParams == null || previewView == null) {
            return;
        }
        if (isCircleStyle() && menuShown && circleMenuView != null) {
            Rect avoid = circleMenuView.getAvoidRect();
            int margin = dp(12);
            int left = GestureMath.previewLeft(x, previewWidth, screenWidth, dp(8));
            int top = GestureMath.previewTop(
                    y,
                    previewHeight,
                    dp(20),
                    topInset + dp(8),
                    screenHeight - bottomInset - previewHeight - dp(8));
            switch (circleEdge) {
                case CircleMenuGeometry.EDGE_LEFT:
                    left = avoid.right + margin;
                    break;
                case CircleMenuGeometry.EDGE_RIGHT:
                    left = avoid.left - previewWidth - margin;
                    break;
                case CircleMenuGeometry.EDGE_TOP:
                    top = avoid.bottom + margin;
                    break;
                case CircleMenuGeometry.EDGE_BOTTOM:
                    top = avoid.top - previewHeight - margin;
                    break;
                default:
                    break;
            }
            int nextX = GestureMath.clamp(
                    left,
                    dp(8),
                    Math.max(dp(8), screenWidth - previewWidth - dp(8)));
            int nextY = GestureMath.clamp(
                    top,
                    topInset + dp(8),
                    Math.max(topInset + dp(8),
                            screenHeight - bottomInset - previewHeight - dp(8)));
            updatePreviewLayoutIfPositionChanged(nextX, nextY);
            return;
        }
        int margin = dp(settings.simpleMenuEdgeDistanceDp);
        int minLeft = dp(8);
        int maxLeft = Math.max(minLeft, screenWidth - previewWidth - dp(8));
        int minTop = topInset + dp(8);
        int maxTop = Math.max(minTop, screenHeight - bottomInset - previewHeight - dp(8));
        if (menuShown) {
            if (isVerticalSimpleMenu()) {
                if (simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_LEFT) {
                    minLeft = Math.min(maxLeft, menuLeft + menuWidth + margin);
                } else if (simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_RIGHT) {
                    maxLeft = Math.max(minLeft, menuLeft - previewWidth - margin);
                }
            } else if (simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_TOP) {
                minTop = Math.min(maxTop, menuTop + menuHeight + margin);
            } else {
                maxTop = Math.max(minTop, menuTop - previewHeight - margin);
            }
        }
        int nextX = GestureMath.clamp(
                Math.round(x - previewWidth / 2f), minLeft, maxLeft);
        int nextY = GestureMath.clamp(
                Math.round(y - previewHeight - dp(20)), minTop, maxTop);
        updatePreviewLayoutIfPositionChanged(nextX, nextY);
    }

    private void updatePreviewLayoutIfPositionChanged(int nextX, int nextY) {
        if (previewParams == null || GestureMath.samePosition(
                previewParams.x, previewParams.y, nextX, nextY)) {
            return;
        }
        previewParams.x = nextX;
        previewParams.y = nextY;
        updateOverlayLayout(previewView, modernPreviewWindow, previewParams);
    }

    private void updateOverlayLayout(
            View view,
            ModernOverlayWindow modernWindow,
            WindowManager.LayoutParams params) {
        if (view == null || params == null) {
            return;
        }
        if (modernWindow != null) modernWindow.updateLayout();
        else windowManager.updateViewLayout(view, params);
    }

    private void updatePortalPullEffect(float x, float y) {
        if (glowView == null || !isPortalStyle()) {
            return;
        }
        float progress = menuShown
                ? 1f
                : GestureMath.clamp01(1f - distanceFromMenuEdge(x, y) / dp(180));
        if (progress > 0.02f && !portalGlowStartedLogged) {
            portalGlowStartedLogged = true;
            log("portal glow progress started");
        }
        glowView.setPullProgress(progress, glowPointerFraction(x, y));
    }

    private void refreshDisplayGeometry() {
        WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
        screenWidth = metrics.getBounds().width();
        screenHeight = metrics.getBounds().height();
        Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        topInset = insets.top;
        bottomInset = insets.bottom;
        updateMenuGeometry();
    }

    private void updateMenuGeometry() {

        if (isCircleStyle()) {
            menuWidth = screenWidth;
            menuHeight = dp(CircleMenuOverlayView.CONTAINER_SIZE_DP);
            menuLeft = 0;
            menuTop = Math.max(topInset, screenHeight - bottomInset - menuHeight);
            menuTriggerTop = menuTop;
            return;
        }
        int menuMargin = dp(settings.simpleMenuEdgeDistanceDp);
        int[] bounds = LinearMenuGeometry.bounds(simpleMenuPosition, screenWidth, screenHeight,
                topInset, bottomInset, menuMargin, shareTargets.size(),
                context.getResources().getDisplayMetrics().density);
        menuLeft = bounds[0];
        menuTop = bounds[1];
        menuWidth = bounds[2];
        menuHeight = bounds[3];
        menuTriggerTop = screenHeight - bottomInset - menuMargin - dp(MENU_TRIGGER_DP);
        if (glowView != null) {
            int inset = simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_TOP ? topInset
                    : simpleMenuPosition == DragShareSettings.SIMPLE_MENU_POSITION_BOTTOM ? bottomInset : 0;
            glowView.setEdge(simpleMenuPosition, inset);
        }
    }

    private WindowManager.LayoutParams overlayParams(int width, int height, String title) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = windowPolicy.windowType;
        params.format = PixelFormat.TRANSLUCENT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        params.gravity = Gravity.TOP | Gravity.START;
        params.width = width;
        params.height = height;
        params.x = 0;
        params.y = 0;
        params.setTitle(title);
        params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        return params;
    }

    private void removeGestureViews() {
        removeGestureViews(false);
    }

    private void removeGestureViews(boolean animatePreviewExit) {
        cancelStaleInputWatchdog();
        cancelTargetQuery();
        cancelPreviewTargetLoad();
        cancelModernMenuDispose();
        cancelLinearMenuFadeOut();
        if (!animatePreviewExit) {
            removePendingPreviewExitImmediately();
        }
        if (previewView != null && !animatePreviewExit) {
            previewView.animate().cancel();
        }
        if (menuView != null) {
            menuView.animate().cancel();
        }
        for (View item : menuItems) {
            item.animate().cancel();
        }
        if (animatePreviewExit && previewView != null) {
            startPreviewExit(previewView, modernPreviewView, modernPreviewWindow);
        } else {
            removeOverlayView(previewView, modernPreviewView, modernPreviewWindow);
        }
        removeOverlayView(menuView, modernMenuView, modernMenuWindow);
        if (glowView != null) {
            glowView.stop();
            try {
                windowManager.removeViewImmediate(glowView);
            } catch (Throwable error) {
                // Already removed or never attached.
                log("overlay cleanup failed layer=glow", error);
            }
        }
        mainHandler.removeCallbacks(circleExpandRunnable);
        if (circleMenuView != null) {
            circleMenuView.collapse();
            try {
                windowManager.removeViewImmediate(circleMenuView);
            } catch (Throwable error) {
                // Already removed or never attached.
                log("overlay cleanup failed layer=circle_menu", error);
            }
        }
        previewView = null;
        modernPreviewView = null;
        modernPreviewWindow = null;
        previewParams = null;
        glowView = null;
        glowParams = null;
        menuView = null;
        modernMenuView = null;
        modernMenuWindow = null;
        modernMenuDisposeRunnable = null;
        fadingLinearMenuView = null;
        modernPreviewEnterStarted = false;
        menuParams = null;
        menuScroll = null;
        menuVerticalScroll = null;
        menuRow = null;
        circleMenuView = null;
        circleMenuParams = null;
        menuItems.clear();
        selectedTarget = null;
        menuShown = false;
        edgeDirection = 0;
        edgeScrollRemainderPx = 0f;
        circleEdge = CircleMenuGeometry.EDGE_NONE;
        circlePendingEdge = CircleMenuGeometry.EDGE_NONE;
        circleScrollDirection = 0;
        menuLeft = 0;
        menuWidth = 0;
    }

    private void removeOverlayView(
            View view,
            ModernOverlayComposeView modernOverlay,
            ModernOverlayWindow modernWindow) {
        if (modernWindow != null) {
            try {
                modernWindow.dispose();
            } catch (RuntimeException error) {
                log("overlay cleanup failed layer=modern_window", error);
            }
            return;
        }
        if (view != null) {
            try {
                windowManager.removeViewImmediate(view);
            } catch (Throwable error) {
                // Already removed or never attached.
                log("overlay cleanup failed layer=view", error);
            }
        }
        if (modernOverlay != null) {
            try {
                modernOverlay.disposeOverlay();
            } catch (RuntimeException error) {
                log("overlay cleanup failed layer=compose_view", error);
            }
        }
    }

    private void startPreviewExit(
            View previewToFade,
            ModernPreviewOverlayView modernPreviewToFade,
            ModernOverlayWindow modernWindowToFade) {
        removePendingPreviewExitImmediately();
        final int generation = ++previewExitGeneration;
        exitingPreviewView = previewToFade;
        exitingModernPreviewView = modernPreviewToFade;
        exitingModernPreviewWindow = modernWindowToFade;

        if (modernWindowToFade != null) {
            modernWindowToFade.hideAnimated(
                    MODERN_OVERLAY_EXIT_DURATION_MS,
                    () -> finishPreviewExit(
                            generation,
                            previewToFade,
                            modernPreviewToFade,
                            modernWindowToFade));
            return;
        }
        if (modernPreviewToFade != null) {
            modernPreviewToFade.hideAnimated();
            mainHandler.postDelayed(
                    () -> finishPreviewExit(
                            generation,
                            previewToFade,
                            modernPreviewToFade,
                            null),
                    MODERN_OVERLAY_EXIT_DURATION_MS);
            return;
        }

        previewToFade.animate().cancel();
        previewToFade.animate()
                .alpha(0f)
                .scaleX(Math.max(0f, previewToFade.getScaleX() * 0.96f))
                .scaleY(Math.max(0f, previewToFade.getScaleY() * 0.96f))
                .setDuration(PREVIEW_EXIT_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .withEndAction(() -> finishPreviewExit(
                        generation,
                        previewToFade,
                        null,
                        null))
                .start();
    }

    private void finishPreviewExit(
            int generation,
            View previewToRemove,
            ModernPreviewOverlayView modernPreviewToRemove,
            ModernOverlayWindow modernWindowToRemove) {
        if (generation != previewExitGeneration
                || exitingPreviewView != previewToRemove
                || exitingModernPreviewView != modernPreviewToRemove
                || exitingModernPreviewWindow != modernWindowToRemove) {
            return;
        }
        exitingPreviewView = null;
        exitingModernPreviewView = null;
        exitingModernPreviewWindow = null;
        removeOverlayView(previewToRemove, modernPreviewToRemove, modernWindowToRemove);
    }

    private boolean isPreviewExitPending() {
        return exitingPreviewView != null;
    }

    private void removePendingPreviewExitImmediately() {
        previewExitGeneration++;
        View previewToRemove = exitingPreviewView;
        ModernPreviewOverlayView modernPreviewToRemove = exitingModernPreviewView;
        ModernOverlayWindow modernWindowToRemove = exitingModernPreviewWindow;
        exitingPreviewView = null;
        exitingModernPreviewView = null;
        exitingModernPreviewWindow = null;
        if (previewToRemove != null) {
            previewToRemove.animate().cancel();
        }
        removeOverlayView(previewToRemove, modernPreviewToRemove, modernWindowToRemove);
    }

    private void scheduleModernMenuDispose(
            ModernMenuOverlayView menuToDispose,
            ModernOverlayWindow windowToDispose) {
        cancelModernMenuDispose();
        Runnable disposeRunnable = () -> {
            if (menuShown || modernMenuView != menuToDispose) {
                return;
            }
            if (windowToDispose != null) {
                windowToDispose.dispose();
            } else {
                try {
                    windowManager.removeViewImmediate(menuToDispose);
                } catch (Throwable ignored) {
                    // The overlay can already be gone while the host tears down its window.
                }
                menuToDispose.disposeOverlay();
            }
            if (menuView == menuToDispose) {
                menuView = null;
                modernMenuView = null;
                modernMenuWindow = null;
                menuParams = null;
            }
        };
        modernMenuDisposeRunnable = disposeRunnable;
        if (windowToDispose != null) {
            windowToDispose.hideAnimated(
                    MODERN_OVERLAY_EXIT_DURATION_MS,
                    () -> {
                        if (modernMenuDisposeRunnable == disposeRunnable) {
                            disposeRunnable.run();
                        }
                    });
        } else {
            menuToDispose.hideAnimated();
            mainHandler.postDelayed(disposeRunnable, MODERN_OVERLAY_EXIT_DURATION_MS);
        }
    }

    private void cancelModernMenuDispose() {
        if (modernMenuDisposeRunnable != null) {
            mainHandler.removeCallbacks(modernMenuDisposeRunnable);
            modernMenuDisposeRunnable = null;
        }
    }

    private void fadeOutLinearMenu(View menuToFade) {
        if (menuToFade == null) {
            return;
        }
        cancelLinearMenuFadeOut();
        final int generation = ++linearMenuFadeGeneration;
        fadingLinearMenuView = menuToFade;
        menuToFade.animate().cancel();
        menuToFade.animate()
                .alpha(0f)
                .setDuration(LINEAR_MENU_EXIT_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .withEndAction(() -> {
                    if (generation != linearMenuFadeGeneration
                            || fadingLinearMenuView != menuToFade) {
                        return;
                    }
                    fadingLinearMenuView = null;
                    if (menuView == menuToFade && menuShown) {
                        return;
                    }
                    removeLinearMenuImmediately(menuToFade);
                })
                .start();
    }

    private void cancelLinearMenuFadeOut(View menuToRestore) {
        if (fadingLinearMenuView == menuToRestore) {
            cancelLinearMenuFadeOut();
        }
    }

    private void cancelLinearMenuFadeOut() {
        linearMenuFadeGeneration++;
        View fading = fadingLinearMenuView;
        fadingLinearMenuView = null;
        if (fading != null) {
            fading.animate().cancel();
        }
    }

    private void removeLinearMenuImmediately(View menuToRemove) {
        if (menuToRemove == null) {
            return;
        }
        menuToRemove.animate().cancel();
        try {
            windowManager.removeViewImmediate(menuToRemove);
        } catch (Throwable ignored) {
            // The overlay may already have been removed by the host.
        }
    }

    private void stopEdgeScroll() {
        edgeDirection = 0;
        circleScrollDirection = 0;
        lastEdgeScrollUptime = 0L;
        edgeScrollRemainderPx = 0f;
        mainHandler.removeCallbacks(edgeScrollRunnable);
    }

    private boolean isPortalStyle() {
        return settings != null && settings.uiStyle == DragShareSettings.STYLE_PORTAL;
    }

    private boolean isCircleStyle() {
        return settings != null && settings.uiStyle == DragShareSettings.STYLE_CIRCLE;
    }

    private boolean isModernStyle() {
        return settings != null && settings.uiStyle == DragShareSettings.STYLE_MODERN;
    }

    private void startBackgroundBlockerIfEnabled() {
        if (!settings.blockBackgroundScroll || backgroundBlockAttempted) {
            return;
        }
        backgroundBlockAttempted = true;
        if (!backgroundTouchBlocker.start()) {
            log("background scroll lock unavailable; continuing without it");
        }
    }

    private GradientDrawable itemBackground(ShareTarget target, boolean selected) {
        if (!selected) {
            return roundDrawable(Color.TRANSPARENT, isPortalStyle() ? 28 : 8);
        }
        GradientDrawable drawable = roundDrawable(
                palette.selectedItemBackground,
                isPortalStyle() ? 28 : 8);
        if (isPortalStyle()) {
            drawable.setStroke(dp(1), palette.selectedItemBorder);
        }
        return drawable;
    }

    private Drawable iconForTarget(ShareTarget target) {
        return ShareTargetRepository.iconForDisplay(target);
    }

    private static int applyAbsoluteOpacity(int color, int percent) {
        int alpha = Math.round(255f * (Math.max(0, Math.min(100, percent)) / 100f));
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    static float simpleMenuBackgroundOpacityFraction(int percent) {
        return Math.max(0, Math.min(100, percent)) / 100f;
    }

    private GradientDrawable roundDrawable(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()));
    }

    private float dpFloat(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics());
    }

    private void showToast(String message) {
        dragShareToast.show(message);
    }

    private void showToast(String message, int duration) {
        dragShareToast.show(message, duration);
    }

    private void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }

    private static void log(String message) {
        DragShareLog.i(TAG, message);
    }

    private static void log(String message, Throwable error) {
        DragShareLog.w(TAG, message, error);
    }

    private void traceAccessibility(String message) {
        if ("accessibility".equals(windowPolicy.sourceName)) {
            AccessibilityTrace.record(context, message);
        }
    }

    private static final class Session {
        final CapturedContent payload;
        final long rootDownTime;
        Uri stagedUri;
        ShareTarget pendingTarget;
        boolean cancelled;

        Session(CapturedContent payload, long rootDownTime) {
            this.payload = payload;
            this.rootDownTime = rootDownTime;
        }
    }

    private static final class OverlayColors {
        final int previewBackground;
        final int menuBackground;
        final int primaryText;
        final int secondaryText;
        final int selectedItemBackground;
        final int selectedItemBorder;
        final int accent;

        private OverlayColors(
                int previewBackground,
                int menuBackground,
                int primaryText,
                int secondaryText,
                int selectedItemBackground,
                int selectedItemBorder,
                int accent) {
            this.previewBackground = previewBackground;
            this.menuBackground = menuBackground;
            this.primaryText = primaryText;
            this.secondaryText = secondaryText;
            this.selectedItemBackground = selectedItemBackground;
            this.selectedItemBorder = selectedItemBorder;
            this.accent = accent;
        }

        static OverlayColors light() {
            return from(false);
        }

        static OverlayColors from(boolean dark) {
            int[] colors = OverlayThemeColors.colors(dark);
            return new OverlayColors(colors[0], colors[0], colors[1], colors[2], colors[4], colors[5], colors[3]);
        }
    }
}

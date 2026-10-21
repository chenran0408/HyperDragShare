package com.leaf.hyperdragshare.gestures.config;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Module-owned touch bounds with a death-aware SystemUI snapshot. */
public final class OverlayTouchRegions {
    private static final Binder MODULE_OWNER = new Binder();
    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private static final Map<Integer, Rect> moduleRects = new HashMap<>();

    private static volatile List<Rect> systemUiRects = List.of();
    private static volatile IBinder systemUiOwner;
    private static IBinder.DeathRecipient systemUiOwnerDeath;
    private static volatile Runnable systemUiChanged = () -> {};

    private OverlayTouchRegions() {}

    public static int newWindowId() { return NEXT_ID.incrementAndGet(); }

    /** Called from module windows; ConfigProvider shares this process-local snapshot. */
    public static void update(Context context, int id, Rect rect) {
        boolean changed;
        synchronized (moduleRects) {
            Rect previous = moduleRects.get(id);
            if (rect == null || rect.isEmpty()) changed = moduleRects.remove(id) != null;
            else if (previous != null && previous.equals(rect)) changed = false;
            else {
                moduleRects.put(id, new Rect(rect));
                changed = true;
            }
        }
        if (changed) context.getContentResolver().notifyChange(ConfigContract.OVERLAY_TOUCH_URI, null);
    }

    /** SystemUI subscribes on its worker thread; observer callbacks never run on input dispatch. */
    public static void observe(Context context, Handler worker, Runnable onChanged) {
        systemUiChanged = onChanged == null ? () -> {} : onChanged;
        ContentObserver observer = new ContentObserver(worker) {
            @Override public void onChange(boolean selfChange) { refresh(context); }
        };
        try {
            context.getContentResolver().registerContentObserver(ConfigContract.OVERLAY_TOUCH_URI,
                    false, observer);
            worker.post(() -> refresh(context));
        } catch (RuntimeException error) {
            android.util.Log.w("HyperDragShareConfig", "Cannot observe overlay touch bounds", error);
            replaceSystemUiSnapshot(null, List.of());
        }
    }

    /** Cached check suitable for ACTION_DOWN on SystemUI's input thread. */
    public static boolean contains(float x, float y) {
        IBinder owner = systemUiOwner;
        if (owner == null || !owner.isBinderAlive()) return false;
        for (Rect rect : systemUiRects) if (rect.contains((int) x, (int) y)) return true;
        return false;
    }

    public static List<Rect> snapshot() {
        IBinder owner = systemUiOwner;
        return owner == null || !owner.isBinderAlive() ? List.of() : copyRects(systemUiRects);
    }

    static Bundle read() {
        Bundle result = new Bundle();
        result.putBinder(ConfigContract.KEY_OVERLAY_TOUCH_OWNER, MODULE_OWNER);
        synchronized (moduleRects) {
            ArrayList<Integer> values = new ArrayList<>(moduleRects.size() * 4);
            for (Rect rect : moduleRects.values()) {
                values.add(rect.left); values.add(rect.top); values.add(rect.right); values.add(rect.bottom);
            }
            int[] bounds = new int[values.size()];
            for (int i = 0; i < values.size(); i++) bounds[i] = values.get(i);
            result.putIntArray(ConfigContract.KEY_OVERLAY_TOUCH_RECTS, bounds);
        }
        return result;
    }

    private static void refresh(Context context) {
        try {
            Bundle response = context.getContentResolver().call(ConfigContract.URI,
                    ConfigContract.METHOD_GET_OVERLAY_TOUCH, null, null);
            if (response == null) {
                replaceSystemUiSnapshot(null, List.of());
                return;
            }
            IBinder owner = response.getBinder(ConfigContract.KEY_OVERLAY_TOUCH_OWNER);
            int[] values = response.getIntArray(ConfigContract.KEY_OVERLAY_TOUCH_RECTS);
            List<Rect> rects = new ArrayList<>();
            if (values != null && values.length % 4 == 0) {
                for (int i = 0; i < values.length; i += 4) {
                    Rect rect = new Rect(values[i], values[i + 1], values[i + 2], values[i + 3]);
                    if (!rect.isEmpty()) rects.add(rect);
                }
            }
            replaceSystemUiSnapshot(owner, rects);
        } catch (RuntimeException error) {
            replaceSystemUiSnapshot(null, List.of());
            android.util.Log.w("HyperDragShareConfig", "Cannot read overlay touch bounds", error);
        }
    }

    private static synchronized void replaceSystemUiSnapshot(IBinder owner, List<Rect> rects) {
        if (owner != systemUiOwner) {
            if (systemUiOwner != null && systemUiOwnerDeath != null)
                systemUiOwner.unlinkToDeath(systemUiOwnerDeath, 0);
            systemUiOwner = owner;
            systemUiOwnerDeath = null;
            if (owner != null) {
                IBinder.DeathRecipient death = () -> {
                    synchronized (OverlayTouchRegions.class) {
                        if (systemUiOwner == owner) {
                            systemUiOwner = null;
                            systemUiOwnerDeath = null;
                            systemUiRects = List.of();
                        }
                    }
                    systemUiChanged.run();
                };
                try {
                    owner.linkToDeath(death, 0);
                    systemUiOwnerDeath = death;
                } catch (RemoteException dead) {
                    systemUiOwner = null;
                    rects = List.of();
                }
            }
        }
        systemUiRects = copyRects(rects);
        systemUiChanged.run();
    }

    private static List<Rect> copyRects(List<Rect> rects) {
        ArrayList<Rect> copy = new ArrayList<>(rects.size());
        for (Rect rect : rects) copy.add(new Rect(rect));
        return List.copyOf(copy);
    }

    /** Locks ownership to the DOWN so moving outside does not hand off a live stream. */
    public static final class DownGate {
        private long ownedDownTime = Long.MIN_VALUE;

        public boolean shouldYield(int action, long downTime, boolean downInsideRegion) {
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                ownedDownTime = downInsideRegion ? downTime : Long.MIN_VALUE;
                return ownedDownTime == downTime;
            }
            if (ownedDownTime == downTime) {
                if (action == android.view.MotionEvent.ACTION_UP
                        || action == android.view.MotionEvent.ACTION_CANCEL) ownedDownTime = Long.MIN_VALUE;
                return true;
            }
            return false;
        }
    }
}

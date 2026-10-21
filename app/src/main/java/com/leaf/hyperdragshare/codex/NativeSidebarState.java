package com.leaf.hyperdragshare.codex;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/** Ephemeral input ownership, with no settings writes and no text/image data. */
public final class NativeSidebarState {
    static final String REPORT = "report_sidebar_regions";
    static final String READ = "get_sidebar_regions";
    private static final Uri URI = Uri.parse("content://com.leaf.hyperdragshare.codex.share/sidebar_regions");
    private static IBinder owner;
    private static IBinder.DeathRecipient death;
    private static int[] regions = new int[0];
    private static volatile List<Rect> cached = List.of();
    private static volatile IBinder cachedOwner;
    private static IBinder.DeathRecipient cachedDeath;
    private static volatile Runnable changed = () -> {};

    static synchronized Bundle report(Context context, Bundle input) throws RemoteException {
        if (input == null) throw new IllegalArgumentException("Missing native sidebar snapshot");
        IBinder next = input.getBinder("owner");
        int[] bounds = input.getIntArray("regions");
        if (next == null || bounds == null || bounds.length > 32 || bounds.length % 4 != 0)
            throw new IllegalArgumentException("Invalid native sidebar regions");
        for (int i = 0; i < bounds.length; i += 4) {
            if (bounds[i] >= bounds[i + 2] || bounds[i + 1] >= bounds[i + 3])
                throw new IllegalArgumentException("Invalid native sidebar rectangle");
        }
        if (owner != next) {
            IBinder.DeathRecipient nextDeath = () -> clear(context, next);
            next.linkToDeath(nextDeath, 0);
            if (owner != null) owner.unlinkToDeath(death, 0);
            owner = next; death = nextDeath;
        }
        if (!java.util.Arrays.equals(regions, bounds)) {
            regions = bounds.clone();
            context.getContentResolver().notifyChange(URI, null);
        }
        return Bundle.EMPTY;
    }

    private static synchronized void clear(Context context, IBinder expected) {
        if (owner != expected) return;
        owner = null; death = null; regions = new int[0];
        context.getContentResolver().notifyChange(URI, null);
    }

    static synchronized Bundle read(Context context) {
        if (owner != null && !owner.isBinderAlive()) clear(context, owner);
        Bundle result = new Bundle(); result.putIntArray("regions", regions.clone());
        result.putBinder("owner", owner); return result;
    }

    /** SystemUI observes only geometry; all IPC stays off the input thread. */
    public static void observe(Context context, Handler worker, Runnable onChanged) {
        if (!"com.android.systemui".equals(context.getPackageName())) return;
        changed = onChanged == null ? () -> {} : onChanged;
        ContentObserver observer = new ContentObserver(worker) {
            @Override public void onChange(boolean selfChange) { refresh(context); }
        };
        try {
            context.getContentResolver().registerContentObserver(URI, false, observer);
            worker.post(() -> refresh(context));
        } catch (IllegalArgumentException | SecurityException error) {
            DragShareLog.w("DragShare/Sidebar", "Cannot observe sidebar regions; Hyper gestures retained", error);
        }
    }

    private static void refresh(Context context) {
        try {
            Bundle response = context.getContentResolver().call(ImageStagingClient.BASE_URI, READ, null, null);
            int[] values = response == null ? null : response.getIntArray("regions");
            IBinder next = response == null ? null : response.getBinder("owner");
            List<Rect> rects = new ArrayList<>();
            if (values != null && values.length <= 32 && values.length % 4 == 0) {
                for (int i = 0; i < values.length; i += 4) {
                    Rect rect = new Rect(values[i], values[i + 1], values[i + 2], values[i + 3]);
                    if (!rect.isEmpty()) rects.add(rect);
                }
            } else if (values != null) {
                throw new IllegalArgumentException("Malformed native sidebar snapshot");
            }
            replaceCached(next, rects);
        } catch (IllegalArgumentException | IllegalStateException | SecurityException error) {
            replaceCached(null, List.of());
            DragShareLog.w("DragShare/Sidebar", "Cannot read sidebar input ownership; Hyper gestures retained", error);
        }
    }

    private static synchronized void replaceCached(IBinder next, List<Rect> rects) {
        boolean changedState = next != cachedOwner || !cached.equals(rects);
        if (next != cachedOwner) {
            if (cachedOwner != null && cachedDeath != null) cachedOwner.unlinkToDeath(cachedDeath, 0);
            cachedOwner = next;
            cachedDeath = null;
            if (next != null) {
                IBinder.DeathRecipient nextDeath = () -> {
                    synchronized (NativeSidebarState.class) {
                        if (cachedOwner == next) {
                            cachedOwner = null;
                            cachedDeath = null;
                            cached = List.of();
                        }
                    }
                    changed.run();
                };
                try {
                    next.linkToDeath(nextDeath, 0);
                    cachedDeath = nextDeath;
                } catch (RemoteException dead) {
                    cachedOwner = null;
                    rects = List.of();
                }
            }
        }
        cached = copyRects(rects);
        if (changedState) changed.run();
    }

    public static List<Rect> snapshot() {
        IBinder live = cachedOwner;
        return live == null || !live.isBinderAlive() ? List.of() : copyRects(cached);
    }

    private static List<Rect> copyRects(List<Rect> source) {
        ArrayList<Rect> copies = new ArrayList<>(source.size());
        for (Rect rect : source) copies.add(new Rect(rect));
        return Collections.unmodifiableList(copies);
    }

    public static boolean owns(float x, float y) {
        IBinder live = cachedOwner;
        if (live == null || !live.isBinderAlive()) return false;
        for (Rect rect : cached) if (rect.contains((int) x, (int) y)) return true;
        return false;
    }
}

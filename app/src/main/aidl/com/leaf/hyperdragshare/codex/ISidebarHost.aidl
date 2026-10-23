package com.leaf.hyperdragshare.codex;

import android.content.ClipData;
import com.leaf.hyperdragshare.codex.ISidebarDragCallback;

interface ISidebarHost {
    oneway void requestClose();
    // Keep this beta12 transaction shape stable for a module loaded beside an older host.
    oneway void startDrag(in ClipData clip, int flags, ISidebarDragCallback callback);
    oneway void startDragWithShadow(in ClipData clip, int flags, in byte[] shadowPng,
            int bitmapWidthPx, int bitmapHeightPx, int shadowWidthPx, int shadowHeightPx,
            int scaledHotspotX, int scaledHotspotY, ISidebarDragCallback callback);
}

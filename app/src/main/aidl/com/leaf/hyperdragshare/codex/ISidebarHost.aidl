package com.leaf.hyperdragshare.codex;

import android.content.ClipData;
import com.leaf.hyperdragshare.codex.ISidebarDragCallback;

interface ISidebarHost {
    oneway void requestClose();
    oneway void startDrag(in ClipData clip, int flags, ISidebarDragCallback callback);
}

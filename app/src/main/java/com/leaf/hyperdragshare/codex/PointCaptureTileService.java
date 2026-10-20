package com.leaf.hyperdragshare.codex;

import android.content.Intent;

/** Explicit one-shot semantic text/image picking, separate from contact-area detection. */
public final class PointCaptureTileService extends DragShareTileService {
    @Override protected Intent entryIntent() {
        return new Intent(this, PointCaptureActivity.class);
    }
}

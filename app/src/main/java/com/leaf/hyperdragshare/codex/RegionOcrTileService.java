package com.leaf.hyperdragshare.codex;

import android.content.Intent;

/** Explicit capture action which selects a region before running OCR. */
public final class RegionOcrTileService extends DragShareTileService {
    @Override
    protected Intent entryIntent() {
        return super.entryIntent()
                .setAction("com.leaf.hyperdragshare.codex.REGION_OCR")
                .putExtra(ScreenOcrActivity.EXTRA_SELECT_REGION, true);
    }
}

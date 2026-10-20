package com.leaf.hyperdragshare.codex;

import android.content.Intent;

/** User-requested side stash entry, sharing the standard tile collapse behavior. */
public final class StashTileService extends DragShareTileService {
    @Override
    protected Intent entryIntent() {
        return new Intent(this, StashWindowActivity.class);
    }
}

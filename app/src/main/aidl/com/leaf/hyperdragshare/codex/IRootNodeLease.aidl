package com.leaf.hyperdragshare.codex;

import android.os.Bundle;
import com.leaf.hyperdragshare.codex.IRootNodeCandidate;

interface IRootNodeLease {
    Bundle capture(float x, float y, in String[] blocked, boolean allowText, boolean allowImages, boolean pressLookup,
                   boolean earlyCapture,
                   IRootNodeCandidate callback);
    boolean verifyWindow(String sourcePackage, int windowId);
    boolean verifySource(String sourcePackage);
    boolean isOpen();
    void close();
}

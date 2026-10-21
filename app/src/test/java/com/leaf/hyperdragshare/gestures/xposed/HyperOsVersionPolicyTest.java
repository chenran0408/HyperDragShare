package com.leaf.hyperdragshare.gestures.xposed;

import org.junit.Test;
import static org.junit.Assert.*;

public class HyperOsVersionPolicyTest {
    @Test public void vendorVersionOverridesAndroidFallback() {
        assertTrue(HyperOsVersionPolicy.useOs4Compatibility("OS4.0.0.15", 36));
        assertTrue(HyperOsVersionPolicy.useOs4Compatibility(" OS4.0.0.7 ", 37));
        assertFalse(HyperOsVersionPolicy.useOs4Compatibility("OS3.0.317.0", 37));
        assertFalse(HyperOsVersionPolicy.useOs4Compatibility("unrecognized", 37));
    }

    @Test public void absentPropertyUsesApkFallback() {
        assertTrue(HyperOsVersionPolicy.useOs4Compatibility(null, 37));
        assertTrue(HyperOsVersionPolicy.useOs4Compatibility("  ", 37));
        assertFalse(HyperOsVersionPolicy.useOs4Compatibility("", 36));
    }
}

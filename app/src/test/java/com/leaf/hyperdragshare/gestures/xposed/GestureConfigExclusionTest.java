package com.leaf.hyperdragshare.gestures.xposed;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class GestureConfigExclusionTest {
    @Test
    public void normalizesConfiguredExcludedPackages() {
        Set<String> packages = GestureConfig.normalizeGestureExcludedPackages(
                Arrays.asList("com.example.game", " com.example.blocked ", "", null,
                        "com.example.game"));

        assertEquals(2, packages.size());
        assertTrue(packages.contains("com.example.game"));
        assertTrue(packages.contains("com.example.blocked"));
    }

    @Test
    public void missingExclusionsAreEmpty() {
        assertTrue(GestureConfig.normalizeGestureExcludedPackages(null).isEmpty());
        assertTrue(GestureConfig.normalizeGestureExcludedPackages(Collections.emptyList())
                .isEmpty());
    }
}

package com.leaf.hyperdragshare.gestures.xposed;

final class HyperOsVersionPolicy {
    private HyperOsVersionPolicy() { }

    static boolean useOs4Compatibility(String version, int sdk) {
        // Keep an explicit OS3 property authoritative. API 37 is only the APK's
        // fallback when the vendor property cannot be obtained.
        if (version != null && !version.trim().isEmpty()) {
            return version.trim().startsWith("OS4");
        }
        return sdk >= 37;
    }
}

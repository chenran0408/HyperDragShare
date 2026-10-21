# Xposed entry points are loaded by class name from assets/xposed_init.
-keep class com.leaf.hyperdragshare.codex.MainHook { *; }
-keep class com.leaf.hyperdragshare.codex.RootNodeCaptureMain {
    public static void main(java.lang.String[]);
}
-keep class com.leaf.hyperdragshare.codex.RootClipboardMain {
    public static void main(java.lang.String[]);
}

# cppjieba registers these methods by their Java class and method names in JNI_OnLoad.
-keep class com.leaf.hyperdragshare.codex.TextSegmenter {
    native <methods>;
}

# HyperGesture runtime hooks and proxies use reflection across the host boundary.
-keep class com.leaf.hyperdragshare.gestures.xposed.** { *; }
-keep class com.leaf.hyperdragshare.gestures.config.ConfigProvider { *; }

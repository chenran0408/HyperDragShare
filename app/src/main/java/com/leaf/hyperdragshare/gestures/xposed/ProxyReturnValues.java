package com.leaf.hyperdragshare.gestures.xposed;

final class ProxyReturnValues {
    private ProxyReturnValues() { }

    static Object defaultFor(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return (char) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    static Object eventResult(Class<?> type, boolean intercepted) {
        return type == boolean.class ? intercepted : defaultFor(type);
    }
}

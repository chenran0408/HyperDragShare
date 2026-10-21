package com.leaf.hyperdragshare.gestures.xposed;

import java.lang.reflect.Proxy;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProxyReturnValuesTest {
    interface VendorCallbacks {
        boolean onEvent();
        boolean interceptEventWhenHandled();
        byte unknownByte();
        char unknownChar();
        short unknownShort();
        int unknownInt();
        long unknownLong();
        float unknownFloat();
        double unknownDouble();
        void unknownVoid();
        Object unknownObject();
    }

    @Test public void vendorPrimitiveCallbacksCanUnboxEveryDefault() {
        VendorCallbacks callback = (VendorCallbacks) Proxy.newProxyInstance(
                VendorCallbacks.class.getClassLoader(), new Class<?>[]{VendorCallbacks.class},
                (proxy, method, args) -> ProxyReturnValues.defaultFor(method.getReturnType()));
        assertFalse(callback.onEvent());
        assertFalse(callback.interceptEventWhenHandled());
        assertEquals(0, callback.unknownByte());
        assertEquals(0, callback.unknownChar());
        assertEquals(0, callback.unknownShort());
        assertEquals(0, callback.unknownInt());
        assertEquals(0L, callback.unknownLong());
        assertEquals(0f, callback.unknownFloat(), 0f);
        assertEquals(0d, callback.unknownDouble(), 0d);
        callback.unknownVoid();
        assertNull(callback.unknownObject());
    }

    @Test public void eventResultHonorsLegacyVoidAndModernBooleanSignatures() {
        assertEquals(true, ProxyReturnValues.eventResult(boolean.class, true));
        assertEquals(false, ProxyReturnValues.eventResult(boolean.class, false));
        assertNull(ProxyReturnValues.eventResult(void.class, true));
        assertEquals(0, ProxyReturnValues.eventResult(int.class, true));
    }
}

package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 纯 JVM 单测：WifiQrPayload 不依赖 Android 类，无需 Robolectric。 */
class WifiQrPayloadTest {
    @Test
    fun parsesRealSampleWithSaeAuth() {
        val payload = WifiQrPayload.parse("WIFI:T:SAE;P:wyfuckmynet;S:Fuck_Mynet;H:false;")!!
        assertEquals("Fuck_Mynet", payload.ssid)
        assertEquals("SAE", payload.authType)
        assertEquals("wyfuckmynet", payload.password)
        assertEquals(false, payload.hidden)
        assertEquals("WPA3", payload.describe())
    }

    @Test
    fun restoresEscapedCharacters() {
        val payload = WifiQrPayload.parse("""WIFI:T:WPA;S:My\;Net;P:pa\:ss;;""")!!
        assertEquals("My;Net", payload.ssid)
        assertEquals("pa:ss", payload.password)
        assertEquals("WPA", payload.authType)
    }

    @Test
    fun parsesOpenNetworkWithoutPassword() {
        val payload = WifiQrPayload.parse("WIFI:T:nopass;S:CoffeeShop;;")!!
        assertEquals("CoffeeShop", payload.ssid)
        assertNull(payload.password)
        assertEquals("开放网络", payload.describe())
    }

    @Test
    fun appendsHiddenSummaryForHiddenNetwork() {
        val payload = WifiQrPayload.parse("WIFI:T:SAE;S:Home;H:TRUE;;")!!
        assertEquals("WPA3 · 隐藏网络", payload.describe())
    }

    @Test
    fun keepsAuthTypeAsWrittenForWpaVariants() {
        val payload = WifiQrPayload.parse("WIFI:T:WPA2;S:Home;P:secret;;")!!
        assertEquals("WPA2", payload.describe())
    }

    @Test
    fun returnsNullWhenSsidMissing() {
        assertNull(WifiQrPayload.parse("WIFI:T:WPA;P:pass;H:false;"))
        // 小写键不属于 zxing 定义的键名，同样视为缺 S。
        assertNull(WifiQrPayload.parse("WIFI:t:wpa;s:home;;"))
    }

    @Test
    fun returnsNullForNonWifiContent() {
        assertNull(WifiQrPayload.parse("https://example.com"))
        assertNull(WifiQrPayload.parse("MECARD:N:leaf;;"))
        assertNull(WifiQrPayload.parse(""))
    }

    @Test
    fun returnsNullForMalformedSegment() {
        // 键值对缺少冒号属于格式错误，不能静默吞掉。
        assertNull(WifiQrPayload.parse("WIFI:T:WPA;broken;S:Home;;"))
    }
}

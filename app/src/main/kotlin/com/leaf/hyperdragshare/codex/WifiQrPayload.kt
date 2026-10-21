package com.leaf.hyperdragshare.codex

/**
 * 解析 zxing 定义的 WiFi Network Config 二维码内容（形如 `WIFI:T:WPA;S:ssid;P:pass;H:false;;`）。
 * 扫码结果页原本只认 http/mailto/tel，WiFi 串会被当成普通文本展示，用户拿不到可一键复制的密码，
 * 因此这里补一个纯 Kotlin 的结构化解析器（不依赖 Android 类，方便 JVM 单测直接覆盖）。
 */
data class WifiQrPayload(
    /** 网络名称，解析成功的必要条件。 */
    val ssid: String,
    /** 认证类型原文（如 WPA、WPA2、WEP、SAE、nopass），未声明时为 null，按开放网络对待。 */
    val authType: String?,
    /** 密码原文，开放网络或未提供时为 null。 */
    val password: String?,
    /** 是否为隐藏网络。 */
    val hidden: Boolean,
) {
    /** 面向用户的一行摘要，例如「WPA3 · 隐藏网络」「开放网络」。 */
    fun describe(): String {
        val auth = when {
            authType == null || authType.equals("nopass", ignoreCase = true) -> "开放网络"
            // SAE 即 WPA3 的个人模式，对用户展示更友好的名称。
            authType.equals("SAE", ignoreCase = true) -> "WPA3"
            // WPA/WPA2/WEP 等按原文展示，避免过度翻译丢失信息。
            else -> authType
        }
        return if (hidden) "$auth · 隐藏网络" else auth
    }

    companion object {
        private const val PREFIX = "WIFI:"
        private const val ESCAPABLE = "\\;,:."

        /**
         * 解析 WiFi 二维码内容；不是 WIFI: 串或缺少 SSID 等格式错误时返回 null，
         * 调用方据此回退到既有的纯文本渲染。
         */
        fun parse(value: String): WifiQrPayload? {
            if (!value.startsWith(PREFIX)) return null
            var ssid: String? = null
            var password: String? = null
            var authType: String? = null
            var hidden = false
            for (token in splitUnescaped(value.substring(PREFIX.length), ';')) {
                // 结尾分号（或 zxing 惯用的双分号）产生的空段直接跳过。
                if (token.isEmpty()) continue
                // 键内不会出现转义，第一个冒号即键值边界；无冒号说明整段格式错误。
                val keyEnd = token.indexOf(':')
                if (keyEnd <= 0) return null
                // 键名大小写敏感（zxing 规范只定义大写键），小写键按未知键忽略。
                when (token.substring(0, keyEnd)) {
                    "S" -> ssid = unescape(token.substring(keyEnd + 1))
                    "P" -> password = unescape(token.substring(keyEnd + 1))
                    "T" -> authType = unescape(token.substring(keyEnd + 1))
                    "H" -> hidden = token.substring(keyEnd + 1).equals("true", ignoreCase = true)
                    else -> Unit
                }
            }
            if (ssid.isNullOrEmpty()) return null
            return WifiQrPayload(ssid, authType, password, hidden)
        }

        /** 按未转义的 [separator] 切分，`\;` 这类转义序列保持在同一段内。 */
        private fun splitUnescaped(value: String, separator: Char): List<String> {
            val segments = mutableListOf<String>()
            val current = StringBuilder()
            var index = 0
            while (index < value.length) {
                val c = value[index]
                if (c == '\\' && index + 1 < value.length && value[index + 1] in ESCAPABLE) {
                    current.append(c).append(value[index + 1])
                    index += 2
                } else if (c == separator) {
                    segments.add(current.toString())
                    current.clear()
                    index++
                } else {
                    current.append(c)
                    index++
                }
            }
            segments.add(current.toString())
            return segments
        }

        /** 还原 `\;` `\\` `\,` `\:` `\.` 转义；其余转义按 zxing 惯例丢弃反斜杠保留字符。 */
        private fun unescape(value: String): String {
            if (!value.contains('\\')) return value
            val result = StringBuilder(value.length)
            var index = 0
            while (index < value.length) {
                val c = value[index]
                if (c == '\\' && index + 1 < value.length) {
                    result.append(value[index + 1])
                    index += 2
                } else {
                    result.append(c)
                    index++
                }
            }
            return result.toString()
        }
    }
}

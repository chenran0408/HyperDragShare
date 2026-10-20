package com.leaf.hyperdragshare.codex

import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal data class SearchEngine(
    val id: String,
    val label: String,
    private val urlPrefix: String,
    private val urlSuffix: String = "",
) {
    fun searchUrl(query: String): String =
        urlPrefix + URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20") + urlSuffix
}

internal object SearchEngineCatalog {
    private val baidu = SearchEngine("baidu", "百度", "https://www.baidu.com/s?wd=")
    private val youdaoDictionary = SearchEngine(
        "youdao-dictionary",
        "有道词典",
        "https://dict.youdao.com/result?word=",
    )
    private val youdaoTranslate = SearchEngine(
        "youdao-translate",
        "有道翻译",
        "https://m.youdao.com/translate?inputtext=",
    )

    val engines = listOf(
        baidu,
        SearchEngine("bing", "必应", "https://www.bing.com/search?q="),
        SearchEngine("google", "Google", "https://www.google.com/search?q="),
        SearchEngine("wikipedia", "维基百科", "https://zh.wikipedia.org/wiki/"),
        youdaoDictionary,
        SearchEngine("bing-dictionary", "必应词典", "https://www.bing.com/dict/search?q="),
        SearchEngine("iciba", "金山词霸", "https://www.iciba.com/word?w="),
        youdaoTranslate,
        SearchEngine("baidu-encyclopedia", "百度百科", "https://baike.baidu.com/item/"),
    )

    fun defaultForType(type: Int): SearchEngine =
        when (type) {
            BoomActionHandler.SEARCH_DICTIONARY -> youdaoDictionary
            BoomActionHandler.SEARCH_TRANSLATE -> youdaoTranslate
            else -> baidu
        }

    fun destination(engine: SearchEngine, input: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return null

        if (looksLikeAddress(value)) {
            return normalizeHttpsAddress("https://$value")
        }
        if (SCHEME_PREFIX.containsMatchIn(value)) {
            return normalizeHttpsAddress(value)
        }
        return engine.searchUrl(value)
    }

    private fun normalizeHttpsAddress(value: String): String? = try {
        val uri = URI(value).normalize()
        if (!uri.scheme.equals("https", ignoreCase = true)
            || uri.host.isNullOrBlank()
            || uri.rawUserInfo != null
        ) {
            null
        } else {
            uri.toASCIIString()
        }
    } catch (_: URISyntaxException) {
        null
    }

    private fun looksLikeAddress(value: String): Boolean {
        if (value.any(Char::isWhitespace)) return false
        val host = value.substringBefore('/')
        return host.startsWith("www.", ignoreCase = true)
            || host.count { it == '.' } >= 1
    }

    private val SCHEME_PREFIX = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")
}

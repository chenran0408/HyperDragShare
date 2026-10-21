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

    fun destination(engine: SearchEngine, input: String, directUrls: Boolean = true): String? {
        val value = input.trim()
        if (value.isEmpty()) return null
        if (!directUrls) return engine.searchUrl(value)

        CopyDirectText.findHttpUrl(value)?.let {
            return normalizeHttpsAddress(it)
        }

        val scheme = SCHEME_PREFIX.find(value)?.groupValues?.get(1)?.lowercase()
        if (scheme == "http" || scheme == "https") {
            return normalizeHttpsAddress(value)
        }

        bareHttpsAddress(value)?.let {
            return it
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

    private fun bareHttpsAddress(value: String): String? {
        if (value.any { Character.isWhitespace(it) || Character.isSpaceChar(it) }
            || '@' in value
        ) return null
        val authority = value.substringBefore('/').substringBefore('?').substringBefore('#')
        if (!authority.startsWith("www.", ignoreCase = true)
            && authority.count { it == '.' } < 1
        ) return null
        val destination = normalizeHttpsAddress("https://$value") ?: return null
        val host = URI(destination).host.orEmpty()
        if (host.substringAfterLast('.').all(Char::isDigit)) return null
        val lastLabel = host.substringAfterLast('.').lowercase()
        if (lastLabel in FILE_EXTENSIONS) return null
        return destination
    }

    private val SCHEME_PREFIX = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")
    private val FILE_EXTENSIONS = setOf(
        "apk", "doc", "docx", "gif", "jpeg", "jpg", "json", "md", "mp3", "mp4",
        "pdf", "png", "ppt", "pptx", "rar", "txt", "webp", "xls", "xlsx", "xml", "zip",
    )
}

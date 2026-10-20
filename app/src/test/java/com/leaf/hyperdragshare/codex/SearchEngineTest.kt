package com.leaf.hyperdragshare.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {
    @Test
    fun searchAndDictionaryActionsUseRelevantDefaults() {
        assertEquals("baidu", SearchEngineCatalog.defaultForType(BoomActionHandler.SEARCH_WEB).id)
        assertEquals(
            "youdao-dictionary",
            SearchEngineCatalog.defaultForType(BoomActionHandler.SEARCH_DICTIONARY).id,
        )
    }

    @Test
    fun translationActionDefaultsToTranslationEngine() {
        val engine = SearchEngineCatalog.defaultForType(BoomActionHandler.SEARCH_TRANSLATE)

        assertEquals("youdao-translate", engine.id)
        assertEquals(
            "https://fanyi.youdao.com/?keyfrom=dict2.index&i=hello%20world",
            SearchEngineCatalog.destination(engine, "hello world"),
        )
    }

    @Test
    fun queryIsUtf8EncodedWithoutChangingTheSelectedEngine() {
        val engine = SearchEngineCatalog.defaultForType(BoomActionHandler.SEARCH_WEB)

        assertEquals(
            "https://www.baidu.com/s?wd=%E4%B8%AD%E6%96%87%20hello%2Bworld",
            engine.searchUrl("中文 hello+world"),
        )
    }

    @Test
    fun explicitHttpsAddressIsOpenedDirectly() {
        val engine = SearchEngineCatalog.defaultForType(BoomActionHandler.SEARCH_WEB)

        assertEquals(
            "https://example.com/path?q=hello%20world",
            SearchEngineCatalog.destination(engine, "https://example.com/path?q=hello%20world"),
        )
        assertEquals(
            "https://example.com/path",
            SearchEngineCatalog.destination(engine, "example.com/path"),
        )
        assertEquals(
            "https://example.com:8443/path",
            SearchEngineCatalog.destination(engine, "example.com:8443/path"),
        )
    }

    @Test
    fun nonHttpsSchemesAndCredentialedUrlsAreRejected() {
        val engine = SearchEngineCatalog.defaultForType(BoomActionHandler.SEARCH_WEB)

        assertNull(SearchEngineCatalog.destination(engine, "http://example.com"))
        assertNull(SearchEngineCatalog.destination(engine, "javascript:alert(1)"))
        assertNull(SearchEngineCatalog.destination(engine, "https://user@example.com"))
    }

    @Test
    fun plainTextUsesTheSelectedSearchEngine() {
        val engine = SearchEngineCatalog.engines.first { it.id == "bing" }

        assertEquals(
            "https://www.bing.com/search?q=hello%20world",
            SearchEngineCatalog.destination(engine, "  hello world  "),
        )
        assertTrue(SearchEngineCatalog.engines.size >= 8)
    }
}

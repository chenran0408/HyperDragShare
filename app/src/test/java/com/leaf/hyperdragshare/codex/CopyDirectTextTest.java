package com.leaf.hyperdragshare.codex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class CopyDirectTextTest {
    @Test
    public void rebuildsUrlFromSeparatedChips() {
        assertEquals(
                "https://github.com/chenran0408/HyperDragShare",
                CopyDirectText.findHttpUrl(
                        "https : / / github . com / chenran0408 / HyperDragShare，我 git 都没上去过"));
    }

    @Test
    public void keepsUrlQueryAndExtractsItFromSurroundingText() {
        assertEquals(
                "https://example.com/search?q=bigbang&view=all",
                CopyDirectText.findHttpUrl(
                        "打开 https://example.com/search?q=bigbang&view=all。 再返回"));
    }

    @Test
    public void doesNotAppendFollowingEnglishWords() {
        assertEquals(
                "https://github.com/chenran0408/HyperDragShare",
                CopyDirectText.findHttpUrl(
                        "https : / / github . com / chenran0408 / HyperDragShare then open"));
    }

    @Test
    public void keepsUnicodePathAndQuery() {
        assertEquals(
                "https://example.com/中文?q=值",
                CopyDirectText.findHttpUrl("https://example.com/中文?q=值"));
    }

    @Test
    public void rebuildsEmailFromSeparatedChips() {
        assertEquals(
                "name@example.com",
                CopyDirectText.findEmail("联系 name @ example . com 获取帮助"));
        assertEquals("name@example.com", CopyDirectText.normalizeEmail(" name @ example . com "));
    }

    @Test
    public void returnsNullWithoutHttpUrl() {
        assertNull(CopyDirectText.findHttpUrl("普通文本"));
        assertNull(CopyDirectText.findHttpUrl(null));
        assertNull(CopyDirectText.findEmail("普通文本"));
    }
}

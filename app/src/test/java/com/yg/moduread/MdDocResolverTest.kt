package com.yg.moduread

import com.yg.moduread.data.MdDocResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 只测纯字符串部分（normalize / isRelativeDocLink / isMarkdownPath）。
 * 真正的 SAF 解析需要 Context，用真机验证。
 */
class MdDocResolverTest {

    @Test
    fun normalizesDotSlash() {
        assertEquals("ch2.md", MdDocResolver.normalize("./ch2.md"))
        assertEquals("a/b.md", MdDocResolver.normalize("./a/./b.md"))
    }

    @Test
    fun collapsesParentSegments() {
        assertEquals("b.md", MdDocResolver.normalize("a/../b.md"))
        assertEquals("x/b.md", MdDocResolver.normalize("x/y/../b.md"))
        // 过多的 .. 不应把路径搞成负数深度
        assertEquals("b.md", MdDocResolver.normalize("../../b.md"))
    }

    @Test
    fun dropsQueryAndFragment() {
        assertEquals("ch2.md", MdDocResolver.normalize("ch2.md#section"))
        assertEquals("ch2.md", MdDocResolver.normalize("ch2.md?raw=1"))
    }

    @Test
    fun relativeDocLinkRejectsExternalSchemes() {
        assertFalse(MdDocResolver.isRelativeDocLink("https://example.com/a.md"))
        assertFalse(MdDocResolver.isRelativeDocLink("http://example.com"))
        assertFalse(MdDocResolver.isRelativeDocLink("mailto:a@b.c"))
        assertFalse(MdDocResolver.isRelativeDocLink("#anchor"))
        assertFalse(MdDocResolver.isRelativeDocLink("content://x/y"))
        assertFalse(MdDocResolver.isRelativeDocLink("file:///sdcard/a.md"))
        assertFalse(MdDocResolver.isRelativeDocLink(""))
    }

    @Test
    fun relativeDocLinkAcceptsPlainPaths() {
        assertTrue(MdDocResolver.isRelativeDocLink("./ch2.md"))
        assertTrue(MdDocResolver.isRelativeDocLink("chapters/ch3.md"))
        assertTrue(MdDocResolver.isRelativeDocLink("../README.md"))
    }

    @Test
    fun onlyMarkdownExtensionsAccepted() {
        assertTrue(MdDocResolver.isMarkdownPath("ch2.md"))
        assertTrue(MdDocResolver.isMarkdownPath("a/B.MD"))
        assertTrue(MdDocResolver.isMarkdownPath("x.markdown"))
        assertFalse(MdDocResolver.isMarkdownPath("pic.png"))
        assertFalse(MdDocResolver.isMarkdownPath("notes.txt"))
        assertFalse(MdDocResolver.isMarkdownPath("archive.zip"))
    }

    @Test
    fun extensionCheckIgnoresFragmentAndQuery() {
        assertTrue(MdDocResolver.isMarkdownPath("ch2.md#sec"))
        assertTrue(MdDocResolver.isMarkdownPath("ch2.md?x=1"))
        assertFalse(MdDocResolver.isMarkdownPath("ch2.md.png"))
    }

    @Test
    fun emptyRelativePathNormalizesToEmpty() {
        assertEquals("", MdDocResolver.normalize("./"))
        assertEquals("", MdDocResolver.normalize(""))
    }
}

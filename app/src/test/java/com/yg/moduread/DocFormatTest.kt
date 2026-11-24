package com.yg.moduread

import com.yg.moduread.format.DocFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocFormatTest {

    @Test
    fun detectsByExtension() {
        assertEquals(DocFormat.MARKDOWN, DocFormat.ofExtension("a.md"))
        assertEquals(DocFormat.TXT, DocFormat.ofExtension("a.txt"))
        assertEquals(DocFormat.EPUB, DocFormat.ofExtension("a.epub"))
        assertEquals(DocFormat.DOCX, DocFormat.ofExtension("a.docx"))
        assertEquals(DocFormat.ODT, DocFormat.ofExtension("a.odt"))
        assertEquals(DocFormat.PDF, DocFormat.ofExtension("a.pdf"))
        assertEquals(DocFormat.CSV, DocFormat.ofExtension("a.csv"))
        assertEquals(DocFormat.JSON, DocFormat.ofExtension("a.json"))
    }

    @Test
    fun extensionIsCaseInsensitive() {
        assertEquals(DocFormat.EPUB, DocFormat.ofExtension("BOOK.EPUB"))
        assertEquals(DocFormat.PDF, DocFormat.ofExtension("X.Pdf"))
    }

    @Test
    fun unknownExtensionReturnsNull() {
        assertNull(DocFormat.ofExtension("a.mobi"))
        assertNull(DocFormat.ofExtension("a.xyz"))
        assertNull(DocFormat.ofExtension("noext"))
        assertNull(DocFormat.ofExtension(null))
    }

    @Test
    fun detectsByMime() {
        assertEquals(DocFormat.EPUB, DocFormat.ofMime("application/epub+zip"))
        assertEquals(DocFormat.PDF, DocFormat.ofMime("application/pdf"))
        assertEquals(DocFormat.DOCX, DocFormat.ofMime(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
    }

    @Test
    fun extensionWinsOverMime() {
        // 文件管理器常把所有未知类型报成 octet-stream，扩展名更可信
        assertEquals(DocFormat.EPUB, DocFormat.detect("book.epub", "application/octet-stream"))
        assertEquals(DocFormat.PDF, DocFormat.detect("doc.pdf", "application/octet-stream"))
    }

    @Test
    fun fallsBackToMarkdownWhenNothingKnown() {
        assertEquals(DocFormat.MARKDOWN, DocFormat.detect(null, null))
        assertEquals(DocFormat.MARKDOWN, DocFormat.detect("weird.zzz", null))
    }

    @Test
    fun containerFormatsAreFlagged() {
        assertTrue(DocFormat.EPUB.isContainer)
        assertTrue(DocFormat.DOCX.isContainer)
        assertTrue(DocFormat.ODT.isContainer)
        assertTrue(!DocFormat.TXT.isContainer)
    }

    @Test
    fun detectsSourceCodeFiles() {
        assertEquals(DocFormat.CODE, DocFormat.ofExtension("Main.kt"))
        assertEquals(DocFormat.CODE, DocFormat.ofExtension("a.java"))
        assertEquals(DocFormat.CODE, DocFormat.ofExtension("run.py"))
        assertEquals(DocFormat.CODE, DocFormat.ofExtension("app.js"))
        assertEquals(DocFormat.CODE, DocFormat.ofExtension("x.go"))
        assertEquals(DocFormat.CODE, DocFormat.ofExtension("y.rs"))
        assertEquals(DocFormat.CODE, DocFormat.ofExtension("z.cpp"))
        assertEquals(DocFormat.CODE, DocFormat.ofExtension("s.sh"))
    }

    @Test
    fun codeFilesAreNotContainer() {
        assertTrue(!DocFormat.CODE.isContainer)
        assertTrue(DocFormat.CODE.supportsSearch)
    }
}

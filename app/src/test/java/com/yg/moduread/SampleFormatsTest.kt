package com.yg.moduread

import com.yg.moduread.format.ContainerAdapters
import com.yg.moduread.format.DocFormat
import com.yg.moduread.format.PdfAdapter
import com.yg.moduread.format.TextAdapters
import com.yg.moduread.md.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 端到端：用 sample/formats 下的真实文件跑解析链。找不到目录时跳过。 */
class SampleFormatsTest {

    private fun sample(name: String): ByteArray? {
        val dirs = listOf("../sample/formats", "sample/formats")
        for (d in dirs) {
            val f = File(d, name)
            if (f.exists()) return f.readBytes()
        }
        return null
    }

    @Test
    fun extensionDetectionCoversAllSamples() {
        val cases = listOf(
            "note.txt" to DocFormat.TXT,
            "data.csv" to DocFormat.CSV,
            "config.json" to DocFormat.JSON,
            "app.ini" to DocFormat.INI,
            "sub.srt" to DocFormat.SUBTITLE,
            "page.html" to DocFormat.HTML,
            "book.epub" to DocFormat.EPUB,
            "doc.docx" to DocFormat.DOCX,
            "doc.pdf" to DocFormat.PDF
        )
        for (c in cases) {
            assertEquals(c.first, c.second, DocFormat.ofExtension(c.first))
        }
    }

    @Test
    fun parsesRealTxtSample() {
        val bytes = sample("note.txt") ?: return
        val doc = TextAdapters.txt(String(bytes, Charsets.UTF_8))
        assertTrue(doc.blocks.isNotEmpty())
        assertTrue(doc.blocks.any { it is MdBlock.Heading })
    }

    @Test
    fun parsesRealCsvSample() {
        val bytes = sample("data.csv") ?: return
        val doc = TextAdapters.csv(String(bytes, Charsets.UTF_8))
        val table = doc.blocks.filterIsInstance<MdBlock.Table>().single()
        assertEquals(3, table.head.size)
        assertEquals(2, table.rows.size)
    }

    @Test
    fun parsesRealJsonSample() {
        val bytes = sample("config.json") ?: return
        val doc = TextAdapters.json(String(bytes, Charsets.UTF_8))
        val code = doc.blocks.filterIsInstance<MdBlock.CodeBlock>().single()
        assertTrue(code.code.contains("{"))
    }

    @Test
    fun parsesRealIniSample() {
        val bytes = sample("app.ini") ?: return
        val doc = TextAdapters.ini(String(bytes, Charsets.UTF_8))
        assertEquals(2, doc.blocks.filterIsInstance<MdBlock.Heading>().size)
    }

    @Test
    fun parsesRealEpubSample() {
        val bytes = sample("book.epub") ?: return
        val doc = ContainerAdapters.epub(bytes)
        assertTrue(doc.blocks.isNotEmpty())
        assertTrue(doc.blocks.any { it is MdBlock.Heading })
        assertTrue(doc.plainText.contains("EPUB"))
    }

    @Test
    fun parsesRealDocxSample() {
        val bytes = sample("doc.docx") ?: return
        val doc = ContainerAdapters.docx(bytes)
        assertTrue(doc.blocks.isNotEmpty())
        assertTrue(doc.blocks.any { it is MdBlock.Heading })
        assertTrue(doc.blocks.any { it is MdBlock.Table })
    }

    @Test
    fun parsesRealPdfSample() {
        val bytes = sample("doc.pdf") ?: return
        // 默认不提取文字层（打开文档只需页数与目录，提取太慢）
        val quick = PdfAdapter.inspect(bytes)
        assertEquals(2, quick.pageCount)
        assertTrue(quick.outline.isNotEmpty())
        assertTrue("默认应跳过文字提取", !quick.hasTextLayer)

        // 显式请求时才做全文提取
        val full = PdfAdapter.inspect(bytes, withText = true)
        assertTrue("显式请求后应有文字层", full.hasTextLayer)
    }
}

package com.yg.moduread

import com.yg.moduread.format.PdfAdapter
import com.yg.moduread.md.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/**
 * PDF 测试只覆盖纯逻辑部分（字符串解码、页数估算、文字提取）。
 * 真正的位图渲染依赖 PdfRenderer，需要真机/模拟器，不在 JVM 单测范围内。
 */
class PdfAdapterTest {

    @Test
    fun decodesEscapedPdfString() {
        assertEquals("a(b)c", PdfAdapter.decodePdfString("a\\(b\\)c"))
        assertEquals("back\\slash", PdfAdapter.decodePdfString("back\\\\slash"))
        assertEquals("line\nbreak", PdfAdapter.decodePdfString("line\\nbreak"))
    }

    @Test
    fun countsPagesFromTypePageMarkers() {
        val pdf = "%PDF-1.4\n1 0 obj <</Type /Page>> endobj\n2 0 obj <</Type /Page>> endobj"
        assertEquals(2, PdfAdapter.inspect(pdf.toByteArray(Charsets.ISO_8859_1)).pageCount)
    }

    @Test
    fun flagsEncryptedDocuments() {
        val pdf = "%PDF-1.4\n1 0 obj <</Encrypt 5 0 R /Type /Page>> endobj"
        assertTrue(PdfAdapter.inspect(pdf.toByteArray(Charsets.ISO_8859_1)).encrypted)
    }

    @Test
    fun extractsLiteralStringsFromContentStream() {
        // 只测内容流里的 Tj 提取：这是文字搜索的核心逻辑，与压缩格式无关
        val content = "BT /F1 12 Tf (Hello PDF) Tj ET"
        val got = PdfAdapter.extractStringsFromContent(content)
        assertEquals("GOT=[" + got + "]", "Hello PDF", got)
    }

    @Test
    fun extractsStringsFromTjArray() {
        val content = "BT [(Hel) -120 (lo)] TJ ET"
        assertTrue(PdfAdapter.extractStringsFromContent(content).contains("Hello"))
    }

    @Test
    fun parseProducesOneBlockPerPage() {
        val pdf = "%PDF-1.4\n1 0 obj <</Type /Page>> endobj".toByteArray(Charsets.ISO_8859_1)
        val doc = PdfAdapter.parse(pdf)
        val pages = doc.blocks.filterIsInstance<MdBlock.PdfPage>()
        assertEquals(1, pages.size)
        assertEquals(0, pages.first().pageIndex)
    }

    @Test
    fun parseAddsOutlineAsHeadings() {
        val pdf = ("%PDF-1.4\n1 0 obj <</Type /Page>> endobj\n" +
            "2 0 obj <</Outlines 3 0 R>> endobj\n" +
            "3 0 obj <</Title (Chapter One)>> endobj").toByteArray(Charsets.ISO_8859_1)
        val doc = PdfAdapter.parse(pdf)
        assertTrue("应把 /Title 变成标题块", doc.blocks.any { it is MdBlock.Heading })
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            out.write(buf, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    @Test
    fun parsesToUnicodeBfchar() {
        // 真实 PDF 的 CMap 片段：把子集码 1 映射到汉字「一」
        val cmap = "beginbfchar\n<0001> <4E00>\n<0002> <4E8C>\nendbfchar"
        val map = PdfAdapter.parseToUnicode(cmap)
        assertEquals("一", map[1])
        assertEquals("二", map[2])
    }

    @Test
    fun parsesToUnicodeBfrange() {
        // bfrange：从 0 开始连续 4 个码映射到 ASCII 的 p q r s
        val cmap = "beginbfrange\n<0000> <0003> <0070>\nendbfrange"
        val map = PdfAdapter.parseToUnicode(cmap)
        assertEquals("p", map[0])
        assertEquals("s", map[3])
    }

    @Test
    fun emptyCmapYieldsEmptyMap() {
        assertTrue(PdfAdapter.parseToUnicode("").isEmpty())
    }

    @Test
    fun decodesCjkTextThroughCmap() {
        // CJK 子集编码：内容流里是 2 字节码，CMap 把它们映射成汉字
        val cmap = mapOf(((1 shl 8) or 2) to "红外")
        val raw = "" + 1.toChar() + 2.toChar()
        assertEquals("红外", PdfAdapter.decodeText(raw, cmap))
    }

    @Test
    fun cjkTextIsExtractedFromContentStream() {
        val cmap = mapOf(((1 shl 8) or 2) to "目标")
        val content = "BT (" + 1.toChar() + 2.toChar() + ") Tj ET"
        val got = PdfAdapter.extractStringsFromContent(content, cmap)
        assertTrue("should contain 目标, got=[" + got + "]", got.contains("目标"))
    }

    @Test
    fun withoutCmapFallsBackToLatin1() {
        val content = "BT (Plain ASCII) Tj ET"
        assertEquals("Plain ASCII", PdfAdapter.extractStringsFromContent(content, emptyMap()))
    }

    @Test
    fun mergeToUnicodeCombinesMultipleFonts() {
        val a = "beginbfchar<0001> <4E00>endbfchar"
        val b = "beginbfchar<0002> <4E8C>endbfchar"
        val merged = PdfAdapter.mergeToUnicode(listOf(a, b))
        assertEquals("一", merged[1])
        assertEquals("二", merged[2])
    }
}

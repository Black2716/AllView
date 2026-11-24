package com.yg.moduread

import com.yg.moduread.data.DocRepository
import com.yg.moduread.format.ContainerAdapters
import com.yg.moduread.format.DocFormat
import com.yg.moduread.format.PdfAdapter
import com.yg.moduread.md.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 四个用户实测故障的端到端回归。 */
class FormatRegressionsTest {

    private fun zipOf(m: Map<String, String>): ByteArray {
        val o = ByteArrayOutputStream()
        ZipOutputStream(o).use { z ->
            for ((n, c) in m) { z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() }
        }
        return o.toByteArray()
    }

    // ── 1. .doc 乱码 ─────────────────────────────────────────

    @Test
    fun legacyDocIsNotParsedAsMarkdown() {
        // .doc 是二进制；若落到 MARKDOWN 会按 UTF-8 读二进制 -> 满屏乱码
        assertEquals(DocFormat.DOC_LEGACY, DocFormat.ofExtension("制度.doc"))
    }

    @Test
    fun docxStillWorks() {
        assertEquals(DocFormat.DOCX, DocFormat.ofExtension("政审表.docx"))
    }

    // ── 2. 标题乱码 ──────────────────────────────────────────

    @Test
    fun titleStripsReplacementAndInvisibleChars() {
        assertEquals("abc", DocRepository.sanitizeTitle("a\u0000b\u200Bc"))
        assertEquals("考试大纲.pdf", DocRepository.sanitizeTitle("\uFFFD考试大纲.pdf"))
    }

    @Test
    fun titleFallsBackWhenAllGarbage() {
        assertEquals("未命名文档", DocRepository.sanitizeTitle("\uFFFD\uFFFD"))
        assertEquals("未命名文档", DocRepository.sanitizeTitle("   "))
    }

    // ── 3. 单页 PDF 多出第二页 ───────────────────────────────

    @Test
    fun singlePagePdfHasExactlyOnePageWithAuthoritativeSizes() {
        val pdf = ("%PDF-1.4\n" +
            "1 0 obj <</Type /Catalog /Pages 2 0 R>> endobj\n" +
            "2 0 obj <</Type /Pages /Kids [3 0 R] /Count 1>> endobj\n" +
            "3 0 obj <</Type /Page /Parent 2 0 R>> endobj\n").toByteArray(Charsets.ISO_8859_1)
        // 传入 PdfRenderer 的权威值：1 页
        val doc = PdfAdapter.parse(pdf, listOf(595 to 842))
        val pages = doc.blocks.filterIsInstance<MdBlock.PdfPage>()
        assertEquals("传入 1 个尺寸就必须只生成 1 页", 1, pages.size)
    }

    @Test
    fun authoritativePageSizeIsUsed() {
        val pdf = "%PDF-1.4\n1 0 obj <</Type /Page>> endobj".toByteArray(Charsets.ISO_8859_1)
        val doc = PdfAdapter.parse(pdf, listOf(1190 to 1684))
        val page = doc.blocks.filterIsInstance<MdBlock.PdfPage>().single()
        assertEquals(1190, page.widthPoints)
        assertEquals(1684, page.heightPoints)
    }

    // ── 4. DOCX 列表散架 ─────────────────────────────────────

    @Test
    fun docxNumberedListBecomesListBlock() {
        val body = "<w:p><w:pPr><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr></w:pPr><w:r><w:t>第一项</w:t></w:r></w:p>" +
            "<w:p><w:pPr><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"1\"/></w:numPr></w:pPr><w:r><w:t>第二项</w:t></w:r></w:p>" +
            "<w:p><w:r><w:t>普通段落</w:t></w:r></w:p>"
        val docx = zipOf(mapOf(
            "[Content_Types].xml" to "<?xml version=\"1.0\"?><Types/>",
            "word/document.xml" to "<?xml version=\"1.0\"?><w:document><w:body>" + body + "</w:body></w:document>"
        ))
        val doc = ContainerAdapters.docx(docx)
        val lists = doc.blocks.filterIsInstance<MdBlock.ListBlock>()
        assertEquals("应生成 1 个列表块", 1, lists.size)
        assertEquals("列表应有 2 项", 2, lists.first().items.size)
        assertTrue("普通段落不应被并入列表", doc.blocks.any { it is MdBlock.Para })
    }

    @Test
    fun docxPlainParagraphsStayParagraphs() {
        val docx = zipOf(mapOf(
            "[Content_Types].xml" to "<?xml version=\"1.0\"?><Types/>",
            "word/document.xml" to "<?xml version=\"1.0\"?><w:document><w:body>" +
                "<w:p><w:r><w:t>A</w:t></w:r></w:p><w:p><w:r><w:t>B</w:t></w:r></w:p>" +
                "</w:body></w:document>"
        ))
        val doc = ContainerAdapters.docx(docx)
        assertEquals(2, doc.blocks.filterIsInstance<MdBlock.Para>().size)
        assertTrue(doc.blocks.none { it is MdBlock.ListBlock })
    }


    // ── 5. 不支持的格式不能崩 ───────────────────────────────

    @Test
    fun unknownFormatIsNotMarkdown() {
        // 关键回归：以前兜底成 MARKDOWN，二进制被当文本读 -> 崩溃
        assertEquals(DocFormat.UNSUPPORTED, DocFormat.detect("book.mobi", null))
        assertEquals(DocFormat.UNSUPPORTED, DocFormat.detect("x.azw3", null))
        assertEquals(DocFormat.UNSUPPORTED, DocFormat.detect("y.djvu", null))
        assertEquals(DocFormat.UNSUPPORTED, DocFormat.detect("noext", null))
        assertTrue(DocFormat.UNSUPPORTED != DocFormat.MARKDOWN)
    }

    @Test
    fun knownFormatsStillDetected() {
        // 兜底改为 UNSUPPORTED 不能误伤已支持的格式
        assertEquals(DocFormat.MARKDOWN, DocFormat.detect("a.md", null))
        assertEquals(DocFormat.TXT, DocFormat.detect("a.txt", null))
        assertEquals(DocFormat.EPUB, DocFormat.detect("a.epub", null))
        assertEquals(DocFormat.PDF, DocFormat.detect("a.pdf", null))
        assertEquals(DocFormat.CODE, DocFormat.detect("a.kt", null))
    }

    @Test
    fun unsupportedByMimeAloneAlsoFallsBack() {
        // 只给 MIME 且不认识时同样不能当 Markdown
        assertEquals(DocFormat.UNSUPPORTED, DocFormat.detect(null, "application/x-mobipocket-ebook"))
    }
}

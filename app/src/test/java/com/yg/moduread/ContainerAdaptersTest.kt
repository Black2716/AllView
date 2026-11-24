package com.yg.moduread

import com.yg.moduread.format.ContainerAdapters
import com.yg.moduread.format.ContainerSupport
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdInline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 容器格式测试。
 *
 * DOCX/EPUB 样本在测试内用 ZipOutputStream 现场生成 ——
 * 仓库里不放任何二进制 fixture，测试自己造最小可解析的包。
 */
class ContainerAdaptersTest {

    private fun zipOf(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun textOf(block: MdBlock): String {
        val p = block as? MdBlock.Para ?: return ""
        return p.content.joinToString("") { (it as? MdInline.Text)?.text ?: "" }
    }

    // ── DOCX ─────────────────────────────────────────────────────

    private fun minimalDocx(body: String): ByteArray = zipOf(
        mapOf(
            "[Content_Types].xml" to "<?xml version=\"1.0\"?><Types/>",
            "word/document.xml" to
                "<?xml version=\"1.0\"?><w:document><w:body>" + body + "</w:body></w:document>"
        )
    )

    @Test
    fun docxParsesParagraphs() {
        val docx = minimalDocx(
            "<w:p><w:r><w:t>First</w:t></w:r></w:p>" +
            "<w:p><w:r><w:t>Second</w:t></w:r></w:p>"
        )
        val doc = ContainerAdapters.docx(docx)
        val paras = doc.blocks.filterIsInstance<MdBlock.Para>()
        assertEquals(2, paras.size)
        assertEquals("First", textOf(paras[0]))
    }

    @Test
    fun docxRecognizesHeadingStyle() {
        val docx = minimalDocx(
            "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:t>Title</w:t></w:r></w:p>"
        )
        val doc = ContainerAdapters.docx(docx)
        assertTrue(doc.blocks.any { it is MdBlock.Heading })
    }

    @Test
    fun headingLevelOfAcceptsSeveralSpellings() {
        assertEquals(1, ContainerAdapters.headingLevelOf("Heading1"))
        assertEquals(2, ContainerAdapters.headingLevelOf("heading 2"))
        assertEquals(3, ContainerAdapters.headingLevelOf("标题3"))
        assertEquals(0, ContainerAdapters.headingLevelOf("Normal"))
        assertEquals(0, ContainerAdapters.headingLevelOf(""))
    }

    @Test
    fun docxMissingDocumentXmlDegrades() {
        val doc = ContainerAdapters.docx(zipOf(mapOf("nothing.txt" to "x")))
        assertTrue(doc.blocks.isNotEmpty())
        assertTrue(doc.blocks.first() is MdBlock.Para)
    }

    // ── EPUB ─────────────────────────────────────────────────────

    private fun minimalEpub(chapters: List<Pair<String, String>>, ncxTitles: List<String>): ByteArray {
        val entries = LinkedHashMap<String, String>()
        val manifest = StringBuilder()
        val spine = StringBuilder()
        chapters.forEachIndexed { i, (name, html) ->
            entries["OEBPS/" + name] = "<?xml version=\"1.0\"?><html><body>" + html + "</body></html>"
            manifest.append("<item id=\"c").append(i).append("\" href=\"").append(name).append("\"/>")
            spine.append("<itemref idref=\"c").append(i).append("\"/>")
        }
        entries["OEBPS/content.opf"] = "<?xml version=\"1.0\"?><package><manifest>" +
            manifest + "</manifest><spine>" + spine + "</spine></package>"
        if (ncxTitles.isNotEmpty()) {
            val navs = ncxTitles.joinToString("") { t ->
                "<navPoint><text>" + t + "</text></navPoint>"
            }
            entries["OEBPS/toc.ncx"] = "<?xml version=\"1.0\"?><ncx><navMap>" + navs + "</navMap></ncx>"
        }
        return zipOf(entries)
    }

    @Test
    fun epubReadsChaptersInSpineOrder() {
        val epub = minimalEpub(
            listOf("ch1.xhtml" to "<p>One</p>", "ch2.xhtml" to "<p>Two</p>"),
            listOf("第一章")
        )
        val doc = ContainerAdapters.epub(epub)
        val texts = doc.blocks.filterIsInstance<MdBlock.Para>().map { textOf(it) }
        assertTrue("应在正文中找到 One", texts.any { it.contains("One") })
        assertTrue("应在正文中找到 Two", texts.any { it.contains("Two") })
    }

    @Test
    fun epubPullsOutlineIntoHeadings() {
        val epub = minimalEpub(
            listOf("ch1.xhtml" to "<p>body</p>"),
            listOf("绪论", "方法")
        )
        val doc = ContainerAdapters.epub(epub)
        val headings = doc.blocks.filterIsInstance<MdBlock.Heading>()
        assertTrue("应插入 NCX 目录标题", headings.isNotEmpty())
        val joined = headings.joinToString { (it.content.first() as MdInline.Text).text }
        assertTrue(joined.contains("绪论"))
    }

    @Test
    fun epubWithoutOpfDegrades() {
        val doc = ContainerAdapters.epub(zipOf(mapOf("a.xhtml" to "<html><body><p>x</p></body></html>")))
        assertTrue(doc.blocks.isNotEmpty())
    }

    // ── HTML / ZIP 工具 ───────────────────────────────────────────

    @Test
    fun htmlExtractsHeadingsAndParagraphs() {
        val doc = ContainerAdapters.html("<html><body><h2>Title</h2><p>Body text</p></body></html>")
        assertTrue(doc.blocks.any { it is MdBlock.Heading })
        assertTrue(doc.blocks.any { it is MdBlock.Para })
    }

    @Test
    fun htmlSkipsScriptAndStyle() {
        val doc = ContainerAdapters.html("<html><body><script>var x=1</script><p>keep</p><style>.a{}</style></body></html>")
        val all = doc.plainText
        assertTrue(all.contains("keep"))
        assertTrue("脚本内容不该进正文", !all.contains("var x"))
    }

    @Test
    fun malformedHtmlFallsBackToText() {
        // 未闭合标签：XmlPullParser 会失败，应降级而不是抛异常
        val doc = ContainerAdapters.html("<html><body><p>Unclosed <b>bold<p>next")
        assertTrue(doc.blocks.isNotEmpty())
    }

    @Test
    fun normalizePathCollapsesDotsAndSlashes() {
        assertEquals("a/b.png", ContainerSupport.normalizePath("./a/../a/b.png"))
        assertEquals("img/1.jpg", ContainerSupport.normalizePath("/img/1.jpg"))
        assertEquals("b.txt", ContainerSupport.normalizePath("a/../b.txt"))
    }

    @Test
    fun siblingPathResolvesRelativeToChapter() {
        assertEquals("OEBPS/img/x.png", ContainerSupport.siblingPath("OEBPS/ch1.xhtml", "img/x.png"))
        assertEquals("img/x.png", ContainerSupport.siblingPath("ch1.xhtml", "img/x.png"))
        // 外链不当作包内资源
        assertEquals("", ContainerSupport.siblingPath("ch1.xhtml", "https://e.com/x.png"))
    }
}

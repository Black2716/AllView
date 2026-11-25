package com.yg.moduread

import com.yg.moduread.format.DocFormat
import com.yg.moduread.format.PptxAdapter
import com.yg.moduread.md.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** PPTX 解析回归。 */
class PptxAdapterTest {

    private fun zipOf(m: Map<String, ByteArray>): ByteArray {
        val o = ByteArrayOutputStream()
        ZipOutputStream(o).use { z ->
            for ((n, c) in m) { z.putNextEntry(ZipEntry(n)); z.write(c); z.closeEntry() }
        }
        return o.toByteArray()
    }

    private fun slide(title: String, body: String, imgRid: String? = null): ByteArray {
        val pic = if (imgRid == null) "" else
            "<p:pic><p:blipFill><a:blip r:embed=\"" + imgRid + "\"/></p:blipFill></p:pic>"
        return ("<?xml version=\"1.0\"?><p:sld><p:cSld><p:spTree>" +
            "<p:sp><p:txBody><a:p><a:r><a:t>" + title + "</a:t></a:r></a:p></p:txBody></p:sp>" +
            "<p:sp><p:txBody><a:p><a:r><a:t>" + body + "</a:t></a:r></a:p></p:txBody></p:sp>" +
            pic +
            "</p:spTree></p:cSld></p:sld>").toByteArray()
    }

    @Test
    fun detectsPptx() {
        assertEquals(DocFormat.PPTX, DocFormat.ofExtension("汇报.pptx"))
        assertTrue(DocFormat.PPTX.isContainer)
    }

    @Test
    fun legacyPptIsUnsupportedBinary() {
        // .ppt 是 97-2003 二进制，与 .pptx 完全不同，不能当文本读
        assertEquals(DocFormat.DOC_LEGACY, DocFormat.ofExtension("old.ppt"))
    }

    @Test
    fun parsesSlidesInNumericOrder() {
        // 字典序会把 slide10 排在 slide2 前面，必须按数字排
        val pptx = zipOf(
            linkedMapOf(
                "ppt/slides/slide1.xml" to slide("第一张", "内容一"),
                "ppt/slides/slide2.xml" to slide("第二张", "内容二"),
                "ppt/slides/slide10.xml" to slide("第十张", "内容十")
            )
        )
        val blocks = PptxAdapter.parse(pptx).blocks
        val titles = blocks.filterIsInstance<MdBlock.Heading>().map { h ->
            h.content.filterIsInstance<com.yg.moduread.md.MdInline.Text>().joinToString("") { it.text }
        }
        assertEquals(3, titles.size)
        assertTrue("第 1 页应在最前", titles[0].contains("第 1 页"))
        assertTrue("第 3 页（对应 slide10）应在最后", titles[2].contains("第 3 页"))
    }

    @Test
    fun extractsSlideText() {
        val pptx = zipOf(mapOf("ppt/slides/slide1.xml" to slide("季度汇报", "营收增长 20%")))
        val text = PptxAdapter.parse(pptx).plainText
        assertTrue(text.contains("季度汇报"))
        assertTrue(text.contains("营收增长 20%"))
    }

    @Test
    fun extractsSlideImages() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val rels = ("<?xml version=\"1.0\"?><Relationships>" +
            "<Relationship Id=\"rId2\" Target=\"../media/image1.png\"/></Relationships>").toByteArray()
        val pptx = zipOf(
            linkedMapOf(
                "ppt/slides/slide1.xml" to slide("带图页", "说明", "rId2"),
                "ppt/slides/_rels/slide1.xml.rels" to rels,
                "ppt/media/image1.png" to png
            )
        )
        val imgs = PptxAdapter.parse(pptx).blocks.filterIsInstance<MdBlock.EmbeddedImage>()
        assertEquals("幻灯片内的图片应被提取", 1, imgs.size)
    }

    @Test
    fun emptyPptxDegradesGracefully() {
        val pptx = zipOf(mapOf("docProps/app.xml" to "<a/>".toByteArray()))
        val doc = PptxAdapter.parse(pptx)
        assertTrue("无幻灯片时应给提示而不是崩溃", doc.blocks.isNotEmpty())
    }
}

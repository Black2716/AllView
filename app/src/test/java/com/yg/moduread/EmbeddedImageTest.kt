package com.yg.moduread

import com.yg.moduread.format.ContainerAdapters
import com.yg.moduread.md.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 含图片的 DOCX / ODT 回归测试。
 *
 * 用户反馈：有些 docx 带图片。
 * 根因是图片嵌在「段落 → run → drawing → blip」这条链里，
 * 而段落分支只取文字、不下探，导致图片静默丢失。
 */
class EmbeddedImageTest {

    private fun zipOf(m: Map<String, ByteArray>): ByteArray {
        val o = ByteArrayOutputStream()
        ZipOutputStream(o).use { z ->
            for ((n, c) in m) { z.putNextEntry(ZipEntry(n)); z.write(c); z.closeEntry() }
        }
        return o.toByteArray()
    }

    private val pngBytes = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
    )

    // ── DOCX ────────────────────────────────────────────────

    @Test
    fun docxImageInsideParagraphIsExtracted() {
        // 图片嵌在 w:p > w:r > w:drawing > a:blip，这才是 Word 的真实结构
        val body = "<w:p><w:r><w:t>图示如下：</w:t></w:r>" +
            "<w:r><w:drawing><a:blip r:embed=\"rId5\"/></w:drawing></w:r></w:p>"
        val rels = "<?xml version=\"1.0\"?><Relationships>" +
            "<Relationship Id=\"rId5\" Target=\"media/image1.png\"/></Relationships>"
        val docx = zipOf(
            linkedMapOf(
                "[Content_Types].xml" to "<?xml version=\"1.0\"?><Types/>".toByteArray(),
                "word/document.xml" to ("<?xml version=\"1.0\"?><w:document><w:body>" + body + "</w:body></w:document>").toByteArray(),
                "word/_rels/document.xml.rels" to rels.toByteArray(),
                "word/media/image1.png" to pngBytes
            )
        )
        val doc = ContainerAdapters.docx(docx)
        val imgs = doc.blocks.filterIsInstance<MdBlock.EmbeddedImage>()
        assertEquals("段落内的图片必须被提取", 1, imgs.size)
        assertTrue("图片字节应完整", imgs.first().bytes.size == pngBytes.size)
        assertTrue("文字也要保留", doc.blocks.any { it is MdBlock.Para })
    }

    @Test
    fun docxImageOrderFollowsText() {
        // 文字段落在前，含图段落在后：输出顺序应保持一致
        val body = "<w:p><w:r><w:t>第一段</w:t></w:r></w:p>" +
            "<w:p><w:r><w:drawing><a:blip r:embed=\"rId9\"/></w:drawing></w:r></w:p>"
        val rels = "<?xml version=\"1.0\"?><Relationships>" +
            "<Relationship Id=\"rId9\" Target=\"media/p.png\"/></Relationships>"
        val docx = zipOf(
            linkedMapOf(
                "[Content_Types].xml" to "<?xml version=\"1.0\"?><Types/>".toByteArray(),
                "word/document.xml" to ("<?xml version=\"1.0\"?><w:document><w:body>" + body + "</w:body></w:document>").toByteArray(),
                "word/_rels/document.xml.rels" to rels.toByteArray(),
                "word/media/p.png" to pngBytes
            )
        )
        val blocks = ContainerAdapters.docx(docx).blocks
        val firstPara = blocks.indexOfFirst { it is MdBlock.Para }
        val firstImg = blocks.indexOfFirst { it is MdBlock.EmbeddedImage }
        assertTrue("文字段应在图片之前", firstPara in 0 until firstImg)
    }

    @Test
    fun docxWithoutImagesProducesNoImageBlocks() {
        val docx = zipOf(
            linkedMapOf(
                "[Content_Types].xml" to "<?xml version=\"1.0\"?><Types/>".toByteArray(),
                "word/document.xml" to "<?xml version=\"1.0\"?><w:document><w:body><w:p><w:r><w:t>纯文字</w:t></w:r></w:p></w:body></w:document>".toByteArray()
            )
        )
        assertTrue(ContainerAdapters.docx(docx).blocks.none { it is MdBlock.EmbeddedImage })
    }

    // ── ODT ─────────────────────────────────────────────────

    @Test
    fun odtImageInsideParagraphIsExtracted() {
        // ODT: text:p > draw:frame > draw:image（xlink:href 指向 Pictures/）
        val content = "<?xml version=\"1.0\"?><office:document-content><office:body><office:text>" +
            "<text:p>图示：</text:p>" +
            "<text:p><draw:frame><draw:image xlink:href=\"Pictures/1000.png\"/></draw:frame></text:p>" +
            "</office:text></office:body></office:document-content>"
        val odt = zipOf(
            linkedMapOf(
                "content.xml" to content.toByteArray(),
                "Pictures/1000.png" to pngBytes
            )
        )
        val imgs = ContainerAdapters.odt(odt).blocks.filterIsInstance<MdBlock.EmbeddedImage>()
        assertEquals("ODT 段落内的图片必须被提取", 1, imgs.size)
    }
}

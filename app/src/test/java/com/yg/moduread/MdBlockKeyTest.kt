package com.yg.moduread

import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdInline
import com.yg.moduread.md.displayTitle
import com.yg.moduread.md.keyHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * LazyColumn key 与页首标题的提取规则。
 * 这两条规则从 ui 包移到 md 包后仍必须产生逐字相同的字符串 ——
 * key 变了会让滚动位置在文档更新时整体跳动。
 */
class MdBlockKeyTest {

    private fun para(text: String) = MdBlock.Para(listOf(MdInline.Text(text)))

    @Test
    fun headingHintCarriesLevelAndText() {
        val block = MdBlock.Heading(2, listOf(MdInline.Text("第一章 绪论")))
        assertEquals("h2:第一章 绪论", block.keyHint())
    }

    @Test
    fun hintsAreTruncatedTo24Chars() {
        val long = "一".repeat(40)
        assertEquals("p:" + "一".repeat(24), para(long).keyHint())
    }

    @Test
    fun differentContentProducesDifferentHints() {
        assertNotEquals(para("甲").keyHint(), para("乙").keyHint())
    }

    @Test
    fun structuralBlocksUseFixedMarkers() {
        assertEquals("r", MdBlock.Rule.keyHint())
        assertEquals("q", MdBlock.Quote(emptyList()).keyHint())
        assertEquals("pg:3", MdBlock.PdfPage(3, 100, 200).keyHint())
    }

    @Test
    fun codeBlockHintUsesCodeHead() {
        assertEquals("c:val x = 1", MdBlock.CodeBlock("val x = 1", "kotlin").keyHint())
    }

    @Test
    fun displayTitlePrefersTopLevelHeading() {
        val blocks = listOf(
            MdBlock.Heading(1, listOf(MdInline.Text("真正的标题"))),
            para("正文")
        )
        assertEquals("真正的标题", blocks.displayTitle("fallback.md"))
    }

    @Test
    fun displayTitleFallsBackWhenNoShallowHeading() {
        val blocks = listOf(MdBlock.Heading(3, listOf(MdInline.Text("太深"))), para("正文"))
        assertEquals("fallback.md", blocks.displayTitle("fallback.md"))
    }

    @Test
    fun displayTitleFallsBackOnBlankHeading() {
        val blocks = listOf(MdBlock.Heading(1, listOf(MdInline.Text("   "))))
        assertEquals("fallback.md", blocks.displayTitle("fallback.md"))
    }
}

package com.yg.moduread

import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdInline
import com.yg.moduread.md.MdParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MdParserTest {

    private val sample = """
        # 一级标题

        普通段落，含 **粗体**、*斜体* 与 `行内代码`，还有 [链接](https://example.com)。

        ## 二级标题

        - 无序 A
        - 无序 B
          - 嵌套 C

        1. 有序一
        2. 有序二

        > 引用第一行
        > 引用第二行

        ```kotlin
        val x = 1
        ```

        | 列1 | 列2 | 列3 |
        |:--|:-:|--:|
        | a | b | c |
        | d | e | f |

        ![图片说明](file:///sdcard/a.png)

        ---
    """.trimIndent()

    private val blocks = MdParser.shared.parse(sample)

    @Test
    fun headingsHaveLevels() {
        val headings = blocks.filterIsInstance<MdBlock.Heading>()
        assertEquals(listOf(1, 2), headings.map { it.level })
        assertEquals("一级标题", (headings[0].content.single() as MdInline.Text).text)
    }

    @Test
    fun paragraphKeepsInlineStyles() {
        val para = blocks.filterIsInstance<MdBlock.Para>().first()
        assertTrue(para.content.any { it is MdInline.Strong })
        assertTrue(para.content.any { it is MdInline.Emph })
        assertTrue(para.content.any { it is MdInline.Code })
        val link = para.content.filterIsInstance<MdInline.Link>().single()
        assertEquals("https://example.com", link.url)
    }

    @Test
    fun unorderedListNests() {
        val list = blocks.filterIsInstance<MdBlock.ListBlock>().first { !it.ordered }
        assertEquals(2, list.items.size)
        val nested = list.items[1].filterIsInstance<MdBlock.ListBlock>().first()
        assertEquals(1, nested.items.size)
    }

    @Test
    fun orderedListStartsAtOne() {
        val list = blocks.filterIsInstance<MdBlock.ListBlock>().first { it.ordered }
        assertEquals(1, list.start)
        assertEquals(2, list.items.size)
    }

    @Test
    fun quoteKeepsChildren() {
        val quote = blocks.filterIsInstance<MdBlock.Quote>().single()
        assertTrue(quote.children.first() is MdBlock.Para)
    }

    @Test
    fun fencedCodeKeepsLanguage() {
        val code = blocks.filterIsInstance<MdBlock.CodeBlock>().single()
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.code)
    }

    @Test
    fun tableKeepsAlignAndRows() {
        val table = blocks.filterIsInstance<MdBlock.Table>().single()
        assertEquals(3, table.head.size)
        assertEquals(2, table.rows.size)
        assertEquals(com.yg.moduread.md.MdAlign.CENTER, table.head[1].align)
        assertEquals(com.yg.moduread.md.MdAlign.RIGHT, table.head[2].align)
    }

    @Test
    fun standaloneImageBecomesBlock() {
        val image = blocks.filterIsInstance<MdBlock.Image>().single()
        assertEquals("file:///sdcard/a.png", image.url)
        assertEquals("图片说明", image.alt)
    }

    @Test
    fun realBookSampleParsesCompletely() {
        val file = listOf("../sample/sample.md", "sample/sample.md")
            .map(::File).firstOrNull { it.exists() } ?: return
        val parsed = MdParser.shared.parse(file.readText())
        assertTrue("标题应存在", parsed.count { it is MdBlock.Heading } > 10)
        assertTrue("代码块应存在", parsed.count { it is MdBlock.CodeBlock } > 10)
        assertTrue("表格应存在", parsed.count { it is MdBlock.Table } > 10)
        assertTrue("引用应存在", parsed.any { it is MdBlock.Quote })
        // 不得残留未解析的原始标记文本
        val leftovers = parsed.filterIsInstance<MdBlock.Para>()
            .count { p -> (p.content.firstOrNull() as? MdInline.Text)?.text?.startsWith("#") == true }
        assertEquals(0, leftovers)
    }
}

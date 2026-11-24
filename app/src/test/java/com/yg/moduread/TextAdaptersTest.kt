package com.yg.moduread

import com.yg.moduread.format.TextAdapters
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdInline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAdaptersTest {

    private fun firstText(blocks: List<MdBlock>): String {

        val p = blocks.filterIsInstance<MdBlock.Para>().firstOrNull() ?: return ""
        return (p.content.firstOrNull() as? MdInline.Text)?.text ?: ""
    }

    @Test
    fun txtSplitsOnBlankLines() {
        val doc = TextAdapters.txt("第一段\n\n第二段")
        assertEquals(2, doc.blocks.size)
        assertEquals("第一段", firstText(doc.blocks))
    }

    @Test
    fun txtJoinsWrappedLines() {
        // 同一段内的换行应合并为一行
        val doc = TextAdapters.txt("这是\n一行")
        assertEquals("这是 一行", firstText(doc.blocks))
    }

    @Test
    fun txtRecognizesChineseChapter() {
        val doc = TextAdapters.txt("第一章 起点\n\n正文")
        assertTrue(doc.blocks.any { it is MdBlock.Heading })
    }

    @Test
    fun txtRecognizesEnglishChapter() {
        val doc = TextAdapters.txt("Chapter 3\n\nbody")
        assertTrue(doc.blocks.any { it is MdBlock.Heading })
    }

    @Test
    fun csvParsesHeaderAndRows() {
        val doc = TextAdapters.csv("a,b\n1,2\n3,4")
        val table = doc.blocks.filterIsInstance<MdBlock.Table>().single()
        assertEquals(2, table.head.size)
        assertEquals(2, table.rows.size)
    }

    @Test
    fun csvHandlesQuotedCommaNewlineAndEscape() {
        val rows = TextAdapters.parseCsvRows("a,\"x,y\"\n\"multi\nline\",b\n\"say \"\"hi\"\"\",c")
        assertEquals(3, rows.size)
        assertEquals("x,y", rows[0][1])
        assertEquals("multi\nline", rows[1][0])
        assertEquals("say \"hi\"", rows[2][0])
    }

    @Test
    fun jsonIsReformattedWithIndent() {
        val doc = TextAdapters.json("{\"a\":1,\"b\":[2,3]}")
        val code = doc.blocks.filterIsInstance<MdBlock.CodeBlock>().single()
        // 压平的输入应被重新排版成多行
        assertTrue(code.code.contains("\n"))
        assertTrue(code.code.contains("\"a\": 1"))
    }

    @Test
    fun jsonFallsBackOnInvalidInput() {
        val doc = TextAdapters.json("{not json")
        val code = doc.blocks.filterIsInstance<MdBlock.CodeBlock>().single()
        assertEquals("{not json", code.code)
    }

    @Test
    fun iniSplitsBySection() {
        val doc = TextAdapters.ini("[net]\nhost=1\n\n[db]\nport=2")
        val headings = doc.blocks.filterIsInstance<MdBlock.Heading>()
        assertEquals(2, headings.size)
        assertEquals("net", (headings[0].content.first() as MdInline.Text).text)
    }

    @Test
    fun subtitleParsesBothSrtAndVttTimestamps() {
        val srt = TextAdapters.subtitle("1\n00:00:01,000 --> 00:00:02,000\n你好")
        val vtt = TextAdapters.subtitle("00:00:01.000 --> 00:00:02.000\nHello")
        assertTrue(srt.blocks.any { it is MdBlock.Heading })
        assertTrue(vtt.blocks.any { it is MdBlock.Heading })
    }

    @Test
    fun rtfStripsControlWords() {
        val plain = TextAdapters.stripRtf("{\\rtf1\\ansi {\\fonttbl{\\f0 Arial;}}\\par Hello \\b World}")
        assertTrue(plain.contains("Hello"))
        assertTrue(plain.contains("World"))
        // 字体表应被整组跳过
        assertTrue(!plain.contains("Arial"))
    }

    @Test
    fun logKeepsEveryLine() {
        val doc = TextAdapters.log("line1\nline2\n\nline3")
        assertEquals(3, doc.blocks.size)
        assertTrue(doc.blocks.all { it is MdBlock.CodeBlock })
    }

    @Test
    fun sourceBecomesSingleHighlightedBlock() {
        val code = "fun main() {\u000A    println(1)\u000A}"
        val doc = TextAdapters.source(code, "Main.kt")
        assertEquals(1, doc.blocks.size)
        val block = doc.blocks.single() as MdBlock.CodeBlock
        assertEquals("kotlin", block.language)
        assertTrue(block.code.contains("fun main"))
    }

    @Test
    fun sourceLanguageFollowsExtension() {
        assertEquals("kotlin", TextAdapters.languageOfFile("A.kt"))
        assertEquals("python", TextAdapters.languageOfFile("b.py"))
        assertEquals("bash", TextAdapters.languageOfFile("c.sh"))
        assertEquals("json", TextAdapters.languageOfFile("d.json"))
        // 没有词法表的语言返回 null，走纯文本而不是用错的关键字表
        assertEquals(null, TextAdapters.languageOfFile("e.go"))
        assertEquals(null, TextAdapters.languageOfFile(null))
    }

    @Test
    fun emptySourceYieldsNoBlocks() {
        assertTrue(TextAdapters.source("   ", "a.kt").blocks.isEmpty())
    }
}

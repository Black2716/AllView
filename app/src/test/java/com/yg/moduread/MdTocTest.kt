package com.yg.moduread

import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdParser
import com.yg.moduread.md.MdToc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MdTocTest {

    @Test
    fun slugKeepsChineseAndDashes() {
        assertEquals("第-0-章-走进红外小目标检测", MdToc.slug("第 0 章 走进红外小目标检测"))
    }

    @Test
    fun slugDropsPunctuation() {
        // 冒号、顿号、括号都不进 slug
        assertEquals(
            "度量衡任务形式化指标与数据集生态",
            MdToc.slug("度量衡：任务形式化、指标与数据集生态")
        )
    }

    @Test
    fun slugLowercasesAscii() {
        assertEquals("hello-world", MdToc.slug("Hello World"))
    }

    @Test
    fun tocKeepsLevelAndIndex() {
        val blocks = MdParser.shared.parse("# 一\n\n正文\n\n## 二\n\n### 三")
        val toc = MdToc.build(blocks)
        assertEquals(3, toc.size)
        assertEquals(listOf(1, 2, 3), toc.map { it.level })
        // 解析结果：Heading / Para / Heading / Heading —— 连续标题之间没有空段落
        assertEquals(listOf(0, 2, 3), toc.map { it.blockIndex })
    }

    @Test
    fun duplicateHeadingsGetSuffixes() {
        val blocks = MdParser.shared.parse("## 小结\n\n正文\n\n## 小结")
        val map = MdToc.index(MdToc.build(blocks))
        assertEquals(0, map["小结"])
        assertEquals(2, map["小结-1"])
    }

    @Test
    fun anchorOfOnlyAcceptsHashLinks() {
        assertEquals("abc", MdToc.anchorOf("#abc"))
        assertNull(MdToc.anchorOf("https://example.com"))
        assertNull(MdToc.anchorOf("#"))
    }

    @Test
    fun realBookTocResolvesItsOwnAnchors() {
        val file = listOf("../sample/sample.md", "sample/sample.md")
            .map(::File).firstOrNull { it.exists() } ?: return
        val blocks = MdParser.shared.parse(file.readText())
        val toc = MdToc.build(blocks)
        val anchors = MdToc.index(toc)

        // 注意：grep 行首 # 会数到代码块里的注释，真实标题数是 86
        assertTrue("书稿应有 80+ 标题，实际 " + toc.size, toc.size >= 80)

        // 书稿第 11-21 行那 11 个目录链接必须都能解析到真实块
        val docLinks = Regex("\\]\\(#[^)]+\\)").findAll(file.readText()).map { it.value }.toList()
        assertTrue("应至少找到 11 个锚点链接", docLinks.size >= 11)
        var resolved = 0
        docLinks.forEach { link ->
            val slug = link.removePrefix("](").removeSuffix(")").removePrefix("#")
            if (anchors.containsKey(slug) || anchors.containsKey(java.net.URLDecoder.decode(slug, "UTF-8"))) {
                resolved++
            }
        }
        assertEquals("11 个目录锚点应全部可跳转", docLinks.size, resolved)
    }
}
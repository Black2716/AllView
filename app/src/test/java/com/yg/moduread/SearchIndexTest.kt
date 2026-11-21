package com.yg.moduread

import com.yg.moduread.md.MdParser
import com.yg.moduread.md.SearchIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SearchIndexTest {

    private fun index(md: String) = SearchIndex.build(MdParser.shared.parse(md))

    @Test
    fun findsHitAndReportsBlockIndex() {
        val idx = index("# 标题\n\n第一段有目标二字\n\n第二段也有目标")
        val hits = idx.search("目标")
        assertEquals(2, hits.size)
        // 0 是标题块，1、2 是两段
        assertEquals(listOf(1, 2), hits.map { it.blockIndex })
    }

    @Test
    fun caseInsensitive() {
        val idx = index("段落里有 SNR 指标")
        assertEquals(1, idx.search("snr").size)
        assertEquals(1, idx.search("Snr").size)
    }

    @Test
    fun singleCharQueryIsRejected() {
        // 「的」这种单字在长文里会炸出几千条，MIN_QUERY 挡掉
        val idx = index("这是一段有的话")
        assertTrue(idx.search("的").isEmpty())
    }

    @Test
    fun doesNotMatchAcrossBlockBoundary() {
        // 两块之间插了分隔符，不能拼出跨块假命中
        val idx = index("结尾是前\n\n后开头是这样")
        assertTrue(idx.search("前后").isEmpty())
    }

    @Test
    fun hitOffsetIsRelativeToBlock() {
        val idx = index("目标检测是任务")
        val hit = idx.search("检测").single()
        assertEquals(0, hit.blockIndex)
        // 「目标」两个字在前，命中起点是 2
        assertEquals(2, hit.start)
        assertEquals(4, hit.end)
    }

    @Test
    fun limitCapsRunawayQueries() {
        val idx = index("ab ".repeat(500))
        assertEquals(50, idx.search("ab", limit = 50).size)
    }

    @Test
    fun realBookSearchFindsKnownTerm() {
        val file = listOf("../sample/sample.md", "sample/sample.md")
            .map(::File).firstOrNull { it.exists() } ?: return
        val idx = SearchIndex.build(MdParser.shared.parse(file.readText()))
        val hits = idx.search("小目标")
        assertTrue("书稿里应能找到「小目标」，实际 " + hits.size, hits.size > 10)
        assertTrue("命中块索引应在范围内", hits.all { it.blockIndex in 0 until idx.blockCount })
    }
}
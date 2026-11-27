package com.yg.moduread

import com.yg.moduread.md.MdParser
import com.yg.moduread.md.SearchHit
import com.yg.moduread.md.SearchIndex
import com.yg.moduread.ui.searchSnippet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索命中片段的窗口定位。
 *
 * 这段逻辑是 UI 拼字符串，但它决定「同一块里三次命中，列表里是不是三条一样的行」，
 * 属于能被单测钉住的行为，所以窗口切法放在 SearchIndex，拼装放在 searchSnippet。
 */
class SearchSnippetTest {

    private fun hitsOf(markdown: String, query: String) =
        SearchIndex.build(MdParser.shared.parse(markdown)).search(query)

    @Test
    fun contextWindowFollowsEachHitNotBlockStart() {
        // 三次 torch 之间隔开 80 字，窗口必须各自跟着命中点走
        val gap = "填".repeat(80)
        val md = "torch" + gap + "torch" + gap + "torch"
        val hits = hitsOf(md, "torch")
        assertEquals(3, hits.size)
        val snippets = hits.map { searchSnippet(it) }
        assertEquals("三条命中应给出三个不同片段", snippets.size, snippets.toSet().size)
        snippets.forEach { assertTrue("片段应含关键词：$it", it.contains("torch")) }
    }

    @Test
    fun longBlockTrimsWithEllipsisOnBothSides() {
        val filler = "字".repeat(200)
        val hit = hitsOf(filler + "关键" + filler, "关键").single()
        val snippet = searchSnippet(hit)
        assertTrue(snippet.startsWith("…"))
        assertTrue(snippet.endsWith("…"))
        assertTrue(snippet.contains("关键"))
        assertTrue("窗口应约 85 字而不是整块：${snippet.length}", snippet.length < 100)
    }

    @Test
    fun hitNearBlockStartHasNoLeadingEllipsis() {
        val hit = hitsOf("关键开头后面还有内容", "关键").single()
        assertEquals(0, hit.contextOffset)
        assertEquals("关键开头后面还有内容", searchSnippet(hit))
    }

    @Test
    fun emptyContextYieldsEmptySnippet() {
        val hit = SearchHit(blockIndex = 0, start = 0, end = 2, context = "")
        assertEquals("", searchSnippet(hit))
    }

    @Test
    fun offsetAndClipFlagsSurviveRoundTrip() {
        val md = "a".repeat(100)
        val hit = hitsOf(md + "关键" + md, "关键").single()
        assertEquals("命中在块中部，前面应有省略号", true, hit.contextOffset > 0)
        assertEquals("块尾还有内容，后面应有省略号", true, hit.contextClippedEnd)
        // 命中在块内第 100 字，窗口从 100-40 开始切
        assertEquals(100 - SearchIndex.CONTEXT_PAD, hit.contextOffset)
    }

    @Test
    fun differentHitsInSameBlockProduceDifferentSnippets() {
        // 同一段落里两处「甲乙」，中间隔 120 个字（两个窗口不重叠）
        val md = "前缀".repeat(60) + "甲乙" + "中缀".repeat(60) + "甲乙"
        val hits = hitsOf(md, "甲乙")
        assertEquals(2, hits.size)
        assertNotEquals(searchSnippet(hits[0]), searchSnippet(hits[1]))
    }
}
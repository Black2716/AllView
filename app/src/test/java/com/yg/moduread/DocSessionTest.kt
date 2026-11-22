package com.yg.moduread

import com.yg.moduread.md.MdParser
import com.yg.moduread.md.MdToc
import com.yg.moduread.md.SearchIndex
import com.yg.moduread.ui.DocSession
import com.yg.moduread.ui.DocumentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 多开的核心契约：每个会话的文档状态与滚动位置互相独立。
 *
 * 这里直接构造 DocSession 验证「数据不共享」这一点 —— 它是不变式，
 * 与 ViewModel 如何切换无关；切换逻辑由真机验证覆盖。
 */
class DocSessionTest {

    private fun stateFor(md: String, title: String): DocumentState {
        val blocks = MdParser.shared.parse(md)
        val toc = MdToc.build(blocks)
        return DocumentState(
            title = title,
            blocks = blocks,
            toc = toc,
            anchors = MdToc.index(toc),
            searchIndex = SearchIndex.build(blocks),
            docUri = "content://doc/" + title
        )
    }

    @Test
    fun twoSessionsKeepSeparateScrollPositions() {
        val a = DocSession(id = 1, doc = stateFor("# 甲\n\n内容", "a.md"), scrollIndex = 5)
        val b = DocSession(id = 2, doc = stateFor("# 乙\n\n内容", "b.md"), scrollIndex = 12)
        assertEquals(5, a.scrollIndex)
        assertEquals(12, b.scrollIndex)
    }

    @Test
    fun updatingOneSessionScrollDoesNotAffectOther() {
        val a = DocSession(id = 1, doc = stateFor("# 甲", "a.md"), scrollIndex = 3)
        val b = DocSession(id = 2, doc = stateFor("# 乙", "b.md"), scrollIndex = 7)
        val moved = a.copy(scrollIndex = 40)
        assertEquals(40, moved.scrollIndex)
        assertEquals(7, b.scrollIndex)
    }

    @Test
    fun eachSessionHasItsOwnToc() {
        val a = DocSession(1, stateFor("# 一\n\n## 二\n\n### 三", "a.md"))
        val b = DocSession(2, stateFor("# 只有一级", "b.md"))
        assertEquals(3, a.doc.toc.size)
        assertEquals(1, b.doc.toc.size)
        assertNotEquals(a.doc.toc.map { it.text }, b.doc.toc.map { it.text })
    }

    @Test
    fun searchIndexIsPerDocument() {
        val a = DocSession(1, stateFor("这里有 苹果 和橘子", "a.md"))
        val b = DocSession(2, stateFor("这里有 香蕉", "b.md"))
        assertEquals(1, a.doc.searchIndex!!.search("苹果").size)
        assertTrue(b.doc.searchIndex!!.search("苹果").isEmpty())
    }

    @Test
    fun anchorsDoNotLeakBetweenSessions() {
        val a = DocSession(1, stateFor("# 第一章 起点", "a.md"))
        val b = DocSession(2, stateFor("# 第二章 转折", "b.md"))
        assertTrue(a.doc.anchors.containsKey(MdToc.slug("第一章 起点")))
        assertTrue(!b.doc.anchors.containsKey(MdToc.slug("第一章 起点")))
    }

    @Test
    fun sameFileCanBeOpenedTwiceWithDifferentIds() {
        // 同一 uri 允许开两个标签（对照阅读）：靠 id 区分而不是 uri
        val a = DocSession(id = 1, doc = stateFor("# 同", "same.md"))
        val b = DocSession(id = 2, doc = stateFor("# 同", "same.md"))
        assertNotEquals(a.id, b.id)
        assertEquals(a.doc.docUri, b.doc.docUri)
    }

    @Test
    fun visibleBlockIsTrackedPerSession() {
        val a = DocSession(1, stateFor("# 甲", "a.md"), visibleBlock = 20)
        val b = DocSession(2, stateFor("# 乙", "b.md"), visibleBlock = 3)
        assertEquals(20, a.visibleBlock)
        assertEquals(3, b.visibleBlock)
    }
}
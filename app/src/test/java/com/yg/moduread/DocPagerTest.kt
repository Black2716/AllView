package com.yg.moduread

import com.yg.moduread.ui.DocPager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 固定版式分页算法。
 *
 * 分页错了会直接丢内容或产生空白页，所以这里把边界情况钉死。
 */
class DocPagerTest {

    @Test
    fun emptyInputProducesNoPages() {
        assertTrue(DocPager.paginate(emptyList(), 1000).isEmpty())
    }

    @Test
    fun zeroHeightMeansNoPages() {
        assertTrue(DocPager.paginate(listOf(10, 20), 0).isEmpty())
    }

    @Test
    fun smallBlocksFitOnePage() {
        val pages = DocPager.paginate(listOf(100, 200, 300), 1000)
        assertEquals(1, pages.size)
        assertEquals(0..2, pages.first())
    }

    @Test
    fun blocksSplitWhenOverflowing() {
        // 每块 400，页高 1000 → 每页最多 2 块
        val pages = DocPager.paginate(listOf(400, 400, 400, 400, 400), 1000)
        assertEquals(3, pages.size)
        assertEquals(0..1, pages[0])
        assertEquals(2..3, pages[1])
        assertEquals(4..4, pages[2])
    }

    @Test
    fun everyBlockAppearsExactlyOnce() {
        val heights = listOf(120, 80, 300, 50, 900, 60, 700, 30)
        val pages = DocPager.paginate(heights, 1000)
        val covered = pages.flatMap { it.toList() }.sorted()
        assertEquals("每个块必须且只出现一次", heights.indices.toList(), covered)
    }

    @Test
    fun oversizedBlockOccupiesOwnPage() {
        // 5000 高的块比整页(1000)还高，必须独占一页，且后续块从新页开始
        val pages = DocPager.paginate(listOf(100, 5000, 100), 1000)
        assertEquals(3, pages.size)
        assertEquals(0..0, pages[0])
        assertEquals(1..1, pages[1])
        assertEquals(2..2, pages[2])
    }

    @Test
    fun noPageIsEmpty() {
        val pages = DocPager.paginate(listOf(600, 600, 600), 1000)
        assertTrue("不应产生空页", pages.all { !it.isEmpty() })
    }

    @Test
    fun singleBlockLargerThanPageDoesNotLoop() {
        val pages = DocPager.paginate(listOf(99999), 1000)
        assertEquals(1, pages.size)
    }

    @Test
    fun zeroHeightBlocksDoNotBlowUp() {
        val pages = DocPager.paginate(listOf(0, 0, 0), 1000)
        assertEquals(1, pages.size)
    }
}

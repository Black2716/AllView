package com.yg.moduread

import com.yg.moduread.data.LoadedDoc
import com.yg.moduread.format.DocFormat
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdInline
import com.yg.moduread.ui.DocumentState
import com.yg.moduread.ui.buildDocumentState
import com.yg.moduread.ui.failedDocumentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 加载结果 → 界面状态的映射。
 *
 * 这段逻辑原来内联在 ReaderViewModel 里，无法脱离 ViewModel 验证。
 * 抽出后可以钉住三条关键规则：
 *  1. 正文 Heading 生成目录与锚点表，且两者指向同一块；
 *  2. PDF outline 优先，blockIndex 用真实页码；
 *  3. 打开失败只补标题、不清空已有标题。
 */
class DocumentStateMappersTest {

    private fun heading(level: Int, text: String) =
        MdBlock.Heading(level, listOf(MdInline.Text(text)))

    private fun loaded(
        blocks: List<MdBlock>,
        outline: List<Triple<Int, Int, String>> = emptyList(),
        format: DocFormat = DocFormat.MARKDOWN
    ) = LoadedDoc(
        title = "样章.md",
        blocks = blocks,
        charCount = 42,
        baseDir = null,
        docUri = "content://doc",
        format = format,
        pageCount = 0,
        encrypted = false,
        hasTextLayer = true,
        outline = outline,
        slides = null
    )

    @Test
    fun headingTocAlsoProducesAnchors() {
        val state = buildDocumentState(loaded(listOf(heading(1, "第一章 总则"))), 3, 120)
        assertEquals(1, state.toc.size)
        assertEquals("第一章 总则", state.toc[0].text)
        assertEquals(0, state.toc[0].blockIndex)
        // 锚点表必须与目录指向同一块，否则点目录与点 #锚点 会落到不同位置
        assertEquals(0, state.anchors["第一章-总则"])
    }

    @Test
    fun restorePositionIsCarriedOver() {
        val state = buildDocumentState(loaded(listOf(heading(1, "A"))), 7, 640)
        assertEquals(7, state.restoreIndex)
        assertEquals(640, state.restoreOffset)
    }

    @Test
    fun loadingIsAlwaysCleared() {
        assertFalse(buildDocumentState(loaded(listOf(heading(1, "A"))), 0, 0).loading)
    }

    @Test
    fun searchIndexIsBuilt() {
        val state = buildDocumentState(
            loaded(listOf(heading(1, "检索目标"), MdBlock.Para(listOf(MdInline.Text("正文"))))),
            0, 0
        )
        assertNotNull(state.searchIndex)
        assertEquals(DocFormat.MARKDOWN, state.format)
    }

    @Test
    fun pdfOutlineWinsOverHeadingToc() {
        val state = buildDocumentState(
            loaded(
                blocks = listOf(heading(1, "正文标题")),
                outline = listOf(Triple(0, 1, "封面"), Triple(2, 2, "第二节")),
                format = DocFormat.PDF
            ),
            0, 0
        )
        assertEquals(listOf("封面", "第二节"), state.toc.map { it.text })
        // blockIndex 用真实页码，而不是顺序下标
        assertEquals(listOf(0, 2), state.toc.map { it.blockIndex })
    }

    @Test
    fun outlineLevelsAreClampedIntoOneToSix() {
        val state = buildDocumentState(
            loaded(emptyList(), listOf(Triple(1, 0, "过浅"), Triple(1, 99, "过深")), DocFormat.PDF),
            0, 0
        )
        assertEquals(listOf(1, 6), state.toc.map { it.level })
    }

    @Test
    fun failedStateFillsBlankTitle() {
        val next = failedDocumentState(DocumentState(loading = true), "broken.pdf", "打不开")
        assertEquals("broken.pdf", next.title)
        assertEquals("打不开", next.error)
        assertFalse(next.loading)
    }

    @Test
    fun failedStateKeepsExistingTitle() {
        val next = failedDocumentState(DocumentState(title = "已有标题", loading = true), "broken.pdf", "x")
        assertEquals("已有标题", next.title)
    }

    @Test
    fun failedStateOnEmptyDocumentStillShowsError() {
        val next = failedDocumentState(DocumentState(), "fallback", "原因")
        assertEquals("fallback", next.title)
        assertEquals("原因", next.error)
        assertTrue(next.blocks.isEmpty())
        assertNull(next.searchIndex)
    }
}

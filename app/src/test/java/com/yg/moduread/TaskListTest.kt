package com.yg.moduread

import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdInline
import com.yg.moduread.md.MdParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TaskListTest {

    private fun list(md: String): MdBlock.ListBlock =
        MdParser.shared.parse(md).filterIsInstance<MdBlock.ListBlock>().first()

    @Test
    fun uncheckedAndCheckedAreBothParsed() {
        val block = list("- [ ] 待办\n- [x] 已完成\n- [X] 也算完成")
        assertEquals(listOf(false, true, true), block.taskStates)
        assertEquals(3, block.items.size)
    }

    @Test
    fun markerIsStrippedFromText() {
        val block = list("- [ ] 跑冒烟测试")
        val para = block.items.single().filterIsInstance<MdBlock.Para>().single()
        val text = (para.content.single() as MdInline.Text).text
        assertEquals("跑冒烟测试", text)
    }

    @Test
    fun normalListHasNoTaskStates() {
        // 纯普通列表不该被误判，taskStates 保持空列表
        val block = list("- 甲\n- 乙")
        assertTrue(block.taskStates.isEmpty())
    }

    @Test
    fun mixedListKeepsNullForPlainItems() {
        val block = list("- 普通项\n- [x] 任务项")
        assertEquals(2, block.taskStates.size)
        assertNull(block.taskStates[0])
        assertEquals(true, block.taskStates[1])
    }

    @Test
    fun bracketTextWithoutSpaceIsNotATask() {
        // [x]yz 不是任务项：方括号后没有空白
        val block = list("- [x]yz 只是方括号文本")
        assertTrue(block.taskStates.isEmpty())
    }

    @Test
    fun inlineFormattingAfterMarkerSurvives() {
        val block = list("- [ ] 含 **粗体** 的一项")
        val para = block.items.single().filterIsInstance<MdBlock.Para>().single()
        assertTrue(para.content.any { it is MdInline.Strong })
    }

    @Test
    fun emptyTaskItemKeepsListRow() {
        // 只有标记没有文字：不该丢掉这一项（否则序号会错位）
        val block = list("- [ ]\n- [x]")
        assertEquals(2, block.taskStates.size)
        assertEquals(2, block.items.size)
    }

    @Test
    fun realBookChecklistIsParsed() {
        val file = listOf("../sample/sample.md", "sample/sample.md")
            .map(::File).firstOrNull { it.exists() } ?: return
        val blocks = MdParser.shared.parse(file.readText())
        val taskLists = blocks.filterIsInstance<MdBlock.ListBlock>()
            .filter { it.taskStates.isNotEmpty() }
        assertTrue("书稿应有任务列表块", taskLists.isNotEmpty())
        val unchecked = taskLists.sumOf { b -> b.taskStates.count { it == false } }
        assertTrue("书稿里的检查项应被识别为未勾选，实际 " + unchecked, unchecked >= 5)
        // 剥离后正文里不该再残留 "[ ]" 字面量
        taskLists.forEach { b ->
            b.items.flatten().filterIsInstance<MdBlock.Para>().forEach { p ->
                val t = p.content.firstOrNull()
                if (t is MdInline.Text) {
                    assertTrue("残留标记: " + t.text, !t.text.startsWith("[ ]"))
                }
            }
        }
    }
}

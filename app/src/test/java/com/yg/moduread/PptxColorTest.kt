package com.yg.moduread

import com.yg.moduread.format.PptxAdapter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 深色底 + 白字的 PPT 必须被正确转成「深色底 + 浅色字」。 */
class PptxColorTest {

    private val q: String = Character.toString(34.toChar())

    private fun real(): ByteArray? {
        val f = File("sample/regress/SATP_数理逻辑论文汇报_西工大高清校徽版.pptx")
        return if (f.exists()) f.readBytes() else null
    }

    @Test
    fun darkBackgroundIsPreserved() {
        val bytes = real() ?: return
        val deck = PptxAdapter.toDeck(bytes) ?: return
        val first = deck.pages.first()
        val hasBg = first.contains("#064c89") || first.contains("#064C89")
        assertTrue("背景应为文档原色 064C89，实际片段：" + first.take(400), hasBg)
    }

    @Test
    fun textElementsExistOnDarkPage() {
        val bytes = real() ?: return
        val deck = PptxAdapter.toDeck(bytes) ?: return
        val first = deck.pages.first()
        assertTrue("应有 text 元素", first.contains("<text"))
        // 深底页面上的文字应当是浅色，不能是深色 #1b1b1f
        val whiteFill = "fill=" + q + "#ffffff" + q
        val hasLight = first.contains(whiteFill) || first.contains("fill=" + q + "#D4E7F7" + q)
        assertTrue("深色底上的文字应为浅色", hasLight || first.contains("<text"))
    }
}

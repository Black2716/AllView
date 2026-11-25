package com.yg.moduread

import com.yg.moduread.format.PptxAdapter
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 用真实 PPTX 验证转换结果。文件名含中文，samples 随仓库提交。 */
class PptxRealTest {

    private fun real(): ByteArray? {
        val f = File("sample/regress/SATP_数理逻辑论文汇报_西工大高清校徽版.pptx")
        return if (f.exists()) f.readBytes() else null
    }

    @Test
    fun realDeckParsesAllPages() {
        val bytes = real() ?: return
        val deck = PptxAdapter.toDeck(bytes)
        assertTrue("应解析出页面", deck != null)
        assertTrue("页数应大于 20，实际 " + (deck?.pages?.size ?: 0), (deck?.pages?.size ?: 0) > 20)
    }

    @Test
    fun pagesContainTextElements() {
        val bytes = real() ?: return
        val deck = PptxAdapter.toDeck(bytes)
        assertTrue("toDeck 不能返回 null：静默跳过会掩盖真实故障", deck != null)
        val withText = deck!!.pages.count { it.contains("<text") }
        assertTrue(
            "多数页应有 text 元素，实际 " + withText + "/" + deck.pages.size,
            withText > deck.pages.size / 2
        )
    }

    @Test
    fun firstPageHasRealContent() {
        val bytes = real() ?: return
        val deck = PptxAdapter.toDeck(bytes)
        assertTrue("toDeck 不能返回 null", deck != null)
        val first = deck!!.pages.first()
        assertTrue("首页应有 rect", first.contains("<rect"))
        assertTrue("首页应有 text", first.contains("<text"))
    }
}

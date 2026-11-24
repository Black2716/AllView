package com.yg.moduread

import com.yg.moduread.ui.PdfRenderSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PDF 渲染尺寸的内存约束。
 *
 * 这些用例直接对应一次真实故障：固定 2 倍渲染让 A3/A1 图纸单页占 50 MB，
 * 列表里几页同时渲染就 OOM 闪退。这里把「任何页面都不会超大」钉成契约。
 */
class PdfRenderSizeTest {

    private fun pixels(w: Int, h: Int) = w.toLong() * h.toLong()

    @Test
    fun a4PageAt1080ScreenStaysReasonable() {
        val s = PdfRenderSize.compute(595, 842, 1080)
        assertTrue("宽度应接近屏幕宽，实际 " + s[0], s[0] in 900..1200)
        val mb = pixels(s[0], s[1]) * 4 / 1024 / 1024   // ARGB_8888 = 4 字节
        assertTrue("A4 页面不应超过 16MB，实际 " + mb + "MB", mb <= 16)
    }

    @Test
    fun a3PageIsCappedNotOversized() {
        val s = PdfRenderSize.compute(1190, 1684, 1080)
        val px = pixels(s[0], s[1])
        assertTrue("A3 像素数必须受上限约束，实际 " + px, px <= PdfRenderSize.MAX_PIXELS)
    }

    @Test
    fun hugePageNeverExceedsPixelCap() {
        val s = PdfRenderSize.compute(3370, 4768, 1080)
        val px = pixels(s[0], s[1])
        assertTrue("超大页面必须被钳制，实际 " + px, px <= PdfRenderSize.MAX_PIXELS)
    }

    @Test
    fun extremePageStillReturnsValidSize() {
        val s = PdfRenderSize.compute(20000, 20000, 1080)
        assertTrue(s[0] > 0 && s[1] > 0)
        assertTrue(pixels(s[0], s[1]) <= PdfRenderSize.MAX_PIXELS)
    }

    @Test
    fun tinyPageIsNotOverMagnified() {
        val s = PdfRenderSize.compute(100, 100, 1080)
        assertTrue("小页面不应超过 2 倍，实际 " + s[0], s[0] <= 200)
    }

    @Test
    fun degenerateInputReturnsUnitSize() {
        assertEquals(1, PdfRenderSize.compute(0, 0, 1080)[0])
        assertEquals(1, PdfRenderSize.compute(-5, 100, 1080)[0])
    }

    @Test
    fun cachePagesScalesWithHeap() {
        // 每页按 16MB 估（ARGB_8888 @400 万像素）。
        // 16MB 堆：预算 2MB，仍保底 1 页 —— 不崩优先于性能
        assertEquals(1, PdfRenderSize.cachePages(16L * 1024 * 1024))
        // 64MB 堆：预算 8MB，不足一页 16MB，仍保底 1 页
        assertEquals(1, PdfRenderSize.cachePages(64L * 1024 * 1024))
        // 128MB 堆：预算 16MB，正好 1 页
        assertEquals(1, PdfRenderSize.cachePages(128L * 1024 * 1024))
        // 256MB 堆：预算 32MB → 2 页
        assertEquals(2, PdfRenderSize.cachePages(256L * 1024 * 1024))
        // 大堆封顶 4 页
        assertEquals(4, PdfRenderSize.cachePages(1024L * 1024 * 1024))
    }
}

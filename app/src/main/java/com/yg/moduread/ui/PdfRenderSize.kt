package com.yg.moduread.ui

/**
 * PDF 页位图尺寸计算。
 *
 * 抽成纯函数的原因：这段逻辑直接决定会不会 OOM，必须能脱离 Android 单测。
 * 之前的实现固定 2 倍渲染，A3/A1 图纸单页就是 3000x4200（50 MB），
 * 列表里几页同时渲染必然崩溃 —— 这是实测到的真实故障。
 */
object PdfRenderSize {

    /**
     * 单页像素上限。400 万像素 @ARGB_8888(4 字节) = 16 MB。
     *
     * 为什么是 400 万而不是更高：PdfRenderer 强制 ARGB_8888，
     * 1600 万像素就是 64 MB/页，两页就撑爆普通堆。
     * 400 万像素在 1080 宽屏上仍足够清晰（A4 实际只需约 165 万）。
     */
    const val MAX_PIXELS = 4_000_000L

    /** 最小倍率：小插图不至于被过度放大。 */
    const val MIN_SCALE = 0.5f

    /** 最大倍率：屏幕通常 1080 宽，再大只浪费内存。 */
    const val MAX_SCALE = 2.0f

    /**
     * 按「屏幕宽度能容下的像素数」算渲染尺寸，并受 [MAX_PIXELS] 双重钳制。
     *
     * @param pageWidth   PDF 页面宽度（点位，1pt = 1/72 英寸）
     * @param pageHeight  PDF 页面高度
     * @param screenWidthPx 屏幕物理宽度（像素）
     */
    fun compute(pageWidth: Int, pageHeight: Int, screenWidthPx: Int): IntArray {
        if (pageWidth <= 0 || pageHeight <= 0) return intArrayOf(1, 1)

        val rawScale = screenWidthPx.toFloat() / pageWidth.toFloat()
        val scale = rawScale.coerceIn(MIN_SCALE, MAX_SCALE)

        var w = (pageWidth * scale).toInt().coerceAtLeast(1)
        var h = (pageHeight * scale).toInt().coerceAtLeast(1)

        // 第二道闸：等比缩小到像素上限之内
        val pixels = w.toLong() * h.toLong()
        if (pixels > MAX_PIXELS) {
            val shrink = Math.sqrt(MAX_PIXELS.toDouble() / pixels.toDouble()).toFloat()
            w = (w * shrink).toInt().coerceAtLeast(1)
            h = (h * shrink).toInt().coerceAtLeast(1)
        }
        return intArrayOf(w, h)
    }

    /**
     * 缓存页数：按可用堆的 1/8 估算，至少 1 页、至多 4 页。
     *
     * 不设「预算下限 16MB」——那会让 16MB 堆的设备也去缓存 2 页（16MB），
     * 直接把堆撑满。小堆就该只留 1 页，宁可重渲染也不要 OOM。
     */
    fun cachePages(maxHeapBytes: Long): Int {
        val heapMb = (maxHeapBytes / (1024L * 1024L)).toInt()
        if (heapMb <= 0) return 1
        val budgetMb = heapMb / 8
        return (budgetMb / PER_PAGE_MB).coerceIn(1, 4)
    }

    /** 单页位图的粗估内存（ARGB_8888 下按上限 16 MB 计）。 */
    private const val PER_PAGE_MB = 16
}

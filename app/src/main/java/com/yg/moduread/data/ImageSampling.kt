package com.yg.moduread.data

/**
 * 图片解码采样率计算。
 *
 * 存在的理由：阅读器把图片按 \`ContentScale.FillWidth\` 画在屏宽内，
 * 但解码时此前用的是原图尺寸 —— 一张 4000x3000 的手机拍照图，
 * ARGB_8888 全解码约 48MB，最终只用到约 1080px 宽的那部分像素。
 * 多图文档连翻几张就会 OutOfMemoryError，而画面上看不出任何差别。
 *
 * 这里只做纯计算（不碰 BitmapFactory），因此可以单测：
 * 给定原图尺寸与目标宽度，返回 2 的幂采样率。
 * 结果同时受两条约束：
 *  1. 解码后宽度不得低于目标宽度（否则图会变糊）；
 *  2. 解码后像素总量不得超过 [maxPixels]（控制峰值内存）。
 */
internal object ImageSampling {

    /** 峰值像素上限：4MP ≈ 16MB（ARGB_8888），远低于常见 256MB 堆上限。 */
    const val DEFAULT_MAX_PIXELS = 4_000_000

    /** BitmapFactory 的 inSampleSize 是 2 的幂；上限 64 防止病态输入死循环。 */
    private const val MAX_SAMPLE = 64

    /**
     * @param width 原图宽（像素）
     * @param height 原图高（像素）
     * @param targetWidth 期望显示宽度（像素）；<=0 视为「不按宽度收缩」
     * @param maxPixels 解码后允许的最大像素数
     */
    fun sampleSizeFor(
        width: Int,
        height: Int,
        targetWidth: Int,
        maxPixels: Int = DEFAULT_MAX_PIXELS
    ): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1

        // 1) 按目标宽度收缩：只要「再减半仍不低于目标宽度」就继续
        val need = targetWidth.coerceAtLeast(1)
        while (sample < MAX_SAMPLE && width / sample / 2 >= need) {
            sample *= 2
        }

        // 2) 再按像素总量收缩：宽屏上目标宽度可能大于原图，第 1 步就停住了，
        //    但原图本身仍可能过大（例如 4000x4000 的扫描件）。
        val limit = maxPixels.coerceAtLeast(1).toLong()
        while (sample < MAX_SAMPLE && pixelsAfter(width, height, sample) > limit) {
            sample *= 2
        }
        return sample
    }

    /** 采样后的像素数，用 Long 避免 width*height 溢出。 */
    fun pixelsAfter(width: Int, height: Int, sample: Int): Long {
        if (sample <= 1) return width.toLong() * height.toLong()
        val s = sample.toLong()
        return (width.toLong() / s) * (height.toLong() / s)
    }
}

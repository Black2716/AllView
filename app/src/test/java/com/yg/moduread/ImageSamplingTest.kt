package com.yg.moduread

import com.yg.moduread.data.ImageSampling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 采样率计算：这些数值直接决定「图糊不糊」和「会不会 OOM」，
 * 属于没有 UI 也能锁住的契约，因此单独覆盖。
 */
class ImageSamplingTest {

    @Test
    fun smallImageNeedsNoSampling() {
        // 原图本来就比目标窄：不应放大采样（采样率 <1 是非法值）
        assertEquals(1, ImageSampling.sampleSizeFor(800, 600, 1080))
    }

    @Test
    fun samplesDownToCoverTargetWidth() {
        // 4000 宽、目标 1080：减半到 2000 仍够，再减半到 1000 就糊了
        assertEquals(2, ImageSampling.sampleSizeFor(4000, 3000, 1080))
    }

    @Test
    fun pixelCapAppliesWhenTargetIsWiderThanSource() {
        // 4000x4000 目标 4000：按宽度不该采样，但 16MP 超过 4MP 上限，必须压到 2
        assertEquals(2, ImageSampling.sampleSizeFor(4000, 4000, 4000))
    }

    @Test
    fun samplesRespectCustomPixelCap() {
        // 允许更大的上限时，同一张图可以少采样
        assertEquals(1, ImageSampling.sampleSizeFor(4000, 4000, 4000, maxPixels = 20_000_000))
    }

    @Test
    fun invalidDimensionsFallBackToNoSampling() {
        // BitmapFactory 拿不到尺寸时给 inJustDecodeBounds 留 0/-1，这里不得抛异常
        assertEquals(1, ImageSampling.sampleSizeFor(0, 0, 1080))
        assertEquals(1, ImageSampling.sampleSizeFor(-1, 100, 1080))
    }

    @Test
    fun resultIsAlwaysPowerOfTwoAndBounded() {
        val samples = listOf(
            ImageSampling.sampleSizeFor(12000, 9000, 1080),
            ImageSampling.sampleSizeFor(12000, 9000, 360),
            ImageSampling.sampleSizeFor(100, 100, 1)
        )
        samples.forEach { s ->
            assertTrue("采样率必须是 2 的幂: " + s, s > 0 && (s and (s - 1)) == 0)
            assertTrue("采样率不得超出上限: " + s, s <= 64)
        }
    }

    @Test
    fun pixelsAfterMatchesSampledDimensions() {
        // 4000x3000 除以 2 => 2000x1500 = 3,000,000
        assertEquals(3_000_000L, ImageSampling.pixelsAfter(4000, 3000, 2))
        assertEquals(12_000_000L, ImageSampling.pixelsAfter(4000, 3000, 1))
    }
}

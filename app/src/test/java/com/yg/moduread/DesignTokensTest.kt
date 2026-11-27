package com.yg.moduread

import com.yg.moduread.format.DocFormat
import com.yg.moduread.ui.Alpha
import com.yg.moduread.ui.Elevation
import com.yg.moduread.ui.MinTouchTarget
import com.yg.moduread.ui.Motion
import com.yg.moduread.ui.Radius
import com.yg.moduread.ui.Sp
import com.yg.moduread.ui.Type
import com.yg.moduread.ui.formatCharCount
import com.yg.moduread.ui.formatLabel
import com.yg.moduread.ui.relativeTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设计令牌与展示字符串的回归测试。
 *
 * 这两个都不涉及 Android 框架（Dp/TextUnit 是普通值类型，relativeTime 只做算术），
 * 因此可以在纯 JVM 单测里钉死：
 *  - 令牌单调递增 —— 再造一个 0.35 和 0.4 混用的情况会立刻失败；
 *  - 触摸目标不掉到 48dp 以下 —— 这是本次外观改造的硬约束；
 *  - 相对时间与字符计数的文案不变 —— 首页与阅读器页首都直接展示它们。
 */
class DesignTokensTest {

    @Test
    fun spacingScaleIsMonotonic() {
        val values = listOf(Sp.xs, Sp.s, Sp.m, Sp.l, Sp.xl, Sp.xxl)
        values.zipWithNext().forEach { (a, b) ->
            assertTrue("间距令牌必须递增：" + a + " -> " + b, b.value > a.value)
        }
    }

    @Test
    fun radiusScaleIsMonotonic() {
        val values = listOf(Radius.s, Radius.m, Radius.l, Radius.pill)
        values.zipWithNext().forEach { (a, b) ->
            assertTrue("圆角令牌必须递增：" + a + " -> " + b, b.value > a.value)
        }
    }

    @Test
    fun typeScaleIsMonotonic() {
        val values = listOf(
            Type.label, Type.caption, Type.body, Type.bodyLarge,
            Type.title, Type.heading, Type.display
        )
        values.zipWithNext().forEach { (a, b) ->
            assertTrue("字阶令牌必须递增：" + a + " -> " + b, b.value > a.value)
        }
    }

    @Test
    fun alphaTokensStayInRange() {
        listOf(Alpha.faint, Alpha.disabled, Alpha.soft, Alpha.progress, Alpha.decorative, Alpha.hairline)
            .forEach { assertTrue("透明度必须在 (0,1)：$it", it > 0f && it < 1f) }
    }

    @Test
    fun elevationAndMotionAreOrdered() {
        assertTrue(Elevation.flat.value < Elevation.card.value)
        assertTrue(Elevation.card.value < Elevation.sheet.value)
        assertTrue(Elevation.sheet.value < Elevation.overlay.value)
        assertTrue(Motion.fast < Motion.base)
        assertTrue(Motion.base < Motion.slow)
    }

    @Test
    fun touchTargetMeetsAccessibilityMinimum() {
        assertEquals(48f, MinTouchTarget.value, 0.01f)
    }

    @Test
    fun relativeTimeBuckets() {
        val now = System.currentTimeMillis()
        assertEquals("刚刚", relativeTime(now))
        assertEquals("刚刚", relativeTime(now + 10_000L))
        assertEquals("5 分钟前", relativeTime(now - 5 * 60_000L))
        assertEquals("3 小时前", relativeTime(now - 3 * 3_600_000L))
        assertEquals("2 天前", relativeTime(now - 2 * 86_400_000L))
        assertEquals("1 周前", relativeTime(now - 8 * 86_400_000L))
    }

    @Test
    fun charCountIsGrouped() {
        assertEquals("", formatCharCount(0))
        assertEquals("", formatCharCount(-5))
        assertEquals("12 字", formatCharCount(12))
        assertEquals("1,234 字", formatCharCount(1234))
        assertEquals("1,234,567 字", formatCharCount(1234567))
    }

    @Test
    fun formatLabelsCoverEveryFormat() {
        // 每一种格式都必须有中文名，否则页首元信息会出现空字符串
        DocFormat.entries.forEach { format ->
            assertTrue("缺少格式名：" + format, formatLabel(format).isNotBlank())
        }
    }
}

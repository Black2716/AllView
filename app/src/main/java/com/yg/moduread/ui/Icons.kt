package com.yg.moduread.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 界面图标：用 Canvas 直接画，不引入矢量图资源、不引入图标库。
 *
 * 三次体积实测（R8 后 release 字节数，基线 1,241,595）：
 *   res/drawable + painterResource  → 1,312,147  (+70 KB，把矢量解析引擎整包拖进来)
 *   内存 ImageVector + PathBuilder  → 1,280,687  (+39 KB，解析引擎仍在)
 *   Canvas 直绘（本文件）           → 见 README
 *
 * 这几个图标只有直线、圆和点，画布原语就够了 ——
 * 为 7 个简单图形背一整套矢量图引擎不划算。
 */
object MiIcons {

    private const val STROKE = 1.8f

    /** 目录：三条横线，长度递减。 */
    @Composable
    fun Toc(tint: Color, size: Dp = 22.dp) = Frame(tint, size) { w, h, sw ->
        val x0 = w * 0.16f
        listOf(0.32f to 1f, 0.52f to 1f, 0.72f to 0.62f).forEach { (y, len) ->
            drawLine(
                color = tint,
                start = Offset(x0, h * y),
                end = Offset(x0 + (w - 2 * x0) * len, h * y),
                strokeWidth = sw,
                cap = StrokeCap.Round
            )
        }
    }

    /** 搜索：圆环 + 手柄。 */
    @Composable
    fun Search(tint: Color, size: Dp = 22.dp) = Frame(tint, size) { w, h, sw ->
        val r = w * 0.30f
        val c = Offset(w * 0.42f, h * 0.42f)
        drawCircle(color = tint, radius = r, center = c, style = Stroke(width = sw))
        drawLine(
            color = tint,
            start = Offset(c.x + r * 0.72f, c.y + r * 0.72f),
            end = Offset(w * 0.84f, h * 0.84f),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
    }

    /** 更多：三个点。 */
    @Composable
    fun More(tint: Color, size: Dp = 22.dp) = Frame(tint, size) { w, h, _ ->
        val r = w * 0.075f
        listOf(0.24f, 0.5f, 0.76f).forEach { x ->
            drawCircle(color = tint, radius = r, center = Offset(w * x, h * 0.5f))
        }
    }

    /** 关闭：叉。 */
    @Composable
    fun Close(tint: Color, size: Dp = 16.dp) = Frame(tint, size) { w, h, sw ->
        val a = Offset(w * 0.24f, h * 0.24f)
        val b = Offset(w * 0.76f, h * 0.76f)
        drawLine(tint, a, b, sw, StrokeCap.Round)
        drawLine(tint, Offset(b.x, a.y), Offset(a.x, b.y), sw, StrokeCap.Round)
    }

    /**
     * 统一的尺寸/线宽注入。
     * 线宽按边长等比缩放，这样同一个图标在 16dp 和 22dp 下看起来「一样粗」。
     */
    @Composable
    private fun Frame(
        tint: Color,
        size: Dp,
        draw: androidx.compose.ui.graphics.drawscope.DrawScope.(w: Float, h: Float, sw: Float) -> Unit
    ) {
        Canvas(Modifier.size(size)) {
            draw(this.size.width, this.size.height, this.size.width * STROKE / 22f)
        }
    }

    /** 字号图标用文字「Aa」更直观，单独提供一个 Composable 而不是画布路径。 */
    const val FONT_LABEL = "Aa"
}

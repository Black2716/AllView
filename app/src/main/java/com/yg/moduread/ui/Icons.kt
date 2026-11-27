package com.yg.moduread.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
 * 这几个图标只有直线、圆、弧和点，画布原语就够了 ——
 * 为十来个简单图形背一整套矢量图引擎不划算。
 *
 * 2026-11 UI 重塑：从 6 个图形扩到 20 个。加图形仍然是纯 Canvas 路径，
 * 每个约 20~40 行，R8 后增量以百字节计，不改变体积量级。
 * 所有图形共用 22dp 基准画布与 STROKE=1.8 线宽，视觉重量一致。
 */
object MiIcons {

    private const val STROKE = 1.8f

    /** 目录：三条横线，长度递减。 */
    @Composable
    fun Toc(tint: Color, size: Dp = 22.dp) = Frame(size) { w, h, sw ->
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
    fun Search(tint: Color, size: Dp = 22.dp) = Frame(size) { w, h, sw ->
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
    fun More(tint: Color, size: Dp = 22.dp) = Frame(size) { w, h, _ ->
        val r = w * 0.075f
        listOf(0.24f, 0.5f, 0.76f).forEach { x ->
            drawCircle(color = tint, radius = r, center = Offset(w * x, h * 0.5f))
        }
    }

    /** 关闭：叉。 */
    @Composable
    fun Close(tint: Color, size: Dp = 16.dp) = Frame(size) { w, h, sw ->
        val a = Offset(w * 0.24f, h * 0.24f)
        val b = Offset(w * 0.76f, h * 0.76f)
        drawLine(tint, a, b, sw, StrokeCap.Round)
        drawLine(tint, Offset(b.x, a.y), Offset(a.x, b.y), sw, StrokeCap.Round)
    }

    /** 返回：左尖括号。 */
    @Composable
    fun Back(tint: Color, size: Dp = 22.dp) = Frame(size) { w, h, sw ->
        drawLine(tint, Offset(w * 0.60f, h * 0.22f), Offset(w * 0.34f, h * 0.5f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.34f, h * 0.5f), Offset(w * 0.60f, h * 0.78f), sw, StrokeCap.Round)
    }

    /** 进入：右尖括号，用于「打开某份文档」的二层入口。 */
    @Composable
    fun Chevron(tint: Color, size: Dp = 18.dp) = Frame(size) { w, h, sw ->
        drawLine(tint, Offset(w * 0.42f, h * 0.24f), Offset(w * 0.62f, h * 0.5f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.62f, h * 0.5f), Offset(w * 0.42f, h * 0.76f), sw, StrokeCap.Round)
    }

    /** 展开/收起：向下箭头。 */
    @Composable
    fun ChevronDown(tint: Color, size: Dp = 18.dp) = Frame(size) { w, h, sw ->
        drawLine(tint, Offset(w * 0.26f, h * 0.42f), Offset(w * 0.5f, h * 0.64f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.5f, h * 0.64f), Offset(w * 0.74f, h * 0.42f), sw, StrokeCap.Round)
    }

    /** 对勾：独立使用（进度完成、选中标记）。 */
    @Composable
    fun Check(tint: Color, size: Dp = 18.dp) = Frame(size) { w, h, sw ->
        drawLine(tint, Offset(w * 0.24f, h * 0.54f), Offset(w * 0.43f, h * 0.72f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.43f, h * 0.72f), Offset(w * 0.77f, h * 0.30f), sw, StrokeCap.Round)
    }

    /** 设置：三条带滑块的横线（比齿轮在小尺寸下更清楚，也不会有 12 个齿的锯齿感）。 */
    @Composable
    fun Tune(tint: Color, size: Dp = 22.dp) = Frame(size) { w, h, sw ->
        val x0 = w * 0.16f
        val x1 = w * 0.84f
        listOf(0.28f to 0.36f, 0.50f to 0.64f, 0.72f to 0.44f).forEach { (y, knob) ->
            drawLine(tint, Offset(x0, h * y), Offset(x1, h * y), sw, StrokeCap.Round)
            drawCircle(tint, radius = w * 0.085f, center = Offset(x0 + (x1 - x0) * knob, h * y))
        }
    }

    /** 书签：缎带。 */
    @Composable
    fun Bookmark(tint: Color, size: Dp = 22.dp) = Frame(size) { w, h, sw ->
        val l = w * 0.28f
        val r = w * 0.72f
        val t = h * 0.20f
        val b = h * 0.82f
        val notch = b - (h * 0.16f)
        drawLine(tint, Offset(l, t), Offset(r, t), sw, StrokeCap.Round)
        drawLine(tint, Offset(l, t), Offset(l, b), sw, StrokeCap.Round)
        drawLine(tint, Offset(r, t), Offset(r, b), sw, StrokeCap.Round)
        drawLine(tint, Offset(l, b), Offset(w * 0.5f, notch), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.5f, notch), Offset(r, b), sw, StrokeCap.Round)
    }

    /** 文件：右上折角的纸张。 */
    @Composable
    fun File(tint: Color, size: Dp = 22.dp) = Frame(size) { w, h, sw ->
        val l = w * 0.24f
        val r = w * 0.76f
        val t = h * 0.16f
        val b = h * 0.84f
        val fold = w * 0.18f
        drawLine(tint, Offset(l, t), Offset(r - fold, t), sw, StrokeCap.Round)
        drawLine(tint, Offset(r - fold, t), Offset(r, t + fold), sw, StrokeCap.Round)
        drawLine(tint, Offset(r, t + fold), Offset(r, b), sw, StrokeCap.Round)
        drawLine(tint, Offset(r, b), Offset(l, b), sw, StrokeCap.Round)
        drawLine(tint, Offset(l, b), Offset(l, t), sw, StrokeCap.Round)
        // 两条正文线，让「文件」与「空方块」区分开
        drawLine(tint, Offset(w * 0.36f, h * 0.52f), Offset(w * 0.64f, h * 0.52f), sw * 0.8f, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.36f, h * 0.66f), Offset(w * 0.56f, h * 0.66f), sw * 0.8f, StrokeCap.Round)
    }

    /** 文件夹：开口梯形。 */
    @Composable
    fun Folder(tint: Color, size: Dp = 22.dp) = Frame(size) { w, h, sw ->
        val l = w * 0.18f
        val r = w * 0.82f
        val t = h * 0.26f
        val b = h * 0.78f
        val tabTo = w * 0.44f
        drawLine(tint, Offset(l, b), Offset(l, t), sw, StrokeCap.Round)
        drawLine(tint, Offset(l, t), Offset(tabTo, t), sw, StrokeCap.Round)
        drawLine(tint, Offset(tabTo, t), Offset(tabTo + w * 0.08f, t + h * 0.1f), sw, StrokeCap.Round)
        drawLine(tint, Offset(tabTo + w * 0.08f, t + h * 0.1f), Offset(r, t + h * 0.1f), sw, StrokeCap.Round)
        drawLine(tint, Offset(r, t + h * 0.1f), Offset(r, b), sw, StrokeCap.Round)
        drawLine(tint, Offset(r, b), Offset(l, b), sw, StrokeCap.Round)
    }

    /** 亮色主题：太阳。 */
    @Composable
    fun Sun(tint: Color, size: Dp = 20.dp) = Frame(size) { w, h, sw ->
        val c = Offset(w * 0.5f, h * 0.5f)
        drawCircle(tint, radius = w * 0.20f, center = c, style = Stroke(width = sw))
        val inner = w * 0.31f
        val outer = w * 0.42f
        for (i in 0 until 8) {
            val a = (Math.PI / 4.0 * i).toFloat()
            val dx = kotlin.math.cos(a)
            val dy = kotlin.math.sin(a)
            drawLine(
                tint,
                Offset(c.x + dx * inner, c.y + dy * inner),
                Offset(c.x + dx * outer, c.y + dy * outer),
                sw,
                StrokeCap.Round
            )
        }
    }

    /** 暗色主题：月牙。用两段圆弧拼出，比布尔运算便宜。 */
    @Composable
    fun Moon(tint: Color, size: Dp = 20.dp) = Frame(size) { w, h, sw ->
        val outer = Rect(w * 0.22f, h * 0.16f, w * 0.80f, h * 0.84f)
        val path = Path()
        path.arcTo(outer, startAngleDegrees = 70f, sweepAngleDegrees = 260f, forceMoveTo = true)
        val inner = Rect(w * 0.34f, h * 0.10f, w * 0.94f, h * 0.88f)
        path.arcTo(inner, startAngleDegrees = 250f, sweepAngleDegrees = -150f, forceMoveTo = false)
        drawPath(path, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round))
    }

    /** 纸张：带折角的页面 + 一条基线，与「文件」区分在于更宽的横纵比。 */
    @Composable
    fun Paper(tint: Color, size: Dp = 20.dp) = Frame(size) { w, h, sw ->
        drawLine(tint, Offset(w * 0.24f, h * 0.24f), Offset(w * 0.76f, h * 0.24f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.24f, h * 0.50f), Offset(w * 0.64f, h * 0.50f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.24f, h * 0.76f), Offset(w * 0.56f, h * 0.76f), sw, StrokeCap.Round)
    }

    /** 字号减小：小号 A 加一道减号。 */
    @Composable
    fun FontMinus(tint: Color, size: Dp = 18.dp) = Frame(size) { w, h, sw ->
        drawLine(tint, Offset(w * 0.22f, h * 0.5f), Offset(w * 0.78f, h * 0.5f), sw, StrokeCap.Round)
    }

    /** 字号增大：加号。 */
    @Composable
    fun FontPlus(tint: Color, size: Dp = 18.dp) = Frame(size) { w, h, sw ->
        drawLine(tint, Offset(w * 0.22f, h * 0.5f), Offset(w * 0.78f, h * 0.5f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.5f, h * 0.22f), Offset(w * 0.5f, h * 0.78f), sw, StrokeCap.Round)
    }

    /** 删除：垃圾桶。 */
    @Composable
    fun Trash(tint: Color, size: Dp = 18.dp) = Frame(size) { w, h, sw ->
        drawLine(tint, Offset(w * 0.22f, h * 0.30f), Offset(w * 0.78f, h * 0.30f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.42f, h * 0.30f), Offset(w * 0.42f, h * 0.20f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.58f, h * 0.30f), Offset(w * 0.58f, h * 0.20f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.42f, h * 0.20f), Offset(w * 0.58f, h * 0.20f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.30f, h * 0.30f), Offset(w * 0.34f, h * 0.80f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.70f, h * 0.30f), Offset(w * 0.66f, h * 0.80f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.34f, h * 0.80f), Offset(w * 0.66f, h * 0.80f), sw, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.5f, h * 0.42f), Offset(w * 0.5f, h * 0.68f), sw * 0.8f, StrokeCap.Round)
    }

    /** 空态：一叠文档（比单个文件更能表达「这里还没有内容」）。 */
    @Composable
    fun Stack(tint: Color, size: Dp = 40.dp) = Frame(size) { w, h, sw ->
        val l = w * 0.26f
        val r = w * 0.74f
        // 后面两层错位的纸边
        drawLine(tint, Offset(l + w * 0.10f, h * 0.26f), Offset(r, h * 0.26f), sw * 0.9f, StrokeCap.Round)
        drawLine(tint, Offset(l + w * 0.05f, h * 0.38f), Offset(r - w * 0.05f, h * 0.38f), sw * 0.9f, StrokeCap.Round)
        // 最前的一张
        drawLine(tint, Offset(l, h * 0.50f), Offset(r, h * 0.50f), sw, StrokeCap.Round)
        drawLine(tint, Offset(l, h * 0.50f), Offset(l, h * 0.80f), sw, StrokeCap.Round)
        drawLine(tint, Offset(r, h * 0.50f), Offset(r, h * 0.80f), sw, StrokeCap.Round)
        drawLine(tint, Offset(l, h * 0.80f), Offset(r, h * 0.80f), sw, StrokeCap.Round)
    }

    /** 搜索空结果：放大镜里一道斜杠，靠 Search + 额外一笔组合出来。 */
    @Composable
    fun SearchOff(tint: Color, size: Dp = 32.dp) = Frame(size) { w, h, sw ->
        val r = w * 0.26f
        val c = Offset(w * 0.44f, h * 0.42f)
        drawCircle(tint, radius = r, center = c, style = Stroke(width = sw))
        drawLine(
            tint,
            Offset(c.x + r * 0.70f, c.y + r * 0.70f),
            Offset(w * 0.80f, h * 0.80f),
            sw,
            StrokeCap.Round
        )
        drawLine(tint, Offset(w * 0.30f, h * 0.56f), Offset(w * 0.58f, h * 0.28f), sw, StrokeCap.Round)
    }

    /**
     * 统一的尺寸/线宽注入。
     * 线宽按边长等比缩放，这样同一个图标在 16dp 和 22dp 下看起来「一样粗」。
     */
    @Composable
    private fun Frame(
        size: Dp,
        draw: androidx.compose.ui.graphics.drawscope.DrawScope.(w: Float, h: Float, sw: Float) -> Unit
    ) {
        // 颜色不由 Frame 传：每个图标的 draw 闭包自己捕获 tint。
        // 之前多传一个 tint 参数，闭包又不用它，编译器只能把它标成「未使用」。
        Canvas(Modifier.size(size)) {
            draw(this.size.width, this.size.height, this.size.width * STROKE / 22f)
        }
    }

    /** 任务列表勾选框：未勾是空心方框，已勾是方框 + 对勾。 */
    @Composable
    fun TaskBox(checked: Boolean, tint: Color, size: Dp = 14.dp) = Frame(size) { w, h, sw ->
        val pad = w * 0.14f
        // 方框
        drawLine(tint, Offset(pad, pad), Offset(w - pad, pad), sw, StrokeCap.Round)
        drawLine(tint, Offset(w - pad, pad), Offset(w - pad, h - pad), sw, StrokeCap.Round)
        drawLine(tint, Offset(w - pad, h - pad), Offset(pad, h - pad), sw, StrokeCap.Round)
        drawLine(tint, Offset(pad, h - pad), Offset(pad, pad), sw, StrokeCap.Round)
        if (checked) {
            // 对勾
            drawLine(tint, Offset(w * 0.28f, h * 0.52f), Offset(w * 0.44f, h * 0.70f), sw, StrokeCap.Round)
            drawLine(tint, Offset(w * 0.44f, h * 0.70f), Offset(w * 0.74f, h * 0.32f), sw, StrokeCap.Round)
        }
    }

    /** 字号图标用文字「Aa」更直观，单独提供一个 Composable 而不是画布路径。 */
    const val FONT_LABEL = "Aa"
}

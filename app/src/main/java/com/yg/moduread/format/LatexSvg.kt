package com.yg.moduread.format

import com.yg.moduread.md.Latex
import com.yg.moduread.md.MathNode
import java.util.Locale

/**
 * 把 LaTeX 排版树画成 SVG。
 *
 * 为什么复用 MathNode 而不是自己解析 OMML：
 * OMML 的结构语义（哪个是底、哪个是指数、谁是上下限）
 * 已经由 OmmlToLatex 转成 LaTeX，而 Latex.parse 已把这些语义吃透。
 * 在语法树上做布局，比在 OMML 上做两件事同时干要可靠。
 *
 * 布局模型：每个节点返回 (svg, 宽, 上升, 下降)，父节点拼接。
 */
object LatexSvg {

    data class Box(val svg: String, val w: Double, val asc: Double, val desc: Double)

    /** 渲染一段 LaTeX。字号单位 pt。 */
    fun render(latex: String, fs: Double, color: String): Box {
        val tree = Latex.parse(latex)
        return node(tree, fs, color)
    }

    private fun node(n: MathNode, fs: Double, color: String): Box = when (n) {
        is MathNode.Row -> hbox(n.children.map { node(it, fs, color) })
        is MathNode.Text -> text(n.text, fs, color, n.bold)
        is MathNode.Space -> Box("", fs * n.widthEm, 0.0, 0.0)
        is MathNode.Frac -> frac(n, fs, color)
        is MathNode.Script -> script(n, fs, color)
        is MathNode.BigOp -> bigOp(n, fs, color)
        is MathNode.Delim -> delim(n, fs, color)
        is MathNode.Sqrt -> sqrt(n, fs, color)
        is MathNode.Accent -> accent(n, fs, color)
        is MathNode.Arrow -> arrow(n, fs, color)
    }

    private fun frac(f: MathNode.Frac, fs: Double, color: String): Box {
        val s = fs * 0.9
        val num = node(f.num, s, color)
        val den = node(f.den, s, color)
        val pad = fs * 0.16
        val w = maxOf(num.w, den.w) + pad * 2
        val barY = -fs * 0.42
        val sb = StringBuilder()
        val numDy = barY - fs * 0.1 - num.desc
        val denDy = barY + fs * 0.1 + den.asc
        sb.append(move((w - num.w) / 2, numDy, num.svg))
        sb.append(rect(0.0, barY, w, fs * 0.055, color))
        sb.append(move((w - den.w) / 2, denDy, den.svg))
        return Box(sb.toString(), w, maxOf(-numDy + num.asc, fs * 0.5), maxOf(denDy + den.desc, fs * 0.5))
    }

    private fun script(s: MathNode.Script, fs: Double, color: String): Box {
        val base = node(s.base, fs, color)
        val sb = StringBuilder()
        sb.append(base.svg)

        // 角标一律排在底数的右上 / 右下，与常规数学排版一致。
        //
        // 之前用宽度阈值判断，超过就居中到正上/正下 ——
        // 结果绝大多数角标都跑到了正上正下（用户实测反馈）。
        //
        // 现在：位置固定右上/右下；脚本过长时靠 limitScript 缩字号，
        // 并允许宽度右扩，保证「看得出是角标」且不压到后续元素。
        val sup = s.sup?.let { limitScript(node(it, fs * 0.68, color), fs) }
        val sub = s.sub?.let { limitScript(node(it, fs * 0.68, color), fs) }

        var asc = base.asc
        var desc = base.desc

        if (sup != null) {
            // 起点贴住底数右边缘略往内，上移约 0.42em
            val dx = base.w - fs * 0.05
            val dy = -fs * 0.42
            sb.append(move(dx, dy, sup.svg))
            asc = maxOf(asc, -dy + sup.asc)
        }
        if (sub != null) {
            val dx = base.w - fs * 0.05
            val dy = fs * 0.16
            sb.append(move(dx, dy + sub.asc, sub.svg))
            desc = maxOf(desc, dy + sub.asc + sub.desc)
        }

        val extraW = maxOf(sup?.w ?: 0.0, sub?.w ?: 0.0)
        return Box(sb.toString(), base.w + extraW, asc, desc)
    }

    /**
     * 限制单个脚本的宽度：过宽时按比例缩字号重排。
     *
     * 脚本过长（例如 max 下方的一长串条件）如果原样右移，
     * 会把整行推到页面外。缩到不超过 1.6 倍基准字号宽即可容纳。
     */
    private fun limitScript(b: Box, fs: Double): Box {
        val maxW = fs * 6.0
        if (b.w <= maxW || b.w <= 0.0) return b
        val k = maxW / b.w
        // 用 transform 等比缩小，保持内部相对位置
        val scaled = "<g transform=" + q("scale(" + f(k) + ")") + ">" + b.svg + "</g>"
        return Box(scaled, b.w * k, b.asc * k, b.desc * k)
    }

    private fun bigOp(op: MathNode.BigOp, fs: Double, color: String): Box {
        val symSize = fs * 1.55
        val small = fs * 0.6
        val sup = op.sup?.let { node(it, small, color) }
        val sub = op.sub?.let { node(it, small, color) }
        val symW = symSize * 0.85
        val colW = maxOf(symW, sup?.w ?: 0.0, sub?.w ?: 0.0)
        val sb = StringBuilder()
        var asc = symSize * 0.75
        var desc = symSize * 0.25
        if (sup != null) {
            sb.append(move((colW - sup.w) / 2, -symSize * 0.7 - sup.desc, sup.svg))
            asc = symSize * 0.7 + sup.asc + sup.desc
        }
        sb.append(symText(op.symbol, colW / 2, 0.0, symSize, color))
        if (sub != null) {
            sb.append(move((colW - sub.w) / 2, symSize * 0.42 + sub.asc, sub.svg))
            desc = symSize * 0.42 + sub.asc + sub.desc
        }
        return Box(sb.toString(), colW, asc, desc)
    }

    private fun delim(d: MathNode.Delim, fs: Double, color: String): Box {
        val body = node(d.body, fs, color)
        val pad = fs * 0.1
        val top = body.asc + fs * 0.1
        val bot = body.desc + fs * 0.1
        val sb = StringBuilder()
        val openW = fs * 0.32
        val closeW = fs * 0.32
        sb.append(paren(0.0, top, bot, openW, color, true))
        sb.append(move(openW + pad, 0.0, body.svg))
        sb.append(paren(openW + pad * 2 + body.w, top, bot, closeW, color, false))
        return Box(sb.toString(), openW + pad * 2 + body.w + closeW, top + fs * 0.05, bot + fs * 0.05)
    }

    private fun sqrt(s: MathNode.Sqrt, fs: Double, color: String): Box {
        val body = node(s.body, fs, color)
        val h = body.asc + body.desc
        val hookW = fs * 0.42
        val sb = StringBuilder()
        val d = "M0," + f(-fs * 0.12) + " L" + f(fs * 0.18) + "," + f(-fs * 0.3) +
            " L" + f(hookW) + "," + f(-h - fs * 0.06) + " L" + f(hookW + fs * 0.16) + "," + f(-h - fs * 0.2)
        sb.append("<path d=" + q(d) + " stroke=" + q(color) + " stroke-width=" + q(f(fs * 0.07)) + " fill=" + q("none") + "/>")
        sb.append(move(hookW + fs * 0.2, 0.0, body.svg))
        return Box(sb.toString(), hookW + fs * 0.2 + body.w, body.asc + fs * 0.2, body.desc)
    }

    private fun accent(a: MathNode.Accent, fs: Double, color: String): Box {
        val body = node(a.body, fs, color)
        val sb = StringBuilder()
        val w = body.w
        val y = -body.asc - fs * 0.16
        sb.append("<path d=" + q("M0," + f(y) + " Q" + f(w / 2) + "," + f(y - fs * 0.16) + " " + f(w) + "," + f(y)) +
            " stroke=" + q(color) + " stroke-width=" + q(f(fs * 0.06)) + " fill=" + q("none") + "/>")
        sb.append(body.svg)
        return Box(sb.toString(), w, body.asc + fs * 0.24, body.desc)
    }

    private fun arrow(a: MathNode.Arrow, fs: Double, color: String): Box {
        val label = a.label?.let { node(it, fs * 0.7, color) }
        val w = maxOf(fs * 2.0, label?.w ?: 0.0)
        val sb = StringBuilder()
        var asc = fs * 0.3
        if (label != null) {
            sb.append(move((w - label.w) / 2, -fs * 0.5 - label.desc, label.svg))
            asc = fs * 0.5 + label.asc
        }
        sb.append("<path d=" + q("M0,0 L" + f(w) + ",0") + " stroke=" + q(color) +
            " stroke-width=" + q(f(fs * 0.05)) + " fill=" + q("none") + "/>")
        sb.append("<path d=" + q("M" + f(w - fs * 0.16) + "," + f(-fs * 0.1) + " L" + f(w) + ",0 L" + f(w - fs * 0.16) + "," + f(fs * 0.1)) +
            " stroke=" + q(color) + " stroke-width=" + q(f(fs * 0.05)) + " fill=" + q("none") + "/>")
        return Box(sb.toString(), w, asc, fs * 0.2)
    }

    private fun text(t: String, fs: Double, color: String, bold: Boolean): Box {
        if (t.isEmpty()) return Box("", 0.0, 0.0, 0.0)
        var w = 0.0
        for (ch in t) w += charW(ch, fs)
        val weight = if (bold) q("bold") else q("normal")
        val sb = "<text x=" + q("0") + " y=" + q("0") + " font-size=" + q(f(fs)) +
            " fill=" + q(color) + " font-family=" + q("serif") + " font-weight=" + weight + ">" + esc(t) + "</text>"
        return Box(sb, w, fs * 0.72, fs * 0.2)
    }

    private fun symText(sym: String, cx: Double, by: Double, fs: Double, color: String): String =
        "<text x=" + q(f(cx)) + " y=" + q(f(by)) + " font-size=" + q(f(fs)) +
            " text-anchor=" + q("middle") + " fill=" + q(color) + " font-family=" + q("serif") + ">" + esc(sym) + "</text>"

    private fun paren(x: Double, top: Double, bot: Double, w: Double, color: String, left: Boolean): String {
        val mid = (top - bot) / 2
        val bulge = w * 0.9
        val sx = x + (if (left) w else 0.0)
        val d = "M" + f(sx) + "," + f(-top) + " Q" + f(x + (if (left) -bulge else w + bulge)) + "," + f(-mid) +
            " " + f(sx) + "," + f(bot)
        return "<path d=" + q(d) + " stroke=" + q(color) + " stroke-width=" + q(f(w * 0.28)) + " fill=" + q("none") + "/>"
    }

    private fun hbox(items: List<Box>): Box {
        if (items.isEmpty()) return Box("", 0.0, 0.0, 0.0)
        val sb = StringBuilder()
        var x = 0.0
        var asc = 0.0
        var desc = 0.0
        for (b in items) {
            if (b.svg.isNotEmpty()) sb.append(move(x, 0.0, b.svg))
            x += b.w
            if (b.asc > asc) asc = b.asc
            if (b.desc > desc) desc = b.desc
        }
        return Box(sb.toString(), x, asc, desc)
    }

    private fun move(dx: Double, dy: Double, inner: String): String {
        if (inner.isEmpty()) return ""
        return "<g transform=" + q("translate(" + f(dx) + "," + f(dy) + ")") + ">" + inner + "</g>"
    }

    private fun rect(x: Double, y: Double, w: Double, h: Double, color: String): String =
        "<rect x=" + q(f(x)) + " y=" + q(f(y)) + " width=" + q(f(w)) + " height=" + q(f(h)) + " fill=" + q(color) + "/>"

    private fun charW(ch: Char, fs: Double): Double {
        val c = ch.code
        return when {
            c in 0x4E00..0x9FFF -> fs
            c in 0x3000..0x303F -> fs
            c in 0xFF00..0xFFEF -> fs
            c in 0x2200..0x22FF -> fs * 0.85
            c == 0x2211 || c == 0x220F -> fs * 1.1
            ch == ' ' -> fs * 0.3
            ch in "il.,;:|" -> fs * 0.3
            ch in "mwMW" -> fs * 0.85
            else -> fs * 0.55
        }
    }

    private fun f(v: Double): String = String.format(Locale.US, "%.2f", v)

    private fun q(s: String): String = "\"" + s + "\""

    private fun esc(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                "<"[0] -> sb.append("&lt;")
                ">"[0] -> sb.append("&gt;")
                "&"[0] -> sb.append("&amp;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}
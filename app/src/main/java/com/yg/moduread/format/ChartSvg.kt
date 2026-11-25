package com.yg.moduread.format

import org.w3c.dom.Element
import java.util.Locale

/**
 * 把 PPTX 图表（c:chart 引用的 chartN.xml）画成 SVG。
 *
 * 当前覆盖柱状图（c:barChart），这是实际文档里最常见的类型。
 * 折线图 / 饼图 / 散点图的标签结构类似，后续可按需扩展。
 *
 * 为什么不复用图表图片：PPTX 里的 chart 不是图片，而是数据 + 样式，
 * 没有可直接渲染的位图，必须自己画。
 */
object ChartSvg {

    /** 图表内容。 */
    private data class Series(val name: String, val values: List<Double>)

    /**
     * 渲染图表。
     *
     * @param chartXml chartN.xml 的全文
     * @param w 目标宽度（pt）
     * @param h 目标高度（pt）
     * @param color 文字颜色
     */
    fun render(chartXml: String, w: Double, h: Double, color: String): String {
        val root = ContainerSupport.parseXml(chartXml) ?: return ""
        val plot = findDescendant(root, "plotArea") ?: return ""
        val bar = findDescendant(plot, "barChart")
            ?: findDescendant(plot, "lineChart")
            ?: return ""

        val cats = readCategories(bar)
        val series = readSeries(bar)
        if (cats.isEmpty() || series.isEmpty()) return ""

        val maxV = series.flatMap { it.values }.maxOrNull() ?: 1.0
        val axisMax = niceMax(maxV)

        // 版面：左侧留出刻度，底部留出类别名，顶部留出标题
        val padL = w * 0.1
        val padR = w * 0.04
        val padT = h * 0.16
        val padB = h * 0.2
        val plotW = (w - padL - padR).coerceAtLeast(1.0)
        val plotH = (h - padT - padB).coerceAtLeast(1.0)

        val sb = StringBuilder()
        sb.append("<g>")

        // 坐标轴
        val axisX = padL
        val axisY = padT + plotH
        sb.append(line(axisX, padT, axisX, axisY, "#999999", 0.8))
        sb.append(line(axisX, axisY, padL + plotW, axisY, "#999999", 0.8))

        // Y 轴刻度（4 段）
        for (t in 0..4) {
            val v = axisMax * t / 4.0
            val y = axisY - plotH * t / 4.0
            sb.append(line(axisX - 3, y, axisX, y, "#cccccc", 0.6))
            sb.append(text(axisX - 5, y + 3, fmtNum(v), h * 0.045, color, "end"))
        }

        // 柱形
        val groupW = plotW / cats.size
        val barGap = groupW * 0.22
        val barsTotalW = groupW - barGap * 2
        val barW = barsTotalW / series.size
        val palette = arrayOf("#3B7DD8", "#E0663A", "#59A14F", "#B07AA1")

        cats.forEachIndexed { ci, _ ->
            series.forEachIndexed { si, s ->
                val v = s.values.getOrNull(ci) ?: return@forEachIndexed
                val bh = plotH * (v / axisMax)
                val bx = padL + groupW * ci + barGap + barW * si
                val by = axisY - bh
                sb.append("<rect x=" + q(f(bx)) + " y=" + q(f(by))
                    + " width=" + q(f(barW * 0.92)) + " height=" + q(f(bh))
                    + " fill=" + q(palette[si % palette.size]) + "/>")
                // 数值标在柱顶
                sb.append(text(bx + barW * 0.46, by - 4, fmtNum(v), h * 0.045, color, "middle"))
            }
            // 类别名
            val cx = padL + groupW * ci + groupW / 2
            sb.append(text(cx, axisY + h * 0.07, cats[ci], h * 0.048, color, "middle"))
        }

        // 系列名（图例）
        series.forEachIndexed { si, s ->
            val lx = padL + si * w * 0.3
            val ly = h * 0.05
            sb.append("<rect x=" + q(f(lx)) + " y=" + q(f(ly - 6))
                + " width=" + q(f(h * 0.03)) + " height=" + q(f(h * 0.03))
                + " fill=" + q(palette[si % palette.size]) + "/>")
            sb.append(text(lx + h * 0.045, ly, s.name, h * 0.05, color, "start"))
        }

        sb.append("</g>")
        return sb.toString()
    }

    private fun readCategories(bar: Element): List<String> {
        val cat = findDescendant(bar, "cat") ?: return emptyList()
        return readPoints(cat).map { it.second }
    }

    private fun readSeries(bar: Element): List<Series> {
        val out = ArrayList<Series>()
        for (ser in allChildren(bar, "ser")) {
            val nameEl = findDescendant(ser, "tx")
            val name = nameEl?.let { readPoints(it).firstOrNull()?.second }.orEmpty()
            val valEl = findDescendant(ser, "val") ?: continue
            val vals = readPoints(valEl).mapNotNull { it.second.toDoubleOrNull() }
            if (vals.isNotEmpty()) out.add(Series(name.ifEmpty { "系列" + (out.size + 1) }, vals))
        }
        return out
    }

    /** 读 c:pt 列表，返回 (索引, 值)。 */
    private fun readPoints(parent: Element): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        val pts = parent.getElementsByTagName("c:pt")
        for (i in 0 until pts.length) {
            val pt = pts.item(i) as? Element ?: continue
            val idx = pt.getAttribute("idx").toIntOrNull() ?: out.size
            val v = pt.getElementsByTagName("c:v")
            val text = if (v.length > 0) (v.item(0) as? Element)?.textContent.orEmpty() else ""
            out.add(idx to text)
        }
        return out.sortedBy { it.first }
    }

    /** 把最大值向上取整到易读刻度（1/2/5 的整数倍）。 */
    private fun niceMax(v: Double): Double {
        if (v <= 0) return 1.0
        val exp = Math.floor(Math.log10(v))
        val base = Math.pow(10.0, exp)
        val n = v / base
        val step = when {
            n <= 1.0 -> 1.0
            n <= 2.0 -> 2.0
            n <= 5.0 -> 5.0
            else -> 10.0
        }
        return step * base
    }

    private fun fmtNum(v: Double): String {
        val s = if (v >= 1.0) {
            String.format(Locale.US, "%.2f", v)
        } else {
            String.format(Locale.US, "%.3f", v)
        }
        return s.trimEnd('0').trimEnd('.')
    }

    private fun line(x1: Double, y1: Double, x2: Double, y2: Double, c: String, sw: Double): String {
        val sb = StringBuilder()
        sb.append("<line x1=").append(q(f(x1))).append(" y1=").append(q(f(y1)))
        sb.append(" x2=").append(q(f(x2))).append(" y2=").append(q(f(y2)))
        sb.append(" stroke=").append(q(c)).append(" stroke-width=").append(q(f(sw))).append("/>")
        return sb.toString()
    }

    private fun text(x: Double, y: Double, s: String, size: Double, c: String, anchor: String): String {
        val sb = StringBuilder()
        sb.append("<text x=").append(q(f(x))).append(" y=").append(q(f(y)))
        sb.append(" font-size=").append(q(f(size)))
        sb.append(" text-anchor=").append(q(anchor))
        sb.append(" fill=").append(q(c)).append(" font-family=").append(q("sans-serif")).append(">")
        sb.append(esc(s)).append("</text>")
        return sb.toString()
    }

    private fun f(v: Double): String = String.format(Locale.US, "%.2f", v)

    private fun q(s: String): String = "\"" + s + "\""

    private fun esc(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '&' -> sb.append("&amp;")
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun local(e: Element): String = e.tagName.lowercase().substringAfter(':')

    private fun children(e: Element): List<Element> {
        val out = ArrayList<Element>()
        val kids = e.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n.nodeType == org.w3c.dom.Node.ELEMENT_NODE) out.add(n as Element)
        }
        return out
    }

    private fun allChildren(p: Element, name: String): List<Element> {
        val t = name.substringAfter(':').lowercase()
        return children(p).filter { local(it) == t }
    }

    private fun findDescendant(p: Element, name: String): Element? {
        val t = name.substringAfter(':').lowercase()
        val all = p.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val e = all.item(i) as? Element ?: continue
            if (local(e) == t) return e
        }
        return null
    }
}

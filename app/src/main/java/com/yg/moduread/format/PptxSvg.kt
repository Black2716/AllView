package com.yg.moduread.format

import com.yg.moduread.format.Base64Lite as B64
import com.yg.moduread.md.LatexText
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * 把一页 PPTX 的 DrawingML 转成 SVG。
 *
 * 为什么用 SVG：PPTX 的每个元素都带绝对坐标（EMU 单位），
 * 与 SVG 的绝对定位模型天然对应，浏览器可以直接渲染。
 * 用位图离屏截图不可靠（WebView 合成层拿不到内容），
 * 让 WebView 直接显示 SVG 才是稳的。
 *
 * 覆盖范围：文本框、矩形/椭圆等基础形状、图片、纯色填充、边框。
 * 不覆盖：渐变、阴影、SmartArt、图表、动画 —— 这些会退化为近似色块或跳过。
 */
object PptxSvg {

    /** PPTX 的 EMU 与 pt 换算：1pt = 12700 EMU。 */
    private const val EMU_PER_PT = 12700.0

    /** 单张图片上限。 */
    private const val MAX_IMAGE_BYTES = 3 * 1024 * 1024

    /**
     * 当前页背景色。
     *
     * 用字段而非层层传参：toHtml 是同步单线程调用，处理完一页才进入下一页，
     * 不存在并发；这样避免改动一串函数签名。
     */
    private var currentBg: String = "#ffffff"

    /** 当前页高度（pt）。文字缺少坐标时用它算安全位置。 */
    private var pageHeightPt: Double = 540.0

    /** 当前页的图表解析器；用字段避免改动一长串函数签名。 */
    private var currentChartResolver: (String) -> String? = { null }

    /** 诊断计数。 */


    /** 诊断：txBody 里直接能查到公式的次数。 */



    /**
     * 把一张幻灯片转成完整 HTML（内嵌 SVG）。
     *
     * @param slideXml slideN.xml 的内容
     * @param realSize 真实内容尺寸（EMU）；取自 presentation.xml 的 sldSz
     * @param imageResolver rId -> (mime, base64) 的解析器
     */
    fun toHtml(
        slideXml: String,
        widthEmu: Long,
        heightEmu: Long,
        imageResolver: (String) -> Pair<String, String>?,
        /** rId -> chartN.xml 全文。图表不是图片，必须取到数据自己画。 */
        chartResolver: (String) -> String? = { null }
    ): String? {
        val root = ContainerSupport.parseXml(slideXml) ?: return null
        val wPt = (widthEmu / EMU_PER_PT).toInt().coerceAtLeast(1)
        val hPt = (heightEmu / EMU_PER_PT).toInt().coerceAtLeast(1)

        val svg = StringBuilder(16 * 1024)
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
        svg.append("xmlns:xlink=\"http://www.w3.org/1999/xlink\" ")
        svg.append("viewBox=\"0 0 ").append(wPt).append(' ').append(hPt).append("\" ")
        // 不要用 100% 宽高：WebView 里 html/body 的 height:100% 依赖父容器，
        // 在 AndroidView 的测量阶段常常解析为 0，SVG 高度塌成 0 就完全空白。
        // 直接给 pt 尺寸，由外层 CSS 的 aspect-ratio 控制缩放。
        svg.append("width=\"").append(wPt).append("\" height=\"").append(hPt)
            .append("\" preserveAspectRatio=\"xMidYMid meet\">")
        // 背景色必须取自文档本身。
        // 很多 PPT 是深色底 + 白色字，若写死白底，
        // 结果是「白字白底」完全看不见 —— 这正是「全部空白」的根因。
        val bgColor = readBackground(root) ?: "#ffffff"
        // 存起来给 renderText 用：文字颜色要按背景明暗推导
        currentBg = bgColor
        pageHeightPt = hPt.toDouble()
        currentChartResolver = chartResolver
        svg.append("<rect x=\"0\" y=\"0\" width=\"").append(wPt)
            .append("\" height=\"").append(hPt)
            .append("\" fill=\"").append(bgColor).append("\"/>")

        // sp 是形状+文本，pic 是图片，grpSp 是组合（递归）
        renderChildren(root, svg, imageResolver)

        svg.append("</svg>")
        return wrapHtml(svg.toString(), wPt, hPt)
    }

    /** 递归渲染容器子节点。 */
    private fun renderChildren(
        parent: Element,
        svg: StringBuilder,
        imageResolver: (String) -> Pair<String, String>?
    ) {
        val kids = parent.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val el = n as Element
            when (local(el)) {
                "sp" -> renderShape(el, svg, imageResolver)
                "pic" -> renderPicture(el, svg, imageResolver)
                // 注意：local() 会把标签名转小写，
                // 所以这里必须用小写形式，写 "cSld"/"grpSp" 永远匹配不上 —— 
                // 递归会在这里静默中断，整页只剩白底（实测踩过）。
                // mc:AlternateContent 是兼容层的包装：里面 mc:Choice 放新特性，
                // mc:Fallback 放旧版近似。不处理它会让整块内容（常见于公式）消失 ——
                // 本项目的公式就藏在这层里（实测踩过）。
                "alternatecontent" -> {
                    val choice = firstChild(el, "mc:choice") ?: firstChild(el, "choice")
                    val fallback = firstChild(el, "mc:fallback") ?: firstChild(el, "fallback")
                    val target = choice ?: fallback
                    if (target != null) renderChildren(target, svg, imageResolver)
                }
                "grpsp", "sptree", "csld", "sld" -> renderChildren(el, svg, imageResolver)
                // local() 返回小写标签名，"graphicFrame" 这种驼峰永远匹配不上 ——
                // 表格因此整块被跳过（这是表格不可见的真正原因）。
                "graphicframe" -> {
                    // graphicFrame 既能装表格也能装图表：
                    // 有 a:tbl 就是表格，有 c:chart 就是图表。
                    if (findDescendant(el, "a:tbl") != null) {
                        renderTable(el, svg)
                    } else {
                        renderChart(el, svg)
                    }
                }
                else -> {}
            }
        }
    }

    /** 形状：先画几何体，再叠文本。 */
    private fun renderShape(
        sp: Element,
        svg: StringBuilder,
        imageResolver: (String) -> Pair<String, String>?
    ) {
        val xfrm = findDescendant(sp, "a:xfrm") ?: findDescendant(sp, "xfrm")
        val box = readBox(xfrm)
        if (box == null) {
            renderText(svg, sp, 0.0, 0.0, 0.0, 0.0, imageResolver)
            return
        }
        val (x, y, w, h) = box
        val rot = readRotation(xfrm)

        // 几何类型：prstGeom 的 prst 属性，默认矩形
        val prst = findDescendant(sp, "a:prstGeom")
            ?.getAttribute("prst").orEmpty().ifEmpty { "rect" }
        val fill = readFill(sp)
        val stroke = readStroke(sp)

        svg.append("<g")
        if (rot != 0.0) {
            val cx = x + w / 2
            val cy = y + h / 2
            svg.append(" transform=\"rotate(").append(fmt(rot))
                .append(' ').append(fmt(cx)).append(' ').append(fmt(cy)).append(")\"")
        }
        svg.append('>')

        when (prst) {
            "ellipse" -> svg.append("<ellipse cx=\"").append(fmt(x + w / 2))
                .append("\" cy=\"").append(fmt(y + h / 2))
                .append("\" rx=\"").append(fmt(w / 2))
                .append("\" ry=\"").append(fmt(h / 2)).append('"')
            "roundRect" -> svg.append("<rect x=\"").append(fmt(x))
                .append("\" y=\"").append(fmt(y))
                .append("\" width=\"").append(fmt(w))
                .append("\" height=\"").append(fmt(h))
                .append("\" rx=\"").append(fmt(minOf(w, h) * 0.12)).append('"')
            "line" -> svg.append("<line x1=\"").append(fmt(x))
                .append("\" y1=\"").append(fmt(y))
                .append("\" x2=\"").append(fmt(x + w))
                .append("\" y2=\"").append(fmt(y + h)).append('"')
                .append(" stroke=\"").append(stroke ?: "#888888").append('"')
                .append(" stroke-width=\"1\" fill=\"none\"/></g>")
                .also { renderText(svg, sp, x, y, w, h, imageResolver); return }
            else -> svg.append("<rect x=\"").append(fmt(x))
                .append("\" y=\"").append(fmt(y))
                .append("\" width=\"").append(fmt(w))
                .append("\" height=\"").append(fmt(h)).append('"')
        }

        // 填充与描边
        if (prst == "line") {
            // 上面已闭合
        } else {
            svg.append(" fill=\"").append(fill ?: "none").append('"')
            if (stroke != null) {
                svg.append(" stroke=\"").append(stroke).append("\" stroke-width=\"1\"")
            }
            svg.append("/>")
            renderText(svg, sp, x, y, w, h, imageResolver)
            svg.append("</g>")
        }
    }

    /** 图片：按目标框缩放，保持比例。 */
    private fun renderPicture(
        pic: Element,
        svg: StringBuilder,
        imageResolver: (String) -> Pair<String, String>?
    ) {
        val blip = findDescendant(pic, "a:blip") ?: return
        val rid = blip.getAttribute("r:embed").ifEmpty { blip.getAttribute("embed") }
        if (rid.isEmpty()) return
        val img = imageResolver(rid) ?: return

        val xfrm = findDescendant(pic, "a:xfrm") ?: findDescendant(pic, "xfrm")
        val box = readBox(xfrm)
        if (box == null) return
        val (x, y, w, h) = box

        svg.append("<image x=\"").append(fmt(x))
            .append("\" y=\"").append(fmt(y))
            .append("\" width=\"").append(fmt(w))
            .append("\" height=\"").append(fmt(h))
            .append("\" preserveAspectRatio=\"xMidYMid meet\"")
            .append(" xlink:href=\"data:").append(img.first)
            .append(";base64,").append(img.second).append("\"/>")
    }

    /** 表格：转成 SVG 里的文本网格（简化处理）。 */
        /**
     * 图表：从 graphicFrame 找到 c:chart 的 rId，取 chartN.xml 后画成 SVG。
     *
     * 图表在 PPTX 里不是图片，而是「数据 + 样式」的结构化描述，
     * 没有可直接渲染的位图，必须按数据自己画。
     */
    private fun renderChart(frame: Element, svg: StringBuilder) {
        val chartEl = findDescendant(frame, "c:chart") ?: return
        val rid = chartEl.getAttribute("r:id").ifEmpty { chartEl.getAttribute("id") }
        if (rid.isEmpty()) return
        val xml = currentChartResolver(rid) ?: return

        val xfrm = firstChild(frame, "p:xfrm")
            ?: firstChild(frame, "xfrm")
            ?: findDescendant(frame, "a:xfrm")
            ?: findDescendant(frame, "xfrm")
        val box = readBox(xfrm) ?: return
        val x = box.x
        val y = box.y
        val w = box.w
        val h = box.h
        if (w <= 0 || h <= 0) return

        val inner = ChartSvg.render(xml, w, h, contrastOn(currentBg))
        if (inner.isEmpty()) return
        svg.append("<g transform=\"translate(").append(fmt(x)).append(",").append(fmt(y)).append(")\">")
        svg.append(inner)
        svg.append("</g>")
    }

/**
     * 表格：用 tblGrid 的真实列宽与 tr 的行高布局。
     *
     * 之前不可见的两个原因：
     *  1. 列宽被均分，宽列压窄、窄列拉宽
     *  2. 单元格只画 fill=none 的矩形，没有任何可见边框
     */
    private fun renderTable(frame: Element, svg: StringBuilder) {
        val tbl = findDescendant(frame, "a:tbl") ?: return
        val rows = directChildren(tbl, "tr")
        if (rows.isEmpty()) return

        val xfrm = firstChild(frame, "p:xfrm")
            ?: firstChild(frame, "xfrm")
            ?: findDescendant(frame, "a:xfrm")
            ?: findDescendant(frame, "xfrm")
        val box = readBox(xfrm) ?: return
        val (x, y, w, h) = box

        val grid = findDescendant(tbl, "a:tblGrid")
        val colWidths = ArrayList<Double>()
        if (grid != null) {
            for (gc in directChildren(grid, "gridCol")) {
                val raw = gc.getAttribute("w").toDoubleOrNull() ?: 0.0
                if (raw > 0) colWidths.add(raw / EMU_PER_PT)
            }
        }
        val maxCols = maxOf(
            colWidths.size,
            rows.maxOfOrNull { directChildren(it, "tc").size } ?: 0
        ).coerceAtLeast(1)

        val gridSum = colWidths.sum()
        val scale = if (gridSum > 0 && w > 0) (w / gridSum).coerceIn(0.2, 5.0) else 1.0
        val widths: List<Double> = if (colWidths.isEmpty()) {
            List(maxCols) { w / maxCols }
        } else {
            val scaled = colWidths.map { it * scale }
            if (scaled.size >= maxCols) scaled
            else scaled + List(maxCols - scaled.size) { w / maxCols }
        }

        val rowHeights = rows.map { tr ->
            val raw = tr.getAttribute("h").toDoubleOrNull() ?: 0.0
            if (raw > 0) raw / EMU_PER_PT else h / rows.size
        }

        var cy = y
        rows.forEachIndexed { ri, tr ->
            val rh = rowHeights.getOrElse(ri) { h / rows.size }
            var cx = x
            directChildren(tr, "tc").forEachIndexed { ci, tc ->
                val cw = widths.getOrElse(ci) { w / maxCols }
                val fill = readCellFill(tc)
                svg.append("<rect x=\"").append(fmt(cx))
                    .append("\" y=\"").append(fmt(cy))
                    .append("\" width=\"").append(fmt(cw))
                    .append("\" height=\"").append(fmt(rh))
                if (fill != null) {
                    svg.append("\" fill=\"").append(fill)
                } else {
                    svg.append("\" fill=\"none")
                }
                svg.append("\" stroke=\"").append(tableLineColor(tc, currentBg))
                    .append("\" stroke-width=\"0.7\"/>")

                val text = collectText(tc).trim()
                if (text.isNotEmpty()) {
                    val fs = minOf(rh * 0.45, 13.0).coerceAtLeast(6.0)
                    // 按单元格可用宽度折行。
                    // 之前把整格文字塞进一个 <text>，SVG 不会自动换行，
                    // 长文本会溢出到相邻单元格（实测踩过）。
                    val lines = wrapCellText(text, cw - 6.0, fs)
                    val lineH = fs * 1.32
                    var ty = cy + fs * 1.05
                    for (ln in lines) {
                        if (ty > cy + rh - 1.0) break
                        svg.append("<text x=\"").append(fmt(cx + 3))
                            .append("\" y=\"").append(fmt(ty))
                            .append("\" font-size=\"").append(fmt(fs))
                            .append("\" fill=\"").append(readTextColor(tc, currentBg))
                            .append("\" font-family=\"sans-serif\">")
                            .append(escape(ln))
                            .append("</text>")
                        ty += lineH
                    }
                }
                cx += cw
            }
            cy += rh
        }
    }



    /**
     * 按可用宽度把文字折成多行。
     *
     * 宽度按「中日韩字符 1.0 em、其他 0.55 em」估算 ——
     * 比统一算 0.55 准，中文表格才不会一排只放两三个字。
     */
    private fun wrapCellText(text: String, maxWidth: Double, fontSize: Double): List<String> {
        if (maxWidth <= 0 || text.isEmpty()) return listOf(text)
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var w = 0.0
        for (ch in text) {
            val cw = if (isWide(ch)) fontSize else fontSize * 0.55
            if (w + cw > maxWidth && sb.isNotEmpty()) {
                out.add(sb.toString())
                sb.setLength(0)
                w = 0.0
            }
            sb.append(ch)
            w += cw
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return if (out.isEmpty()) listOf(text) else out
    }

    /** 全角字符判定：CJK、全角标点、中文标点区间。 */
    private fun isWide(ch: Char): Boolean {
        val c = ch.code
        return (c in 0x4E00..0x9FFF) ||
            (c in 0x3000..0x303F) ||
            (c in 0xFF00..0xFFEF) ||
            (c in 0x3400..0x4DBF)
    }

    /** 单元格底色：a:tcPr 下的 solidFill。 */
    private fun readCellFill(tc: Element): String? {
        val pr = firstChild(tc, "tcPr") ?: return null
        val solid = firstChild(pr, "solidFill") ?: return null
        val clr = firstChild(solid, "srgbClr") ?: return null
        val v = clr.getAttribute("val")
        return if (v.length == 6) "#" + v else null
    }

    /** 表格线颜色：优先取单元格边框色，否则按背景明暗给对比色。 */
    private fun tableLineColor(tc: Element, bg: String): String {
        val pr = firstChild(tc, "tcPr")
        if (pr != null) {
            for (side in listOf("lnL", "lnR", "lnT", "lnB")) {
                val ln = firstChild(pr, side) ?: continue
                val solid = firstChild(ln, "solidFill") ?: continue
                val clr = firstChild(solid, "srgbClr") ?: continue
                val v = clr.getAttribute("val")
                if (v.length == 6) return "#" + v
            }
        }
        return if (contrastOn(bg) == "#ffffff") "#666666" else "#bbbbbb"
    }

    /** 文字：按文本框位置逐段落输出。 */
    private fun renderText(
        svg: StringBuilder,
        sp: Element,
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        imageResolver: (String) -> Pair<String, String>?
    ) {
        val body = findDescendant(sp, "a:txBody") ?: return
        val paras = directChildren(body, "p")
        if (paras.isEmpty()) return

        // 首个段落的字号决定行高；缺省按框高的 1/6 估
        var fontSize = 18.0
        val firstSz = findDescendant(paras.first(), "a:rPr")
            ?.getAttribute("sz").orEmpty()
        if (firstSz.isNotEmpty()) {
            firstSz.toIntOrNull()?.let { fontSize = it / 100.0 }   // sz 单位是 1/100 pt
        }
        val lineH = fontSize * 1.25

        // 垂直居中：PPT 默认文本框内容居中偏上，这里做居中近似。
        // 若外层没拿到坐标（w/h 为 0），必须给一个安全起点，
        // 否则 (0 - totalH)/2 得到负值，整段文字被画到页面外（实测踩过）。
        val safeX = if (w > 0) x else 40.0
        val safeW = if (w > 0) w else 880.0
        val safeH = if (h > 0) h else pageHeightPt * 0.8
        val totalH = lineH * paras.size
        var cy = if (h > 0) y + (safeH - totalH) / 2 + fontSize else pageHeightPt * 0.18
        if (cy < fontSize) cy = fontSize

        paras.forEach { p ->
            // 调试计数（供测试观察）
            // 公式优先：m:oMath 是结构化数学标记，不是文本。
            // 若只走 collectText，取到的是零散字符，公式会彻底变形或空白。
            val oMath = findDescendant(p, "m:oMath")
            if (oMath != null) {
                // 公式走「OMML → LaTeX → Unicode 线性文本」。
                //
                // 为什么不再手绘 SVG：布局引擎的基线、字宽、嵌套变换
                // 无法靠单测验证，只能肉眼试错，反复失败。
                // 项目里 Latex 已能正确线性化上下标、求和上下限、根号、定界符，
                // 直接复用它的成果远比另写一套可靠。
                val latex = OmmlToLatex.convert(oMath)
                if (latex.isNotEmpty()) {
                    // 在 Latex.parse 的语法树上直接画 SVG。
                    // 不用 LatexText.unicode：它对分式、大运算符上下限返回 null，
                    // 正是这两类让公式显示为空（实测踩过）。
                    runCatching {
                        val box = LatexSvg.render(latex, fontSize, readTextColor(p, currentBg))
                        if (box.svg.isNotEmpty()) {
                            svg.append("<g transform=\"translate(")
                                .append(fmt(safeX)).append(',').append(fmt(cy)).append(")\">")
                                .append(box.svg)
                                .append("</g>")
                        }
                    }
                }
                cy += lineH * 1.5
                return@forEach
            }
            val text = collectText(p)
            if (text.isNotEmpty()) {
                val align = readAlign(p)
                val anchorX = when (align) {
                    "ctr" -> safeX + safeW / 2
                    "r" -> safeX + safeW
                    else -> safeX
                }
                val anchor = when (align) {
                    "ctr" -> "middle"
                    "r" -> "end"
                    else -> "start"
                }
                // 折行会改变 cy，使后续形状的定位偏移、内容错位甚至消失，
                // 因此这里保持单行输出：文字位置由 PPT 自身的文本框坐标决定。
                svg.append("<text x=\"").append(fmt(anchorX))
                    .append("\" y=\"").append(fmt(cy))
                    .append("\" font-size=\"").append(fmt(fontSize)).append('"')
                    .append(" text-anchor=\"").append(anchor).append('"')
                    .append(" fill=\"").append(readTextColor(p, currentBg)).append('"')
                    .append(" font-family=\"sans-serif\">")
                    .append(escape(text)).append("</text>")
            }
            cy += lineH
        }
    }

    // ── 属性读取 ─────────────────────────────────────────────

    private data class Box(val x: Double, val y: Double, val w: Double, val h: Double)

    private fun readBox(xfrm: Element?): Box? {
        if (xfrm == null) return null
        // 注意：graphicFrame 的变换在 p:xfrm（presentation 命名空间），
        // 形状在 a:xfrm。调用方应按类型取对应的那个。
        val off = firstChild(xfrm, "off") ?: return null
        val ext = firstChild(xfrm, "ext") ?: return null
        val x = off.getAttribute("x").toDoubleOrNull() ?: return null
        val y = off.getAttribute("y").toDoubleOrNull() ?: return null
        val w = ext.getAttribute("cx").toDoubleOrNull() ?: return null
        val h = ext.getAttribute("cy").toDoubleOrNull() ?: return null
        return Box(x / EMU_PER_PT, y / EMU_PER_PT, w / EMU_PER_PT, h / EMU_PER_PT)
    }

    private fun readRotation(xfrm: Element?): Double {
        val v = xfrm?.getAttribute("rot").orEmpty()
        return if (v.isEmpty()) 0.0 else (v.toLongOrNull() ?: 0L) / 60000.0
    }

    /**
     * 读幻灯片背景色。
     *
     * p:bg/p:bgPr/a:solidFill/a:srgbClr —— 这是最常见的显式背景。
     * 读不到时返回 null，由调用方用白色兜底。
     */
    private fun readBackground(root: Element): String? {
        val bg = findDescendant(root, "p:bg") ?: return null
        val solid = findDescendant(bg, "a:solidFill") ?: return null
        val clr = firstChild(solid, "a:srgbClr") ?: return null
        val v = clr.getAttribute("val")
        return if (v.length == 6) "#" + v else null
    }

    /** 文字颜色：取 run 的 rPr/solidFill；读不到时按背景明暗自动给出对比色。 */
    private fun readTextColor(run: Element?, bgColor: String): String {
        val rPr = run?.let { findDescendant(it, "a:rPr") }
        if (rPr != null) {
            val solid = firstChild(rPr, "a:solidFill")
            if (solid != null) {
                val clr = firstChild(solid, "a:srgbClr")
                val v = clr?.getAttribute("val").orEmpty()
                if (v.length == 6) return "#" + v
            }
        }
        return contrastOn(bgColor)
    }

    /** 深色底给浅色字，浅色底给深色字。 */
    private fun contrastOn(bg: String): String {
        val hex = bg.removePrefix("#")
        if (hex.length != 6) return "#1b1b1f"
        val r = hex.substring(0, 2).toIntOrNull(16) ?: 255
        val g = hex.substring(2, 4).toIntOrNull(16) ?: 255
        val b = hex.substring(4, 6).toIntOrNull(16) ?: 255
        // 感知亮度（Rec.601）
        val luminance = (r * 299 + g * 587 + b * 114) / 1000
        return if (luminance < 128) "#ffffff" else "#1b1b1f"
    }

    private fun readFill(sp: Element): String? {
        val spPr = firstChild(sp, "spPr") ?: return null
        val solid = firstChild(spPr, "solidFill") ?: return null
        val clr = firstChild(solid, "srgbClr") ?: return null
        val v = clr.getAttribute("val")
        return if (v.length == 6) "#" + v else null
    }

    private fun readStroke(sp: Element): String? {
        val spPr = firstChild(sp, "spPr") ?: return null
        val ln = firstChild(spPr, "ln") ?: return null
        val solid = firstChild(ln, "solidFill") ?: return null
        val clr = firstChild(solid, "srgbClr") ?: return null
        val v = clr.getAttribute("val")
        return if (v.length == 6) "#" + v else null
    }

    private fun readAlign(p: Element): String {
        val pPr = firstChild(p, "pPr") ?: return "l"
        return pPr.getAttribute("algn").ifEmpty { "l" }
    }

    /**
     * LaTeX 无法完全线性化时的兜底：去掉命令保留可见字符。
     *
     * 分式、大型运算符上下限这类结构 Latex.unicode 会返回 null，
     * 此时退化为「尽量可读」的纯文本，至少不出现反斜杠命令。
     */
    private fun linearizeFallback(latex: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < latex.length) {
            val c = latex[i]
            when {
                c == '\\' -> {
                    // 跳过命令名，保留其后的内容
                    var j = i + 1
                    while (j < latex.length && latex[j].isLetter()) j++
                    i = j
                }
                c == '{' || c == '}' -> i++
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString().replace("  ", " ").trim()
    }

    private fun collectText(el: Element): String {
        val sb = StringBuilder()
        val ts = el.getElementsByTagName("a:t")
        for (i in 0 until ts.length) {
            sb.append((ts.item(i) as? Element)?.textContent.orEmpty())
        }
        return sb.toString()
    }

    // ── DOM 工具 ─────────────────────────────────────────────

    private fun local(el: Element): String = el.tagName.lowercase().substringAfter(':')

    private fun firstChild(parent: Element, name: String): Element? {
        // local() 返回小写，所以比较目标也要小写。
        // 写成 "spPr"/"solidFill" 这类驼峰名会永远匹配不上 ——
        // 这正是「背景色与文字全部读不到、SVG 只剩白底」的根因。
        val target = name.substringAfter(':').lowercase()
        val kids = parent.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val e = n as Element
            if (local(e) == target) return e
        }
        return null
    }

    private fun directChildren(parent: Element, name: String): List<Element> {
        val target = name.substringAfter(':').lowercase()
        val out = ArrayList<Element>()
        val kids = parent.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val e = n as Element
            if (local(e) == target) out.add(e)
        }
        return out
    }

    /** 按本地名深度搜索第一个匹配（不限命名空间前缀）。 */
    private fun findDescendant(parent: Element, name: String): Element? {
        // 同 firstChild：local() 是小写，比较目标必须小写
        val target = name.substringAfter(':').lowercase()
        val all = parent.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val e = all.item(i) as? Element ?: continue
            if (local(e) == target) return e
        }
        return null
    }

    private fun fmt(v: Double): String = String.format("%.2f", v)

    private fun escape(s: String): String = buildString(s.length + 16) {
        for (c in s) {
            when (c) {
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '&' -> append("&amp;")
                else -> append(c)
            }
        }
    }

    /** 页面外壳：SVG 占满，白底，无滚动条。 */
    private fun wrapHtml(svg: String, wPt: Int, hPt: Int): String = buildString {
        append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>")
        append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>")
        // 用 aspect-ratio 撑开高度，而不是 height:100%（后者在 WebView 里可能是 0）
        append("<style>html,body{margin:0;padding:0;background:#fff;overflow:hidden}")
        append("svg{display:block;width:100%;height:auto;")
        append("aspect-ratio:").append(wPt).append("/").append(hPt).append("}</style>")
        append("</head><body>")
        append(svg)
        append("</body></html>")
    }
}
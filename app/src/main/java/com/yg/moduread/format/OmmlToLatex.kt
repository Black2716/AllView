package com.yg.moduread.format

import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * OMML（Office Math）转 LaTeX。
 *
 * 为什么不自己画 SVG：项目里已有 Latex.kt + MathView.kt，
 * 那套实现已处理好分式、上下标、求和上下限、根号、定界符的成组关系。
 * 转成 LaTeX 再交给它，比另写布局引擎可靠得多 ——
 * 之前手绘 SVG 反复失败，因为基线、字宽、嵌套变换无法靠单测验证。
 */
object OmmlToLatex {

    fun convert(oMath: Element): String {
        val sb = StringBuilder()
        for (c in children(oMath)) sb.append(node(c))
        return sb.toString().trim()
    }

    private fun node(el: Element): String = when (local(el)) {
        // 分支名必须全小写：local() 返回 tagName.lowercase()。
        // 写成 sSub/limLow 这类驼峰永远匹配不上，会落到 else，
        // 表现为下标丢失、上下限错位。
        "r" -> text(firstChild(el, "t")?.textContent.orEmpty())
        "f" -> frac(el)
        "ssup" -> sup(el)
        "ssub" -> sub(el)
        "ssubsup" -> subSup(el)
        "rad" -> rad(el)
        "nary" -> nary(el)
        "d" -> delim(el)
        "e", "num", "den", "sub", "sup", "omath", "omathpara" -> wrap(el)
        "func" -> funcNode(el)
        "limlow" -> limLow(el)
        "limupp" -> limUpp(el)
        "acc" -> acc(el)
        "bar" -> bar(el)
        "m" -> matrix(el)
        else -> wrap(el)
    }

    private fun children(el: Element): List<Element> {
        val out = ArrayList<Element>()
        val kids = el.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n.nodeType == Node.ELEMENT_NODE) out.add(n as Element)
        }
        return out
    }

    private fun local(el: Element): String = el.tagName.lowercase().substringAfter(':')

    private fun firstChild(parent: Element, name: String): Element? {
        val t = name.substringAfter(':').lowercase()
        for (e in children(parent)) if (local(e) == t) return e
        return null
    }

    private fun allChildren(parent: Element, name: String): List<Element> {
        val t = name.substringAfter(':').lowercase()
        return children(parent).filter { local(it) == t }
    }

    private fun wrap(el: Element): String {
        val sb = StringBuilder()
        for (c in children(el)) sb.append(node(c))
        return sb.toString()
    }

    /** 逐码点映射，避免代理对被拆成半个。 */
    private fun text(raw: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            sb.append(mapChar(cp))
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    private fun mapChar(cp: Int): String = when {
        cp in 0x1D400..0x1D419 -> (65 + (cp - 0x1D400)).toChar().toString()
        cp in 0x1D41A..0x1D433 -> (97 + (cp - 0x1D41A)).toChar().toString()
        cp in 0x1D434..0x1D44D -> (65 + (cp - 0x1D434)).toChar().toString()
        cp in 0x1D44E..0x1D467 -> (97 + (cp - 0x1D44E)).toChar().toString()
        cp in 0x1D468..0x1D481 -> (65 + (cp - 0x1D468)).toChar().toString()
        cp in 0x1D482..0x1D49B -> (97 + (cp - 0x1D482)).toChar().toString()
        cp in 0x1D4B6..0x1D4CF -> (97 + (cp - 0x1D4B6)).toChar().toString()
        cp in 0x1D4D0..0x1D4E9 -> (65 + (cp - 0x1D4D0)).toChar().toString()
        cp in 0x1D4EA..0x1D503 -> (97 + (cp - 0x1D4EA)).toChar().toString()
        cp in 0x1D504..0x1D51D -> (65 + (cp - 0x1D504)).toChar().toString()
        cp in 0x1D51E..0x1D537 -> (97 + (cp - 0x1D51E)).toChar().toString()
        cp in 0x1D552..0x1D56B -> (97 + (cp - 0x1D552)).toChar().toString()
        cp in 0x1D670..0x1D689 -> (65 + (cp - 0x1D670)).toChar().toString()
        cp in 0x1D68A..0x1D6A3 -> (97 + (cp - 0x1D68A)).toChar().toString()
        // ≔ 直接输出 Unicode 字符。
        // 不能写 coloneqq：Latex.parse 的 SYMBOLS 表里没有这个命令，
        // 会被当作未知命令降级成字面文字，导致等号显示成 "coloneqq"。
        cp == 0x2254 -> " ≔ "
        cp == 0x2208 -> "\\in "
        cp == 0x2211 -> "\\sum "
        cp == 0x220F -> "\\prod "
        cp == 0x222B -> "\\int "
        cp == 0x2264 -> "\\le "
        cp == 0x2265 -> "\\ge "
        cp == 0x2260 -> "\\ne "
        cp == 0x221E -> "\\infty "
        cp == 0x2192 -> "\\to "
        cp == 0x21A6 -> "\\mapsto "
        cp == 0x2200 -> "\\forall "
        cp == 0x2203 -> "\\exists "
        cp == 0x2227 -> " ∧ "
        cp == 0x2228 -> " ∨ "
        cp == 0x00D7 -> "\\times "
        cp == 0x00B1 -> "\\pm "
        cp == 0x221A -> "\\sqrt "
        // 数学斜体希腊字母：实际区块从 U+1D6A8（大写）与 U+1D6FC（小写）开始。
        // 之前只覆盖到 U+1D6DA，π(U+1D70B)、θ(U+1D703) 都落在外面，
        // 于是原样输出成 &#x1d70b; 这种码点，界面显示为乱码。
        cp in 0x1D6A8..0x1D6C0 -> greekUpper(cp - 0x1D6A8)
        cp in 0x1D6FC..0x1D714 -> greekLower(cp - 0x1D6FC)
        cp == 0x03B8 -> "\\theta "
        cp == 0x03C0 -> "\\pi "
        cp == 0x03B1 -> "\\alpha "
        cp == 0x03B2 -> "\\beta "
        cp == 0x03BB -> "\\lambda "
        cp == 0x03BC -> "\\mu "
        cp == 0x03C3 -> "\\sigma "
        cp == 0x0398 -> "\\Theta "
        cp == 0x2202 -> "\\partial "
        cp == 0x2207 -> "\\nabla "
        cp == 0x22C5 -> "\\cdot "
        cp == 0x2026 -> "\\ldots "
        cp == 0x2261 -> "\\equiv "
        cp == 0x2248 -> "\\approx "
        cp == 0x221D -> "\\propto "
        cp == 0x2212 -> "-"
        // 花体大写字母：Latex.parse 支持 mathscr{...}。
        cp in 0x1D49C..0x1D4B5 -> "\\mathscr{" + (65 + (cp - 0x1D49C)).toChar() + "}"
        // 双线体大写：mathbb{...}
        cp in 0x1D538..0x1D551 -> "\\mathbb{" + (65 + (cp - 0x1D538)).toChar() + "}"
        // 波浪号与相似符号
        cp == 0x223C -> " ∼ "
        cp == 0x211B -> "\\mathscr{R}"
        cp == 0x2112 -> "\\mathscr{L}"
        cp == 0x2110 -> "\\mathscr{I}"
        cp == 0x2118 -> "\\mathscr{P}"
        cp == 0x211C -> "R"
        cp == 0x1D4A5 -> "\\mathscr{J}"
        cp == 0x00A0 -> " "
        cp == 0x005F -> "\\_"
        cp == 0x0025 -> "\\%"
        cp == 0x0023 -> "\\#"
        cp == 0x0024 -> "\\$"
        else -> String(Character.toChars(cp))
    }

    private val GREEK_UP = arrayOf(
        "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta",
        "iota", "kappa", "lambda", "mu", "nu", "xi", "o", "pi", "rho", "theta",
        "sigma", "tau", "upsilon", "phi", "chi", "psi", "omega"
    )

    /** 斜体小写希腊字母顺序：α..ω，中间含 final sigma(U+1D70C 之后)。 */
    private val GREEK_LO = arrayOf(
        "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta",
        "iota", "kappa", "lambda", "mu", "nu", "xi", "omicron", "pi", "rho",
        "varsigma", "sigma", "tau", "upsilon", "phi", "chi", "psi", "omega"
    )

    private fun greekUpper(i: Int): String {
        val n = GREEK_UP.getOrElse(i) { "alpha" }
        return if (n == "o") "o" else "\\" + n
    }

    private fun greekLower(i: Int): String {
        val n = GREEK_LO.getOrElse(i) { "alpha" }
        return if (n == "o") "o" else "\\" + n
    }

    private fun frac(f: Element): String {
        val num = firstChild(f, "num")?.let { wrap(it) } ?: ""
        val den = firstChild(f, "den")?.let { wrap(it) } ?: ""
        return "\\frac{" + num + "}{" + den + "}"
    }

    private fun sup(el: Element): String {
        val b = firstChild(el, "e")?.let { wrap(it) } ?: ""
        val s = firstChild(el, "sup")?.let { wrap(it) } ?: ""
        return "{" + b + "}^{" + s + "}"
    }

    private fun sub(el: Element): String {
        val b = firstChild(el, "e")?.let { wrap(it) } ?: ""
        val s = firstChild(el, "sub")?.let { wrap(it) } ?: ""
        return "{" + b + "}_{" + s + "}"
    }

    private fun subSup(el: Element): String {
        val b = firstChild(el, "e")?.let { wrap(it) } ?: ""
        val sb2 = firstChild(el, "sub")?.let { wrap(it) } ?: ""
        val sp = firstChild(el, "sup")?.let { wrap(it) } ?: ""
        return "{" + b + "}_{" + sb2 + "}^{" + sp + "}"
    }

    private fun rad(el: Element): String {
        val b = firstChild(el, "e")?.let { wrap(it) } ?: ""
        val d = firstChild(el, "deg")?.let { wrap(it) } ?: ""
        return if (d.isEmpty()) "\\sqrt{" + b + "}" else "\\sqrt[" + d + "]{" + b + "}"
    }

    private fun nary(el: Element): String {
        val pr = firstChild(el, "naryPr")
        val chrEl = pr?.let { firstChild(it, "chr") }
        var chr = chrEl?.getAttribute("m:val").orEmpty()
        if (chr.isEmpty()) chr = chrEl?.getAttribute("val").orEmpty()
        val op = when (chr) {
            "∏" -> "\\prod"
            "∫" -> "\\int"
            "⋃" -> "⋃"
            "⋂" -> "⋂"
            else -> "\\sum"
        }
        val s1 = firstChild(el, "sub")?.let { wrap(it) } ?: ""
        val s2 = firstChild(el, "sup")?.let { wrap(it) } ?: ""
        val body = firstChild(el, "e")?.let { wrap(it) } ?: ""
        val sb = StringBuilder(op)
        if (s1.isNotEmpty()) sb.append("_{").append(s1).append("}")
        if (s2.isNotEmpty()) sb.append("^{").append(s2).append("}")
        if (body.isNotEmpty()) sb.append(" ").append(body)
        return sb.toString()
    }

    private fun delim(d: Element): String {
        val pr = firstChild(d, "dPr")
        fun attr(name: String, def: String): String {
            val e = pr?.let { firstChild(it, name) } ?: return def
            val v = e.getAttribute("m:val").ifEmpty { e.getAttribute("val") }
            return if (v.isEmpty()) def else v
        }
        val beg = attr("begChr", "(")
        val end = attr("endChr", ")")
        val parts = allChildren(d, "e").map { wrap(it) }
        val inner = parts.joinToString(" , ")
        val open = if (beg == "{") "{" else beg
        val close = if (end == "}") "}" else end
        return open + inner + close
    }

    private fun funcNode(el: Element): String {
        val name = firstChild(el, "fName")?.let { wrap(it) } ?: ""
        val body = firstChild(el, "e")?.let { wrap(it) } ?: ""
        val clean = name.replace("\\", "").trim()
        return if (clean.isEmpty()) body else "\\" + clean + "{" + body + "}"
    }

    private fun limLow(el: Element): String {
        val b = firstChild(el, "e")?.let { wrap(it) } ?: ""
        val lim = firstChild(el, "lim")?.let { wrap(it) } ?: ""
        return "{" + b + "}_{" + lim + "}"
    }

    private fun limUpp(el: Element): String {
        val b = firstChild(el, "e")?.let { wrap(it) } ?: ""
        val lim = firstChild(el, "lim")?.let { wrap(it) } ?: ""
        return "{" + b + "}^{" + lim + "}"
    }

    private fun acc(el: Element): String {
        val b = firstChild(el, "e")?.let { wrap(it) } ?: ""
        return "\\hat{" + b + "}"
    }

    private fun bar(el: Element): String {
        val b = firstChild(el, "e")?.let { wrap(it) } ?: ""
        return "\\overline{" + b + "}"
    }

    private fun matrix(m: Element): String {
        val rows = allChildren(m, "mr").map { mr ->
            allChildren(mr, "e").joinToString(" & ") { wrap(it) }
        }
        // 解析器不支持 \begin{pmatrix}，会被当字面文本，宽度爆到 500pt+ 撑破版面。
        // 改为分号分隔的线性形式：信息不丢，也不会溢出。
        if (rows.isEmpty()) return ""
        return "(" + rows.joinToString(" ; ") + ")"
    }
}

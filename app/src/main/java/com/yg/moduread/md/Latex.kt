package com.yg.moduread.md

/**
 * LaTeX 子集 -> 排版树。
 *
 * 只覆盖阅读场景里真正会出现的语法：分式、上下标、大运算符（含上下限）、
 * 定界符、根号、重音、箭头、希腊字母与常用关系符。未知命令降级为正体文字，
 * 保证任何公式都不会“渲染失败”，最差也只是显示命令名。
 */
sealed interface MathNode {
    data class Row(val children: List<MathNode>) : MathNode

    /** 一段文字。upright=正体（\mathrm / \text），script=花体（\mathcal），bold=粗体。 */
    data class Text(
        val text: String,
        val upright: Boolean = false,
        val bold: Boolean = false,
        val script: Boolean = false
    ) : MathNode

    data class Frac(val num: MathNode, val den: MathNode) : MathNode

    data class Script(val base: MathNode, val sup: MathNode?, val sub: MathNode?) : MathNode

    /** 大运算符：sum/prod/int 的上下限排在符号上下，min/max 亦然。 */
    data class BigOp(
        val symbol: String,
        val sup: MathNode?,
        val sub: MathNode?,
        val limitsAboveBelow: Boolean = true
    ) : MathNode

    data class Delim(val open: String, val close: String, val body: MathNode) : MathNode
    data class Sqrt(val body: MathNode) : MathNode
    data class Accent(val mark: String, val body: MathNode) : MathNode

    /** \xrightarrow{label}：上方带标注的长箭头。 */
    data class Arrow(val label: MathNode?) : MathNode

    data class Space(val widthEm: Float) : MathNode
}

object Latex {

    fun parse(source: String): MathNode {
        val parser = Parser(source)
        val row = parser.parseRow(stopAtBrace = false)
        return row
    }

    // ── 符号表 ──────────────────────────────────────────────
    private val GREEK = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
        "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
        "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
        "pi" to "π", "rho" to "ρ", "sigma" to "σ", "tau" to "τ",
        "upsilon" to "υ", "phi" to "φ", "varphi" to "φ", "chi" to "χ",
        "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
        "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Phi" to "Φ",
        "Psi" to "Ψ", "Omega" to "Ω"
    )

    private val SYMBOLS = mapOf(
        "cdot" to "·", "times" to "×", "div" to "÷", "pm" to "±", "mp" to "∓",
        "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥", "ne" to "≠", "neq" to "≠",
        "approx" to "≈", "equiv" to "≡", "sim" to "∼", "propto" to "∝",
        "in" to "∈", "notin" to "∉", "subset" to "⊂", "subseteq" to "⊆",
        "cup" to "∪", "cap" to "∩", "emptyset" to "∅",
        "forall" to "∀", "exists" to "∃", "infty" to "∞", "partial" to "∂", "nabla" to "∇",
        "to" to "→", "rightarrow" to "→", "leftarrow" to "←", "mapsto" to "↦",
        "Rightarrow" to "⇒", "Leftarrow" to "⇐", "Leftrightarrow" to "⇔",
        "cdots" to "⋯", "ldots" to "…", "dots" to "…", "vdots" to "⋮",
        "circ" to "∘", "ast" to "∗", "star" to "⋆", "bullet" to "•",
        "angle" to "∠", "perp" to "⊥", "parallel" to "∥",
        "lvert" to "|", "rvert" to "|", "vert" to "|", "mid" to "|",
        "lVert" to "‖", "rVert" to "‖", "Vert" to "‖",
        "langle" to "⟨", "rangle" to "⟩",
        "prime" to "′", "degree" to "°", "surd" to "√"
    )

    /** 关系符/运算符：渲染时正体且两侧留白。 */
    private val BINARY_OPS = setOf("·", "×", "÷", "±", "∓", "∘", "∗", "⋆")
    private val RELATIONS = setOf(
        "≤", "≥", "≠", "≈", "≡", "∼", "∝", "∈", "∉", "⊂", "⊆", "→", "←", "↦",
        "⇒", "⇐", "⇔", "⊥", "∥", "=", "<", ">", "+", "−", "±"
    )

    private class Parser(private val src: String) {
        private var i = 0

        fun parseRow(stopAtBrace: Boolean): MathNode {
            val out = ArrayList<MathNode>()
            while (i < src.length) {
                val c = src[i]
                if (c == '}' && stopAtBrace) break
                when {
                    c == '\\' -> parseCommand()?.let { out.add(it) }
                    c == '{' -> {
                        i++
                        val inner = parseRow(stopAtBrace = true)
                        if (i < src.length && src[i] == '}') i++
                        out.add(inner)
                    }
                    c == '^' || c == '_' -> {
                        val base = out.removeLastOrNull() ?: MathNode.Text("")
                        out.add(attachScript(base))
                    }
                    c.isWhitespace() -> { i++; if (out.isNotEmpty()) out.add(MathNode.Space(0.18f)) }
                    else -> { out.add(MathNode.Text(c.toString())); i++ }
                }
            }
            return collapse(out)
        }

        /** 处理 ^ / _ 连同其后的一组原子。 */
        private fun attachScript(base: MathNode): MathNode {
            var sup: MathNode? = null
            var sub: MathNode? = null
            while (i < src.length && (src[i] == '^' || src[i] == '_')) {
                val kind = src[i]
                i++
                val arg = readAtom()
                if (kind == '^') sup = arg else sub = arg
            }
            // 已经是 BigOp 且带上下限时，把脚本并入运算符，交给渲染层排上下限
            if (base is MathNode.BigOp) {
                return base.copy(sup = base.sup ?: sup, sub = base.sub ?: sub)
            }
            return MathNode.Script(base, sup, sub)
        }

        /** 读一个原子：花括号组、单字符或一条命令。 */
        private fun readAtom(): MathNode {
            while (i < src.length && src[i].isWhitespace()) i++
            if (i >= src.length) return MathNode.Text("")
            val c = src[i]
            return when {
                c == '{' -> {
                    i++
                    val inner = parseRow(stopAtBrace = true)
                    if (i < src.length && src[i] == '}') i++
                    inner
                }
                c == '\\' -> parseCommand() ?: MathNode.Text("")
                else -> { i++; MathNode.Text(c.toString()) }
            }
        }

        /** 读 {…} 原文（给 \mathrm / \text 用，内部不再解析数学）。 */
        private fun readRawGroup(): String {
            while (i < src.length && src[i].isWhitespace()) i++
            if (i >= src.length) return ""
            if (src[i] != '{') { val c = src[i]; i++; return c.toString() }
            var depth = 0
            val sb = StringBuilder()
            while (i < src.length) {
                val c = src[i]
                if (c == '{') { depth++; if (depth == 1) { i++; continue } }
                if (c == '}') { depth--; i++; if (depth == 0) break else { sb.append(c); continue } }
                sb.append(c)
                i++
            }
            return sb.toString()
        }

        private fun readName(): String {
            val start = i
            while (i < src.length && src[i].isLetter()) i++
            return src.substring(start, i)
        }

        private fun parseCommand(): MathNode? {
            if (src[i] != '\\') return null
            i++ // 跳过反斜杠
            if (i >= src.length) return null
            // \, \; \! \  空白命令
            val c = src[i]
            if (!c.isLetter() && c != '\\') {
                i++
                return when (c) {
                    ',' -> MathNode.Space(0.17f)
                    ';' -> MathNode.Space(0.28f)
                    ':' -> MathNode.Space(0.22f)
                    '!' -> MathNode.Space(-0.14f)
                    ' ' -> MathNode.Space(0.25f)
                    '{' -> MathNode.Text("{")
                    '}' -> MathNode.Text("}")
                    '|' -> MathNode.Text("‖")
                    else -> MathNode.Text(c.toString())
                }
            }
            val name = readName()
            if (name.isEmpty()) return null
            return when (name) {
                "frac", "dfrac", "tfrac" -> {
                    val num = readAtom()
                    val den = readAtom()
                    MathNode.Frac(num, den)
                }

                "sqrt" -> MathNode.Sqrt(readAtom())

                "mathrm", "operatorname", "text", "textrm", "mbox" ->
                    MathNode.Text(readRawGroup(), upright = true)

                "mathbf", "textbf" -> MathNode.Text(readRawGroup(), upright = true, bold = true)
                "mathcal", "mathscr" -> MathNode.Text(readRawGroup(), script = true)

                // \mathbb{R} 之类：映射到 Unicode 黑板体字母，映射不到就保持原字母
                "mathbb" -> MathNode.Text(
                    readRawGroup().map { c ->
                        when (c) {
                            'R' -> 'ℝ'
                            'N' -> 'ℕ'
                            'Z' -> 'ℤ'
                            'Q' -> 'ℚ'
                            'C' -> 'ℂ'
                            'P' -> 'ℙ'
                            else -> c
                        }
                    }.joinToString(""),
                    upright = true
                )

                "hat", "widehat" -> MathNode.Accent("ˆ", readAtom())
                "bar", "overline" -> MathNode.Accent("‾", readAtom())
                "tilde", "widetilde" -> MathNode.Accent("˜", readAtom())
                "vec" -> MathNode.Accent("→", readAtom())

                "left" -> {
                    val open = readDelimiter()
                    val body = parseRow(stopAtBrace = false)
                    val close = if (i < src.length) readRightDelimiter() else ""
                    MathNode.Delim(open, close, body)
                }

                "right" -> {
                    readDelimiter()
                    null
                }

                "xrightarrow", "xrightarrow[]" -> {
                    val label = if (i < src.length && src[i] == '{') readAtom() else null
                    MathNode.Arrow(label)
                }

                "sum", "prod", "coprod" -> MathNode.BigOp(
                    if (name == "sum") "∑" else if (name == "prod") "∏" else "∐",
                    null, null
                )

                "int", "iint", "oint" -> MathNode.BigOp("∫", null, null, limitsAboveBelow = false)

                "min", "max", "lim", "sup", "inf", "argmin", "argmax", "log", "ln", "exp" ->
                    MathNode.BigOp(nameLabel(name), null, null)

                "quad" -> MathNode.Space(1.0f)
                "qquad" -> MathNode.Space(2.0f)

                "Big", "big", "Bigg", "bigg", "Bigl", "bigl", "Bigr", "bigr" -> {
                    val d = readDelimiter()
                    MathNode.Text(d, upright = true)
                }

                else -> {
                    GREEK[name]?.let { return MathNode.Text(it) }
                    SYMBOLS[name]?.let { s ->
                        val isOp = s in BINARY_OPS || s in RELATIONS
                        return MathNode.Text(s, upright = isOp)
                    }
                    // 未知命令：正体显示命令名，绝不吞掉内容
                    MathNode.Text(name, upright = true)
                }
            }
        }

        private fun nameLabel(name: String): String = when (name) {
            "argmin" -> "arg min"
            "argmax" -> "arg max"
            else -> name
        }

        private fun readDelimiter(): String {
            while (i < src.length && src[i].isWhitespace()) i++
            if (i >= src.length) return ""
            val c = src[i]
            if (c == '\\') {
                i++
                val start = i
                while (i < src.length && src[i].isLetter()) i++
                return when (src.substring(start, i)) {
                    "lbrace" -> "{"
                    "rbrace" -> "}"
                    "langle" -> "⟨"
                    "rangle" -> "⟩"
                    "lvert", "vert" -> "|"
                    "lVert", "Vert" -> "‖"
                    "lfloor" -> "⌊"
                    "rfloor" -> "⌋"
                    "lceil" -> "⌈"
                    "rceil" -> "⌉"
                    else -> ""
                }
            }
            i++
            return if (c == '.') "" else c.toString()
        }

        private fun readRightDelimiter(): String {
            // 此刻 i 指向 \right 的反斜杠
            if (i < src.length && src[i] == '\\') {
                i++
                val save = i
                while (i < src.length && src[i].isLetter()) i++
                if (src.substring(save, i) == "right") return readDelimiter()
                i = save
            }
            return ""
        }

        /** 把连续的 Text 合并成一个，减少布局节点数。 */
        private fun collapse(nodes: List<MathNode>): MathNode {
            if (nodes.isEmpty()) return MathNode.Text("")
            if (nodes.size == 1) return nodes[0]
            val merged = ArrayList<MathNode>(nodes.size)
            for (node in nodes) {
                val last = merged.lastOrNull()
                if (node is MathNode.Text && last is MathNode.Text &&
                    !node.upright && !node.bold && !node.script &&
                    !last.upright && !last.bold && !last.script
                ) {
                    merged[merged.size - 1] = MathNode.Text(last.text + node.text)
                } else {
                    merged.add(node)
                }
            }
            return if (merged.size == 1) merged[0] else MathNode.Row(merged)
        }
    }
}

/**
 * LaTeX 子集的「纯文本」降级：把能无损转成 Unicode 的符号直接落地，
 * 供段落里的行内公式使用 —— AnnotatedString 无法排版分式，
 * 但 μ_t、σ、x² 这类完全可以用 Unicode 融入正文，排版不被打断。
 */
object LatexText {

    private val SUP = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'T' to 'ᵀ'
    )

    private val SUB = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'o' to 'ₒ', 'x' to 'ₓ', 'h' to 'ₕ',
        'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'p' to 'ₚ',
        's' to 'ₛ', 't' to 'ₜ', 'i' to 'ᵢ', 'j' to 'ⱼ', 'r' to 'ᵣ', 'u' to 'ᵤ', 'v' to 'ᵥ'
    )

    /** \mathbb 的常用字母。 */
    private val BLACKBOARD = mapOf(
        'R' to 'ℝ', 'N' to 'ℕ', 'Z' to 'ℤ', 'Q' to 'ℚ', 'C' to 'ℂ', 'P' to 'ℙ'
    )

    /**
     * 尽力转成 Unicode 纯文本。
     * 返回 null 表示这条公式含有无法线性化的结构（分式、大运算符的上下限、定界符整体等），
     * 调用方应改用专门的行内公式排版。
     */
    fun unicode(latex: String): String? {
        val tree = Latex.parse(latex)
        val sb = StringBuilder()
        return if (flatten(tree, sb)) sb.toString().trim() else null
    }

    private fun flatten(node: MathNode, sb: StringBuilder): Boolean = when (node) {
        is MathNode.Row -> node.children.all { flatten(it, sb) }
        is MathNode.Text -> { sb.append(node.text); true }
        is MathNode.Space -> { sb.append(' '); true }

        is MathNode.Script -> flattenScript(node, sb)

        // 分式无法线性化：交给调用方决定（这里明确拒绝，避免 a/b 丢掉括号语义）
        is MathNode.Frac -> false
        is MathNode.Sqrt -> {
            sb.append('√')
            if (node.body is MathNode.Text || node.body is MathNode.Row) flatten(node.body, sb) else false
        }

        is MathNode.Accent -> flattenAccent(node, sb)

        is MathNode.Delim -> {
            if (node.open.isNotEmpty()) sb.append(node.open)
            val ok = flatten(node.body, sb)
            if (node.close.isNotEmpty()) sb.append(node.close)
            ok
        }

        is MathNode.Arrow -> {
            node.label?.let { if (!flatten(it, sb)) return false; sb.append(' ') }
            sb.append("→")
            true
        }

        // 大运算符带上下限时无法线性化
        is MathNode.BigOp -> {
            if (node.sup != null || node.sub != null) false else { sb.append(node.symbol); true }
        }
    }

    /** 重音：把组合附加符号贴在首个基字符后面，Unicode 会自己完成对齐。 */
    private fun flattenAccent(node: MathNode.Accent, sb: StringBuilder): Boolean {
        val mark = when (node.mark) {
            "ˆ" -> '\u0302'
            "‾" -> '\u0305'
            "˜" -> '\u0303'
            "→" -> '\u20D7'
            else -> null
        }
        val inner = StringBuilder()
        if (!flatten(node.body, inner)) return false
        if (inner.isEmpty()) return false
        if (mark == null) {
            sb.append(inner)
            return true
        }
        sb.append(inner[0]).append(mark).append(inner.substring(1))
        return true
    }

    private fun flattenScript(node: MathNode.Script, sb: StringBuilder): Boolean {
        val base = StringBuilder()
        if (!flatten(node.base, base)) return false
        sb.append(base)

        node.sup?.let { sup ->
            val s = StringBuilder()
            if (!flatten(sup, s)) return false
            val mapped = s.toString().map { SUP[it] ?: return@map null }
            if (mapped.any { it == null }) return false
            sb.append(mapped.joinToString(""))
        }

        node.sub?.let { sub ->
            val s = StringBuilder()
            if (!flatten(sub, s)) return false
            val mapped = s.toString().map { SUB[it] ?: return@map null }
            if (mapped.any { it == null }) return false
            sb.append(mapped.joinToString(""))
        }
        return true
    }
}


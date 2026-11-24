package com.yg.moduread.format

import com.yg.moduread.md.MdAlign
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdInline
import com.yg.moduread.md.MdTableCell

/**
 * 纯文本系格式的解析（第一档）。
 *
 * 全部输出 List<MdBlock>，因此渲染、目录、书签、进度、搜索、多开都自动复用。
 * 每种的差别只体现在「怎么分块」：
 *  - TXT：空行分段，并识别常见章节写法
 *  - LOG：逐行，不合并（日志的价值在逐行对齐）
 *  - CSV：解析成表格块（RFC 4180 风格）
 *  - JSON：真正解析后按缩进重新序列化
 *  - YAML：保留原缩进的等宽呈现
 *  - INI：按 [节] 分段
 *  - SRT/VTT：按时间轴分块
 *  - RTF：剥控制字后按段落
 */
object TextAdapters {

    private const val MAX_LOG_LINE = 2000

    // ── TXT ──────────────────────────────────────────────────────

    fun txt(text: String): ParsedDoc {
        val blocks = ArrayList<MdBlock>()
        val buffer = StringBuilder()

        fun flush() {
            val t = buffer.toString().trim()
            buffer.setLength(0)
            if (t.isNotEmpty()) blocks.add(MdBlock.Para(listOf(MdInline.Text(t))))
        }

        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            if (line.isBlank()) {
                flush()
                continue
            }
            if (looksLikeChapter(line)) {
                flush()
                blocks.add(MdBlock.Heading(2, listOf(MdInline.Text(line.trim()))))
                continue
            }
            if (buffer.isNotEmpty()) buffer.append(' ')
            buffer.append(line)
        }
        flush()
        return ParsedDoc(blocks, text)
    }

    /** 章节标题启发式：命中即当作二级标题，让 TXT 也有目录可用。 */
    /** 章节标题启发式：只有命中明确的章节编号或极短全大写行才算。 */
    fun looksLikeChapter(line: String): Boolean {
        val t = line.trim()
        if (t.length !in 2..40) return false
        if (Regex("""^第[一二三四五六七八九十百零〇0-9]+[章节篇部]""").containsMatchIn(t)) return true
        if (Regex("""^chapter\s+\d+""", RegexOption.IGNORE_CASE).containsMatchIn(t)) return true
        // 全大写短行：只对拉丁字母判断。
        // 中文没有大小写，isLowerCase() 恒为 false，若直接用它判断
        // 会把整段中文正文全部误判成标题（这是真实踩过的坑）。
        val latin = t.filter { it in 'A'..'Z' || it in 'a'..'z' }
        if (latin.length >= 3 && latin.all { it.isUpperCase() } && t.length <= 30) return true
        return false
    }

    // ── LOG ──────────────────────────────────────────────────────

    fun log(text: String): ParsedDoc {
        val blocks = ArrayList<MdBlock>()
        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            if (line.isEmpty()) continue
            val shown = if (line.length > MAX_LOG_LINE) line.take(MAX_LOG_LINE) + " ..." else line
            blocks.add(MdBlock.CodeBlock(shown, null))
        }
        return ParsedDoc(blocks, text)
    }

    // ── CSV / TSV ────────────────────────────────────────────────

    fun csv(text: String, delimiter: Char = ','): ParsedDoc {
        val rows = parseCsvRows(text, delimiter)
        if (rows.isEmpty()) return ParsedDoc(emptyList(), "")

        val width = rows.maxOf { it.size }.coerceAtLeast(1)
        fun toCells(row: List<String>): List<MdTableCell> =
            (0 until width).map { i ->
                MdTableCell(listOf(MdInline.Text(row.getOrElse(i) { "" })), MdAlign.NONE)
            }

        val head = toCells(rows.first())
        val body = rows.drop(1).map { toCells(it) }
        return ParsedDoc(listOf(MdBlock.Table(head, body)), text)
    }

    /** RFC 4180 风格：处理引号包裹、引号内换行与分隔符、双引号转义。 */
    fun parseCsvRows(text: String, delimiter: Char = ','): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val s = text.removePrefix("\uFEFF")

        while (i < s.length) {
            val c = s[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < s.length && s[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
            } else {
                when {
                    c == '"' -> inQuotes = true
                    c == delimiter -> { row.add(field.toString()); field.setLength(0) }
                    c == '\r' -> Unit
                    c == '\n' -> {
                        row.add(field.toString()); field.setLength(0)
                        rows.add(row); row = ArrayList()
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows.filter { r -> r.any { it.isNotEmpty() } }
    }

    // ── JSON ─────────────────────────────────────────────────────

    /**
     * 真正解析 JSON 后按缩进重新序列化。
     *
     * 刻意用「重新序列化」而不是「原样缩进」：输入常是压在一行的 minified JSON，
     * 原样显示毫无可读性。解析失败时降级为原始文本块，保证至少能读。
     */
    fun json(text: String): ParsedDoc = runCatching {
        val node = JsonParser(text).parseValue()
        val pretty = StringBuilder().also { prettyJson(node, 0, it) }.toString()
        ParsedDoc(listOf(MdBlock.CodeBlock(pretty, "json")), text)
    }.getOrElse {
        ParsedDoc(listOf(MdBlock.CodeBlock(text, "json")), text)
    }

    sealed interface JsonNode {
        data class Obj(val entries: List<Pair<String, JsonNode>>) : JsonNode
        data class Arr(val items: List<JsonNode>) : JsonNode
        data class Str(val value: String) : JsonNode
        data class Num(val raw: String) : JsonNode
        data class Bool(val value: Boolean) : JsonNode
        data object Null : JsonNode
    }

    private fun prettyJson(node: JsonNode, indent: Int, out: StringBuilder) {
        val pad = "  ".repeat(indent)
        val padIn = "  ".repeat(indent + 1)
        when (node) {
            is JsonNode.Obj -> {
                if (node.entries.isEmpty()) { out.append("{}"); return }
                out.append("{\n")
                node.entries.forEachIndexed { i, e ->
                    out.append(padIn).append('"').append(escapeJson(e.first)).append("\": ")
                    prettyJson(e.second, indent + 1, out)
                    if (i != node.entries.lastIndex) out.append(',')
                    out.append('\n')
                }
                out.append(pad).append('}')
            }
            is JsonNode.Arr -> {
                if (node.items.isEmpty()) { out.append("[]"); return }
                out.append("[\n")
                node.items.forEachIndexed { i, v ->
                    out.append(padIn)
                    prettyJson(v, indent + 1, out)
                    if (i != node.items.lastIndex) out.append(',')
                    out.append('\n')
                }
                out.append(pad).append(']')
            }
            is JsonNode.Str -> out.append('"').append(escapeJson(node.value)).append('"')
            is JsonNode.Num -> out.append(node.raw)
            is JsonNode.Bool -> out.append(node.value)
            JsonNode.Null -> out.append("null")
        }
    }

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    /** 最小 JSON 解析器：够排版用，不追求 RFC 全量。 */
    class JsonParser(private val src: String) {
        private var pos = 0

        fun parseValue(): JsonNode {
            skipWs()
            if (pos >= src.length) throw IllegalStateException("空 JSON")
            return when (val c = src[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonNode.Str(parseString())
                't', 'f' -> parseBool()
                'n' -> { expect("null"); JsonNode.Null }
                else -> if (c == '-' || c.isDigit()) parseNumber()
                        else throw IllegalStateException("非法字符 " + c)
            }
        }

        private fun parseObject(): JsonNode.Obj {
            pos++
            val list = ArrayList<Pair<String, JsonNode>>()
            skipWs()
            if (peek() == '}') { pos++; return JsonNode.Obj(list) }
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                if (peek() != ':') throw IllegalStateException("缺少冒号")
                pos++
                list.add(key to parseValue())
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> { pos++; return JsonNode.Obj(list) }
                    else -> throw IllegalStateException("对象未闭合")
                }
            }
        }

        private fun parseArray(): JsonNode.Arr {
            pos++
            val items = ArrayList<JsonNode>()
            skipWs()
            if (peek() == ']') { pos++; return JsonNode.Arr(items) }
            while (true) {
                items.add(parseValue())
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    ']' -> { pos++; return JsonNode.Arr(items) }
                    else -> throw IllegalStateException("数组未闭合")
                }
            }
        }

        private fun parseString(): String {
            if (peek() != '"') throw IllegalStateException("期望字符串")
            pos++
            val sb = StringBuilder()
            while (pos < src.length) {
                val c = src[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (pos >= src.length) break
                        when (val e = src[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = src.substring(pos, minOf(pos + 4, src.length))
                                pos += hex.length
                                hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            throw IllegalStateException("字符串未闭合")
        }

        private fun parseBool(): JsonNode.Bool =
            if (src.startsWith("true", pos)) { pos += 4; JsonNode.Bool(true) }
            else { expect("false"); JsonNode.Bool(false) }

        private fun parseNumber(): JsonNode.Num {
            val start = pos
            if (peek() == '-') pos++
            while (pos < src.length && (src[pos].isDigit() || src[pos] in ".eE+-")) pos++
            return JsonNode.Num(src.substring(start, pos))
        }

        private fun expect(word: String) {
            if (!src.startsWith(word, pos)) throw IllegalStateException("期望 " + word)
            pos += word.length
        }

        private fun peek(): Char =
            if (pos < src.length) src[pos] else throw IllegalStateException("意外结束")

        private fun skipWs() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }
    }

    // ── YAML ─────────────────────────────────────────────────────

    /**
     * YAML 不做完整解析（规范太复杂，半吊子解析会把内容弄错）。
     * 保留原缩进放进等宽块，对阅读配置文件已经够用。
     */
    fun yaml(text: String): ParsedDoc {
        val body = text.trimEnd()
        return if (body.isEmpty()) ParsedDoc(emptyList(), "")
        else ParsedDoc(listOf(MdBlock.CodeBlock(body, "yaml")), text)
    }

    // ── INI / CONF ───────────────────────────────────────────────

    fun ini(text: String): ParsedDoc {
        val blocks = ArrayList<MdBlock>()
        val buffer = StringBuilder()

        fun flush() {
            val t = buffer.toString().trim()
            buffer.setLength(0)
            if (t.isNotEmpty()) blocks.add(MdBlock.CodeBlock(t, null))
        }

        for (raw in text.lineSequence()) {
            val line = raw.trimEnd()
            val section = Regex("^\\s*\\[([^]]+)]\\s*$").find(line)
            if (section != null) {
                flush()
                blocks.add(MdBlock.Heading(2, listOf(MdInline.Text(section.groupValues[1].trim()))))
                continue
            }
            if (line.isBlank()) { flush(); continue }
            buffer.append(line).append('\n')
        }
        flush()
        return ParsedDoc(blocks, text)
    }

    // ── SRT / VTT ───────────────────────────────────────────────

    /** 字幕：每个时间轴条目一个块，时间戳作为小标题。兼容 SRT 与 VTT。 */
    fun subtitle(text: String): ParsedDoc {
        val blocks = ArrayList<MdBlock>()
        val lines = text.removePrefix("\uFEFF").lines()
        var i = 0
        val timeRe = Regex("^(\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{3})\\s*-->\\s*(\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{3})")

        while (i < lines.size) {
            val m = timeRe.find(lines[i].trim())
            if (m == null) { i++; continue }
            val start = m.groupValues[1]
            val end = m.groupValues[2]
            i++
            val body = StringBuilder()
            while (i < lines.size && lines[i].isNotBlank()) {
                if (body.isNotEmpty()) body.append(' ')
                body.append(cleanSubtitleText(lines[i]))
                i++
            }
            blocks.add(MdBlock.Heading(4, listOf(MdInline.Text(start + " -> " + end))))
            if (body.isNotEmpty()) blocks.add(MdBlock.Para(listOf(MdInline.Text(body.toString()))))
        }
        return ParsedDoc(blocks, text)
    }

    fun cleanSubtitleText(s: String): String = s
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()

    // ── RTF ─────────────────────────────────────────────────────

    fun rtf(text: String): ParsedDoc {
        val plain = stripRtf(text)
        val blocks = ArrayList<MdBlock>()
        val buffer = StringBuilder()

        fun flush() {
            val t = buffer.toString().trim()
            buffer.setLength(0)
            if (t.isNotEmpty()) blocks.add(MdBlock.Para(listOf(MdInline.Text(t))))
        }

        for (raw in plain.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) { flush(); continue }
            if (buffer.isNotEmpty()) buffer.append(' ')
            buffer.append(line)
        }
        flush()
        return ParsedDoc(blocks, plain)
    }

    /**
     * 剥 RTF 控制结构，保留文字。
     *
     * 只处理最常见控制字（\\par 换行、\\tab、\\uN Unicode、跳过字体/颜色表）。
     * 不追求完整规范 —— 目标只是「至少能读」。
     */
    fun stripRtf(src: String): String {
        val out = StringBuilder()
        var i = 0
        var depth = 0
        val skipFrom = ArrayList<Int>()

        while (i < src.length) {
            val c = src[i]
            if (c == '{') { depth++; i++; continue }
            if (c == '}') {
                skipFrom.removeAll { it == depth }
                depth--
                i++
                continue
            }
            if (c == '\\' && i + 1 < src.length) {
                if (src[i + 1] == '\'') {
                    val hex = src.substring(i + 2, minOf(i + 4, src.length))
                    hex.toIntOrNull(16)?.let { out.append(it.toChar()) }
                    i += 4
                    continue
                }
                var j = i + 1
                val wordStart = j
                while (j < src.length && src[j].isLetter()) j++
                val word = src.substring(wordStart, j)
                val numStart = j
                if (j < src.length && (src[j] == '-' || src[j].isDigit())) j++
                while (j < src.length && src[j].isDigit()) j++
                val num = src.substring(numStart, j).toIntOrNull()
                if (j < src.length && src[j] == ' ') j++

                when (word) {
                    "par", "line" -> out.append('\n')
                    "tab" -> out.append('\t')
                    "u" -> num?.let { out.append((if (it < 0) it + 65536 else it).toChar()) }
                    "fonttbl", "colortbl", "stylesheet", "info", "pict",
                    "header", "footer", "object" -> skipFrom.add(depth + 1)
                    else -> Unit
                }
                i = j
                continue
            }
            if (skipFrom.isNotEmpty()) { i++; continue }
            if (c == '\r') { i++; continue }
            if (c == '\n') { out.append('\n'); i++; continue }
            out.append(c)
            i++
        }
        return out.toString()
    }

    // ── 源码文件 ────────────────────────────────────────────────

    /**
     * 源码：整份文件一个高亮代码块。
     *
     * 不按行切块 —— 代码的缩进与上下文需要连续呈现，
     * 拆成多块反而破坏对齐，也让复制变得麻烦。
     * 语言由扩展名决定（见 [languageOfFile]），未知扩展名按纯文本处理。
     */
    fun source(text: String, fileName: String?): ParsedDoc {
        val lang = languageOfFile(fileName)
        val body = text.trimEnd()
        if (body.isEmpty()) return ParsedDoc(emptyList(), "")
        return ParsedDoc(listOf(MdBlock.CodeBlock(body, lang)), text)
    }

    /**
     * 文件名 -> 高亮器认识的语言名。
     *
     * 只返回 [com.yg.moduread.md.CodeHighlighter] 真正支持的几种；
     * 其余源码格式（java/go/rs 等）没有对应词法，返回 null 走纯文本，
     * 保证"能读"而不是用错的关键字表染出奇怪的颜色。
     */
    fun languageOfFile(fileName: String?): String? {
        if (fileName.isNullOrBlank()) return null
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "kt", "kts" -> "kotlin"
            "py" -> "python"
            "sh", "bash", "zsh" -> "bash"
            "json" -> "json"
            // 其余暂时没有词法表
            else -> null
        }
    }
}

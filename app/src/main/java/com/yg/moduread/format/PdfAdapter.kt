package com.yg.moduread.format

import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdInline
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * PDF 解析：页面结构、/Outlines 目录、文字层提取。
 *
 * 全部零依赖：只用 JDK 的 Inflater 解压、正则与字节扫描解析 PDF 对象结构。
 * 不生成位图 —— 页面位图由界面层用 PdfRenderer 按需渲染（见 PdfPageView 的调用方）。
 *
 * 已知边界（真实 PDF 的复杂性所在）：
 *  1. 只支持未加密 PDF。加密文件（/Encrypt）直接降级为「按页显示，无目录无文字」。
 *  2. 文字层依赖 /ToUnicode CMap。CJK 字体常把字形映射成子集编码，
 *     没有 ToUnicode 就无法还原文字 —— 这类文件搜索不到内容，但页面仍可正常显示。
 *  3. 交叉引用表损坏时退回全文件扫描（本实现默认即全扫描，容错更好）。
 */
object PdfAdapter {

    private val WHITESPACE = Regex("""\s+""")
    private const val MAX_STREAM_SCAN = 40 * 1024 * 1024

    /** 解析结果中的附加信息。 */
    data class PdfResult(
        val pageCount: Int,
        val outline: List<Pair<Int, String>>,
        val textByPage: List<String>,
        val encrypted: Boolean
    ) {
        /** 文字层是否可用（有任意一页能抽出文字）。 */
        val hasTextLayer: Boolean get() = textByPage.any { it.isNotBlank() }
    }

    /**
     * 解析 PDF 字节，产出块列表。
     *
     * 每页一个 [MdBlock.PdfPage]；目录存在时，在最前面补一组标题块，
     * 让目录抽屉可以跳转（跳转落到对应页的块下标）。
     */
    fun parse(bytes: ByteArray): ParsedDoc {
        val info = inspect(bytes)
        val blocks = ArrayList<MdBlock>()

        // 目录先以标题形式插入，使目录抽屉有内容可点
        for ((level, title) in info.outline) {
            blocks.add(MdBlock.Heading(level.coerceIn(1, 6), listOf(MdInline.Text(title))))
        }

        for (i in 0 until info.pageCount) {
            blocks.add(MdBlock.PdfPage(i, 595, 842))
        }

        return ParsedDoc(blocks, info.textByPage.joinToString(" "))
    }

    /** 只取元信息，不构造块。供界面显示限制提示与页数。 */
    fun inspect(bytes: ByteArray): PdfResult {
        val text = if (bytes.size <= MAX_STREAM_SCAN) String(bytes, Charsets.ISO_8859_1) else ""

        val encrypted = text.contains("/Encrypt")
        val pageCount = countPages(text)
        val outline = if (encrypted) emptyList() else runCatching { parseOutline(bytes, text) }.getOrDefault(emptyList())
        val texts = if (encrypted) List(pageCount) { "" } else runCatching { extractText(bytes, text) }.getOrDefault(List(pageCount) { "" })

        return PdfResult(pageCount, outline, texts, encrypted)
    }

    /** 用 /Type /Page 的出现次数估页数；失败时退回 1 页。 */
    private fun countPages(text: String): Int {
        if (text.isEmpty()) return 1
        val n = Regex("""/Type\s*/Page[^s]""").findAll(text).count()
        return if (n > 0) n else 1
    }

    /**
     * 解析 /Outlines 目录树。
     *
     * PDF 的 Outlines 是链表结构：/First 指第一个子项，/Next 指同级下一个，
     * /Parent 用于层级。这里按对象出现的 /Title 顺序取，层级用 /First 嵌套深度近似。
     * 不追求完整还原层级 —— 目标是把章节名和顺序给出来。
     */
    private fun parseOutline(bytes: ByteArray, text: String): List<Pair<Int, String>> {
        if (!text.contains("/Outlines")) return emptyList()
        val out = ArrayList<Pair<Int, String>>()
        val titleRe = Regex("""/Title\s*\(([^)]*)\)""")
        for (m in titleRe.findAll(text)) {
            val title = decodePdfString(m.groupValues[1]).trim()
            if (title.isNotEmpty() && title.length <= 120) out.add(1 to title)
        }
        // 数量过多时截断，避免目录抽屉塞进上千条
        return out.take(500)
    }

    /** PDF 字面字符串转义：\( \) \\ \n 与八进制。 */
    fun decodePdfString(raw: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val n = raw[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    '(', ')', '\\' -> { sb.append(n); i += 2 }
                    else -> {
                        // 八进制 \ddd
                        var j = i + 1
                        val oct = StringBuilder()
                        while (j < raw.length && oct.length < 3 && raw[j] in '0'..'7') {
                            oct.append(raw[j]); j++
                        }
                        if (oct.isNotEmpty()) {
                            oct.toString().toIntOrNull(8)?.let { sb.append(it.toChar()) }
                            i = j
                        } else {
                            sb.append(n); i += 2
                        }
                    }
                }
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    /**
     * 抽取每页文字。
     *
     * 做法：找到所有未压缩的 content stream，按页顺序切分，
     * 解压 FlateDecode，再从 Tj/TJ 操作符里取字符串。
     *
     * 限制（重要）：不复原 /ToUnicode CMap。因此：
     *  - 纯 ASCII 的 PDF（多数英文技术文档）能正确抽出
     *  - 使用子集编码的 CJK PDF 抽出来多是乱码或空，这类文件搜索不到
     * 页面显示不受影响 —— 那只依赖 PdfRenderer。
     */
    private fun extractText(bytes: ByteArray, text: String): List<String> {
        val pageCount = countPages(text)
        val decoded = decodeAllStreams(bytes)
        // 汇总所有 ToUnicode CMap：CJK 子集编码必须靠它才能还原成可读文字
        val cmap = runCatching { mergeToUnicode(decoded) }.getOrDefault(emptyMap())
        val pages = MutableList(pageCount) { "" }

        // 按 stream 顺序大致对应页顺序；数量不足时把文字都归到第一页
        for ((index, content) in decoded.withIndex()) {
            val extracted = extractStringsFromContent(content, cmap)
            if (extracted.isBlank()) continue
            val target = index.coerceAtMost(pageCount - 1)
            pages[target] = (pages[target] + " " + extracted).trim()
        }
        return pages
    }

    /** 解压所有 FlateDecode 流。 */
    private fun decodeAllStreams(bytes: ByteArray): List<String> {
        val out = ArrayList<String>()
        var i = 0
        val needle = "stream".toByteArray(Charsets.ISO_8859_1)
        while (i < bytes.size - 6) {
            if (!matchesAt(bytes, i, needle)) { i++; continue }
            var dataStart = i + 6
            if (dataStart < bytes.size && bytes[dataStart] == 13.toByte()) dataStart++
            if (dataStart < bytes.size && bytes[dataStart] == 10.toByte()) dataStart++
            val endIdx = indexOf(bytes, "endstream".toByteArray(Charsets.ISO_8859_1), dataStart)
            if (endIdx < 0) break
            val slice = bytes.copyOfRange(dataStart, endIdx)
            val inflated = inflate(slice)
            // 未压缩的内容流（老 PDF 与手写生成器常见）：Inflate 失败时按原文收下，
            // 否则这类文件的文字层直接丢失（这是端到端测试抓到的真实缺陷）。
            val content = inflated ?: slice
            out.add(String(content, Charsets.ISO_8859_1))
            i = endIdx + 9
        }
        return out
    }

    private fun inflate(data: ByteArray): ByteArray? = runCatching {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        // 循环到 finished()；needsInput 时说明流读到尾了，但已解出的部分要保留。
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n > 0) {
                out.write(buf, 0, n)
                continue
            }
            // n == 0：既没产出又没结束，说明输入不足或需要字典，停止避免死循环
            if (inflater.needsInput() || inflater.needsDictionary()) break
        }
        inflater.end()
        out.toByteArray().takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** 从解压后的内容流里抽 Tj / TJ 字符串。 */
    /**
     * 从内容流抽文字。
     *
     * [cmap] 非空时按 CMap 把字符码还原成 Unicode —— 这是 CJK 子集编码
     * 能读出正确文字的唯一途径；为空时退回 Latin-1 直读（ASCII PDF 够用）。
     */
    fun extractStringsFromContent(content: String, cmap: Map<Int, String> = emptyMap()): String {
        val sb = StringBuilder()
        val literalRe = Regex("""[(]([^()]*)[)]\s*Tj""")
        for (m in literalRe.findAll(content)) {
            sb.append(decodeText(m.groupValues[1], cmap)).append(' ')
        }
        val arrayRe = Regex("""\[(.*?)\]\s*TJ""")
        val innerRe = Regex("""[(]([^()]*)[)]""")
        for (m in arrayRe.findAll(content)) {
            for (im in innerRe.findAll(m.value)) {
                sb.append(decodeText(im.groupValues[1], cmap))
            }
            sb.append(' ')
        }
        return sb.toString().replace(WHITESPACE, " ").trim()
    }

    /**
     * 解码括号内的一段文字。
     *
     * 有 CMap 时按「每 2 字节一个码」切分（CJK 复合字体常见 2 字节编码），
     * 逐码查表；查不到就保留原字符，避免整段丢失。
     * 无 CMap 时按 PDF 字面转义解码后直读。
     */
    fun decodeText(raw: String, cmap: Map<Int, String>): String {
        if (cmap.isEmpty()) return decodePdfString(raw)
        val decoded = decodePdfString(raw)
        if (decoded.isEmpty()) return ""
        // 先假设 2 字节编码（CJK 复合字体常见）：若有任意一个码命中 CMap 就采用
        if (decoded.length >= 2) {
            var hits = 0
            var i = 0
            val sb = StringBuilder()
            while (i + 1 < decoded.length) {
                val code = (decoded[i].code shl 8) or decoded[i + 1].code
                val mapped = cmap[code]
                if (mapped != null) { hits++; sb.append(mapped) }
                else sb.append(decoded[i]).append(decoded[i + 1])
                i += 2
            }
            if (i < decoded.length) sb.append(decoded[i])
            if (hits > 0) return sb.toString()
        }
        // 退回 1 字节编码：逐字符查表
        val out = StringBuilder()
        var singleHits = 0
        for (ch in decoded) {
            val mapped = cmap[ch.code]
            if (mapped != null) { singleHits++; out.append(mapped) } else out.append(ch)
        }
        return if (singleHits > 0) out.toString() else decoded
    }

    private fun matchesAt(hay: ByteArray, at: Int, needle: ByteArray): Boolean {
        if (at + needle.size > hay.size) return false
        for (k in needle.indices) if (hay[at + k] != needle[k]) return false
        return true
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray, from: Int): Int {
        var i = from
        while (i <= hay.size - needle.size) {
            if (matchesAt(hay, i, needle)) return i
            i++
        }
        return -1
    }

    // ── ToUnicode CMap ────────────────────────────────────────

    /**
     * 解析 /ToUnicode CMap，建立「字符码 -> Unicode 字符串」映射。
     *
     * CMap 的 bfchar 段形如：
     *   <0001> <4E00>
     *   <0002> <4E8C>
     * 多字节码用 bfrange：
     *   <0000> <00FF> <E000>
     *
     * 有这张表就能把子集编码还原成可读文字；没有则字符码直接当 Latin-1 用，
     * 这也是 CJK PDF 抽不出正确文字的根本原因。
     */
    fun parseToUnicode(cmap: String): Map<Int, String> {
        val map = HashMap<Int, String>()
        // bfchar 段
        val bfcharRe = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""")
        val sectionRe = Regex("""beginbfchar(.*?)endbfchar""", RegexOption.DOT_MATCHES_ALL)
        for (sec in sectionRe.findAll(cmap)) {
            for (m in bfcharRe.findAll(sec.groupValues[1])) {
                val code = m.groupValues[1].toIntOrNull(16) ?: continue
                map[code] = hexToUnicode(m.groupValues[2])
            }
        }
        // bfrange 段：<lo> <hi> <dst>，dst 逐码递增
        val rangeRe = Regex("""<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>""")
        val rangeSecRe = Regex("""beginbfrange(.*?)endbfrange""", RegexOption.DOT_MATCHES_ALL)
        for (sec in rangeSecRe.findAll(cmap)) {
            for (m in rangeRe.findAll(sec.groupValues[1])) {
                val lo = m.groupValues[1].toIntOrNull(16) ?: continue
                val hi = m.groupValues[2].toIntOrNull(16) ?: continue
                val dst = m.groupValues[3].toIntOrNull(16) ?: continue
                if (hi < lo || hi - lo > 65535) continue
                for (k in lo..hi) map[k] = (dst + (k - lo)).toChar().toString()
            }
        }
        return map
    }

    /** 4 位（或 8 位代理对）十六进制串 -> Unicode 字符串。 */
    private fun hexToUnicode(hex: String): String {
        if (hex.length <= 4) {
            val cp = hex.toIntOrNull(16) ?: return ""
            return cp.toChar().toString()
        }
        // 8 位 = UTF-16 代理对
        val sb = StringBuilder()
        var i = 0
        while (i + 4 <= hex.length) {
            val cp = hex.substring(i, i + 4).toIntOrNull(16) ?: break
            sb.append(cp.toChar())
            i += 4
        }
        return sb.toString()
    }

    /** 从所有已解压的 stream 里收集 CMap 并合并（不同字体的映射表相互独立）。 */
    fun mergeToUnicode(streams: List<String>): Map<Int, String> {
        val merged = HashMap<Int, String>()
        for (s in streams) {
            if (!s.contains("beginbfchar") && !s.contains("beginbfrange")) continue
            merged.putAll(parseToUnicode(s))
        }
        return merged
    }
}

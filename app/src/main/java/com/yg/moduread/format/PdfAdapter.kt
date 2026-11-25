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
        val outline: List<Triple<Int, Int, String>>,
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
    /**
     * 解析 PDF。页数与每页尺寸以 [PdfRenderer] 为准。
     *
     * 为什么不靠正则数 /Type /Page：那个字符串在真实 PDF 里会出现在对象流、
     * 注释、甚至页面树的引用里，数出来常常偏多。
     * 多出来的页没有真实内容，PdfRenderer.openPage 就越界报
     * "Invalid page index"（用户实测到的问题）。
     * PdfRenderer.pageCount 是权威值，直接用它。
     */
    fun parse(
        bytes: ByteArray,
        pageSizes: List<Pair<Int, Int>>? = null,
        /** 已有元信息时直接复用，避免重复解压与正则扫描（大文件会慢十倍）。 */
        known: PdfResult? = null
    ): ParsedDoc {
        // 不要在这里重新 inspect：DocRepository 已经取过一次元信息，
        // 再取一次等于把「解压所有流 + 全文正则」重跑一遍，
        // 这是大 PDF「一直转圈」的主因。
        val info = known ?: inspect(bytes, withText = false)
        val blocks = ArrayList<MdBlock>()

        // 不在正文里插入目录标题。
        //
        // PDF 目录（/Outlines）是导航索引，页面本身已经把标题画进内容了；
        // 再插一遍会让正文开头出现一大段重复标题（用户实测反馈）。
        // 目录抽屉直接读 info.outline，不依赖这些块。

        val count = pageSizes?.size ?: info.pageCount
        for (i in 0 until count) {
            val size = pageSizes?.getOrNull(i)
            blocks.add(MdBlock.PdfPage(i, size?.first ?: 595, size?.second ?: 842))
        }

        return ParsedDoc(blocks, info.textByPage.joinToString(" "))
    }

    /**
     * 只取元信息，不构造块。
     *
     * [withText] 为 false 时跳过全文提取 —— 这一步要对每个 stream 做 Inflate + 正则，
     * 大 PDF 上要几秒甚至十几秒。打开文档只需要页数与目录，
     * 文字层留给搜索时再按需提取，否则用户会看到长时间的「加载中」。
     */
    fun inspect(bytes: ByteArray, withText: Boolean = false): PdfResult {
        val text = if (bytes.size <= MAX_STREAM_SCAN) String(bytes, Charsets.ISO_8859_1) else ""

        val encrypted = text.contains("/Encrypt")
        val pageCount = countPages(text)
        val outline = if (encrypted) emptyList() else runCatching { parseOutline(bytes, text) }.getOrDefault(emptyList())
        val texts = if (!withText || encrypted) {
            List(pageCount) { "" }
        } else {
            runCatching { extractText(bytes, text) }.getOrDefault(List(pageCount) { "" })
        }

        return PdfResult(pageCount, outline, texts, encrypted)
    }

    /**
     * 统计页数：用 indexOf 线性扫描，不用正则。
     *
     * 正则匹配在 15MB 字符串上会回溯，
     * 设备端实测要几十秒（JVM 预热后看不出，冷启动才暴露）。
     * indexOf 是纯线性扫描，快一个数量级。
     */
    private fun countPages(text: String): Int {
        if (text.isEmpty()) return 1
        var n = 0
        var i = 0
        while (true) {
            val p2 = text.indexOf("/Type", i)
            if (p2 < 0) break
            var j = p2 + 5
            while (j < text.length) {
                val c = text[j]
                if (c == 0x20.toChar() || c == 0x09.toChar() || c == 0x0D.toChar() || c == 0x0A.toChar()) j++ else break
            }
            if (text.startsWith("/Page", j)) {
                val after = j + 5
                if (after >= text.length || (text[after] != 0x73.toChar() && !text[after].isLetterOrDigit())) n++
                i = after
            } else {
                i = p2 + 5
            }
        }
        return if (n > 0) n else 1
    }

        /**
     * 解析 /Outlines 目录。
     *
     * 按「对象块」而不是「Title 之后找 Dest」来解析：
     * 这份文档里 /Dest 出现在 /Title **之前**
     *   281 0 obj <</Dest [272 0 R /XYZ ...] /Title (...) >>
     * 之前假设 Title 在前，导致 Dest 全部匹配失败、页码退化成顺序编号。
     *
     * 用对象号在页面树里的位置换算真实页序。
     */
    private fun parseOutline(bytes: ByteArray, text: String): List<Triple<Int, Int, String>> {
        // 改走 /First-Next 树序遍历：物理字节顺序与阅读顺序无关，
        // 按扫描顺序输出会让目录错乱（4.2.1 排在 4.1 前面）。
        return parseOutlineV2(text)
    }

    /**
     * 解析目录：按 /First + /Next 树序遍历。
     *
     * 为什么必须走树：scanObjects 返回的是「对象在文件里的物理顺序」，
     * 与阅读顺序无关。之前直接按它输出，目录就乱了
     * （4.2.1 排在 4.1 前面，用户实测反馈）。
     * /First 指向首个子项，/Next 指向下一个兄弟，
     * 递归下去既得到正确的文档顺序，也天然得到层级。
     */
    private fun parseOutlineV2(text: String): List<Triple<Int, Int, String>> {
        if (!text.contains("/Outlines")) return emptyList()

        val pageOrder = collectPageOrder(text)
        val objs = HashMap<Int, String>()
        for ((num, body) in scanObjects(text, 4000)) objs[num] = body

        // 找 Outlines 根：必须从 Catalog 的 /Outlines 引用进来。
        // 真正的根对象里并没有 "/Outlines" 字样
        // （全文只出现一次，在 Catalog 里），
        // 所以按关键字搜对象永远搜不到。
        var rootFirst: Int? = null
        val rootRef = Regex("""/Outlines\s+(\d+)\s+0\s+R""").find(text)
            ?.groupValues?.get(1)?.toIntOrNull()
        if (rootRef != null) {
            val rootBody = objs[rootRef]
            if (rootBody != null) rootFirst = refOf(rootBody, "First")
        }
        // 兜底：某些生成器直接内联，退化为找带 /First 且被 /Title 索引的对象
        if (rootFirst == null) {
            for ((num, body) in objs) {
                if (body.contains("/First") && !body.contains("/Title")) {
                    val f = refOf(body, "First")
                    if (f != null && objs.containsKey(f)) { rootFirst = f; break }
                }
            }
        }
        if (rootFirst == null) return emptyList()

        val out = ArrayList<Triple<Int, Int, String>>()
        // 与 out 等长：每条的页内 y 坐标（PDF 坐标系，值越大越靠上）
        val destYs = ArrayList<Double>()

        fun walk(firstObj: Int, depth: Int) {
            var cur: Int? = firstObj
            var siblings = 0
            while (cur != null && siblings < 400 && out.size < 600) {
                val body = objs[cur] ?: break

                // 标题
                val tm = Regex("""/Title\s*(\((?:[^()\\\\]|\\\\.)*\)|<[0-9A-Fa-f]+>)""").find(body)
                if (tm != null) {
                    val raw = tm.groupValues[1]
                    val title = if (raw.startsWith("<")) {
                        decodeHexTitle(raw.trim('<', '>'))
                    } else {
                        decodeTitleBytes(raw.substring(1, raw.length - 1))
                    }
                    if (title.isNotEmpty() && title.length <= 120 &&
                        title.none { it == 0xFFFD.toChar() || it.code in 0xE000..0xF8FF } &&
                        isMostlyReadable(title)
                    ) {
                        // 目标页 + 页内 Y 坐标。
                        // /Dest [obj 0 R /XYZ x y z] 里的 y 是页内位置：
                        // 同一页常挂多个标题（实测第 7 页有 3 个），
                        // 只用页号无法区分，高亮和跳转都会落到页首那一条。
                        var pageIdx = -1
                        var destY = -1.0
                        val dm = Regex("""/Dest\s*\[?\s*(\d+)\s+0\s+R(?:\s*/XYZ\s+[-\d.]+\s+([-\d.]+))?""")
                            .find(body)
                        if (dm != null) {
                            dm.groupValues[1].toIntOrNull()?.let { po ->
                                pageIdx = pageOrder.indexOf(po)
                            }
                            destY = dm.groupValues.getOrNull(2)?.toDoubleOrNull() ?: -1.0
                        }
                        if (pageIdx < 0) pageIdx = out.lastOrNull()?.first ?: 0
                        val prevPage = out.lastOrNull()?.first ?: 0
                        if (pageIdx < prevPage) pageIdx = prevPage
                        out.add(Triple(pageIdx, depth.coerceIn(1, 6), title))
                        // 记录每条的页内高度：PDF 的 y 轴向上，越大越靠上。
                        // 用于同页多条目的排序与「当前页首个条目」判定。
                        destYs.add(destY)
                    }
                }

                // 先下钻子项，再走兄弟 —— 这保持了文档顺序
                refOf(body, "First")?.let { walk(it, depth + 1) }
                cur = refOf(body, "Next")
                siblings++
            }
        }

        walk(rootFirst, 1)
        // 同页多条目按 y 降序排列：PDF 的 y 越大越靠上，
        // 这样「该页第一条」就是页面上方的那条标题，
        // 与阅读时「先看到它」的顺序一致。
        // 页序不能动，所以只在同一页内重排。
        if (destYs.size == out.size && out.size > 1) {
            val grouped = LinkedHashMap<Int, MutableList<Int>>()
            out.indices.forEach { i -> grouped.getOrPut(out[i].first) { ArrayList() }.add(i) }
            val reordered = ArrayList<Triple<Int, Int, String>>(out.size)
            for ((_, idxs) in grouped) {
                idxs.sortedByDescending { destYs[it] }.forEach { reordered.add(out[it]) }
            }
            out.clear()
            out.addAll(reordered)
        }
        return out
    }

    /** 取字典里的对象引用，如 /First 282 0 R -> 282。 */
    private fun refOf(body: String, key: String): Int? =
        Regex("/" + key + "\\s+(\\d+)\\s+0\\s+R").find(body)
            ?.groupValues?.get(1)?.toIntOrNull()

    /**
     * 从标题编号推断层级。
     *
     * "1." → 1，"1.1" → 2，"1.1.1" → 3。
     * 只读开头连续的「数字 + 点」段，遇到别的字符就停，
     * 避免把 "1.1 登录（v2.0）" 里的版本号也算成层级。
     */
    private fun countSectionDots(title: String): Int {
        val t = title.trimStart()
        var dots = 0
        var i = 0
        var expectDigit = true
        while (i < t.length) {
            val c = t[i]
            if (expectDigit) {
                if (!c.isDigit()) break
                expectDigit = false
                i++
            } else {
                if (c == '.') {
                    dots++
                    expectDigit = true
                    i++
                } else {
                    break
                }
            }
        }
        return if (dots > 0) dots + 1 else 0
    }

    /**
     * 线性扫描出所有 "N 0 obj << ... >>" 对象块。
     *
     * 不用正则 ""(\d+)\s+0\s+obj\s*<<([\s\S]{0,3000}?)>>""：
     * 非贪婪跨行匹配在 15MB 文本上会反复回溯，
     * 设备端实测要几十秒（JVM 预热看不出，冷启动才暴露）。
     * indexOf 定位边界是纯线性扫描。
     */
    private fun scanObjects(text: String, maxBody: Int = 3000): List<Pair<Int, String>> {
        val out = ArrayList<Pair<Int, String>>()
        var i = 0
        while (true) {
            val objAt = text.indexOf("obj", i)
            if (objAt < 0) break
            // 对象号在 "obj" 之前，形如 "5 0 obj" 或 "1068 0 obj"。
            // 必须跳过中间的 "0"（generation 号）再取序号；
            // 之前从 " obj" 往前连着读数字，读到的其实是 "0"，
            // 所有对象号都变成 0（实测踩过，目录因此完全找不到）。
            var k = objAt - 1
            while (k >= 0 && (text[k] == ' ' || text[k] == '\t')) k--
            // 跳过 generation
            while (k >= 0 && text[k].isDigit()) k--
            var genEnd = k
            while (genEnd >= 0 && (text[genEnd] == ' ' || text[genEnd] == '\t')) genEnd--
            k = genEnd
            while (k >= 0 && text[k].isDigit()) k--
            val num = if (k < genEnd) {
                text.substring(k + 1, genEnd + 1).toIntOrNull()
            } else null
            val lt = text.indexOf("<<", objAt)
            if (num != null && lt >= 0 && lt - objAt < 40) {
                val end = text.indexOf(">>", lt)
                if (end > lt && end - lt <= maxBody) {
                    out.add(num to text.substring(lt + 2, end))
                    i = end + 2
                    continue
                }
            }
            i = objAt + 4
        }
        return out
    }

    /**
     * 收集页面对象号的顺序。
     *
     * 优先用页面树的 /Kids —— PDF 里唯一的权威页序来源。
     * 之前用 "<<" 紧跟 /Type /Page 的正则，但页面对象里常有嵌套字典
     * （/Resources << ... >>），[^>]*? 会提前截断，导致大量页面匹配不到、
     * 页序错乱（目录页码与内容不对应就是这么来的）。
     */
    private fun collectPageOrder(text: String): List<Int> {
        // 1) /Kids 扁平化
        val kids = ArrayList<Int>()
        for (objPair in scanObjects(text, 4000)) {
            val body = objPair.second
            if (!body.contains("/Type") || !body.contains("/Pages")) continue
            val kidsMatch = Regex("""/Kids\s*\[([^\]]*)\]""").find(body) ?: continue
            val refRe = Regex("""(\d+)\s+0\s+R""")
            for (k in refRe.findAll(kidsMatch.groupValues[1])) {
                val v = k.groupValues[1].toIntOrNull() ?: continue
                if (!kids.contains(v)) kids.add(v)
            }
        }
        if (kids.isNotEmpty()) return kids

        // 2) 兜底：按对象出现顺序
        val out = ArrayList<Int>()
        val fallback = Regex("""(\d+)\s+0\s+obj\s*<<([\s\S]{0,600}?)>>""")
        for (m in fallback.findAll(text)) {
            if (Regex("""/Type\s*/Page\b""").containsMatchIn(m.groupValues[2])) {
                m.groupValues[1].toIntOrNull()?.let { out.add(it) }
            }
        }
        return out
    }

            /**
     * 解码 PDF 标题。
     *
     * 关键：字节流里同时有两层编码 —— PDF 字符串转义 与 UTF-16BE。
     * PDF 规范要求字面量里的 ( ) \ 必须转义，所以 UTF-16 码元中
     * 恰好含这些字节时会被写成 "5c XX"：
     *
     *   "用" = 7528 → 字节 28 是 '(' → 落盘为 75 5c 28
     *   "(1)" = 0028 0031 0029 → 落盘为 00 5c 28 00 31 00 5c 29
     *
     * 因此必须「先反转义，再按 UTF-16BE 解码」。
     * 之前直接在字节流上按固定宽度读，遇到转义就错位，才会出现零星乱码。
     */
    private fun decodeTitleBytes(raw: String): String {
        if (raw.length < 2) return ""
        val hasBom = raw[0].code == 0xFE && raw[1].code == 0xFF
        val body = if (hasBom) raw.substring(2) else raw

        // 第一步：反转义（5c 后面那个字节按字面量处理）
        val bytes = ArrayList<Byte>(body.length)
        var i = 0
        while (i < body.length) {
            val c = body[i].code
            if (c == 0x5C && i + 1 < body.length) {
                bytes.add(body[i + 1].code.toByte())
                i += 2
            } else {
                bytes.add(c.toByte())
                i++
            }
        }

        // 第二步：按 UTF-16BE 解码（奇数长度补齐，避免越界）
        if (bytes.isEmpty()) return ""
        val len = if (bytes.size % 2 == 0) bytes.size else bytes.size - 1
        if (len <= 0) return ""
        val arr = ByteArray(len) { bytes[it] }
        val decoded = runCatching { String(arr, Charsets.UTF_16BE) }.getOrNull().orEmpty()

        // 过滤控制字符与替换符
        val sb = StringBuilder(decoded.length)
        for (ch in decoded) {
            val code = ch.code
            if (code == 0xFFFD) continue
            if (code in 0xE000..0xF8FF) continue
            if (code < 0x20 && code != 0x09) continue
            sb.append(ch)
        }
        return sb.toString().trim()
    }

    /** 字符串里可读字符（字母/数字/CJK/常见标点）是否占多数。 */
    private fun isMostlyReadable(s: String): Boolean {
        if (s.isEmpty()) return false
        var good = 0
        for (c in s) {
            if (c.isLetterOrDigit() || c.isWhitespace() || c in "，。、（）()[]-.:：_") good++
        }
        return good >= s.length * 0.7
    }

    private fun decodeHexTitle(hex: String): String {
        if (hex.length % 4 != 0) return ""
        val bytes = ByteArray(hex.length / 2)
        var i = 0
        while (i + 1 < hex.length) {
            val b = hex.substring(i, i + 2).toIntOrNull(16) ?: return ""
            bytes[i / 2] = b.toByte()
            i += 2
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE).trim()
        }
        // 无 BOM：按 UTF-16BE 试一次，能解出可读文字就用
        val guess = runCatching { String(bytes, Charsets.UTF_16BE).trim() }.getOrNull().orEmpty()
        return if (guess.isNotEmpty() && isReadable(guess)) guess else ""
    }

    /** 粗略判断字符串是否像正常文字（含足够多的可打印字符）。 */
    private fun isReadable(s: String): Boolean {
        var good = 0
        for (c in s) {
            if (c.isLetterOrDigit() || c.isWhitespace() || c in "，。、（）()-.：:") good++
        }
        return good >= s.length * 0.6
    }

    /** PDF 字面字符串转义：\( \) \\ \n 与八进制。 */
    fun decodePdfString(raw: String): String {
        // 以 FEFF 开头的字面串是 UTF-16BE（PDF 里 CJK 标题的标准写法）。
        // 必须先处理：否则字节被当作 Latin-1 单字符，
        // 结果就是 "þÿN..." 这类乱码（用户截图里正是这种）。
        if (raw.length >= 2 && raw[0].code == 0xFE && raw[1].code == 0xFF) {
            // 先按 UTF-16BE 正常解码字节，再清理结果里的字面转义。
            //
            // 不在字节层处理转义：反斜杠在 UTF-16 里是高位 0x5C，
            // 提前跳过会把码元撞坏，反而产生 "(1" 这类残缺标题。
            // 长度可能是奇数：PDF 里 "\(" 只占 1 字节，与 2 字节的汉字混排后
            // 总长就不是偶数了。补一个 0 凑齐码元，否则整条标题会掉进兜底分支
            // 变成 "þÿ ( 1 " 这种更乱的形式（实测踩过）。
            val bodyLen = raw.length - 2
            val padded = if (bodyLen % 2 == 0) bodyLen else bodyLen + 1
            val bytes = ByteArray(padded)
            for (k in 2 until raw.length) bytes[k - 2] = raw[k].code.toByte()
            if (padded > 0) {
                val decoded = runCatching { String(bytes, Charsets.UTF_16BE) }.getOrNull()
                if (!decoded.isNullOrEmpty()) {
                    // 清理可能残留的 LaTeX 风格转义（\( \) \ 等）
                    // 去掉残留的反斜杠：PDF 标题里常把括号写作 \( \)，
                    // 解码后这些反斜杠就成了可见字符（截图里的 "ば(3\" 就是这样）。
                    // 逐字符过滤比正则更不容易出错。
                    // 有些文件带 BOM 但正文是单字节 ASCII（例如 "(1\"），
                    // 按 UTF-16 解出来会是全角怪字符。此时退回逐字节读取。
                    if (!isMostlyReadable(decoded)) {
                        val sb3 = StringBuilder(raw.length)
                        var z = 2
                        while (z < raw.length) {
                            val c = raw[z]
                            if (c == 0x5C.toChar() && z + 1 < raw.length) {
                                sb3.append(raw[z + 1])
                                z += 2
                            } else {
                                sb3.append(c)
                                z++
                            }
                        }
                        return sb3.toString().replace("\u0000", "").trimEnd(0x5C.toChar()).trim()
                    }
                    val sb2 = StringBuilder(decoded.length)
                    var q = 0
                    while (q < decoded.length) {
                        val ch = decoded[q]
                        if (ch == 0x5C.toChar() && q + 1 < decoded.length) {
                            sb2.append(decoded[q + 1])
                            q += 2
                        } else {
                            sb2.append(ch)
                            q++
                        }
                    }
                    return sb2.toString().trim()
                }
            }
        }
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

    /** 调试：暴露流数量与 CMap 规模（测试用）。 */
    fun dbgStreams(bytes: ByteArray): Pair<Int, Int> {
        val d = decodeAllStreams(bytes)
        val c = mergeToUnicode(d)
        return d.size to c.size
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

            // 跳过图片流：DCTDecode(JPEG) / JPXDecode 的内容无法也不需当文本解析。
            // 14MB 的文档里图片流占绝大部分，全解压一遍就是「一直转圈」的主因。
            // 用字节判断图片流：JPEG 以 FF D8 开头。
            // 注意不能写成 head.startsWith("") —— 空前缀恒为 true，
            // 会把所有流（含内容流）全跳过，文字层直接消失（实测踩过）。
            val isJpeg = slice.size >= 2 &&
                (slice[0].toInt() and 0xFF) == 0xFF &&
                (slice[1].toInt() and 0xFF) == 0xD8
            if (isJpeg) {
                i = endIdx + 9
                continue
            }


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
    /** 从所有已解压的 stream 里收集 CMap 并合并（不同字体的映射表相互独立）。 */
    fun mergeToUnicode(streams: List<String>): Map<Int, String> {
        val merged = HashMap<Int, String>()
        for (s in streams) {
            if (!s.contains("beginbfchar") && !s.contains("beginbfrange")) continue
            merged.putAll(parseToUnicode(s))
        }
        return merged
    }

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

    private fun hexToUnicode(hex: String): String {
        if (hex.length <= 4) {
            val cp = hex.toIntOrNull(16) ?: return ""
            return cp.toChar().toString()
        }
        val sb = StringBuilder()
        var i = 0
        while (i + 4 <= hex.length) {
            val cp = hex.substring(i, i + 4).toIntOrNull(16) ?: break
            sb.append(cp.toChar())
            i += 4
        }
        return sb.toString()
    }
}

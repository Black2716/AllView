package com.yg.moduread.md

/**
 * 从 AST 抽目录，并把 GitHub 风格的锚点 slug 解析回来。
 *
 * 存在的理由：技术书普遍在开头放一个 `[章节](#锚点)` 目录，
 * 而 commonmark 只把 `#锚点` 当成一个普通 link destination，不做文档内跳转。
 * 这里把标题文本按 GitHub 的规则生成 slug，就能让那些链接真正跳起来。
 */
data class TocEntry(
    val level: Int,
    val text: String,
    /** 在 MdBlock 列表里的下标（不是 LazyColumn 的 item 下标）。 */
    val blockIndex: Int,
    val slug: String
)

object MdToc {

    /** 抽取目录。level 范围按 Markdown 规范做 1..6 收敛。 */
    fun build(blocks: List<MdBlock>): List<TocEntry> =
        blocks.mapIndexedNotNull { index, block ->
            if (block !is MdBlock.Heading) null else {
                val text = block.content.plainText().trim()
                if (text.isEmpty()) null else TocEntry(
                    level = block.level.coerceIn(1, 6),
                    text = text,
                    blockIndex = index,
                    slug = slug(text)
                )
            }
        }

    /**
     * GitHub 的标题锚点规则：
     *   1. 转小写
     *   2. 去掉标点（保留字母、数字、空格、连字符、下划线，中日韩字符原样保留）
     *   3. 空格转连字符
     *
     * 例："第 0 章 走进红外小目标检测" -> "第-0-章-走进红外小目标检测"
     */
    fun slug(heading: String): String {
        val sb = StringBuilder(heading.length)
        for (ch in heading.lowercase()) {
            when {
                ch == ' ' -> sb.append('-')
                ch == '-' || ch == '_' -> sb.append(ch)
                ch.isLetterOrDigit() -> sb.append(ch)
                // 其余标点、emoji、全角符号一律丢弃
                else -> Unit
            }
        }
        return sb.toString()
    }

    /**
     * slug -> blockIndex。同名标题按 GitHub 习惯加 `-1`、`-2` 后缀去重，
     * 保证目录里两个「小结」都能各自跳对位置。
     */
    fun index(entries: List<TocEntry>): Map<String, Int> {
        val out = HashMap<String, Int>(entries.size)
        val seen = HashMap<String, Int>(entries.size)
        entries.forEach { entry ->
            val n = seen.getOrDefault(entry.slug, 0)
            seen[entry.slug] = n + 1
            val key = if (n == 0) entry.slug else entry.slug + "-" + n
            out[key] = entry.blockIndex
            // 也登记裸 slug，兼容只写了第一次出现的文档
            out.putIfAbsent(entry.slug, entry.blockIndex)
        }
        return out
    }

    /** 目录里最常见的形态：`[文本](#slug)`。取出 slug；不是文档内链接返回 null。 */
    fun anchorOf(url: String): String? {
        if (!url.startsWith("#")) return null
        val raw = url.substring(1)
        if (raw.isEmpty()) return null
        // 有些文档写成 #toc_0 这类非 ASCII 锚点，原样返回即可
        return runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }
}

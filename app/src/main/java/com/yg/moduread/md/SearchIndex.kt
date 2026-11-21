package com.yg.moduread.md

/** 一处命中：哪个块、块内字符区间。 */
data class SearchHit(
    val blockIndex: Int,
    /** 块内命中起点（用于高亮）。 */
    val start: Int,
    val end: Int,
    /** 命中所在的整块文本，供结果列表显示上下文。 */
    val context: String
)

/**
 * 全文搜索索引。
 *
 * 解析完成后一次性把每个块的纯文本拼进一个大字符串，并记录每块的起止偏移。
 * 搜索就是在这个大字符串上做 indexOf，命中后二分回块索引 —— 比逐块扫描快，
 * 也天然支持「跨块不命中」（因为拼接时插入了分隔符）。
 */
class SearchIndex private constructor(
    private val text: String,
    /** 每块的 [start, end) 偏移，下标即块索引。 */
    private val spans: IntArray
) {

    val blockCount: Int get() = spans.size / 2

    /**
     * 按关键词搜全部命中。大小写不敏感。
     * [limit] 防止「的」这类单字在长文里炸出几千条。
     */
    fun search(query: String, limit: Int = 200): List<SearchHit> {
        val q = query.trim()
        if (q.length < MIN_QUERY) return emptyList()
        val haystack = text.lowercase()
        val needle = q.lowercase()
        val hits = ArrayList<SearchHit>()
        var from = 0
        while (hits.size < limit) {
            val at = haystack.indexOf(needle, from)
            if (at < 0) break
            val blockIndex = blockOf(at)
            if (blockIndex >= 0) {
                val start = spans[blockIndex * 2]
                val end = spans[blockIndex * 2 + 1]
                hits.add(
                    SearchHit(
                        blockIndex = blockIndex,
                        start = at - start,
                        end = at - start + needle.length,
                        context = text.substring(start, minOf(end, start + CONTEXT_LEN)).replace('\n', ' ')
                    )
                )
            }
            from = at + needle.length.coerceAtLeast(1)
        }
        return hits
    }

    /** 二分找到偏移所属的块。 */
    private fun blockOf(offset: Int): Int {
        var lo = 0
        var hi = blockCount - 1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val start = spans[mid * 2]
            val end = spans[mid * 2 + 1]
            when {
                offset < start -> hi = mid - 1
                offset >= end -> lo = mid + 1
                else -> return mid
            }
        }
        return -1
    }

    companion object {
        /** 单字符查询太吵（"的" 能出几千条），至少两个字符。 */
        const val MIN_QUERY = 2
        private const val CONTEXT_LEN = 60

        fun build(blocks: List<MdBlock>): SearchIndex {
            val sb = StringBuilder()
            val spans = IntArray(blocks.size * 2)
            blocks.forEachIndexed { index, block ->
                val plain = block.searchableText()
                spans[index * 2] = sb.length
                sb.append(plain)
                spans[index * 2 + 1] = sb.length
                // 块之间插分隔符：避免「上一块结尾 + 下一块开头」拼出假命中
                sb.append('\n')
                spans[index * 2 + 1] = spans[index * 2] + plain.length
            }
            return SearchIndex(sb.toString(), spans)
        }
    }
}

/** 提取块的纯文本（用于搜索与上下文展示）。表格取全部单元格。 */
fun MdBlock.searchableText(): String = when (this) {
    is MdBlock.Heading -> content.plainText()
    is MdBlock.Para -> content.plainText()
    is MdBlock.Quote -> children.joinToString(" ") { it.searchableText() }
    is MdBlock.ListBlock -> items.joinToString(" ") { item ->
        item.joinToString(" ") { it.searchableText() }
    }
    is MdBlock.CodeBlock -> code
    is MdBlock.Math -> latex
    is MdBlock.Table -> (listOf(head) + rows).joinToString(" ") { row ->
        row.joinToString(" ") { cell -> cell.inlines.plainText() }
    }
    is MdBlock.Image -> alt
    is MdBlock.Rule -> ""
}

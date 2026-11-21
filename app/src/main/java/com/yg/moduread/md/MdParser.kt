package com.yg.moduread.md

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListBlock as CmListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/**
 * commonmark-java -> 轻量 AST 的一次性转换。
 * 只保留阅读器需要的节点，未知节点丢弃，避免渲染期做类型判断。
 */
class MdParser {

    private val parser: Parser = Parser.builder()
        .extensions(listOf(TablesExtension.create()))
        .build()

    fun parse(source: String): List<MdBlock> = parseWithMath(source)

    /**
     * commonmark 不认识 $$ 公式块，会把它当成普通段落原样输出。
     * 因此在交给解析器之前，先把源码按 $$ 切成「Markdown 段 / 公式段」交替的片段，
     * Markdown 段正常解析，公式段直接成为 MdBlock.Math。
     */
    private fun parseWithMath(source: String): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        for ((text, isMath) in splitMathBlocks(source)) {
            if (isMath) {
                out.add(MdBlock.Math(text.trim(), display = true))
            } else {
                if (text.isBlank()) continue
                out.addAll(blocksOf(parser.parse(text)))
            }
        }
        return out
    }

    /** 按 $$ 切分；偶数段是 Markdown，奇数段是公式内容。 */
    private fun splitMathBlocks(source: String): List<Pair<String, Boolean>> {
        val parts = ArrayList<Pair<String, Boolean>>()
        var cursor = 0
        while (true) {
            val open = source.indexOf("$$", cursor)
            if (open < 0) {
                parts.add(source.substring(cursor) to false)
                break
            }
            // 转义的 \$\$ 不算公式
            if (open > 0 && source[open - 1] == '\\') {
                parts.add(source.substring(cursor, open + 2) to false)
                cursor = open + 2
                continue
            }
            val close = source.indexOf("$$", open + 2)
            if (close < 0) {
                parts.add(source.substring(cursor) to false)
                break
            }
            parts.add(source.substring(cursor, open) to false)
            parts.add(source.substring(open + 2, close) to true)
            cursor = close + 2
        }
        return parts
    }

    private fun blocksOf(parent: Node): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        var child = parent.firstChild
        while (child != null) {
            blockOf(child)?.let(out::add)
            child = child.next
        }
        return out
    }

    private fun blockOf(node: Node): MdBlock? = when (node) {
        is Heading -> MdBlock.Heading(node.level, inlinesOf(node))
        is Paragraph -> imageBlockOf(node) ?: MdBlock.Para(inlinesOf(node))
        is BlockQuote -> MdBlock.Quote(blocksOf(node))
        is BulletList -> listBlockOf(node, ordered = false)
        is OrderedList -> listBlockOf(node, ordered = true)
        is FencedCodeBlock -> MdBlock.CodeBlock(
            node.literal.orEmpty().trimEnd('\n'),
            node.info?.trim()?.takeIf { it.isNotEmpty() }
        )

        is IndentedCodeBlock -> MdBlock.CodeBlock(node.literal.orEmpty().trimEnd('\n'), null)
        is ThematicBreak -> MdBlock.Rule
        is TableBlock -> tableOf(node)
        is HtmlBlock -> node.literal.trim().takeIf { it.isNotEmpty() }?.let {
            MdBlock.Para(listOf(MdInline.Text(it)))
        }

        else -> null
    }

    private fun listBlockOf(node: CmListBlock, ordered: Boolean): MdBlock {
        val items = ArrayList<List<MdBlock>>()
        var child = node.firstChild
        while (child != null) {
            if (child is ListItem) items.add(blocksOf(child))
            child = child.next
        }
        val start = if (node is OrderedList) node.markerStartNumber ?: 1 else 1
        return MdBlock.ListBlock(ordered, start, node.isTight, items)
    }

    private fun tableOf(node: Node): MdBlock.Table {
        var head: List<MdTableCell> = emptyList()
        val rows = ArrayList<List<MdTableCell>>()
        var child = node.firstChild
        while (child != null) {
            if (child is TableHead) {
                head = rowsOf(child).firstOrNull() ?: emptyList()
            } else if (child is TableBody) {
                rows.addAll(rowsOf(child))
            }
            child = child.next
        }
        return MdBlock.Table(head, rows)
    }

    private fun rowsOf(parent: Node): List<List<MdTableCell>> {
        val rows = ArrayList<List<MdTableCell>>()
        var child = parent.firstChild
        while (child != null) {
            if (child is TableRow) {
                val cells = ArrayList<MdTableCell>()
                var cell = child.firstChild
                while (cell != null) {
                    if (cell is TableCell) cells.add(MdTableCell(inlinesOf(cell), cell.alignment.toMd()))
                    cell = cell.next
                }
                rows.add(cells)
            }
            child = child.next
        }
        return rows
    }

    private fun TableCell.Alignment?.toMd(): MdAlign = when (this) {
        TableCell.Alignment.LEFT -> MdAlign.LEFT
        TableCell.Alignment.CENTER -> MdAlign.CENTER
        TableCell.Alignment.RIGHT -> MdAlign.RIGHT
        else -> MdAlign.NONE
    }

    /** 段落里只有一个图片时，升级为块级图片，获得更好的排版与缓存。 */
    private fun imageBlockOf(paragraph: Paragraph): MdBlock? {
        var image: Image? = null
        var child = paragraph.firstChild
        while (child != null) {
            when (child) {
                is Image -> {
                    if (image != null) return null
                    image = child
                }
                is SoftLineBreak, is HardLineBreak -> Unit
                is Text -> if (child.literal.isNotBlank()) return null
                else -> return null
            }
            child = child.next
        }
        val img = image ?: return null
        return MdBlock.Image(img.destination.orEmpty(), inlinesOf(img).plainText())
    }

    private fun inlinesOf(parent: Node): List<MdInline> {
        val out = ArrayList<MdInline>()
        appendInlines(parent, out)
        return out
    }

    private fun appendInlines(parent: Node, out: MutableList<MdInline>) {
        var child = parent.firstChild
        while (child != null) {
            when (child) {
                is Text -> appendTextWithMath(child.literal, out)
                is Code -> out.add(MdInline.Code(child.literal))
                is Emphasis -> out.add(MdInline.Emph(inlinesOf(child)))
                is StrongEmphasis -> out.add(MdInline.Strong(inlinesOf(child)))
                is Link -> out.add(MdInline.Link(child.destination.orEmpty(), inlinesOf(child)))
                is Image -> out.add(MdInline.InlineImage(child.destination.orEmpty(), inlinesOf(child).plainText()))
                is SoftLineBreak -> out.add(MdInline.Text(" "))
                is HardLineBreak -> out.add(MdInline.Break)
                is HtmlInline -> out.add(MdInline.Text(child.literal))
                else -> appendInlines(child, out)
            }
            child = child.next
        }
    }

    /**
     * 段落里的 $...$ 行内公式。
     * 只认「单个 $ + 非空内容 + 单个 $」，且要求等宽配对，避免把货币符号误判成公式。
     */
    private fun appendTextWithMath(literal: String, out: MutableList<MdInline>) {
        if (!literal.contains('$')) {
            out.add(MdInline.Text(literal))
            return
        }
        var cursor = 0
        while (cursor < literal.length) {
            val open = literal.indexOf('$', cursor)
            if (open < 0) {
                out.add(MdInline.Text(literal.substring(cursor)))
                break
            }
            if (open > cursor) out.add(MdInline.Text(literal.substring(cursor, open)))
            // $$ 已在上层处理，这里遇到就原样保留
            if (open + 1 < literal.length && literal[open + 1] == '$') {
                out.add(MdInline.Text("$"))
                cursor = open + 1
                continue
            }
            var close = -1
            var scan = open + 1
            while (scan < literal.length) {
                if (literal[scan] == '\\') { scan += 2; continue }
                if (literal[scan] == '$') { close = scan; break }
                scan++
            }
            if (close < 0 || close == open + 1) {
                out.add(MdInline.Text(literal.substring(open, minOf(open + 1, literal.length))))
                cursor = open + 1
                continue
            }
            out.add(MdInline.InlineMath(literal.substring(open + 1, close)))
            cursor = close + 1
        }
    }

    companion object {
        val shared = MdParser()
    }
}

fun List<MdInline>.plainText(): String = buildString { appendPlain(this@plainText) }

private fun StringBuilder.appendPlain(items: List<MdInline>) {
    items.forEach { item ->
        when (item) {
            is MdInline.Text -> append(item.text)
            is MdInline.Code -> append(item.text)
            is MdInline.Emph -> appendPlain(item.children)
            is MdInline.Strong -> appendPlain(item.children)
            is MdInline.Link -> appendPlain(item.children)
            is MdInline.InlineImage -> append(item.alt)
            is MdInline.InlineMath -> append(item.latex)
            MdInline.Break -> append(' ')
        }
    }
}

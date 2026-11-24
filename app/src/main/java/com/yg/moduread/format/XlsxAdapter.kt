package com.yg.moduread.format

import com.yg.moduread.md.MdAlign
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdInline
import com.yg.moduread.md.MdTableCell
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * XLSX 解析：读 sheet 的单元格，转成表格块。
 *
 * 做法：
 *  1. 读 xl/sharedStrings.xml —— 文本型单元格的值都在这里，sheet 里只存索引
 *  2. 读 xl/worksheets/sheet*.xml 的 row/c，按 r 属性定位列
 *  3. 每个 sheet 产出一个 Heading + 若干 Table（超过 TABLE_ROWS 行就拆块）
 *
 * 不处理样式与合并单元格 —— 目标是「能读数据」，不是还原版式。
 */
object XlsxAdapter {

    /** 单表块的最大行数。太长的表拆成多个块，避免一次布局上千行。 */
    private const val TABLE_ROWS = 200

    fun parse(bytes: ByteArray): ParsedDoc {
        val zip = ContainerSupport.readZip(bytes)
        val shared = readSharedStrings(zip)
        val blocks = ArrayList<MdBlock>()

        val sheets = zip.names()
            .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .sorted()

        if (sheets.isEmpty()) {
            blocks.add(MdBlock.Para(listOf(MdInline.Text("XLSX 解析失败：未找到工作表"))))
            return ParsedDoc(blocks, "")
        }

        for (sheetPath in sheets) {
            val xml = zip.text(sheetPath) ?: continue
            val root = ContainerSupport.parseXml(xml) ?: continue
            val name = sheetPath.substringAfterLast("/").substringBeforeLast(".")
            blocks.add(MdBlock.Heading(2, listOf(MdInline.Text(name))))
            blocks.addAll(readSheet(root, shared))
        }

        return ParsedDoc(blocks, ContainerAdapters.plainOf(blocks))
    }

    /** sharedStrings.xml 里每个 si 是一个字符串，按出现顺序即索引。 */
    private fun readSharedStrings(zip: ContainerSupport.ZipBundle): List<String> {
        val xml = zip.text("xl/sharedStrings.xml") ?: return emptyList()
        val root = ContainerSupport.parseXml(xml) ?: return emptyList()
        val out = ArrayList<String>()
        val sis = root.getElementsByTagName("si")
        for (i in 0 until sis.length) {
            val el = sis.item(i) as? Element ?: continue
            val ts = el.getElementsByTagName("t")
            val sb = StringBuilder()
            for (j in 0 until ts.length) sb.append(ts.item(j).textContent ?: "")
            out.add(sb.toString())
        }
        return out
    }

    private fun readSheet(root: Element, shared: List<String>): List<MdBlock> {
        val rows = ArrayList<List<String>>()
        val trs = root.getElementsByTagName("row")
        for (i in 0 until trs.length) {
            val rowEl = trs.item(i) as? Element ?: continue
            // 按列号放置单元格，而不是简单 append：
            //  - XLSX 会省略空单元格，直接 append 会让后续列整体左移（行列错位）
            //  - 必须用 r 属性（如 "C5"）解出真实列号
            // 用稀疏映射收集，最后按最大列号展开成完整行。
            val byColumn = HashMap<Int, String>()
            var maxCol = -1
            val cs = directChildren(rowEl, "c")
            for (c in cs) {
                val colIndex = columnIndex(c.getAttribute("r"))
                byColumn[colIndex] = cellValue(c, shared)
                if (colIndex > maxCol) maxCol = colIndex
            }
            if (maxCol < 0) continue
            val cells = ArrayList<String>(maxCol + 1)
            for (k in 0..maxCol) cells.add(byColumn[k] ?: "")
            if (cells.any { it.isNotEmpty() }) rows.add(cells)
        }
        if (rows.isEmpty()) {
            return listOf(MdBlock.Para(listOf(MdInline.Text("（空表）"))))
        }
        val width = rows.maxOf { it.size }.coerceAtLeast(1)

        val out = ArrayList<MdBlock>()
        val header = rows.first()
        var start = 1
        while (start < rows.size) {
            val end = (start + TABLE_ROWS).coerceAtMost(rows.size)
            val chunk = rows.subList(start, end)
            out.add(
                MdBlock.Table(
                    head = toCells(header, width),
                    rows = chunk.map { toCells(it, width) }
                )
            )
            start = end
        }
        if (out.isEmpty()) out.add(MdBlock.Table(toCells(header, width), emptyList()))
        return out
    }

    /**
     * 取直接子元素（按标签名，忽略命名空间前缀）。
     *
     * 不能用 getElementsByTagName：它会递归到所有后代，
     * 嵌套结构里同名元素会被重复计入，导致行列错位。
     */
    private fun directChildren(parent: Element, tag: String): List<Element> {
        val out = ArrayList<Element>()
        val kids = parent.childNodes
        for (i in 0 until kids.length) {
            val n = kids.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val e = n as Element
            val local = e.tagName.lowercase().substringAfter(':')
            if (local == tag) out.add(e)
        }
        return out
    }

    private fun toCells(row: List<String>, width: Int): List<MdTableCell> =
        (0 until width).map { i ->
            MdTableCell(listOf(MdInline.Text(row.getOrElse(i) { "" })), MdAlign.NONE)
        }

    /** 单元格地址 -> 列下标。A1 -> 0，B1 -> 1，AA1 -> 26。 */
    fun columnIndex(ref: String): Int {
        var sum = 0
        for (ch in ref) {
            if (ch < 'A' || ch > 'Z') break
            sum = sum * 26 + (ch - 'A' + 1)
        }
        return (sum - 1).coerceAtLeast(0)
    }

    /** 单元格值：t="s" 是共享字符串索引，否则直接读 v。 */
    private fun cellValue(c: Element, shared: List<String>): String {
        val type = c.getAttribute("t")
        val vs = c.getElementsByTagName("v")
        val raw = if (vs.length > 0) vs.item(0).textContent ?: "" else ""
        if (type == "s") {
            val idx = raw.trim().toIntOrNull() ?: return ""
            return shared.getOrElse(idx) { "" }
        }
        if (type == "inlineStr") {
            val isEls = c.getElementsByTagName("t")
            return if (isEls.length > 0) isEls.item(0).textContent ?: "" else raw
        }
        return raw
    }
}

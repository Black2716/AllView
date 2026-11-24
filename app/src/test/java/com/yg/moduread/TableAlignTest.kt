package com.yg.moduread

import com.yg.moduread.format.ContainerAdapters
import com.yg.moduread.format.XlsxAdapter
import com.yg.moduread.md.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 表格行列对齐的回归测试。
 *
 * 用户反馈：xlsx 与 docx 但凡涉及表格，行列怎么都对不齐。
 * 三个根因：XLSX 省略空单元格导致左移、DOCX 嵌套元素被重复计入、
 * 渲染层每列按自身内容独立撑宽。
 */
class TableAlignTest {

    private fun zipOf(m: Map<String, String>): ByteArray {
        val o = ByteArrayOutputStream()
        ZipOutputStream(o).use { z ->
            for ((n, c) in m) { z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() }
        }
        return o.toByteArray()
    }

    // ── XLSX：省略的空单元格不能让后续列左移 ───────────────

    @Test
    fun xlsxSkippedCellsKeepColumnPosition() {
        // 第 1 行只有 A 与 C（B 被 XLSX 省略）；C 必须落在第 3 列
        val sheet = "<?xml version=\"1.0\"?><worksheet><sheetData>" +
            "<row r=\"1\"><c r=\"A1\"><v>1</v></c><c r=\"C1\"><v>3</v></c></row>" +
            "<row r=\"2\"><c r=\"A2\"><v>4</v></c><c r=\"B2\"><v>5</v></c><c r=\"C2\"><v>6</v></c></row>" +
            "</sheetData></worksheet>"
        val xlsx = zipOf(mapOf("xl/worksheets/sheet1.xml" to sheet))
        val doc = XlsxAdapter.parse(xlsx)
        val table = doc.blocks.filterIsInstance<MdBlock.Table>().first()
        // 表头应有 3 列；第 2 列为空占位
        assertEquals(3, table.head.size)
        assertEquals(3, table.rows.first().size)
    }

    @Test
    fun xlsxAllRowsHaveSameColumnCount() {
        val sheet = "<?xml version=\"1.0\"?><worksheet><sheetData>" +
            "<row r=\"1\"><c r=\"A1\"><v>1</v></c></row>" +
            "<row r=\"2\"><c r=\"A2\"><v>1</v></c><c r=\"B2\"><v>2</v></c><c r=\"C2\"><v>3</v></c></row>" +
            "</sheetData></worksheet>"
        val xlsx = zipOf(mapOf("xl/worksheets/sheet1.xml" to sheet))
        val table = XlsxAdapter.parse(xlsx).blocks.filterIsInstance<MdBlock.Table>().first()
        val allWidths = (listOf(table.head) + table.rows).map { it.size }.distinct()
        assertEquals("所有行必须等宽，否则行列对不齐", 1, allWidths.size)
    }

    // ── DOCX：嵌套表格不能污染外层 ──────────────────────────

    @Test
    fun docxNestedTableDoesNotDuplicateOuterRows() {
        val body = "<w:tbl>" +
            "<w:tr><w:tc><w:p><w:r><w:t>A1</w:t></w:r></w:p></w:tc>" +
            "<w:tc><w:p><w:r><w:t>B1</w:t></w:r></w:p></w:tc></w:tr>" +
            "<w:tr><w:tc><w:p><w:r><w:t>A2</w:t></w:r></w:p>" +
            // 单元格内嵌一个小表：它的 w:tr / w:tc 不能算进外层
            "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>inner</w:t></w:r></w:p></w:tc></w:tr></w:tbl>" +
            "</w:tc>" +
            "<w:tc><w:p><w:r><w:t>B2</w:t></w:r></w:p></w:tc></w:tr>" +
            "</w:tbl>"
        val docx = zipOf(mapOf(
            "[Content_Types].xml" to "<?xml version=\"1.0\"?><Types/>",
            "word/document.xml" to "<?xml version=\"1.0\"?><w:document><w:body>" + body + "</w:body></w:document>"
        ))
        val table = ContainerAdapters.docx(docx).blocks.filterIsInstance<MdBlock.Table>().first()
        // 外层只应有 2 行、2 列；嵌套表不应把行数翻倍
        assertEquals("外层表应是 2 行", 2, table.rows.size + 1)
        assertEquals("外层表应是 2 列", 2, table.head.size)
    }

    @Test
    fun docxGridSpanAddsPlaceholders() {
        // 第 1 行：一个跨 2 列的单元格；第 2 行：两个普通单元格
        val body = "<w:tbl>" +
            "<w:tr><w:tc><w:tcPr><w:gridSpan w:val=\"2\"/></w:tcPr><w:p><w:r><w:t>merged</w:t></w:r></w:p></w:tc></w:tr>" +
            "<w:tr><w:tc><w:p><w:r><w:t>x</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>y</w:t></w:r></w:p></w:tc></w:tr>" +
            "</w:tbl>"
        val docx = zipOf(mapOf(
            "[Content_Types].xml" to "<?xml version=\"1.0\"?><Types/>",
            "word/document.xml" to "<?xml version=\"1.0\"?><w:document><w:body>" + body + "</w:body></w:document>"
        ))
        val table = ContainerAdapters.docx(docx).blocks.filterIsInstance<MdBlock.Table>().first()
        val widths = (listOf(table.head) + table.rows).map { it.size }.distinct()
        assertEquals("合并单元格必须补占位，使各行等宽", 1, widths.size)
        assertEquals(2, widths.first())
    }
}

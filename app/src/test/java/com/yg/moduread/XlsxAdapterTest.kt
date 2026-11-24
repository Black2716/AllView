package com.yg.moduread

import com.yg.moduread.format.XlsxAdapter
import com.yg.moduread.md.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 测试内现场生成最小 xlsx，仓库不放二进制 fixture。 */
class XlsxAdapterTest {

    private fun zipOf(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun simpleXlsx(): ByteArray = zipOf(
        mapOf(
            "xl/sharedStrings.xml" to
                "<?xml version='1.0'?><sst><si><t>Name</t></si><si><t>Score</t></si>" +
                "<si><t>Alice</t></si><si><t>Bob</t></si></sst>",
            "xl/worksheets/sheet1.xml" to
                "<?xml version='1.0'?><worksheet><sheetData>" +
                "<row r='1'><c r='A1' t='s'><v>0</v></c><c r='B1' t='s'><v>1</v></c></row>" +
                "<row r='2'><c r='A2' t='s'><v>2</v></c><c r='B2'><v>92</v></c></row>" +
                "<row r='3'><c r='A3' t='s'><v>3</v></c><c r='B3'><v>78</v></c></row>" +
                "</sheetData></worksheet>"
        )
    )

    @Test
    fun columnIndexMapsLettersToZeroBased() {
        assertEquals(0, XlsxAdapter.columnIndex("A1"))
        assertEquals(1, XlsxAdapter.columnIndex("B2"))
        assertEquals(25, XlsxAdapter.columnIndex("Z1"))
        assertEquals(26, XlsxAdapter.columnIndex("AA1"))
    }

    @Test
    fun parsesSheetIntoTable() {
        val doc = XlsxAdapter.parse(simpleXlsx())
        val table = doc.blocks.filterIsInstance<MdBlock.Table>().first()
        assertEquals(2, table.head.size)
        assertEquals(2, table.rows.size)
    }

    @Test
    fun sharedStringsAreResolved() {
        val doc = XlsxAdapter.parse(simpleXlsx())
        // 文本应来自 sharedStrings，而不是索引数字
        assertTrue("应包含 Alice，实际：" + doc.plainText, doc.plainText.contains("Alice"))
        assertTrue(doc.plainText.contains("92"))
    }

    @Test
    fun sheetGetsHeading() {
        val doc = XlsxAdapter.parse(simpleXlsx())
        assertTrue(doc.blocks.any { it is MdBlock.Heading })
    }

    @Test
    fun missingSheetDegrades() {
        val doc = XlsxAdapter.parse(zipOf(mapOf("x.txt" to "y")))
        assertTrue(doc.blocks.isNotEmpty())
    }
}

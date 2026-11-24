package com.yg.moduread.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.yg.moduread.format.ContainerAdapters
import com.yg.moduread.format.DocFormat
import com.yg.moduread.format.ParsedDoc
import com.yg.moduread.format.PdfAdapter
import com.yg.moduread.format.TextAdapters
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LoadedDoc(
    val title: String,
    val blocks: List<MdBlock>,
    val charCount: Int,
    val baseDir: String?,
    val docUri: String?,
    /** 命中的格式。界面据此决定隐藏搜索、显示限制提示等。 */
    val format: DocFormat = DocFormat.MARKDOWN,
    /** PDF 页数；非 PDF 为 0。 */
    val pageCount: Int = 0,
    /** 是否加密（仅 PDF 有意义）。 */
    val encrypted: Boolean = false,
    /** 文字层是否可用。PDF 扫描件为 false，搜索应提示不可用。 */
    val hasTextLayer: Boolean = true
)

/**
 * 读文件 + 按格式解析成统一的 List<MdBlock>。
 *
 * 路由依据：先用 SAF 的 DISPLAY_NAME 判扩展名（最准），取不到再问 MIME。
 * 不能靠 URI 判 —— SAF 给的是 content://...msf%3A1000000026 这种，没有扩展名。
 */
object DocRepository {

    suspend fun load(context: Context, uri: Uri): LoadedDoc = withContext(Dispatchers.IO) {
        val name = queryName(context, uri) ?: uri.lastPathSegment ?: "未命名.md"
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val format = DocFormat.detect(name, mime)

        val raw = context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
            ?: throw IllegalStateException("无法打开该文件")

        val baseDir = if (uri.scheme == "file") uri.path?.substringBeforeLast('/') else null
        val title = name.substringAfterLast('/')

        if (format == DocFormat.PDF) {
            val info = runCatching { PdfAdapter.inspect(raw) }
                .getOrDefault(PdfAdapter.PdfResult(1, emptyList(), listOf(""), false))
            val parsed = PdfAdapter.parse(raw)
            return@withContext LoadedDoc(
                title = title,
                blocks = parsed.blocks,
                charCount = parsed.plainText.length,
                baseDir = baseDir,
                docUri = uri.toString(),
                format = format,
                pageCount = info.pageCount,
                encrypted = info.encrypted,
                hasTextLayer = info.hasTextLayer
            )
        }

        val parsed: ParsedDoc = runCatching { parseByFormat(format, raw) }
            .getOrElse { e ->
                // 解析失败时降级为纯文本，保证"至少能读"
                ParsedDoc(TextAdapters.txt(safeDecode(raw)).blocks, safeDecode(raw))
            }

        LoadedDoc(
            title = title,
            blocks = parsed.blocks,
            charCount = parsed.plainText.length,
            baseDir = baseDir,
            docUri = uri.toString(),
            format = format,
            hasTextLayer = true
        )
    }

    /** 按格式分派。MARKDOWN 走原有 commonmark 链路，其余走新增适配器。 */
    private fun parseByFormat(format: DocFormat, raw: ByteArray): ParsedDoc = when (format) {
        DocFormat.MARKDOWN -> {
            val text = safeDecode(raw)
            ParsedDoc(MdParser.shared.parse(text), text)
        }
        DocFormat.TXT -> TextAdapters.txt(safeDecode(raw))
        DocFormat.LOG -> TextAdapters.log(safeDecode(raw))
        DocFormat.CSV -> {
            val text = safeDecode(raw)
            val delimiter = if (text.lineSequence().firstOrNull()?.contains('	') == true) '	' else ','
            TextAdapters.csv(text, delimiter)
        }
        DocFormat.JSON -> TextAdapters.json(safeDecode(raw))
        DocFormat.YAML -> TextAdapters.yaml(safeDecode(raw))
        DocFormat.INI -> TextAdapters.ini(safeDecode(raw))
        DocFormat.SUBTITLE -> TextAdapters.subtitle(safeDecode(raw))
        DocFormat.RTF -> TextAdapters.rtf(safeDecode(raw))
        DocFormat.XML, DocFormat.HTML -> ContainerAdapters.html(safeDecode(raw))
        DocFormat.EPUB -> ContainerAdapters.epub(raw)
        DocFormat.DOCX -> ContainerAdapters.docx(raw)
        DocFormat.ODT -> ContainerAdapters.odt(raw)
        DocFormat.PDF -> ParsedDoc(emptyList(), "")
    }

    /** UTF-8 优先；失败退回 GB18030（中文 TXT 常见），再退回 ISO-8859-1。 */
    fun safeDecode(raw: ByteArray): String {
        val utf8 = runCatching { String(raw, Charsets.UTF_8) }.getOrNull()
        if (utf8 != null && !utf8.contains('�')) return utf8.removePrefix("﻿")
        val gb = runCatching { String(raw, charset("GB18030")) }.getOrNull()
        if (gb != null && !gb.contains('�')) return gb.removePrefix("﻿")
        return String(raw, Charsets.ISO_8859_1).removePrefix("﻿")
    }

    private fun queryName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
}

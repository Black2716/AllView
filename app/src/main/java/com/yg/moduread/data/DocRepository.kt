package com.yg.moduread.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.yg.moduread.format.ContainerAdapters
import com.yg.moduread.format.DocFormat
import com.yg.moduread.format.ParsedDoc
import com.yg.moduread.format.PdfAdapter
import com.yg.moduread.format.TextAdapters
import com.yg.moduread.format.PptxAdapter
import com.yg.moduread.format.XlsxAdapter
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdInline
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
    val hasTextLayer: Boolean = true,
    /** PDF 目录（层级, 标题）。只用于目录抽屉，不插进正文。 */
    val outline: List<Triple<Int, Int, String>> = emptyList(),
    /** PPTX 的逐页 SVG（HTML）。非空时界面按 16:9 逐页显示。 */
    val slides: com.yg.moduread.format.SlideDeck? = null
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
        val title = sanitizeTitle(name.substringAfterLast('/'))

        if (format == DocFormat.PDF) {
            // 打开文档时不提取全文：这一步要解压所有流 + 全文正则，
            // 大 PDF（图片多）会卡好几秒（用户实测「一直转圈」）。
            // 页数与目录已足够渲染阅读页；文字层留给搜索按需处理。
            val info = runCatching { PdfAdapter.inspect(raw, withText = false) }
                .getOrDefault(PdfAdapter.PdfResult(1, emptyList(), listOf(""), false))
            // 页数与每页尺寸以 PdfRenderer 为准：它是唯一权威来源。
            // 正则数 /Type /Page 会把对象流、注释里的字符串也算进去，
            // 多出来的页 openPage 就越界（用户实测的 "Invalid page index"）。
            // 不再同步构造 PdfRenderer 读页尺寸：
            // 它要解析整个文件结构，15MB 文档在设备上要几十秒
            // （用户实测「一直转圈 2 分钟，加载出来才能滑动」）。
            // 先用正则估页数，尺寸交给渲染层按屏幕等比缩放。
            // 直接用 inspect 已经算好的页数。
            // 之前这里又调了一次 estimatePages(raw)，它会重新创建
            // 15MB 的字符串并跑一遍全文正则 —— 与 inspect 完全重复，
            // 白等一倍时间（这就是「转圈一分钟」的一半来源）。
            val pageCountEstimate = info.pageCount
            val sizes: List<Pair<Int, Int>>? = null
            val parsed = runCatching { PdfAdapter.parse(raw, sizes, info) }
                .getOrElse { PdfAdapter.parse(raw, known = info) }
            return@withContext LoadedDoc(
                title = title,
                blocks = parsed.blocks,
                charCount = parsed.plainText.length,
                baseDir = baseDir,
                docUri = uri.toString(),
                format = format,
                pageCount = sizes?.size ?: pageCountEstimate,
                encrypted = info.encrypted,
                hasTextLayer = info.hasTextLayer,
                outline = info.outline
            )
        }

        // 不支持/旧版二进制格式必须提前返回：它们绝不能走下面的「降级为纯文本」，
        // 那会把二进制硬当 UTF-8 读，正是 .mobi 闪退的原因。
        if (format == DocFormat.UNSUPPORTED || format == DocFormat.DOC_LEGACY) {
            val parsed = parseByFormat(format, raw, name)
            return@withContext LoadedDoc(
                title = title,
                blocks = parsed.blocks,
                charCount = parsed.plainText.length,
                baseDir = baseDir,
                docUri = uri.toString(),
                format = format,
                hasTextLayer = false
            )
        }

        val parsed: ParsedDoc = runCatching { parseByFormat(format, raw, name) }
            .getOrElse { e ->
                // 解析失败时降级为纯文本，保证「至少能读」。
                // 仅用于文本系格式 —— 二进制格式已在上面提前返回。
                ParsedDoc(TextAdapters.txt(safeDecode(raw)).blocks, safeDecode(raw))
            }

        LoadedDoc(
            title = title,
            blocks = parsed.blocks,
            charCount = parsed.plainText.length,
            baseDir = baseDir,
            docUri = uri.toString(),
            format = format,
            hasTextLayer = true,
            // PPTX 额外生成逐页 SVG：界面据此按 16:9 逐页显示。
            // 生成失败返回 null，界面自动降级为文本渲染。
            slides = if (format == DocFormat.PPTX) {
                val dr = runCatching { PptxAdapter.toDeck(raw) }
                android.util.Log.i("AllViewPptx", "html0=" +
                    (dr.getOrNull()?.pages?.firstOrNull()?.take(400) ?: "null"))
                android.util.Log.i("AllViewPptx", "deck=" +
                    (dr.getOrNull()?.pages?.size ?: -1) + " err=" + dr.exceptionOrNull())
                dr.getOrNull()
            } else null
        )
    }

    /** 按格式分派。MARKDOWN 走原有 commonmark 链路，其余走新增适配器。 */
    private fun parseByFormat(format: DocFormat, raw: ByteArray, fileName: String? = null): ParsedDoc = when (format) {
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
        DocFormat.CODE -> TextAdapters.source(safeDecode(raw), fileName)
        DocFormat.XLSX -> XlsxAdapter.parse(raw)
        DocFormat.PPTX -> PptxAdapter.parse(raw)
        DocFormat.EPUB -> ContainerAdapters.epub(raw)
        DocFormat.DOCX -> ContainerAdapters.docx(raw)
        DocFormat.ODT -> ContainerAdapters.odt(raw)
        DocFormat.PDF -> ParsedDoc(emptyList(), "")
        DocFormat.DOC_LEGACY -> {
            // 不要尝试按文本解析：.doc 是二进制，读出来就是乱码。
            // 明确告诉用户该怎么办，比给一堆乱码有用。
            val hint = """
                这个文件是 Office 97-2003 的旧格式（.doc），不是 .docx。

                它是二进制复合文档，与 .docx 的 ZIP+XML 结构完全不同，当前版本没有解析它。

                解决办法：用 Word / WPS 打开后「另存为 .docx」，再用 AllView 打开即可。
            """.trimIndent()
            ParsedDoc(listOf(MdBlock.Para(listOf(MdInline.Text(hint)))), hint)
        }
        DocFormat.UNSUPPORTED -> {
            // 关键：绝不能按文本读。之前兜底到 MARKDOWN，
            // 二进制文件（.mobi/.azw3/.djvu 等）会被当成 UTF-8 硬读，
            // 解析器吞下海量无效内容后直接崩溃（用户实测 .mobi 闪退）。
            val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
            val shown = if (ext.isNotBlank()) "." + ext else "该格式"
            val hint = """
                AllView 暂不支持 " + shown + " 格式。

                当前支持：Markdown、TXT、LOG、CSV、JSON、YAML、INI、SRT/VTT、RTF、
                HTML、EPUB、DOCX、ODT、XLSX、PDF，以及常见源码文件。

                如果是 Office 97-2003 的 .doc，请用 Word / WPS 另存为 .docx 后再打开。
            """.trimIndent()
            ParsedDoc(listOf(MdBlock.Para(listOf(MdInline.Text(hint)))), hint)
        }
    }

    /** UTF-8 优先；失败退回 GB18030（中文 TXT 常见），再退回 ISO-8859-1。 */
    fun safeDecode(raw: ByteArray): String {
        val utf8 = runCatching { String(raw, Charsets.UTF_8) }.getOrNull()
        if (utf8 != null && !utf8.contains('�')) return utf8.removePrefix("﻿")
        val gb = runCatching { String(raw, charset("GB18030")) }.getOrNull()
        if (gb != null && !gb.contains('�')) return gb.removePrefix("﻿")
        return String(raw, Charsets.ISO_8859_1).removePrefix("﻿")
    }

    /**
     * 清理文件名里的乱码字符。
     *
     * 用户实测有些 PDF 的顶栏标题是乱码 —— 那不是解析问题，
     * 而是 SAF 返回的 displayName 本身带替换字符（U+FFFD）或控制字符。
     * 这里把不可见/替换字符去掉；全被清空就退回占位名。
     */
    fun sanitizeTitle(raw: String): String {
        val cleaned = raw.filter { ch ->
            // 去掉替换字符、控制字符、以及零宽字符
            ch != '\uFFFD' && !ch.isISOControl() &&
                ch != '\u200B' && ch != '\u200C' && ch != '\u200D' && ch != '\uFEFF'
        }.trim()
        return cleaned.ifBlank { "未命名文档" }
    }

    private fun queryName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
}

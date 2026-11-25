package com.yg.moduread.format

/** 支持的文档格式。 */
enum class DocFormat {
    MARKDOWN,
    TXT,
    /** 日志：与 TXT 同解析，但按行呈现、不合并空行。 */
    LOG,
    /** CSV / TSV：解析成表格块。 */
    CSV,
    /** JSON：解析成缩进的结构化文本。 */
    JSON,
    /** XML / XHTML 单文件：按标签转块。 */
    XML,
    YAML,
    /** INI / CONF：按节分段。 */
    INI,
    /** SRT / VTT 字幕：按时间轴分块。 */
    SUBTITLE,
    RTF,
    EPUB,
    DOCX,
    ODT,
    HTML,
    /** 源码文件：按扩展名挑语言做语法高亮。 */
    /** Excel 表格：读 sharedStrings + sheet XML 转成表格块。 */
    XLSX,
    /** PowerPoint（.pptx），ZIP+XML 容器。 */
    PPTX,
    /** Office 97-2003 二进制格式，暂不支持解析。 */
    DOC_LEGACY,
    /**
     * 未知/不支持的格式。
     *
     * 以前 detect() 会兜底成 MARKDOWN —— 后果是任何二进制文件（如 .mobi）
     * 都被按 UTF-8 当文本读，解析器吞下海量无效内容后直接崩。
     * 现在兜底到这里，只显示一句明确提示。
     */
    UNSUPPORTED,
    CODE,
    PDF;

    /** 是否由 ZIP + XML 容器解析（需要读包内图片）。 */
    val isContainer: Boolean
        get() = this == EPUB || this == DOCX || this == ODT || this == XLSX || this == PPTX

    /** 是否支持全文搜索（PDF 走文字层提取，可能部分失败）。 */
    val supportsSearch: Boolean
        get() = this != PDF

    companion object {

        /**
         * 扩展名 -> 格式。接受带点与不带点，大小写不敏感。
         * 返回 null 表示不支持；路由层据此给出明确提示而不是静默失败。
         */
        fun ofExtension(name: String?): DocFormat? {
            if (name.isNullOrBlank()) return null
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "md", "markdown", "mdown", "mkd" -> MARKDOWN
                "txt", "text" -> TXT
                "log" -> LOG
                "csv", "tsv" -> CSV
                "json" -> JSON
                "xml", "xhtml" -> XML
                "yaml", "yml" -> YAML
                "ini", "conf", "cfg", "properties" -> INI
                "srt", "vtt" -> SUBTITLE
                "rtf" -> RTF
                "epub" -> EPUB
                "docx" -> DOCX
                // .doc 是 97-2003 的二进制复合文档，与 .docx（ZIP+XML）完全不同。
                // 之前没有这个分支，会回退到 MARKDOWN 按文本读二进制 —— 界面直接是乱码。
                // 这里单独标记，交给上层给出明确提示。
                "doc" -> DOC_LEGACY
                "odt" -> ODT
                "html", "htm" -> HTML
                "kt", "kts", "java", "py", "js", "ts", "jsx", "tsx",
                "c", "h", "cpp", "cc", "hpp", "cs", "go", "rs",
                "rb", "php", "swift", "m", "scala", "lua", "pl", "r",
                "sql", "sh", "bash", "zsh", "ps1", "bat", "cmd", "gradle",
                "groovy", "css", "scss", "less", "vue", "toml" -> CODE
                "xlsx", "xlsm" -> XLSX
                // .pptx 同样是 ZIP+XML：每张幻灯片一个 slideN.xml，文本在 a:t 里。
                // .ppt（97-2003）是二进制复合文档，与 .doc 同类，暂不支持。
                "pptx" -> PPTX
                "ppt" -> DOC_LEGACY
                "pdf" -> PDF
                else -> null
            }
        }

        /** MIME -> 格式。用于扩展名缺失时兜底（SAF 有时只给 MIME）。 */
        fun ofMime(mime: String?): DocFormat? {
            if (mime.isNullOrBlank()) return null
            return when (mime.lowercase().substringBefore(';').trim()) {
                "text/markdown", "text/x-markdown" -> MARKDOWN
                "text/plain" -> TXT
                "text/csv", "text/tab-separated-values" -> CSV
                "application/json" -> JSON
                "application/xml", "text/xml" -> XML
                "application/yaml", "text/yaml", "application/x-yaml" -> YAML
                "application/x-subrip", "text/vtt" -> SUBTITLE
                "application/rtf", "text/rtf" -> RTF
                "application/epub+zip" -> EPUB
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DOCX
                "application/vnd.oasis.opendocument.text" -> ODT
                "text/html" -> HTML
                "application/javascript", "text/javascript", "application/x-sh" -> CODE
                "application/pdf" -> PDF
                else -> null
            }
        }

        /**
         * 综合判定：先看扩展名（最准），再看 MIME。
         * 两者都没有时按 MARKDOWN 处理 —— 老版本行为，保证不回归。
         */
        fun detect(name: String?, mime: String?): DocFormat =
            ofExtension(name) ?: ofMime(mime) ?: UNSUPPORTED

        /** 供 SAF 文件选择器使用：把所有支持的 MIME 都声明上。 */
        val pickerMimeTypes: Array<String> = arrayOf(
            "text/markdown", "text/x-markdown", "text/plain", "text/html",
            "text/csv", "text/tab-separated-values", "application/json",
            "application/xml", "text/xml", "application/yaml", "text/yaml",
            "application/x-subrip", "text/vtt", "application/rtf", "text/rtf",
            "application/epub+zip",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.oasis.opendocument.text",
            "application/pdf",
            "application/javascript", "text/javascript", "application/x-sh",
            "application/octet-stream"
        )
    }
}

/** 解析结果：块列表 + 纯文字（供字数统计与搜索）。 */
data class ParsedDoc(
    val blocks: List<com.yg.moduread.md.MdBlock>,
    /** 纯文字内容；为空表示无文字层（PDF 扫描件）。 */
    val plainText: String = ""
)

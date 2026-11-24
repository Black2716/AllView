package com.yg.moduread.ui

import androidx.compose.runtime.Immutable
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.TocEntry

@Immutable
data class DocumentState(
    val title: String = "",
    val blocks: List<MdBlock> = emptyList(),
    val charCount: Int = 0,
    val baseDir: String? = null,
    val docUri: String? = null,
    /** 上次读到的列表位置（0 号是页首标题项）。解析完成后由界面消费一次。 */
    val restoreIndex: Int = 0,
    val restoreOffset: Int = 0,
    /** 目录条目，解析完成时一次性生成。 */
    val toc: List<TocEntry> = emptyList(),
    /** GitHub 风格 slug -> 块索引，供文档内锚点跳转。 */
    val anchors: Map<String, Int> = emptyMap(),
    /** 全文搜索索引，解析完成时建一次。 */
    val searchIndex: com.yg.moduread.md.SearchIndex? = null,
    /** 文档格式。界面据此隐藏搜索入口、显示限制提示。 */
    val format: com.yg.moduread.format.DocFormat = com.yg.moduread.format.DocFormat.MARKDOWN,
    /** PDF 页数；非 PDF 为 0。 */
    val pageCount: Int = 0,
    /** PDF 是否加密（加密文件无目录、无文字层）。 */
    val encrypted: Boolean = false,
    /** 文字层是否可用（扫描件为 false，搜索应提示不可用）。 */
    val hasTextLayer: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null
)

/** 纸张色调。与深/浅无关 —— 米黄在浅色模式下表更暖，夜间模式本身是深色。 */
enum class ReadingTheme { PAPER, SEPIA, NIGHT }

@Immutable
data class ReaderPrefs(
    val fontSizeSp: Float = 17f,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val readingTheme: ReadingTheme = ReadingTheme.PAPER
) {
    companion object {
        const val MIN_FONT = 13f
        const val MAX_FONT = 26f
        const val FONT_STEP = 1f
    }
}

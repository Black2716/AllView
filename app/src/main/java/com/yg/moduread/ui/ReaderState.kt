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
    val loading: Boolean = false,
    val error: String? = null
)

@Immutable
data class ReaderPrefs(
    val fontSizeSp: Float = 17f,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
) {
    companion object {
        const val MIN_FONT = 13f
        const val MAX_FONT = 26f
        const val FONT_STEP = 1f
    }
}

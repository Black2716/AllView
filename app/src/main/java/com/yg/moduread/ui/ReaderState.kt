package com.yg.moduread.ui

import androidx.compose.runtime.Immutable
import com.yg.moduread.md.MdBlock

@Immutable
data class DocumentState(
    val title: String = "",
    val blocks: List<MdBlock> = emptyList(),
    val charCount: Int = 0,
    val baseDir: String? = null,
    val docUri: String? = null,
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

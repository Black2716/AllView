package com.yg.moduread.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yg.moduread.data.DocRepository
import com.yg.moduread.data.RecentFile
import com.yg.moduread.data.RecentFiles
import com.yg.moduread.md.MdToc
import com.yg.moduread.md.SearchIndex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("moduread", Application.MODE_PRIVATE)
    private val recentFiles = RecentFiles(prefs)

    private val _recent = MutableStateFlow(recentFiles.list())
    val recent: StateFlow<List<RecentFile>> = _recent.asStateFlow()

    private val _doc = MutableStateFlow(DocumentState())
    val doc: StateFlow<DocumentState> = _doc.asStateFlow()

    private val _readerPrefs = MutableStateFlow(
        ReaderPrefs(
            fontSizeSp = prefs.getFloat(KEY_FONT, 17f),
            themeMode = runCatching {
                ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!)
            }.getOrDefault(ThemeMode.SYSTEM),
            readingTheme = runCatching {
                ReadingTheme.valueOf(prefs.getString(KEY_READING_THEME, ReadingTheme.PAPER.name)!!)
            }.getOrDefault(ReadingTheme.PAPER)
        )
    )
    val readerPrefs: StateFlow<ReaderPrefs> = _readerPrefs.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    /** 一次性跳转请求：目录点击、锚点点击都往这里塞目标块索引。 */
    private val _jumpTo = MutableStateFlow<Int?>(null)
    val jumpTo: StateFlow<Int?> = _jumpTo.asStateFlow()

    fun requestJump(blockIndex: Int) {
        _jumpTo.value = blockIndex.coerceAtLeast(0)
    }

    fun consumeJump() {
        _jumpTo.value = null
    }

    /** 文档内锚点 -> 块索引；找不到返回 null。 */
    fun resolveAnchor(url: String): Int? {
        val slug = MdToc.anchorOf(url) ?: return null
        return _doc.value.anchors[slug]
    }

    private var restoreStarted = false

    /**
     * 冷启动时由 MainActivity 塞进来的外部打开请求（文件管理器 / 其他 App 的 VIEW）。
     * 界面首帧后再消费，避免和「续读上次文件」抢同一条加载路径。
     */
    var pendingExternalUri: Uri? = null

    /**
     * 续读上次的文件。刻意不在 init 里触发：
     * 首帧只绘制空态骨架，等界面真正显示后再读文件，冷启动路径上没有 IO 与解析。
     */
    fun restoreLastIfNeeded() {
        if (restoreStarted) return
        restoreStarted = true

        // 从外部打开优先于「续读上次文件」：用户明确点了某个文件
        pendingExternalUri?.let { uri ->
            pendingExternalUri = null
            open(uri)
            return
        }

        val last = prefs.getString(KEY_LAST_URI, null) ?: return
        runCatching { Uri.parse(last) }.getOrNull()?.let { open(it, silent = true) }
    }

    fun open(uri: Uri, silent: Boolean = false) {
        _doc.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            runCatching {
                DocRepository.load(getApplication(), uri)
            }.onSuccess { loaded ->
                val saved = readProgress(uri)
                // 目录与锚点表在这里一次算好，界面只管用，不在重组时装算
                val toc = MdToc.build(loaded.blocks)
                _doc.value = DocumentState(
                    title = loaded.title,
                    blocks = loaded.blocks,
                    charCount = loaded.charCount,
                    baseDir = loaded.baseDir,
                    docUri = loaded.docUri,
                    restoreIndex = saved.first,
                    restoreOffset = saved.second,
                    toc = toc,
                    anchors = MdToc.index(toc),
                    searchIndex = SearchIndex.build(loaded.blocks),
                    loading = false
                )
                prefs.edit().putString(KEY_LAST_URI, uri.toString()).apply()
                recentFiles.remember(uri.toString(), loaded.title)
                _recent.value = recentFiles.list()
            }.onFailure { e ->
                _doc.update {
                    it.copy(loading = false, error = if (silent) null else friendlyError(e))
                }
            }
        }
    }

    /**
     * 记录阅读位置。按文档 uri 分开存，换书各自记住各自的位置。
     * 只写 SharedPreferences 的两个整数，不建数据库、不占包体。
     */
    fun saveProgress(index: Int, offset: Int) {
        val uri = _doc.value.docUri ?: return
        val key = progressKey(uri)
        prefs.edit()
            .putInt(KEY_PROGRESS_INDEX + key, index)
            .putInt(KEY_PROGRESS_OFFSET + key, offset)
            .apply()
    }

    private fun readProgress(uri: Uri): Pair<Int, Int> {
        val key = progressKey(uri.toString())
        val index = prefs.getInt(KEY_PROGRESS_INDEX + key, 0)
        val offset = prefs.getInt(KEY_PROGRESS_OFFSET + key, 0)
        return index.coerceAtLeast(0) to offset.coerceAtLeast(0)
    }

    /** uri 可能很长，用 hashCode 做键；碰撞概率对「记住几本书的位置」这个用途无影响。 */
    private fun progressKey(uri: String): String = uri.hashCode().toString()

    /**
     * 打开失败的原因翻译成人话。
     * 系统抛的 SecurityException 是一大段英文加完整 uri，
     * 直接摊在空态页上既看不懂也难看。
     */
    private fun friendlyError(e: Throwable): String {
        val raw = e.message.orEmpty()
        return when {
            e is SecurityException || raw.contains("Permission Denial", true) ||
                raw.contains("requires that you obtain access", true) ->
                "这个文件没有被授权访问。\n点「选择文件」重新选一次即可（从外部打开时若被拒绝，也需要这样授权一次）。"

            e is java.io.FileNotFoundException || raw.contains("No such file", true) ||
                raw.contains("ENOENT", true) ->
                "文件找不到了，可能已被移动或删除。"

            raw.contains("EACCES", true) -> "没有读取该文件的权限。"

            raw.isBlank() -> "打开失败，请重试。"

            // 兜底：只保留第一行，避免长堆栈式文本铺满屏幕
            else -> raw.lineSequence().firstOrNull()?.take(120) ?: "打开失败，请重试。"
        }
    }

    fun forgetRecent(uri: String) {
        recentFiles.forget(uri)
        _recent.value = recentFiles.list()
    }

    fun clearDocument() {
        _doc.value = DocumentState()
        prefs.edit().remove(KEY_LAST_URI).apply()
    }

    fun setFontSize(size: Float) {
        val clamped = size.coerceIn(ReaderPrefs.MIN_FONT, ReaderPrefs.MAX_FONT)
        _readerPrefs.update { it.copy(fontSizeSp = clamped) }
        prefs.edit().putFloat(KEY_FONT, clamped).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _readerPrefs.update { it.copy(themeMode = mode) }
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setReadingTheme(theme: ReadingTheme) {
        _readerPrefs.update { it.copy(readingTheme = theme) }
        prefs.edit().putString(KEY_READING_THEME, theme.name).apply()
    }

    fun setSettingsVisible(visible: Boolean) {
        _showSettings.value = visible
    }

    private companion object {
        const val KEY_FONT = "font_size"
        const val KEY_THEME = "theme_mode"
        const val KEY_READING_THEME = "reading_theme"
        const val KEY_LAST_URI = "last_uri"
        const val KEY_PROGRESS_INDEX = "progress_index_"
        const val KEY_PROGRESS_OFFSET = "progress_offset_"
    }
}

package com.yg.moduread.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yg.moduread.data.DocRepository
import com.yg.moduread.data.Bookmark
import com.yg.moduread.data.Bookmarks
import com.yg.moduread.data.RecentFile
import com.yg.moduread.data.RecentFiles
import com.yg.moduread.md.MdToc
import com.yg.moduread.md.bookmarkExcerpt
import com.yg.moduread.md.SearchIndex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("moduread", Application.MODE_PRIVATE)
    private val recentFiles = RecentFiles(prefs)
    private val bookmarkStore = Bookmarks(prefs)

    private val _recent = MutableStateFlow(recentFiles.list())
    val recent: StateFlow<List<RecentFile>> = _recent.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    /** 当前文档的书签列表。 */
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    // ── 多开：会话列表 + 活动会话 id ──────────────────────────────
    private var nextSessionId = 1L
    /**
     * 待切到的活动文档 uri。
     * 恢复多开时，open() 是异步的 —— 立刻按 docUri 匹配会全是 null，
     * 只能退回「最后一个标签」。所以先记下目标，等对应会话加载完成再切过去。
     */
    private var pendingActiveUri: String? = null
    private val _sessions = MutableStateFlow<List<DocSession>>(emptyList())
    val sessions: StateFlow<List<DocSession>> = _sessions.asStateFlow()

    private val _activeId = MutableStateFlow<Long?>(null)
    val activeId: StateFlow<Long?> = _activeId.asStateFlow()

    /** 活动会话的文档状态。没有会话时返回空态，界面逻辑无需判断 null。 */
    val doc: StateFlow<DocumentState> = combine(_sessions, _activeId) { list, id ->
        list.firstOrNull { it.id == id }?.doc ?: DocumentState()
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, DocumentState())

    /** 活动会话。供需要 scroll 位置的地方取。 */
    private fun activeSession(): DocSession? = _sessions.value.firstOrNull { it.id == _activeId.value }

    private fun updateDoc(transform: (DocumentState) -> DocumentState) {
        val id = _activeId.value ?: return
        _sessions.update { list ->
            list.map { if (it.id == id) it.copy(doc = transform(it.doc)) else it }
        }
    }

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
        return doc.value.anchors[slug]
    }

    private var restoreStarted = false

    /**
     * 冷启动时由 MainActivity 塞进来的外部打开请求（文件管理器 / 其他 App 的 VIEW）。
     * 界面首帧后再消费，避免和「续读上次文件」抢同一条加载路径。
     */
    var pendingExternalUri: Uri? = null

    /**
     * 持久化当前打开的所有文档 uri（顺序即标签顺序）与活动标签，
     * 重启后恢复到上次正在读的那一本，而不是永远回到第一个标签。
     */
    private fun persistSessionUris() {
        val uris = _sessions.value.mapNotNull { it.doc.docUri }
        val active = activeSession()?.doc?.docUri
        prefs.edit()
            .putString(KEY_SESSION_URIS, uris.joinToString("\n"))
            .putString(KEY_ACTIVE_URI, active)
            .apply()
    }

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

        // 新版本优先恢复多开会话；老版本只存了单个 last_uri，走下面的兜底
        val saved = prefs.getString(KEY_SESSION_URIS, null)
        val uris = saved?.split("\n")?.filter { it.isNotBlank() }.orEmpty()
        if (uris.isNotEmpty()) {
            uris.forEach { s ->
                runCatching { Uri.parse(s) }.getOrNull()?.let { open(it, silent = true, newTab = true) }
            }
            // 回到上次活动的那一本。此时各会话都在异步加载，docUri 还没落定，
            // 所以只记下目标，真正切换放到加载成功回调里做。
            pendingActiveUri = prefs.getString(KEY_ACTIVE_URI, null)
            return
        }

        val last = prefs.getString(KEY_LAST_URI, null) ?: return
        runCatching { Uri.parse(last) }.getOrNull()?.let { open(it, silent = true) }
    }

    /**
     * 打开文档。
     *
     * [newTab] 为 false（默认）时，若该 uri 已开过则直接切过去 —— 避免重复标签；
     * 为 true 时强制新开一个标签，用于「同一文件对照阅读」。
     */
    fun open(uri: Uri, silent: Boolean = false, newTab: Boolean = false) {
        val key = uri.toString()
        val existing = _sessions.value.firstOrNull { it.doc.docUri == key && !newTab }
        if (existing != null) {
            _activeId.value = existing.id
            // 复用一个已开的标签也是一次「切换」，同样要记住，
            // 否则重启后会回到上一个活动标签而不是这里
            prefs.edit().putString(KEY_ACTIVE_URI, existing.doc.docUri).apply()
            return
        }
        openInNewSession(uri, silent)
    }

    private fun openInNewSession(uri: Uri, silent: Boolean) {
        val id = nextSessionId++
        _sessions.update { it + DocSession(id = id, doc = DocumentState(loading = true)) }
        _activeId.value = id
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
                _sessions.update { list ->
                    list.map { session ->
                        if (session.id != id) session else session.copy(
                            doc = DocumentState(
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
                        )
                    }
                }
                prefs.edit().putString(KEY_LAST_URI, uri.toString()).apply()
                recentFiles.remember(uri.toString(), loaded.title)
                _recent.value = recentFiles.list()
                _bookmarks.value = bookmarkStore.list(uri.toString())
                // 该会话正好是上次停留的那一本就切过去（恢复多开时的活动标签）
                if (pendingActiveUri != null && loaded.docUri == pendingActiveUri) {
                    _activeId.value = id
                    pendingActiveUri = null
                }
                persistSessionUris()
            }.onFailure { e ->
                updateDoc {
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
        val uri = doc.value.docUri ?: return
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
        _sessions.value = emptyList()
        prefs.edit().remove(KEY_SESSION_URIS).apply()
        _activeId.value = null
        prefs.edit().remove(KEY_LAST_URI).apply()
    }

    // ── 会话管理 ────────────────────────────────────────────────

    /** 切换活动会话。目标不存在时忽略。 */
    fun switchTo(id: Long) {
        if (_sessions.value.none { it.id == id }) return
        _activeId.value = id
        val uri = _sessions.value.first { it.id == id }.doc.docUri.orEmpty()
        _bookmarks.value = bookmarkStore.list(uri)
        // 记住上次停在哪一本，重启后回到它
        prefs.edit().putString(KEY_ACTIVE_URI, uri).apply()
    }

    /** 关闭一个会话。关掉活动会话时自动切到相邻的一个。 */
    fun closeSession(id: Long) {
        val list = _sessions.value
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        val rest = list.filterNot { it.id == id }
        _sessions.value = rest
        persistSessionUris()
        if (_activeId.value == id) {
            _activeId.value = rest.getOrNull(index.coerceAtMost(rest.lastIndex))?.id
            _bookmarks.value = bookmarkStore.list(
                rest.getOrNull(index.coerceAtMost(rest.lastIndex))?.doc?.docUri.orEmpty()
            )
        }
    }

    /** 界面层把某会话的滚动位置回写进来（切换标签前调用）。 */
    fun saveSessionScroll(id: Long, index: Int, offset: Int) {
        _sessions.update { list ->
            list.map {
                if (it.id == id) it.copy(scrollIndex = index, scrollOffset = offset) else it
            }
        }
    }

    /** 界面层回写当前可见块（目录高亮用）。 */
    fun saveSessionVisibleBlock(id: Long, blockIndex: Int) {
        _sessions.update { list ->
            list.map { if (it.id == id) it.copy(visibleBlock = blockIndex) else it }
        }
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

    /** 给某个块加书签。同一块重复加会更新摘要与时间，不会重复。 */
    fun addBookmark(blockIndex: Int) {
        val uri = doc.value.docUri ?: return
        val blocks = doc.value.blocks
        if (blockIndex !in blocks.indices) return
        val excerpt = blocks[blockIndex].bookmarkExcerpt()
        bookmarkStore.add(
            Bookmark(
                uri = uri,
                blockIndex = blockIndex,
                excerpt = excerpt,
                createdAt = System.currentTimeMillis()
            )
        )
        _bookmarks.value = bookmarkStore.list(uri)
    }

    fun removeBookmark(blockIndex: Int) {
        val uri = doc.value.docUri ?: return
        bookmarkStore.remove(uri, blockIndex)
        _bookmarks.value = bookmarkStore.list(uri)
    }

    /**
     * 从书签跳转。文档改版后 blockIndex 会错位，
     * 这里用摘要做二次校验：对不上就返回 false，让界面提示而不是跳到错误位置。
     */
    fun jumpToBookmark(bookmark: Bookmark): Boolean {
        val blocks = doc.value.blocks
        if (bookmark.blockIndex !in blocks.indices) return false
        val current = blocks[bookmark.blockIndex].bookmarkExcerpt()
        if (current != bookmark.excerpt) return false
        requestJump(bookmark.blockIndex)
        return true
    }

    fun setSettingsVisible(visible: Boolean) {
        _showSettings.value = visible
    }

    private companion object {
        const val KEY_FONT = "font_size"
        const val KEY_THEME = "theme_mode"
        const val KEY_READING_THEME = "reading_theme"
        const val KEY_LAST_URI = "last_uri"
        const val KEY_SESSION_URIS = "session_uris"
        const val KEY_ACTIVE_URI = "active_uri"
        const val KEY_PROGRESS_INDEX = "progress_index_"
        const val KEY_PROGRESS_OFFSET = "progress_offset_"
    }
}

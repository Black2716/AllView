package com.yg.moduread.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yg.moduread.data.Bookmark
import com.yg.moduread.data.Bookmarks
import com.yg.moduread.data.DocRepository
import com.yg.moduread.data.ProgressStore
import com.yg.moduread.data.RecentFile
import com.yg.moduread.data.RecentFiles
import com.yg.moduread.data.SessionUris
import com.yg.moduread.data.friendlyOpenError
import com.yg.moduread.md.MdToc
import com.yg.moduread.md.bookmarkExcerpt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 阅读器的会话与状态中枢。
 *
 * 重构说明（行为未变，只改职责划分）：
 *  - 偏好的读写 → [ReaderPrefsStore]（键名与默认值封在那边）
 *  - 阅读位置的读写 → [ProgressStore]（hashCode 键、非负收敛）
 *  - 多开 uri 列表的编解码 → [SessionUris]（保留 \n 兼容老数据）
 *  - 打开失败的文案 → [friendlyOpenError]（纯映射，可单测）
 * 这里只保留真正的会话编排：多开、切换、关闭、书签、跳转。
 */
class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("moduread", Application.MODE_PRIVATE)
    private val recentFiles = RecentFiles(prefs)
    private val bookmarkStore = Bookmarks(prefs)
    private val prefsStore = ReaderPrefsStore(prefs)
    private val progressStore = ProgressStore(prefs)

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
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DocumentState())

    /** 活动会话。供需要 scroll 位置的地方取。 */
    private fun activeSession(): DocSession? = _sessions.value.firstOrNull { it.id == _activeId.value }

    private fun updateDoc(transform: (DocumentState) -> DocumentState) {
        val id = _activeId.value ?: return
        _sessions.update { list ->
            list.map { if (it.id == id) it.copy(doc = transform(it.doc)) else it }
        }
    }

    private val _readerPrefs = MutableStateFlow(prefsStore.load())
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
            .putString(SessionUris.KEY_SESSION_URIS, SessionUris.encode(uris))
            .putString(SessionUris.KEY_ACTIVE_URI, active)
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
        val uris = SessionUris.decode(prefs.getString(SessionUris.KEY_SESSION_URIS, null))
        if (uris.isNotEmpty()) {
            uris.forEach { s ->
                runCatching { Uri.parse(s) }.getOrNull()?.let { open(it, silent = true, newTab = true) }
            }
            // 回到上次活动的那一本。此时各会话都在异步加载，docUri 还没落定，
            // 所以只记下目标，真正切换放到加载成功回调里做。
            pendingActiveUri = prefs.getString(SessionUris.KEY_ACTIVE_URI, null)
            return
        }

        val last = prefs.getString(SessionUris.KEY_LAST_URI, null) ?: return
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
            prefs.edit().putString(SessionUris.KEY_ACTIVE_URI, existing.doc.docUri).apply()
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
                val saved = progressStore.read(uri.toString())
                // 目录、锚点表、搜索索引在这里一次算好，界面只管用，不在重组时装算。
                // 具体的映射规则见 buildDocumentState。
                val nextDoc = buildDocumentState(loaded, saved.first, saved.second)
                _sessions.update { list ->
                    list.map { session ->
                        if (session.id != id) session else session.copy(doc = nextDoc)
                    }
                }
                prefs.edit().putString(SessionUris.KEY_LAST_URI, uri.toString()).apply()
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
                if (silent) {
                    // 恢复会话时失败（文件被删/授权失效）：直接丢掉这个会话，
                    // 不留「未命名」空标签 —— 否则重启几次会攒出一排空 tab。
                    _sessions.update { list -> list.filterNot { it.id == id } }
                    if (_activeId.value == id) _activeId.value = _sessions.value.lastOrNull()?.id
                    persistSessionUris()
                } else {
                    // 用户主动打开失败：保留标签并显示原因，让用户看到「未被授权」这类提示。
                    // 标题取 URI 末段（可能是乱码 id），至少比「未命名」有辨识度。
                    val fallbackTitle = uri.lastPathSegment
                        ?.substringAfterLast('/')
                        ?.takeIf { it.isNotBlank() }
                        ?: "打开失败"
                    updateDoc { failedDocumentState(it, fallbackTitle, friendlyOpenError(e)) }
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
        progressStore.save(uri, index, offset)
    }

    fun forgetRecent(uri: String) {
        recentFiles.forget(uri)
        _recent.value = recentFiles.list()
    }

    fun clearDocument() {
        _sessions.value = emptyList()
        prefs.edit().remove(SessionUris.KEY_SESSION_URIS).apply()
        _activeId.value = null
        prefs.edit().remove(SessionUris.KEY_LAST_URI).apply()
    }

    // ── 会话管理 ────────────────────────────────────────────────

    /** 切换活动会话。目标不存在时忽略。 */
    fun switchTo(id: Long) {
        val target = _sessions.value.firstOrNull { it.id == id } ?: return
        _activeId.value = id
        val uri = target.doc.docUri.orEmpty()
        _bookmarks.value = bookmarkStore.list(uri)
        // 记住上次停在哪一本，重启后回到它
        prefs.edit().putString(SessionUris.KEY_ACTIVE_URI, uri).apply()
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
            val next = rest.getOrNull(index.coerceAtMost(rest.lastIndex))
            _activeId.value = next?.id
            _bookmarks.value = bookmarkStore.list(next?.doc?.docUri.orEmpty())
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
        prefsStore.saveFontSize(clamped)
    }

    fun setThemeMode(mode: ThemeMode) {
        _readerPrefs.update { it.copy(themeMode = mode) }
        prefsStore.saveThemeMode(mode)
    }

    fun setReadingTheme(theme: ReadingTheme) {
        _readerPrefs.update { it.copy(readingTheme = theme) }
        prefsStore.saveReadingTheme(theme)
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
}

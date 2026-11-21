package com.yg.moduread.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yg.moduread.data.DocRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReaderViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("moduread", Application.MODE_PRIVATE)

    private val _doc = MutableStateFlow(DocumentState())
    val doc: StateFlow<DocumentState> = _doc.asStateFlow()

    private val _readerPrefs = MutableStateFlow(
        ReaderPrefs(
            fontSizeSp = prefs.getFloat(KEY_FONT, 17f),
            themeMode = runCatching {
                ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!)
            }.getOrDefault(ThemeMode.SYSTEM)
        )
    )
    val readerPrefs: StateFlow<ReaderPrefs> = _readerPrefs.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    private var restoreStarted = false

    /**
     * 续读上次的文件。刻意不在 init 里触发：
     * 首帧只绘制空态骨架，等界面真正显示后再读文件，冷启动路径上没有 IO 与解析。
     */
    fun restoreLastIfNeeded() {
        if (restoreStarted) return
        restoreStarted = true
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
                _doc.value = DocumentState(
                    title = loaded.title,
                    blocks = loaded.blocks,
                    charCount = loaded.charCount,
                    baseDir = loaded.baseDir,
                    docUri = loaded.docUri,
                    loading = false
                )
                prefs.edit().putString(KEY_LAST_URI, uri.toString()).apply()
            }.onFailure { e ->
                _doc.update {
                    it.copy(loading = false, error = if (silent) null else (e.message ?: "打开失败"))
                }
            }
        }
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

    fun setSettingsVisible(visible: Boolean) {
        _showSettings.value = visible
    }

    private companion object {
        const val KEY_FONT = "font_size"
        const val KEY_THEME = "theme_mode"
        const val KEY_LAST_URI = "last_uri"
    }
}

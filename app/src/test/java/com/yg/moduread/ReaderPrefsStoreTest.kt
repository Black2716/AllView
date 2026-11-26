package com.yg.moduread

import com.yg.moduread.ui.ReaderPrefs
import com.yg.moduread.ui.ReaderPrefsStore
import com.yg.moduread.ui.ReadingTheme
import com.yg.moduread.ui.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 偏好持久化：默认值、键名、枚举解析失败的回落行为。
 * 这三件事一旦改动，用户升级后字号/主题会被重置，属于必须锁死的对外行为。
 */
class ReaderPrefsStoreTest {

    private val prefs = FakePrefs()
    private val store = ReaderPrefsStore(prefs)

    @Test
    fun defaultsMatchReaderPrefsDefaults() {
        val loaded = store.load()
        assertEquals(17f, loaded.fontSizeSp, 0.001f)
        assertEquals(ThemeMode.SYSTEM, loaded.themeMode)
        assertEquals(ReadingTheme.PAPER, loaded.readingTheme)
    }

    @Test
    fun keysAreStable() {
        store.saveFontSize(21f)
        store.saveThemeMode(ThemeMode.DARK)
        store.saveReadingTheme(ReadingTheme.NIGHT)
        assertEquals(21f, prefs.getFloat("font_size", 0f), 0.001f)
        assertEquals("DARK", prefs.getString("theme_mode", null))
        assertEquals("NIGHT", prefs.getString("reading_theme", null))
    }

    @Test
    fun savedValuesLoadBack() {
        store.saveFontSize(23f)
        store.saveThemeMode(ThemeMode.LIGHT)
        store.saveReadingTheme(ReadingTheme.SEPIA)
        val loaded = store.load()
        assertEquals(23f, loaded.fontSizeSp, 0.001f)
        assertEquals(ThemeMode.LIGHT, loaded.themeMode)
        assertEquals(ReadingTheme.SEPIA, loaded.readingTheme)
    }

    @Test
    fun unknownEnumNameFallsBackToDefaults() {
        // 降级安装（新版本写过 UNKNOWN 主题）不能让偏好读取抛异常
        prefs.edit().putString("theme_mode", "PLASMA").putString("reading_theme", "INK").apply()
        val loaded = store.load()
        assertEquals(ThemeMode.SYSTEM, loaded.themeMode)
        assertEquals(ReadingTheme.PAPER, loaded.readingTheme)
    }

    @Test
    fun fontSizeIsStoreDumbClampIsInViewModel() {
        // 存储层不做范围收敛，clamp 由 ReaderViewModel.setFontSize 负责 —— 行为与重构前一致
        store.saveFontSize(99f)
        assertEquals(99f, store.load().fontSizeSp, 0.001f)
        assertEquals(99f, ReaderPrefs(fontSizeSp = 99f).fontSizeSp, 0.001f)
    }
}

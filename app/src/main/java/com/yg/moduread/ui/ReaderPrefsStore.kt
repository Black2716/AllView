package com.yg.moduread.ui

import android.content.SharedPreferences

/**
 * 阅读偏好（字号 / 深色模式 / 纸张色）的持久化。
 *
 * 抽出的理由：ReaderViewModel 同时承担会话管理、文件加载和偏好读写，
 * 三类变更互相牵连。把偏好这一组「读一次、写三处」的逻辑独立出来后，
 * ViewModel 只保留「调用 → 更新 StateFlow」两行，键名也集中在一处。
 *
 * 放在 ui 包而不是 data 包：它读写的 [ReaderPrefs] 是界面状态模型，
 * 让 data 层反过来依赖 ui 的枚举会把依赖方向搞乱。
 *
 * 键名与默认值逐字保留：font_size / theme_mode / reading_theme，
 * 字号缺省 17f，枚举解析失败回落到 SYSTEM / PAPER。
 */
internal class ReaderPrefsStore(private val prefs: SharedPreferences) {

    fun load(): ReaderPrefs = ReaderPrefs(
        fontSizeSp = prefs.getFloat(KEY_FONT, ReaderPrefs().fontSizeSp),
        themeMode = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM),
        readingTheme = runCatching {
            ReadingTheme.valueOf(prefs.getString(KEY_READING_THEME, ReadingTheme.PAPER.name)!!)
        }.getOrDefault(ReadingTheme.PAPER)
    )

    fun saveFontSize(size: Float) {
        prefs.edit().putFloat(KEY_FONT, size).apply()
    }

    fun saveThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun saveReadingTheme(theme: ReadingTheme) {
        prefs.edit().putString(KEY_READING_THEME, theme.name).apply()
    }

    private companion object {
        const val KEY_FONT = "font_size"
        const val KEY_THEME = "theme_mode"
        const val KEY_READING_THEME = "reading_theme"
    }
}

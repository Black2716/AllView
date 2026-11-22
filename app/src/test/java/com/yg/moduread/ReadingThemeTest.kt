package com.yg.moduread

import com.yg.moduread.ui.ReadingTheme
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 纸张主题的持久化契约。
 *
 * 存在理由：主题名会写进 SharedPreferences，改名或删值会让老用户的设置失效。
 * 这个测试把「枚举名是持久化 key」这一点钉住 —— 谁要重命名就得先想清楚迁移。
 */
class ReadingThemeTest {

    @Test
    fun namesAreStablePersistenceKeys() {
        assertEquals("PAPER", ReadingTheme.PAPER.name)
        assertEquals("SEPIA", ReadingTheme.SEPIA.name)
        assertEquals("NIGHT", ReadingTheme.NIGHT.name)
    }

    @Test
    fun valueOfRoundTrips() {
        ReadingTheme.entries.forEach { theme ->
            assertEquals(theme, ReadingTheme.valueOf(theme.name))
        }
    }

    @Test
    fun unknownValueFallsBackToPaper() {
        // 与 ReaderViewModel 的 runCatching{}.getOrDefault(PAPER) 保持同一份契约
        val parsed = runCatching { ReadingTheme.valueOf("SOMETHING_OLD") }
            .getOrDefault(ReadingTheme.PAPER)
        assertEquals(ReadingTheme.PAPER, parsed)
    }

    @Test
    fun defaultIsPaper() {
        // 老用户升级后不该突然变成米黄或夜间
        assertEquals(ReadingTheme.PAPER, ReadingTheme.entries.first())
    }
}

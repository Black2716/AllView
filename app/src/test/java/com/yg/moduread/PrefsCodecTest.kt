package com.yg.moduread

import com.yg.moduread.data.PrefsCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 编解码是 RecentFiles / Bookmarks 共用的唯一存储实现，
 * 这里固定住它的契约：空值返回空表、字段原样保留、写回可被读回。
 */
class PrefsCodecTest {

    private val prefs = FakePrefs()

    @Test
    fun missingKeyReadsAsEmpty() {
        assertTrue(PrefsCodec.read(prefs, "absent", '\u0001', '\u0002').isEmpty())
    }

    @Test
    fun emptyStringReadsAsEmpty() {
        prefs.edit().putString("k", "").apply()
        assertTrue(PrefsCodec.read(prefs, "k", '\u0001', '\u0002').isEmpty())
    }

    @Test
    fun roundTripKeepsFieldBoundaries() {
        PrefsCodec.write(prefs, "k", '\u0001', '\u0002', listOf(listOf("a", "b", "1"), listOf("c", "d", "2")))
        assertEquals(
            listOf(listOf("a", "b", "1"), listOf("c", "d", "2")),
            PrefsCodec.read(prefs, "k", '\u0001', '\u0002')
        )
    }

    @Test
    fun emptyRowsWriteEmptyString() {
        PrefsCodec.write(prefs, "k", '\u0001', '\u0002', emptyList())
        assertEquals("", prefs.getString("k", null))
    }

    @Test
    fun differentSeparatorsDoNotCollide() {
        // 最近文件用 \u0001/\u0002，书签用 \u0003/\u0004：读错分隔符必须读不出内容
        PrefsCodec.write(prefs, "k", '\u0003', '\u0004', listOf(listOf("x")))
        assertTrue(PrefsCodec.read(prefs, "k", '\u0001', '\u0002').size == 1)
        assertEquals(listOf(listOf("x")), PrefsCodec.read(prefs, "k", '\u0003', '\u0004'))
    }
}

package com.yg.moduread

import android.content.SharedPreferences
import com.yg.moduread.data.RecentFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RecentFiles 只依赖 SharedPreferences 的两个方法，
 * 用一个内存实现替身即可覆盖序列化/去重/上限三条逻辑，不需要 Robolectric。
 */
class RecentFilesTest {

    private class FakePrefs : SharedPreferences {
        private val map = HashMap<String, String?>()

        override fun getString(key: String?, defValue: String?): String? {
            if (key == null) return defValue
            return if (map.containsKey(key)) map[key] else defValue
        }

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = HashMap<String, String?>()
            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun apply() { map.putAll(pending) }
            override fun commit(): Boolean { map.putAll(pending); return true }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) map.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor { map.clear(); return this }
            override fun putStringSet(k: String?, v: MutableSet<String>?) = this
            override fun putInt(k: String?, v: Int) = this
            override fun putLong(k: String?, v: Long) = this
            override fun putFloat(k: String?, v: Float) = this
            override fun putBoolean(k: String?, v: Boolean) = this
        }
        override fun getAll(): MutableMap<String, *> = map
        override fun getStringSet(k: String?, d: MutableSet<String>?) = d
        override fun getInt(k: String?, d: Int) = d
        override fun getLong(k: String?, d: Long) = d
        override fun getFloat(k: String?, d: Float) = d
        override fun getBoolean(k: String?, d: Boolean) = d
        override fun contains(k: String?) = if (k == null) false else map.containsKey(k)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    @Test
    fun newestGoesFirst() {
        val files = RecentFiles(FakePrefs())
        files.remember("content://a", "a.md", now = 1000)
        files.remember("content://b", "b.md", now = 2000)
        assertEquals(listOf("content://b", "content://a"), files.list().map { it.uri })
    }

    @Test
    fun reopeningMovesToFrontWithoutDuplicating() {
        val files = RecentFiles(FakePrefs())
        files.remember("content://a", "a.md", now = 1000)
        files.remember("content://b", "b.md", now = 2000)
        files.remember("content://a", "a.md", now = 3000)
        val list = files.list()
        assertEquals(2, list.size)
        assertEquals("content://a", list[0].uri)
        assertEquals(3000L, list[0].openedAt)
    }

    @Test
    fun capsAtEightEntries() {
        val files = RecentFiles(FakePrefs())
        (1..12).forEach { files.remember("content://" + it, it.toString() + ".md", now = it.toLong()) }
        assertEquals(8, files.list().size)
        assertEquals("content://12", files.list().first().uri)
    }

    @Test
    fun forgetRemovesOnlyThatEntry() {
        val files = RecentFiles(FakePrefs())
        files.remember("content://a", "a.md", now = 1000)
        files.remember("content://b", "b.md", now = 2000)
        files.forget("content://a")
        assertEquals(listOf("content://b"), files.list().map { it.uri })
    }

    @Test
    fun nameWithSeparatorSurvives() {
        // 文件名里出现分隔符不能把记录切坏：分隔符是 \u0001/\u0002，正常文件名不会有
        val files = RecentFiles(FakePrefs())
        files.remember("content://x", "很长的 名字 (1).md", now = 5000)
        assertEquals("很长的 名字 (1).md", files.list().single().name)
    }

    @Test
    fun emptyStoreReturnsEmptyList() {
        assertTrue(RecentFiles(FakePrefs()).list().isEmpty())
    }
}

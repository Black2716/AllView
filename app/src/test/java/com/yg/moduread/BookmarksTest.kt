package com.yg.moduread

import android.content.SharedPreferences
import com.yg.moduread.data.Bookmark
import com.yg.moduread.data.Bookmarks
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdParser
import com.yg.moduread.md.bookmarkExcerpt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarksTest {

    /** 与 RecentFilesTest 同一份内存替身，避免引 Robolectric。 */
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

    private fun mark(uri: String = "content://a", index: Int = 3, text: String = "摘要") =
        Bookmark(uri = uri, blockIndex = index, excerpt = text, createdAt = 1000L)

    @Test
    fun addAndListPerDocument() {
        val store = Bookmarks(FakePrefs())
        store.add(mark(uri = "content://a", index = 1))
        store.add(mark(uri = "content://b", index = 2))
        assertEquals(1, store.list("content://a").size)
        assertEquals(1, store.list("content://b").size)
        assertEquals(2, store.all().size)
    }

    @Test
    fun addingSameBlockUpdatesInsteadOfDuplicating() {
        val store = Bookmarks(FakePrefs())
        store.add(mark(index = 5, text = "旧摘要"))
        store.add(mark(index = 5, text = "新摘要"))
        val list = store.list("content://a")
        assertEquals(1, list.size)
        assertEquals("新摘要", list.single().excerpt)
    }

    @Test
    fun removeDropsOnlyThatBlock() {
        val store = Bookmarks(FakePrefs())
        store.add(mark(index = 1))
        store.add(mark(index = 2))
        store.remove("content://a", 1)
        assertEquals(listOf(2), store.list("content://a").map { it.blockIndex })
    }

    @Test
    fun clearDocumentKeepsOtherDocuments() {
        val store = Bookmarks(FakePrefs())
        store.add(mark(uri = "content://a", index = 1))
        store.add(mark(uri = "content://b", index = 1))
        store.clearDocument("content://a")
        assertTrue(store.list("content://a").isEmpty())
        assertEquals(1, store.list("content://b").size)
    }

    @Test
    fun excerptSurvivesSerializationWithSpaces() {
        val store = Bookmarks(FakePrefs())
        store.add(mark(index = 9, text = "含 空格 的 摘要"))
        assertEquals("含 空格 的 摘要", store.list("content://a").single().excerpt)
    }

    @Test
    fun excerptIsStableForSameBlockAndChangesWhenTextChanges() {
        val blocks = MdParser.shared.parse("# 标题\n\n第一段内容\n\n第二段内容")
        val first = blocks[1].bookmarkExcerpt()
        assertEquals(first, blocks[1].bookmarkExcerpt())
        assertNotEquals(first, blocks[2].bookmarkExcerpt())
    }

    @Test
    fun excerptIsCollapsedAndTruncated() {
        val blocks = MdParser.shared.parse("段落\n含换行   与多余空格\n以及更多文字" + "填充".repeat(60))
        val text = blocks.single().bookmarkExcerpt()
        assertTrue("不该含换行", !text.contains('\n'))
        assertTrue("应截断到 40 字，实际 " + text.length, text.length <= 40)
    }

    @Test
    fun emptyStoreReturnsEmptyList() {
        assertTrue(Bookmarks(FakePrefs()).all().isEmpty())
    }
}

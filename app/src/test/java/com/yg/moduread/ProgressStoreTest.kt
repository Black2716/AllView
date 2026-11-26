package com.yg.moduread

import com.yg.moduread.data.ProgressStore
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 阅读位置：键由 uri.hashCode 决定，缺失回 0，异常值收敛非负。
 * 这三条一起保证「换书各自记住位置」且「坏数据不会把列表滚到负数」。
 */
class ProgressStoreTest {

    private val prefs = FakePrefs()
    private val store = ProgressStore(prefs)

    @Test
    fun missingProgressReadsZero() {
        assertEquals(0 to 0, store.read("content://nothing"))
    }

    @Test
    fun saveThenReadRoundTrips() {
        store.save("content://a", 12, 340)
        assertEquals(12 to 340, store.read("content://a"))
    }

    @Test
    fun documentsAreIsolatedByUri() {
        store.save("content://a", 1, 2)
        store.save("content://b", 7, 8)
        assertEquals(1 to 2, store.read("content://a"))
        assertEquals(7 to 8, store.read("content://b"))
    }

    @Test
    fun negativeValuesAreClampedToZero() {
        store.save("content://a", -5, -9)
        assertEquals(0 to 0, store.read("content://a"))
    }

    @Test
    fun keyPrefixIsStable() {
        // 改动键名等于让已装应用的阅读进度全部失效，这里把它钉住
        store.save("uri", 3, 4)
        val hash = "uri".hashCode()
        assertEquals(3, prefs.getInt("progress_index_" + hash, -1))
        assertEquals(4, prefs.getInt("progress_offset_" + hash, -1))
    }
}

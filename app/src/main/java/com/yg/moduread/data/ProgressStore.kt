package com.yg.moduread.data

import android.content.SharedPreferences

/**
 * 按文档记阅读位置。
 *
 * 从 ReaderViewModel 抽出：键名拼接、hashCode 取键、非负收敛这三件事
 * 与界面生命周期无关，留在 ViewModel 里只能靠「读代码确认」来保证一致。
 *
 * 键的形态保持原样（progress_index_<uri.hashCode()>）—— 改前缀或换成别的
 * 摘要算法，已装应用里的阅读进度就全部失效，用户重开每本书都会跳回开头。
 */
internal class ProgressStore(private val prefs: SharedPreferences) {

    fun save(uri: String, index: Int, offset: Int) {
        val key = keyOf(uri)
        prefs.edit()
            .putInt(KEY_INDEX + key, index)
            .putInt(KEY_OFFSET + key, offset)
            .apply()
    }

    /** 读回上次位置；缺失或异常值一律收敛到 0。 */
    fun read(uri: String): Pair<Int, Int> {
        val key = keyOf(uri)
        val index = prefs.getInt(KEY_INDEX + key, 0)
        val offset = prefs.getInt(KEY_OFFSET + key, 0)
        return index.coerceAtLeast(0) to offset.coerceAtLeast(0)
    }

    /** uri 可能很长，用 hashCode 做键；碰撞概率对「记住几本书的位置」这个用途无影响。 */
    private fun keyOf(uri: String): String = uri.hashCode().toString()

    private companion object {
        const val KEY_INDEX = "progress_index_"
        const val KEY_OFFSET = "progress_offset_"
    }
}

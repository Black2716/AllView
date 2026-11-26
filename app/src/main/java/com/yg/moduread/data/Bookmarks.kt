package com.yg.moduread.data

import android.content.SharedPreferences

/**
 * 一条书签。
 *
 * [excerpt] 不只用于列表展示 —— 文档改版后 [blockIndex] 会错位，
 * 跳转前用它做二次校验：若该块现在的正文与摘要对不上，说明文档变了，
 * 提示用户而不是跳到错误位置。
 */
data class Bookmark(
    val uri: String,
    val blockIndex: Int,
    val excerpt: String,
    val createdAt: Long
)

/**
 * 书签存储。
 *
 * 与 RecentFiles 同一套路：SharedPreferences + 自定义分隔符序列化，
 * 不引 Gson 也不建数据库。单本文档的书签量级是个位数到几十条，够用。
 *
 * 编解码同样交给 [PrefsCodec]；这里只留「同一块只存一条、超出上限淘汰」
 * 这类业务规则。
 */
class Bookmarks(private val prefs: SharedPreferences) {

    fun list(uri: String): List<Bookmark> = all().filter { it.uri == uri }

    fun all(): List<Bookmark> =
        PrefsCodec.read(prefs, KEY, SEP_RECORD, SEP_FIELD).mapNotNull { f ->
            if (f.size < 4) return@mapNotNull null
            val idx = f[1].toIntOrNull() ?: return@mapNotNull null
            val at = f[3].toLongOrNull() ?: return@mapNotNull null
            Bookmark(uri = f[0], blockIndex = idx, excerpt = f[2], createdAt = at)
        }

    /** 同一块只留一条：重复加书签等价于「更新摘要与时间」。 */
    fun add(bookmark: Bookmark) {
        val rest = all().filterNot { it.uri == bookmark.uri && it.blockIndex == bookmark.blockIndex }
        write((listOf(bookmark) + rest).take(LIMIT))
    }

    fun remove(uri: String, blockIndex: Int) {
        write(all().filterNot { it.uri == uri && it.blockIndex == blockIndex })
    }

    fun clearDocument(uri: String) {
        write(all().filterNot { it.uri == uri })
    }

    private fun write(items: List<Bookmark>) {
        PrefsCodec.write(
            prefs, KEY, SEP_RECORD, SEP_FIELD,
            items.map { listOf(it.uri, it.blockIndex.toString(), it.excerpt, it.createdAt.toString()) }
        )
    }

    companion object {
        private const val KEY = "bookmarks"
        private const val SEP_RECORD = '\u0003'
        private const val SEP_FIELD = '\u0004'

        /** 摘要长度上限：够校验文档是否变化，又不至于让存储膨胀。 */
        const val EXCERPT_LEN = 40

        /** 全局上限。单本文档几十条书签足矣，超出按时间淘汰最旧的。 */
        const val LIMIT = 200
    }
}

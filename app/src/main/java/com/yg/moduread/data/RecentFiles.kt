package com.yg.moduread.data

import android.content.SharedPreferences

/** 最近打开过的一本书。uri 已拿到持久化授权，重启后仍可读。 */
data class RecentFile(
    val uri: String,
    val name: String,
    val openedAt: Long
)

/**
 * 最近文件列表。
 *
 * 刻意不建数据库：顶多 8 条记录，序列化成一个用 \u0001 / \u0002 分隔的字符串，
 * 既不需要 Gson 也不需要 Room，包体积零增长。
 *
 * 存取格式的编解码交给 [PrefsCodec]（与 Bookmarks 共用同一套实现）。
 * 本类只保留「记录怎么排序、怎么去重、留几条」这类业务规则，
 * 分隔符原样传给编解码层 —— 已有用户的记录就是按这对分隔符写的。
 */
class RecentFiles(private val prefs: SharedPreferences) {

    fun list(): List<RecentFile> =
        PrefsCodec.read(prefs, KEY, SEP_RECORD, SEP_FIELD).mapNotNull { parts ->
            if (parts.size < 3) return@mapNotNull null
            val at = parts[2].toLongOrNull() ?: return@mapNotNull null
            RecentFile(uri = parts[0], name = parts[1], openedAt = at)
        }

    /** 打开成功后调用：同 uri 的旧记录先移除，再插到最前，最多留 [LIMIT] 条。 */
    fun remember(uri: String, name: String, now: Long = System.currentTimeMillis()) {
        val rest = list().filterNot { it.uri == uri }
        val next = (listOf(RecentFile(uri, name, now)) + rest).take(LIMIT)
        write(next)
    }

    fun forget(uri: String) {
        write(list().filterNot { it.uri == uri })
    }

    private fun write(items: List<RecentFile>) {
        PrefsCodec.write(
            prefs, KEY, SEP_RECORD, SEP_FIELD,
            items.map { listOf(it.uri, it.name, it.openedAt.toString()) }
        )
    }

    private companion object {
        const val KEY = "recent_files"
        const val SEP_RECORD = '\u0001'
        const val SEP_FIELD = '\u0002'

        /** 持久化授权系统上限约 128 个，留 8 条远不到顶。 */
        const val LIMIT = 8
    }
}

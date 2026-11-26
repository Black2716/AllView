package com.yg.moduread.data

import android.content.SharedPreferences

/**
 * SharedPreferences + 自定义分隔符的极简记录编解码。
 *
 * 历史成因：RecentFiles 与 Bookmarks 各自实现了同一套「split 记录 / split 字段 /
 * joinToString 写回」的代码，两份实现只差分隔符与字段个数。任何一处修 bug
 * （例如空串处理、脏记录跳过）都要改两遍，漏一遍就是两条不一致的持久化路径。
 * 这里把「怎么存」收敛到一处，「存什么」仍由各自的 data class 决定。
 *
 * 注意：分隔符是调用方传入的字面量，**不能**统一成某个全局常量 ——
 * 已有用户数据用的是 \u0001/\u0002（最近文件）与 \u0003/\u0004（书签），
 * 改分隔符等于让历史记录全部读不出来。
 */
internal object PrefsCodec {

    /**
     * 读出一张二维表：每条记录一行、每个字段一格。
     *
     * 不做字段数校验 —— 版本升级导致的字段增减由各自的 [list] 决定丢弃策略，
     * 编解码层只负责切分，保持「旧记录能被新代码读到」。
     */
    fun read(
        prefs: SharedPreferences,
        key: String,
        sepRecord: Char,
        sepField: Char
    ): List<List<String>> {
        val raw = prefs.getString(key, null)
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(sepRecord).map { record -> record.split(sepField) }
    }

    /** 写回同一张二维表。空列表会写成空串（而不是 remove），与历史行为一致。 */
    fun write(
        prefs: SharedPreferences,
        key: String,
        sepRecord: Char,
        sepField: Char,
        rows: List<List<String>>
    ) {
        val raw = rows.joinToString(sepRecord.toString()) { row ->
            row.joinToString(sepField.toString())
        }
        prefs.edit().putString(key, raw).apply()
    }
}

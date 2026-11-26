package com.yg.moduread.data

/**
 * 多开会话的 uri 列表编解码。
 *
 * 存在理由：ReaderViewModel 里「把打开的 uri 用 \n 拼起来存、重启时拆开」
 * 这段逻辑出现了两次（写入、读取），而它承担了跨版本兼容的职责 ——
 * 老版本只存单个 last_uri，新版本存列表，两者必须共存。
 * 把它抽成两个纯函数后，这条兼容规则就有了唯一实现处。
 */
internal object SessionUris {

    /** 恢复顺序 = 标签顺序，所以这里必须保序，不能去重也不能排序。 */
    fun encode(uris: List<String>): String = uris.joinToString("\n")

    /** 空白行一律丢弃：手工清理过的偏好文件里可能留下空行。 */
    fun decode(raw: String?): List<String> =
        raw?.split("\n")?.filter { it.isNotBlank() }.orEmpty()

    const val KEY_SESSION_URIS = "session_uris"
    const val KEY_ACTIVE_URI = "active_uri"
    const val KEY_LAST_URI = "last_uri"
}

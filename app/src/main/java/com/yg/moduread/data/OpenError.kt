package com.yg.moduread.data

/**
 * 把打开文档时抛出的异常翻译成人话。
 *
 * 从 ReaderViewModel 抽出来的理由：这是纯字符串映射，没有任何 Android 状态，
 * 却因为写在 ViewModel 私有方法里而无法单测 —— 想验证「SecurityException 会
 * 显示未授权文案」必须起一个 ViewModel。抽成顶层函数后它就是一个可断言的映射表。
 *
 * 判定顺序与文案逐字保留：先看异常类型，再看 message 里的关键字；
 * 匹配顺序影响结果（SecurityException 的 message 里也可能出现 ENOENT），不要重排。
 */
internal fun friendlyOpenError(e: Throwable): String {
    val raw = e.message.orEmpty()
    return when {
        e is SecurityException || raw.contains("Permission Denial", true) ||
            raw.contains("requires that you obtain access", true) ->
            "这个文件没有被授权访问。\n点「选择文件」重新选一次即可（从外部打开时若被拒绝，也需要这样授权一次）。"

        e is java.io.FileNotFoundException || raw.contains("No such file", true) ||
            raw.contains("ENOENT", true) ->
            "文件找不到了，可能已被移动或删除。"

        raw.contains("EACCES", true) -> "没有读取该文件的权限。"

        raw.isBlank() -> "打开失败，请重试。"

        // 兜底：只保留第一行，避免长堆栈式文本铺满屏幕
        else -> raw.lineSequence().firstOrNull()?.take(120) ?: "打开失败，请重试。"
    }
}

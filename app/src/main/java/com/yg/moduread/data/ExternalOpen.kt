package com.yg.moduread.data

import android.content.Intent
import android.net.Uri

/**
 * 外部打开（文件管理器 / 其他 App 的 VIEW）的意图解析。
 *
 * 单独抽出来是为了可单测：Activity 里的私有方法没法测，
 * 而「哪些 intent 该被当作文档打开」是有明确边界的逻辑。
 */
object ExternalOpen {

    /**
     * 只接受 VIEW + content/file 两种 scheme。
     *
     * 排除掉的情况：
     *  - 启动器点图标（action=MAIN，data 为 null）
     *  - 带 http(s)/mailto 等其它 scheme 的 VIEW（不是本地文档）
     */
    fun uriOf(intent: Intent?): Uri? {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return null
        val data = intent.data ?: return null
        return when (data.scheme) {
            "content", "file" -> data
            else -> null
        }
    }
}

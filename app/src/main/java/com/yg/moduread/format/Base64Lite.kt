package com.yg.moduread.format

/**
 * 极简 Base64 编码。
 *
 * 为什么不用 android.util.Base64：它在 JVM 单元测试里是空壳，
 * 调用会抛 "not mocked"，导致图片相关的解析逻辑完全无法测试。
 * 为什么不用 java.util.Base64：它要求 API 26，而本项目 minSdk 是 24。
 * 自己实现二十行，两端行为一致。
 */
object Base64Lite {

    private const val ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i + 2 < data.size) {
            val n = ((data[i].toInt() and 0xFF) shl 16) or
                ((data[i + 1].toInt() and 0xFF) shl 8) or
                (data[i + 2].toInt() and 0xFF)
            sb.append(ALPHABET[(n ushr 18) and 0x3F])
            sb.append(ALPHABET[(n ushr 12) and 0x3F])
            sb.append(ALPHABET[(n ushr 6) and 0x3F])
            sb.append(ALPHABET[n and 0x3F])
            i += 3
        }
        val remain = data.size - i
        if (remain == 1) {
            val n = (data[i].toInt() and 0xFF) shl 16
            sb.append(ALPHABET[(n ushr 18) and 0x3F])
            sb.append(ALPHABET[(n ushr 12) and 0x3F])
            sb.append("==")
        } else if (remain == 2) {
            val n = ((data[i].toInt() and 0xFF) shl 16) or
                ((data[i + 1].toInt() and 0xFF) shl 8)
            sb.append(ALPHABET[(n ushr 18) and 0x3F])
            sb.append(ALPHABET[(n ushr 12) and 0x3F])
            sb.append(ALPHABET[(n ushr 6) and 0x3F])
            sb.append("=")
        }
        return sb.toString()
    }
}
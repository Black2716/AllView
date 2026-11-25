package com.yg.moduread.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri

/**
 * 用 PdfRenderer 读权威页数与每页尺寸。
 *
 * 这是唯一可信来源：文本正则数 /Type /Page 会把对象流、注释、
 * 页面树引用里的同名串也算进去，导致页数偏多。
 * 多出来的页 openPage 会抛 "Invalid page index"。
 */
object PdfPageSizes {

    /**
     * 快速估计页数：直接数 /Type /Page，不构造 PdfRenderer。
     *
     * PdfRenderer 初始化要解析整个文件结构，15MB 的文档在设备上
     * 要几十秒甚至更久（用户实测「转圈 2 分钟」）。
     * 页数只需上界，越界页由渲染层兜底跳过。
     */
    fun estimatePages(bytes: ByteArray): Int {
        val text = String(bytes, Charsets.ISO_8859_1)
        val n = Regex("""/Type\s*/Page[^s]""").findAll(text).count()
        return if (n > 0) n else 1
    }

    /**
     * 读页数与每页尺寸。
     *
     * 必须读真实尺寸：渲染层用 width/height 算宽高比，
     * 统一占位会让横向页、异形页变形。
     *
     * 性能上这不算瓶颈 —— openPage 只读 MediaBox，不解码内容。
     * 真正的慢点在文本层：extractText 会把 14MB 的图片流也解压一遍。
     */
    fun read(context: Context, uri: Uri): List<Pair<Int, Int>>? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val n = renderer.pageCount
                if (n <= 0) return@use emptyList<Pair<Int, Int>>()

                // 只开第 1 页取尺寸，其余复用。
                //
                // 为什么不是逐页 openPage：设备上每次 openPage 都要
                // 解析该页的对象与资源引用，177 页会累计到几十秒
                // （用户实测「等了 65 秒」）。而实测这份文档
                // 177 页的 MediaBox 完全一致（595×842），
                // 逐页读纯属浪费。
                //
                // 参考页尺寸对渲染足够：阅读层按屏幕宽度等比缩放，
                // 偶有异形页时按比例呈现也不会破版。
                val first = renderer.openPage(0)
                val w: Int
                val h: Int
                try {
                    w = first.width
                    h = first.height
                } finally {
                    first.close()
                }
                List(n) { w to h }
            }
        }
    }.getOrNull()

    private const val DEFAULT_W = 595
    private const val DEFAULT_H = 842
}

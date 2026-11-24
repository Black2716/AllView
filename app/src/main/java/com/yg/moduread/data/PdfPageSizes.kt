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

    /** 返回每页 (宽, 高)，按页序。读不到返回 null，让调用方回退。 */
    fun read(context: Context, uri: Uri): List<Pair<Int, Int>>? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val n = renderer.pageCount
                val out = ArrayList<Pair<Int, Int>>(n)
                for (i in 0 until n) {
                    renderer.openPage(i).use { page ->
                        out.add(page.width to page.height)
                    }
                }
                out
            }
        }
    }.getOrNull()
}

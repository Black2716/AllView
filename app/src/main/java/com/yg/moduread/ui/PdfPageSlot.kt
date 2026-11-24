package com.yg.moduread.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yg.moduread.md.MdBlock
import java.io.File

/**
 * PDF 页的真实渲染。
 *
 * PdfRenderer 约束：
 *  - 非线程安全，且必须 close
 *  - 不能同时开多个 PdfRenderer 实例指向同一文件
 *  - 页面必须显式 openPage/closePage
 *
 * 因此这里每个文档持有一个 renderer（remember(docUri)），页面位图按页缓存。
 * 位图缓存用固定上限的 LRU：PDF 页位图是最大的内存来源（A4@2x 约 8 MB/页）。
 */
@Composable
fun PdfPageSlot(
    block: MdBlock.PdfPage,
    docUri: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val cache = remember(docUri) { PdfBitmapCache(context, docUri) }
    val bitmap = remember(docUri, block.pageIndex) { cache.get(block.pageIndex) }

    val ratio = if (block.heightPoints > 0) {
        block.widthPoints.toFloat() / block.heightPoints.toFloat()
    } else 0.707f

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(Shapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "第 " + (block.pageIndex + 1) + " 页",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = "第 " + (block.pageIndex + 1) + " 页（渲染失败）",
                fontSize = Type.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/**
 * PDF 页位图缓存。
 *
 * 只缓存最近的若干页，避免整本入内存。renderer 在对象生命周期内复用，
 * 由 GC 回收时关闭（PdfRenderer 无 finalize，这里保留显式 close 入口）。
 */
private class PdfBitmapCache(
    private val context: android.content.Context,
    private val docUri: String?
) {
    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private val lru = object : LinkedHashMap<Int, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?): Boolean {
            val evict = size > MAX_PAGES
            if (evict) eldest?.value?.recycle()
            return evict
        }
    }

    fun get(pageIndex: Int): Bitmap? {
        lru[pageIndex]?.let { if (!it.isRecycled) return it }
        val r = ensureRenderer() ?: return null
        return runCatching {
            r.openPage(pageIndex).use { page ->
                // 按屏幕密度放大，保证清晰度；上限 2 倍防止内存爆掉
                val scale = 2
                val w = (page.width * scale).coerceAtLeast(1)
                val h = (page.height * scale).coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                lru[pageIndex] = bmp
                bmp
            }
        }.getOrNull()
    }

    private fun ensureRenderer(): PdfRenderer? {
        renderer?.let { return it }
        return runCatching {
            val uri = android.net.Uri.parse(docUri ?: return null)
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            pfd = descriptor
            PdfRenderer(descriptor).also { renderer = it }
        }.getOrNull()
    }

    companion object {
        /** 同时保留的页数上限。A4@2x 约 8 MB/页，4 页约 32 MB，是安全区间。 */
        private const val MAX_PAGES = 4
    }
}

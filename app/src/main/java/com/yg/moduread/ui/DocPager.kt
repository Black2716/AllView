package com.yg.moduread.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import com.yg.moduread.md.MdBlock

/**
 * 固定版式文档（DOCX / ODT）的分页引擎。
 *
 * 为什么 PDF 能整页、DOCX 要自己做：PDF 由系统 PdfRenderer 直接渲染已排好版的页面；
 * DOCX 只是一串「块」，没有分页与版式信息，必须自己测量高度再切页。
 */
object DocPager {

    /** A4 纵横比（210:297）。页面高度 = 页宽 × 该值。 */
    const val PAGE_ASPECT = 1.4142f

    /** 页内边距占页宽比例，接近 Word 默认左右边距的观感。 */
    const val PAGE_PADDING_RATIO = 0.07f

    /**
     * 把块按高度切页，返回每页的块下标区间 [起, 止)。
     *
     * 规则：
     *  - 贪心累加，超出一页就换页
     *  - 单块高过整页（超长表格/大图）时独占一页，不把后续块挤到更后面
     */
    fun paginate(heights: List<Int>, pageContentHeight: Int): List<IntRange> {
        if (heights.isEmpty() || pageContentHeight <= 0) return emptyList()
        val pages = ArrayList<IntRange>()
        var start = 0
        var used = 0
        for (i in heights.indices) {
            val h = heights[i].coerceAtLeast(0)
            if (h >= pageContentHeight) {
                if (i > start) pages.add(start until i)
                pages.add(i..i)
                start = i + 1
                used = 0
                continue
            }
            if (used + h > pageContentHeight && i > start) {
                pages.add(start until i)
                start = i
                used = 0
            }
            used += h
        }
        if (start < heights.size) pages.add(start until heights.size)
        return pages
    }
}

/**
 * 测量每个块的高度并算出分页。
 *
 * 实现要点：SubcomposeLayout 里为**每个块单独**开一个 slot，
 * 各自 measure 后取出高度。若把整列当一个 slot 测，只能拿到总和，无法切页。
 * 测量层高度为 0，不占可见空间。
 */
@Composable
fun rememberPageRanges(
    blocks: List<MdBlock>,
    fontSizeSp: Float,
    docUri: String?,
    onAnchor: (String) -> Unit,
    onOpenPdfViewer: (android.graphics.Bitmap, String) -> Unit
): List<IntRange> {
    var ranges by remember(blocks, fontSizeSp) { mutableStateOf<List<IntRange>>(emptyList()) }

    // 图片渲染时按页宽等比缩放，测量必须用同一规则，否则高度对不上
    fun estimateImageHeight(block: MdBlock, width: Int, all: List<MdBlock>): Int? = when (block) {
        is MdBlock.EmbeddedImage -> {
            val bmp = runCatching {
                android.graphics.BitmapFactory.decodeByteArray(block.bytes, 0, block.bytes.size)
            }.getOrNull()
            if (bmp != null && bmp.width > 0) {
                (width.toLong() * bmp.height / bmp.width).toInt()
            } else {
                width * 3 / 4   // 解不出时按 4:3 估
            }
        }
        else -> null
    }

    SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val pageWidth = constraints.maxWidth
        if (pageWidth <= 0 || blocks.isEmpty()) {
            return@SubcomposeLayout layout(pageWidth, 0) { }
        }
        val padding = (pageWidth * DocPager.PAGE_PADDING_RATIO).toInt()
        val contentWidth = (pageWidth - padding * 2).coerceAtLeast(1)
        val pageHeightPx = (pageWidth * DocPager.PAGE_ASPECT).toInt()
        val contentHeight = (pageHeightPx - padding * 2).coerceAtLeast(1)

        // 逐块测量：同一 slotId 才能让 subcompose 复用，避免每帧重建
        val heights = ArrayList<Int>(blocks.size)
        for (i in blocks.indices) {
            val block = blocks[i]
            // 图片块单独估算高度，不参与真实测量：
            // 测量发生在位图解码之前，MdBlockView 此时高度是不确定的，
            // 测出来会偏小 —— 渲染后图片更高，就会溢出页底（实测第 1 页底部留大片空白，
            // 图被挤到下一页正是这个原因）。
            // 用「按页宽等比缩放后的高度」估算，与实际渲染一致。
            val estimated = estimateImageHeight(block, contentWidth, blocks)
            if (estimated != null) {
                heights.add(estimated)
                continue
            }
            val ms = subcompose(i, {
                MdBlockView(
                    block = block,
                    baseFontSize = fontSizeSp,
                    modifier = Modifier.fillMaxWidth(),
                    docUri = docUri,
                    onAnchor = onAnchor,
                    onOpenPdfViewer = onOpenPdfViewer
                )
            })
            val pm = ms.firstOrNull()?.measure(
                Constraints.fixedWidth(contentWidth)
            )
            heights.add(pm?.height ?: 0)
        }

        val computed = DocPager.paginate(heights, contentHeight)
        if (computed != ranges) ranges = computed

        layout(pageWidth, 0) { }
    }
    return ranges
}

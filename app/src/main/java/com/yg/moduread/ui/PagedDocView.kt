package com.yg.moduread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yg.moduread.md.MdBlock

/**
 * 固定版式文档的整页视图。
 *
 * DOCX / ODT 按 A4 比例切成页，每页一张卡片，和 PDF 的观感一致。
 * 与 PDF 的区别：PDF 的页由系统渲染成位图，这里是真实的 Compose 内容，
 * 所以文字可选中、可搜索、缩放跟随字号。
 */
@Composable
fun PagedDocView(
    blocks: List<MdBlock>,
    fontSizeSp: Float,
    docUri: String?,
    onAnchor: (String) -> Unit,
    onOpenPdfViewer: (android.graphics.Bitmap, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val pages = rememberPageRanges(blocks, fontSizeSp, docUri, onAnchor, onOpenPdfViewer)

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
    ) {
        if (pages.isEmpty()) {
            // 测量尚未完成：给一行占位，避免闪一下空白
            Text(
                text = "分页中…",
                fontSize = Type.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
            return@Column
        }
        pages.forEachIndexed { pageIndex, range ->
            PageCard(
                pageNo = pageIndex + 1,
                total = pages.size,
                blocks = blocks,
                range = range,
                fontSizeSp = fontSizeSp,
                docUri = docUri,
                onAnchor = onAnchor,
                onOpenPdfViewer = onOpenPdfViewer
            )
        }
    }
}

/** 单页：A4 比例的纸张，内容按页内边距排版。 */
@Composable
private fun PageCard(
    pageNo: Int,
    total: Int,
    blocks: List<MdBlock>,
    range: IntRange,
    fontSizeSp: Float,
    docUri: String?,
    onAnchor: (String) -> Unit,
    onOpenPdfViewer: (android.graphics.Bitmap, String) -> Unit
) {
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .aspectRatio(1f / DocPager.PAGE_ASPECT)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = DocPager.PAGE_PADDING_RATIO.dp * 100)
                .padding(vertical = DocPager.PAGE_PADDING_RATIO.dp * 100)
        ) {
            for (i in range) {
                blocks.getOrNull(i)?.let { b ->
                    MdBlockView(
                        block = b,
                        baseFontSize = fontSizeSp,
                        docUri = docUri,
                        onAnchor = onAnchor,
                        onOpenPdfViewer = onOpenPdfViewer
                    )
                }
            }
        }
        // 页码放右下角，不占正文流
        Text(
            text = pageNo.toString() + " / " + total,
            fontSize = Type.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
        )
    }
}

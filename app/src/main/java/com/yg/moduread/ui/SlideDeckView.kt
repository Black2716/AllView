package com.yg.moduread.ui

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * PPTX 逐页阅读视图。
 *
 * 每页一个固定 16:9 的卡片，内含一个 WebView 显示该页的 SVG。
 *
 * 为什么用「可见 WebView」而不是离屏截成位图：
 * WebView 的内容画在独立合成层，离屏 draw(Canvas) 经常只能拿到背景，
 * 时序也极不稳定（早一帧空白、晚一帧超时）。让 WebView 可见即可正常绘制，
 * 这是最稳的做法。
 *
 * 为什么用 LazyColumn：几十页幻灯片如果一次性组合，会同时存在几十个 WebView，
 * 内存扛不住。懒加载后同时存活的只有可视区附近的几个。
 */
@Composable
fun SlideDeckView(
    pages: List<String>,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    /** 外部列表状态：进度与顶栏标题都依赖它，必须交给上层持有。 */
    listState: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy.rememberLazyListState(),
    /** 底部额外留白：给阅读器自己的底栏让位，否则最后一页被遮住。 */
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp,
            end = 12.dp,
            // 顶部不再额外加留白：外层 Box 已经有 insets.calculateTopPadding()，
            // 这里再加 56dp 会叠成「进度条与切换栏之间出现一大段空隙」（实测反馈）。
            top = 6.dp,
            bottom = 150.dp + bottomInset
        )
    ) {
        itemsIndexed(
            items = pages,
            key = { index, _ -> "slide-" + index }
        ) { index, html ->
            SlidePage(
                html = html,
                aspectRatio = aspectRatio,
                pageNo = index + 1,
                total = pages.size
            )
        }
    }
}

/** 单页幻灯片：16:9 卡片 + WebView。 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SlidePage(
    html: String,
    aspectRatio: Float,
    pageNo: Int,
    total: Int
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            // 固定 16:9：PPT 是投影格式，比例固定才与 PowerPoint 观感一致
            .aspectRatio(if (aspectRatio > 0f) aspectRatio else 16f / 9f)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    // 最简配置：只要 JS 关闭 + 允许 baseUrl 加载即可。
                    // 之前加了 blockNetworkLoads/allowFileAccess 等一堆限制，
                    // 实测会把 about:blank 的初始加载一起挡掉，页面全白。
                    setBackgroundColor(0xFFFFFFFF.toInt())
                    settings.javaScriptEnabled = true
                    loadDataWithBaseURL("about:blank", html, "text/html", "utf-8", null)
               }
            }
        )
        Text(
            text = pageNo.toString() + " / " + total,
            fontSize = Type.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        )
    }
}

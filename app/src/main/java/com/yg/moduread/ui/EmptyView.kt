package com.yg.moduread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.moduread.data.RecentFile

/**
 * 空态页与「最近打开」列表。
 *
 * 从 ReaderScreen.kt 拆出的理由：空态页的每一项（品牌名、按钮、最近列表、
 * 相对时间文案）都与阅读器的滚动编排无关，它只依赖 [RecentFile] 与几个回调。
 * 留在 720 行的 ReaderScreen 里，改一个「×」按钮的点击区要翻过整个列表编排逻辑。
 *
 * 行为未变：文案、字号、间距（28/32/10/22/30、30sp/14sp/15sp/12sp/11sp）
 * 与 relativeTime 的分档阈值都逐字保留。
 */

/** 空态：没有文档时显示品牌、错误提示、选文件入口与最近打开列表。 */
@Composable
internal fun EmptyView(
    error: String?,
    recent: List<RecentFile>,
    onPickFile: () -> Unit,
    onOpenRecent: (String) -> Unit,
    onForgetRecent: (String) -> Unit
) {
    // 必须可滚动：竖屏时「标题 + 按钮 + 8 条最近文件」约 500dp，
    // 横屏或分屏后视口不足，Column 不滚动就会裁掉底部的最近文件且够不着。
    // 内容不足一屏时 fillMaxSize 仍以视口高度为最小高度，居中不受影响。
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "AllView",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = error ?: "打开本地文件开始阅读",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onPickFile)
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            Text(text = "选择文件", fontSize = 15.sp, color = MaterialTheme.colorScheme.onPrimary)
        }

        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(30.dp))
            Text(
                text = "最近打开",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
            recent.forEach { file ->
                RecentRow(
                    file = file,
                    onOpen = { onOpenRecent(file.uri) },
                    onForget = { onForgetRecent(file.uri) }
                )
            }
        }
    }
}

@Composable
private fun RecentRow(
    file: RecentFile,
    onOpen: () -> Unit,
    onForget: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = file.name,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = relativeTime(file.openedAt),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 长按才能移除太隐蔽，这里给一个显式的「×」。
        // 触摸区撑到 MinTouchTarget：改造前是 16sp 文字 + 10/4dp 内边距，
        // 实际命中区只有约 26×24dp，是全应用最难点中的按钮之一。
        // 「×」字号与颜色不变，只是可点范围变大。
        Box(
            Modifier
                .size(MinTouchTarget)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onForget)
                .semantics { contentDescription = "从最近打开中移除" },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "×",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 最近文件的时间显示：不引第三方时间库，按粗粒度分档。 */
private fun relativeTime(epochMillis: Long): String {
    val delta = System.currentTimeMillis() - epochMillis
    if (delta < 0) return "刚刚"
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        delta < minute -> "刚刚"
        delta < hour -> (delta / minute).toString() + " 分钟前"
        delta < day -> (delta / hour).toString() + " 小时前"
        delta < 7 * day -> (delta / day).toString() + " 天前"
        else -> (delta / (7 * day)).toString() + " 周前"
    }
}

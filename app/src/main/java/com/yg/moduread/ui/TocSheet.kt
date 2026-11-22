package com.yg.moduread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.moduread.md.TocEntry

/**
 * 目录抽屉。material3 自带 ModalBottomSheet，不引入任何第三方抽屉库。
 * 长文档（书稿 115 个标题）用 LazyColumn 只渲染可见项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocSheet(
    entries: List<TocEntry>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    // 打开抽屉时把当前章节滚进视野（上方留两条，让上下文可见）。
    // 只在首次组合时算一次 —— 抽屉里手动滚动后不该被拽回去。
    LaunchedEffect(entries, currentIndex) {
        if (entries.isEmpty()) return@LaunchedEffect
        val at = entries.indexOfLast { it.blockIndex <= currentIndex }
        if (at >= 0) listState.scrollToItem((at - 2).coerceAtLeast(0))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "目录",
                    fontSize = Type.title,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entries.size.toString() + " 个标题",
                    fontSize = Type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 当前所在章节 = 最后一个「起始块不晚于当前块」的标题。
            // 不能用 blockIndex == currentIndex 精确匹配：正文停在一个表格或代码块里时，
            // 那个位置根本不是任何标题的索引，精确匹配会导致整份目录一个都不高亮。
            val activeIndex = remember(entries, currentIndex) {
                entries.indexOfLast { it.blockIndex <= currentIndex }
            }

            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 8.dp)) {
                itemsIndexed(entries, key = { _, entry -> entry.blockIndex.toString() + ":" + entry.slug }) { index, entry ->
                    TocRow(
                        entry = entry,
                        active = index == activeIndex,
                        onClick = { onSelect(entry.blockIndex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TocRow(entry: TocEntry, active: Boolean, onClick: () -> Unit) {
    // 按级别缩进：一级标题不缩进，最深 6 级也不超过 60dp
    val indent = ((entry.level - 1).coerceIn(0, 4)) * 14
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 1.dp)
            .clip(Shapes.chip)
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(start = 8.dp + indent.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 一级标题加一条短竖线，方便扫读层级
        if (entry.level <= 2) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = entry.text,
            fontSize = when (entry.level) {
                1 -> (Type.bodyLarge.value + 1f).sp
                2 -> Type.bodyLarge
                else -> Type.body
            },
            fontWeight = if (entry.level <= 2) FontWeight.Medium else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

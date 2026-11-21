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
import androidx.compose.foundation.lazy.items
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "目录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entries.size.toString() + " 个标题",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 8.dp)) {
                items(entries, key = { it.blockIndex.toString() + ":" + it.slug }) { entry ->
                    TocRow(
                        entry = entry,
                        active = entry.blockIndex == currentIndex,
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
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (active) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
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
                1 -> 15.sp
                2 -> 14.sp
                else -> 13.sp
            },
            fontWeight = if (entry.level <= 2) FontWeight.Medium else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

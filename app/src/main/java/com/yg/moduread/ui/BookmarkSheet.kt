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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.moduread.data.Bookmark

/**
 * 书签抽屉。与目录/搜索同一套 ModalBottomSheet，外观由设计令牌统一。
 *
 * 外观改造：
 *  - 标题行换 SheetHeader（带关闭键，与目录/搜索一致）；
 *  - 每条书签从「裸文字 + 一个 14dp 的 ×」变成卡片：左侧一条主色竖线标出
 *    「这是一处定位」，右侧删除键 48dp 命中区 + contentDescription；
 *  - 空态换 EmptyHint，与目录/搜索的空态同一套排版。
 */
@Composable
fun BookmarkSheet(
    bookmarks: List<Bookmark>,
    onOpen: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
    onDismiss: () -> Unit
) {
    AppSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = Sp.l)) {
            SheetHeader(
                title = "书签",
                badge = bookmarks.size.toString() + " 条",
                onClose = onDismiss
            )

            if (bookmarks.isEmpty()) {
                EmptyHint(
                    title = "还没有书签",
                    detail = "长按正文任意段落即可添加"
                )
            } else {
                LazyColumn(
                    Modifier.heightIn(max = SheetListMaxHeight),
                    contentPadding = PaddingValues(horizontal = Sp.l, vertical = Sp.xs)
                ) {
                    items(bookmarks, key = { it.uri + ":" + it.blockIndex }) { mark ->
                        BookmarkRow(
                            mark = mark,
                            onOpen = { onOpen(mark) },
                            onDelete = { onDelete(mark) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(mark: Bookmark, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(Shapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onOpen)
            .semantics {
                role = Role.Button
                contentDescription = "跳到第 " + (mark.blockIndex + 1) + " 块"
            }
            .padding(start = Sp.m, top = Sp.s, bottom = Sp.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧竖线：书签是一处「定位」，卡片左侧标一条，扫读时不必读文字
        Box(
            Modifier
                .width(2.dp)
                .height(28.dp)
                .clip(Shapes.pill)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(Sp.m))
        Column(Modifier.weight(1f)) {
            Text(
                text = mark.excerpt.ifBlank { "（空段落）" },
                fontSize = Type.body,
                lineHeight = (Type.body.value * 1.45f).sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "第 " + (mark.blockIndex + 1) + " 块",
                fontSize = Type.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // 删除键命中区 48dp（改造前是 14dp 图标 + 8/4dp 内边距 ≈ 30dp，且没有描述）
        IconAction(
            description = "删除这条书签",
            onClick = onDelete,
            draw = { tint -> MiIcons.Trash(tint, 16.dp) }
        )
    }
}

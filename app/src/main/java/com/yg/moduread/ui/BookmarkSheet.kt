package com.yg.moduread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yg.moduread.data.Bookmark

/**
 * 书签抽屉。与目录/搜索同一套 ModalBottomSheet，外观由设计令牌统一。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkSheet(
    bookmarks: List<Bookmark>,
    onOpen: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Sp.l, vertical = Sp.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "书签",
                    fontSize = Type.title,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(Sp.s))
                Text(
                    text = bookmarks.size.toString() + " 条",
                    fontSize = Type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Sp.s))

            if (bookmarks.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = Sp.xl),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "还没有书签\n长按正文任意段落即可添加",
                        fontSize = Type.body,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    Modifier.heightIn(max = 420.dp),
                    contentPadding = PaddingValues(bottom = Sp.l)
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
            .padding(vertical = 1.dp)
            .clip(Shapes.chip)
            .clickable(onClick = onOpen)
            .padding(horizontal = Sp.s, vertical = Sp.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = mark.excerpt.ifBlank { "（空段落）" },
                fontSize = Type.body,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "第 " + (mark.blockIndex + 1) + " 块",
                fontSize = Type.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            Modifier
                .clip(Shapes.pill)
                .clickable(onClick = onDelete)
                .padding(horizontal = Sp.s, vertical = Sp.xs)
        ) {
            MiIcons.Close(MaterialTheme.colorScheme.onSurfaceVariant, 14.dp)
        }
    }
}

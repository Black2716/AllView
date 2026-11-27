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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.moduread.md.TocEntry

/**
 * 目录抽屉。material3 自带 ModalBottomSheet，不引入任何第三方抽屉库。
 * 长文档（书稿 115 个标题）用 LazyColumn 只渲染可见项。
 *
 * 外观改造：
 *  - 标题行原本是 20dp 裸内边距 + 8dp 裸间距，改走 SheetHeader（带关闭键，
 *    面板只能下滑关闭的话，目录很长时手指会先滚到列表里）；
 *  - 搜索框从 M3 OutlinedTextField 换成 SearchField（填充 + 描边，与全站一致）；
 *  - 每行原本固定约 36dp 高（9dp 上下内边距），不合格；改 heightIn(min=48dp)，
 *    缩进与竖线保留（层级是目录最重要的信息）。
 */
@Composable
fun TocSheet(
    entries: List<TocEntry>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    // 过滤只作用于展示，不改 entries 本身 —— 高亮仍按原始下标计算
    val shown = remember(entries, query) {
        val q = query.trim()
        if (q.isEmpty()) entries else entries.filter { it.text.contains(q, ignoreCase = true) }
    }

    // 打开抽屉时把当前章节滚进视野（上方留两条，让上下文可见）。
    // 只在首次组合时算一次 —— 抽屉里手动滚动后不该被拽回去。
    LaunchedEffect(shown, currentIndex) {
        if (shown.isEmpty()) return@LaunchedEffect
        // 与高亮规则一致：当前页的**首个**条目
        val at = shown.indexOfFirst { it.blockIndex >= currentIndex }
        val target = if (at >= 0) at else shown.lastIndex
        listState.scrollToItem((target - 2).coerceAtLeast(0))
    }

    AppSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = Sp.m)) {
            SheetHeader(
                title = "目录",
                badge = entries.size.toString() + " 个标题",
                onClose = onDismiss
            )
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "在目录中查找",
                focusRequester = focus,
                modifier = Modifier.padding(horizontal = Sp.l, vertical = Sp.xs)
            )

            // 当前所在章节 = 最后一个「起始块不晚于当前块」的标题。
            // 不能用 blockIndex == currentIndex 精确匹配：正文停在一个表格或代码块里时，
            // 那个位置根本不是任何标题的索引，精确匹配会导致整份目录一个都不高亮。
            // 高亮规则：当前页停留的**第一个**条目。
            //
            // 多个目录项常指向同一页（PDF 很常见）。
            // indexOfFirst 取该页首个条目，符合「我在这一页开头」的直觉；
            // 找不到同页条目时退到「最后一个不超过当前页的条目」，
            // 这样停在两章之间的正文里也能高亮到所属章节。
            val activeIndex = remember(shown, currentIndex) {
                val same = shown.indexOfFirst { it.blockIndex == currentIndex }
                if (same >= 0) {
                    same
                } else {
                    val at = shown.indexOfFirst { it.blockIndex > currentIndex }
                    when {
                        at > 0 -> at - 1
                        at == 0 -> 0
                        else -> shown.lastIndex
                    }
                }
            }

            if (shown.isEmpty()) {
                EmptyHint(
                    title = "没有匹配「" + query.trim() + "」的标题",
                    detail = "换个关键词，或清空后浏览完整目录"
                )
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = Sp.m, vertical = Sp.xs)
            ) {
                // key 必须全局唯一。
                // PDF 目录里多个标题会指向同一页（blockIndex 相同），
                // slug 又是空串，拼出来就是重复 key "6:"，
                // LazyColumn 直接抛 IllegalArgumentException 闪退（用户实测）。
                // 用列表下标保证唯一。
                itemsIndexed(shown, key = { i, entry -> i.toString() + ":" + entry.blockIndex }) { index, entry ->
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
            .heightIn(min = MinTouchTarget)
            .clip(Shapes.chip)
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .semantics {
                // 读屏要知道「这是目录项、当前在读的是它」
                role = Role.Tab
                if (active) contentDescription = entry.text + "，当前章节"
            }
            .padding(start = Sp.s + indent.dp, end = Sp.m, top = Sp.xs, bottom = Sp.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 一级标题加一条短竖线，方便扫读层级
        if (entry.level <= 2) {
            Box(
                Modifier
                    .width(2.dp)
                    .height(14.dp)
                    .clip(Shapes.pill)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
            )
            Spacer(Modifier.width(Sp.s))
        }
        Text(
            text = entry.text,
            fontSize = when (entry.level) {
                1 -> (Type.bodyLarge.value + 1f).sp
                2 -> Type.bodyLarge
                else -> Type.body
            },
            fontWeight = if (entry.level <= 2) FontWeight.Medium else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.moduread.md.SearchHit
import com.yg.moduread.md.SearchIndex

/**
 * 全文搜索面板。
 *
 * 搜索在 SearchIndex 上同步跑（一本书的纯文本约 60 KB，indexOf 是毫秒级），
 * 不需要协程也不需要在输入时防抖到奇怪的程度。
 */
@Composable
fun SearchSheet(
    index: SearchIndex?,
    onSelect: (SearchHit) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query, index) {
        hits = if (query.isBlank() || index == null) emptyList() else index.search(query)
    }

    AppSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索全文", fontSize = Type.bodyLarge) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
            )
            Spacer(Modifier.height(10.dp))

            when {
                index == null -> Hint("文档还没加载完")
                query.isBlank() -> Hint("输入至少 2 个字开始搜索")
                query.trim().length < SearchIndex.MIN_QUERY -> Hint("再输入一个字")
                hits.isEmpty() -> Hint("没有找到「" + query.trim() + "」")
                else -> {
                    Text(
                        text = if (hits.size >= 200) "命中超过 200 条，只显示前 200 条"
                        else hits.size.toString() + " 处命中",
                        fontSize = Type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyColumn(
                        Modifier.heightIn(max = 420.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(hits, key = { it.blockIndex.toString() + ":" + it.start }) { hit ->
                            HitRow(hit = hit, query = query.trim(), onClick = { onSelect(hit) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 26.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = Type.body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun HitRow(hit: SearchHit, query: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(Shapes.chip)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "§" + (hit.blockIndex + 1),
                fontSize = Type.label,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = snippet(hit, query),
                fontSize = Type.body,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 上下文里把关键词两侧截出固定窗口，避免长段落把列表撑爆。 */
private fun snippet(hit: SearchHit, query: String): String {
    val ctx = hit.context
    val len = query.length.coerceAtLeast(1)
    // context 是块开头的截断文本，命中点可能不在里面，这时退化为块首
    val at = ctx.indexOf(query, ignoreCase = true)
    return if (at < 0) {
        ctx.take(50)
    } else {
        val from = (at - 18).coerceAtLeast(0)
        val to = (at + len + 32).coerceAtMost(ctx.length)
        (if (from > 0) "…" else "") + ctx.substring(from, to) + (if (to < ctx.length) "…" else "")
    }
}

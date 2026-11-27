package com.yg.moduread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
 *
 * 外观改造：
 *  - 输入框换 SearchField，与目录面板同一套；
 *  - 命中片段里把查询词高亮（改造前是一片纯灰文字，看不出为什么这条命中）；
 *  - 命中行的「§N」原本只是版块序号，改成右侧的块号徽标，主信息让给片段；
 *  - 空态从 26dp 内边距的一行字换成 EmptyHint（标题 + 说明）。
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
        Column(Modifier.fillMaxWidth().padding(bottom = Sp.l)) {
            SheetHeader(title = "全文搜索", badge = if (hits.isEmpty()) null else hits.size.toString() + " 处命中", onClose = onDismiss)
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索全文",
                focusRequester = focus,
                modifier = Modifier.padding(horizontal = Sp.l, vertical = Sp.xs)
            )
            Spacer(Modifier.height(Sp.s))

            when {
                index == null -> EmptyHint(title = "文档还没加载完", detail = "解析完成后即可搜索")
                query.isBlank() -> EmptyHint(title = "输入关键词开始搜索", detail = "至少需要 " + SearchIndex.MIN_QUERY + " 个字")
                query.trim().length < SearchIndex.MIN_QUERY ->
                    EmptyHint(title = "再输入一个字", detail = "至少需要 " + SearchIndex.MIN_QUERY + " 个字")
                hits.isEmpty() -> EmptyHint(
                    title = "没有找到「" + query.trim() + "」",
                    detail = "换个说法，或到目录里按章节浏览"
                )
                else -> {
                    if (hits.size >= 200) {
                        Text(
                            text = "命中超过 200 条，只显示前 200 条",
                            fontSize = Type.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Sp.l, bottom = Sp.xs)
                        )
                    }
                    LazyColumn(
                        Modifier.heightIn(max = SheetListMaxHeight),
                        contentPadding = PaddingValues(horizontal = Sp.l, vertical = Sp.xs)
                    ) {
                        items(hits, key = { it.blockIndex.toString() + ":" + it.start }) { hit ->
                            HitRow(hit = hit, query = query.trim(), onClick = { onSelect(hit) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HitRow(hit: SearchHit, query: String, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val highlight = MaterialTheme.colorScheme.primaryContainer
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .padding(vertical = 2.dp)
            .clip(Shapes.chip)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = Sp.m, vertical = Sp.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = highlighted(snippet(hit, query), query, highlight, accent),
            fontSize = Type.body,
            lineHeight = (Type.body.value * 1.5f).sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(Sp.s))
        MetaBadge(text = "§" + (hit.blockIndex + 1))
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

/**
 * 把片段里的查询词涂成高亮底色。
 *
 * 改造前片段是一整块单色文字，同一段里命中三次也看不出来命中的是哪几个字 ——
 * 用户只能读完整句再自己找。这里逐处标记（不区分大小写），
 * 命中过多时最多标 20 处，避免整段变成色块。
 */
private fun highlighted(
    text: String,
    query: String,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color
) = buildAnnotatedString {
    if (query.isEmpty() || text.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    var cursor = 0
    var marked = 0
    while (cursor < text.length && marked < 20) {
        val at = text.indexOf(query, cursor, ignoreCase = true)
        if (at < 0) break
        append(text.substring(cursor, at))
        withStyle(SpanStyle(background = background, color = foreground, fontWeight = FontWeight.Medium)) {
            append(text.substring(at, at + query.length))
        }
        cursor = at + query.length
        marked++
    }
    if (cursor < text.length) append(text.substring(cursor))
}

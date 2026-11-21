package com.yg.moduread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.moduread.md.MdBlock

@OptIn(FlowPreview::class)
@Composable
fun ReaderScreen(
    doc: DocumentState,
    prefs: ReaderPrefs,
    onPickFile: () -> Unit,
    onFontChange: (Float) -> Unit,
    onOpenSettings: () -> Unit,
    onSaveProgress: (Int, Int) -> Unit = { _, _ -> },
    onOpenToc: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onAnchor: (String) -> Unit = {},
    /** 最近打开过的文件，空态页列出；点一项直接打开。 */
    recent: List<com.yg.moduread.data.RecentFile> = emptyList(),
    onOpenRecent: (String) -> Unit = {},
    onForgetRecent: (String) -> Unit = {},
    /** 一次性跳转目标（列表 item 下标）。消费后必须调 onJumpConsumed 清空。 */
    jumpTarget: Int? = null,
    onJumpConsumed: () -> Unit = {}
) {
    val bottomInsets = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
    val listState = rememberLazyListState()
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    // 一级标题已作为页首大标题呈现，正文里去掉同一份，避免重复
    val body = remember(doc.blocks) {
        when {
            doc.blocks.firstOrNull() is MdBlock.Heading -> doc.blocks.drop(1)
            else -> doc.blocks
        }
    }
    // 滚动后顶部栏淡入，停在顶部时隐藏，把版面还给文字
    val showBar by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 220
        }
    }

    // 文档换了（或首次解析完成）就恢复上次位置，每份文档只消费一次
    var restoredFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(doc.docUri, body.size) {
        val uri = doc.docUri ?: return@LaunchedEffect
        if (body.isEmpty() || restoredFor == uri) return@LaunchedEffect
        restoredFor = uri
        // +1：0 号位置是页首标题项
        val target = doc.restoreIndex.coerceIn(0, body.size)
        if (target > 0 || doc.restoreOffset > 0) {
            listState.scrollToItem(target, doc.restoreOffset)
        }
    }

    // 目录/锚点跳转：滚到目标后回报已消费，避免重复触发
    LaunchedEffect(jumpTarget, body.size) {
        val target = jumpTarget ?: return@LaunchedEffect
        if (body.isEmpty()) return@LaunchedEffect
        val clamped = target.coerceIn(0, body.size)
        listState.animateScrollToItem(clamped)
        onJumpConsumed()
    }

    // 位置变化后节流 1 秒落盘，避免每帧都写 SharedPreferences
    LaunchedEffect(listState, doc.docUri) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .debounce(1000)
            .collect { (index, offset) -> onSaveProgress(index, offset) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding())
    ) {
        when {
            doc.loading && doc.blocks.isEmpty() -> LoadingView()
            doc.blocks.isEmpty() -> EmptyView(
                error = doc.error,
                recent = recent,
                onPickFile = onPickFile,
                onOpenRecent = onOpenRecent,
                onForgetRecent = onForgetRecent
            )
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 56.dp,
                        bottom = 96.dp + insets.calculateBottomPadding()
                    )
                ) {
                    item(key = "title") {
                        DocTitle(doc = doc, fontSize = prefs.fontSizeSp)
                    }
                    // 一块一项：索引即块索引，目录/搜索/书签都能直接定位。
                    // key 用「序号 + 类型 + 正文前 24 字」，跨重组稳定，滚动不抖。
                    itemsIndexed(body, key = { i, block -> i.toString() + ':' + block.keyHint() }) { _, block ->
                        SelectionContainer(Modifier.fillMaxWidth()) {
                            MdBlockView(
                                block = block,
                                baseFontSize = prefs.fontSizeSp,
                                docUri = doc.docUri,
                                onAnchor = onAnchor
                            )
                        }
                    }
                }

                if (showBar) {
                    TopBar(
                        title = body.displayTitle(doc.title),
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
                ReaderBottomBar(
                    fontSize = prefs.fontSizeSp,
                    onFontChange = onFontChange,
                    onOpenSettings = onOpenSettings,
                    onOpenToc = onOpenToc,
                    onOpenSearch = onOpenSearch,
                    onPickFile = onPickFile,
                    bottomPadding = bottomInsets,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun DocTitle(doc: DocumentState, fontSize: Float) {
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Text(
            text = doc.blocks.displayTitle(doc.title),
            fontSize = (fontSize * 1.85f).sp,
            lineHeight = (fontSize * 1.85f * 1.3f).sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .width(44.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun TopBar(title: String, modifier: Modifier) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(
                text = title,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        )
    }
}

@Composable
private fun ReaderBottomBar(
    fontSize: Float,
    onFontChange: (Float) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenSearch: () -> Unit,
    onPickFile: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier
) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp + bottomPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PillButton(text = "A-", onClick = { onFontChange(fontSize - ReaderPrefs.FONT_STEP) })
            PillButton(text = "A+", onClick = { onFontChange(fontSize + ReaderPrefs.FONT_STEP) })
            Spacer(Modifier.size(4.dp))
            PillButton(text = "目录", onClick = onOpenToc)
            PillButton(text = "搜索", onClick = onOpenSearch)
            PillButton(text = "设置", onClick = onOpenSettings)
            Spacer(Modifier.weight(1f))
            PillButton(text = "换文件", onClick = onPickFile)
        }
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text = text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun EmptyView(
    error: String?,
    recent: List<com.yg.moduread.data.RecentFile>,
    onPickFile: () -> Unit,
    onOpenRecent: (String) -> Unit,
    onForgetRecent: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "墨读",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = error ?: "打开一个本地 .md 文件开始阅读",
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
    file: com.yg.moduread.data.RecentFile,
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
        // 长按才能移除太隐蔽，这里给一个显式的「×」
        Text(
            text = "×",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onForget)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
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

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
import androidx.compose.runtime.derivedStateOf
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

@Composable
fun ReaderScreen(
    doc: DocumentState,
    prefs: ReaderPrefs,
    onPickFile: () -> Unit,
    onFontChange: (Float) -> Unit,
    onOpenSettings: () -> Unit
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
    // 长文档分组：40 个块一组，LazyColumn 只为可见组创建布局节点
    val chunks = remember(body) { body.chunked(40) }
    // 滚动后顶部栏淡入，停在顶部时隐藏，把版面还给文字
    val showBar by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 220
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding())
    ) {
        when {
            doc.loading && doc.blocks.isEmpty() -> LoadingView()
            doc.blocks.isEmpty() -> EmptyView(doc.error, onPickFile)
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
                    itemsIndexed(chunks) { _, chunk ->
                        Column(Modifier.fillMaxWidth()) {
                            chunk.forEach { block ->
                                SelectionContainer(Modifier.fillMaxWidth()) {
                                    MdBlockView(block, prefs.fontSizeSp, docUri = doc.docUri)
                                }
                            }
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
private fun EmptyView(error: String?, onPickFile: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
        }
    }
}

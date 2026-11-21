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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.yg.moduread.R
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
import androidx.compose.ui.graphics.graphicsLayer
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

    // 字号浮层的开合状态。放这里而不是每个按钮里，切换文档时保持用户的选择
    var fontPanelOpen by remember { mutableStateOf(false) }

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
        // 空态 → 文档的切换用透明度过渡，避免 Crossfade（animation 模块）
        val contentAlpha by animateFloatAsState(
            targetValue = if (doc.blocks.isEmpty()) 0f else 1f,
            animationSpec = tween(200),
            label = "contentAlpha"
        )

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
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = contentAlpha },
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

                // 顶栏淡入淡出：用 animateFloatAsState 而不是 AnimatedVisibility，
                // 后者会把 androidx.compose.animation 整包拖进 dex
                val topBarAlpha by animateFloatAsState(
                    targetValue = if (showBar) 1f else 0f,
                    animationSpec = tween(150),
                    label = "topBarAlpha"
                )
                if (topBarAlpha > 0.01f) {
                    TopBar(
                        title = body.displayTitle(doc.title),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .graphicsLayer { alpha = topBarAlpha }
                    )
                }
                ReaderBottomBar(
                    fontPanelOpen = fontPanelOpen,
                    onToggleFontPanel = { fontPanelOpen = !fontPanelOpen },
                    onOpenSettings = onOpenSettings,
                    onOpenToc = onOpenToc,
                    onOpenSearch = onOpenSearch,
                    onPickFile = onPickFile,
                    bottomPadding = bottomInsets,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // 字号浮层：从底部栏之上滑出，拖动即时生效。
                // 用 animation-core 的 animateFloatAsState + graphicsLayer 而不是
                // AnimatedVisibility —— 后者会把整个 androidx.compose.animation
                // 模块拖进包体（实测约 +50 KB dex），而这个淡入淡出用 core 就够。
                val panelAlpha by animateFloatAsState(
                    targetValue = if (fontPanelOpen) 1f else 0f,
                    animationSpec = tween(140),
                    label = "fontPanelAlpha"
                )
                val panelShift by animateFloatAsState(
                    targetValue = if (fontPanelOpen) 0f else 12f,
                    animationSpec = tween(160),
                    label = "fontPanelShift"
                )
                if (fontPanelOpen || panelAlpha > 0.01f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 52.dp + bottomInsets)
                            .graphicsLayer { alpha = panelAlpha; translationY = panelShift }
                    ) {
                        FontPanel(
                            fontSize = prefs.fontSizeSp,
                            onFontChange = onFontChange,
                            onDismiss = { fontPanelOpen = false }
                        )
                    }
                }
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

/**
 * 底部栏：四个等距图标 + 一个低频项收进「⋯」。
 *
 * 改造前这里是六个同款文字胶囊（A- A+ 目录 搜索 设置 ... 换文件），
 * 没有主次也没有图标 —— 密集却没有信息量。现在：
 *  - 图标 24dp、触摸区 48dp，四个按钮视觉重量一致
 *  - 字号（低频）改成点「Aa」弹浮层，腾出两个按钮位
 *  - 「换文件」收进「⋯」，主栏从 6 项降到 4 项
 */
@Composable
private fun ReaderBottomBar(
    fontPanelOpen: Boolean,
    onToggleFontPanel: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenSearch: () -> Unit,
    onPickFile: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier
) {
    var moreOpen by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        // 发丝线：比改造前的 1dp outline(0.6f) 更淡，只做分隔不抢视线
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = Sp.s, vertical = Sp.xs)
                .padding(bottom = bottomPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarIcon(
                label = "目录",
                onClick = onOpenToc,
                modifier = Modifier.weight(1f),
                draw = { tint -> MiIcons.Toc(tint) }
            )
            BarIcon(
                label = "搜索",
                onClick = onOpenSearch,
                modifier = Modifier.weight(1f),
                draw = { tint -> MiIcons.Search(tint) }
            )
            BarIcon(
                label = "字号",
                onClick = onToggleFontPanel,
                active = fontPanelOpen,
                modifier = Modifier.weight(1f),
                draw = { tint -> FontGlyph(tint) }
            )
            BarIcon(
                label = "更多",
                onClick = { moreOpen = true },
                modifier = Modifier.weight(1f),
                draw = { tint -> MiIcons.More(tint) }
            )
        }

        // 「⋯」下拉：低频操作都放这儿，不再占用主栏
        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
            DropdownMenuItem(
                text = { Text("换一个文件", fontSize = Type.bodyLarge) },

                onClick = { moreOpen = false; onPickFile() }
            )
            DropdownMenuItem(
                text = { Text("阅读设置", fontSize = Type.bodyLarge) },

                onClick = { moreOpen = false; onOpenSettings() }
            )
        }
    }
}

/** 底部栏图标按钮：图标 24dp，触摸区撑到 48dp 高，点击标题可无障碍朗读。 */
@Composable
private fun BarIcon(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    draw: @Composable (Color) -> Unit
) {
    val tint = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .height(MinTouchTarget)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        draw(tint)
    }
}

/** 字号图标用「Aa」字样，比画两个三角形更像约定的 UI 语言。 */
@Composable
private fun FontGlyph(tint: Color) {
    Text(
        text = MiIcons.FONT_LABEL,
        fontSize = Type.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = tint
    )
}

/**
 * 字号浮层。改造前是两个常驻按钮 A-/A+；
 * 调字号是低频操作（一本书 1~2 次），不值得占两个主栏位置。
 */
@Composable
private fun FontPanel(
    fontSize: Float,
    onFontChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        Modifier
            .padding(horizontal = Sp.l)
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = Sp.m, vertical = Sp.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "A",
            fontSize = Type.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = fontSize,
            onValueChange = onFontChange,
            valueRange = ReaderPrefs.MIN_FONT..ReaderPrefs.MAX_FONT,
            steps = (ReaderPrefs.MAX_FONT - ReaderPrefs.MIN_FONT).toInt() - 1,
            modifier = Modifier.weight(1f).padding(horizontal = Sp.s)
        )
        Text(
            text = "A",
            fontSize = Type.title,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(Sp.s))
        Text(
            text = fontSize.toInt().toString(),
            fontSize = Type.caption,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(18.dp)
        )
        Box(
            Modifier
                .size(32.dp)
                .clip(Shapes.pill)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            MiIcons.Close(MaterialTheme.colorScheme.onSurfaceVariant, 14.dp)
        }
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

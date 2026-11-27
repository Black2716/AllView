package com.yg.moduread.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.displayTitle
import com.yg.moduread.md.keyHint
import com.yg.moduread.md.plainText
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/**
 * 阅读器主界面：只负责**滚动编排**。
 *
 * 重构说明：外观部件（页首标题、顶栏、底栏、字号浮层、加载态）已拆到
 * ReaderChrome.kt，空态页拆到 EmptyView.kt。本文件保留的是有状态的部分：
 * 恢复位置、目录跳转、可见块上报、进度落盘节流、以及三种内容形态的分派。
 * 这些逻辑的每条注释都记录着一个真实踩过的偏移量 bug，因此一律保留。
 */
@OptIn(FlowPreview::class)
@Composable
fun ReaderScreen(
    doc: DocumentState,
    prefs: ReaderPrefs,
    onPickFile: () -> Unit,
    onFontChange: (Float) -> Unit,
    onOpenSettings: () -> Unit,
    onSaveProgress: (Int, Int) -> Unit = { _, _ -> },
    /** 当前可见块索引（用于目录高亮跟随），与落盘节流分开，要求即时。 */
    onVisibleBlock: (Int) -> Unit = {},
    onOpenToc: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    /** 长按某块正文（用于加书签）。参数是块索引。 */
    onLongPressBlock: (Int) -> Unit = {},
    /** PDF 页点击回调：把位图交给上层全屏查看。 */
    onOpenPdfViewer: (android.graphics.Bitmap, String) -> Unit = { _, _ -> },
    /** 所有链接点击（锚点、相对路径、外部 URL）。 */
    onLink: (String) -> Unit = {},
    /** 最近打开过的文件，空态页列出；点一项直接打开。 */
    recent: List<com.yg.moduread.data.RecentFile> = emptyList(),
    onOpenRecent: (String) -> Unit = {},
    onForgetRecent: (String) -> Unit = {},
    /** 一次性跳转目标（列表 item 下标）。消费后必须调 onJumpConsumed 清空。 */
    jumpTarget: Int? = null,
    onJumpConsumed: () -> Unit = {}
) {
    // 只取一次安全区：改造前这里连调两次 asPaddingValues()，
    // 又另有一个 insets 变量，同一个值有三种取法，容易改漏。
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val bottomInsets = insets.calculateBottomPadding()
    val listState = rememberLazyListState()
    // 一级标题已作为页首大标题呈现，正文里去掉同一份，避免重复
    val body = remember(doc.blocks) {
        when {
            doc.blocks.firstOrNull() is MdBlock.Heading -> doc.blocks.drop(1)
            else -> doc.blocks
        }
    }
    // 滚动后顶部栏淡入，停在顶部时隐藏，把版面还给文字。
    // 带 docUri 作 key：切换文档时列表位置会被重置，顶栏状态也要跟着归位。
    val showBar by remember(doc.docUri) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 220
        }
    }

    // 阅读进度：已滚过的块数占比。
    //
    // remember 必须带 body 作为 key。文档是异步加载的，首次组合时 body 还是空列表；
    // 若不加 key，闭包会一直捕获那个空列表，条件永远走 body.isEmpty() -> 0f，
    // 进度就卡死在 0%（这正是之前的 bug）。
    // PPTX 走 SlideDeckView，列表项是「页」而不是块；
    // 用 body.size 做分母会让进度永远停在很小的值（实测卡在 4%）。
    // 分母必须取实际的列表项总数。
    // 分母必须在 derivedStateOf 内部实时读取。
    // doc.slides 是异步就绪的：首次组合时 itemCount 为 0，
    // 若把它当作 remember 的 key，闭包会先捕获 0 并在数据到达后仍不更新，
    // 进度就永远停在 0%（实测日志确认 count=0）。
    val progress by remember(doc.docUri) {
        derivedStateOf {
            val count = doc.slides?.pages?.size ?: body.size
            if (count <= 0) {
                0f
            } else {
                val idx = listState.firstVisibleItemIndex
                val off = listState.firstVisibleItemScrollOffset
                val base = idx.toFloat() / count.toFloat()
                val within = off.toFloat() / 2000f / count.toFloat()
                (base + within).coerceIn(0f, 1f)
            }
        }
    }

    // 顶栏标题跟随当前章节，而不是固定显示首个标题。
    //
    // 取「起始块不晚于当前可见块」的最后一个标题 —— 与目录抽屉的高亮规则一致。
    // 用 exact 相等匹配会在正文停在表格/代码块时失效（历史上踩过这个坑）。
    // 列表 0 号是页首大标题项，所以可见块下标要减 1 才是 body 下标。
    val currentTitle by remember(doc.docUri, body) {
        derivedStateOf {
            val index = listState.firstVisibleItemIndex - 1
            if (index < 0) return@derivedStateOf body.displayTitle(doc.title)
            val heading = body.subList(0, (index + 1).coerceAtMost(body.size))
                .lastOrNull { it is MdBlock.Heading } as? MdBlock.Heading
            heading?.let { it.content.plainText().trim().takeIf { t -> t.isNotEmpty() } }
                ?: body.displayTitle(doc.title)
        }
    }

    // 文档换了（或首次解析完成）就恢复上次位置，每份文档只消费一次
    var restoredFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(doc.docUri, body.size) {
        val uri = doc.docUri ?: return@LaunchedEffect
        if (body.isEmpty() || restoredFor == uri) return@LaunchedEffect
        restoredFor = uri
        // +1：列表第 0 项是文档标题，正文从第 1 项起。
        // 少这一格会让恢复位置整体偏一页。
        val target = (doc.restoreIndex + 1).coerceIn(0, body.size)
        if (target > 0 || doc.restoreOffset > 0) {
            listState.scrollToItem(target, doc.restoreOffset)
        }
    }

    // 目录/锚点跳转：滚到目标后回报已消费，避免重复触发
    LaunchedEffect(jumpTarget, body.size) {
        val target = jumpTarget ?: return@LaunchedEffect
        if (body.isEmpty()) return@LaunchedEffect
        // +1：列表第 0 项是文档标题（DocTitle），正文块从第 1 项开始。
        // 之前直接用 blockIndex 当下标，整体少了一格，
        // 于是目标页总是偏上/被相邻页挤到下半屏（用户实测：靠着低栏）。
        val clamped = (target + 1).coerceIn(0, body.size)
        listState.scrollToItem(clamped)
        // 立即上报一次可见块。
        // snapshotFlow 只在值「变化」时发射：跳转前后若停在同一下标
        //（例如从第 9 页跳到同页的另一条目录项），它不会触发，
        // 高亮就还停在旧条目上（用户实测「差一格」）。
        onVisibleBlock(clamped - 1)
        onJumpConsumed()
    }

    // 字号浮层的开合状态。放这里而不是每个按钮里，切换文档时保持用户的选择
    var fontPanelOpen by remember { mutableStateOf(false) }

    // 可见块即时上报：目录抽屉打开时用它高亮当前章节。
    // 与落盘的 1 秒节流分开 —— 高亮要跟手，写盘不需要。
    LaunchedEffect(listState, body.size) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                // 列表第 0 项是 DocTitle，正文块从第 1 项开始；
                // TocEntry.blockIndex 用的是正文块下标。
                // 不减这一格，目录高亮会整体偏移一页（用户实测）。
                onVisibleBlock((index - 1).coerceIn(0, body.size))
            }
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
    ) {
        // 空态 → 文档的切换用透明度过渡，避免 Crossfade（animation 模块）
        val contentAlpha by animateFloatAsState(
            targetValue = if (doc.blocks.isEmpty()) 0f else 1f,
            animationSpec = tween(200),
            label = "contentAlpha"
        )

        when {
            doc.loading && doc.blocks.isEmpty() -> LoadingView()
            // PPTX 且成功生成 SVG：按 16:9 逐页显示。
            // WebView 可见时才会正常绘制 —— 离屏截图在 Android 上不可靠。
            doc.slides != null -> {
                SlideDeckView(
                    pages = doc.slides.pages,
                    aspectRatio = doc.slides.aspectRatio,
                    modifier = Modifier.graphicsLayer { alpha = contentAlpha },
                    // 复用阅读器的列表状态：进度百分比与顶栏标题都读它，
                    // 之前用自己的 rememberLazyListState，于是进度永远停在初始值。
                    listState = listState,
                    bottomInset = insets.calculateBottomPadding()
                )
            }
            doc.blocks.isEmpty() -> HomeScreen(
                error = doc.error,
                recent = recent,
                onPickFile = onPickFile,
                onOpenRecent = onOpenRecent,
                onForgetRecent = onForgetRecent
            )
            // DOCX / ODT 也走流式渲染。
            //
            // 曾尝试两条「整页」路线，都因实际问题回退：
            //  1. 自绘分页（逐块测高）—— 每次布局全量重测，长文档滑动明显卡顿。
            //  2. WebView 渲染 HTML —— 会盖住顶栏/进度条/底栏，分页观感也与 PC 端差距大。
            // 结论：先把内容正确、流畅地读出来更重要；版式还原留待以后单独解决。
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = contentAlpha },
                    contentPadding = PaddingValues(
                        // 左右留白取令牌，改 ReaderGutter 即全站生效
                        start = ReaderGutter,
                        end = ReaderGutter,
                        // 顶栏是叠在内容上的（滚动后才淡入），必须预留它的完整高度，
                        // 否则首行文字会被顶栏压住。改造前这里写死 56.dp，
                        // 与 ReaderTopBarHeight 是两个各自维护的数。
                        top = ReaderTopBarHeight,
                        bottom = ReaderBottomContentInset + insets.calculateBottomPadding()
                    )
                ) {
                    item(key = "title") {
                        DocTitle(doc = doc, fontSize = prefs.fontSizeSp)
                    }
                    // 一块一项：索引即块索引，目录/搜索/书签都能直接定位。
                    // key 用「序号 + 类型 + 正文前 24 字」，跨重组稳定，滚动不抖。
                    itemsIndexed(body, key = { i, block -> i.toString() + ':' + block.keyHint() }) { index, block ->
                        SelectionContainer(Modifier.fillMaxWidth()) {
                            MdBlockView(
                                block = block,
                                baseFontSize = prefs.fontSizeSp,
                                docUri = doc.docUri,
                                onAnchor = onLink,
                                // 块索引要加 1：body 是 blocks.drop(1)，而书签按原始块下标存
                                onLongPress = { onLongPressBlock(index + 1) },
                                onOpenPdfViewer = onOpenPdfViewer
                            )
                        }
                    }
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
                title = currentTitle,
                progress = progress,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer { alpha = topBarAlpha }
            )
        }
        // 进度线的绘制已并入 ReaderBottomBar 顶部轨道（一条即可，两处会画两条）
        ReaderBottomBar(
            fontPanelOpen = fontPanelOpen,
            onToggleFontPanel = { fontPanelOpen = !fontPanelOpen },
            onOpenSettings = onOpenSettings,
            onOpenToc = onOpenToc,
            onOpenSearch = onOpenSearch,
            searchEnabled = doc.format.supportsSearch && doc.hasTextLayer,
            onOpenBookmarks = onOpenBookmarks,
            onPickFile = onPickFile,
            progress = progress,
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
                    .padding(bottom = BottomBarHeight + bottomInsets)
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

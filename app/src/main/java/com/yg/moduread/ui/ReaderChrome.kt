package com.yg.moduread.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.moduread.format.DocFormat
import com.yg.moduread.md.displayTitle

/**
 * 阅读界面的「外壳」部件：页首大标题、顶栏、底栏、字号浮层、加载态。
 *
 * 从 ReaderScreen.kt 拆出的理由：ReaderScreen 的主体职责是**滚动编排**
 * （恢复位置、跳转、可见块上报、进度落盘节流），但这些只占它一半篇幅，
 * 另一半是「长什么样」。两部分改动频率与原因完全不同 ——
 * 调整图标间距不该动到 scrollToItem 的 +1 偏移计算，反之亦然。
 * 拆开后 ReaderScreen 只剩编排逻辑，本文件只描述外观与交互。
 *
 * 行为未变：组件签名、可见性（原 private，现 internal 以便跨文件引用）、
 * 尺寸令牌与文案都保持等价。
 */

/**
 * 顶栏总高度。
 *
 * 改造前 ReaderScreen 的 contentPadding.top 写死 56.dp，而 TopBar 实际只有
 * 约 30dp 高（caption 字 + 6dp 上下内边距 + 1dp 线）。也就是说正文上方
 * 有 26dp 的空白被「预留」给一个不存在的栏 —— 滚动起来顶部像缺了一块。
 * 现在顶栏自身就是 56dp，正文留白与栏高由同一个常量给出。
 */
internal val ReaderTopBarHeight = 56.dp

/**
 * 底栏总高度（含 1dp 发丝线与内边距）。
 *
 * 存在的理由：进度线、字号浮层都要「贴在底栏正上方」，
 * 改造前三处各写了一个 52.dp —— 它其实是从 MinTouchTarget(48) 手算出来的，
 * 谁调底栏高度就会漏改另一处，进度线立刻压到按钮上。
 * 现在底栏自身与浮层引用同一个值，改这里即全站一致。
 */
internal val BottomBarHeight = 52.dp

/** 正文滚动区底部留白：底栏高度再加一档，保证最后一行不被浮层遮住。 */
internal val ReaderBottomContentInset = 96.dp

/** 文档格式的展示名。用于页首元信息与首页卡片徽标。 */
internal fun formatLabel(format: DocFormat): String = when (format) {
    DocFormat.MARKDOWN -> "Markdown"
    DocFormat.TXT -> "纯文本"
    DocFormat.LOG -> "日志"
    DocFormat.CSV -> "CSV"
    DocFormat.JSON -> "JSON"
    DocFormat.XML -> "XML"
    DocFormat.YAML -> "YAML"
    DocFormat.INI -> "配置"
    DocFormat.SUBTITLE -> "字幕"
    DocFormat.RTF -> "RTF"
    DocFormat.EPUB -> "EPUB"
    DocFormat.DOCX -> "Word"
    DocFormat.ODT -> "ODT"
    DocFormat.HTML -> "HTML"
    DocFormat.XLSX -> "Excel"
    DocFormat.PPTX -> "幻灯片"
    DocFormat.DOC_LEGACY -> "旧版 Word"
    DocFormat.PDF -> "PDF"
    DocFormat.CODE -> "代码"
    DocFormat.UNSUPPORTED -> "未知"
}

/** 千分位字数，首页与页首都用同一种写法。 */
internal fun formatCharCount(count: Int): String {
    if (count <= 0) return ""
    val s = count.toString()
    val sb = StringBuilder()
    for ((i, c) in s.withIndex()) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return sb.toString() + " 字"
}

/**
 * 页首大标题：文档标题 + 一条短下划线 + 一行元信息。
 *
 * 元信息（格式 · 字数 / 页数）是改造前没有的 —— 阅读时长文档却不知道
 * 自己打开的是 PDF 还是 Markdown、有多少字，只能靠记忆。
 * 这里只展示已有字段，不引入任何新数据来源。
 */
@Composable
internal fun DocTitle(doc: DocumentState, fontSize: Float) {
    Column(Modifier.fillMaxWidth().padding(bottom = Sp.l)) {
        Text(
            text = doc.blocks.displayTitle(doc.title),
            fontSize = (fontSize * 1.85f).sp,
            lineHeight = (fontSize * 1.85f * 1.3f).sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(Sp.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(44.dp)
                    .height(3.dp)
                    .clip(Shapes.pill)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(Sp.m))
            Text(
                text = buildString {
                    append(formatLabel(doc.format))
                    val pages = doc.slides?.pages?.size ?: doc.pageCount
                    if (doc.format == DocFormat.PDF && pages > 0) {
                        append(" · ")
                        append(pages)
                        append(" 页")
                    } else {
                        val chars = formatCharCount(doc.charCount)
                        if (chars.isNotEmpty()) {
                            append(" · ")
                            append(chars)
                        }
                    }
                },
                fontSize = Type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 顶部栏：当前章节 + 阅读进度。
 *
 * 改造前是一个 30dp 高的裸 Row：左侧 caption 章节名、右侧「37%」，
 * 底下一条 1dp 线。它飘在正文上方、高度与为它预留的 56dp 不一致，
 * 滚动时看起来像正文被什么东西压掉一块。
 *
 * 现在：固定 56dp，章节名与进度各占一侧，中间留空，
 * 底边发丝线与正文之间正好是设计好的呼吸距离。
 */
@Composable
internal fun TopBar(
    title: String,
    progress: Float,
    modifier: Modifier,
    topPadding: Dp = 0.dp
) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                // 顶栏是叠在内容上的浮层，必须自己让出状态栏：
                // 否则章节名会和状态栏的时间/电量印在同一行上。
                .padding(top = topPadding)
                .height(ReaderTopBarHeight)
                .padding(horizontal = ReaderGutter),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = Type.body,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(Sp.m))
            Text(
                text = (progress * 100).toInt().toString() + "%",
                fontSize = Type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HairlineDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * 底部栏：四个等距图标 + 一个低频项收进「⋯」。
 *
 * 改造前这里是六个同款文字胶囊（A- A+ 目录 搜索 设置 ... 换文件），
 * 没有主次也没有图标 —— 密集却没有信息量。现在：
 *  - 图标 22dp、触摸区 48dp，四个按钮视觉重量一致
 *  - 字号（低频）改成点「Aa」弹浮层，腾出两个按钮位
 *  - 「换文件」收进「⋯」，主栏从 6 项降到 4 项
 *
 * 2026-11：顶部的 1dp 发丝线改成「进度轨道」——条本身就表示读到哪里，
 * 不再需要另画一条 2dp 进度线叠在底栏上方（原来那条线会盖住底栏的发丝线，
 * 滚动时两者颜色不同，边缘看起来在闪）。
 */
@Composable
internal fun ReaderBottomBar(
    fontPanelOpen: Boolean,
    onToggleFontPanel: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenSearch: () -> Unit,
    searchEnabled: Boolean = true,
    onOpenBookmarks: () -> Unit,
    onPickFile: () -> Unit,
    progress: Float,
    bottomPadding: Dp,
    modifier: Modifier
) {
    var moreOpen by remember { mutableStateOf(false) }
    val fill by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(Motion.base),
        label = "bottomProgress"
    )

    Column(modifier.fillMaxWidth().height(BottomBarHeight + bottomPadding)) {
        // 进度轨道：底轨用 outlineVariant，填充用 primary。
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fill)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
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
            // PDF 与无文字层的扫描件没有搜索索引，入口置灰而不是静默失效
            BarIcon(
                label = "搜索",
                onClick = { if (searchEnabled) onOpenSearch() },
                modifier = Modifier.weight(1f),
                dimmed = !searchEnabled,
                draw = { tint -> MiIcons.Search(tint) }
            )
            BarIcon(
                label = "字号",
                onClick = onToggleFontPanel,
                active = fontPanelOpen,
                modifier = Modifier.weight(1f),
                draw = { tint -> FontGlyph(tint) }
            )
            // 「⋯」下拉必须锚定到这个按钮本身。
            // 直接放在 Column 里的话，锚点是整个 Column 的左上角，菜单会贴屏幕最左边。
            Box(Modifier.weight(1f)) {
                BarIcon(
                    label = "更多",
                    onClick = { moreOpen = true },
                    active = moreOpen,
                    modifier = Modifier.fillMaxWidth(),
                    draw = { tint -> MiIcons.More(tint) }
                )

                // 低频操作都放这儿，不占用主栏
                // material3 1.2 的 DropdownMenu 没有 shape/containerColor 参数
                //（那是 1.3 才加的），外观沿用 M3 默认的 surfaceContainer 容器色。
                DropdownMenu(
                    expanded = moreOpen,
                    onDismissRequest = { moreOpen = false }
                ) {
                    MenuRow("换一个文件", { MiIcons.Folder(it, 18.dp) }) {
                        moreOpen = false; onPickFile()
                    }
                    MenuRow("书签", { MiIcons.Bookmark(it, 18.dp) }) {
                        moreOpen = false; onOpenBookmarks()
                    }
                    MenuRow("阅读设置", { MiIcons.Tune(it, 18.dp) }) {
                        moreOpen = false; onOpenSettings()
                    }
                }
            }
        }
    }
}

/** 下拉菜单项：图标 + 文案，48dp 高。 */
@Composable
private fun MenuRow(
    label: String,
    draw: @Composable (Color) -> Unit,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label, fontSize = Type.bodyLarge) },
        leadingIcon = { draw(MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = MinTouchTarget)
    )
}

/**
 * 底部栏图标按钮：图标 22dp，触摸区撑到 48dp，点击标题可无障碍朗读。
 *
 * 选中态（字号浮层展开、更多菜单打开）除了换颜色，还加一层淡色胶囊底 ——
 * 改造前只改 tint，在深色主题里「灰」和「蓝」的差别很小，
 * 用户看不出这个按钮正处于展开状态。
 */
@Composable
private fun BarIcon(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    /** 置灰：功能当前不可用（如 PDF 无搜索索引）。 */
    dimmed: Boolean = false,
    draw: @Composable (Color) -> Unit
) {
    val tint = when {
        dimmed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alpha.disabled)
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier.height(MinTouchTarget),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(width = 56.dp, height = 44.dp)
                .clip(Shapes.card)
                .background(
                    if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = Alpha.selected)
                    else Color.Transparent
                )
                .clickable(onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            draw(tint)
        }
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
 *
 * 2026-11：两端换成「A− / A+」两个真正的按钮（点一下走一档），
 * 中间仍是滑块 —— 滑块适合大范围调，按钮适合「再大一点点」。
 * 两个按钮都调 onFontChange，语义与滑块完全一致，不新增状态。
 */
@Composable
internal fun FontPanel(
    fontSize: Float,
    onFontChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    // fillMaxWidth 之后 Box 的 align 不再居中生效，内容会贴在左侧 padding 边界上。
    // 用 wrapContentWidth + 水平居中的 Row 让胶囊整体居中。
    Row(
        Modifier
            .wrapContentWidth()
            .clip(Shapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = Sp.xs, end = Sp.xs, top = Sp.xs, bottom = Sp.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        StepFontButton(
            description = "减小字号",
            enabled = fontSize > ReaderPrefs.MIN_FONT,
            draw = { tint -> MiIcons.FontMinus(tint, 16.dp) },
            onClick = {
                onFontChange((fontSize - ReaderPrefs.FONT_STEP).coerceAtLeast(ReaderPrefs.MIN_FONT))
            }
        )
        Slider(
            value = fontSize,
            onValueChange = onFontChange,
            valueRange = ReaderPrefs.MIN_FONT..ReaderPrefs.MAX_FONT,
            steps = (ReaderPrefs.MAX_FONT - ReaderPrefs.MIN_FONT).toInt() - 1,
            modifier = Modifier.width(148.dp).padding(horizontal = Sp.xs)
        )
        StepFontButton(
            description = "增大字号",
            enabled = fontSize < ReaderPrefs.MAX_FONT,
            draw = { tint -> MiIcons.FontPlus(tint, 16.dp) },
            onClick = {
                onFontChange((fontSize + ReaderPrefs.FONT_STEP).coerceAtMost(ReaderPrefs.MAX_FONT))
            }
        )
        Spacer(Modifier.width(Sp.xs))
        // 数值固定 18dp 宽 + 右对齐：两位数和一位数切换时不抖动
        Text(
            text = fontSize.toInt().toString(),
            fontSize = Type.caption,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(20.dp)
        )
        IconAction(
            description = "关闭字号面板",
            onClick = onDismiss,
            draw = { tint -> MiIcons.Close(tint, 14.dp) }
        )
    }
}

/** 字号步进按钮：40dp 触摸区（浮层内空间有限，仍高于原 32dp 的关闭键）。 */
@Composable
private fun StepFontButton(
    description: String,
    enabled: Boolean,
    draw: @Composable (Color) -> Unit,
    onClick: () -> Unit
) {
    val tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Alpha.disabled)
    Box(
        Modifier
            .size(40.dp)
            .clip(Shapes.pill)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        draw(tint)
    }
}

/** 首帧骨架：文档还在解析时的居中转圈 + 一行说明。 */
@Composable
internal fun LoadingView() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(Sp.l))
        Text(
            text = "正在解析文档",
            fontSize = Type.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

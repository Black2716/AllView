package com.yg.moduread.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.luminance
import com.yg.moduread.md.CodeHighlighter
import com.yg.moduread.md.MdAlign
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdInline
import com.yg.moduread.md.MdTableCell
import com.yg.moduread.md.TokenKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 正文行高：1.75 是中文长文阅读的舒适区间。 */
private const val LINE_HEIGHT_RATIO = 1.75f

/** 宽表格（>4 列）的每列固定宽度。所有行共用，保证上下对齐。 */
private const val COLUMN_WIDTH_DP = 140

@Composable
fun MdBlockView(
    block: MdBlock,
    baseFontSize: Float,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    docUri: String? = null,
    /** 链接点击：上层按 [MdDocResolver] 分派锚点 / 兄弟文档 / 外部浏览器。 */
    onAnchor: (String) -> Unit = {},
    /** 长按正文：用于加书签。参数是块自身（上层据此取摘要）。 */
    onLongPress: (() -> Unit)? = null,
    /** PDF 页点击回调。 */
    onOpenPdfViewer: (android.graphics.Bitmap, String) -> Unit = { _, _ -> }
) {
    when (block) {
        is MdBlock.Heading -> HeadingView(block, baseFontSize, modifier, onAnchor)
        is MdBlock.Para -> MdText(
            text = buildInline(block.content),
            fontSize = baseFontSize.sp,
            lineHeight = (baseFontSize * LINE_HEIGHT_RATIO).sp,
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = if (compact) 2.dp else baseFontSize.dp * 0.7f),
            onAnchor = onAnchor,
            onLongPress = onLongPress
        )

        is MdBlock.Quote -> QuoteView(block, baseFontSize, modifier, docUri, onAnchor)
        is MdBlock.ListBlock -> ListView(block, baseFontSize, modifier, docUri, onAnchor)
        is MdBlock.CodeBlock -> CodeView(block, baseFontSize, modifier)
        is MdBlock.Table -> TableView(block, baseFontSize, modifier)
        is MdBlock.Math -> MathView(
            latex = block.latex,
            baseFontSize = baseFontSize,
            display = block.display,
            modifier = modifier
        )

        is MdBlock.Image -> ImageView(block, modifier, docUri)
        is MdBlock.EmbeddedImage -> EmbeddedImageView(block, modifier)
        is MdBlock.PdfPage -> PdfPageSlot(block, docUri, modifier, onOpenPdfViewer)
        is MdBlock.Rule -> RuleLine(modifier)
    }
}

@Composable
private fun RuleLine(modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline)
    )
}

@Composable
private fun HeadingView(
    block: MdBlock.Heading,
    baseFontSize: Float,
    modifier: Modifier,
    onAnchor: (String) -> Unit
) {
    // 层级靠「字号 + 行高 + 字重 + 段前距」四件套区分，
    // 而不是只缩字号 —— 改造前 H2 与 H3 只差 0.22 倍，视觉上分不开。
    val spec = when (block.level) {
        1 -> HeadingSpec(1.75f, 1.25f, FontWeight.Bold, 28.dp, 12.dp)
        2 -> HeadingSpec(1.40f, 1.30f, FontWeight.SemiBold, 24.dp, 10.dp)
        3 -> HeadingSpec(1.18f, 1.35f, FontWeight.SemiBold, 18.dp, 8.dp)
        4 -> HeadingSpec(1.05f, 1.40f, FontWeight.Medium, 14.dp, 6.dp)
        else -> HeadingSpec(1.00f, 1.45f, FontWeight.Medium, 12.dp, 4.dp)
    }
    val secondary = block.level >= 5

    Column(
        modifier
            .fillMaxWidth()
            .padding(top = spec.topPad, bottom = spec.bottomPad)
    ) {
        MdText(
            text = buildInline(block.content),
            fontSize = (baseFontSize * spec.scale).sp,
            lineHeight = (baseFontSize * spec.scale * spec.lineHeightScale).sp,
            fontWeight = spec.weight,
            color = if (secondary) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onBackground,
            onAnchor = onAnchor
        )
        // H1 用强调色短横收尾；H2 用通栏发丝线，把大节和亲子节分开
        when (block.level) {
            1 -> {
                Spacer(Modifier.height(Sp.m))
                Box(
                    Modifier
                        .width(40.dp)
                        .height(3.dp)
                        .clip(Shapes.chip)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            2 -> {
                Spacer(Modifier.height(Sp.s))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}

/** 标题排版参数：字号倍率、行高倍率、字重、段前、段后。 */
private data class HeadingSpec(
    val scale: Float,
    val lineHeightScale: Float,
    val weight: FontWeight,
    val topPad: androidx.compose.ui.unit.Dp,
    val bottomPad: androidx.compose.ui.unit.Dp
)

@Composable
private fun QuoteView(
    block: MdBlock.Quote,
    baseFontSize: Float,
    modifier: Modifier,
    docUri: String?,
    onAnchor: (String) -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 6.dp)
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(Shapes.chip)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = Alpha.decorative))
        )
        Column(Modifier.padding(start = Sp.l).fillMaxWidth()) {
            block.children.forEach { child ->
                MdBlockView(
                    block = child,
                    baseFontSize = baseFontSize * 0.97f,
                    compact = true,
                    docUri = docUri,
                    onAnchor = onAnchor
                )
            }
        }
    }
}

@Composable
private fun ListView(
    block: MdBlock.ListBlock,
    baseFontSize: Float,
    modifier: Modifier,
    docUri: String?,
    onAnchor: (String) -> Unit
) {
    Column(modifier.fillMaxWidth().padding(bottom = baseFontSize.dp * 0.4f)) {
        block.items.forEachIndexed { index, item ->
            // 任务项用方框代替项目符号；其它项保持原样
            val task = block.taskStates.getOrNull(index)
            Row(Modifier.fillMaxWidth().padding(vertical = if (block.tight) 1.dp else 3.dp)) {
                if (task != null) {
                    TaskCheckbox(
                        checked = task,
                        baseFontSize = baseFontSize,
                        modifier = Modifier.widthIn(min = baseFontSize.dp * 1.5f)
                    )
                } else {
                    Text(
                        text = if (block.ordered) (block.start + index).toString() + "." else "•",
                        fontSize = baseFontSize.sp,
                        lineHeight = (baseFontSize * LINE_HEIGHT_RATIO).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(min = baseFontSize.dp * 1.5f)
                    )
                }
                Column(Modifier.fillMaxWidth().padding(start = 6.dp)) {
                    item.forEach { child ->
                        MdBlockView(
                            block = child,
                            baseFontSize = baseFontSize,
                            compact = true,
                            docUri = docUri,
                            onAnchor = onAnchor
                        )
                    }
                }
            }
        }
    }
}

/** 代码高亮配色。深浅色各一套，都从现有主题色衍生，不新增调色板文件。 */
@Composable
private fun syntaxColors(): Map<TokenKind, Color> {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) mapOf(
        TokenKind.PLAIN to Color(0xFFD6D6D6),
        TokenKind.KEYWORD to Color(0xFF7AA2F7),
        TokenKind.STRING to Color(0xFF9ECE6A),
        TokenKind.COMMENT to Color(0xFF6B7089),
        TokenKind.NUMBER to Color(0xFFE0AF68),
        TokenKind.FUNCTION to Color(0xFF7DCFFF)
    ) else mapOf(
        TokenKind.PLAIN to Color(0xFF24292F),
        TokenKind.KEYWORD to Color(0xFF2F6FEB),
        TokenKind.STRING to Color(0xFF116329),
        TokenKind.COMMENT to Color(0xFF8C959F),
        TokenKind.NUMBER to Color(0xFF953800),
        TokenKind.FUNCTION to Color(0xFF0550AE)
    )
}

/**
 * 任务列表的勾选框。只读 —— 阅读器不写回源文件，
 * 所以这里用图标而不是可点击的 Checkbox，避免暗示「能勾」。
 */
@Composable
private fun TaskCheckbox(checked: Boolean, baseFontSize: Float, modifier: Modifier = Modifier) {
    val tint = if (checked) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        MiIcons.TaskBox(
            checked = checked,
            tint = tint,
            size = (baseFontSize * 0.82f).dp
        )
    }
}

@Composable
private fun CodeView(block: MdBlock.CodeBlock, baseFontSize: Float, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Sp.s)
            .clip(Shapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = Sp.m)
    ) {
        // 语言标签从「独占一行」改成右上角小胶囊，省一行高度
        block.language?.let { lang ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = Sp.s),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = lang,
                    fontSize = Type.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(Shapes.chip)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        SelectionContainer {
            val colors = syntaxColors()
            Text(
                text = buildCodeHighlight(block.code, block.language, colors),
                fontSize = (baseFontSize * 0.86f).sp,
                lineHeight = (baseFontSize * 0.86f * 1.55f).sp,
                fontFamily = FontFamily.Monospace,
                color = colors.getValue(TokenKind.PLAIN),
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
private fun TableView(block: MdBlock.Table, baseFontSize: Float, modifier: Modifier) {
    val columns = maxOf(block.head.size, block.rows.maxOfOrNull { it.size } ?: 0)
    if (columns == 0) return
    val cellFont = baseFontSize * 0.82f
    // 列宽必须由「整张表」统一决定，不能每列各自按内容撑开 ——
    // 那样同一列在不同行宽窄不同，行列必然对不齐（这是用户反馈的核心问题）。
    //
    // 策略：
    //  - 列数 ≤ 4：按内容加权，占满屏宽，不横向滚动
    //  - 列数 > 4：每列固定宽度，整表横向滚动；所有行共用同一套宽度
    val fits = columns <= 4
    val weights = remember(block) { columnWeights(block, columns) }
    val fixedCellWidth = if (fits) 0.dp else COLUMN_WIDTH_DP.dp

    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (fits) Modifier else Modifier.horizontalScroll(rememberScrollState()))
    ) {
        TableRowView(block.head, columns, cellFont, fits, weights, fixedCellWidth, header = true)
        block.rows.forEach { row ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = Alpha.hairline))
            )
            TableRowView(row, columns, cellFont, fits, weights, fixedCellWidth, header = false)
        }
    }
}

/** 按每列平均字符数分配宽度权重，限制在 0.7–2.2，避免某一列独占整行。 */
private fun columnWeights(block: MdBlock.Table, columns: Int): List<Float> {
    val averages = (0 until columns).map { c ->
        val cells = (listOf(block.head) + block.rows).mapNotNull { it.getOrNull(c) }
        val total = cells.sumOf { cell -> plainTextOf(cell).length }
        total.toFloat() / maxOf(cells.size, 1)
    }
    return averages.map { it.coerceIn(4f, 22f) }
}

private fun plainTextOf(cell: MdTableCell): String = buildString {
    fun walk(items: List<MdInline>) {
        items.forEach {
            when (it) {
                is MdInline.Text -> append(it.text)
                is MdInline.Code -> append(it.text)
                is MdInline.Emph -> walk(it.children)
                is MdInline.Strong -> walk(it.children)
                is MdInline.Link -> walk(it.children)
                is MdInline.InlineImage -> append(it.alt)
                is MdInline.InlineMath -> append(it.latex)
                MdInline.Break -> append(' ')
            }
        }
    }
    walk(cell.inlines)
}

@Composable
private fun TableRowView(
    cells: List<MdTableCell>,
    columns: Int,
    cellFont: Float,
    fits: Boolean,
    weights: List<Float>,
    /** 非 fits 模式下每列的固定宽度；所有行共用，保证上下对齐。 */
    fixedCellWidth: androidx.compose.ui.unit.Dp,
    header: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (header) Modifier.background(MaterialTheme.colorScheme.surfaceContainer) else Modifier)
            .padding(vertical = 9.dp)
    ) {
        for (c in 0 until columns) {
            val cell = cells.getOrNull(c)
            val align = when (cell?.align) {
                MdAlign.CENTER -> TextAlign.Center
                MdAlign.RIGHT -> TextAlign.End
                else -> TextAlign.Start
            }
            MdText(
                text = cell?.let { buildInline(it.inlines) } ?: AnnotatedString(""),
                fontSize = cellFont.sp,
                lineHeight = (cellFont * 1.5f).sp,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = align,
                // 两种模式都用「确定的宽度」：
                // fits 用 weight（同一行内按权重分屏宽，所有行一致）；
                // 非 fits 用固定 dp（所有行同一列同宽，横向滚动时整体平移）。
                modifier = if (fits) {
                    Modifier.weight(weights.getOrElse(c) { 1f }).padding(horizontal = 8.dp)
                } else {
                    Modifier.width(fixedCellWidth).padding(horizontal = 10.dp)
                }
            )
        }
    }
}

@Composable
private fun ImageView(block: MdBlock.Image, modifier: Modifier, docUri: String?) {
    val context = LocalContext.current
    // 目标宽度按屏宽算：图片最终按 ContentScale.FillWidth 铺满阅读区，
    // 超出屏宽的像素解出来只会占内存，画面上看不出来。
    // 采样率由 ImageSampling 计算（纯函数、可单测），解码走两遍：
    // 第一遍 inJustDecodeBounds 只读尺寸，第二遍按 inSampleSize 真正解码。
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val targetWidthPx = with(density) { screenWidthDp.dp.roundToPx() }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        null, block.url, docUri, targetWidthPx
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeSampled(context, docUri, block.url, targetWidthPx) }.getOrNull()
        }
    }
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = block.alt,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(Shapes.card)
            )
        } else {
            // 13.sp / 12.dp 与 Type.body / Sp.m 是同一个值，换成令牌只为去掉散落的数字；
            // 圆角 8.dp 仍在三档圆角之外，保持原值不动（改它属于视觉变更）。
            Text(
                text = "图片未加载（仅支持本地图片）：" + block.url,
                fontSize = Type.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(Sp.m)
            )
        }
        if (block.alt.isNotBlank()) {
            Text(
                text = block.alt,
                fontSize = Type.label,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = Sp.s)
            )
        }
    }
}

/**
 * 带链接点击的文本。Compose 1.6 没有内建 LinkAnnotation，
 * 这里用 TextLayoutResult + 点击坐标反查 URL 注解，命中即交给系统处理。
 */
@Composable
private fun MdText(
    text: AnnotatedString,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Start,
    color: Color? = null,
    onAnchor: (String) -> Unit = {},
    onLongPress: (() -> Unit)? = null
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // 用 rememberUpdatedState 拿最新回调与布局结果：
    // 若把它们作为 pointerInput 的 key，每次 onTextLayout 都会重启协程并重建事件循环，
    // 长文档滚动会持续掉帧。
    val latestLayout = rememberUpdatedState(layout)
    val latestOnAnchor = rememberUpdatedState(onAnchor)
    val latestOnLongPress = rememberUpdatedState(onLongPress)

    // 点击识别：只在 Final 阶段观察事件。
    //
    // 这是滚动流畅的关键 —— Main 阶段会抢在父级 LazyColumn 的滚动之前拿到事件，
    // 导致整份文档滑不动（实测滑动完全无响应）。Final 阶段等子级都处理完再看，
    // 既不影响滚动与文本选择，也能取到抬起坐标做 URL 命中。
    val tapModifier = Modifier.pointerInput(text) {
        val slop = viewConfiguration.touchSlop
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                val startOffset = down.position
                var moved = false
                var up: PointerInputChange? = null
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if ((change.position - startOffset).getDistance() > slop) moved = true
                    if (!change.pressed) { up = change; break }
                }
                if (!moved && up != null) {
                    val result = latestLayout.value
                    if (result != null) {
                        val offset = result.getOffsetForPosition(up.position)
                        text.getStringAnnotations("URL", offset, offset)
                            .firstOrNull()
                            ?.let { annotation -> latestOnAnchor.value(annotation.item) }
                    }
                }
            }
        }
    }

    // 长按：交给 detectTapGestures，它本身不消费移动事件。
    // 仅在需要长按（正文段落）时挂载，标题/表格等不挂，避免无谓的手势节点。
    val longPressModifier = if (onLongPress != null) {
        Modifier.pointerInput(text) {
            detectTapGestures(
                onLongPress = { latestOnLongPress.value?.invoke() },
                onTap = {}
            )
        }
    } else {
        Modifier
    }

    Text(
        text = text,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        textAlign = textAlign,
        color = color ?: MaterialTheme.colorScheme.onBackground,
        onTextLayout = { layout = it },
        modifier = modifier.then(tapModifier).then(longPressModifier)
    )
}

/**
 * 代码块高亮：tokenizer 只给语义类别，这里映射成 SpanStyle。
 * 未支持的语言直接返回原文，不构造额外的 AnnotatedString。
 */
private fun buildCodeHighlight(
    code: String,
    language: String?,
    colors: Map<TokenKind, Color>
): AnnotatedString {
    if (!CodeHighlighter.supports(language)) return AnnotatedString(code)
    val tokens = CodeHighlighter.tokenize(code, language)
    if (tokens.isEmpty()) return AnnotatedString(code)
    return buildAnnotatedString {
        tokens.forEach { token ->
            val color = colors[token.kind]
            if (token.kind == TokenKind.PLAIN || color == null) {
                append(token.text)
            } else {
                withStyle(
                    SpanStyle(
                        color = color,
                        fontWeight = if (token.kind == TokenKind.KEYWORD) FontWeight.Medium
                        else FontWeight.Normal
                    )
                ) { append(token.text) }
            }
        }
    }
}

/**
 * 行内样式拼装：粗体/斜体/行内代码/链接由 AnnotatedString 承载。
 *
 * remember 的理由：正文块每次重组（滚动、字号变化、主题切换）都会走到这里，
 * 而 buildAnnotatedString 要遍历全部 inline 节点并逐个 push/pop 样式。
 * 样式只由三个颜色决定，颜色不变时没必要重建同一份字符串。
 * 注意 AnnotatedString 按内容比较，Text 本来就不会因此重绘 ——
 * 这里省下的是每帧的对象分配与 GC 压力，不是重绘。
 */
@Composable
fun buildInline(inlines: List<MdInline>): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(inlines, linkColor, codeBg, codeColor) {
        buildInline(inlines, linkColor, codeBg, codeColor)
    }
}

/** 纯函数版本：颜色由调用方给定，供上面缓存、也便于单独测试。 */
internal fun buildInline(
    inlines: List<MdInline>,
    linkColor: Color,
    codeBg: Color,
    codeColor: Color
): AnnotatedString = buildAnnotatedString {
    appendInlines(inlines, linkColor, codeBg, codeColor)
}

/**
 * 按目标宽度采样解码。
 *
 * 存在的理由：直接 decodeStream 会按原图尺寸分配像素，
 * 4000x3000 的拍照图约 48MB，而显示区只有屏宽。
 * 采样率取 2 的幂（BitmapFactory 的硬性要求），由 ImageSampling 统一计算；
 * 取不到尺寸（outWidth <= 0，例如非图片流）时回落为 1，与改造前行为一致。
 */
private fun decodeSampled(
    context: android.content.Context,
    docUri: String?,
    url: String,
    targetWidthPx: Int
): androidx.compose.ui.graphics.ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    com.yg.moduread.data.MdImageResolver.open(context, docUri, url)
        ?.use { input -> BitmapFactory.decodeStream(input, null, bounds) }
    val sample = com.yg.moduread.data.ImageSampling
        .sampleSizeFor(bounds.outWidth, bounds.outHeight, targetWidthPx)
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return com.yg.moduread.data.MdImageResolver.open(context, docUri, url)
        ?.use { input -> BitmapFactory.decodeStream(input, null, options)?.asImageBitmap() }
}

private fun AnnotatedString.Builder.appendInlines(
    inlines: List<MdInline>,
    linkColor: Color,
    codeBg: Color,
    codeColor: Color
) {
    inlines.forEach { item ->
        when (item) {
            is MdInline.Text -> append(item.text)

            is MdInline.Code -> withStyle(
                SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, color = codeColor)
            ) {
                append(" " + item.text + " ")
            }

            is MdInline.Emph -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendInlines(item.children, linkColor, codeBg, codeColor)
            }

            is MdInline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInlines(item.children, linkColor, codeBg, codeColor)
            }

            is MdInline.Link -> {
                pushStringAnnotation("URL", item.url)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    appendInlines(item.children, linkColor, codeBg, codeColor)
                }
                pop()
            }

            is MdInline.InlineImage -> withStyle(SpanStyle(color = linkColor)) { append("[图片]") }

            // 行内公式：能线性化的走 Unicode（σ、μₜ、x² 直接融入正文），
            // 含分式等无法线性化的结构才退回等宽原文
            is MdInline.InlineMath -> {
                val plain = com.yg.moduread.md.LatexText.unicode(item.latex)
                if (plain != null) {
                    append(plain)
                } else {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = codeColor)) {
                        append(item.latex)
                    }
                }
            }

            MdInline.Break -> append("\n")
        }
    }
}

/**
 * 内嵌图片（EPUB / DOCX / ODT 包里解出的字节）。
 *
 * 不走 MdImageResolver 的 SAF 路径 —— 那些是「文档同目录的外部图片」；
 * 这里的字节已经在内存里，直接 BitmapFactory 解码即可。
 *
 * 解码用 remember(bytes) 缓存，避免每次重组都重新解一遍（大图会很贵）。
 */
@Composable
private fun EmbeddedImageView(block: MdBlock.EmbeddedImage, modifier: Modifier) {
    val bitmap = remember(block.bytes) { decodeBitmap(block.bytes) }
    if (bitmap == null) {
        // 解不出来也给出明确提示，而不是留白
        Box(
            modifier
                .fillMaxWidth()
                .clip(Shapes.card)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(Sp.m)
        ) {
            Text(
                text = "图片无法解码" + if (block.alt.isBlank()) "" else "：" + block.alt,
                fontSize = Type.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    Column(modifier.fillMaxWidth().padding(bottom = Sp.s)) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = block.alt.ifBlank { null },
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().clip(Shapes.card)
        )
        if (block.alt.isNotBlank()) {
            Text(
                text = block.alt,
                fontSize = Type.label,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = Sp.s)
            )
        }
    }
}

/** GIF/WebP/超大图都可能解不出来，统一返回 null 由调用方降级。 */
private fun decodeBitmap(bytes: ByteArray): Bitmap? = runCatching {
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

/**
 * PDF 页占位。
 *
 * 真正的位图由 PdfPageView 的调用方（ReaderScreen 里的 PdfPageSlot）
 * 通过 PdfRenderer 按需渲染 —— PdfRenderer 非线程安全且必须 close，
 * 不适合放在纯渲染函数里创建。这里只画一个占位框，保证块本身可测量。
 */
@Composable
private fun PdfPageView(block: MdBlock.PdfPage, modifier: Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(
                if (block.heightPoints > 0) block.widthPoints.toFloat() / block.heightPoints.toFloat()
                else 0.707f  // A4 近似，防止除零
            )
            .clip(Shapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Text(
            text = "第 " + (block.pageIndex + 1) + " 页",
            fontSize = Type.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

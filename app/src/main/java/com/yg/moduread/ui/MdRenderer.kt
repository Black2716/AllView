package com.yg.moduread.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.yg.moduread.md.MdAlign
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdInline
import com.yg.moduread.md.MdTableCell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 正文行高：1.75 是中文长文阅读的舒适区间。 */
private const val LINE_HEIGHT_RATIO = 1.75f

@Composable
fun MdBlockView(
    block: MdBlock,
    baseFontSize: Float,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    docUri: String? = null
) {
    when (block) {
        is MdBlock.Heading -> HeadingView(block, baseFontSize, modifier)
        is MdBlock.Para -> MdText(
            text = buildInline(block.content),
            fontSize = baseFontSize.sp,
            lineHeight = (baseFontSize * LINE_HEIGHT_RATIO).sp,
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = if (compact) 2.dp else baseFontSize.dp * 0.7f)
        )

        is MdBlock.Quote -> QuoteView(block, baseFontSize, modifier, docUri)
        is MdBlock.ListBlock -> ListView(block, baseFontSize, modifier, docUri)
        is MdBlock.CodeBlock -> CodeView(block, baseFontSize, modifier)
        is MdBlock.Table -> TableView(block, baseFontSize, modifier)
        is MdBlock.Image -> ImageView(block, modifier, docUri)
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
private fun HeadingView(block: MdBlock.Heading, baseFontSize: Float, modifier: Modifier) {
    val scale = when (block.level) {
        1 -> 1.72f
        2 -> 1.42f
        3 -> 1.20f
        4 -> 1.06f
        5 -> 0.98f
        else -> 0.92f
    }
    val topPad = when (block.level) {
        1 -> 24.dp
        2 -> 18.dp
        else -> 12.dp
    }
    MdText(
        text = buildInline(block.content),
        fontSize = (baseFontSize * scale).sp,
        lineHeight = (baseFontSize * scale * 1.32f).sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPad, bottom = baseFontSize.dp * 0.45f)
    )
}

@Composable
private fun QuoteView(
    block: MdBlock.Quote,
    baseFontSize: Float,
    modifier: Modifier,
    docUri: String?
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 6.dp)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        )
        Column(Modifier.padding(start = 12.dp).fillMaxWidth()) {
            block.children.forEach { child ->
                MdBlockView(child, baseFontSize * 0.97f, compact = true, docUri = docUri)
            }
        }
    }
}

@Composable
private fun ListView(
    block: MdBlock.ListBlock,
    baseFontSize: Float,
    modifier: Modifier,
    docUri: String?
) {
    Column(modifier.fillMaxWidth().padding(bottom = baseFontSize.dp * 0.4f)) {
        block.items.forEachIndexed { index, item ->
            Row(Modifier.fillMaxWidth().padding(vertical = if (block.tight) 1.dp else 3.dp)) {
                Text(
                    text = if (block.ordered) (block.start + index).toString() + "." else "•",
                    fontSize = baseFontSize.sp,
                    lineHeight = (baseFontSize * LINE_HEIGHT_RATIO).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = baseFontSize.dp * 1.5f)
                )
                Column(Modifier.fillMaxWidth().padding(start = 6.dp)) {
                    item.forEach { child ->
                        MdBlockView(child, baseFontSize, compact = true, docUri = docUri)
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeView(block: MdBlock.CodeBlock, baseFontSize: Float, modifier: Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        block.language?.let { lang ->
            Text(
                text = lang,
                fontSize = (baseFontSize * 0.72f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        SelectionContainer {
            Text(
                text = block.code,
                fontSize = (baseFontSize * 0.86f).sp,
                lineHeight = (baseFontSize * 0.86f * 1.55f).sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    // 列少时按内容宽度加权分配（不横向滚动），列多时退化为固定列宽 + 横向滚动
    val fits = columns <= 4
    val weights = remember(block) { columnWeights(block, columns) }

    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .then(if (fits) Modifier else Modifier.horizontalScroll(rememberScrollState()))
    ) {
        TableRowView(block.head, columns, cellFont, fits, weights, header = true)
        block.rows.forEach { row ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            )
            TableRowView(row, columns, cellFont, fits, weights, header = false)
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
    header: Boolean
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (header) Modifier.background(MaterialTheme.colorScheme.surfaceVariant) else Modifier)
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
                modifier = if (fits) {
                    Modifier.weight(weights.getOrElse(c) { 1f }).padding(horizontal = 8.dp)
                } else {
                    Modifier.widthIn(min = 110.dp, max = 240.dp).padding(horizontal = 12.dp)
                }
            )
        }
    }
}

@Composable
private fun ImageView(block: MdBlock.Image, modifier: Modifier, docUri: String?) {
    val context = LocalContext.current
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, block.url, docUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                com.yg.moduread.data.MdImageResolver.open(context, docUri, block.url)
                    ?.use { input -> BitmapFactory.decodeStream(input)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = block.alt,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            )
        } else {
            Text(
                text = "图片未加载（仅支持本地图片）：" + block.url,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            )
        }
        if (block.alt.isNotBlank()) {
            Text(
                text = block.alt,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
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
    textAlign: TextAlign = TextAlign.Start
) {
    val uriHandler = LocalUriHandler.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text = text,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        textAlign = textAlign,
        color = MaterialTheme.colorScheme.onBackground,
        onTextLayout = { layout = it },
        modifier = modifier.pointerInput(text) {
            detectTapGestures { position ->
                val result = layout ?: return@detectTapGestures
                val offset = result.getOffsetForPosition(position)
                text.getStringAnnotations("URL", offset, offset)
                    .firstOrNull()
                    ?.let { annotation ->
                        // 页内锚点（#标题）没有外部处理器，点击时保持静默
                        if (!annotation.item.startsWith("#")) {
                            runCatching { uriHandler.openUri(annotation.item) }
                        }
                    }
            }
        }
    )
}

/** 行内样式拼装：粗体/斜体/行内代码/链接由 AnnotatedString 承载。 */
@Composable
fun buildInline(inlines: List<MdInline>): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
    return buildAnnotatedString {
        appendInlines(inlines, linkColor, codeBg, codeColor)
    }
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

            MdInline.Break -> append("\n")
        }
    }
}

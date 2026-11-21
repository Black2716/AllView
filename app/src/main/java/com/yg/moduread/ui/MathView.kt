package com.yg.moduread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.moduread.md.Latex
import com.yg.moduread.md.MathNode

/**
 * 公式渲染：把 LaTeX 排版树画成 Compose 布局。
 *
 * 不做 TeX 级别的精细排版（没有斜体校正、没有伸缩定界符），
 * 但结构完整：分式、上下标、求和上下限、定界符、根号、重音、箭头都正确成组。
 */
@Composable
fun MathView(latex: String, baseFontSize: Float, display: Boolean, modifier: Modifier = Modifier) {
    val tree = remember(latex) { Latex.parse(latex) }
    val color = MaterialTheme.colorScheme.onBackground
    val size = if (display) baseFontSize * 1.06f else baseFontSize * 0.98f

    if (display) {
        Box(
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 12.dp)
            , contentAlignment = Alignment.Center
        ) {
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                MathRow(treeNode = tree, size = size.sp, color = color)
            }
        }
    } else {
        MathInline(tree = tree, size = size.sp, color = color, modifier = modifier)
    }
}

/** 行内公式：与文字基线对齐，不换行不滚动。 */
@Composable
private fun MathInline(
    tree: MathNode,
    size: TextUnit,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        MathNodeView(tree, size, color)
    }
}

@Composable
private fun MathRow(treeNode: MathNode, size: TextUnit, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MathNodeView(treeNode, size, color)
    }
}

@Composable
fun MathNodeView(node: MathNode, size: TextUnit, color: Color) {
    when (node) {
        is MathNode.Row -> Row(verticalAlignment = Alignment.CenterVertically) {
            node.children.forEach { MathNodeView(it, size, color) }
        }

        is MathNode.Text -> Text(
            text = node.text,
            fontSize = size,
            color = color,
            // 数学排版惯例：变量斜体，数字与运算符正体
            fontStyle = FontStyle.Italic.takeUnless { node.upright || node.text.isNumericOrPunct() },
            fontWeight = if (node.bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = if (node.script) FontFamily.Serif else FontFamily.Default
        )

        is MathNode.Space -> Spacer(Modifier.width((size.value * node.widthEm * 0.6f).coerceAtLeast(0f).dp))

        is MathNode.Frac -> FracView(node, size, color)
        is MathNode.Script -> ScriptView(node, size, color)
        is MathNode.BigOp -> BigOpView(node, size, color)
        is MathNode.Delim -> DelimView(node, size, color)
        is MathNode.Sqrt -> SqrtView(node, size, color)
        is MathNode.Accent -> AccentView(node, size, color)
        is MathNode.Arrow -> ArrowView(node, size, color)
    }
}

/** 全由数字、空白与常见标点组成时按正体排，避免 "2021" 变成斜体。 */
private fun String.isNumericOrPunct(): Boolean =
    isNotEmpty() && all { it.isDigit() || it.isWhitespace() || it in ".,;:!?()[]{}'\"-–—/|<>*+=<>" }

@Composable
private fun FracView(node: MathNode.Frac, size: TextUnit, color: Color) {
    // width(IntrinsicSize.Max) 让分数线宽度 = 分子分母中较宽的那个
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.width(IntrinsicSize.Max).padding(horizontal = 2.dp)
    ) {
        Box(Modifier.padding(bottom = 2.dp)) {
            MathNodeView(node.num, size * 0.92f, color)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.2.dp)
                .background(color.copy(alpha = 0.9f))
        )
        Box(Modifier.padding(top = 2.dp)) {
            MathNodeView(node.den, size * 0.92f, color)
        }
    }
}

@Composable
private fun ScriptView(node: MathNode.Script, size: TextUnit, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MathNodeView(node.base, size, color)
        if (node.sup != null) {
            Column(Modifier.padding(bottom = size.value.dp * 0.5f)) {
                MathNodeView(node.sup, size * 0.68f, color)
            }
        }
        if (node.sub != null) {
            Column(Modifier.padding(top = size.value.dp * 0.4f)) {
                MathNodeView(node.sub, size * 0.68f, color)
            }
        }
    }
}

@Composable
private fun BigOpView(node: MathNode.BigOp, size: TextUnit, color: Color) {
    if (node.limitsAboveBelow && (node.sup != null || node.sub != null)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (node.sup != null) MathNodeView(node.sup, size * 0.66f, color)
            Text(
                text = node.symbol,
                fontSize = size * 1.25f,
                color = color,
                fontWeight = FontWeight.Medium
            )
            if (node.sub != null) MathNodeView(node.sub, size * 0.66f, color)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = node.symbol,
                fontSize = size * 1.2f,
                color = color,
                fontWeight = FontWeight.Medium
            )
            if (node.sub != null) MathNodeView(node.sub, size * 0.66f, color)
            if (node.sup != null) MathNodeView(node.sup, size * 0.66f, color)
        }
    }
}

@Composable
private fun DelimView(node: MathNode.Delim, size: TextUnit, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (node.open.isNotEmpty()) {
            Text(node.open, fontSize = size * 1.15f, color = color.copy(alpha = 0.85f))
        }
        Box(Modifier.padding(horizontal = size.value.dp * 0.12f)) {
            MathNodeView(node.body, size, color)
        }
        if (node.close.isNotEmpty()) {
            Text(node.close, fontSize = size * 1.15f, color = color.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun SqrtView(node: MathNode.Sqrt, size: TextUnit, color: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            "√",
            fontSize = size * 1.25f,
            color = color,
            modifier = Modifier.padding(bottom = 1.dp)
        )
        Column(
            Modifier
                .width(IntrinsicSize.Max)
                .padding(bottom = 1.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.2.dp)
                    .background(color.copy(alpha = 0.9f))
            )
            Box(Modifier.padding(top = 1.dp)) {
                MathNodeView(node.body, size, color)
            }
        }
    }
}

@Composable
private fun AccentView(node: MathNode.Accent, size: TextUnit, color: Color) {
    // 优先用 Unicode 组合附标：字形引擎会自动把 ̂ ̄ ̃ 放到基字符正上方
    val combining = when (node.mark) {
        "ˆ" -> "\u0302"
        "‾" -> "\u0305"
        "˜" -> "\u0303"
        "→" -> "\u20D7"
        else -> null
    }
    val base = node.body.simpleText()
    if (combining != null && base != null && base.isNotEmpty()) {
        Text(
            text = base[0] + combining + base.substring(1),
            fontSize = size,
            color = color
        )
    } else {
        // 退化：附标单独一行居中，不再用负 padding（Compose 不允许）
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(node.mark, fontSize = size * 0.75f, color = color)
            MathNodeView(node.body, size, color)
        }
    }
}

/** 若整棵子树只含文字，返回其纯文本；否则 null（调用方走结构化排版）。 */
private fun MathNode.simpleText(): String? {
    val sb = StringBuilder()
    fun walk(node: MathNode): Boolean = when (node) {
        is MathNode.Text -> { sb.append(node.text); true }
        is MathNode.Row -> node.children.all { walk(it) }
        is MathNode.Space -> { sb.append(' '); true }
        else -> false
    }
    return if (walk(this)) sb.toString() else null
}

@Composable
private fun ArrowView(node: MathNode.Arrow, size: TextUnit, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (node.label != null) {
            MathNodeView(node.label, size * 0.62f, color)
        }
        Text("⟶", fontSize = size * 1.1f, color = color)
    }
}

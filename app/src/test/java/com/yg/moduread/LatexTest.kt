package com.yg.moduread

import com.yg.moduread.md.Latex
import com.yg.moduread.md.MathNode
import com.yg.moduread.md.MdBlock
import com.yg.moduread.md.MdParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LatexTest {

    private fun first(node: MathNode): MathNode =
        if (node is MathNode.Row) node.children.firstOrNull() ?: MathNode.Text("") else node

    @Test
    fun fractionIsNested() {
        val tree = Latex.parse("\\frac{a}{b}")
        val frac = first(tree)
        assertTrue("应为分式", frac is MathNode.Frac)
    }

    @Test
    fun greekAndOperatorsMapped() {
        val tree = Latex.parse("\\sigma + \\mu_t")
        val text = render(tree)
        assertTrue(text.contains("σ"))
        assertTrue(text.contains("μ"))
    }

    @Test
    fun bigOperatorTakesLimits() {
        val tree = Latex.parse("\\min_{B,S} f")
        val op = first(tree)
        assertTrue("min 应带下限", op is MathNode.BigOp && op.sub != null)
    }

    @Test
    fun delimiterWrapsBody() {
        val tree = Latex.parse("\\Bigl( x \\Bigr)")
        assertTrue(render(tree).contains("x"))
    }

    @Test
    fun unknownCommandDegradesNotDrops() {
        val tree = Latex.parse("\\unknowncmd{x}")
        assertTrue(render(tree).contains("unknowncmd"))
    }

    @Test
    fun mathBlockIsRecognised() {
        val blocks = MdParser.shared.parse("前言\n\n$$\nE = mc^2\n$$\n\n后记")
        val math = blocks.filterIsInstance<MdBlock.Math>().single()
        assertTrue(math.display)
        assertTrue(math.latex.contains("mc^2"))
        assertTrue("前后段落保留", blocks.filterIsInstance<MdBlock.Para>().size == 2)
    }

    @Test
    fun inlineMathInsideParagraph() {
        val blocks = MdParser.shared.parse("设 \$x^2\$ 为平方")
        val para = blocks.filterIsInstance<MdBlock.Para>().single()
        assertTrue(para.content.any { it is com.yg.moduread.md.MdInline.InlineMath })
    }

    @Test
    fun realBookFormulasAllParseWithoutLoss() {
        val file = listOf("../sample/sample.md", "sample/sample.md")
            .map(::File).firstOrNull { it.exists() } ?: return
        val blocks = MdParser.shared.parse(file.readText())
        val math = blocks.filterIsInstance<MdBlock.Math>()
        assertTrue("书中应有 14 个块级公式，实际 ${math.size}", math.size >= 14)
        // 每个公式都必须能解析出结构，且不残留 LaTeX 反斜杠命令
        math.forEach { block ->
            val tree = Latex.parse(block.latex)
            val text = render(tree)
            assertTrue("公式不应为空: ${block.latex.take(30)}", text.isNotBlank())
            assertTrue(
                "公式残留 LaTeX 命令: ${block.latex.take(40)} -> $text",
                !text.contains("\\frac") && !text.contains("\\mathrm")
            )
        }
    }

    private fun render(node: MathNode): String = buildString { walk(node, this) }

    private fun walk(node: MathNode, sb: StringBuilder) {
        when (node) {
            is MathNode.Row -> node.children.forEach { walk(it, sb) }
            is MathNode.Text -> sb.append(node.text)
            is MathNode.Space -> sb.append(' ')
            is MathNode.Frac -> { walk(node.num, sb); sb.append('/'); walk(node.den, sb) }
            is MathNode.Script -> {
                walk(node.base, sb)
                node.sup?.let { sb.append("^("); walk(it, sb); sb.append(')') }
                node.sub?.let { sb.append("_("); walk(it, sb); sb.append(')') }
            }
            is MathNode.BigOp -> {
                sb.append(node.symbol)
                node.sup?.let { sb.append("^("); walk(it, sb); sb.append(')') }
                node.sub?.let { sb.append("_("); walk(it, sb); sb.append(')') }
            }
            is MathNode.Delim -> { sb.append(node.open); walk(node.body, sb); sb.append(node.close) }
            is MathNode.Sqrt -> { sb.append('√'); walk(node.body, sb) }
            is MathNode.Accent -> walk(node.body, sb)
            is MathNode.Arrow -> { node.label?.let { walk(it, sb) }; sb.append("→") }
        }
    }
}

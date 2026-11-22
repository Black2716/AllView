package com.yg.moduread.md

/** 行内元素（用于标题、段落、表格单元格、引用内的文本流）。 */
sealed interface MdInline {
    data class Text(val text: String) : MdInline
    data class Code(val text: String) : MdInline
    data class Emph(val children: List<MdInline>) : MdInline
    data class Strong(val children: List<MdInline>) : MdInline
    data class Link(val url: String, val children: List<MdInline>) : MdInline
    /** 行内图片（段落里混合出现时降级为占位标记，独占段落时按块级图片渲染）。 */
    data class InlineImage(val url: String, val alt: String) : MdInline

    /** 行内公式 $...$。 */
    data class InlineMath(val latex: String) : MdInline
    data object Break : MdInline
}

enum class MdAlign { NONE, LEFT, CENTER, RIGHT }

data class MdTableCell(val inlines: List<MdInline>, val align: MdAlign)

/** 块级元素。解析一次、渲染多次，因此全部为不可变数据。 */
sealed interface MdBlock {
    data class Heading(val level: Int, val content: List<MdInline>) : MdBlock
    data class Para(val content: List<MdInline>) : MdBlock
    data class Quote(val children: List<MdBlock>) : MdBlock
    data class ListBlock(
        val ordered: Boolean,
        val start: Int,
        val tight: Boolean,
        val items: List<List<MdBlock>>,
        /**
         * 任务列表的勾选态，与 [items] 同下标；普通列表项为 null。
         * 只读 —— 阅读器不写回源文件，所以不做点击交互。
         */
        val taskStates: List<Boolean?> = emptyList()
    ) : MdBlock

    data class CodeBlock(val code: String, val language: String?) : MdBlock
    data class Table(val head: List<MdTableCell>, val rows: List<List<MdTableCell>>) : MdBlock
    /** 块级公式：display=true 时独立成行居中，false 为行内公式。 */
    data class Math(val latex: String, val display: Boolean) : MdBlock

    data class Image(val url: String, val alt: String) : MdBlock
    data object Rule : MdBlock
}

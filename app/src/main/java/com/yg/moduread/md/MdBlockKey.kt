package com.yg.moduread.md

/**
 * 块的稳定标识与标题提取。
 *
 * 这两个函数原本写在 ui/ReaderRoot.kt 的文件末尾，但它们操作的全是
 * [MdBlock] 模型本身（类型、正文、层级），与 Compose 无关。
 * 放在 ui 包里导致：md 层无法自测这两个规则，任何使用方（ReaderScreen）
 * 虽然同包可见，却要靠「文件末尾还有个顶层函数」这种隐式约定发现它们。
 * 移到 md 包后，规则与它描述的模型在同一处，并且可以脱离 Compose 断言。
 */

/**
 * LazyColumn 的稳定 key 提示：类型 + 正文前 24 字。
 * 只用于 key，不做任何展示；同一位置内容变了 key 才变，滚动时不会误重组。
 */
fun MdBlock.keyHint(): String = when (this) {
    is MdBlock.Heading -> "h" + level + ":" + content.plainText().take(24)
    is MdBlock.Para -> "p:" + content.plainText().take(24)
    is MdBlock.CodeBlock -> "c:" + code.take(24)
    is MdBlock.Math -> "m:" + latex.take(24)
    is MdBlock.Image -> "i:" + url.take(24)
    is MdBlock.Table -> "t:" + (head.firstOrNull()?.inlines?.plainText()?.take(16) ?: "")
    is MdBlock.Quote -> "q"
    is MdBlock.ListBlock -> "l:" + (items.firstOrNull()?.firstOrNull()?.let { b ->
        when (b) {
            is MdBlock.Para -> b.content.plainText().take(16)
            else -> ""
        }
    } ?: "")
    is MdBlock.Rule -> "r"
    is MdBlock.EmbeddedImage -> "eb:" + alt.take(16)
    is MdBlock.PdfPage -> "pg:" + pageIndex
}

/** 小工具：标题文本（首个一级标题优先，其次文件名）。 */
fun List<MdBlock>.displayTitle(fallback: String): String {
    val heading = firstOrNull { it is MdBlock.Heading && it.level <= 2 } as? MdBlock.Heading
    val text = heading?.let { it.content.plainText() }
    return text?.takeIf { it.isNotBlank() } ?: fallback
}

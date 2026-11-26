package com.yg.moduread.ui

import com.yg.moduread.data.LoadedDoc
import com.yg.moduread.md.MdToc
import com.yg.moduread.md.SearchIndex
import com.yg.moduread.md.TocEntry

/**
 * 加载结果 → 界面状态的映射。
 *
 * 从 ReaderViewModel.openInNewSession 里抽出来的理由：那段 25 行的
 * `DocumentState(...)` 构造夹在「取授权、读文件、写偏好、刷书签」之间，
 * 导致一次打开流程有 90 行。构造本身是纯函数（输入 LoadedDoc + 上次位置，
 * 输出 DocumentState），抽出来后可单独断言「目录来源、锚点表、索引是否一致」。
 *
 * 关键规则（全部保留原样）：
 *  - PDF 的 outline 优先于正文 Heading 生成的目录 —— 它带真实层级；
 *  - PDF 目录的 blockIndex 用真实页码，因为第 page 页就是第 page 个 PdfPage 块；
 *  - anchors 由最终 toc 生成，保证「点目录项」与「点正文锚点」落到同一块。
 */
internal fun buildDocumentState(
    loaded: LoadedDoc,
    restoreIndex: Int,
    restoreOffset: Int
): DocumentState {
    val blockToc = MdToc.build(loaded.blocks)
    val toc = if (loaded.outline.isNotEmpty()) {
        loaded.outline.map { (page, lvl, title) ->
            TocEntry(
                level = lvl.coerceIn(1, 6),
                text = title,
                // blockIndex 用真实页码：PDF 的第 page 页对应第 page 个 PdfPage 块
                blockIndex = page,
                slug = ""
            )
        }
    } else blockToc
    return DocumentState(
        title = loaded.title,
        blocks = loaded.blocks,
        charCount = loaded.charCount,
        baseDir = loaded.baseDir,
        docUri = loaded.docUri,
        restoreIndex = restoreIndex,
        restoreOffset = restoreOffset,
        toc = toc,
        anchors = MdToc.index(toc),
        searchIndex = SearchIndex.build(loaded.blocks),
        format = loaded.format,
        pageCount = loaded.pageCount,
        encrypted = loaded.encrypted,
        hasTextLayer = loaded.hasTextLayer,
        slides = loaded.slides,
        loading = false
    )
}

/**
 * 打开失败的会话状态：保留标签、标题取 uri 末段、附上人话错误。
 *
 * 标题只在当前为空时才覆盖 —— 已经加载出标题的文档再失败（例如重试），
 * 不应该被「打开失败」冲掉原有标题。
 */
internal fun failedDocumentState(
    current: DocumentState,
    fallbackTitle: String,
    error: String
): DocumentState = current.copy(
    title = if (current.title.isBlank()) fallbackTitle else current.title,
    loading = false,
    error = error
)

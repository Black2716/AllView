package com.yg.moduread.ui

import androidx.compose.runtime.Immutable

/**
 * 一个打开的文档会话。
 *
 * 多开的核心：每个会话持有**自己的** [DocumentState]（AST、目录、锚点、搜索索引）
 * 与**自己的**滚动位置。切换标签只切换「当前是哪个 session」，
 * 不销毁其它 session 的任何状态 —— 滚动位置、进度、书签全部各自独立。
 *
 * 只做数据；滚动位置由界面层的 LazyListState 持有，通过 [scrollIndex]/[scrollOffset]
 * 在切走时回写、切回时恢复。
 */
@Immutable
data class DocSession(
    /** 会话 id。用创建序递增，不用 uri —— 同一文件允许开两个标签对照阅读。 */
    val id: Long,
    val doc: DocumentState,
    /** 该文档的滚动位置，切换标签时由界面层回写。 */
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    /** 该文档当前可见块（目录高亮用）。 */
    val visibleBlock: Int = 0
)

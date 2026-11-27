package com.yg.moduread.ui

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable

/**
 * 全应用统一的底部面板外壳。
 *
 * 抽出的理由：目录 / 搜索 / 书签 / 阅读设置四个面板各自写了一遍
 * `@OptIn(ExperimentalMaterial3Api::class)` + `rememberModalBottomSheetState(skipPartiallyExpanded = true)`
 * + `ModalBottomSheet(onDismissRequest, sheetState)`。四份逐字重复的样板意味着
 * 「面板怎么出现、怎么消失、能不能半展开」这件事有四个定义处 —— 想让面板行为
 * 一致就得改四遍，且新增面板时容易漏掉 skipPartiallyExpanded，出现半屏态。
 *
 * 收敛后：面板内容只描述「显示什么」，呈现方式只有这一个实现。
 * skipPartiallyExpanded = true 是刻意的 —— 这四个面板都是列表/表单，
 * 半展开态下只能看到两三行，没有使用价值。
 *
 * 2026-11：补齐 shape / containerColor / scrimColor / dragHandle。
 * 改造前这四个参数全用 M3 默认（顶部 28dp 圆角 + 默认遮罩），
 * 与 Design.kt 里 Shapes.sheet=12dp 的定义互相矛盾 —— 面板圆角因此
 * 比卡片圆角大一倍多，观感割裂。现在面板圆角来自 Unified 令牌。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = Shapes.sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f),
        dragHandle = { SheetDragHandle() },
        content = content
    )
}

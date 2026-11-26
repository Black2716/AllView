package com.yg.moduread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 文档标签条。
 *
 * 为什么是标签而不是分屏：手机上分屏后每栏只剩五六个汉字一行，
 * 长文阅读体验反而变差；标签保持单栏满宽，切换成本也低。
 *
 * 只在打开 2 个以上文档时显示 —— 单文档时不占任何高度。
 */
@Composable
fun DocTabs(
    sessions: List<DocSession>,
    activeId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (sessions.size < 2) return
    val scroll = rememberScrollState()
    Row(
        modifier
            .fillMaxWidth()
            // 安全区：不加这行标签条会压住状态栏（时间和电量图标）
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .horizontalScroll(scroll)
            .padding(horizontal = Sp.s, vertical = Sp.xs),
        horizontalArrangement = Arrangement.spacedBy(Sp.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sessions.forEach { session ->
            TabChip(
                label = session.doc.title.ifBlank { "未命名" },
                active = session.id == activeId,
                onClick = { onSelect(session.id) },
                onClose = { onClose(session.id) }
            )
        }
    }
}

// 标签条尺寸：30dp 是「单行 caption + 上下各 6dp 呼吸」的最小可用高度，
// 160dp 保证标题能显示约 8 个汉字再省略。抽成常量让 TabChip 与关闭按钮共用同一份定义。
private val TabChipHeight = 30.dp
private val TabChipMaxWidth = 160.dp

@Composable
private fun TabChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        Modifier
            .height(TabChipHeight)
            .widthIn(max = TabChipMaxWidth)
            // 形状取令牌，不再手写 RoundedCornerShape(Radius.s)：同一个圆角有两处定义迟早不同步
            .clip(Shapes.chip)
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .clickable(onClick = onClick)
            .padding(start = Sp.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = Type.caption,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        // 【已标注的尺寸改动】关闭按钮命中区从约 20x16dp 撑到 48x30dp。
        // 改造前是 12dp 图标 + 4/2dp 内边距 + 尾距 2dp，手指几乎点不中，
        // 一按就落到标签本体上变成「切换到该文档」。图标尺寸、颜色、居中都不变；
        // 代价是每个标签横向宽了约 22dp（原尾距被命中区吸收）。高度受标签条 30dp 约束，
        // 取 48x30 而不再加高 —— 再高就会把标签条本身撑高。
        Box(
            Modifier
                .size(width = MinTouchTarget, height = TabChipHeight)
                .clip(Shapes.chip)
                .clickable(onClick = onClose)
                .semantics { contentDescription = "关闭 " + label },
            contentAlignment = Alignment.Center
        ) {
            MiIcons.Close(
                tint = if (active) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                size = 12.dp
            )
        }
    }
}

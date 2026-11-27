package com.yg.moduread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
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
 *
 * 外观改造：
 *  - 底色从 surfaceContainerLow 换成 background，底部补一条发丝线，
 *    与阅读器顶栏同一套「浮在内容之上但不抢视线」的处理；
 *  - 选中态从「整块实心主色」换成 primaryContainer 填充 + onPrimaryContainer 文字，
 *    实心主色在夜间纸模式里会亮得刺眼，而它是常驻元素；
 *  - 标签高度 30dp → 48dp，关闭键 20×16dp → 48×48dp（原来是竖屏最容易误触的地方：
 *    想关掉标签，结果点中标签本体切了过去）。
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
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                // 安全区：不加这行标签条会压住状态栏（时间和电量图标）
                .windowInsetsPadding(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.background)
                .horizontalScroll(scroll)
                .padding(horizontal = Sp.s),
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
        HairlineDivider()
    }
}

// 标签高度就取触摸目标最小值：这一条是常驻可点区域，
// 30dp 在手机上等同于「必须瞄准」，而 48dp 只是 +18dp 的竖向代价（且只在多文档时出现）。
private val TabChipHeight = MinTouchTarget
private val TabChipMaxWidth = 200.dp

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
                if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.background
            )
            .clickable(onClick = onClick)
            .semantics {
                // 读屏用户需要知道「这是标签、当前是不是它」，
                // 改造前只有一个可点的 Row，语义上等同于一段文字。
                role = Role.Tab
                selected = active
                contentDescription = label
            }
            .padding(start = Sp.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = Type.caption,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        // 关闭键命中区 48×48dp（改造前约 20×16dp：12dp 图标 + 4/2dp 内边距 + 尾距 2dp，
        // 手指几乎点不中，一按就落到标签本体上变成「切换到该文档」）。
        // 图标尺寸、颜色、居中都不变，只放大命中区。
        Box(
            Modifier
                .size(TabChipHeight)
                .clip(Shapes.chip)
                .clickable(onClick = onClose)
                .semantics { contentDescription = "关闭 " + label },
            contentAlignment = Alignment.Center
        ) {
            MiIcons.Close(
                tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                size = 14.dp
            )
        }
    }
}
